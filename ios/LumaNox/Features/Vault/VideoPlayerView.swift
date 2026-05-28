import AVFoundation
import SwiftUI
import UIKit

/// 解密保险箱视频并用 AVPlayer 播放（对齐 Android ExoPlayer + video_cache 流程）。
struct VideoPlayerView: View {
    @EnvironmentObject private var router: AppRouter
    @EnvironmentObject private var vaultStore: VaultStore
    @Environment(\.dismiss) private var dismiss

    let path: String
    var isTrash: Bool = false
    var source: PhotoViewerSource = .recent
    var onOpenAlbum: ((String) -> Void)? = nil

    @State private var player: AVPlayer?
    @State private var orderedItems: [LNMediaItem] = []
    @State private var tempPlaybackURL: URL?
    @State private var timeObserver: Any?
    @State private var loadError: String?
    @State private var isLoading = true
    @State private var isPlaying = false
    @State private var muted = true
    @State private var currentTime: Double = 0
    @State private var duration: Double = 0
    @State private var sliderTime: Double?
    @State private var seekFeedback: String?
    @State private var showDelete = false
    @State private var showPurge = false
    @State private var showInfo = false
    @State private var isPreparingShare = false
    @State private var isExportingSystem = false
    @State private var shareURL: URL?
    @State private var showShareSheet = false
    @State private var shareSheetLockToken: UUID?
    @State private var showShareFailure = false
    @State private var exportAlertMessage: String?
    @State private var showExportAlert = false

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                LNColor.bgBottom.ignoresSafeArea()

                VideoPlayerSurface(player: player)
                    .ignoresSafeArea()
                    .contentShape(Rectangle())
                    .onTapGesture { togglePlayback() }
                    .highPriorityGesture(mediaSwipeGesture)

                playerStateOverlay

                if !isLoading && player != nil {
                    VideoPlayerCenterButton(
                        isPlaying: isPlaying,
                        action: togglePlayback
                    )
                }

                if let seekFeedback {
                    Text(seekFeedback)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(LNColor.title)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(LNColor.navBarBg.opacity(0.78))
                        .clipShape(RoundedRectangle(cornerRadius: 13))
                        .transition(.opacity.combined(with: .scale(scale: 0.92)))
                }

                VideoPlayerTopChrome(
                    title: L10n.tr("video_player_title"),
                    topInset: proxy.safeAreaInsets.top,
                    onBack: { dismiss() }
                )

