import CryptoKit
import Foundation

@MainActor
final class VaultStore: ObservableObject {
    static let shared = VaultStore()

    @Published private(set) var snapshot: VaultSnapshot?
    @Published var isImporting = false
    @Published var lastImportMessage: String?
    @Published var lastImportIsError = false

    let fileManager = FileManager.default
    let cipher = VaultCipher.shared
    let metadataStore = VaultMetadataStore.shared
    let trashRetainMs: TimeInterval = 30 * 24 * 60 * 60

    private init() {}

    func invalidateCache() {
        snapshot = nil
    }

    func rootDirectory() throws -> URL {
        let docs = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let root = docs.appendingPathComponent("vault_albums", isDirectory: true)
        if !fileManager.fileExists(atPath: root.path) {
            try fileManager.createDirectory(at: root, withIntermediateDirectories: true)
        }
        let defaultAlbum = root.appendingPathComponent(vaultDefaultAlbumName, isDirectory: true)
        if !fileManager.fileExists(atPath: defaultAlbum.path) {
            try fileManager.createDirectory(at: defaultAlbum, withIntermediateDirectories: true)
        }
        return root
    }

    func loadSnapshot(recentLimit: Int = 60) async {
        do {
            let root = try rootDirectory()
            let trash = try trashDirectory()
            let metadata = try metadataStore.reconcile(vaultRoot: root, trashRoot: trash)
            let albums = metadata.albums.map {
                VaultAlbum(id: $0.id, name: $0.name, photoCount: $0.mediaCount)
            }
            let recent = metadata.recentActive(limit: recentLimit).map { mediaRecordToVaultPhoto($0) }
            let total = metadata.totalActiveCount
            snapshot = VaultSnapshot(
                albums: albums,
                recentPhotos: recent,
                totalCount: total
            )
            QuotaManager.shared.updateVaultCount(total)
        } catch {
            lastImportMessage = error.localizedDescription
            lastImportIsError = true
        }
    }

    func photos(in albumName: String) -> [VaultPhoto] {
        metadataStore
            .load()
            .activeMedia(in: albumName)
            .map { mediaRecordToVaultPhoto($0) }
    }

    func searchPhotos(query: String, limit: Int = 200) -> [VaultPhoto] {
        metadataStore
            .search(query: query, limit: limit)
            .map { mediaRecordToVaultPhoto($0) }
    }

    func storageSummary() -> VaultStorageSummary {
        metadataStore.storageSummary()
    }

    func createAlbum(named name: String) throws -> String {
        let safe = sanitizeAlbumName(name)
        let dir = try rootDirectory().appendingPathComponent(safe, isDirectory: true)
        try fileManager.createDirectory(at: dir, withIntermediateDirectories: true)
        return safe
    }

    func importPlainData(_ data: Data, fileExtension: String, albumName: String = vaultDefaultAlbumName) async -> VaultImportResult {
        do {
            let safeAlbum = (try? createAlbum(named: albumName)) ?? vaultDefaultAlbumName
            let albumDir = try rootDirectory().appendingPathComponent(safeAlbum, isDirectory: true)
            let ext = fileExtension.lowercased()
            let hash = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
            let dest = albumDir.appendingPathComponent("asset_\(hash).\(ext)")
            if fileManager.fileExists(atPath: dest.path) { return .duplicate }

            let temp = albumDir.appendingPathComponent("tmp_\(UUID().uuidString).\(ext)")
            try data.write(to: temp, options: .atomic)
            defer { try? fileManager.removeItem(at: temp) }
            try cipher.encryptFile(at: temp, to: dest)
            return .added
        } catch {
            return .failed
        }
    }

    func finalizeImportBatch(_ summary: VaultImportSummary) async {
        await loadSnapshot()
        lastImportMessage = formatImportMessage(summary)
        lastImportIsError = summary.added == 0 && summary.failed > 0
    }

    func beginImportBatch() {
        isImporting = true
    }

    func endImportBatch() {
        isImporting = false
    }

    func isVaultMediaFile(_ url: URL) -> Bool {
        guard !url.hasDirectoryPath else { return false }
        let name = url.lastPathComponent
        if name.hasPrefix(".") { return false }
        if name.contains(".enc_tmp_") { return false }
        if name == ".vault_encrypted_v1" { return false }
        return true
    }

    private func mediaRecordToVaultPhoto(_ record: VaultMediaRecord) -> VaultPhoto {
        let docs = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return VaultPhoto(
            id: record.id,
            albumName: record.albumName,
            path: record.absoluteURL(documentsDirectory: docs).path,
            name: record.fileName,
            modifiedAt: Date(timeIntervalSince1970: Double(record.modifiedAtMs) / 1000),
            isVideo: record.isVideo,
            sizeBytes: record.encryptedSizeBytes
        )
    }

    func sanitizeAlbumName(_ name: String) -> String {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let safe = trimmed.filter { $0.isLetter || $0.isNumber || $0 == "_" || $0 == "-" || $0 == " " }
        return safe.replacingOccurrences(of: " ", with: "_").isEmpty ? vaultDefaultAlbumName : safe
    }

    private func formatImportMessage(_ s: VaultImportSummary) -> String {
        if s.added > 0 {
            return L10n.tr("home_import_success_count", s.added)
        }
        if s.duplicate > 0 && s.failed == 0 {
            return L10n.tr("home_import_duplicate_count", s.duplicate)
        }
        if s.failed > 0 {
            return L10n.tr("home_import_failed")
        }
        return L10n.tr("home_import_none")
    }
}
