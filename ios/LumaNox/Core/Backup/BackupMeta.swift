import Foundation

/// `Application Support/backup_meta.json` — 对齐 Android [BackupMeta]。
enum BackupMeta {
    struct AssetIndexEntry: Codable, Equatable {
        let relativePath: String
        let sha256Hex: String
        let sizeBytes: Int64
    }

    struct AutoMeta: Codable, Equatable {
        let lastBackupId: String
        let lastBackupAtMs: Int64
        let keyFingerprintHex: String
        let kdfParams: BackupKeyManager.KdfParams
        let externalPath: String?
        let assetIndex: [AssetIndexEntry]
    }

    struct ManualEntry: Codable, Equatable {
        let createdAtMs: Int64
        let uri: String
        let sizeBytes: Int64
        let note: String?
    }

    struct Snapshot: Codable, Equatable {
        var auto: AutoMeta?
        var manualHistory: [ManualEntry]
    }

    private static let fileName = "backup_meta.json"
    private static let version = 1

    private static func fileURL() throws -> URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent(fileName)
    }

    static func load() -> Snapshot {
        guard let url = try? fileURL(), FileManager.default.fileExists(atPath: url.path),
              let data = try? Data(contentsOf: url),
              let root = try? JSONDecoder().decode(Root.self, from: data) else {
            return Snapshot(auto: nil, manualHistory: [])
        }
        return Snapshot(auto: root.auto, manualHistory: root.manualHistory ?? [])
    }

    static func save(_ snapshot: Snapshot) {
        guard let url = try? fileURL() else { return }
        let root = Root(version: version, auto: snapshot.auto, manualHistory: snapshot.manualHistory)
        guard let data = try? JSONEncoder().encode(root) else { return }
        try? data.write(to: url, options: .atomic)
    }

    static func updateAuto(_ auto: AutoMeta) {
        var cur = load()
        cur.auto = auto
        save(cur)
    }

    static func appendManual(_ entry: ManualEntry, keepLatest: Int = 64) {
        var cur = load()
        cur.manualHistory = ([entry] + cur.manualHistory).prefix(keepLatest).map { $0 }
        save(cur)
    }

    private struct Root: Codable {
        let version: Int
        let auto: AutoMeta?
        let manualHistory: [ManualEntry]?
    }
}
