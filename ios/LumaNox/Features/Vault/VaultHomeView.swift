import Photos
import PhotosUI
import SwiftUI
import UIKit

struct VaultHomeView: View {
    @EnvironmentObject private var router: AppRouter
    @ObservedObject private var viewModel: VaultHomeViewModel
    @State private var visibleImportToast: VaultHomeImportToast?
    @State private var importToastDismissTask: Task<Void, Never>?

    init(viewModel: VaultHomeViewModel) {
        self.viewModel = viewModel
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            vaultHomeBackground
                .ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 20) {
                    heroCard

                    if !viewModel.hasPinConfigured {
                        pinSetupBanner
                    }

                    statusMessage

                    if viewModel.showPermissionDenied {
                        permissionCard
                    } else if viewModel.shouldShowInitialLoading {
                        loadingVaultCard
                    } else if viewModel.shouldShowEmptyState {
                        emptyVaultCard
                    } else {
                        albumsSection
                    }
                }
                .padding(.horizontal, LNSpacing.screenHorizontal)
                .padding(.top, 10)
                .padding(.bottom, 28)
            }

            if let visibleImportToast {
                VaultHomeImportToastView(toast: visibleImportToast)
                    .padding(.bottom, 20)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .zIndex(2)
            }
        }
        .onAppear { viewModel.onAppear() }
        .onDisappear {
            importToastDismissTask?.cancel()
            importToastDismissTask = nil
        }
        .onChange(of: viewModel.pickerItems) { _ in
            _ = viewModel.onPickerItemsChanged(router: router)
        }
        .onReceive(viewModel.$importToast.compactMap { $0 }) { toast in
            showImportToast(toast)
        }
        .overlay {
            if viewModel.showCreateAlbum {
                LNInputDialog(
                    title: L10n.homeAlbumCreateTitle,
                    text: $viewModel.newAlbumName,
                    placeholder: L10n.homeAlbumCreateHint,
                    confirmTitle: L10n.homeAlbumCreateConfirm,
                    dismissTitle: L10n.commonCancel,
                    onConfirm: { viewModel.createAlbum(router: router) },
                    onDismiss: { viewModel.showCreateAlbum = false }
                )
            }
            if let duplicateMessage = viewModel.duplicateImportDialogMessage {
                LNDialog(
                    title: L10n.tr("home_import_duplicate_dialog_title"),
                    message: duplicateMessage,
                    confirmTitle: L10n.commonOk,
                    onConfirm: { viewModel.duplicateImportDialogMessage = nil }
                )
            }
            if let count = viewModel.pendingImportOriginalsCount {
                ImportOriginalsDecisionSheet(
                    selectionCount: count,
                    rememberChoice: $viewModel.rememberImportOriginalsChoice,
                    onKeepOriginals: { viewModel.confirmPendingImportOriginals(action: .keepOriginals) },
                    onDeleteOriginals: { viewModel.confirmPendingImportOriginals(action: .deleteOriginals) }
                )
            }
            if let message = viewModel.originalsActionDialogMessage {
                LNDialog(
                    title: L10n.tr("import_originals_result_title"),
                    message: message,
                    confirmTitle: L10n.commonOk,
                    onConfirm: { viewModel.originalsActionDialogMessage = nil }
                )
            }
        }
        .accessibilityIdentifier("vault_home_view")
    }

    private var vaultHomeBackground: some View {
        ZStack(alignment: .top) {
            LinearGradient(
                gradient: Gradient(stops: [
                    .init(color: Color(hex: 0x0E233A), location: 0.00),
                    .init(color: Color(hex: 0x0B1D31), location: 0.24),
                    .init(color: Color(hex: 0x071423), location: 0.54),
                    .init(color: Color(hex: 0x05080D), location: 1.00),
                ]),
                startPoint: .top,
                endPoint: .bottom
            )

            LinearGradient(
                gradient: Gradient(stops: [
                    .init(color: Color(hex: 0x123A5E).opacity(0.18), location: 0.00),
                    .init(color: Color(hex: 0x0B2740).opacity(0.12), location: 0.34),
                    .init(color: Color(hex: 0x071827).opacity(0.07), location: 0.62),
                    .init(color: Color.clear, location: 1.00),
                ]),
                startPoint: .top,
                endPoint: .bottom
            )
            .allowsHitTesting(false)
        }
    }

    private var heroCard: some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 6) {
                Text(L10n.appName)
                    .font(.system(size: 28, weight: .heavy))
                    .foregroundStyle(Color(hex: 0xF2F6FF))
                    .lineLimit(1)
                Text(viewModel.heroStatusText)
                    .font(LNTypography.labelMedium())
                    .foregroundStyle(Color(hex: 0x9BAEC8))
                    .lineLimit(1)
                    .minimumScaleFactor(0.82)
            }
            .frame(width: 230, alignment: .leading)
            .padding(.leading, 20)
            .padding(.top, 34)

            HStack(spacing: 8) {
                HomeHeroBadge(icon: "wifi.slash", title: L10n.tr("home_hero_badge_offline"), width: 84)
                HomeHeroBadge(icon: "lock.shield", title: L10n.tr("home_hero_badge_encrypted"), width: 104)
                HomeHeroBadge(
                    icon: "sparkles",
                    title: L10n.tr("home_hero_badge_ai_local"),
                    width: 128
                )
            }
            .padding(.leading, 20)
            .padding(.top, 111)

            HStack {
                Spacer()
                AppIconHeroView()
            }
            .padding(.top, 2)
            .padding(.trailing, 12)

        }
        .frame(height: 163)
        .frame(maxWidth: .infinity, alignment: .leading)
        .homeGlassCard(cornerRadius: 28)
        .shadow(color: .black.opacity(0.40), radius: 30, x: 0, y: 18)
    }

    private var loadingVaultCard: some View {
        HStack(spacing: 10) {
            ProgressView()
                .tint(LNColor.brandBlue)
            Text(L10n.commonLoading)
                .font(LNTypography.bodyMedium())
                .foregroundStyle(LNColor.subtitle)
            Spacer()
        }
        .padding(.vertical, 18)
        .padding(.horizontal, LNSpacing.cardPadding)
        .homeGlassCard(cornerRadius: 24)
    }

    private var pinSetupBanner: some View {
        Button { router.pushSettings(.changePin) } label: {
            HStack(spacing: 12) {
                Text(L10n.tr("home_pin_banner_message"))
                    .font(LNTypography.labelMedium())
                    .foregroundStyle(LNColor.title)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(L10n.tr("home_pin_banner_action"))
                    .font(LNTypography.labelMedium().weight(.semibold))
                    .foregroundStyle(LNColor.navItemActive)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .homeGlassCard(cornerRadius: LNRadius.homeCard, stroke: LNColor.error.opacity(0.45))
        }
        .buttonStyle(.lnPressable())
    }

    @ViewBuilder
    private var statusMessage: some View {
        if viewModel.isImporting {
            HStack(spacing: 8) {
                ProgressView().tint(LNColor.brandBlue)
                Text(L10n.commonLoading)
                    .font(LNTypography.bodyMedium())
                    .foregroundStyle(LNColor.subtitle)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var permissionCard: some View {
        VStack(spacing: 16) {
            Image(systemName: "photo.on.rectangle.angled")
                .font(.system(size: 34, weight: .semibold))
                .foregroundStyle(LNColor.brandBlue)
                .frame(width: 78, height: 78)
                .background(LNColor.emptyIconBg)
                .clipShape(RoundedRectangle(cornerRadius: LNRadius.vaultEmptyIconWrap))
            Text(L10n.tr("home_permission_title"))
                .font(LNTypography.headlineMedium())
                .foregroundStyle(LNColor.title)
            Text(L10n.tr("home_permission_denied_desc"))
                .font(LNTypography.bodyLarge())
                .foregroundStyle(LNColor.subtitle)
                .multilineTextAlignment(.center)
            LNButton(title: L10n.tr("home_permission_settings"), variant: .primary) {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
        }
        .padding(.vertical, 28)
        .padding(.horizontal, LNSpacing.cardPadding)
        .homeGlassCard(cornerRadius: 24)
    }

    private var emptyVaultCard: some View {
        VStack(spacing: 16) {
            Image(systemName: "lock.shield")
                .font(.system(size: 36, weight: .semibold))
                .foregroundStyle(LNColor.brandBlue)
                .frame(width: 96, height: 96)
                .background(LNColor.emptyIconBg)
                .clipShape(RoundedRectangle(cornerRadius: LNRadius.vaultEmptyIconWrap))
            Text(L10n.homeEmptyTitle)
                .font(LNTypography.headlineMedium())
                .foregroundStyle(LNColor.title)
            Text(L10n.homeEmptyDesc)
                .font(LNTypography.bodyLarge())
                .foregroundStyle(LNColor.subtitle)
                .multilineTextAlignment(.center)
            PhotosPicker(
                selection: $viewModel.pickerItems,
                maxSelectionCount: 32,
                matching: .any(of: [.images, .videos]),
                photoLibrary: PHPhotoLibrary.shared()
            ) {
                Text(L10n.homeEmptyAction)
                    .font(LNTypography.button())
                    .foregroundStyle(LNColor.buttonPrimaryFg)
                    .frame(maxWidth: .infinity)
                    .frame(height: LNSpacing.buttonHeightPrimary)
                    .background(LNColor.buttonPrimaryBg)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
            }
            .appLockSystemInteraction()
            .buttonStyle(.lnPressable(scale: 0.985, pressedOpacity: 0.88))
            Button { router.openPrivateCamera() } label: {
                Text(L10n.tr("home_camera_empty_action"))
                    .font(LNTypography.button())
                    .foregroundStyle(LNColor.title)
                    .frame(maxWidth: .infinity)
                    .frame(height: LNSpacing.buttonHeightPrimary)
                    .background(LNColor.buttonSecondaryBg)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
            }
            .buttonStyle(.lnPressable())
        }
        .padding(.vertical, 32)
        .padding(.horizontal, LNSpacing.cardPadding)
        .homeGlassCard(cornerRadius: 24)
    }

    private var recentSection: some View {
        HomeSectionCard(
            title: L10n.recentListTitle,
            actionTitle: L10n.tr("home_section_all"),
            actionIdentifier: "home_recent_view_more",
            action: { router.pushVault(.recentList) }
        ) {
            GeometryReader { proxy in
                let cardWidth = floor((proxy.size.width - 20) / 3)
                HStack(spacing: 10) {
                    ForEach(Array(viewModel.recentPhotos.prefix(3))) { item in
                        RecentMediaCard(item: item, width: cardWidth) {
                            open(item)
                        }
                    }
                }
            }
            .frame(height: 122)
            .accessibilityIdentifier("vault_recent_grid")
        }
    }

    private var albumsSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(L10n.albumListTitle)
                .font(.system(size: 20, weight: .heavy))
                .foregroundStyle(Color(hex: 0xF6F9FF))
            .frame(height: 34)

            GeometryReader { proxy in
                let cardWidth = floor((proxy.size.width - 10) / 2)
                LazyVGrid(columns: albumGridColumns(cardWidth: cardWidth), alignment: .leading, spacing: 10) {
                    ForEach(viewModel.albums) { album in
                        AlbumHomeCard(
                            album: album,
                            mediaItems: viewModel.recentMediaItems(for: album),
                            width: cardWidth
                        ) {
                            open(album)
                        }
                    }
                    CreateAlbumHomeCard(width: cardWidth) {
                        viewModel.showCreateAlbum = true
                    }
                }
                .frame(width: proxy.size.width, alignment: .topLeading)
            }
            .frame(height: albumGridHeight)
        }
        .padding(16)
        .homeGlassCard(cornerRadius: 26, stroke: LNColor.stroke)
        .shadow(color: .black.opacity(0.33), radius: 24, x: 0, y: 16)
        .accessibilityIdentifier("vault_album_grid_section")
    }

    private func open(_ item: LNMediaItem) {
        if item.isVideo {
            router.pushVault(.videoPlayer(path: item.path))
        } else {
            router.pushVault(.photoViewer(path: item.path, isTrash: false, source: .recent))
        }
    }

    private func open(_ album: VaultAlbum) {
        if let category = album.aiCategory {
            router.pushVault(.aiClassifyDetail(category: category))
        } else {
            router.pushVault(.album(name: album.name))
        }
    }

    private func albumGridColumns(cardWidth: CGFloat) -> [GridItem] {
        [
            GridItem(.fixed(cardWidth), spacing: 10),
            GridItem(.fixed(cardWidth), spacing: 10),
        ]
    }

    private var albumGridHeight: CGFloat {
        let itemCount = viewModel.albums.count + 1
        let rowCount = max(1, Int(ceil(Double(itemCount) / 2.0)))
        return CGFloat(rowCount) * AlbumHomeLayout.tileHeight + CGFloat(rowCount - 1) * 10
    }

    private func showImportToast(_ toast: VaultHomeImportToast) {
        importToastDismissTask?.cancel()
        withAnimation(.easeInOut(duration: 0.2)) {
            visibleImportToast = toast
        }
        importToastDismissTask = Task {
            try? await Task.sleep(nanoseconds: 2_400_000_000)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                guard visibleImportToast?.id == toast.id else { return }
                withAnimation(.easeInOut(duration: 0.2)) {
                    visibleImportToast = nil
                }
            }
        }
    }
}

private struct VaultHomeImportToastView: View {
    let toast: VaultHomeImportToast

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: toast.isError ? "exclamationmark.circle" : "checkmark")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(toast.isError ? LNColor.error : LNColor.success)
            Text(toast.message)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(LNColor.title)
                .lineLimit(2)
                .minimumScaleFactor(0.9)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 16)
        .frame(width: 301, alignment: .leading)
        .frame(minHeight: 48)
        .background(Color(hex: 0x101722).opacity(0.93))
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke((toast.isError ? LNColor.error : LNColor.success).opacity(0.33), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.40), radius: 28, x: 0, y: 14)
        .accessibilityIdentifier("vault_import_toast")
    }
}

