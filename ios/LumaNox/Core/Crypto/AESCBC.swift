import Foundation
import CommonCrypto

/// AES-256-CBC，IV 16 字节前置（与 Android VaultCipher 文件格式一致）。
enum AESCBC {
    private static let keyLength = kCCKeySizeAES256
    private static let ivLength = kCCBlockSizeAES128

    static func encrypt(plain: Data, key: Data) throws -> Data {
        guard key.count == keyLength else { throw CryptoError.invalidKey }
        var iv = Data(count: ivLength)
        let ivResult = iv.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, ivLength, $0.baseAddress!) }
        guard ivResult == errSecSuccess else { throw CryptoError.randomFailed }

        let encrypted = try crypt(operation: CCOperation(kCCEncrypt), data: plain, key: key, iv: iv)
        return iv + encrypted
    }

    static func decrypt(cipherWithIV: Data, key: Data) throws -> Data {
        guard key.count == keyLength else { throw CryptoError.invalidKey }
        guard cipherWithIV.count > ivLength else { throw CryptoError.invalidCipher }
        let iv = cipherWithIV.prefix(ivLength)
        let cipher = cipherWithIV.suffix(from: ivLength)
        return try crypt(operation: CCOperation(kCCDecrypt), data: Data(cipher), key: key, iv: Data(iv))
    }

    private static func crypt(operation: CCOperation, data: Data, key: Data, iv: Data) throws -> Data {
        let outLength = data.count + ivLength
        var outBytes = Data(count: outLength)
        var moved = 0
        let status = outBytes.withUnsafeMutableBytes { outBuf in
            data.withUnsafeBytes { dataBuf in
                key.withUnsafeBytes { keyBuf in
                    iv.withUnsafeBytes { ivBuf in
                        CCCrypt(
                            operation,
                            CCAlgorithm(kCCAlgorithmAES),
                            CCOptions(kCCOptionPKCS7Padding),
                            keyBuf.baseAddress, keyLength,
                            ivBuf.baseAddress,
                            dataBuf.baseAddress, data.count,
                            outBuf.baseAddress, outLength,
                            &moved
                        )
                    }
                }
            }
        }
        guard status == kCCSuccess else { throw CryptoError.cryptFailed(status) }
        return outBytes.prefix(moved)
    }
}

enum CryptoError: LocalizedError {
    case invalidKey
    case missingKey
    case invalidCipher
    case unsupportedCipherVersion(Int)
    case randomFailed
    case cryptFailed(CCCryptorStatus)

    var errorDescription: String? {
        switch self {
        case .invalidKey: return "Invalid AES key"
        case .missingKey: return "Missing AES key"
        case .invalidCipher: return "Invalid ciphertext"
        case .unsupportedCipherVersion(let version): return "Unsupported vault cipher version \(version)"
        case .randomFailed: return "Random IV failed"
        case .cryptFailed(let s): return "Crypt failed \(s)"
        }
    }
}
