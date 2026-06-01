import Combine
import CoreTransferable
import Foundation
import Photos
import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

@MainActor
final class VaultHomeViewModel: ObservableObject {
    @Published var pickerItems: [PhotosPickerItem] = []
    @Published var showPermissionDenied = false
    @Published var showCreateAlbum = false
    @Published var newAlbumName = ""
    @Published var duplicateImportDialogMessage: String?
    @Published var pendingImportOriginalsCount: Int?
    @Published var rememberImportOriginalsChoice = false
    @Published var originalsActionDialogMessage: String?
    @Published var importToast: VaultHomeImportToast?
    @Published var softPaywallReason: SoftPaywallReason?
    @Published var hardPaywallSource: String?
    @Published private(set) var snapshot: VaultSnapshot?
    @Published private(set) var isLoadingSnapshot = false
    @Published private(set) var hasPinConfigured = AppDebugPolicy.skipsPinGate || SecuritySettingsStore.shared.hasPinConfigured

    private let vaultStore = VaultStore.shared
    private let importOriginalsPreference = ImportOriginalsPreferenceStore.shared
    private var pendingPickerItems: [PhotosPickerItem] = []
    private var cancellables = Set<AnyCancellable>()

    init() {
        self.snapshot = vaultStore.snapshot
        self.isLoadingSnapshot = vaultStore.snapshot == nil

        vaultStore.$snapshot
            .sink { [weak self] snapshot in
                guard let self else { return }
                self.snapshot = snapshot
                if snapshot != nil {
                    self.isLoadingSnapshot = false
                }
            }
            .store(in: &cancellables)

        vaultStore.objectWillChange
            .sink { [weak self] _ in
                self?.objectWillChange.send()
            }
            .store(in: &cancellables)
    }

    var isImporting: Bool { vaultStore.isImporting }
    var importTip: String? { vaultStore.lastImportMessage }
    var importTipIsError: Bool { vaultStore.lastImportIsError }

    var albums: [VaultAlbum] {
        snapshot?.albums ?? []
    }

    var recentPhotos: [LNMediaItem] {
        snapshot?.recentPhotos.map { $0.toMediaItem() } ?? []
    }

    func recentMediaItems(for album: VaultAlbum, limit: Int = 4) -> [LNMediaItem] {
        vaultStore
            .photos(for: album)
            .prefix(limit)
            .map { $0.toMediaItem() }
    }

    var isEmpty: Bool { recentPhotos.isEmpty }
    var hasUserCreatedAlbum: Bool {
        albums.contains { $0.source == .user && $0.name != vaultDefaultAlbumName }
    }
    var shouldShowInitialLoading: Bool { snapshot == nil && isLoadingSnapshot }
    var shouldShowEmptyState: Bool { snapshot != nil && isEmpty && !hasUserCreatedAlbum && !isImporting }
    var shouldShowFloatingImportButton: Bool {
        snapshot != nil && !isEmpty && !showPermissionDenied
    }
    var shouldHideBottomTabBar: Bool {
        showCreateAlbum ||
            duplicateImportDialogMessage != nil ||
            pendingImportOriginalsCount != nil ||
            originalsActionDialogMessage != nil
    }
    var totalCount: Int { snapshot?.totalCount ?? 0 }
    var imageCount: Int { snapshot?.imageCount ?? 0 }
    var videoCount: Int { snapshot?.videoCount ?? 0 }
    var heroStatusText: String {
        if videoCount == 0 {
            return L10n.tr("home_vault_status_photos", imageCount)
        }
        return L10n.tr("home_vault_status_items", totalCount)
    }

    func onAppear() {
        refreshAuthorization()
        hasPinConfigured = AppDebugPolicy.skipsPinGate || SecuritySettingsStore.shared.hasPinConfigured
        Task {
            if snapshot == nil {
                isLoadingSnapshot = true
            }
            await vaultStore.loadSnapshot()
            snapshot = vaultStore.snapshot
            isLoadingSnapshot = false
        }
    }

    func refreshAuthorization() {
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        showPermissionDenied = status == .denied || status == .restricted
    }

    func requestPhotoAccess() {
        let lockToken = AppLockManager.shared.beginSystemInteraction()
        PHPhotoLibrary.requestAuthorization(for: .readWrite) { _ in
            Task { @MainActor in
                AppLockManager.shared.endSystemInteraction(lockToken)
                self.refreshAuthorization()
            }
        }
    }

    func onPickerItemsChanged(router: AppRouter) -> Bool {
        guard !pickerItems.isEmpty else { return true }
        guard router.guardProFeature(.vaultImport) else {
            pickerItems = []
            return false
        }
        let items = pickerItems
        pickerItems = []
        if importOriginalsPreference.action == .askEachTime {
            pendingPickerItems = items
            pendingImportOriginalsCount = items.count
            rememberImportOriginalsChoice = false
            return true
        }
        Task {
            await importPickedItems(items, originalsAction: importOriginalsPreference.action)
        }
        return true
    }

