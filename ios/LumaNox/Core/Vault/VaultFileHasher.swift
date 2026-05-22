import CryptoKit
import Foundation

enum VaultFileHasher {
    static func sha256Hex(of url: URL, chunkSize: Int = 64 * 1024) throws -> String {
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }

        var digest = SHA256()
        while let chunk = try handle.read(upToCount: chunkSize), !chunk.isEmpty {
            digest.update(data: chunk)
        }
        return digest.finalize().map { String(format: "%02x", $0) }.joined()
    }
}
