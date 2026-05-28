import PhotosUI
import SwiftUI
import UIKit

struct VaultHomeView: View {
    @EnvironmentObject private var router: AppRouter
    @ObservedObject private var viewModel: VaultHomeViewModel

    init(viewModel: VaultHomeViewModel) {
        self.viewModel = viewModel
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 24) {
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
                .padding(.top, 8)
                .padding(.bottom, LNSpacing.homeNavBarHeight + 72)
            }

            if viewModel.shouldShowFloatingImportButton {
                floatingImportButton
                    .padding(.bottom, LNSpacing.homeNavBarHeight + 22)
            }
        }
        .onAppear { viewModel.onAppear() }
        .onChange(of: viewModel.pickerItems) { _ in
            _ = viewModel.onPickerItemsChanged(router: router)
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

    private var heroCard: some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 6) {
                Text(L10n.appName)
                    .font(.system(size: 28, weight: .bold))
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
                HomeHeroBadge(icon: "wifi.slash", title: L10n.tr("home_hero_badge_offline"), width: 68)
                HomeHeroBadge(icon: "lock.shield", title: L10n.tr("home_hero_badge_encrypted"), width: 80)
                HomeHeroBadge(icon: "sparkles", title: L10n.tr("home_hero_badge_ai_local"), width: 104)
            }
            .padding(.leading, 20)
            .padding(.top, 104)

            HStack {
                Spacer()
                AppIconHeroView()
            }
            .padding(.top, 19)
            .padding(.trailing, 8)
        }
        .frame(height: 150)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(
                colors: [Color(hex: 0x07101A), Color(hex: 0x081321), Color(hex: 0x0D2238)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .clipShape(RoundedRectangle(cornerRadius: 24))
        .overlay(
            RoundedRectangle(cornerRadius: 24)
                .stroke(LNColor.stroke, lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.33), radius: 14, x: 0, y: 12)
    }

    private var floatingImportButton: some View {
        PhotosPicker(
            selection: $viewModel.pickerItems,
            maxSelectionCount: 32,
            matching: .any(of: [.images, .videos])
        ) {
            Image(systemName: "plus")
                .font(.system(size: 31, weight: .semibold))
                .foregroundStyle(LNColor.navItemActive)
                .frame(width: 64, height: 64)
                .background(
                    LinearGradient(
                        colors: [Color(hex: 0x153A5C), Color(hex: 0x10233A), Color(hex: 0x07101C)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .clipShape(Circle())
                .overlay(Circle().stroke(Color(hex: 0x2F5F8E), lineWidth: 1))
                .shadow(color: Color(hex: 0x153A5C).opacity(0.40), radius: 28, x: 0, y: 14)
        }
        .buttonStyle(.lnPressable(scale: 0.94, pressedOpacity: 0.78))
        .disabled(viewModel.isImporting)
        .accessibilityLabel(L10n.tr("home_import_fab_accessibility"))
        .accessibilityIdentifier("vault_import_button")
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
        if viewModel.isImporting || viewModel.importTip != nil {
            VStack(alignment: .leading, spacing: 8) {
                if viewModel.isImporting {
                    HStack(spacing: 8) {
                        ProgressView().tint(LNColor.brandBlue)
                        Text(L10n.commonLoading)
                            .font(LNTypography.bodyMedium())
                            .foregroundStyle(LNColor.subtitle)
                    }
                }
                if let tip = viewModel.importTip {
                    Text(tip)
                        .font(LNTypography.bodyMedium())
                        .foregroundStyle(viewModel.importTipIsError ? LNColor.error : LNColor.success)
                }
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
                matching: .any(of: [.images, .videos])
            ) {
                Text(L10n.homeEmptyAction)
                    .font(LNTypography.button())
                    .foregroundStyle(LNColor.buttonPrimaryFg)
                    .frame(maxWidth: .infinity)
                    .frame(height: LNSpacing.buttonHeightPrimary)
                    .background(LNColor.buttonPrimaryBg)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
            }
            .buttonStyle(.lnPressable(scale: 0.985, pressedOpacity: 0.88))
            Button { router.openPrivateCamera() } label: {
                Text(L10n.tr("home_camera_empty_action"))
                    .font(LNTypography.button())
                    .foregroundStyle(LNColor.title)
                    .frame(maxWidth: .infinity)
                    .frame(height: LNSpacing.buttonHeightSecondary)
                    .background(LNColor.buttonSecondaryBg)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
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
            HStack {
                Text(L10n.albumListTitle)
                    .font(.system(size: 18, weight: .heavy))
                    .foregroundStyle(Color(hex: 0xF6F9FF))
                Spacer()
                Button {
                    viewModel.showCreateAlbum = true
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "plus")
                            .font(.system(size: 13, weight: .bold))
                        Text(L10n.tr("home_album_new_action"))
                            .font(.system(size: 12, weight: .heavy))
                    }
                    .foregroundStyle(LNColor.navItemActive)
                    .frame(width: 78, height: 30)
                    .background(LNColor.navBarBg.opacity(0.8))
                    .clipShape(Capsule())
                    .overlay(Capsule().stroke(Color(hex: 0x2F5F8E), lineWidth: 1))
                }
                .buttonStyle(.lnPressable(scale: 0.98, pressedOpacity: 0.78))
                .accessibilityIdentifier("home_create_album_button")
            }
            .frame(height: 34)

            GeometryReader { proxy in
                let cardWidth = floor((proxy.size.width - 10) / 2)
                LazyVGrid(columns: albumGridColumns(cardWidth: cardWidth), spacing: 10) {
                    ForEach(Array(viewModel.albums.prefix(4))) { album in
                        AlbumHomeCard(
                            album: album,
                            mediaItems: viewModel.recentMediaItems(for: album),
                            width: cardWidth
                        ) {
                            router.pushVault(.album(name: album.name))
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .topLeading)
            }
            .frame(height: 380)
        }
        .accessibilityIdentifier("vault_album_grid_section")
    }

    private func open(_ item: LNMediaItem) {
        if item.isVideo {
            router.pushVault(.videoPlayer(path: item.path))
        } else {
            router.pushVault(.photoViewer(path: item.path, isTrash: false, source: .recent))
        }
    }

    private func albumGridColumns(cardWidth: CGFloat) -> [GridItem] {
        [
            GridItem(.fixed(cardWidth), spacing: 10),
            GridItem(.fixed(cardWidth), spacing: 10),
        ]
    }
}

private struct ImportOriginalsDecisionSheet: View {
    let selectionCount: Int
    @Binding var rememberChoice: Bool
    let onKeepOriginals: () -> Void
    let onDeleteOriginals: () -> Void

    var body: some View {
        ZStack(alignment: .bottom) {
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
            .padding(.top, 22)
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
            .background(LNColor.dialogBg)
            .clipShape(RoundedCorner(radius: 26, corners: [.topLeft, .topRight]))
            .overlay(
                RoundedCorner(radius: 26, corners: [.topLeft, .topRight])
                    .stroke(LNColor.stroke, lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.35), radius: 28, x: 0, y: -14)
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

private struct RoundedCorner: Shape {
    var radius: CGFloat
    var corners: UIRectCorner

    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(
            roundedRect: rect,
            byRoundingCorners: corners,
            cornerRadii: CGSize(width: radius, height: radius)
        )
        return Path(path.cgPath)
    }
}

private struct HomeHeroBadge: View {
    let icon: String
    let title: String
    let width: CGFloat

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: icon)
                .font(.system(size: 10, weight: .medium))
            Text(title)
                .font(.system(size: 9, weight: .medium))
                .lineLimit(1)
                .minimumScaleFactor(0.78)
        }
        .foregroundStyle(Color(hex: 0xAFC4E2))
        .frame(width: width, height: 26)
        .background(Color.white.opacity(0.035))
        .clipShape(RoundedRectangle(cornerRadius: 13))
        .overlay(
            RoundedRectangle(cornerRadius: 13)
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
            VStack(alignment: .leading, spacing: 8) {
                AlbumPreviewGrid(mediaItems: mediaItems)
                    .frame(maxWidth: .infinity)
                    .frame(height: 86)

                HStack(spacing: 6) {
                    Text(album.name)
                        .font(.system(size: 14, weight: .heavy))
                        .foregroundStyle(Color(hex: 0xF6F9FF))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .lineLimit(1)
                        .minimumScaleFactor(0.72)
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(LNColor.navItemActive)
                }
                .frame(height: 20)

                Text(albumCountText)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(LNColor.subtitle)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .lineLimit(1)
            }
            .padding(10)
            .frame(width: width, height: 185, alignment: .top)
            .homeItemCard(cornerRadius: 20)
        }
        .buttonStyle(.lnPressable())
    }

    private var albumCountText: String {
        L10n.tr("home_album_item_count", album.photoCount)
    }
}

private struct CreateAlbumHomeCard: View {
    let width: CGFloat
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .center, spacing: 0) {
                ZStack {
                    RoundedRectangle(cornerRadius: 14)
                        .fill(
                            LinearGradient(
                                colors: [Color(hex: 0x102A46), Color(hex: 0x07101C)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                    Image(systemName: "plus")
                        .font(.system(size: 26, weight: .semibold))
                        .foregroundStyle(LNColor.navItemActive)
                }
                .frame(width: 82, height: 82)

                Spacer(minLength: 0)

                Text(L10n.homeAlbumCreateTitle)
                    .font(.system(size: 12, weight: .heavy))
                    .foregroundStyle(Color(hex: 0xF6F9FF))
                    .frame(maxWidth: .infinity, alignment: .center)
                    .multilineTextAlignment(.center)
                    .lineLimit(1)
                    .minimumScaleFactor(0.72)
            }
            .padding(9)
            .frame(width: width, height: 122, alignment: .top)
            .homeItemCard(cornerRadius: 18)
        }
        .buttonStyle(.lnPressable())
        .accessibilityIdentifier("home_create_album_card")
    }
}

private struct AlbumPreviewGrid: View {
    let mediaItems: [LNMediaItem]

    var body: some View {
        GeometryReader { proxy in
            let tileSize = max(0, floor((proxy.size.width - 19) / 2))
            LazyVGrid(
                columns: Array(repeating: GridItem(.fixed(tileSize), spacing: 5), count: 2),
                spacing: 5
            ) {
                ForEach(0..<4, id: \.self) { index in
                    if index < mediaItems.count {
                        VaultMediaThumbnailView(
                            encryptedPath: mediaItems[index].path,
                            isVideo: mediaItems[index].isVideo,
                            contentMode: .fill,
                            targetPixelSize: 180,
                            showVideoIndicator: false
                        )
                        .frame(width: tileSize, height: 32)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    } else {
                        RoundedRectangle(cornerRadius: 10)
                            .fill(
                                LinearGradient(
                                    colors: [Color(hex: 0x1D4773), Color(hex: 0x0B1727)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .frame(width: tileSize, height: 32)
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(Color.white.opacity(0.08), lineWidth: 1)
                            )
                    }
                }
            }
            .padding(7)
        }
        .background(
            LinearGradient(
                colors: [Color(hex: 0x102A46), Color(hex: 0x07101C)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

private struct AppIconHeroView: View {
    var body: some View {
        ZStack {
            RadialGradient(
                colors: [LNColor.brandBlue.opacity(0.12), Color(hex: 0x0D2238).opacity(0.10), Color.clear],
                center: .center,
                startRadius: 4,
                endRadius: 60
            )
            .frame(width: 108, height: 108)
            .clipShape(RoundedRectangle(cornerRadius: 34))

            Image("AppLogo")
                .resizable()
                .scaledToFill()
                .frame(width: 87, height: 87)
                .opacity(0.86)
                .clipShape(RoundedRectangle(cornerRadius: 26))
                .overlay(
                    RadialGradient(
                        colors: [Color.clear, Color.clear, Color(hex: 0x0D2238).opacity(0.58)],
                        center: UnitPoint(x: 0.5, y: 0.48),
                        startRadius: 18,
                        endRadius: 56
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 26))
                )
                .overlay(
                    RadialGradient(
                        colors: [LNColor.brandBlue.opacity(0.12), LNColor.brandBlue.opacity(0.05), Color.clear],
                        center: .center,
                        startRadius: 0,
                        endRadius: 34
                    )
                    .frame(width: 66, height: 66)
                )
        }
        .frame(width: 108, height: 108)
        .shadow(color: LNColor.brandBlue.opacity(0.12), radius: 30, x: 0, y: 12)
        .accessibilityHidden(true)
    }
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