private struct ImportOriginalsDecisionSheet: View {
    let selectionCount: Int
    @Binding var rememberChoice: Bool
    let onKeepOriginals: () -> Void
    let onDeleteOriginals: () -> Void

    var body: some View {
        ZStack {
            LNColor.scrim.ignoresSafeArea()
            VStack(spacing: 14) {
                Text(L10n.tr("import_originals_sheet_title"))
                    .font(.system(size: 25, weight: .bold))
                    .foregroundStyle(LNColor.title)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                Text(L10n.tr("import_originals_sheet_message", selectionCount))
                    .font(LNTypography.bodyLarge())
                    .foregroundStyle(LNColor.subtitle)
                    .lineSpacing(2)
                    .multilineTextAlignment(.center)

                importFlow
                deleteNote
                rememberRow

                HStack(spacing: 10) {
                    LNButton(
                        title: L10n.tr("import_originals_keep_action"),
                        variant: .secondary,
                        action: onKeepOriginals
                    )
                    LNButton(
                        title: L10n.tr("import_originals_delete_action"),
                        variant: .danger,
                        action: onDeleteOriginals
                    )
                }
            }
            .frame(maxWidth: 361)
            .padding(.top, 22)
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
            .background(LNColor.dialogBg)
            .clipShape(RoundedRectangle(cornerRadius: 26))
            .overlay(
                RoundedRectangle(cornerRadius: 26)
                    .stroke(LNColor.stroke, lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.35), radius: 28, x: 0, y: 14)
            .padding(.horizontal, LNSpacing.screenHorizontal)
        }
        .accessibilityIdentifier("import_originals_decision_sheet")
    }

