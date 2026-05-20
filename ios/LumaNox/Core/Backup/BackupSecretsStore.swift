import CryptoKit
import Foundation
import Security

/// Caches derived backup key wrapped by a Keychain-stored AES key (Android [BackupSecretsStore] equivalent).
enum BackupSecretsStore {
    private static let cacheFileName = "backup_secret.bin"
    private static let wrapService = "com.xpx.vault.backup_wrap_key"
    private static let wrapAccount = "backup_wrap"

    static var hasCached: Bool {
        guard let url = try? cacheFileURL() else { return false }
        return FileManager.default.fileExists(atPath: url.path)
    }

    static func cache(backupKey: Data) throws {
        let wrapKey = try getOrCreateWrapKey()
        let sealed = try AES.GCM.seal(backupKey, using: wrapKey)
        guard let combined = sealed.combined else { throw CryptoError.cryptFailed(0) }
        let url = try cacheFileURL()
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try combined.write(to: url, options: .atomic)
    }

    static func loadCached() -> Data? {
        guard let url = try? cacheFileURL(), FileManager.default.fileExists(atPath: url.path) else { return nil }
        return (try? loadCached(from: url)) ?? {
            clear()
            return nil
        }()
    }

    static func clear() {
        if let url = try? cacheFileURL() {
            try? FileManager.default.removeItem(at: url)
        }
    }

    private static func loadCached(from url: URL) throws -> Data {
        let combined = try Data(contentsOf: url)
        let wrapKey = try getOrCreateWrapKey()
        let box = try AES.GCM.SealedBox(combined: combined)
        return try AES.GCM.open(box, using: wrapKey)
    }

    private static func cacheFileURL() throws -> URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return dir.appendingPathComponent(cacheFileName)
    }

    private static func getOrCreateWrapKey() throws -> SymmetricKey {
        if let data = loadWrapKeyData() {
            return SymmetricKey(data: data)
        }
        var raw = Data(count: 32)
        let status = raw.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 32, $0.baseAddress!) }
        guard status == errSecSuccess else { throw CryptoError.randomFailed }
        try saveWrapKeyData(raw)
        return SymmetricKey(data: raw)
    }

    private static func loadWrapKeyData() -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: wrapService,
            kSecAttrAccount as String: wrapAccount,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data, data.count == 32 else { return nil }
        return data
    }

    private static func saveWrapKeyData(_ data: Data) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: wrapService,
            kSecAttrAccount as String: wrapAccount,
        ]
        SecItemDelete(query as CFDictionary)
        var add = query
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        let status = SecItemAdd(add as CFDictionary, nil)
        guard status == errSecSuccess else { throw KeychainError.osStatus(status) }
    }
}