    func confirmPendingImportOriginals(action: ImportOriginalsAction) {
        guard !pendingPickerItems.isEmpty else { return }
        if rememberImportOriginalsChoice {
            importOriginalsPreference.action = action
        }
        let items = pendingPickerItems
        pendingPickerItems = []
        pendingImportOriginalsCount = nil
        rememberImportOriginalsChoice = false
        Task {
            await importPickedItems(items, originalsAction: action)
        }
    }

    private func importPickedItems(
        _ items: [PhotosPickerItem],
        originalsAction: ImportOriginalsAction
    ) async {
        vaultStore.beginImportBatch()
        let summary = await PhotosPickerVaultImporter.importItems(items, into: vaultDefaultAlbumName, vaultStore: vaultStore)
        vaultStore.endImportBatch()
        await vaultStore.finalizeImportBatch(summary)
        if originalsAction == .deleteOriginals, summary.added > 0 {
            originalsActionDialogMessage = await PhotosOriginalDeletionService.deleteImportedAssets(
                localIdentifiers: summary.importedPhotoLibraryAssetIdentifiers
            )
        }
        duplicateImportDialogMessage = VaultImportFeedback.duplicateDialogMessage(for: summary)
        if duplicateImportDialogMessage == nil {
            importToast = VaultHomeImportToast(
                message: VaultImportFeedback.inlineMessage(for: summary),
                isError: summary.added == 0 && summary.failed > 0
            )
        }
        snapshot = vaultStore.snapshot
        if summary.quotaExceeded {
            hardPaywallSource = PaywallSource.quotaVault
        } else if summary.added > 0 && originalsAction != .deleteOriginals {
            softPaywallReason = PaywallPromptManager.promptAfterVaultImport(currentVaultCount: totalCount)
        }
    }

    func createAlbum(router: AppRouter) {
        let name = newAlbumName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }
        if let safe = try? vaultStore.createAlbum(named: name) {
            router.pushVault(.album(name: safe))
        }
        newAlbumName = ""
        showCreateAlbum = false
        Task {
            await vaultStore.loadSnapshot()
            snapshot = vaultStore.snapshot
        }
    }
}

struct VaultHomeImportToast: Identifiable, Equatable {
    let id = UUID()
    let message: String
    let isError: Bool
}

enum VaultImportFeedback {
    static func duplicateDialogMessage(for summary: VaultImportSummary) -> String? {
        guard summary.duplicate > 0, summary.added == 0, summary.failed == 0 else { return nil }
        if summary.duplicate == 1 {
            return L10n.tr("home_import_duplicate_dialog_message")
        }
        return L10n.tr("home_import_duplicate_dialog_message_count", summary.duplicate)
    }

    static func inlineMessage(for summary: VaultImportSummary) -> String {
        if summary.added > 0 && (summary.duplicate > 0 || summary.failed > 0) {
            return L10n.tr("home_import_multi_result", summary.added, summary.duplicate, summary.failed)
        }
        if summary.added > 0 {
            return L10n.tr("home_import_success_count", summary.added)
        }
        if summary.duplicate > 0 && summary.failed == 0 {
            return L10n.tr("home_import_duplicate_count", summary.duplicate)
        }
        if summary.failed > 0 {
            return L10n.tr("home_import_failed")
        }
        return L10n.tr("home_import_none")
    }
}

private struct VaultPickedMediaFile: Transferable {
    let url: URL
    let originalFileName: String

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(contentType: .movie) { file in
            SentTransferredFile(file.url)
        } importing: { received in
            try Self.importReceivedFile(received.file)
        }
        FileRepresentation(contentType: .image) { file in
            SentTransferredFile(file.url)
        } importing: { received in
            try Self.importReceivedFile(received.file)
        }
        FileRepresentation(contentType: .item) { file in
            SentTransferredFile(file.url)
        } importing: { received in
            try Self.importReceivedFile(received.file)
        }
    }

    private static func importReceivedFile(_ sourceURL: URL) throws -> VaultPickedMediaFile {
        let copy = try PlaintextTempFileManager.shared.copyFileToTemporary(
            sourceURL: sourceURL,
            scene: .importStaging,
            preferredName: sourceURL.lastPathComponent
        )
        return VaultPickedMediaFile(url: copy, originalFileName: sourceURL.lastPathComponent)
    }
}