    private var importFlow: some View {
        HStack(spacing: 18) {
            flowItem(icon: "photo.on.rectangle", title: L10n.tr("import_originals_photos_label"), tint: LNColor.amberWarning)
            Image(systemName: "arrow.right")
                .font(.system(size: 27, weight: .semibold))
                .foregroundStyle(LNColor.title)
            flowItem(icon: "lock.shield", title: L10n.appName, tint: LNColor.brandBlue)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 2)
    }

    private func flowItem(icon: String, title: String, tint: Color) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 25, weight: .semibold))
                .foregroundStyle(tint)
                .frame(width: 52, height: 52)
                .background(tint.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 15))
                .overlay(RoundedRectangle(cornerRadius: 15).stroke(tint.opacity(0.45), lineWidth: 1))
            Text(title)
                .font(LNTypography.labelMedium().weight(.semibold))
                .foregroundStyle(LNColor.subtitle)
        }
        .frame(width: 82)
    }

    private var deleteNote: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "info.circle")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(LNColor.brandBlue)
            Text(L10n.tr("import_originals_delete_note"))
                .font(LNTypography.labelMedium())
                .foregroundStyle(LNColor.subtitle)
                .lineSpacing(2)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(LNColor.sectionBg.opacity(0.8))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(LNColor.stroke, lineWidth: 1))
    }

    private var rememberRow: some View {
        Button {
            rememberChoice.toggle()
        } label: {
            HStack(spacing: 12) {
                Image(systemName: rememberChoice ? "checkmark.square.fill" : "square")
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(rememberChoice ? LNColor.brandBlue : LNColor.subtitle)
                Text(L10n.tr("import_originals_remember"))
                    .font(LNTypography.titleMedium())
                    .foregroundStyle(LNColor.title)
                Spacer(minLength: 0)
            }
            .frame(height: 48)
            .padding(.horizontal, 14)
            .background(LNColor.sectionBg.opacity(0.55))
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(LNColor.stroke, lineWidth: 1))
        }
        .buttonStyle(.lnPressable())
        .accessibilityIdentifier("import_originals_remember_choice")
    }
}

