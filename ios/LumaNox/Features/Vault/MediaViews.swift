import Photos
import PhotosUI
import SwiftUI
import UIKit

struct AlbumListView: View {
    @EnvironmentObject private var router: AppRouter
    @EnvironmentObject private var vaultStore: VaultStore
    @Environment(\.dismiss) private var dismiss
    @State private var showCreate = false
    @State private var newName = ""

    var body: some View {
        VaultListScreenChrome(title: L10n.tr("album_list_screen_title"), onBack: { dismiss() }) { availableWidth in
            let cardWidth = max(0, floor((availableWidth - LNSpacing.screenHorizontal * 2 - 12) / 2))

            VStack(alignment: .leading, spacing: 16) {
                VaultFilterChip(title: L10n.tr("album_sort_recently_created"))

                LazyVGrid(columns: albumColumns(cardWidth: cardWidth), spacing: 12) {
                    ForEach(vaultStore.snapshot?.albums ?? []) { album in
                        Button {
                            router.pushVault(.album(name: album.name))
                        } label: {
                            VaultAlbumListCard(
                                width: cardWidth,
                                title: album.name,
                                subtitle: L10n.tr("home_album_photo_count", album.photoCount),
                                coverItem: coverItem(for: album)
                            )
                        }
                        .buttonStyle(.lnPressable())
                    }

                    Button { showCreate = true } label: {
                        VaultCreateAlbumListCard(width: cardWidth)
                    }
                    .buttonStyle(.lnPressable())
                }
            }
            .padding(.horizontal, LNSpacing.screenHorizontal)
            .padding(.top, 6)
            .padding(.bottom, 28)
        }
        .onAppear { Task { await vaultStore.loadSnapshot() } }
        .overlay {
            if showCreate {
                LNInputDialog(
                    title: L10n.homeAlbumCreateTitle,
                    text: $newName,
                    placeholder: L10n.homeAlbumCreateHint,
                    confirmTitle: L10n.homeAlbumCreateConfirm,
                    dismissTitle: L10n.commonCancel,
                    onConfirm: createAlbumAndOpen,
                    onDismiss: { showCreate = false }
                )
            }
        }
    }

    private func albumColumns(cardWidth: CGFloat) -> [GridItem] {
        [
            GridItem(.fixed(cardWidth), spacing: 12),
            GridItem(.fixed(cardWidth), spacing: 12),
        ]
    }

    private func coverItem(for album: VaultAlbum) -> LNMediaItem? {
        vaultStore.photos(in: album.name).first?.toMediaItem()
    }

    private func createAlbumAndOpen() {
        let trimmed = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let safeName = (try? vaultStore.createAlbum(named: trimmed)) ?? trimmed
        showCreate = false
        router.pushVault(.album(name: safeName))
        newName = ""
        Task { await vaultStore.loadSnapshot() }
    }
}

struct RecentPhotosView: View {
    @EnvironmentObject private var router: AppRouter
    @EnvironmentObject private var vaultStore: VaultStore
    @Environment(\.dismiss) private var dismiss

    private var items: [LNMediaItem] {
        vaultStore.snapshot?.recentPhotos.map { $0.toMediaItem() } ?? []
    }

    var body: some View {
        VaultListScreenChrome(title: L10n.tr("recent_photos_screen_title"), onBack: { dismiss() }) { availableWidth in
            let cardWidth = max(0, availableWidth - LNSpacing.screenHorizontal * 2)

            VStack(spacing: 0) {
                if items.isEmpty {
                    LNEmptyStateCard(
                        title: L10n.homeEmptyTitle,
                        message: L10n.homeEmptyDesc,
                        actionTitle: L10n.homeEmptyAction,
                        action: {}
                    )
                } else {
                    VaultMediaGridCard(items: items, width: cardWidth, onSelect: open)
                }
            }
            .padding(.horizontal, LNSpacing.screenHorizontal)
            .padding(.top, 6)
            .padding(.bottom, 28)
        }
        .onAppear { Task { await vaultStore.loadSnapshot() } }
    }

