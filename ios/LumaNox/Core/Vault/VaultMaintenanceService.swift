import Foundation

enum VaultMaintenanceService {
    static func performStartupCleanup() {
        let manager = PlaintextTempFileManager.shared
        manager.cleanupExpired()
        cleanupLegacyPlaintextDirectories()
        cleanupLegacyRestoreInputs()
        cleanupInterruptedVaultWrites()
    }

    @MainActor
    static func performUnlockedCleanup() async {
        _ = await VaultStore.shared.listTrashItems()
    }

    private static func cleanupLegacyPlaintextDirectories() {
        let fm = FileManager.default
        let docs = fm.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let caches = fm.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let tmp = fm.temporaryDirectory
        [
            docs.appendingPathComponent("camera_tmp", isDirectory: true),
            caches.appendingPathComponent("video_cache", isDirectory: true),
            tmp.appendingPathComponent("vault_thumb_video", isDirectory: true),
            tmp.appendingPathComponent("lumanox_share", isDirectory: true),
            tmp.appendingPathComponent("backup_tmp", isDirectory: true),
        ].forEach { try? fm.removeItem(at: $0) }
    }

    private static func cleanupLegacyRestoreInputs() {
        let fm = FileManager.default
        guard let entries = try? fm.contentsOfDirectory(
            at: fm.temporaryDirectory,
            includingPropertiesForKeys: nil,
            options: [.skipsHiddenFiles]
        ) else { return }
        for entry in entries {
            let name = entry.lastPathComponent
            if name.hasPrefix("restore_in_") || name.hasPrefix("restore_auto_") {
                try? fm.removeItem(at: entry)
            }
        }
    }

    private static func cleanupInterruptedVaultWrites() {
        let fm = FileManager.default
        let docs = fm.urls(for: .documentDirectory, in: .userDomainMask)[0]
        [
            docs.appendingPathComponent("vault_albums", isDirectory: true),
            docs.appendingPathComponent("vault_trash", isDirectory: true),
        ].forEach { root in
            guard let enumerator = fm.enumerator(
                at: root,
                includingPropertiesForKeys: [.isRegularFileKey],
                options: [.skipsHiddenFiles]
            ) else { return }

            for case let file as URL in enumerator {
                let name = file.lastPathComponent
                if name.contains(".enc_tmp_") || name.hasPrefix("tmp_") {
                    try? fm.removeItem(at: file)
                }
            }
        }
    }
}
