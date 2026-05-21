import AVFoundation
import ImageIO
import SwiftUI
import UIKit

private enum VaultThumbnailLoadState {
    case loading
    case ready(UIImage)
    case failed
}

@MainActor
private enum VaultThumbnailMemoryCache {
    static let shared = NSCache<NSString, UIImage>()

    static func image(for key: String) -> UIImage? {
        shared.object(forKey: key as NSString)
    }

    static func insert(_ image: UIImage, for key: String) {
        shared.setObject(image, forKey: key as NSString)
    }
}

/// 解密保险箱缩略图用于查看器预览。
struct VaultThumbnailView: View {
    let encryptedPath: String

    var body: some View {
        VaultMediaThumbnailView(
            encryptedPath: encryptedPath,
            isVideo: false,
            contentMode: .fit,
            targetPixelSize: 1200
        )
    }
}

struct VaultMediaThumbnailView: View {
    let encryptedPath: String
    let isVideo: Bool
    var contentMode: ContentMode = .fill
    var targetPixelSize: CGFloat = 360

    @State private var state: VaultThumbnailLoadState = .loading

    var body: some View {
        ZStack {
            LNColor.sectionBg

            switch state {
            case .loading:
                ProgressView().tint(LNColor.brandBlue)
            case .ready(let image):
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()
            case .failed:
                Image(systemName: isVideo ? "video.fill" : "photo")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(LNColor.subtitle)
            }

            if isVideo {
                Image(systemName: "play.fill")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(LNColor.title)
                    .padding(7)
                    .background(.black.opacity(0.42))
                    .clipShape(Circle())
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                    .padding(7)
            }
        }
        .task(id: cacheKey) {
            await loadThumbnail()
        }
        .accessibilityHidden(true)
    }

    private var cacheKey: String {
        "\(encryptedPath)|\(isVideo ? "v" : "i")|\(Int(targetPixelSize))"
    }

    private func loadThumbnail() async {
        guard !encryptedPath.isEmpty else {
            state = .failed
            return
        }
        if let cached = VaultThumbnailMemoryCache.image(for: cacheKey) {
            state = .ready(cached)
            return
        }
        let url = URL(fileURLWithPath: encryptedPath)
        guard FileManager.default.fileExists(atPath: url.path) else {
            state = .failed
            return
        }
        state = .loading
        let pixelSize = targetPixelSize
        let image = await Task.detached(priority: .userInitiated) {
            isVideo
                ? makeVideoThumbnail(encryptedURL: url, targetPixelSize: pixelSize)
                : makeImageThumbnail(encryptedURL: url, targetPixelSize: pixelSize)
        }.value
        guard let image else {
            state = .failed
            return
        }
        VaultThumbnailMemoryCache.insert(image, for: cacheKey)
        state = .ready(image)
    }
}

private func makeImageThumbnail(encryptedURL: URL, targetPixelSize: CGFloat) -> UIImage? {
    guard let data = vaultReadableData(at: encryptedURL) else { return nil }
    let options: [CFString: Any] = [
        kCGImageSourceCreateThumbnailFromImageAlways: true,
        kCGImageSourceShouldCacheImmediately: false,
        kCGImageSourceCreateThumbnailWithTransform: true,
        kCGImageSourceThumbnailMaxPixelSize: max(120, Int(targetPixelSize)),
    ]
    if let source = CGImageSourceCreateWithData(data as CFData, nil),
       let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) {
        return UIImage(cgImage: cgImage)
    }
    return UIImage(data: data)
}

private func makeVideoThumbnail(encryptedURL: URL, targetPixelSize: CGFloat) -> UIImage? {
    var tempURL: URL?
    do {
        let playbackURL: URL
        if let decrypted = try? PlaintextTempFileManager.shared.decryptVaultFileToTemporary(
            sourceURL: encryptedURL,
            scene: .videoThumbnail,
            preferredName: "\(UUID().uuidString).\(encryptedURL.pathExtension.isEmpty ? "mov" : encryptedURL.pathExtension)"
        ) {
            playbackURL = decrypted
            tempURL = decrypted
        } else {
            playbackURL = encryptedURL
        }
        defer {
            PlaintextTempFileManager.shared.removeItem(tempURL)
        }

        let asset = AVURLAsset(url: playbackURL)
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: targetPixelSize, height: targetPixelSize)
        let cgImage = try generator.copyCGImage(at: CMTime(seconds: 0.15, preferredTimescale: 600), actualTime: nil)
        return UIImage(cgImage: cgImage)
    } catch {
        PlaintextTempFileManager.shared.removeItem(tempURL)
        return nil
    }
}

private func vaultReadableData(at url: URL) -> Data? {
    if let decrypted = try? VaultCipher.shared.decryptFile(at: url) {
        return decrypted
    }

    guard let raw = try? Data(contentsOf: url) else { return nil }
    if CGImageSourceCreateWithData(raw as CFData, nil) != nil || UIImage(data: raw) != nil {
        return raw
    }
    return nil
}
