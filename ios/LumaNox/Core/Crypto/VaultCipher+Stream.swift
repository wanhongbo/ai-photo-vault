import CommonCrypto
import CryptoKit
import Foundation

extension VaultCipher {
    /// Stream-decrypt vault file (IV + AES-CBC) and invoke `sink` for each plaintext chunk.
    func decryptStream(at sourceURL: URL, bufferSize: Int = 64 * 1024, sink: (Data) throws -> Void) throws {
        let key = try requireExistingKey()
        let handle = try FileHandle(forReadingFrom: sourceURL)
        defer { try? handle.close() }
        guard let iv = try handle.read(upToCount: kCCBlockSizeAES128), iv.count == kCCBlockSizeAES128 else {
            throw CryptoError.invalidCipher
        }

        var cryptor: CCCryptorRef?
        var status = key.withUnsafeBytes { keyBuf in
            iv.withUnsafeBytes { ivBuf in
                CCCryptorCreate(
                    CCOperation(kCCDecrypt),
                    CCAlgorithm(kCCAlgorithmAES),
                    CCOptions(kCCOptionPKCS7Padding),
                    keyBuf.baseAddress, keyBuf.count,
                    ivBuf.baseAddress,
                    &cryptor
                )
            }
        }
        guard status == kCCSuccess, let cryptor else { throw CryptoError.cryptFailed(status) }
        defer { CCCryptorRelease(cryptor) }

        while true {
            guard let chunk = try handle.read(upToCount: bufferSize), !chunk.isEmpty else { break }
            let outAvailable = chunk.count + kCCBlockSizeAES128
            var out = Data(count: outAvailable)
            var moved = 0
            status = chunk.withUnsafeBytes { inBuf in
                out.withUnsafeMutableBytes { outBuf in
                    CCCryptorUpdate(
                        cryptor,
                        inBuf.baseAddress, chunk.count,
                        outBuf.baseAddress, outAvailable,
                        &moved
                    )
                }
            }
            guard status == kCCSuccess else { throw CryptoError.cryptFailed(status) }
            if moved > 0 { try sink(out.prefix(moved)) }
        }

        var tail = Data(count: kCCBlockSizeAES128)
        var tailMoved = 0
        status = tail.withUnsafeMutableBytes { outBuf in
            CCCryptorFinal(cryptor, outBuf.baseAddress, kCCBlockSizeAES128, &tailMoved)
        }
        guard status == kCCSuccess else { throw CryptoError.cryptFailed(status) }
        if tailMoved > 0 { try sink(tail.prefix(tailMoved)) }
    }

    /// Push-mode encrypt: write IV then CBC-encrypt chunks from `feed`.
    func encryptFileFromChunks(to destURL: URL, feed: (@escaping (Data) throws -> Void) throws -> Void) throws {
        let key = try getOrCreateKey()
        var iv = Data(count: kCCBlockSizeAES128)
        let ivStatus = iv.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, kCCBlockSizeAES128, $0.baseAddress!) }
        guard ivStatus == errSecSuccess else { throw CryptoError.randomFailed }

        let dir = destURL.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let tmp = dir.appendingPathComponent(".enc_tmp_\(UUID().uuidString)")

        var cryptor: CCCryptorRef?
        var status = key.withUnsafeBytes { keyBuf in
            iv.withUnsafeBytes { ivBuf in
                CCCryptorCreate(
                    CCOperation(kCCEncrypt),
                    CCAlgorithm(kCCAlgorithmAES),
                    CCOptions(kCCOptionPKCS7Padding),
                    keyBuf.baseAddress, keyBuf.count,
                    ivBuf.baseAddress,
                    &cryptor
                )
            }
        }
        guard status == kCCSuccess, let cryptor else { throw CryptoError.cryptFailed(status) }
        defer { CCCryptorRelease(cryptor) }

        FileManager.default.createFile(atPath: tmp.path, contents: nil)
        let handle = try FileHandle(forWritingTo: tmp)
        defer {
            try? handle.close()
        }
        try handle.write(contentsOf: iv)

        try feed { plain in
            guard !plain.isEmpty else { return }
            let outAvailable = plain.count + kCCBlockSizeAES128
            var out = Data(count: outAvailable)
            var moved = 0
            status = plain.withUnsafeBytes { inBuf in
                out.withUnsafeMutableBytes { outBuf in
                    CCCryptorUpdate(
                        cryptor,
                        inBuf.baseAddress, plain.count,
                        outBuf.baseAddress, outAvailable,
                        &moved
                    )
                }
            }
            guard status == kCCSuccess else { throw CryptoError.cryptFailed(status) }
            if moved > 0 { try handle.write(contentsOf: out.prefix(moved)) }
        }

        var tail = Data(count: kCCBlockSizeAES128)
        var tailMoved = 0
        status = tail.withUnsafeMutableBytes { outBuf in
            CCCryptorFinal(cryptor, outBuf.baseAddress, kCCBlockSizeAES128, &tailMoved)
        }
        guard status == kCCSuccess else { throw CryptoError.cryptFailed(status) }
        if tailMoved > 0 { try handle.write(contentsOf: tail.prefix(tailMoved)) }

        if FileManager.default.fileExists(atPath: destURL.path) {
            try FileManager.default.removeItem(at: destURL)
        }
        try FileManager.default.moveItem(at: tmp, to: destURL)
    }

    func decryptedSha256Hex(at sourceURL: URL) -> String? {
        var digest = SHA256()
        return (try? decryptStream(at: sourceURL) { chunk in
            digest.update(data: chunk)
        }).map { digest.finalize().hexString }
    }
}

private extension SHA256.Digest {
    var hexString: String {
        map { String(format: "%02x", $0) }.joined()
    }
}
