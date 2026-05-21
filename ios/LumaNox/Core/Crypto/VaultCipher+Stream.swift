import CommonCrypto
import CryptoKit
import Foundation

extension VaultCipher {
    /// Stream-decrypt a vault file and invoke `sink` for each plaintext chunk.
    func decryptStream(at sourceURL: URL, bufferSize: Int = 64 * 1024, sink: (Data) throws -> Void) throws {
        let key = try requireExistingKey()
        let handle = try FileHandle(forReadingFrom: sourceURL)
        defer { try? handle.close() }

        guard let prefix = try handle.read(upToCount: VaultFileFormat.v2Magic.count),
              !prefix.isEmpty else {
            throw CryptoError.invalidCipher
        }

        if prefix == VaultFileFormat.v2Magic {
            try decryptAEADV2(handle: handle, key: key, magic: prefix, sink: sink)
        } else {
            try decryptCBCV1(handle: handle, key: key, initialBytes: prefix, bufferSize: bufferSize, sink: sink)
        }
    }

    private func decryptCBCV1(
        handle: FileHandle,
        key: Data,
        initialBytes: Data,
        bufferSize: Int,
        sink: (Data) throws -> Void
    ) throws {
        var iv = initialBytes
        let remainingIVBytes = kCCBlockSizeAES128 - iv.count
        if remainingIVBytes > 0 {
            guard let rest = try handle.read(upToCount: remainingIVBytes),
                  rest.count == remainingIVBytes else {
                throw CryptoError.invalidCipher
            }
            iv.append(rest)
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

    /// Push-mode encrypt. New callers write AEAD v2 unless a test/migration path asks for CBC v1.
    func encryptFileFromChunks(
        to destURL: URL,
        version: Int = VaultFileCipherVersion.aeadV2,
        feed: (@escaping (Data) throws -> Void) throws -> Void
    ) throws {
        switch version {
        case VaultFileCipherVersion.cbcV1:
            try encryptCBCV1(to: destURL, feed: feed)
        case VaultFileCipherVersion.aeadV2:
            try encryptAEADV2(to: destURL, feed: feed)
        default:
            throw CryptoError.unsupportedCipherVersion(version)
        }
    }

    private func encryptCBCV1(to destURL: URL, feed: (@escaping (Data) throws -> Void) throws -> Void) throws {
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

    private func encryptAEADV2(to destURL: URL, feed: (@escaping (Data) throws -> Void) throws -> Void) throws {
        let key = SymmetricKey(data: try getOrCreateKey())
        let dir = destURL.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let tmp = dir.appendingPathComponent(".enc_tmp_\(UUID().uuidString)")

        var baseNonce = Data(count: VaultFileFormat.v2BaseNonceLength)
        let nonceStatus = baseNonce.withUnsafeMutableBytes {
            SecRandomCopyBytes(kSecRandomDefault, VaultFileFormat.v2BaseNonceLength, $0.baseAddress!)
        }
        guard nonceStatus == errSecSuccess else { throw CryptoError.randomFailed }

        let header = makeV2Header(baseNonce: baseNonce)
        FileManager.default.createFile(atPath: tmp.path, contents: nil)
        let handle = try FileHandle(forWritingTo: tmp)
        var counter: UInt64 = 0

        do {
            try handle.write(contentsOf: header)
            try feed { plain in
                guard !plain.isEmpty else { return }
                var offset = 0
                while offset < plain.count {
                    let count = min(VaultFileFormat.v2DefaultChunkSize, plain.count - offset)
                    let chunk = plain.subdata(in: offset..<(offset + count))
                    try self.writeV2Frame(
                        plain: chunk,
                        counter: counter,
                        baseNonce: baseNonce,
                        headerAAD: header,
                        key: key,
                        handle: handle
                    )
                    counter += 1
                    offset += count
                }
            }
            try handle.close()

            if FileManager.default.fileExists(atPath: destURL.path) {
                try FileManager.default.removeItem(at: destURL)
            }
            try FileManager.default.moveItem(at: tmp, to: destURL)
        } catch {
            try? handle.close()
            try? FileManager.default.removeItem(at: tmp)
            throw error
        }
    }

    private func decryptAEADV2(
        handle: FileHandle,
        key: Data,
        magic: Data,
        sink: (Data) throws -> Void
    ) throws {
        var header = magic
        let version = try readExactByte(from: handle, appendingTo: &header)
        guard version == VaultFileFormat.v2Version else {
            throw CryptoError.unsupportedCipherVersion(Int(version))
        }
        let algorithm = try readExactByte(from: handle, appendingTo: &header)
        guard algorithm == VaultFileFormat.v2AES256GCMAlgorithm else {
            throw CryptoError.invalidCipher
        }
        _ = try readExactUInt32(from: handle, appendingTo: &header)
        let baseNonce = try readExactly(VaultFileFormat.v2BaseNonceLength, from: handle)
        header.append(baseNonce)
        let aadLength = Int(try readExactUInt16(from: handle, appendingTo: &header))
        if aadLength > 0 {
            let aad = try readExactly(aadLength, from: handle)
            header.append(aad)
        }

        let symmetricKey = SymmetricKey(data: key)
        var counter: UInt64 = 0
        while true {
            guard let plainLengthBytes = try handle.read(upToCount: MemoryLayout<UInt32>.size),
                  !plainLengthBytes.isEmpty else {
                break
            }
            guard plainLengthBytes.count == MemoryLayout<UInt32>.size else {
                throw CryptoError.invalidCipher
            }
            let plainLength = plainLengthBytes.uint32BE
            let sealedLengthBytes = try readExactly(MemoryLayout<UInt32>.size, from: handle)
            let sealedLength = sealedLengthBytes.uint32BE
            guard sealedLength >= VaultFileFormat.v2AuthenticationTagLength else {
                throw CryptoError.invalidCipher
            }
            let sealed = try readExactly(Int(sealedLength), from: handle)
            let cipherLength = Int(sealedLength) - VaultFileFormat.v2AuthenticationTagLength
            let ciphertext = sealed.prefix(cipherLength)
            let tag = sealed.suffix(VaultFileFormat.v2AuthenticationTagLength)
            let nonce = try makeV2Nonce(baseNonce: baseNonce, counter: counter)
            let box = try AES.GCM.SealedBox(nonce: nonce, ciphertext: ciphertext, tag: tag)
            var frameAAD = header
            frameAAD.appendUInt64BE(counter)
            frameAAD.appendUInt32BE(plainLength)
            let plain = try AES.GCM.open(box, using: symmetricKey, authenticating: frameAAD)
            guard plain.count == Int(plainLength) else { throw CryptoError.invalidCipher }
            try sink(plain)
            counter += 1
        }
    }

    private func makeV2Header(baseNonce: Data) -> Data {
        var header = Data()
        header.append(VaultFileFormat.v2Magic)
        header.append(VaultFileFormat.v2Version)
        header.append(VaultFileFormat.v2AES256GCMAlgorithm)
        header.appendUInt32BE(UInt32(VaultFileFormat.v2DefaultChunkSize))
        header.append(baseNonce)
        header.appendUInt16BE(0)
        return header
    }

    private func writeV2Frame(
        plain: Data,
        counter: UInt64,
        baseNonce: Data,
        headerAAD: Data,
        key: SymmetricKey,
        handle: FileHandle
    ) throws {
        guard plain.count <= UInt32.max else { throw CryptoError.invalidCipher }
        let plainLength = UInt32(plain.count)
        let nonce = try makeV2Nonce(baseNonce: baseNonce, counter: counter)
        var frameAAD = headerAAD
        frameAAD.appendUInt64BE(counter)
        frameAAD.appendUInt32BE(plainLength)
        let sealed = try AES.GCM.seal(plain, using: key, nonce: nonce, authenticating: frameAAD)
        var payload = Data()
        payload.append(sealed.ciphertext)
        payload.append(sealed.tag)
        var prefix = Data()
        prefix.appendUInt32BE(plainLength)
        prefix.appendUInt32BE(UInt32(payload.count))
        try handle.write(contentsOf: prefix)
        try handle.write(contentsOf: payload)
    }

    private func makeV2Nonce(baseNonce: Data, counter: UInt64) throws -> AES.GCM.Nonce {
        guard baseNonce.count == VaultFileFormat.v2BaseNonceLength else { throw CryptoError.invalidCipher }
        var bytes = [UInt8](baseNonce)
        var bigEndianCounter = counter.bigEndian
        withUnsafeBytes(of: &bigEndianCounter) { counterBytes in
            bytes.replaceSubrange(4..<12, with: counterBytes)
        }
        return try AES.GCM.Nonce(data: Data(bytes))
    }

    private func readExactByte(from handle: FileHandle, appendingTo header: inout Data) throws -> UInt8 {
        let data = try readExactly(1, from: handle)
        header.append(data)
        return data[0]
    }

    private func readExactUInt16(from handle: FileHandle, appendingTo header: inout Data) throws -> UInt16 {
        let data = try readExactly(MemoryLayout<UInt16>.size, from: handle)
        header.append(data)
        return data.uint16BE
    }

    private func readExactUInt32(from handle: FileHandle, appendingTo header: inout Data) throws -> UInt32 {
        let data = try readExactly(MemoryLayout<UInt32>.size, from: handle)
        header.append(data)
        return data.uint32BE
    }

    private func readExactly(_ count: Int, from handle: FileHandle) throws -> Data {
        guard count >= 0 else { throw CryptoError.invalidCipher }
        guard count > 0 else { return Data() }
        guard let data = try handle.read(upToCount: count), data.count == count else {
            throw CryptoError.invalidCipher
        }
        return data
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

private extension Data {
    mutating func appendUInt16BE(_ value: UInt16) {
        var bigEndian = value.bigEndian
        Swift.withUnsafeBytes(of: &bigEndian) { append(contentsOf: $0) }
    }

    mutating func appendUInt32BE(_ value: UInt32) {
        var bigEndian = value.bigEndian
        Swift.withUnsafeBytes(of: &bigEndian) { append(contentsOf: $0) }
    }

    mutating func appendUInt64BE(_ value: UInt64) {
        var bigEndian = value.bigEndian
        Swift.withUnsafeBytes(of: &bigEndian) { append(contentsOf: $0) }
    }

    var uint16BE: UInt16 {
        reduce(UInt16(0)) { ($0 << 8) | UInt16($1) }
    }

    var uint32BE: UInt32 {
        reduce(UInt32(0)) { ($0 << 8) | UInt32($1) }
    }
}
