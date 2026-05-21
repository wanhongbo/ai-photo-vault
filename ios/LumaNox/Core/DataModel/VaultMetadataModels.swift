import Foundation

let vaultMetadataSchemaVersion = 1
let vaultCipherVersionCBCv1 = 1

enum VaultMediaKind: String, Codable, Hashable {
    case image
    case video
    case other

    static func infer(from fileExtension: String) -> VaultMediaKind {
        let ext = fileExtension.lowercased()
        if ["mp4", "mov", "m4v", "mkv", "webm", "avi", "3gp", "flv"].contains(ext) {
            return .video
        }
        if ["jpg", "jpeg", "png", "heic", "heif", "gif", "webp", "tiff"].contains(ext) {
            return .image
        }
        return .other
    }
}

enum VaultMediaState: String, Codable, Hashable {
    case active
    case trashed
}

enum VaultMediaSource: String, Codable, Hashable {
    case picker
    case camera
    case restore
    case unknown
}

struct VaultAiMetadata: Codable, Hashable {
    var scannedAtMs: Int64?
    var sensitiveScore: Double?
    var cleanupScore: Double?
    var category: String?
    var tags: [String]

    static let empty = VaultAiMetadata(
        scannedAtMs: nil,
        sensitiveScore: nil,
        cleanupScore: nil,
        category: nil,
        tags: []
    )
}

struct VaultMediaRecord: Identifiable, Codable, Hashable {
    var id: String
    var storagePath: String
    var albumName: String
    var fileName: String
    var mediaKind: VaultMediaKind
    var state: VaultMediaState
    var encryptedSizeBytes: Int64
    var originalSha256Hex: String?
    var encryptedSha256Hex: String?
    var originalFileName: String?
    var mimeType: String?
    var uti: String?
    var cipherVersion: Int?
    var createdAtMs: Int64
    var importedAtMs: Int64
    var modifiedAtMs: Int64
    var trashedAtMs: Int64?
    var width: Int?
    var height: Int?
    var durationMs: Int64?
    var source: VaultMediaSource
    var ai: VaultAiMetadata

    var isVideo: Bool { mediaKind == .video }

    func absoluteURL(documentsDirectory: URL) -> URL {
        documentsDirectory.appendingPathComponent(storagePath, isDirectory: false)
    }
}

struct VaultAlbumRecord: Identifiable, Codable, Hashable {
    var id: String
    var name: String
    var mediaCount: Int
    var createdAtMs: Int64
    var modifiedAtMs: Int64
}

struct VaultMetadataSnapshot: Codable, Hashable {
    var schemaVersion: Int
    var albums: [VaultAlbumRecord]
    var media: [VaultMediaRecord]
    var updatedAtMs: Int64

    static let empty = VaultMetadataSnapshot(
        schemaVersion: vaultMetadataSchemaVersion,
        albums: [],
        media: [],
        updatedAtMs: 0
    )

    var activeMedia: [VaultMediaRecord] {
        media.filter { $0.state == .active }
    }

    var trashedMedia: [VaultMediaRecord] {
        media.filter { $0.state == .trashed }
    }

    var totalActiveCount: Int {
        activeMedia.count
    }

    var totalEncryptedBytes: Int64 {
        media.reduce(Int64(0)) { $0 + $1.encryptedSizeBytes }
    }

    func activeMedia(in albumName: String) -> [VaultMediaRecord] {
        activeMedia
            .filter { $0.albumName == albumName }
            .sorted { $0.modifiedAtMs > $1.modifiedAtMs }
    }

    func recentActive(limit: Int) -> [VaultMediaRecord] {
        Array(activeMedia.sorted { $0.modifiedAtMs > $1.modifiedAtMs }.prefix(limit))
    }

    func searchActive(query: String, limit: Int = 200) -> [VaultMediaRecord] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let source = activeMedia.sorted { $0.modifiedAtMs > $1.modifiedAtMs }
        guard !trimmed.isEmpty else { return Array(source.prefix(limit)) }
        return Array(source.filter { record in
            record.fileName.localizedCaseInsensitiveContains(trimmed)
                || record.albumName.localizedCaseInsensitiveContains(trimmed)
                || record.ai.tags.contains { $0.localizedCaseInsensitiveContains(trimmed) }
                || (record.ai.category?.localizedCaseInsensitiveContains(trimmed) == true)
        }.prefix(limit))
    }
}
