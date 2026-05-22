import Foundation
import Photos

@MainActor
enum SystemPhotoLibraryExportError: Error {
    case authorizationDenied
    case albumUnavailable
    case unsupportedMedia
}

@MainActor
final class SystemPhotoLibraryExportService {
    static let shared = SystemPhotoLibraryExportService()
    static let albumTitle = "LumaNox"

    private init() {}

    func export(fileURL: URL) async throws {
        let status = await requestPhotoAddAuthorization()
        guard status == .authorized || status == .limited else {
            throw SystemPhotoLibraryExportError.authorizationDenied
        }

        let album = try await album()
        let isVideo = isVideoURL(fileURL)
        var didCreateAsset = false

        try await PHPhotoLibrary.shared().performChanges {
            let assetRequest: PHAssetChangeRequest?
            if isVideo {
                assetRequest = PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: fileURL)
            } else {
                assetRequest = PHAssetChangeRequest.creationRequestForAssetFromImage(atFileURL: fileURL)
            }

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

    private func requestPhotoAddAuthorization() async -> PHAuthorizationStatus {
        let current = PHPhotoLibrary.authorizationStatus(for: .addOnly)
        guard current == .notDetermined else { return current }
        return await withCheckedContinuation { continuation in
            PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
                continuation.resume(returning: status)
            }
        }
    }

    private func album() async throws -> PHAssetCollection {
        if let existing = fetchAlbum() {
            return existing
        }

        var createdIdentifier: String?
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

    private func isVideoURL(_ url: URL) -> Bool {
        let ext = url.pathExtension.lowercased()
        return ["mov", "mp4", "m4v", "avi", "hevc"].contains(ext)
    }
}
