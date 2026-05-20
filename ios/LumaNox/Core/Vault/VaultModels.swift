import Foundation

let vaultDefaultAlbumName = "Default"

struct VaultPhoto: Identifiable, Hashable {
    let id: String
    let albumName: String
    let path: String
    let name: String
    let modifiedAt: Date
    let isVideo: Bool
    let sizeBytes: Int64

    var fileName: String { name }
    var sizeLabel: String {
        guard sizeBytes > 0 else { return "" }
        return ByteCountFormatter.string(fromByteCount: sizeBytes, countStyle: .file)
    }
    var createdAt: String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: modifiedAt)
    }

    func toMediaItem() -> LNMediaItem {
        LNMediaItem(
            id: id,
            path: path,
            fileName: name,
            isVideo: isVideo,
            sizeLabel: sizeLabel,
            createdAt: createdAt
        )
    }
}

struct VaultAlbum: Identifiable, Hashable {
    let id: String
    let name: String
    let photoCount: Int
}

struct VaultSnapshot {
    let albums: [VaultAlbum]
    let recentPhotos: [VaultPhoto]
    let totalCount: Int
}

enum VaultImportResult {
    case added
    case duplicate
    case failed
}

struct VaultImportSummary {
    var added = 0
    var duplicate = 0
    var failed = 0
}

struct VaultTrashItem: Identifiable, Hashable {
    let id: String
    let path: String
    let name: String
    let trashedAt: Date
    let albumName: String?

    func toMediaItem() -> LNMediaItem {
        LNMediaItem(
            id: id,
            path: path,
            fileName: name,
            isVideo: isVideo,
            sizeLabel: "",
            createdAt: formattedDate
        )
    }

    var isVideo: Bool {
        let ext = (path as NSString).pathExtension.lowercased()
        return ["mp4", "mov", "m4v", "mkv", "webm", "avi", "3gp", "flv"].contains(ext)
    }

    private var formattedDate: String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: trashedAt)
    }
}
