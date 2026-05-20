import CryptoKit
import Foundation

/// Backup key derivation — mirrors Android [BackupKeyManager].
final class BackupKeyManager {
    struct KdfParams: Codable, Equatable {
        var algorithm: String
        var saltHex: String
        var iterations: Int
        var memoryKb: Int
        var parallelism: Int

        static let argon2id = "Argon2id"
    }

    struct BackupKeyMaterial {
        let key: Data
        let fingerprintHex: String
        let kdfParams: KdfParams
    }

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func getOrCreateKdfParams() -> KdfParams {
        if let saltHex = defaults.string(forKey: Keys.saltHex) {
            let mem = defaults.integer(forKey: Keys.memoryKb)
            let iter = defaults.integer(forKey: Keys.iterations)
            let par = defaults.integer(forKey: Keys.parallelism)
            if mem > 0, iter > 0, par > 0 {
                return KdfParams(
                    algorithm: defaults.string(forKey: Keys.algorithm) ?? KdfParams.argon2id,
                    saltHex: saltHex,
                    iterations: iter,
                    memoryKb: mem,
                    parallelism: par
                )
            }
        }
        var salt = Data(count: 32)
        _ = salt.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 32, $0.baseAddress!) }
        let saltHex = salt.hexString
        let (memKb, iterations) = Argon2idKdf.chooseParams()
        let params = KdfParams(
            algorithm: KdfParams.argon2id,
            saltHex: saltHex,
            iterations: iterations,
            memoryKb: memKb,
            parallelism: Argon2idKdf.defaultParallelism
        )
        defaults.set(params.algorithm, forKey: Keys.algorithm)
        defaults.set(params.saltHex, forKey: Keys.saltHex)
        defaults.set(params.memoryKb, forKey: Keys.memoryKb)
        defaults.set(params.iterations, forKey: Keys.iterations)
        defaults.set(params.parallelism, forKey: Keys.parallelism)
        return params
    }

    func deriveKey(password: String, params: KdfParams) throws -> BackupKeyMaterial {
        let salt = Data(hexString: params.saltHex)
        let key = try Argon2idKdf.derive(
            password: password,
            salt: salt,
            iterations: params.iterations,
            memoryKb: params.memoryKb,
            parallelism: params.parallelism
        )
        return BackupKeyMaterial(
            key: key,
            fingerprintHex: fingerprint(key: key),
            kdfParams: params
        )
    }

    func fingerprint(key: Data) -> String {
        let symmetric = SymmetricKey(data: key)
        let mac = HMAC<SHA256>.authenticationCode(
            for: Data("aivault.fp.v1".utf8),
            using: symmetric
        )
        return Data(mac.prefix(16)).hexString
    }

    private enum Keys {
        static let saltHex = "backup_kdf_salt_hex"
        static let algorithm = "backup_kdf_algorithm"
        static let memoryKb = "backup_kdf_memory_kb"
        static let iterations = "backup_kdf_iterations"
        static let parallelism = "backup_kdf_parallelism"
    }
}

extension Data {
    var hexString: String {
        map { String(format: "%02x", $0) }.joined()
    }

    init(hexString: String) {
        precondition(hexString.count % 2 == 0)
        var bytes = [UInt8]()
        bytes.reserveCapacity(hexString.count / 2)
        var index = hexString.startIndex
        while index < hexString.endIndex {
            let next = hexString.index(index, offsetBy: 2)
            let byte = UInt8(hexString[index..<next], radix: 16) ?? 0
            bytes.append(byte)
            index = next
        }
        self = Data(bytes)
    }
}