                if isTrash {
                    VideoPlayerTrashDock(
                        bottomInset: proxy.safeAreaInsets.bottom,
                        onRecover: { Task { await restoreFromTrash() } },
                        onDelete: { showPurge = true }
                    )
                } else {
                    VideoPlayerBottomChrome(
                        bottomInset: proxy.safeAreaInsets.bottom,
                        isPlaying: isPlaying,
                        muted: muted,
                        currentTime: $currentTime,
                        duration: duration,
                        sliderTime: $sliderTime,
                        isPreparingShare: isPreparingShare,
                        isExportingSystem: isExportingSystem,
                        onPlayPause: togglePlayback,
                        onSeek: seek,
                        onSeekRelative: seekRelative,
                        onMute: toggleMute,
                        onShare: prepareShare,
                        onExportSystem: exportToSystemPhotos,
                        onInfo: { showInfo = true },
                        onDelete: { showDelete = true }
                    )
                }
            }
        }
        .task(id: path) {
            await reloadOrderedItems()
            await preparePlayback()
        }
        .onDisappear {
            cleanupPlayback()
        }
        .onChange(of: muted) { _ in
            player?.volume = muted ? 0 : 1
        }
        .onReceive(NotificationCenter.default.publisher(for: .AVPlayerItemDidPlayToEndTime)) { notification in
            guard notification.object as? AVPlayerItem === player?.currentItem else { return }
            player?.seek(to: .zero)
            player?.play()
            isPlaying = true
        }
        .onReceive(NotificationCenter.default.publisher(for: .AVPlayerItemFailedToPlayToEndTime)) { _ in
            loadError = L10n.tr("video_decrypt_failed")
            isLoading = false
            cleanupPlayback()
        }
        .overlay { dialogs }
        .sheet(isPresented: $showShareSheet) {
            if let shareURL {
                VideoShareSheet(url: shareURL) {
                    PlaintextTempFileManager.shared.removeItem(shareURL)
                    self.shareURL = nil
                }
                .ignoresSafeArea()
            }
        }
        .onChange(of: showShareSheet) { isPresented in
            if !isPresented, let shareURL {
                PlaintextTempFileManager.shared.removeItem(shareURL)
                self.shareURL = nil
            }
            if !isPresented {
                AppLockManager.shared.endSystemInteraction(shareSheetLockToken)
                shareSheetLockToken = nil
            }
        }
        .alert(L10n.tr("photo_viewer_share_failed"), isPresented: $showShareFailure) {
            Button(L10n.commonOk, role: .cancel) {}
        }
        .alert(exportAlertMessage ?? "", isPresented: $showExportAlert) {
            Button(L10n.commonOk, role: .cancel) {}
        }
        .edgeSwipeBack { dismiss() }
        .accessibilityIdentifier("video_player_view")
    }

    @ViewBuilder
    private var playerStateOverlay: some View {
        if isLoading {
            ProgressView()
                .tint(LNColor.brandBlue)
                .scaleEffect(1.15)
        } else if player == nil {
            VStack(spacing: 10) {
                Image(systemName: "exclamationmark.triangle")
                    .font(.system(size: 30, weight: .semibold))
                    .foregroundStyle(LNColor.error)
                Text(loadError ?? L10n.tr("video_decrypt_failed"))
                    .font(LNTypography.bodyMedium())
                    .foregroundStyle(LNColor.subtitle)
                    .multilineTextAlignment(.center)
            }
            .padding(22)
            .frame(maxWidth: 300)
            .background(LNColor.navBarBg.opacity(0.72))
            .clipShape(RoundedRectangle(cornerRadius: LNRadius.homeCard))
            .overlay(
                RoundedRectangle(cornerRadius: LNRadius.homeCard)
                    .stroke(LNColor.strokeStrong, lineWidth: 1)
            )
        }
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
                        if await vaultStore.moveToTrash(path: path) {
                            await vaultStore.loadSnapshot()
                            dismiss()
                        }
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

    @MainActor
    private func preparePlayback() async {
        cleanupPlayback()
        isLoading = true
        loadError = nil
        currentTime = 0
        duration = 0
        sliderTime = nil

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
            avPlayer.volume = muted ? 0 : 1
            player = avPlayer
            addTimeObserver(to: avPlayer)
            avPlayer.play()
            isPlaying = true
        } catch {
            loadError = error.localizedDescription
            cleanupPlayback()
        }

        isLoading = false
    }

    private func cleanupPlayback() {
        if let timeObserver, let player {
            player.removeTimeObserver(timeObserver)
        }
        timeObserver = nil
        player?.pause()
        player = nil
        isPlaying = false
        if let temp = tempPlaybackURL {
            PlaintextTempFileManager.shared.removeItem(temp)
            tempPlaybackURL = nil
        }
    }

    private func addTimeObserver(to avPlayer: AVPlayer) {
        let interval = CMTime(seconds: 0.25, preferredTimescale: 600)
        timeObserver = avPlayer.addPeriodicTimeObserver(forInterval: interval, queue: .main) { time in
            guard sliderTime == nil else { return }
            let seconds = time.seconds
            currentTime = seconds.isFinite ? max(0, seconds) : 0
            if let durationSeconds = avPlayer.currentItem?.duration.seconds, durationSeconds.isFinite {
                duration = max(0, durationSeconds)
            }
            isPlaying = avPlayer.timeControlStatus == .playing
        }
    }

    private func togglePlayback() {
        guard let player else { return }
        if player.timeControlStatus == .playing {
            player.pause()
            isPlaying = false
        } else {
            player.play()
            isPlaying = true
        }
    }

    private func toggleMute() {
        muted.toggle()
        player?.volume = muted ? 0 : 1
    }

    private func seek(to seconds: Double) {
        guard let player else { return }
        let safeTime = seconds.coerced(to: 0...max(duration, 1))
        player.seek(to: CMTime(seconds: safeTime, preferredTimescale: 600))
        currentTime = safeTime
    }

    private func seekRelative(_ delta: Double) {
        let target = (sliderTime ?? currentTime) + delta
        seek(to: target)
        seekFeedback = delta >= 0 ? "+10s" : "-10s"
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.75) {
            seekFeedback = nil
        }
    }

    private func prepareShare() {
        guard !isPreparingShare else { return }
        player?.pause()
        isPlaying = false
        isPreparingShare = true
        Task {
            do {
                let url = try await makeShareURL()
                await MainActor.run {
                    shareURL = url
                    shareSheetLockToken = AppLockManager.shared.beginSystemInteraction(timeout: 300)
                    showShareSheet = true
                    isPreparingShare = false
                }
            } catch {
                await MainActor.run {
                    if let staleShareURL = shareURL {
                        PlaintextTempFileManager.shared.removeItem(staleShareURL)
                        shareURL = nil
                    }
                    isPreparingShare = false
                    showShareFailure = true
                }
            }
        }
    }

    private func exportToSystemPhotos() {
        guard !isExportingSystem else { return }
        player?.pause()
        isPlaying = false
        isExportingSystem = true
        Task {
            var tempURL: URL?
            defer {
                if let tempURL {
                    PlaintextTempFileManager.shared.removeItem(tempURL)
                }
            }

            do {
                let url = try await makeShareURL()
                tempURL = url
                try await SystemPhotoLibraryExportService.shared.export(fileURL: url)
                await MainActor.run {
                    exportAlertMessage = L10n.tr("photo_viewer_export_success")
                    showExportAlert = true
                    isExportingSystem = false
                }
            } catch SystemPhotoLibraryExportError.authorizationDenied {
                await MainActor.run {
                    exportAlertMessage = L10n.tr("photo_viewer_export_denied")
                    showExportAlert = true
                    isExportingSystem = false
                }
            } catch {
                await MainActor.run {
                    exportAlertMessage = L10n.tr("photo_viewer_export_failed")
                    showExportAlert = true
                    isExportingSystem = false
                }
            }
        }
    }

    private func makeShareURL() async throws -> URL {
        try await Task.detached(priority: .userInitiated) {
            let sourceURL = URL(fileURLWithPath: path)
            let name = sourceURL.lastPathComponent.isEmpty ? "LumaNox.mp4" : sourceURL.lastPathComponent
            return try PlaintextTempFileManager.shared.decryptVaultFileToTemporary(
                sourceURL: sourceURL,
                scene: .share,
                preferredName: name
            )
        }.value
    }

    private func restoreFromTrash() async {
        if let album = await vaultStore.restoreFromTrash(path: path) {
            if let onOpenAlbum { onOpenAlbum(album) }
            dismiss()
        }
    }

    private var currentIndex: Int? {
        orderedItems.firstIndex { $0.path == path }
    }

    private var mediaSwipeGesture: some Gesture {
        DragGesture(minimumDistance: 28, coordinateSpace: .local)
            .onEnded { value in
                let horizontal = value.translation.width
                let vertical = value.translation.height
                guard abs(horizontal) > 72, abs(horizontal) > abs(vertical) * 1.35 else { return }
                switchToAdjacentMedia(offset: horizontal < 0 ? 1 : -1)
            }
    }

    @MainActor
    private func reloadOrderedItems() async {
        let items = await source.loadMediaItems(isTrash: isTrash, vaultStore: vaultStore)
        let currentItem = items.first { $0.path == path } ?? Self.fallbackItem(path: path, isVideo: true)
        orderedItems = items.contains(where: { $0.path == path }) ? items : [currentItem] + items
    }

    private func switchToAdjacentMedia(offset: Int) {
        guard let currentIndex else { return }
        let targetIndex = currentIndex + offset
        guard orderedItems.indices.contains(targetIndex) else { return }

        let target = orderedItems[targetIndex]
        player?.pause()
        isPlaying = false

        if target.isVideo {
            router.replaceCurrentTab(with: .videoPlayer(path: target.path, isTrash: isTrash, source: source))
        } else {
            router.replaceCurrentTab(with: .photoViewer(path: target.path, isTrash: isTrash, source: source))
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

    private static func fallbackItem(path: String, isVideo: Bool) -> LNMediaItem {
        let url = URL(fileURLWithPath: path)
        return LNMediaItem(
            id: path,
            path: path,
            fileName: url.lastPathComponent,
            isVideo: isVideo,
            sizeLabel: "",
            createdAt: ""
        )
    }
}

private struct VideoPlayerTopChrome: View {
    let title: String
    let topInset: CGFloat
    let onBack: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            LinearGradient(
                colors: [LNColor.bgBottom.opacity(0.96), LNColor.bgBottom.opacity(0)],
                startPoint: .top,
                endPoint: .bottom
            )
            .overlay(alignment: .top) {
                HStack(spacing: 0) {
                    Button(action: onBack) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(LNColor.title)
                            .frame(width: LNSpacing.minTouchTarget, height: LNSpacing.minTouchTarget)
                            .background(LNColor.navBarBg.opacity(0.82))
                            .clipShape(RoundedRectangle(cornerRadius: LNRadius.topBarButton))
                    }
                    .buttonStyle(.lnPressable(scale: 0.94, pressedOpacity: 0.78))
                    .accessibilityLabel(L10n.commonBack)
                    .accessibilityIdentifier("video_player_back")

                    Text(title)
                        .font(LNTypography.titleMedium())
                        .foregroundStyle(LNColor.title)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .frame(maxWidth: .infinity)
                        .accessibilityAddTraits(.isHeader)

                    Color.clear.frame(width: LNSpacing.minTouchTarget, height: LNSpacing.minTouchTarget)
                }
                .padding(.top, topInset + 8)
                .padding(.horizontal, LNSpacing.screenHorizontal)
            }
            .frame(height: topInset + 104)
            Spacer()
        }
        .ignoresSafeArea()
    }
}