private struct HomeHeroBadge: View {
    let icon: String
    let title: String
    let width: CGFloat

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 12, weight: .medium))
            Text(title)
                .font(LNTypography.labelMedium())
                .lineLimit(1)
                .minimumScaleFactor(0.78)
        }
        .foregroundStyle(Color(hex: 0xAFC4E2))
        .frame(width: width, height: 30)
        .background(Color.white.opacity(0.035))
        .clipShape(RoundedRectangle(cornerRadius: 15))
        .overlay(
            RoundedRectangle(cornerRadius: 15)
                .stroke(Color.white.opacity(0.06), lineWidth: 1)
        )
    }
}

private struct HomeHeroActionTile: View {
    let title: String
    let subtitle: String
    let icon: String

    var body: some View {
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color(hex: 0xDDE8F8))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Text(subtitle)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(LNColor.navItemIdle)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Image(systemName: icon)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(LNColor.subtitle)
                .frame(width: 32, height: 32)
                .background(Color.white.opacity(0.05))
                .clipShape(RoundedRectangle(cornerRadius: 11))
                .overlay(
                    RoundedRectangle(cornerRadius: 11)
                        .stroke(Color.white.opacity(0.10), lineWidth: 1)
                )
        }
        .padding(.horizontal, 12)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            LinearGradient(
                colors: [Color(hex: 0x0C1727), Color(hex: 0x07101C), Color(hex: 0x05080D)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(LNColor.stroke, lineWidth: 1)
        )
    }
}

