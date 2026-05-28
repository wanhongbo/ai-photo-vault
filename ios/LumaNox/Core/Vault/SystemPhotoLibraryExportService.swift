import Foundation
import Photos

enum SystemPhotoLibraryExportError: LocalizedError {
    case authorizationDenied
    case albumUnavailable
    case unsupportedMedia

    var errorDescription: String? {
        switch self {
        case .authorizationDenied:
            return L10n.tr("photo_viewer_export_denied")
        case .albumUnavailable:
            return L10n.tr("export_result_album_unavailable")
        case .unsupportedMedia:
            return L10n.tr("export_result_unsupported_media")
        }
    }
}

@MainActor
final class SystemPhotoLibraryExportService {
    static let shared = SystemPhotoLibraryExportService()
    static let albumTitle = "LumaNox"

    private init() {}

    func export(fileURL: URL) async throws {
        let readWriteStatus = await requestPhotoReadWriteAuthorization()
        let addOnlyStatus = PHPhotoLibrary.authorizationStatus(for: .addOnly)
        guard readWriteStatus == .authorized ||
                readWriteStatus == .limited ||
                addOnlyStatus == .authorized ||
                addOnlyStatus == .limited else {
            throw SystemPhotoLibraryExportError.authorizationDenied
        }

        do {
            try await exportToAlbum(fileURL: fileURL)
        } catch SystemPhotoLibraryExportError.unsupportedMedia {
            throw SystemPhotoLibraryExportError.unsupportedMedia
        } catch {
            try await exportToPhotoLibrary(fileURL: fileURL)
        }
    }

    private func exportToAlbum(fileURL: URL) async throws {
        let album = try await album()
        var didCreateAsset = false

        let lockToken = AppLockManager.shared.beginSystemInteraction()
        defer { AppLockManager.shared.endSystemInteraction(lockToken) }
        try await PHPhotoLibrary.shared().performChanges {
            let assetRequest = self.makeAssetChangeRequest(fileURL: fileURL)

            guard let placeholder = assetRequest?.placeholderForCreatedAsset,
                  let albumRequest = PHAssetCollectionChangeRequest(for: album) else {
                return
            }
            didCreateAsset = true
            albumRequest.addAssets([placeholder] as NSArray)
        }

        guard didCreateAsset else {
            throw SystemPhotoLibraryExportError.unsupportedMedia
        }
    }

    private func exportToPhotoLibrary(fileURL: URL) async throws {
        var didCreateAsset = false
        let lockToken = AppLockManager.shared.beginSystemInteraction()
        defer { AppLockManager.shared.endSystemInteraction(lockToken) }
        try await PHPhotoLibrary.shared().performChanges {
            guard self.makeAssetChangeRequest(fileURL: fileURL) != nil else { return }
            didCreateAsset = true
        }

        guard didCreateAsset else {
            throw SystemPhotoLibraryExportError.unsupportedMedia
        }
    }

    private func requestPhotoReadWriteAuthorization() async -> PHAuthorizationStatus {
        let current = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        guard current == .notDetermined else { return current }
        let lockToken = AppLockManager.shared.beginSystemInteraction()
        return await withCheckedContinuation { continuation in
            PHPhotoLibrary.requestAuthorization(for: .readWrite) { status in
                Task { @MainActor in
                    AppLockManager.shared.endSystemInteraction(lockToken)
                    continuation.resume(returning: status)
                }
            }
        }
    }

    private func album() async throws -> PHAssetCollection {
        if let existing = fetchAlbum() {
            return existing
        }

        var createdIdentifier: String?
        let lockToken = AppLockManager.shared.beginSystemInteraction()
        defer { AppLockManager.shared.endSystemInteraction(lockToken) }
        try await PHPhotoLibrary.shared().performChanges {
            let request = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(
                withTitle: Self.albumTitle
            )
            createdIdentifier = request.placeholderForCreatedAssetCollection.localIdentifier
        }

        guard let createdIdentifier,
              let created = PHAssetCollection.fetchAssetCollections(
                withLocalIdentifiers: [createdIdentifier],
                options: nil
              ).firstObject else {
            throw SystemPhotoLibraryExportError.albumUnavailable
        }
        return created
    }

    private func fetchAlbum() -> PHAssetCollection? {
        let options = PHFetchOptions()
        options.predicate = NSPredicate(format: "localizedTitle = %@", Self.albumTitle)
        return PHAssetCollection.fetchAssetCollections(
            with: .album,
            subtype: .albumRegular,
            options: options
        ).firstObject
    }

    private func makeAssetChangeRequest(fileURL: URL) -> PHAssetChangeRequest? {
        if isVideoURL(fileURL) {
            return PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: fileURL)
        }
        return PHAssetChangeRequest.creationRequestForAssetFromImage(atFileURL: fileURL)
    }

    private func isVideoURL(_ url: URL) -> Bool {
        let ext = url.pathExtension.lowercased()
        return ["mov", "mp4", "m4v", "avi", "hevc"].contains(ext)
    }
}
