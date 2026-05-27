import Foundation

@MainActor
extension PhotoViewerSource {
    func loadMediaItems(isTrash: Bool, vaultStore: VaultStore) async -> [LNMediaItem] {
        if isTrash || self == .trash {
            let trashItems = await vaultStore.listTrashItems()
            return trashItems.map { $0.toMediaItem() }
        }

        switch self {
        case .album(let name):
            await vaultStore.loadSnapshot()
            let safeName = vaultStore.sanitizeAlbumName(name)
            return vaultStore.photos(in: safeName).map { $0.toMediaItem() }
        case .search(let query):
            await vaultStore.loadSnapshot()
            return vaultStore.searchPhotos(query: query).map { $0.toMediaItem() }
        case .aiCleanup:
            let records = aiRecords()
                .filter { VaultAIAnalysisService.isCleanable($0) }
                .sorted { $0.modifiedAtMs > $1.modifiedAtMs }
            return records.map(Self.mediaItem)
        case .aiSensitive:
            let service = VaultAIAnalysisService.shared
            service.refreshSummary()
            return service.sensitiveRecords.map(Self.mediaItem)
        case .aiClassify(let category, let tag):
            let records = aiRecords()
                .filter { record in
                    guard record.ai.category == category else { return false }
                    guard let tag else { return true }
                    return record.ai.tags.contains(tag)
                }
                .sorted { $0.modifiedAtMs > $1.modifiedAtMs }
            return records.map(Self.mediaItem)
        case .recent, .trash:
            await vaultStore.loadSnapshot()
            return vaultStore.snapshot?.recentPhotos.map { $0.toMediaItem() } ?? []
        }
    }

    private func aiRecords() -> [VaultMediaRecord] {
        let service = VaultAIAnalysisService.shared
        service.refreshSummary()
        return service.records
    }

    private static func mediaItem(_ record: VaultMediaRecord) -> LNMediaItem {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let modified = Date(timeIntervalSince1970: Double(record.modifiedAtMs) / 1000)
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return LNMediaItem(
            id: record.id,
            path: record.absoluteURL(documentsDirectory: documents).path,
            fileName: record.originalFileName ?? record.fileName,
            isVideo: record.isVideo,
            sizeLabel: ByteCountFormatter.string(fromByteCount: record.encryptedSizeBytes, countStyle: .file),
            createdAt: formatter.string(from: modified)
        )
    }
}
