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
    @Published private(set) var snapshot: VaultSnapshot?
    @Published private(set) var hasPinConfigured = debugSkipsPin || SecuritySettingsStore.shared.hasPinConfigured

    private let vaultStore = VaultStore.shared

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
    var totalCount: Int { snapshot?.totalCount ?? 0 }
    var imageCount: Int { snapshot?.recentPhotos.filter { !$0.isVideo }.count ?? 0 }
    var videoCount: Int { snapshot?.recentPhotos.filter(\.isVideo).count ?? 0 }

    func onAppear() {
        refreshAuthorization()
        hasPinConfigured = debugSkipsPin || SecuritySettingsStore.shared.hasPinConfigured
        Task {
            await vaultStore.loadSnapshot()
            snapshot = vaultStore.snapshot
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

private var debugSkipsPin: Bool {
    #if DEBUG
    true
    #else
    false
    #endif
}

private struct VaultPickedMediaFile: Transferable {
    let url: URL

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(contentType: .item) { file in
            SentTransferredFile(file.url)
        } importing: { received in
            let ext = received.file.pathExtension.isEmpty ? "bin" : received.file.pathExtension
            let copy = FileManager.default.temporaryDirectory
                .appendingPathComponent("lumanox_picker_\(UUID().uuidString).\(ext)")
            if FileManager.default.fileExists(atPath: copy.path) {
                try FileManager.default.removeItem(at: copy)
            }
            try FileManager.default.copyItem(at: received.file, to: copy)
            return VaultPickedMediaFile(url: copy)
        }
    }
}

@MainActor
enum PhotosPickerVaultImporter {
    static func importItems(
        _ items: [PhotosPickerItem],
        into albumName: String,
        vaultStore: VaultStore = .shared
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
            defer { try? FileManager.default.removeItem(at: picked.url) }
            return await vaultStore.importPlainFile(
                at: picked.url,
                fileExtension: extensionForPickerItem(item, fallback: picked.url.pathExtension),
                albumName: albumName
            )
        }

        if let data = try? await item.loadTransferable(type: Data.self) {
            return await vaultStore.importPlainData(
                data,
                fileExtension: extensionForPickerItem(item),
                albumName: albumName
            )
        }

        return .failed
    }

    private static func extensionForPickerItem(_ item: PhotosPickerItem, fallback: String = "jpg") -> String {
        if let type = item.supportedContentTypes.first {
            if type.conforms(to: .movie) { return type.preferredFilenameExtension ?? "mp4" }
            if type.conforms(to: .png) { return "png" }
            if type.conforms(to: .heic) { return "heic" }
            return type.preferredFilenameExtension ?? fallback
        }
        return fallback.isEmpty ? "jpg" : fallback
    }
}
