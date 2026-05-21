import CryptoKit
import Foundation

@MainActor
final class VaultMetadataStore {
    static let shared = VaultMetadataStore()

    private let fileManager = FileManager.default
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder
    private var cached: VaultMetadataSnapshot?

    private init() {
        encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        decoder = JSONDecoder()
    }

    func load() -> VaultMetadataSnapshot {
        if let cached { return cached }
        guard let data = try? Data(contentsOf: metadataURL()),
              let snapshot = try? decoder.decode(VaultMetadataSnapshot.self, from: data),
              snapshot.schemaVersion == vaultMetadataSchemaVersion else {
            cached = .empty
            return .empty
        }
        cached = snapshot
        return snapshot
    }

    @discardableResult
    func reconcile(vaultRoot: URL, trashRoot: URL) throws -> VaultMetadataSnapshot {
        let previous = load()
        let previousByPath = Dictionary(uniqueKeysWithValues: previous.media.map { ($0.storagePath, $0) })
        let docs = documentsDirectory()
        var media: [VaultMediaRecord] = []

        media.append(contentsOf: try scanActiveMedia(
            vaultRoot: vaultRoot,
            documentsDirectory: docs,
            previousByPath: previousByPath
        ))
        media.append(contentsOf: try scanTrashedMedia(
            trashRoot: trashRoot,
            documentsDirectory: docs,
            previousByPath: previousByPath
        ))

        let albums = buildAlbums(vaultRoot: vaultRoot, media: media)
        let snapshot = VaultMetadataSnapshot(
            schemaVersion: vaultMetadataSchemaVersion,
            albums: albums,
            media: media.sorted { $0.modifiedAtMs > $1.modifiedAtMs },
            updatedAtMs: Date().epochMs
        )
        try save(snapshot)
        return snapshot
    }

    func mediaRecord(forPath path: String) -> VaultMediaRecord? {
        let docs = documentsDirectory()
        let storagePath = URL(fileURLWithPath: path).relativePath(from: docs)
        return load().media.first { $0.storagePath == storagePath || $0.id == storagePath }
    }

    func search(query: String, limit: Int = 200) -> [VaultMediaRecord] {
        load().searchActive(query: query, limit: limit)
    }

    func storageSummary() -> VaultStorageSummary {
        let snapshot = load()
        return VaultStorageSummary(
            activeCount: snapshot.activeMedia.count,
            trashCount: snapshot.trashedMedia.count,
            albumCount: snapshot.albums.count,
            encryptedBytes: snapshot.media.reduce(Int64(0)) { $0 + $1.encryptedSizeBytes },
            updatedAtMs: snapshot.updatedAtMs
        )
    }

    func recordImportedMedia(
        encryptedURL: URL,
        albumName: String,
        plainURL: URL,
        plainSha256Hex: String,
        source: VaultMediaSource,
        originalFileName: String? = nil
    ) throws {
        let docs = documentsDirectory()
        let storagePath = encryptedURL.relativePath(from: docs)
        let values = try encryptedURL.resourceValues(forKeys: [.contentModificationDateKey, .creationDateKey, .fileSizeKey])
        let now = Date().epochMs
        let createdAtMs = (values.creationDate ?? Date()).epochMs
        let modifiedAtMs = (values.contentModificationDate ?? Date()).epochMs
        let details = MediaMetadataExtractor.extract(from: plainURL)
        var snapshot = load()
        snapshot.media.removeAll { $0.storagePath == storagePath }
        snapshot.media.append(VaultMediaRecord(
            id: storagePath,
            storagePath: storagePath,
            albumName: albumName,
            fileName: encryptedURL.lastPathComponent,
            mediaKind: VaultMediaKind.infer(from: encryptedURL.pathExtension),
            state: .active,
            encryptedSizeBytes: Int64(values.fileSize ?? 0),
            originalSha256Hex: plainSha256Hex,
            encryptedSha256Hex: encryptedSha256Hex(encryptedURL),
            originalFileName: originalFileName,
            mimeType: details.mimeType,
            uti: details.uti,
            cipherVersion: vaultCipherVersionCBCv1,
            createdAtMs: createdAtMs,
            importedAtMs: now,
            modifiedAtMs: modifiedAtMs,
            trashedAtMs: nil,
            width: details.width,
            height: details.height,
            durationMs: details.durationMs,
            source: source,
            ai: .empty
        ))
        let vaultRoot = docs.appendingPathComponent("vault_albums", isDirectory: true)
        snapshot.albums = buildAlbums(vaultRoot: vaultRoot, media: snapshot.media)
        snapshot.media.sort { $0.modifiedAtMs > $1.modifiedAtMs }
        snapshot.updatedAtMs = now
        try save(snapshot)
    }

