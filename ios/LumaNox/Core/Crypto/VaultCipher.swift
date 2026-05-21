import Foundation
import Security

/// Software AES-256 key stored in Keychain. Reads CBC v1 and AEAD v2; writes AEAD v2 by default.
final class VaultCipher {
    static let shared = VaultCipher()

    private let keyService = "com.xpx.vault.aes_data_key"
    private let keyAccount = "master"
    private let keyLock = NSLock()
    #if DEBUG
    private var testingKeyOverride: Data?
    #endif

    private init() {}

    func getOrCreateKey() throws -> Data {
        keyLock.lock()
        defer { keyLock.unlock() }

        #if DEBUG
        if let testingKeyOverride { return testingKeyOverride }
        #endif
        if let existing = loadKeyUnlocked() { return existing }
        #if DEBUG
        if let existing = loadDebugFallbackKeyUnlocked() { return existing }
        #endif
        var key = Data(count: 32)
        let status = key.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 32, $0.baseAddress!) }
        guard status == errSecSuccess else { throw CryptoError.randomFailed }
        do {
            try saveKeyUnlocked(key)
        } catch {
            #if DEBUG
            try saveDebugFallbackKeyUnlocked(key)
            #else
            throw error
            #endif
        }
        return key
    }

    func requireExistingKey() throws -> Data {
        keyLock.lock()
        defer { keyLock.unlock() }

        #if DEBUG
        if let testingKeyOverride { return testingKeyOverride }
        #endif
        if let key = loadKeyUnlocked() { return key }
        #if DEBUG
        if let key = loadDebugFallbackKeyUnlocked() { return key }
        #endif
        throw CryptoError.missingKey
    }

    #if DEBUG
    func installTestingKeyForUnitTests(_ key: Data?) throws {
        if let key, key.count != 32 { throw CryptoError.invalidKey }
        keyLock.lock()
        testingKeyOverride = key
        keyLock.unlock()
    }
    #endif

    func encryptFile(
        at sourceURL: URL,
        to destURL: URL,
        version: Int = VaultFileCipherVersion.aeadV2
    ) throws {
        try encryptFileFromChunks(to: destURL, version: version) { sink in
            let handle = try FileHandle(forReadingFrom: sourceURL)
            defer { try? handle.close() }
            while let chunk = try handle.read(upToCount: VaultFileFormat.v2DefaultChunkSize), !chunk.isEmpty {
                try sink(chunk)
            }
        }
    }

    func decryptFile(at sourceURL: URL) throws -> Data {
        var plain = Data()
        try decryptStream(at: sourceURL) { chunk in
            plain.append(chunk)
        }
        return plain
    }

    func cipherVersion(of sourceURL: URL) -> Int? {
        guard let handle = try? FileHandle(forReadingFrom: sourceURL) else { return nil }
        defer { try? handle.close() }
        guard let prefix = try? handle.read(upToCount: VaultFileFormat.v2Magic.count),
              !prefix.isEmpty else { return nil }
        if prefix == VaultFileFormat.v2Magic { return VaultFileCipherVersion.aeadV2 }
        return VaultFileCipherVersion.cbcV1
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

    #if DEBUG
    private func debugFallbackKeyURL() -> URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("LumaNox", isDirectory: true)
            .appendingPathComponent("debug_master_key.bin", isDirectory: false)
    }

    private func loadDebugFallbackKeyUnlocked() -> Data? {
        guard let key = try? Data(contentsOf: debugFallbackKeyURL()), key.count == 32 else {
            return nil
        }
        return key
    }

    private func saveDebugFallbackKeyUnlocked(_ key: Data) throws {
        guard key.count == 32 else { throw CryptoError.invalidKey }
        let url = debugFallbackKeyURL()
        try FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        try key.write(to: url, options: .atomic)
        try? FileManager.default.setAttributes(
            [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
            ofItemAtPath: url.path
        )
    }
    #endif
}
