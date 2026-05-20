import Foundation

/// 外部备份目录（「文件」App 中用户选择的文件夹）— 对齐 Android [ExternalBackupLocation]。
enum ExternalBackupLocation {
    static let autoFileName = "backup.dat"
    static let tmpFileName = "backup.dat.writing"
    static let bakFileName = "backup.dat.bak"

    private static let bookmarkKey = "external_backup_folder_bookmark"
    private static let displayPathKey = "external_backup_folder_display"

    static var displayPath: String? {
        UserDefaults.standard.string(forKey: displayPathKey)
    }

    static var hasFolder: Bool {
        (try? resolveFolderURL()) != nil
    }

    static func isWritable() -> Bool {
        guard let folder = try? resolveFolderURL() else { return false }
        guard folder.startAccessingSecurityScopedResource() else { return false }
        defer { folder.stopAccessingSecurityScopedResource() }
        var isDir: ObjCBool = false
        guard FileManager.default.fileExists(atPath: folder.path, isDirectory: &isDir), isDir.boolValue else {
            return false
        }
        let probe = folder.appendingPathComponent(".aivault_write_probe")
        defer { try? FileManager.default.removeItem(at: probe) }
        return FileManager.default.createFile(atPath: probe.path, contents: Data([0]))
    }

    /// 持久化用户通过文档选择器授权的文件夹（security-scoped bookmark）。
    static func persistFolder(_ url: URL) throws {
        let _ = url.startAccessingSecurityScopedResource()
        defer { url.stopAccessingSecurityScopedResource() }
        let bookmark = try url.bookmarkData(
            options: [],
            includingResourceValuesForKeys: nil,
            relativeTo: nil
        )
        UserDefaults.standard.set(bookmark, forKey: bookmarkKey)
        UserDefaults.standard.set(url.path, forKey: displayPathKey)
    }

    static func clearFolder() {
        UserDefaults.standard.removeObject(forKey: bookmarkKey)
        UserDefaults.standard.removeObject(forKey: displayPathKey)
    }

    static func resolveFolderURL() throws -> URL? {
        guard let data = UserDefaults.standard.data(forKey: bookmarkKey) else { return nil }
        var stale = false
        let url = try URL(
            resolvingBookmarkData: data,
            options: [],
            relativeTo: nil,
            bookmarkDataIsStale: &stale
        )
        if stale {
            try persistFolder(url)
        }
        return url
    }

    static func autoBackupFileURL() throws -> URL? {
        try resolveFolderURL()?.appendingPathComponent(autoFileName, isDirectory: false)
    }

    /// 已授权目录内是否存在可恢复的 `backup.dat`（对齐 Android `findAuto`）。
    static func findAutoBackup() -> Bool {
        guard let folder = try? resolveFolderURL() else { return false }
        guard folder.startAccessingSecurityScopedResource() else { return false }
        defer { folder.stopAccessingSecurityScopedResource() }
        let file = folder.appendingPathComponent(autoFileName)
        var isDir: ObjCBool = false
        guard FileManager.default.fileExists(atPath: file.path, isDirectory: &isDir), !isDir.boolValue else {
            return false
        }
        let size = (try? FileManager.default.attributesOfItem(atPath: file.path)[.size] as? NSNumber)?.int64Value ?? 0
        return size > BackupPackageV1.magic.count + 8
    }

    /// 将外部 `backup.dat` 复制到临时路径供恢复引擎读取。
    static func copyAutoBackupToTemporary() throws -> URL {
        try withFolder { folder in
            let src = folder.appendingPathComponent(autoFileName)
            let tmp = FileManager.default.temporaryDirectory
                .appendingPathComponent("restore_auto_\(UUID().uuidString).dat")
            if FileManager.default.fileExists(atPath: tmp.path) {
                try FileManager.default.removeItem(at: tmp)
            }
            try FileManager.default.copyItem(at: src, to: tmp)
            return tmp
        }
    }

    /// 启动时修复残留的 `.writing` / `.bak`。
    static func sanitizeOnStartup() {
        guard let folder = try? resolveFolderURL() else { return }
        guard folder.startAccessingSecurityScopedResource() else { return }
        defer { folder.stopAccessingSecurityScopedResource() }
        let fm = FileManager.default
        let main = folder.appendingPathComponent(autoFileName)
        let writing = folder.appendingPathComponent(tmpFileName)
        let bak = folder.appendingPathComponent(bakFileName)
        if fm.fileExists(atPath: writing.path) {
            try? fm.removeItem(at: writing)
        }
        if fm.fileExists(atPath: main.path), fm.fileExists(atPath: bak.path) {
            try? fm.removeItem(at: bak)
        } else if !fm.fileExists(atPath: main.path), fm.fileExists(atPath: bak.path) {
            try? fm.moveItem(at: bak, to: main)
        }
    }

    /// 原子覆盖 `backup.dat`（先写 `.writing`，再轮换 `.bak`）。
    static func atomicReplaceAuto(tmpLocal: URL) throws {
        guard let folder = try resolveFolderURL() else {
            throw BackupError.io(L10n.tr("backup_error_no_saf_dir"))
        }
        guard folder.startAccessingSecurityScopedResource() else {
            throw BackupError.io(L10n.tr("backup_error_no_saf_dir"))
        }
        defer { folder.stopAccessingSecurityScopedResource() }

        let fm = FileManager.default
        let writing = folder.appendingPathComponent(tmpFileName)
        let main = folder.appendingPathComponent(autoFileName)
        let bak = folder.appendingPathComponent(bakFileName)

        if fm.fileExists(atPath: writing.path) {
            try? fm.removeItem(at: writing)
        }
        if fm.fileExists(atPath: tmpLocal.path) {
            try fm.copyItem(at: tmpLocal, to: writing)
        } else {
            throw BackupError.io("missing temp backup")
        }

        if fm.fileExists(atPath: main.path) {
            try? fm.removeItem(at: bak)
            try? fm.moveItem(at: main, to: bak)
        }
        if fm.fileExists(atPath: main.path) {
            try fm.removeItem(at: main)
        }
        try fm.moveItem(at: writing, to: main)
        try? fm.removeItem(at: bak)
    }

    static func withFolder<T>(_ body: (URL) throws -> T) throws -> T {
        guard let folder = try resolveFolderURL() else {
            throw BackupError.io(L10n.tr("backup_error_no_saf_dir"))
        }
        guard folder.startAccessingSecurityScopedResource() else {
            throw BackupError.io(L10n.tr("backup_error_no_saf_dir"))
        }
        defer { folder.stopAccessingSecurityScopedResource() }
        return try body(folder)
    }
}
