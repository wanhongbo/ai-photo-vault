import CryptoKit
import Foundation

private let importDataStreamThresholdBytes = 8 * 1024 * 1024

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

    func importPlainData(
        _ data: Data,
        fileExtension: String,
        albumName: String = vaultDefaultAlbumName,
        originalFileName: String? = nil
    ) async -> VaultImportResult {
        if data.count >= importDataStreamThresholdBytes {
            let ext = fileExtension.lowercased()
            let tempURL: URL
            do {
                tempURL = try PlaintextTempFileManager.shared.makeFileURL(
                    for: .importStaging,
                    preferredBaseName: "import_\(UUID().uuidString)",
                    fileExtension: ext
                )
                try data.write(to: tempURL, options: .atomic)
            } catch {
                return .failed
            }
            defer { PlaintextTempFileManager.shared.removeItem(tempURL) }
            return await importPlainFile(
                at: tempURL,
                fileExtension: ext,
                albumName: albumName,
                originalFileName: originalFileName,
                source: .picker
            )
        }

        do {
            let safeAlbum = (try? createAlbum(named: albumName)) ?? vaultDefaultAlbumName
            let albumDir = try rootDirectory().appendingPathComponent(safeAlbum, isDirectory: true)
            let ext = fileExtension.lowercased()
            let hash = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
            let dest = albumDir.appendingPathComponent("asset_\(hash).\(ext)")
            if fileManager.fileExists(atPath: dest.path), canReadExistingVaultMedia(at: dest) {
                return .duplicate
            }

            let temp = try PlaintextTempFileManager.shared.makeFileURL(
                for: .importStaging,
                preferredBaseName: "import_\(UUID().uuidString)",
                fileExtension: ext
            )
            try data.write(to: temp, options: .atomic)
            defer { PlaintextTempFileManager.shared.removeItem(temp) }
            try cipher.encryptFile(at: temp, to: dest)
            try metadataStore.recordImportedMedia(
                encryptedURL: dest,
                albumName: safeAlbum,
                plainURL: temp,
                plainSha256Hex: hash,
                source: .picker,
                originalFileName: originalFileName
            )
            return .added
        } catch {
            return .failed
        }
    }

    func importPlainFile(
        at sourceURL: URL,
        fileExtension: String,
        albumName: String = vaultDefaultAlbumName,
        originalFileName: String? = nil,
        source: VaultMediaSource = .picker
    ) async -> VaultImportResult {
        do {
            let safeAlbum = (try? createAlbum(named: albumName)) ?? vaultDefaultAlbumName
            let albumDir = try rootDirectory().appendingPathComponent(safeAlbum, isDirectory: true)
            let ext = fileExtension.lowercased()
            let hash = try sha256Hex(of: sourceURL)
            let dest = albumDir.appendingPathComponent("asset_\(hash).\(ext)")
            if fileManager.fileExists(atPath: dest.path), canReadExistingVaultMedia(at: dest) {
                return .duplicate
            }

            try cipher.encryptFileFromChunks(to: dest) { sink in
                let handle = try FileHandle(forReadingFrom: sourceURL)
                defer { try? handle.close() }
                while let chunk = try handle.read(upToCount: 64 * 1024), !chunk.isEmpty {
                    try sink(chunk)
                }
            }
            try metadataStore.recordImportedMedia(
                encryptedURL: dest,
                albumName: safeAlbum,
                plainURL: sourceURL,
                plainSha256Hex: hash,
                source: source,
                originalFileName: originalFileName ?? sourceURL.lastPathComponent
            )
            invalidateCache()
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
        if s.added > 0 && (s.duplicate > 0 || s.failed > 0) {
            return L10n.tr("home_import_multi_result", s.added, s.duplicate, s.failed)
        }
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

    func sha256Hex(of url: URL) throws -> String {
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        var digest = SHA256()
        while let chunk = try handle.read(upToCount: 64 * 1024), !chunk.isEmpty {
            digest.update(data: chunk)
        }
        return digest.finalize().map { String(format: "%02x", $0) }.joined()
    }

    private func canReadExistingVaultMedia(at url: URL) -> Bool {
        if let decrypted = try? cipher.decryptFile(at: url), looksLikeMediaData(decrypted) {
            return true
        }
        if let raw = try? Data(contentsOf: url), looksLikeMediaData(raw) {
            return true
        }
        return false
    }

    private func looksLikeMediaData(_ data: Data) -> Bool {
        let bytes = Array(data.prefix(16))
        guard bytes.count >= 4 else { return false }

        if bytes.starts(with: [0xFF, 0xD8, 0xFF]) { return true }
        if bytes.starts(with: [0x89, 0x50, 0x4E, 0x47]) { return true }
        if bytes.starts(with: [0x47, 0x49, 0x46, 0x38]) { return true }
        if bytes.starts(with: [0x49, 0x49, 0x2A, 0x00]) || bytes.starts(with: [0x4D, 0x4D, 0x00, 0x2A]) {
            return true
        }
        if bytes.count >= 12,
           bytes[0...3].elementsEqual([0x52, 0x49, 0x46, 0x46]),
           bytes[8...11].elementsEqual([0x57, 0x45, 0x42, 0x50]) {
            return true
        }
        if bytes.count >= 12,
           bytes[4...7].elementsEqual([0x66, 0x74, 0x79, 0x70]) {
            return true
        }
        return false
    }
}
