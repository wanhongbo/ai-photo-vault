import AVFoundation
import CryptoKit
import ImageIO
import UIKit

struct ThumbnailCacheDescriptor: Hashable {
    let sourceURL: URL
    let cacheKey: String
    let isVideo: Bool
    let targetPixelSize: CGFloat
}

final class ThumbnailService: @unchecked Sendable {
    static let shared = ThumbnailService()

    private let fileManager = FileManager.default
    private let memoryCache = NSCache<NSString, UIImage>()

    private init() {
        memoryCache.countLimit = 360
        memoryCache.totalCostLimit = 48 * 1024 * 1024
    }

    func thumbnail(
        encryptedPath: String,
        isVideo: Bool,
        targetPixelSize: CGFloat
    ) async -> UIImage? {
        guard !encryptedPath.isEmpty else { return nil }

        let descriptor = await makeDescriptor(
            encryptedPath: encryptedPath,
            isVideo: isVideo,
            targetPixelSize: targetPixelSize
        )
        guard fileManager.fileExists(atPath: descriptor.sourceURL.path) else { return nil }

        if let image = memoryCache.object(forKey: descriptor.cacheKey as NSString) {
            return image
        }

        if let image = readDiskCachedImage(for: descriptor.cacheKey) {
            insertIntoMemory(image, for: descriptor.cacheKey)
            return image
        }

        let image = await Task.detached(priority: .userInitiated) {
            descriptor.isVideo
                ? Self.makeVideoThumbnail(descriptor)
                : Self.makeImageThumbnail(descriptor)
        }.value

        guard let image else { return nil }
        insertIntoMemory(image, for: descriptor.cacheKey)
        writeDiskCachedImage(image, for: descriptor.cacheKey)
        return image
    }

    func invalidate(encryptedPath: String) {
        memoryCache.removeAllObjects()
        let descriptor = fallbackDescriptor(
            encryptedPath: encryptedPath,
            isVideo: false,
            targetPixelSize: 0
        )
        let pathToken = stableToken(descriptor.sourceURL.standardizedFileURL.path)
        guard let files = try? fileManager.contentsOfDirectory(
            at: cacheDirectory(),
            includingPropertiesForKeys: nil
        ) else { return }

        for file in files where file.lastPathComponent.contains(pathToken) {
            try? fileManager.removeItem(at: file)
        }
    }

    func clearAll() {
        memoryCache.removeAllObjects()
        try? fileManager.removeItem(at: cacheDirectory())
    }

    private func makeDescriptor(
        encryptedPath: String,
        isVideo: Bool,
        targetPixelSize: CGFloat
    ) async -> ThumbnailCacheDescriptor {
        await MainActor.run {
            let docs = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
            let record = VaultMetadataStore.shared.mediaRecord(forPath: encryptedPath)
            let sourceURL = record?.absoluteURL(documentsDirectory: docs) ?? URL(fileURLWithPath: encryptedPath)
            let storagePath = record?.storagePath ?? sourceURL.standardizedFileURL.path
            let encryptedHash = record?.encryptedSha256Hex ?? fileFingerprint(sourceURL)
            let mediaKind = record?.mediaKind.rawValue ?? (isVideo ? VaultMediaKind.video.rawValue : VaultMediaKind.image.rawValue)
            let pixel = max(120, Int(targetPixelSize.rounded()))
            let pathToken = stableToken(sourceURL.standardizedFileURL.path)
            let key = "\(pathToken)_\(stableToken("\(storagePath)|\(encryptedHash)|\(pixel)|\(mediaKind)"))"
            return ThumbnailCacheDescriptor(
                sourceURL: sourceURL,
                cacheKey: key,
                isVideo: isVideo,
                targetPixelSize: CGFloat(pixel)
            )
        }
    }

    private func fallbackDescriptor(
        encryptedPath: String,
        isVideo: Bool,
        targetPixelSize: CGFloat
    ) -> ThumbnailCacheDescriptor {
        let sourceURL = URL(fileURLWithPath: encryptedPath)
        let pixel = max(120, Int(targetPixelSize.rounded()))
        let pathToken = stableToken(sourceURL.standardizedFileURL.path)
        let key = "\(pathToken)_\(stableToken("\(sourceURL.path)|\(fileFingerprint(sourceURL))|\(pixel)|\(isVideo ? "video" : "image")"))"
        return ThumbnailCacheDescriptor(
            sourceURL: sourceURL,
            cacheKey: key,
            isVideo: isVideo,
            targetPixelSize: CGFloat(pixel)
        )
    }

