import Argon2Swift
import Foundation

/// Argon2id KDF — parameters aligned with Android [Argon2idKdf].
enum Argon2idKdf {
    static let defaultIterations = 3
    static let defaultMemoryKb = 64 * 1024
    static let defaultParallelism = 1
    static let lowEndMemoryKb = 32 * 1024
    static let lowEndIterations = 4
    static let defaultKeyLengthBytes = 32

    static func chooseParams() -> (memoryKb: Int, iterations: Int) {
        let memoryClass = ProcessInfo.processInfo.physicalMemory / (1024 * 1024)
        if memoryClass < 4 * 1024 {
            return (lowEndMemoryKb, lowEndIterations)
        }
        return (defaultMemoryKb, defaultIterations)
    }

    static func derive(
        password: String,
        salt: Data,
        iterations: Int = defaultIterations,
        memoryKb: Int = defaultMemoryKb,
        parallelism: Int = defaultParallelism,
        outLen: Int = defaultKeyLengthBytes
    ) throws -> Data {
        guard !password.isEmpty else { throw CryptoError.invalidKey }
        guard !salt.isEmpty else { throw CryptoError.invalidCipher }
        let result = try Argon2Swift.hashPasswordString(
            password: password,
            salt: Salt(bytes: salt),
            iterations: iterations,
            memory: memoryKb,
            parallelism: parallelism,
            length: outLen,
            type: .id,
            version: .V13
        )
        return result.hashData()
    }
}
