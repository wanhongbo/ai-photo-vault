import Foundation

private let trashDirectoryName = "vault_trash"
private let trashRetainInterval: TimeInterval = 30 * 24 * 60 * 60

extension VaultStore {
    func trashDirectory() throws -> URL {
        let docs = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let trash = docs.appendingPathComponent(trashDirectoryName, isDirectory: true)
        if !fileManager.fileExists(atPath: trash.path) {
            try fileManager.createDirectory(at: trash, withIntermediateDirectories: true)
        }
        return trash
    }

    /// 移入回收站（与 Android `VaultStore.deletePhoto` 一致：按相册分子目录）。
    @discardableResult
    func moveToTrash(path: String) async -> Bool {
        let source = URL(fileURLWithPath: path)
        guard fileManager.fileExists(atPath: path) else { return false }
        do {
            let trashRoot = try trashDirectory()
            let albumName = source.deletingLastPathComponent().lastPathComponent
            let targetDir: URL
            if !albumName.isEmpty, albumName != trashDirectoryName {
                targetDir = trashRoot.appendingPathComponent(sanitizeAlbumName(albumName), isDirectory: true)
            } else {
                targetDir = trashRoot
            }
            try fileManager.createDirectory(at: targetDir, withIntermediateDirectories: true)
            let dest = targetDir.appendingPathComponent(source.lastPathComponent)
            if fileManager.fileExists(atPath: dest.path) {
                try fileManager.removeItem(at: dest)
            }
            try fileManager.moveItem(at: source, to: dest)
            try fileManager.setAttributes([.modificationDate: Date()], ofItemAtPath: dest.path)
            invalidateCache()
            await loadSnapshot()
            return true
        } catch {
            lastImportMessage = error.localizedDescription
            lastImportIsError = true
            return false
        }
    }

    func listTrashItems() async -> [VaultTrashItem] {
        do {
            let trashRoot = try trashDirectory()
            let now = Date()
            let entries = try fileManager.contentsOfDirectory(
                at: trashRoot,
                includingPropertiesForKeys: [.contentModificationDateKey, .isDirectoryKey]
            )
            for entry in entries {
                let values = try entry.resourceValues(forKeys: [.isDirectoryKey, .contentModificationDateKey])
                if values.isDirectory == true {
                    let files = try fileManager.contentsOfDirectory(
                        at: entry,
                        includingPropertiesForKeys: [.contentModificationDateKey]
                    )
                    for file in files where !file.hasDirectoryPath {
                        _ = try processTrashFile(file, now: now)
                    }
                    if try fileManager.contentsOfDirectory(atPath: entry.path).isEmpty {
                        try? fileManager.removeItem(at: entry)
                    }
                } else {
                    _ = try processTrashFile(entry, now: now)
                }
            }
            let snapshot = try metadataStore.reconcile(vaultRoot: try rootDirectory(), trashRoot: trashRoot)
            let docs = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
            return snapshot.trashedMedia.map { record in
                VaultTrashItem(
                    id: record.id,
                    path: record.absoluteURL(documentsDirectory: docs).path,
                    name: record.fileName,
                    trashedAt: Date(timeIntervalSince1970: Double(record.trashedAtMs ?? record.modifiedAtMs) / 1000),
                    albumName: record.albumName
                )
            }.sorted { $0.trashedAt > $1.trashedAt }
        } catch {
            return []
        }
    }

    /// 从回收站恢复到原相册目录。
    func restoreFromTrash(path: String) async -> String? {
        let file = URL(fileURLWithPath: path)
        guard fileManager.fileExists(atPath: path) else { return nil }
        do {
            let trashRoot = try trashDirectory()
            let parent = file.deletingLastPathComponent()
            let albumName: String
            if parent.path != trashRoot.path {
                albumName = parent.lastPathComponent
            } else {
                albumName = vaultDefaultAlbumName
            }
            let safeAlbum = try createAlbum(named: albumName)
            let albumDir = try rootDirectory().appendingPathComponent(safeAlbum, isDirectory: true)
            var dest = albumDir.appendingPathComponent(file.lastPathComponent)
            if fileManager.fileExists(atPath: dest.path) {
                let base = dest.deletingPathExtension().lastPathComponent
                let ext = dest.pathExtension
                let suffix = ext.isEmpty ? "" : ".\(ext)"
                dest = albumDir.appendingPathComponent("\(base)_restored_\(Int(Date().timeIntervalSince1970))\(suffix)")
            }
            try fileManager.moveItem(at: file, to: dest)
            try fileManager.setAttributes([.modificationDate: Date()], ofItemAtPath: dest.path)
            if parent.path != trashRoot.path {
                let remaining = try? fileManager.contentsOfDirectory(atPath: parent.path)
                if remaining?.isEmpty == true {
                    try? fileManager.removeItem(at: parent)
                }
            }
            invalidateCache()
            await loadSnapshot()
            return safeAlbum
        } catch {
            lastImportMessage = error.localizedDescription
            lastImportIsError = true
            return nil
        }
    }

    /// 永久删除回收站中的文件。
    @discardableResult
    func purgeFromTrash(path: String) async -> Bool {
        let file = URL(fileURLWithPath: path)
        guard fileManager.fileExists(atPath: path) else { return false }
        do {
            let parent = file.deletingLastPathComponent()
            let trashRoot = try trashDirectory()
            try fileManager.removeItem(at: file)
            if parent.path != trashRoot.path {
                let remaining = try? fileManager.contentsOfDirectory(atPath: parent.path)
                if remaining?.isEmpty == true {
                    try? fileManager.removeItem(at: parent)
                }
            }
            invalidateCache()
            await loadSnapshot()
            return true
        } catch {
            return false
        }
    }

    private func processTrashFile(
        _ file: URL,
        now: Date
    ) throws {
        let values = try file.resourceValues(forKeys: [.contentModificationDateKey])
        let modified = values.contentModificationDate ?? Date()
        if now.timeIntervalSince(modified) > trashRetainMs {
            try? fileManager.removeItem(at: file)
        }
    }
}
