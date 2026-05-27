import PhotosUI
import SwiftUI

struct VaultHomeView: View {
    @EnvironmentObject private var router: AppRouter
    @ObservedObject private var viewModel: VaultHomeViewModel

    init(viewModel: VaultHomeViewModel) {
        self.viewModel = viewModel
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: LNSpacing.sectionGap) {
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
                    recentSection
                    albumsSection
                }
            }
            .padding(.horizontal, LNSpacing.screenHorizontal)
            .padding(.top, 8)
            .padding(.bottom, LNSpacing.homeNavBarHeight + 28)
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
        }
        .accessibilityIdentifier("vault_home_view")
    }

    private var heroCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 7) {
                    Text(L10n.appName)
                        .font(.system(size: 28, weight: .bold))
                        .foregroundStyle(Color(hex: 0xF2F6FF))
                    Text(L10n.tr("home_vault_security_info_total", viewModel.totalCount))
                        .font(LNTypography.labelMedium())
                        .foregroundStyle(Color(hex: 0x9BAEC8))
                        .lineLimit(2)
                }
                Spacer(minLength: 8)
                AppIconHeroView()
            }

            HStack(spacing: 8) {
                HomeHeroBadge(icon: "wifi.slash", title: L10n.tr("home_hero_badge_offline"))
                HomeHeroBadge(icon: "lock.shield", title: L10n.tr("home_hero_badge_encrypted"))
                HomeHeroBadge(icon: "sparkles", title: L10n.tr("home_hero_badge_ai_ready"))
            }

            HStack(spacing: 10) {
                PhotosPicker(
                    selection: $viewModel.pickerItems,
                    maxSelectionCount: 32,
                    matching: .any(of: [.images, .videos])
                ) {
                    HomeHeroActionTile(
                        title: L10n.tr("home_hero_import_title"),
                        subtitle: L10n.tr("home_hero_import_subtitle"),
                        icon: "plus"
                    )
                }
                .buttonStyle(.lnPressable(scale: 0.985, pressedOpacity: 0.86))
                .disabled(viewModel.isImporting)
                .accessibilityLabel(L10n.homeEmptyAction)
                .accessibilityIdentifier("vault_import_button")

                Button {
                    router.selectedTab = .ai
                } label: {
                    HomeHeroActionTile(
                        title: L10n.tr("home_hero_scan_title"),
                        subtitle: L10n.tr("home_hero_scan_subtitle"),
                        icon: "sparkles"
                    )
                }
                .buttonStyle(.lnPressable(scale: 0.985, pressedOpacity: 0.86))
                .accessibilityIdentifier("vault_ai_scan_shortcut")
            }
            .frame(height: 56)
        }
        .padding(18)
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
        HomeSectionCard(
            title: L10n.albumListTitle,
            actionTitle: L10n.tr("home_section_all"),
            actionIdentifier: "home_albums_view_all",
            action: { router.pushVault(.albumList) }
        ) {
            GeometryReader { proxy in
                let cardWidth = floor((proxy.size.width - 20) / 3)
                HStack(spacing: 10) {
                    ForEach(Array(viewModel.albums.prefix(2))) { album in
                        AlbumHomeCard(
                            album: album,
                            mediaItems: viewModel.recentMediaItems(for: album),
                            width: cardWidth
                        ) {
                            router.pushVault(.album(name: album.name))
                        }
                    }
                    CreateAlbumHomeCard(width: cardWidth) {
                        viewModel.showCreateAlbum = true
                    }
                }
            }
            .frame(height: 122)
        }
    }

    private func open(_ item: LNMediaItem) {
        if item.isVideo {
            router.pushVault(.videoPlayer(path: item.path))
        } else {
            router.pushVault(.photoViewer(path: item.path, isTrash: false, source: .recent))
        }
    }
}

private struct HomeHeroBadge: View {
    let icon: String
    let title: String

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
        .padding(.horizontal, 10)
        .frame(height: 26)
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
                .overlay(
                    RoundedRectangle(cornerRadius: 13)
                        .stroke(Color(hex: 0x315D84), lineWidth: 1)
                )
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
            VStack(alignment: .center, spacing: 0) {
                AlbumPreviewGrid(mediaItems: mediaItems)
                    .frame(maxWidth: .infinity)
                    .frame(height: 82)

                Spacer(minLength: 0)

                Text(album.name)
                    .font(.system(size: 12, weight: .heavy))
                    .foregroundStyle(Color(hex: 0xF6F9FF))
                    .frame(maxWidth: .infinity, alignment: .center)
                    .multilineTextAlignment(.center)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
            }
            .padding(9)
            .frame(width: width, height: 122, alignment: .top)
            .homeItemCard(cornerRadius: 18)
        }
        .buttonStyle(.lnPressable())
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
                .frame(maxWidth: .infinity)
                .frame(height: 82)
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(Color(hex: 0x315D84), lineWidth: 1)
                )

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
        LazyVGrid(
            columns: Array(repeating: GridItem(.flexible(), spacing: 5), count: 2),
            spacing: 5
        ) {
            ForEach(0..<4, id: \.self) { index in
                if index < mediaItems.count {
                    VaultMediaThumbnailView(
                        encryptedPath: mediaItems[index].path,
                        isVideo: mediaItems[index].isVideo,
                        contentMode: .fill,
                        targetPixelSize: 160,
                        showVideoIndicator: false
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 7))
                } else {
                    RoundedRectangle(cornerRadius: 7)
                        .fill(
                            LinearGradient(
                                colors: [Color(hex: 0x1D4773), Color(hex: 0x0B1727)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                }
            }
        }
        .padding(7)
        .background(
            LinearGradient(
                colors: [Color(hex: 0x102A46), Color(hex: 0x07101C)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color(hex: 0x315D84), lineWidth: 1)
        )
    }
}

private struct AppIconHeroView: View {
    var body: some View {
        ZStack {
            Image("AppLogo")
                .resizable()
                .scaledToFill()
            RoundedRectangle(cornerRadius: 17)
                .stroke(Color.white.opacity(0.14), lineWidth: 1)
        }
        .frame(width: 58, height: 58)
        .clipShape(RoundedRectangle(cornerRadius: 17))
        .shadow(color: LNColor.brandBlue.opacity(0.14), radius: 10, x: 0, y: 7)
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
