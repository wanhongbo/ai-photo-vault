import Foundation
import CryptoKit

/// 与 Android `PasswordHasher.sha256HexOfUtf8` 对齐。
enum PasswordHasher {
    static func sha256Hex(of string: String) -> String {
        let data = Data(string.utf8)
        let digest = SHA256.hash(data: data)
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}