    private func save(_ snapshot: VaultMetadataSnapshot) throws {
        let url = metadataURL()
        try fileManager.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        let tmp = url.deletingLastPathComponent().appendingPathComponent(".vault_metadata_\(UUID().uuidString).json")
        let data = try encoder.encode(snapshot)
        try data.write(to: tmp, options: .atomic)
        if fileManager.fileExists(atPath: url.path) {
            try fileManager.removeItem(at: url)
        }
        try fileManager.moveItem(at: tmp, to: url)
        cached = snapshot
    }

    private func scanActiveMedia(
        vaultRoot: URL,
        documentsDirectory docs: URL,
        previousByPath: [String: VaultMediaRecord]
    ) throws -> [VaultMediaRecord] {
        let albumDirs = try fileManager.contentsOfDirectory(
            at: vaultRoot,
            includingPropertiesForKeys: [.isDirectoryKey]
        ).filter { $0.hasDirectoryPath }

        var records: [VaultMediaRecord] = []
        for albumURL in albumDirs {
            let albumName = albumURL.lastPathComponent
            let files = try fileManager.contentsOfDirectory(
                at: albumURL,
                includingPropertiesForKeys: [.contentModificationDateKey, .creationDateKey, .fileSizeKey]
            )
            for file in files where isVaultMediaFile(file) {
                records.append(try buildRecord(
                    file: file,
                    documentsDirectory: docs,
                    albumName: albumName,
                    state: .active,
                    previousByPath: previousByPath
                ))
            }
        }
        return records
    }

    private func scanTrashedMedia(
        trashRoot: URL,
        documentsDirectory docs: URL,
        previousByPath: [String: VaultMediaRecord]
    ) throws -> [VaultMediaRecord] {
        guard fileManager.fileExists(atPath: trashRoot.path) else { return [] }
        let entries = try fileManager.contentsOfDirectory(
            at: trashRoot,
            includingPropertiesForKeys: [.isDirectoryKey, .contentModificationDateKey, .creationDateKey, .fileSizeKey]
        )
        var records: [VaultMediaRecord] = []
        for entry in entries {
            let values = try entry.resourceValues(forKeys: [.isDirectoryKey])
            if values.isDirectory == true {
                let albumName = entry.lastPathComponent
                let files = try fileManager.contentsOfDirectory(
                    at: entry,
                    includingPropertiesForKeys: [.contentModificationDateKey, .creationDateKey, .fileSizeKey]
                )
                for file in files where isVaultMediaFile(file) {
                    records.append(try buildRecord(
                        file: file,
                        documentsDirectory: docs,
                        albumName: albumName,
                        state: .trashed,
                        previousByPath: previousByPath
                    ))
                }
            } else if isVaultMediaFile(entry) {
                records.append(try buildRecord(
                    file: entry,
                    documentsDirectory: docs,
                    albumName: vaultDefaultAlbumName,
                    state: .trashed,
                    previousByPath: previousByPath
                ))
            }
        }
        return records
    }

