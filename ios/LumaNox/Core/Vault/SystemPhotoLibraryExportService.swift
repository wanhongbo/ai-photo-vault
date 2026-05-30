import Foundation
import Photos
import UIKit
import UniformTypeIdentifiers

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

    func export(fileURL: URL, skipWatermark: Bool = false) async throws {
        let readWriteStatus = await requestPhotoReadWriteAuthorization()
        let addOnlyStatus = PHPhotoLibrary.authorizationStatus(for: .addOnly)
        guard readWriteStatus == .authorized ||
                readWriteStatus == .limited ||
                addOnlyStatus == .authorized ||
                addOnlyStatus == .limited else {
            throw SystemPhotoLibraryExportError.authorizationDenied
        }

        let exportURL = watermarkedURLIfNeeded(fileURL, skipWatermark: skipWatermark)
        defer {
            if exportURL != fileURL {
                try? FileManager.default.removeItem(at: exportURL)
            }
        }

        do {
            try await exportToAlbum(fileURL: exportURL)
        } catch SystemPhotoLibraryExportError.unsupportedMedia {
            throw SystemPhotoLibraryExportError.unsupportedMedia
        } catch {
            try await exportToPhotoLibrary(fileURL: exportURL)
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

    private func watermarkedURLIfNeeded(_ fileURL: URL, skipWatermark: Bool) -> URL {
        guard !skipWatermark else { return fileURL }
        return (try? ImageWatermarkService.makeWatermarkedJPEGIfPossible(from: fileURL)) ?? fileURL
    }

    private func isVideoURL(_ url: URL) -> Bool {
        let ext = url.pathExtension.lowercased()
        return ["mov", "mp4", "m4v", "avi", "hevc"].contains(ext)
    }
}

enum ImageWatermarkService {
    private static let watermarkText = "LumaNox"
    private static let excludedExtensions: Set<String> = ["gif", "svg"]

    static func makeWatermarkedJPEGIfPossible(from sourceURL: URL) throws -> URL? {
        guard shouldWatermarkImage(at: sourceURL) else { return nil }

        let outputURL = try uniqueWatermarkURL(for: sourceURL)
        try renderWatermarkedJPEG(from: sourceURL, to: outputURL)
        return outputURL
    }

    static func shouldWatermarkImage(at url: URL) -> Bool {
        let ext = url.pathExtension.lowercased()
        guard !ext.isEmpty, !excludedExtensions.contains(ext) else { return false }

        if let type = UTType(filenameExtension: ext) {
            return type.conforms(to: .image) && !type.conforms(to: .movie)
        }

        return ["jpg", "jpeg", "png", "heic", "heif", "tif", "tiff", "webp", "bmp"].contains(ext)
    }

    private static func renderWatermarkedJPEG(from sourceURL: URL, to outputURL: URL) throws {
        guard let sourceImage = UIImage(contentsOfFile: sourceURL.path) else {
            throw CocoaError(.fileReadCorruptFile)
        }

        let pixelSize = imagePixelSize(sourceImage)
        guard pixelSize.width >= 4, pixelSize.height >= 4 else {
            throw CocoaError(.fileReadCorruptFile)
        }

        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true

        let renderer = UIGraphicsImageRenderer(size: pixelSize, format: format)
        let watermarked = renderer.image { context in
            sourceImage.draw(in: CGRect(origin: .zero, size: pixelSize))
            drawWatermark(in: context.cgContext, imageSize: pixelSize)
        }

        guard let data = watermarked.jpegData(compressionQuality: 0.92) else {
            throw CocoaError(.fileWriteUnknown)
        }

        try data.write(to: outputURL, options: .atomic)
    }

    private static func drawWatermark(in context: CGContext, imageSize: CGSize) {
        let width = imageSize.width
        let height = imageSize.height
        let fontSize = min(75, max(57, width * 0.037))
        let font = UIFont.systemFont(ofSize: fontSize, weight: .regular)
        let textColor = UIColor(red: 225 / 255, green: 232 / 255, blue: 245 / 255, alpha: 52 / 255)
        let shadow = NSShadow()
        shadow.shadowColor = UIColor.black.withAlphaComponent(32 / 255)
        shadow.shadowOffset = CGSize(width: 0, height: 1.8)
        shadow.shadowBlurRadius = 3.6

        let attributes: [NSAttributedString.Key: Any] = [
            .font: font,
            .foregroundColor: textColor,
            .shadow: shadow
        ]
        let attributed = NSAttributedString(string: watermarkText, attributes: attributes)
        let textSize = attributed.size()
        let padX = max(21, width * 0.02)
        let padY = max(21, height * 0.016)
        let origin = CGPoint(
            x: max(0, width - padX - textSize.width),
            y: max(0, height - padY - textSize.height)
        )

        UIGraphicsPushContext(context)
        attributed.draw(at: origin)
        UIGraphicsPopContext()
    }

    private static func imagePixelSize(_ image: UIImage) -> CGSize {
        if let cgImage = image.cgImage {
            return CGSize(width: cgImage.width, height: cgImage.height)
        }
        return CGSize(width: image.size.width * image.scale, height: image.size.height * image.scale)
    }

    private static func uniqueWatermarkURL(for sourceURL: URL) throws -> URL {
        let directory = sourceURL.deletingLastPathComponent()
        let base = sourceURL.deletingPathExtension().lastPathComponent
        let sanitizedBase = base.isEmpty ? UUID().uuidString : base

        for index in 0...999 {
            let suffix = index == 0 ? "_wm" : "_wm_\(index)"
            let candidate = directory.appendingPathComponent("\(sanitizedBase)\(suffix).jpg")
            if !FileManager.default.fileExists(atPath: candidate.path) {
                return candidate
            }
        }

        throw CocoaError(.fileWriteFileExists)
    }
}