private struct VideoPlayerBottomChrome: View {
    let bottomInset: CGFloat
    let isPlaying: Bool
    let muted: Bool
    @Binding var currentTime: Double
    let duration: Double
    @Binding var sliderTime: Double?
    let isPreparingShare: Bool
    let isExportingSystem: Bool
    let onPlayPause: () -> Void
    let onSeek: (Double) -> Void
    let onSeekRelative: (Double) -> Void
    let onMute: () -> Void
    let onShare: () -> Void
    let onExportSystem: () -> Void
    let onInfo: () -> Void
    let onDelete: () -> Void

    var body: some View {
        VStack {
            Spacer()
            LinearGradient(
                colors: [LNColor.bgBottom.opacity(0), LNColor.bgBottom.opacity(0.94), LNColor.bgBottom],
                startPoint: .top,
                endPoint: .bottom
            )
            .overlay(alignment: .bottom) {
                VStack(spacing: 12) {
                    playbackControls
                    actionDock
                }
                .padding(.horizontal, LNSpacing.screenHorizontal)
                .padding(.bottom, bottomInset + 14)
            }
            .frame(height: bottomInset + 230)
        }
        .ignoresSafeArea()
    }

    private var playbackControls: some View {
        HStack(spacing: 10) {
            VideoIconButton(
                systemImage: isPlaying ? "pause.fill" : "play.fill",
                title: isPlaying ? L10n.tr("video_player_pause") : L10n.tr("video_player_play"),
                action: onPlayPause
            )

            Text(VideoPlayerFormat.duration(sliderTime ?? currentTime))
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(LNColor.title.opacity(0.9))
                .monospacedDigit()
                .frame(width: 42, alignment: .leading)

            Slider(
                value: Binding(
                    get: { sliderTime ?? currentTime },
                    set: { sliderTime = $0 }
                ),
                in: 0...max(duration, 1),
                onEditingChanged: { isEditing in
                    if !isEditing {
                        let target = sliderTime ?? currentTime
                        onSeek(target)
                        sliderTime = nil
                    }
                }
            )
            .tint(LNColor.title.opacity(0.88))

            Text(VideoPlayerFormat.duration(duration))
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(LNColor.title.opacity(0.9))
                .monospacedDigit()
                .frame(width: 42, alignment: .trailing)

            VideoIconButton(
                systemImage: muted ? "speaker.slash.fill" : "speaker.wave.2.fill",
                title: muted ? L10n.tr("video_player_unmute") : L10n.tr("video_player_mute"),
                action: onMute
            )
        }
        .padding(.horizontal, 12)
        .frame(height: 52)
        .background(LNColor.navBarBg.opacity(0.74))
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(LNColor.strokeStrong, lineWidth: 1))
        .overlay(alignment: .top) {
            HStack {
                Button { onSeekRelative(-10) } label: {
                    Text("-10")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(LNColor.subtitle)
                        .frame(width: 42, height: 28)
                }
                .buttonStyle(.lnPressable(scale: 0.94, pressedOpacity: 0.75))
                .accessibilityLabel(L10n.tr("video_player_rewind_10"))

                Spacer()

                Button { onSeekRelative(10) } label: {
                    Text("+10")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(LNColor.subtitle)
                        .frame(width: 42, height: 28)
                }
                .buttonStyle(.lnPressable(scale: 0.94, pressedOpacity: 0.75))
                .accessibilityLabel(L10n.tr("video_player_forward_10"))
            }
            .padding(.horizontal, 54)
            .offset(y: -30)
        }
    }

    private var actionDock: some View {
        HStack(spacing: 4) {
            VideoDockButton(
                title: L10n.tr("photo_viewer_share"),
                systemImage: isPreparingShare ? "hourglass" : "square.and.arrow.up",
                foreground: LNColor.title,
                action: onShare
            )
            VideoDockButton(
                title: L10n.tr("photo_viewer_export_system"),
                systemImage: isExportingSystem ? "hourglass" : "square.and.arrow.down",
                foreground: LNColor.title,
                action: onExportSystem
            )
            VideoDockButton(
                title: L10n.tr("photo_viewer_info"),
                systemImage: "info.circle",
                foreground: LNColor.title,
                action: onInfo
            )
            VideoDockButton(
                title: L10n.tr("photo_viewer_delete"),
                systemImage: "trash",
                foreground: LNColor.buttonDangerFg,
                action: onDelete
            )
        }
        .padding(6)
        .frame(height: 78)
        .background(LNColor.navBarBg.opacity(0.84))
        .clipShape(RoundedRectangle(cornerRadius: 26))
        .overlay(RoundedRectangle(cornerRadius: 26).stroke(LNColor.strokeStrong, lineWidth: 1))
    }
}

