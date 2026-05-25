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
    @Published private(set) var snapshot: VaultSnapshot?
    @Published private(set) var isLoadingSnapshot = false
    @Published private(set) var hasPinConfigured = AppDebugPolicy.skipsPinGate || SecuritySettingsStore.shared.hasPinConfigured

    private let vaultStore = VaultStore.shared
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

    var isEmpty: Bool { recentPhotos.isEmpty }
    var shouldShowInitialLoading: Bool { snapshot == nil && isLoadingSnapshot }
    var shouldShowEmptyState: Bool { snapshot != nil && isEmpty && !isImporting }
    var totalCount: Int { snapshot?.totalCount ?? 0 }
    var imageCount: Int { snapshot?.imageCount ?? 0 }
    var videoCount: Int { snapshot?.videoCount ?? 0 }

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
        PHPhotoLibrary.requestAuthorization(for: .readWrite) { _ in
            Task { @MainActor in
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
        Task {
            vaultStore.beginImportBatch()
            let summary = await PhotosPickerVaultImporter.importItems(items, into: vaultDefaultAlbumName, vaultStore: vaultStore)
            vaultStore.endImportBatch()
            await vaultStore.finalizeImportBatch(summary)
            duplicateImportDialogMessage = VaultImportFeedback.duplicateDialogMessage(for: summary)
            snapshot = vaultStore.snapshot
        }
        return true
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

enum VaultImportFeedback {
    static func duplicateDialogMessage(for summary: VaultImportSummary) -> String? {
        guard summary.duplicate > 0, summary.added == 0, summary.failed == 0 else { return nil }
        if summary.duplicate == 1 {
            return L10n.tr("home_import_duplicate_dialog_message")
        }
        return L10n.tr("home_import_duplicate_dialog_message_count", summary.duplicate)
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
        for item in items {
            switch await importItem(item, into: albumName, vaultStore: vaultStore) {
            case .added: summary.added += 1
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