private struct HomeSectionCard<Content: View>: View {
    let title: String
    let actionTitle: String
    let actionIdentifier: String
    let action: () -> Void
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(title)
                    .font(.system(size: 16, weight: .heavy))
                    .foregroundStyle(Color(hex: 0xF6F9FF))
                Spacer()
                Button(action: action) {
                    HStack(spacing: 2) {
                        Text(actionTitle)
                            .font(.system(size: 11, weight: .heavy))
                        Image(systemName: "chevron.right")
                            .font(.system(size: 10, weight: .bold))
                    }
                    .foregroundStyle(LNColor.navItemActive)
                    .frame(minWidth: 60, minHeight: 30)
                    .background(
                        LinearGradient(
                            colors: [Color(hex: 0x173B62), Color(hex: 0x0A1525)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(Color(hex: 0x2F5F8E), lineWidth: 1)
                    )
                }
                .buttonStyle(.lnPressable(scale: 0.98, pressedOpacity: 0.78))
                .accessibilityIdentifier(actionIdentifier)
            }
            .frame(height: 32)

            content
        }
        .padding(16)
        .homeGlassCard(cornerRadius: 24)
        .shadow(color: .black.opacity(0.33), radius: 14, x: 0, y: 12)
    }
}

private struct RecentMediaCard: View {
    let item: LNMediaItem
    let width: CGFloat
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                VaultMediaThumbnailView(
                    encryptedPath: item.path,
                    isVideo: item.isVideo,
                    contentMode: .fill,
                    targetPixelSize: 300
                )
                .frame(width: max(width - 16, 0), height: 96)
                .clipShape(RoundedRectangle(cornerRadius: 13))
            }
            .frame(width: width, height: 122, alignment: .center)
            .homeItemCard(cornerRadius: 18)
        }
        .buttonStyle(.lnPressable())
    }
}