@MainActor
enum PhotosPickerVaultImporter {
    static func importItems(
        _ items: [PhotosPickerItem],
        into albumName: String,
        vaultStore: VaultStore
    ) async -> VaultImportSummary {
        var summary = VaultImportSummary()
        let isPremium = SubscriptionService.shared.isPremium
        var importedCount = vaultStore.snapshot?.totalCount ?? vaultStore.storageSummary().activeCount
        for item in items {
            if !isPremium, importedCount >= FreeQuota.maxVaultItems {
                summary.quotaExceeded = true
                break
            }
            switch await importItem(item, into: albumName, vaultStore: vaultStore) {
            case .added:
                summary.added += 1
                importedCount += 1
                QuotaManager.shared.updateVaultCount(importedCount)
                if let identifier = item.itemIdentifier {
                    summary.importedPhotoLibraryAssetIdentifiers.append(identifier)
                }
            case .duplicate: summary.duplicate += 1
            case .failed: summary.failed += 1
            }
        }
        return summary
    }

    private static func importItem(
        _ item: PhotosPickerItem,
        into albumName: String,
        vaultStore: VaultStore
    ) async -> VaultImportResult {
        if let picked = try? await item.loadTransferable(type: VaultPickedMediaFile.self) {
            defer { PlaintextTempFileManager.shared.removeItem(picked.url) }
            return await vaultStore.importPlainFile(
                at: picked.url,
                fileExtension: extensionForPickerItem(item, fallback: picked.url.pathExtension),
                albumName: albumName,
                originalFileName: picked.originalFileName
            )
        }

        if let data = try? await item.loadTransferable(type: Data.self) {
            return await vaultStore.importPlainData(
                data,
                fileExtension: extensionForPickerItem(item),
                albumName: albumName,
                originalFileName: nil
            )
        }

        return .failed
    }

    private static func extensionForPickerItem(_ item: PhotosPickerItem, fallback: String = "jpg") -> String {
        if let type = item.supportedContentTypes.first(where: { $0.conforms(to: .movie) }) {
            return normalizedExtension(type.preferredFilenameExtension, fallback: "mp4")
        }
        if let type = item.supportedContentTypes.first(where: { $0.conforms(to: .image) }) {
            if type.conforms(to: .png) { return "png" }
            if type.conforms(to: .heic) { return "heic" }
            return normalizedExtension(type.preferredFilenameExtension, fallback: fallback)
        }
        if let type = item.supportedContentTypes.first {
            return normalizedExtension(type.preferredFilenameExtension, fallback: fallback)
        }
        return normalizedExtension(nil, fallback: fallback)
    }

    private static func normalizedExtension(_ value: String?, fallback: String) -> String {
        let rawValue = (value?.isEmpty == false ? value : nil) ?? fallback
        let ext = rawValue
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: ".", with: "")
            .lowercased()
        if ext == "qt" { return "mov" }
        return ext.isEmpty ? "jpg" : ext
    }
}

@MainActor
enum PhotosOriginalDeletionService {
    static func deleteImportedAssets(localIdentifiers: [String]) async -> String? {
        let uniqueIdentifiers = Array(Set(localIdentifiers))
        guard !uniqueIdentifiers.isEmpty else {
            return L10n.tr("import_originals_delete_no_match")
        }

        let authorizationStatus = await readWriteAuthorizationStatus()
        guard authorizationStatus == .authorized || authorizationStatus == .limited else {
            return L10n.tr("import_originals_delete_denied")
        }

        let assets = PHAsset.fetchAssets(withLocalIdentifiers: uniqueIdentifiers, options: nil)
        guard assets.count > 0 else {
            return L10n.tr("import_originals_delete_no_match")
        }

        do {
            try await performDelete(assets)
            return L10n.tr("import_originals_delete_success", assets.count)
        } catch {
            return L10n.tr("import_originals_delete_failed")
        }
    }

    private static func readWriteAuthorizationStatus() async -> PHAuthorizationStatus {
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        guard status == .notDetermined else { return status }
        let lockToken = AppLockManager.shared.beginSystemInteraction()
        return await withCheckedContinuation { continuation in
            PHPhotoLibrary.requestAuthorization(for: .readWrite) { newStatus in
                Task { @MainActor in
                    AppLockManager.shared.endSystemInteraction(lockToken)
                    continuation.resume(returning: newStatus)
                }
            }
        }
    }

    private static func performDelete(_ assets: PHFetchResult<PHAsset>) async throws {
        let lockToken = AppLockManager.shared.beginSystemInteraction()
        defer { AppLockManager.shared.endSystemInteraction(lockToken) }
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            PHPhotoLibrary.shared().performChanges {
                PHAssetChangeRequest.deleteAssets(assets)
            } completionHandler: { success, error in
                if success {
                    continuation.resume()
                } else {
                    continuation.resume(throwing: error ?? NSError(domain: "LumaNox.PhotosDelete", code: 1))
                }
            }
        }
    }
}
