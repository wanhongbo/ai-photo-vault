import Foundation
import Security

struct SecuritySettings: Codable, Equatable {
    var lockType: String = "pin"
    var pinHashHex: String?
    var biometricEnabled: Bool = false
    var failCount: Int = 0
}

/// PIN 哈希与开关存 Keychain，避免 UserDefaults 明文风险。
final class SecuritySettingsStore: ObservableObject {
    static let shared = SecuritySettingsStore()

    @Published private(set) var settings: SecuritySettings?

    private let service = "com.xpx.vault.security_settings"
    private let account = "singleton"

    private init() {
        settings = load()
    }

    var hasPinConfigured: Bool {
        settings?.pinHashHex != nil
    }

    var biometricEnabled: Bool {
        settings?.biometricEnabled ?? false
    }

    func reload() {
        settings = load()
    }

    func savePin(_ pin: String, enableBiometric: Bool = false) throws {
        var s = settings ?? SecuritySettings()
        s.pinHashHex = PasswordHasher.sha256Hex(of: pin)
        s.biometricEnabled = enableBiometric
        s.failCount = 0
        try persist(s)
        settings = s
    }

    func setBiometricEnabled(_ enabled: Bool) throws {
        guard var s = settings else { return }
        s.biometricEnabled = enabled
        try persist(s)
        settings = s
    }

    func verifyPin(_ pin: String) -> Bool {
        guard let stored = settings?.pinHashHex else { return false }
        return stored == PasswordHasher.sha256Hex(of: pin)
    }

    func recordFailedAttempt() throws -> Int {
        guard var s = settings else { return 0 }
        s.failCount += 1
        try persist(s)
        settings = s
        return s.failCount
    }

    func resetFailCount() throws {
        guard var s = settings else { return }
        s.failCount = 0
        try persist(s)
        settings = s
    }

    func clearPin() throws {
        try deleteKeychainItem()
        settings = nil
    }

    // MARK: - Keychain

    private func persist(_ value: SecuritySettings) throws {
        let data = try JSONEncoder().encode(value)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
        var add = query
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        let status = SecItemAdd(add as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw KeychainError.osStatus(status)
        }
    }

    private func load() -> SecuritySettings? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else {
            if status == errSecItemNotFound { return nil }
            return nil
        }
        return try? JSONDecoder().decode(SecuritySettings.self, from: data)
    }

    private func deleteKeychainItem() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
    }
}

enum KeychainError: LocalizedError {
    case osStatus(OSStatus)
    var errorDescription: String? {
        switch self {
        case .osStatus(let status): return "Keychain error \(status)"
        }
    }
}
