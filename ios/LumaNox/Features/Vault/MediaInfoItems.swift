import Foundation

func mediaInfoItems(
    fallbackURL: URL,
    fallbackPath: String,
    record: VaultMediaRecord?,
    fallbackKind: VaultMediaKind
) -> [(String, String)] {
    let byteFormatter = ByteCountFormatter()
    byteFormatter.countStyle = .file
    let dateFormatter = DateFormatter()
    dateFormatter.dateFormat = "yyyy-MM-dd HH:mm"

    let fileAttributes = try? FileManager.default.attributesOfItem(atPath: fallbackPath)
    let size = record?.encryptedSizeBytes ?? int64Attribute(fileAttributes?[.size])
    let modified = record.map { Date(timeIntervalSince1970: Double($0.modifiedAtMs) / 1000) }
        ?? (fileAttributes?[.modificationDate] as? Date)
        ?? Date()
    let kind = record?.mediaKind ?? fallbackKind

    var items: [(String, String)] = [
        (
            L10n.tr("photo_viewer_info_name"),
            record?.originalFileName ?? record?.fileName ?? fallbackURL.lastPathComponent
        ),
        (L10n.tr("photo_viewer_info_album"), record?.albumName ?? fallbackURL.deletingLastPathComponent().lastPathComponent),
        (L10n.tr("photo_viewer_info_type"), localizedMediaKind(kind)),
        (L10n.tr("photo_viewer_info_size"), byteFormatter.string(fromByteCount: size)),
        (L10n.tr("photo_viewer_info_modified"), dateFormatter.string(from: modified)),
    ]

    if let width = record?.width, let height = record?.height {
        items.insert(
            (L10n.tr("photo_viewer_info_dimensions"), L10n.tr("photo_viewer_info_dimensions_value", width, height)),
            at: min(3, items.count)
        )
    }

    if let durationMs = record?.durationMs, durationMs > 0 {
        items.insert(
            (L10n.tr("photo_viewer_info_duration"), formattedDuration(durationMs)),
            at: min(3, items.count)
        )
    }

    return items
}

private func localizedMediaKind(_ kind: VaultMediaKind) -> String {
    switch kind {
    case .image: return L10n.tr("photo_viewer_info_type_image")
    case .video: return L10n.tr("photo_viewer_info_type_video")
    case .other: return L10n.tr("photo_viewer_info_type_other")
    }
}

private func formattedDuration(_ durationMs: Int64) -> String {
    let totalSeconds = max(0, Int(durationMs / 1000))
    let hours = totalSeconds / 3600
    let minutes = (totalSeconds % 3600) / 60
    let seconds = totalSeconds % 60
    if hours > 0 {
        return String(format: "%d:%02d:%02d", hours, minutes, seconds)
    }
    return String(format: "%d:%02d", minutes, seconds)
}

private func int64Attribute(_ value: Any?) -> Int64 {
    if let value = value as? Int64 { return value }
    if let value = value as? Int { return Int64(value) }
    if let value = value as? NSNumber { return value.int64Value }
    return 0
}
