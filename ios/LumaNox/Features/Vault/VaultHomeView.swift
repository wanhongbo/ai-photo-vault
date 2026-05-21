import PhotosUI
import SwiftUI

struct VaultHomeView: View {
    @EnvironmentObject private var router: AppRouter
    @StateObject private var viewModel = VaultHomeViewModel()

    var body: some View {
        VStack(spacing: 0) {
            header

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: LNSpacing.sectionGap) {
                    mediaTags

                    if !viewModel.hasPinConfigured {
                        pinSetupBanner
                    }

                    statusMessage

                    if viewModel.showPermissionDenied {
                        permissionCard
                    } else if viewModel.isEmpty && !viewModel.isImporting {
                        emptyVaultCard
                    } else {
                        albumsSection
                        recentSection
                    }
                }
                .padding(.horizontal, LNSpacing.screenHorizontal)
                .padding(.top, 12)
                .padding(.bottom, LNSpacing.homeNavBarHeight + 28)
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
        }
        .accessibilityIdentifier("vault_home_view")
    }

    private var header: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 6) {
                Text(L10n.appName)
                    .font(LNTypography.displaySmall())
                    .foregroundStyle(LNColor.title)
                Text(L10n.tr("home_vault_security_info", viewModel.totalCount))
                    .font(LNTypography.bodyMedium())
                    .foregroundStyle(LNColor.subtitle)
            }
            Spacer()
            PhotosPicker(
                selection: $viewModel.pickerItems,
                maxSelectionCount: 32,
                matching: .any(of: [.images, .videos])
            ) {
                Image(systemName: "plus")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(LNColor.title)
                    .frame(width: LNSpacing.minTouchTarget, height: LNSpacing.minTouchTarget)
                    .lnOutlinedCard(cornerRadius: LNRadius.topBarButton, fill: LNColor.sectionBg)
            }
            .disabled(viewModel.isImporting)
        }
        .padding(.horizontal, LNSpacing.screenHorizontal)
        .padding(.top, 8)
    }

    private var mediaTags: some View {
        HStack(spacing: 6) {
            tag(L10n.tr("home_header_stat_photos", viewModel.imageCount))
            tag(L10n.tr("home_header_stat_videos", viewModel.videoCount))
        }
    }

    private func tag(_ label: String) -> some View {
        Text(label)
            .font(LNTypography.labelMedium())
            .foregroundStyle(LNColor.subtitle)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .lnOutlinedCard(cornerRadius: 8, fill: LNColor.sectionBg)
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
            .lnOutlinedCard(cornerRadius: LNRadius.homeCard, fill: LNColor.sectionBg, stroke: LNColor.error.opacity(0.45))
        }
        .buttonStyle(.plain)
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
        }
        if let tip = viewModel.importTip {
            Text(tip)
                .font(LNTypography.bodyMedium())
                .foregroundStyle(viewModel.importTipIsError ? LNColor.error : LNColor.success)
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
        .lnOutlinedCard()
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
            Button { router.openPrivateCamera() } label: {
                Text(L10n.tr("home_camera_empty_action"))
                    .font(LNTypography.button())
                    .foregroundStyle(LNColor.title)
                    .frame(maxWidth: .infinity)
                    .frame(height: LNSpacing.buttonHeightSecondary)
                    .background(LNColor.buttonSecondaryBg)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 32)
        .padding(.horizontal, LNSpacing.cardPadding)
        .lnOutlinedCard()
    }

    private var albumsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(L10n.albumListTitle)
                    .font(LNTypography.titleMedium())
                    .foregroundStyle(LNColor.title)
                Spacer()
                Button(L10n.tr("home_albums_view_all")) { router.pushVault(.albumList) }
                    .font(LNTypography.labelMedium())
                    .foregroundStyle(LNColor.navItemActive)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(viewModel.albums) { album in
                        albumCard(album)
                    }
                    createAlbumCard
                }
            }
        }
        .padding(LNSpacing.cardPadding)
        .lnOutlinedCard()
    }

    private func albumCard(_ album: VaultAlbum) -> some View {
        Button { router.pushVault(.album(name: album.name)) } label: {
            VStack(alignment: .leading, spacing: 7) {
                DecorativeVaultThumb(seed: abs(album.id.hashValue), height: LNSpacing.albumCoverHeight)
                Text(album.name)
                    .font(LNTypography.bodyMedium().weight(.semibold))
                    .foregroundStyle(LNColor.title)
                    .lineLimit(1)
                Text(L10n.tr("home_album_photo_count", album.photoCount))
                    .font(LNTypography.labelMedium())
                    .foregroundStyle(LNColor.subtitle)
            }
            .padding(10)
            .frame(width: LNSpacing.albumCardWidth)
            .lnOutlinedCard(cornerRadius: LNRadius.homeAlbumCard, fill: LNColor.bgBottom)
        }
        .buttonStyle(.plain)
    }

    private var createAlbumCard: some View {
        Button { viewModel.showCreateAlbum = true } label: {
            VStack(alignment: .leading, spacing: 7) {
                ZStack {
                    RoundedRectangle(cornerRadius: LNRadius.homeThumb)
                        .fill(LNColor.emptyIconBg)
                    Image(systemName: "plus")
                        .font(.system(size: 24, weight: .semibold))
                        .foregroundStyle(LNColor.navItemActive)
                }
                .frame(height: LNSpacing.albumCoverHeight)
                Text(L10n.homeAlbumCreateTitle)
                    .font(LNTypography.bodyMedium().weight(.semibold))
                    .foregroundStyle(LNColor.title)
                Text(" ")
                    .font(LNTypography.labelMedium())
            }
            .padding(10)
            .frame(width: LNSpacing.albumCardWidth)
            .lnOutlinedCard(cornerRadius: LNRadius.homeAlbumCard, fill: LNColor.bgBottom)
        }
        .buttonStyle(.plain)
    }

    private var recentSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(L10n.recentListTitle)
                    .font(LNTypography.titleMedium())
                    .foregroundStyle(LNColor.title)
                Spacer()
                Button(L10n.tr("home_recent_view_more")) { router.pushVault(.recentList) }
                    .font(LNTypography.labelMedium())
                    .foregroundStyle(LNColor.navItemActive)
            }

            LazyVGrid(
                columns: Array(repeating: GridItem(.flexible(), spacing: LNSpacing.gridGap), count: 3),
                spacing: LNSpacing.gridGap
            ) {
                ForEach(viewModel.recentPhotos.prefix(6)) { item in
                    Button {
                        if item.isVideo {
                            router.pushVault(.videoPlayer(path: item.path))
                        } else {
                            router.pushVault(.photoViewer(path: item.path, isTrash: false, source: .recent))
                        }
                    } label: {
                        VaultMediaThumbnailView(
                            encryptedPath: item.path,
                            isVideo: item.isVideo,
                            contentMode: .fill,
                            targetPixelSize: 300
                        )
                        .frame(height: 74)
                        .clipShape(RoundedRectangle(cornerRadius: LNRadius.homeThumb))
                        .overlay(
                            RoundedRectangle(cornerRadius: LNRadius.homeThumb)
                                .stroke(LNColor.stroke, lineWidth: 1)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(LNSpacing.cardPadding)
        .lnOutlinedCard()
    }
}

private struct DecorativeVaultThumb: View {
    let seed: Int
    let height: CGFloat
    var showVideo = false

    private var accent: Color {
        let palette = [LNColor.brandBlue, LNColor.amberWarning, LNColor.aiDedup, LNColor.allClearTeal, LNColor.navItemActive]
        return palette[abs(seed) % palette.count]
    }

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: LNRadius.homeThumb)
                .fill(LNColor.emptyIconBg)
            RoundedRectangle(cornerRadius: LNRadius.homeThumb)
                .fill(
                    LinearGradient(
                        colors: [accent.opacity(0.22), LNColor.sectionBg],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            Circle()
                .fill(accent.opacity(0.7))
                .frame(width: 18, height: 18)
                .offset(x: 24, y: -20)
            Capsule()
                .fill(accent.opacity(0.45))
                .frame(width: 46, height: 9)
                .rotationEffect(.degrees(seed.isMultiple(of: 2) ? -18 : 15))
                .offset(x: -12, y: 14)
            if showVideo {
                Image(systemName: "play.fill")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(LNColor.title)
                    .padding(7)
                    .background(.black.opacity(0.35))
                    .clipShape(Circle())
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                    .padding(7)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .overlay(
            RoundedRectangle(cornerRadius: LNRadius.homeThumb)
                .stroke(LNColor.stroke, lineWidth: 1)
        )
    }
}