private struct VideoPlayerTrashDock: View {
    let bottomInset: CGFloat
    let onRecover: () -> Void
    let onDelete: () -> Void

    var body: some View {
        VStack {
            Spacer()
            LinearGradient(
                colors: [LNColor.bgBottom.opacity(0), LNColor.bgBottom.opacity(0.94), LNColor.bgBottom],
                startPoint: .top,
                endPoint: .bottom
            )
            .overlay(alignment: .bottom) {
                HStack(spacing: 12) {
                    LNButton(title: L10n.tr("trash_recover"), variant: .secondary, action: onRecover)
                    LNButton(title: L10n.tr("trash_delete"), variant: .danger, action: onDelete)
                }
                .padding(.horizontal, LNSpacing.screenHorizontal)
                .padding(.bottom, bottomInset + 16)
            }
            .frame(height: bottomInset + 144)
        }
        .ignoresSafeArea()
    }
}

private struct VideoPlayerCenterButton: View {
    let isPlaying: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                .font(.system(size: 30, weight: .bold))
                .foregroundStyle(LNColor.title.opacity(isPlaying ? 0.22 : 0.96))
                .frame(width: 72, height: 72)
                .background(LNColor.navBarBg.opacity(isPlaying ? 0.06 : 0.66))
                .clipShape(Circle())
        }
        .buttonStyle(.lnPressable(scale: 0.94, pressedOpacity: 0.72))
        .accessibilityLabel(isPlaying ? L10n.tr("video_player_pause") : L10n.tr("video_player_play"))
    }
}