    private func open(_ item: LNMediaItem) {
        if item.isVideo {
            router.pushVault(.videoPlayer(path: item.path, source: .recent))
        } else {
            router.pushVault(.photoViewer(path: item.path, isTrash: false, source: .recent))
        }
    }
}

struct VaultListScreenChrome<Content: View>: View {
    let title: String
    let onBack: () -> Void
    @ViewBuilder let content: (CGFloat) -> Content

    var body: some View {
        GeometryReader { proxy in
            VStack(spacing: 0) {
                header
                ScrollView(showsIndicators: false) {
                    content(proxy.size.width)
                        .frame(width: proxy.size.width, alignment: .topLeading)
                }
            }
            .background(LNColor.bgBottom.ignoresSafeArea())
            .edgeSwipeBack(action: onBack)
        }
    }

    private var header: some View {
        HStack(spacing: 0) {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(LNColor.title)
                    .frame(width: LNSpacing.minTouchTarget, height: LNSpacing.minTouchTarget)
                    .background(LNColor.navBarBg.opacity(0.8))
                    .clipShape(RoundedRectangle(cornerRadius: LNRadius.topBarButton))
            }
            .buttonStyle(.lnPressable(scale: 0.94, pressedOpacity: 0.78))
            .accessibilityLabel(L10n.commonBack)
            .accessibilityIdentifier("vault_list_back")

            Text(title)
                .font(LNTypography.titleMedium())
                .foregroundStyle(LNColor.title)
                .frame(maxWidth: .infinity)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
                .accessibilityAddTraits(.isHeader)

            Color.clear
                .frame(width: LNSpacing.minTouchTarget, height: LNSpacing.minTouchTarget)
        }
        .padding(.horizontal, LNSpacing.screenHorizontal)
        .padding(.top, 0)
        .padding(.bottom, 2)
    }
}

private struct VaultFilterChip: View {
    let title: String

    var body: some View {
        Text(title)
            .font(.system(size: 13, weight: .medium))
            .foregroundStyle(LNColor.navItemActive)
            .lineLimit(1)
            .padding(.horizontal, 10)
            .frame(height: 38)
            .background(LNColor.sectionBg)
            .clipShape(RoundedRectangle(cornerRadius: 9))
            .overlay(
                RoundedRectangle(cornerRadius: 9)
                    .stroke(LNColor.brandBlue, lineWidth: 1.4)
            )
            .accessibilityIdentifier("album_sort_recently_created")
    }
}

private struct VaultAlbumListCard: View {
    let width: CGFloat
    let title: String
    let subtitle: String
    let coverItem: LNMediaItem?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            albumCover
                .frame(height: 130)
                .clipShape(RoundedRectangle(cornerRadius: 10))

            Text(title)
                .font(.system(size: 20, weight: .regular))
                .foregroundStyle(LNColor.title)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
                .padding(.top, 16)

            Text(subtitle)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Color(hex: 0x9FB2D1))
                .lineLimit(1)
                .padding(.top, 7)
        }
        .padding(16)
        .frame(width: width, height: 226, alignment: .leading)
        .background(LNColor.sectionBg)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(LNColor.strokeStrong, lineWidth: 1)
        )
        .contentShape(RoundedRectangle(cornerRadius: 18))
    }

    @ViewBuilder
    private var albumCover: some View {
        if let coverItem {
            VaultMediaThumbnailView(
                encryptedPath: coverItem.path,
                isVideo: coverItem.isVideo,
                contentMode: .fill,
                targetPixelSize: 520
            )
        } else {
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(hex: 0x142741))
        }
    }
}

private struct VaultCreateAlbumListCard: View {
    let width: CGFloat

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack {
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color(hex: 0x142741))
                Image(systemName: "plus")
                    .font(.system(size: 27, weight: .regular))
                    .foregroundStyle(Color(hex: 0x9FB2D1))
            }
            .frame(height: 130)

            Text(L10n.homeAlbumCreateTitle)
                .font(.system(size: 20, weight: .regular))
                .foregroundStyle(LNColor.title)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
                .padding(.top, 16)

            Spacer(minLength: 0)
        }
        .padding(16)
        .frame(width: width, height: 226, alignment: .leading)
        .background(LNColor.sectionBg)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(LNColor.strokeStrong, lineWidth: 1)
        )
        .contentShape(RoundedRectangle(cornerRadius: 18))
        .accessibilityIdentifier("album_create_card")
    }
}

