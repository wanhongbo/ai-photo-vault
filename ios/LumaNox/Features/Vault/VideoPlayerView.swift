import AVKit
import SwiftUI

/// 解密保险箱视频并用 AVPlayer 播放（对齐 Android ExoPlayer + video_cache 流程）。
struct VideoPlayerView: View {
    @EnvironmentObject private var vaultStore: VaultStore
    @Environment(\.dismiss) private var dismiss

    let path: String
    var isTrash: Bool = false
    var onOpenAlbum: ((String) -> Void)? = nil

    @State private var player: AVPlayer?
    @State private var tempPlaybackURL: URL?
    @State private var loadError: String?
    @State private var isLoading = true
    @State private var showDelete = false
    @State private var showPurge = false
    @State private var showInfo = false

    var body: some View {
        LNScreenScaffold(title: L10n.tr("video_player_title"), onBack: { dismiss() }) {
            ZStack {
                RoundedRectangle(cornerRadius: LNRadius.homeCard)
                    .fill(LNColor.sectionBg)
                if isLoading {
                    ProgressView().tint(LNColor.brandBlue)
                } else if let player {
                    VideoPlayer(player: player)
                        .clipShape(RoundedRectangle(cornerRadius: LNRadius.homeCard))
                } else {
                    VStack(spacing: 8) {
                        Image(systemName: "exclamationmark.triangle")
                            .foregroundStyle(LNColor.error)
                        Text(loadError ?? L10n.tr("video_decrypt_failed"))
                            .font(LNTypography.bodyMedium())
                            .foregroundStyle(LNColor.subtitle)
                            .multilineTextAlignment(.center)
                    }
                    .padding()
                }
            }
            .frame(height: 280)

            if isTrash {
                HStack(spacing: 8) {
                    LNButton(title: L10n.tr("trash_recover"), variant: .secondary) {
                        Task { await restoreFromTrash() }
                    }
                    LNButton(title: L10n.tr("trash_delete"), variant: .danger) {
                        showPurge = true
                    }
                }
            } else {
                LNButton(title: L10n.tr("photo_viewer_info_title"), variant: .secondary) { showInfo = true }
                LNButton(title: L10n.tr("video_viewer_delete_title"), variant: .danger) {
                    showDelete = true
                }
            }
        }
        .task(id: path) {
            await preparePlayback()
        }
        .onDisappear {
            cleanupPlayback()
        }
        .onReceive(NotificationCenter.default.publisher(for: .AVPlayerItemFailedToPlayToEndTime)) { _ in
            loadError = L10n.tr("video_decrypt_failed")
            isLoading = false
            cleanupPlayback()
        }
        .overlay { dialogs }
    }

    @ViewBuilder
    private var dialogs: some View {
        if showDelete {
            LNDialog(
                title: L10n.tr("video_viewer_delete_title"),
                message: L10n.tr("video_viewer_delete_message"),
                confirmTitle: L10n.commonConfirm,
                dismissTitle: L10n.commonCancel,
                confirmVariant: .danger,
                onConfirm: {
                    showDelete = false
                    Task {
                        if await vaultStore.moveToTrash(path: path) { dismiss() }
                    }
                },
                onDismiss: { showDelete = false }
            )
        }
        if showPurge {
            LNDialog(
                title: L10n.tr("trash_purge_title"),
                message: L10n.tr("trash_purge_message"),
                confirmTitle: L10n.tr("trash_delete"),
                dismissTitle: L10n.commonCancel,
                confirmVariant: .danger,
                onConfirm: {
                    showPurge = false
                    Task {
                        if await vaultStore.purgeFromTrash(path: path) { dismiss() }
                    }
                },
                onDismiss: { showPurge = false }
            )
        }
        if showInfo {
            LNMediaInfoDialog(
                title: L10n.tr("photo_viewer_info_title"),
                items: infoItems(),
                confirmTitle: L10n.commonOk,
                onDismiss: { showInfo = false }
            )
        }
    }

    private func preparePlayback() async {
        cleanupPlayback()
        isLoading = true
        loadError = nil
        let source = URL(fileURLWithPath: path)
        let ext = source.pathExtension.isEmpty ? "mp4" : source.pathExtension
        let tempName = "play_\(UUID().uuidString).\(ext)"
        do {
            let tempURL = try await Task.detached(priority: .userInitiated) {
                try PlaintextTempFileManager.shared.decryptVaultFileToTemporary(
                    sourceURL: source,
                    scene: .videoPlayback,
                    preferredName: tempName
                )
            }.value
            tempPlaybackURL = tempURL
            let avPlayer = AVPlayer(url: tempURL)
            avPlayer.actionAtItemEnd = .none
            player = avPlayer
            avPlayer.play()
        } catch {
            loadError = error.localizedDescription
            cleanupPlayback()
        }
        isLoading = false
    }

    private func cleanupPlayback() {
        player?.pause()
        player = nil
        if let temp = tempPlaybackURL {
            PlaintextTempFileManager.shared.removeItem(temp)
            tempPlaybackURL = nil
        }
    }

    private func restoreFromTrash() async {
        if let album = await vaultStore.restoreFromTrash(path: path) {
            if let onOpenAlbum { onOpenAlbum(album) }
            dismiss()
        }
    }

    private func infoItems() -> [(String, String)] {
        let url = URL(fileURLWithPath: path)
        let record = VaultMetadataStore.shared.mediaRecord(forPath: path)
        return mediaInfoItems(
            fallbackURL: url,
            fallbackPath: path,
            record: record,
            fallbackKind: .video
        )
    }
}