private struct VideoIconButton: View {
    let systemImage: String
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(LNColor.title)
                .frame(width: LNSpacing.minTouchTarget, height: LNSpacing.minTouchTarget)
        }
        .buttonStyle(.lnPressable(scale: 0.92, pressedOpacity: 0.72))
        .accessibilityLabel(title)
    }
}

private struct VideoDockButton: View {
    let title: String
    let systemImage: String
    let foreground: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 5) {
                Image(systemName: systemImage)
                    .font(.system(size: 21, weight: .semibold))
                Text(title)
                    .font(.system(size: 11, weight: .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.76)
            }
            .foregroundStyle(foreground)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .clipShape(RoundedRectangle(cornerRadius: 21))
        }
        .buttonStyle(.lnPressable(scale: 0.94, pressedOpacity: 0.76))
        .accessibilityLabel(title)
    }
}

private struct VideoPlayerSurface: UIViewRepresentable {
    let player: AVPlayer?

    func makeUIView(context: Context) -> PlayerLayerView {
        let view = PlayerLayerView()
        view.playerLayer.videoGravity = .resizeAspect
        view.playerLayer.backgroundColor = UIColor.black.cgColor
        return view
    }

    func updateUIView(_ uiView: PlayerLayerView, context: Context) {
        uiView.playerLayer.player = player
    }
}

private final class PlayerLayerView: UIView {
    override static var layerClass: AnyClass { AVPlayerLayer.self }

    var playerLayer: AVPlayerLayer {
        layer as! AVPlayerLayer
    }
}

private struct VideoShareSheet: UIViewControllerRepresentable {
    let url: URL
    let onComplete: () -> Void

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        controller.completionWithItemsHandler = { _, _, _, _ in
            DispatchQueue.main.async {
                onComplete()
            }
        }
        return controller
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

private enum VideoPlayerFormat {
    static func duration(_ seconds: Double) -> String {
        guard seconds.isFinite else { return "00:00" }
        let totalSeconds = Int(max(0, seconds).rounded(.down))
        let hours = totalSeconds / 3600
        let minutes = (totalSeconds % 3600) / 60
        let secs = totalSeconds % 60
        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, secs)
        }
        return String(format: "%02d:%02d", minutes, secs)
    }
}

private extension Comparable {
    func coerced(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