struct VaultMediaGridCard: View {
    let items: [LNMediaItem]
    let width: CGFloat
    let onSelect: (LNMediaItem) -> Void
    var importSelection: Binding<[PhotosPickerItem]>? = nil
    var isImporting = false

    private var hasImportTile: Bool {
        importSelection != nil
    }

    private var tileCount: Int {
        items.count + (hasImportTile ? 1 : 0)
    }

    private var columnCount: Int {
        hasImportTile ? min(3, max(1, tileCount)) : 3
    }

    private var cellWidth: CGFloat {
        max(0, floor((width - 32 - LNSpacing.gridGap * 2) / 3))
    }

    private var cardWidth: CGFloat {
        guard hasImportTile else { return width }
        return 32 + CGFloat(columnCount) * cellWidth + CGFloat(columnCount - 1) * LNSpacing.gridGap
    }

    private var columns: [GridItem] {
        Array(repeating: GridItem(.fixed(cellWidth), spacing: LNSpacing.gridGap), count: columnCount)
    }

    var body: some View {
        LazyVGrid(columns: columns, spacing: LNSpacing.gridGap) {
            if let importSelection {
                PhotosPicker(
                    selection: importSelection,
                    maxSelectionCount: 32,
                    matching: .any(of: [.images, .videos]),
                    photoLibrary: PHPhotoLibrary.shared()
                ) {
                    VaultMediaImportGridTile(size: cellWidth, isImporting: isImporting)
                }
                .disabled(isImporting)
                .buttonStyle(.lnPressable())
                .accessibilityLabel(L10n.tr("album_import_media_action"))
                .accessibilityIdentifier("album_import_media_tile")
            }

            ForEach(items) { item in
                Button {
                    onSelect(item)
                } label: {
                    VaultMediaThumbnailView(
                        encryptedPath: item.path,
                        isVideo: item.isVideo,
                        contentMode: .fill,
                        targetPixelSize: 360
                    )
                    .frame(width: cellWidth, height: cellWidth)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(LNColor.stroke, lineWidth: 1)
                    )
                }
                .buttonStyle(.lnPressable())
            }
        }
        .padding(LNSpacing.cardPadding)
        .frame(width: cardWidth)
        .background(LNColor.sectionBg)
        .clipShape(RoundedRectangle(cornerRadius: LNRadius.homeCard))
        .overlay(
            RoundedRectangle(cornerRadius: LNRadius.homeCard)
                .stroke(LNColor.strokeStrong, lineWidth: 1)
        )
        .frame(width: width, alignment: .leading)
    }
}

private struct VaultMediaImportGridTile: View {
    let size: CGFloat
    let isImporting: Bool

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(hex: 0x142741))
            RoundedRectangle(cornerRadius: 10)
                .stroke(LNColor.stroke, lineWidth: 1)

            if isImporting {
                ProgressView()
                    .tint(LNColor.brandBlue)
            } else {
                Image(systemName: "plus")
                    .font(.system(size: 28, weight: .semibold))
                    .foregroundStyle(Color(hex: 0x9FB2D1))
            }
        }
        .frame(width: size, height: size)
    }
}

struct AlbumView: View {
    @EnvironmentObject private var router: AppRouter
    @EnvironmentObject private var vaultStore: VaultStore
    @Environment(\.dismiss) private var dismiss
    let albumName: String
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var duplicateImportDialogMessage: String?

    private var safeAlbumName: String {
        vaultStore.sanitizeAlbumName(albumName)
    }

    private var albumItems: [LNMediaItem] {
        vaultStore.photos(in: safeAlbumName).map { $0.toMediaItem() }
    }

