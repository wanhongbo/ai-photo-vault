import Foundation
import Security

/// 软件 AES-256 密钥存 Keychain；文件 IV(16) + AES-CBC 密文。
final class VaultCipher {
    static let shared = VaultCipher()

    private let keyService = "com.xpx.vault.aes_data_key"
    private let keyAccount = "master"
    private let keyLock = NSLock()

    private init() {}

    func getOrCreateKey() throws -> Data {
        keyLock.lock()
        defer { keyLock.unlock() }

        if let existing = loadKeyUnlocked() { return existing }
        var key = Data(count: 32)
        let status = key.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 32, $0.baseAddress!) }
        guard status == errSecSuccess else { throw CryptoError.randomFailed }
        try saveKeyUnlocked(key)
        return key
    }

    func requireExistingKey() throws -> Data {
        keyLock.lock()
        defer { keyLock.unlock() }

        guard let key = loadKeyUnlocked() else { throw CryptoError.missingKey }
        return key
    }

    func encryptFile(at sourceURL: URL, to destURL: URL) throws {
        let key = try getOrCreateKey()
        let plain = try Data(contentsOf: sourceURL)
        let cipher = try AESCBC.encrypt(plain: plain, key: key)
        let dir = destURL.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let tmp = dir.appendingPathComponent(".tmp_\(UUID().uuidString)")
        try cipher.write(to: tmp, options: .atomic)
        if FileManager.default.fileExists(atPath: destURL.path) {
            try FileManager.default.removeItem(at: destURL)
        }
        try FileManager.default.moveItem(at: tmp, to: destURL)
    }

    func decryptFile(at sourceURL: URL) throws -> Data {
        let key = try requireExistingKey()
        let cipher = try Data(contentsOf: sourceURL)
        return try AESCBC.decrypt(cipherWithIV: cipher, key: key)
    }

    /// 解密到缓存目录供 AVPlayer 播放；退出页面后由调用方删除临时文件。
    func decryptToTempFile(sourceURL: URL, cacheDirectory: URL, fileName: String) throws -> URL {
        let plain = try decryptFile(at: sourceURL)
        try FileManager.default.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)
        let dest = cacheDirectory.appendingPathComponent(fileName)
        if FileManager.default.fileExists(atPath: dest.path) {
            try FileManager.default.removeItem(at: dest)
        }
        try plain.write(to: dest, options: .atomic)
        return dest
    }

    private func saveKeyUnlocked(_ key: Data) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keyService,
            kSecAttrAccount as String: keyAccount,
        ]
        let update: [String: Any] = [
            kSecValueData as String: key,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        ]
        let updateStatus = SecItemUpdate(query as CFDictionary, update as CFDictionary)
        if updateStatus == errSecSuccess { return }
        guard updateStatus == errSecItemNotFound else { throw KeychainError.osStatus(updateStatus) }

        var add = query
        add[kSecValueData as String] = key
        add[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        let status = SecItemAdd(add as CFDictionary, nil)
        guard status == errSecSuccess else { throw KeychainError.osStatus(status) }
    }

    private func loadKeyUnlocked() -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keyService,
            kSecAttrAccount as String: keyAccount,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data, data.count == 32 else { return nil }
        return data
    }
}