    private func cacheDirectory() -> URL {
        fileManager.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("LumaNox/thumbs", isDirectory: true)
    }

    private func cachedFileURL(for key: String) -> URL {
        cacheDirectory().appendingPathComponent("\(key).jpg", isDirectory: false)
    }

    private func readDiskCachedImage(for key: String) -> UIImage? {
        let url = cachedFileURL(for: key)
        guard let data = try? Data(contentsOf: url),
              let image = UIImage(data: data) else { return nil }
        return image
    }

    private func writeDiskCachedImage(_ image: UIImage, for key: String) {
        guard let data = image.jpegData(compressionQuality: 0.82) ?? image.pngData() else { return }
        let directory = cacheDirectory()
        do {
            try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
            try applyProtection(to: directory)
            let target = cachedFileURL(for: key)
            try data.write(to: target, options: .atomic)
            try applyProtection(to: target)
        } catch {
            return
        }
    }

    private func insertIntoMemory(_ image: UIImage, for key: String) {
        let pixels = Int(image.size.width * image.scale * image.size.height * image.scale)
        memoryCache.setObject(image, forKey: key as NSString, cost: max(1, pixels * 4))
    }

    private func applyProtection(to url: URL) throws {
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var mutableURL = url
        try? mutableURL.setResourceValues(values)
        try? fileManager.setAttributes(
            [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
            ofItemAtPath: url.path
        )
    }

    private func stableToken(_ value: String) -> String {
        SHA256.hash(data: Data(value.utf8))
            .prefix(16)
            .map { String(format: "%02x", $0) }
            .joined()
    }

    private func fileFingerprint(_ url: URL) -> String {
        guard let values = try? url.resourceValues(forKeys: [.fileSizeKey, .contentModificationDateKey]) else {
            return "missing"
        }
        let size = values.fileSize ?? 0
        let modified = Int64((values.contentModificationDate ?? .distantPast).timeIntervalSince1970 * 1000)
        return "\(size)-\(modified)"
    }

    private static func makeImageThumbnail(_ descriptor: ThumbnailCacheDescriptor) -> UIImage? {
        guard let data = vaultReadableData(at: descriptor.sourceURL) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceShouldCacheImmediately: false,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: max(120, Int(descriptor.targetPixelSize)),
        ]
        if let source = CGImageSourceCreateWithData(data as CFData, nil),
           let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) {
            return UIImage(cgImage: cgImage)
        }
        return UIImage(data: data)
    }

    private static func makeVideoThumbnail(_ descriptor: ThumbnailCacheDescriptor) -> UIImage? {
        var tempURL: URL?
        do {
            let playbackURL: URL
            if let decrypted = try? PlaintextTempFileManager.shared.decryptVaultFileToTemporary(
                sourceURL: descriptor.sourceURL,
                scene: .videoThumbnail,
                preferredName: "\(UUID().uuidString).\(descriptor.sourceURL.pathExtension.isEmpty ? "mov" : descriptor.sourceURL.pathExtension)"
            ) {
                playbackURL = decrypted
                tempURL = decrypted
            } else {
                playbackURL = descriptor.sourceURL
            }
            defer {
                PlaintextTempFileManager.shared.removeItem(tempURL)
            }

            let asset = AVURLAsset(url: playbackURL)
            let generator = AVAssetImageGenerator(asset: asset)
            generator.appliesPreferredTrackTransform = true
            generator.maximumSize = CGSize(width: descriptor.targetPixelSize, height: descriptor.targetPixelSize)
            let cgImage = try generator.copyCGImage(at: CMTime(seconds: 0.15, preferredTimescale: 600), actualTime: nil)
            return UIImage(cgImage: cgImage)
        } catch {
            PlaintextTempFileManager.shared.removeItem(tempURL)
            return nil
        }
    }

    private static func vaultReadableData(at url: URL) -> Data? {
        if let decrypted = try? VaultCipher.shared.decryptFile(at: url) {
            return decrypted
        }

        guard let raw = try? Data(contentsOf: url) else { return nil }
        if CGImageSourceCreateWithData(raw as CFData, nil) != nil || UIImage(data: raw) != nil {
            return raw
        }
        return nil
    }
}