    var body: some View {
        VaultListScreenChrome(title: safeAlbumName, onBack: { dismiss() }) { availableWidth in
            let cardWidth = max(0, availableWidth - LNSpacing.screenHorizontal * 2)

            VStack(spacing: 24) {
                LNButton(title: L10n.tr("bulk_export_title"), variant: .secondary) {
                    ExportRuntimeState.prepareSource(albumName: safeAlbumName)
                    router.pushInCurrentTab(.bulkExport)
                }
                .frame(width: cardWidth)

                VaultMediaGridCard(
                    items: albumItems,
                    width: cardWidth,
                    onSelect: open,
                    importSelection: $pickerItems,
                    isImporting: vaultStore.isImporting
                )
            }
            .padding(.horizontal, LNSpacing.screenHorizontal)
            .padding(.top, 22)
            .padding(.bottom, 28)
        }
        .onAppear { Task { await vaultStore.loadSnapshot() } }
        .onChange(of: pickerItems) { _ in
            importSelectedItems()
        }
        .overlay {
            if let duplicateMessage = duplicateImportDialogMessage {
                LNDialog(
                    title: L10n.tr("home_import_duplicate_dialog_title"),
                    message: duplicateMessage,
                    confirmTitle: L10n.commonOk,
                    onConfirm: { duplicateImportDialogMessage = nil }
                )
            }
        }
    }

    private func open(_ item: LNMediaItem) {
        if item.isVideo {
            router.pushVault(.videoPlayer(path: item.path, source: .album(name: safeAlbumName)))
        } else {
            router.pushVault(.photoViewer(path: item.path, isTrash: false, source: .album(name: safeAlbumName)))
        }
    }

    private func importSelectedItems() {
        guard !pickerItems.isEmpty else { return }
        guard router.guardProFeature(.vaultImport) else {
            pickerItems = []
            return
        }

        let items = pickerItems
        pickerItems = []
        Task {
            vaultStore.beginImportBatch()
            let summary = await PhotosPickerVaultImporter.importItems(items, into: safeAlbumName, vaultStore: vaultStore)
            vaultStore.endImportBatch()
            await vaultStore.finalizeImportBatch(summary)
            duplicateImportDialogMessage = VaultImportFeedback.duplicateDialogMessage(for: summary)
        }
    }
}

struct VaultSearchView: View {
    @EnvironmentObject private var router: AppRouter
    @EnvironmentObject private var vaultStore: VaultStore
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    private var results: [LNMediaItem] {
        vaultStore.searchPhotos(query: query).map { $0.toMediaItem() }
    }

    var body: some View {
        VaultListScreenChrome(title: L10n.vaultSearchTitle, onBack: { dismiss() }) { availableWidth in
            let cardWidth = max(0, availableWidth - LNSpacing.screenHorizontal * 2)

            VStack(spacing: 16) {
                TextField(L10n.vaultSearchTitle, text: $query)
                    .padding(12)
                    .frame(width: cardWidth)
                    .background(LNColor.sectionBg)
                    .clipShape(RoundedRectangle(cornerRadius: LNRadius.homeThumb))
                    .overlay(RoundedRectangle(cornerRadius: LNRadius.homeThumb).stroke(LNColor.stroke, lineWidth: 1))
                    .foregroundStyle(LNColor.title)

                if !results.isEmpty {
                    VaultMediaGridCard(items: results, width: cardWidth) { item in
                        if item.isVideo {
                            router.pushVault(.videoPlayer(path: item.path, source: .search(query: query)))
                        } else {
                            router.pushVault(.photoViewer(path: item.path, isTrash: false, source: .search(query: query)))
                        }
                    }
                    .accessibilityIdentifier("vault_search_results_grid")
                }
            }
            .padding(.horizontal, LNSpacing.screenHorizontal)
            .padding(.top, 22)
            .padding(.bottom, 28)
        }
        .onAppear { Task { await vaultStore.loadSnapshot() } }
    }
}

struct PhotoViewerView: View {
    @EnvironmentObject private var router: AppRouter
    @EnvironmentObject private var vaultStore: VaultStore
    @Environment(\.dismiss) private var dismiss
    let path: String
    var isTrash: Bool = false
    var source: PhotoViewerSource = .recent
    var onOpenAlbum: ((String) -> Void)? = nil

