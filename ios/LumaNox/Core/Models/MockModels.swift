import Foundation

struct LNAlbum: Identifiable, Hashable {
    let id: String
    let name: String
    let itemCount: Int
}

struct LNMediaItem: Identifiable, Hashable {
    let id: String
    let path: String
    let fileName: String
    let isVideo: Bool
    let sizeLabel: String
    let createdAt: String
}

enum AISuggestState: String, CaseIterable {
    case scanning
    case sensitive
    case cleanup
    case allClear
    case idle
}

enum MockData {
    static let albums: [LNAlbum] = [
        LNAlbum(id: "1", name: "Default", itemCount: 12),
        LNAlbum(id: "2", name: "Travel", itemCount: 8),
        LNAlbum(id: "3", name: "Documents", itemCount: 3),
    ]

    static let mediaItems: [LNMediaItem] = (1...12).map { i in
        LNMediaItem(
            id: "\(i)",
            path: "/vault/item_\(i).jpg",
            fileName: "IMG_\(1000 + i).jpg",
            isVideo: i % 5 == 0,
            sizeLabel: "\(1 + i % 4) MB",
            createdAt: "2026-05-\(String(format: "%02d", min(i, 19)))"
        )
    }

    static let trashItems: [LNMediaItem] = mediaItems.prefix(3).map { item in
        LNMediaItem(
            id: "t-\(item.id)",
            path: item.path,
            fileName: item.fileName,
            isVideo: item.isVideo,
            sizeLabel: item.sizeLabel,
            createdAt: item.createdAt
        )
    }

    static let classifyCategories = ["People", "Documents", "Screenshots", "Food", "Nature"]

    static let aiFeatures: [(titleKey: String, route: AppRoute)] = [
        ("ai_feat_classify", .aiClassify),
        ("ai_feat_search", .vaultSearch),
        ("ai_feat_blur", .recentList),
        ("ai_feat_compress", .aiCleanup),
        ("ai_feat_encrypt", .aiSensitive),
        ("ai_feat_dedup", .aiCleanup),
    ]
}