    private func buildRecord(
        file: URL,
        documentsDirectory docs: URL,
        albumName: String,
        state: VaultMediaState,
        previousByPath: [String: VaultMediaRecord]
    ) throws -> VaultMediaRecord {
        let storagePath = file.relativePath(from: docs)
        let values = try file.resourceValues(forKeys: [.contentModificationDateKey, .creationDateKey, .fileSizeKey])
        let modifiedAtMs = (values.contentModificationDate ?? Date()).epochMs
        let createdAtMs = (values.creationDate ?? values.contentModificationDate ?? Date()).epochMs
        let previous = previousByPath[storagePath]
        let trashedAtMs = state == .trashed
            ? previous?.trashedAtMs ?? modifiedAtMs
            : nil

        return VaultMediaRecord(
            id: storagePath,
            storagePath: storagePath,
            albumName: albumName,
            fileName: file.lastPathComponent,
            mediaKind: VaultMediaKind.infer(from: file.pathExtension),
            state: state,
            encryptedSizeBytes: Int64(values.fileSize ?? 0),
            originalSha256Hex: previous?.originalSha256Hex,
            encryptedSha256Hex: previous?.encryptedSha256Hex ?? encryptedSha256Hex(file),
            originalFileName: previous?.originalFileName,
            mimeType: previous?.mimeType,
            uti: previous?.uti,
            cipherVersion: previous?.cipherVersion ?? vaultCipherVersionCBCv1,
            createdAtMs: previous?.createdAtMs ?? createdAtMs,
            importedAtMs: previous?.importedAtMs ?? createdAtMs,
            modifiedAtMs: modifiedAtMs,
            trashedAtMs: trashedAtMs,
            width: previous?.width,
            height: previous?.height,
            durationMs: previous?.durationMs,
            source: previous?.source ?? inferSource(fileName: file.lastPathComponent, state: state),
            ai: previous?.ai ?? .empty
        )
    }

    private func buildAlbums(vaultRoot: URL, media: [VaultMediaRecord]) -> [VaultAlbumRecord] {
        let active = media.filter { $0.state == .active }
        let grouped = Dictionary(grouping: active, by: \.albumName)
        let dirs = (try? fileManager.contentsOfDirectory(
            at: vaultRoot,
            includingPropertiesForKeys: [.creationDateKey, .contentModificationDateKey]
        )) ?? []

        var names = Set(grouped.keys)
        dirs.filter(\.hasDirectoryPath).forEach { names.insert($0.lastPathComponent) }
        names.insert(vaultDefaultAlbumName)

        return names.map { name in
            let files = grouped[name] ?? []
            let albumURL = vaultRoot.appendingPathComponent(name, isDirectory: true)
            let values = try? albumURL.resourceValues(forKeys: [.creationDateKey, .contentModificationDateKey])
            let latestMedia = files.map(\.modifiedAtMs).max()
            return VaultAlbumRecord(
                id: name,
                name: name,
                mediaCount: files.count,
                createdAtMs: (values?.creationDate ?? Date()).epochMs,
                modifiedAtMs: latestMedia ?? (values?.contentModificationDate ?? Date()).epochMs
            )
        }.sorted { $0.name < $1.name }
    }

    private func encryptedSha256Hex(_ file: URL) -> String? {
        guard let data = try? Data(contentsOf: file) else { return nil }
        return SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    private func inferSource(fileName: String, state: VaultMediaState) -> VaultMediaSource {
        guard state == .active else { return .unknown }
        if fileName.hasPrefix("camera_") { return .camera }
        if fileName.hasPrefix("asset_") { return .picker }
        return .unknown
    }

    private func isVaultMediaFile(_ url: URL) -> Bool {
        guard !url.hasDirectoryPath else { return false }
        let name = url.lastPathComponent
        if name.hasPrefix(".") { return false }
        if name.hasPrefix("tmp_") { return false }
        if name.contains(".enc_tmp_") { return false }
        if name == ".vault_encrypted_v1" { return false }
        return true
    }

    private func metadataURL() -> URL {
        applicationSupportDirectory()
            .appendingPathComponent("vault_metadata_v1.json", isDirectory: false)
    }

    private func applicationSupportDirectory() -> URL {
        let base = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("LumaNox", isDirectory: true)
    }

    private func documentsDirectory() -> URL {
        fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }
}

struct VaultStorageSummary: Hashable {
    let activeCount: Int
    let trashCount: Int
    let albumCount: Int
    let encryptedBytes: Int64
    let updatedAtMs: Int64
}

private extension Date {
    var epochMs: Int64 {
        Int64(timeIntervalSince1970 * 1000)
    }
}

private extension URL {
    func relativePath(from base: URL) -> String {
        let basePath = base.standardizedFileURL.path
        let ownPath = standardizedFileURL.path
        guard ownPath.hasPrefix(basePath + "/") else { return lastPathComponent }
        return String(ownPath.dropFirst(basePath.count + 1))
    }
}