    @State private var showDelete = false
    @State private var showPurge = false
    @State private var currentPath: String
    @State private var orderedItems: [LNMediaItem]
    @State private var isPreparingShare = false
    @State private var isExportingSystem = false
    @State private var shareURL: URL?
    @State private var showShareSheet = false
    @State private var showShareFailure = false
    @State private var exportAlertMessage: String?
    @State private var showExportAlert = false

    init(
        path: String,
        isTrash: Bool = false,
        source: PhotoViewerSource = .recent,
        onOpenAlbum: ((String) -> Void)? = nil
    ) {
        self.path = path
        self.isTrash = isTrash
        self.source = source
        self.onOpenAlbum = onOpenAlbum
        _currentPath = State(initialValue: path)
        _orderedItems = State(initialValue: [Self.fallbackItem(path: path, isVideo: false)])
    }

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                LNColor.bgBottom.ignoresSafeArea()
                TabView(selection: $currentPath) {
                    ForEach(displayItems) { item in
                        PhotoViewerPage(item: item)
                            .tag(item.path)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .ignoresSafeArea()

                PhotoViewerTopChrome(
                    current: currentIndex + 1,
                    total: displayItems.count,
                    topInset: proxy.safeAreaInsets.top,
                    onBack: { dismiss() }
                )

                if isTrash {
                    PhotoViewerTrashDock(
                        bottomInset: proxy.safeAreaInsets.bottom,
                        onRecover: { Task { await restoreFromTrash() } },
                        onDelete: { showPurge = true }
                    )
                } else {
                    PhotoViewerActionDock(
                        bottomInset: proxy.safeAreaInsets.bottom,
                        isPreparingShare: isPreparingShare,
                        isExportingSystem: isExportingSystem,
                        onShare: prepareShare,
                        onRedact: { router.pushInCurrentTab(.privacyRedact(path: currentPath)) },
                        onExportSystem: exportToSystemPhotos,
                        onDelete: { showDelete = true }
                    )
                }
            }
        }
        .task { await reloadOrderedPaths() }
        .onChange(of: currentPath) { newPath in
            guard let item = displayItems.first(where: { $0.path == newPath }), item.isVideo else { return }
            router.replaceCurrentTab(with: .videoPlayer(path: item.path, isTrash: isTrash, source: source))
        }
        .overlay { dialogOverlays }
        .sheet(isPresented: $showShareSheet) {
            if let shareURL {
                PhotoShareSheet(url: shareURL) {
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
        }
        .alert(L10n.tr("photo_viewer_share_failed"), isPresented: $showShareFailure) {
            Button(L10n.commonOk, role: .cancel) {}
        }
        .alert(exportAlertMessage ?? "", isPresented: $showExportAlert) {
            Button(L10n.commonOk, role: .cancel) {}
        }
        .edgeSwipeBack { dismiss() }
    }

    private func restoreFromTrash() async {
        if let album = await vaultStore.restoreFromTrash(path: currentPath) {
            if let onOpenAlbum { onOpenAlbum(album) }
            dismiss()
        }
    }

    private var displayItems: [LNMediaItem] {
        orderedItems.isEmpty ? [Self.fallbackItem(path: currentPath, isVideo: false)] : orderedItems
    }

    private var currentIndex: Int {
        displayItems.firstIndex { $0.path == currentPath } ?? 0
    }

    @MainActor
    private func reloadOrderedPaths() async {
        currentPath = path
        let items = await source.loadMediaItems(isTrash: isTrash, vaultStore: vaultStore)
        let currentItem = items.first { $0.path == path } ?? Self.fallbackItem(path: path, isVideo: false)
        orderedItems = items.contains(where: { $0.path == path }) ? items : [currentItem] + items
    }

    private func removeCurrentFromList() {
        let oldIndex = currentIndex
        let remaining = displayItems.filter { $0.path != currentPath }
        guard !remaining.isEmpty else {
            dismiss()
            return
        }
        orderedItems = remaining
        currentPath = remaining[min(oldIndex, remaining.count - 1)].path
    }

    private func prepareShare() {
        guard !isPreparingShare else { return }
        isPreparingShare = true
        let pathToShare = currentPath
        Task {
            do {
                let url = try await makeShareURL(for: pathToShare)
                await MainActor.run {
                    shareURL = url
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
        isExportingSystem = true
        let pathToExport = currentPath
        Task {
            var tempURL: URL?
            defer {
                if let tempURL {
                    PlaintextTempFileManager.shared.removeItem(tempURL)
                }
            }

            do {
                let url = try await makeShareURL(for: pathToExport)
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

    private func makeShareURL(for path: String) async throws -> URL {
        try await Task.detached(priority: .userInitiated) {
            let sourceURL = URL(fileURLWithPath: path)
            let name = sourceURL.lastPathComponent.isEmpty ? "LumaNox.jpg" : sourceURL.lastPathComponent
            return try PlaintextTempFileManager.shared.decryptVaultFileToTemporary(
                sourceURL: sourceURL,
                scene: .share,
                preferredName: name
            )
        }.value
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

    @ViewBuilder
    private var dialogOverlays: some View {
        if showDelete {
            LNDialog(
                title: L10n.tr("photo_viewer_delete_title"),
                message: L10n.tr("photo_viewer_delete_message"),
                confirmTitle: L10n.commonConfirm,
                dismissTitle: L10n.commonCancel,
                confirmVariant: .danger,
                onConfirm: {
                    showDelete = false
                    Task {
                        if await vaultStore.moveToTrash(path: currentPath) {
                            await vaultStore.loadSnapshot()
                            removeCurrentFromList()
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
                        if await vaultStore.purgeFromTrash(path: currentPath) { removeCurrentFromList() }
                    }
                },
                onDismiss: { showPurge = false }
            )
        }
    }
}

private struct PhotoViewerPage: View {
    let item: LNMediaItem

    var body: some View {
        ZStack {
            if item.isVideo {
                VaultMediaThumbnailView(
                    encryptedPath: item.path,
                    isVideo: true,
                    contentMode: .fit,
                    targetPixelSize: 960,
                    showVideoIndicator: false
                )
            } else {
                VaultThumbnailView(encryptedPath: item.path)
            }

            if item.isVideo {
                Image(systemName: "play.fill")
                    .font(.system(size: 34, weight: .bold))
                    .foregroundStyle(LNColor.title)
                    .frame(width: 74, height: 74)
                    .background(LNColor.navBarBg.opacity(0.66))
                    .clipShape(Circle())
                    .overlay(Circle().stroke(LNColor.strokeStrong, lineWidth: 1))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(LNColor.bgBottom)
    }
}

private struct PhotoViewerTopChrome: View {
    let current: Int
    let total: Int
    let topInset: CGFloat
    let onBack: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            ZStack(alignment: .top) {
                LinearGradient(
                    colors: [LNColor.bgBottom.opacity(0.96), LNColor.bgBottom.opacity(0)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                HStack {
                    Button(action: onBack) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(LNColor.title)
                            .frame(width: LNSpacing.minTouchTarget, height: LNSpacing.minTouchTarget)
                            .background(LNColor.navBarBg.opacity(0.8))
                            .clipShape(RoundedRectangle(cornerRadius: LNRadius.topBarButton))
                    }
                    .buttonStyle(.lnPressable(scale: 0.94, pressedOpacity: 0.78))
                    .accessibilityLabel(L10n.commonBack)

                    Spacer()

                    Text(L10n.tr("photo_viewer_counter", current, total))
                        .font(LNTypography.labelMedium().weight(.semibold))
                        .foregroundStyle(LNColor.subtitle)

                    Spacer()

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

private struct PhotoViewerActionDock: View {
    let bottomInset: CGFloat
    let isPreparingShare: Bool
    let isExportingSystem: Bool
    let onShare: () -> Void
    let onRedact: () -> Void
    let onExportSystem: () -> Void
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
                HStack(spacing: 4) {
                    PhotoViewerDockButton(
                        title: L10n.tr("photo_viewer_share"),
                        systemImage: isPreparingShare ? "hourglass" : "square.and.arrow.up",
                        foreground: LNColor.title,
                        action: onShare
                    )
                    PhotoViewerDockButton(
                        title: L10n.tr("photo_viewer_redact"),
                        systemImage: "eye.slash",
                        foreground: LNColor.amberWarning,
                        background: LNColor.amberWarning.opacity(0.10),
                        action: onRedact
                    )
                    PhotoViewerDockButton(
                        title: L10n.tr("photo_viewer_export_system"),
                        systemImage: isExportingSystem ? "hourglass" : "square.and.arrow.down",
                        foreground: LNColor.title,
                        action: onExportSystem
                    )
                    PhotoViewerDockButton(
                        title: L10n.tr("photo_viewer_delete"),
                        systemImage: "trash",
                        foreground: LNColor.buttonDangerFg,
                        action: onDelete
                    )
                }
                .padding(6)
                .frame(height: 78)
                .background(LNColor.navBarBg.opacity(0.82))
                .clipShape(RoundedRectangle(cornerRadius: 26))
                .overlay(
                    RoundedRectangle(cornerRadius: 26)
                        .stroke(LNColor.strokeStrong, lineWidth: 1)
                )
                .padding(.horizontal, LNSpacing.screenHorizontal)
                .padding(.bottom, bottomInset + 14)
            }
            .frame(height: bottomInset + 170)
        }
        .ignoresSafeArea()
    }
}

private struct PhotoViewerTrashDock: View {
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

private struct PhotoViewerDockButton: View {
    let title: String
    let systemImage: String
    let foreground: Color
    var background: Color = .clear
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 5) {
                Image(systemName: systemImage)
                    .font(.system(size: 21, weight: .semibold))
                Text(title)
                    .font(.system(size: 11, weight: .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.78)
            }
            .foregroundStyle(foreground)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(background)
            .clipShape(RoundedRectangle(cornerRadius: 21))
        }
        .buttonStyle(.lnPressable(scale: 0.94, pressedOpacity: 0.76))
        .accessibilityLabel(title)
    }
}

private struct PhotoShareSheet: UIViewControllerRepresentable {
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

struct TrashBinView: View {
    @EnvironmentObject private var router: AppRouter
    @EnvironmentObject private var vaultStore: VaultStore
    @Environment(\.dismiss) private var dismiss
    @State private var trashItems: [LNMediaItem] = []

    var body: some View {
        VaultListScreenChrome(title: L10n.trashTitle, onBack: { dismiss() }) { availableWidth in
            let cardWidth = max(0, availableWidth - LNSpacing.screenHorizontal * 2)

            VStack(alignment: .leading, spacing: 16) {
                if trashItems.isEmpty {
                    Text(L10n.tr("trash_empty"))
                        .font(LNTypography.bodyMedium())
                        .foregroundStyle(LNColor.subtitle)
                        .frame(width: cardWidth, alignment: .leading)
                } else {
                    Text(L10n.tr("trash_retain_hint"))
                        .font(LNTypography.labelMedium())
                        .foregroundStyle(LNColor.subtitle)
                        .frame(width: cardWidth, alignment: .leading)

                    VaultMediaGridCard(items: trashItems, width: cardWidth) { item in
                        if item.isVideo {
                            router.pushSettings(.videoPlayer(path: item.path, isTrash: true, source: .trash))
                        } else {
                            router.pushSettings(.photoViewer(path: item.path, isTrash: true, source: .trash))
                        }
                    }
                    .accessibilityIdentifier("trash_media_grid")
                }
            }
            .padding(.horizontal, LNSpacing.screenHorizontal)
            .padding(.top, 22)
            .padding(.bottom, 28)
        }
        .task { await reloadTrash() }
    }

    private func reloadTrash() async {
        let items = await vaultStore.listTrashItems()
        trashItems = items.map { $0.toMediaItem() }
    }
}