private struct AlbumHomeCard: View {
    let album: VaultAlbum
    let mediaItems: [LNMediaItem]
    let width: CGFloat
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 0) {
                AlbumCoverPreview(mediaItems: mediaItems, width: width)

                HStack {
                    Text(displayName)
                        .font(.system(size: 13, weight: .heavy))
                        .foregroundStyle(Color(hex: 0xF4F8FF))
                        .lineLimit(1)
                        .minimumScaleFactor(0.72)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 10)
                .frame(height: AlbumHomeLayout.labelHeight)
            }
            .frame(width: width, height: AlbumHomeLayout.tileHeight, alignment: .top)
        }
        .buttonStyle(.lnPressable())
    }

    private var displayName: String {
        if let category = album.aiCategory {
            return localizedVaultAICategory(category)
        }
        return album.name == vaultDefaultAlbumName ? L10n.tr("album_default_name") : album.name
    }
}

private struct CreateAlbumHomeCard: View {
    let width: CGFloat
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 0) {
                ZStack {
                    Color(hex: 0x0D2742)
                    Image(systemName: "plus")
                        .font(.system(size: 34, weight: .regular))
                        .foregroundStyle(LNColor.navItemActive)
                }
                .frame(width: width, height: AlbumHomeLayout.coverHeight)
                .clipShape(RoundedRectangle(cornerRadius: 20))

                Color.clear
                    .frame(width: width, height: AlbumHomeLayout.labelHeight)
            }
            .frame(width: width, height: AlbumHomeLayout.tileHeight, alignment: .top)
        }
        .buttonStyle(.lnPressable())
        .accessibilityIdentifier("home_create_album_card")
    }
}

private enum AlbumHomeLayout {
    static let coverHeight: CGFloat = 142
    static let labelHeight: CGFloat = 34
    static let tileHeight: CGFloat = coverHeight + labelHeight
}

private struct AlbumCoverPreview: View {
    let mediaItems: [LNMediaItem]
    let width: CGFloat

    var body: some View {
        ZStack {
            Color(hex: 0x12304C)
            if let item = mediaItems.first {
                VaultMediaThumbnailView(
                    encryptedPath: item.path,
                    isVideo: item.isVideo,
                    contentMode: .fill,
                    targetPixelSize: 320,
                    showVideoIndicator: true
                )
                .frame(width: width, height: AlbumHomeLayout.coverHeight)
                .clipped()
            } else {
                Image(systemName: "photo.on.rectangle.angled")
                    .font(.system(size: 34, weight: .regular))
                    .foregroundStyle(Color(hex: 0xAFC4E2).opacity(0.54))
            }
        }
        .frame(width: width, height: AlbumHomeLayout.coverHeight)
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .contentShape(RoundedRectangle(cornerRadius: 20))
    }
}

private struct AppIconHeroView: View {
    var body: some View {
        Image("AppLogoHero")
            .resizable()
            .scaledToFit()
            .frame(width: 112, height: 112)
            .compositingGroup()
        .accessibilityHidden(true)
    }
}

private func localizedVaultAICategory(_ category: String) -> String {
    let key = VaultAIAnalysisService.categoryLabelKey(category)
    let localized = L10n.tr(key)
    return localized == key ? L10n.tr("ai_category_other") : localized
}

private extension View {
    func homeGlassCard(cornerRadius: CGFloat, stroke: Color = Color(hex: 0x2D4A68)) -> some View {
        background(
            LinearGradient(
                colors: [Color(hex: 0x07101E), Color(hex: 0x0B172A), Color(hex: 0x102A46)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
        .overlay(
            RoundedRectangle(cornerRadius: cornerRadius)
                .stroke(stroke, lineWidth: 1)
        )
    }

    func homeItemCard(cornerRadius: CGFloat) -> some View {
        background(
            LinearGradient(
                colors: [Color(hex: 0x12365A), Color(hex: 0x08111F), Color(hex: 0x050A13)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
        .overlay(
            RoundedRectangle(cornerRadius: cornerRadius)
                .stroke(Color(hex: 0x2D4A68), lineWidth: 1)
        )
    }
}
