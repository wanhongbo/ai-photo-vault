import SwiftUI
import UIKit

private enum VaultThumbnailLoadState {
    case loading
    case ready(UIImage)
    case failed
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
        state = .loading
        let image = await ThumbnailService.shared.thumbnail(
            encryptedPath: encryptedPath,
            isVideo: isVideo,
            targetPixelSize: targetPixelSize
        )
        guard let image else {
            state = .failed
            return
        }
        state = .ready(image)
    }
}
