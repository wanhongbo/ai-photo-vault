import CryptoKit
import Foundation

/// v1 backup package — binary-compatible with Android [BackupPackageV1].
enum BackupPackageV1 {
    static let version = 1
    static let chunkMaxPlainBytes = 1 * 1024 * 1024
    static let cipherSuite = "AES-256-GCM"
    static let ivLength = 12
    static let gcmTagLength = 16
    static let trailerLength = 32
    static let magic = Data([0x41, 0x49, 0x56, 0x41, 0x55, 0x4c, 0x54, 0x01]) // AIVAULT\x01

    enum Kind: String {
        case AUTO
        case MANUAL
    }

    struct HeaderBase {
        let backupId: String
        let createdAtMs: Int64
        let kind: Kind
        let kdfAlgorithm: String
        let kdfSaltHex: String
        let kdfIterations: Int
        let kdfMemoryKb: Int
        let kdfParallelism: Int
        let keyFingerprintHex: String
    }

    struct AssetHeader {
        let relativePath: String
        let sha256Hex: String
        let sizeBytes: Int64
        let fromFrame: Int
        let frameCount: Int
    }

    struct Header {
        let version: Int
        let backupId: String
        let createdAtMs: Int64
        let kind: Kind
        let kdfAlgorithm: String
        let kdfSaltHex: String
        let kdfIterations: Int
        let kdfMemoryKb: Int
        let kdfParallelism: Int
        let keyFingerprintHex: String
        let cipher: String
        let assets: [AssetHeader]
    }

    // MARK: - Body writer

    final class BodyWriter {
        private let output: OutputStream
        private let backupKey: SymmetricKey
        private var frameIndex = 0
        private var currentAsset: AssetBuilder?
        private var finalized: [AssetHeader] = []

        private struct AssetBuilder {
            let relativePath: String
            let sha256Hex: String
            let sizeBytes: Int64
            let fromFrame: Int
        }

        init(output: OutputStream, backupKey: Data) {
            self.output = output
            self.backupKey = SymmetricKey(data: backupKey)
        }

        func beginAsset(relativePath: String, sha256Hex: String, sizeBytes: Int64) {
            precondition(currentAsset == nil)
            currentAsset = AssetBuilder(
                relativePath: relativePath,
                sha256Hex: sha256Hex,
                sizeBytes: sizeBytes,
                fromFrame: frameIndex
            )
        }

        func writeChunk(_ plain: Data, offset: Int = 0, length: Int? = nil) throws {
            guard let _ = currentAsset else { throw BackupError.noAssetInProgress }
            let len = length ?? plain.count
            guard len >= 1, len <= chunkMaxPlainBytes else { throw BackupError.invalidChunkLength(len) }
            let slice = plain.subdata(in: offset ..< (offset + len))
            var iv = Data(count: ivLength)
            let status = iv.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, ivLength, $0.baseAddress!) }
            guard status == errSecSuccess else { throw CryptoError.randomFailed }
            let nonce = try AES.GCM.Nonce(data: iv)
            let sealed = try AES.GCM.seal(slice, using: backupKey, nonce: nonce)
            try output.write(iv)
            try output.writeInt32BE(Int32(sealed.ciphertext.count))
            try output.write(sealed.ciphertext)
            try output.write(sealed.tag)
            frameIndex += 1
        }

        func endAsset() throws -> AssetHeader {
            guard let b = currentAsset else { throw BackupError.noAssetInProgress }
            let count = frameIndex - b.fromFrame
            let header = AssetHeader(
                relativePath: b.relativePath,
                sha256Hex: b.sha256Hex,
                sizeBytes: b.sizeBytes,
                fromFrame: b.fromFrame,
                frameCount: count
            )
            finalized.append(header)
            currentAsset = nil
            return header
        }

        func snapshot() -> [AssetHeader] { finalized }
    }

    static func newBodyWriter(output: OutputStream, backupKey: Data) -> BodyWriter {
        BodyWriter(output: output, backupKey: backupKey)
    }

    static func finalizePackage(
        bodyFile: URL,
        bodyWriter: BodyWriter,
        headerBase: HeaderBase,
        finalOutput: OutputStream
    ) throws -> Int64 {
        let headerJson = buildHeaderJson(base: headerBase, assets: bodyWriter.snapshot())
        let headerBytes = Data(headerJson.utf8)
        var digest = SHA256()
        var written: Int64 = 0

        func writeAndDigest(_ data: Data) throws {
            try finalOutput.write(data)
            digest.update(data: data)
            written += Int64(data.count)
        }

        try writeAndDigest(magic)
        try writeAndDigest(int32ToBytes(version))
        try writeAndDigest(int32ToBytes(headerBytes.count))
        try writeAndDigest(headerBytes)

        let handle = try FileHandle(forReadingFrom: bodyFile)
        defer { try? handle.close() }
        while let chunk = try handle.read(upToCount: 64 * 1024), !chunk.isEmpty {
            try writeAndDigest(chunk)
        }

        let trailer = Data(digest.finalize())
        try finalOutput.write(trailer)
        written += Int64(trailer.count)
        return written
    }

    // MARK: - Reader

    final class Reader {
        private let input: InputStream
        private let backupKey: SymmetricKey
        private(set) var header: Header?
        private var framesRead = 0

        init(input: InputStream, backupKey: Data) {
            self.input = input
            self.backupKey = SymmetricKey(data: backupKey)
        }

        func readHeader() throws -> Header {
            if let header { return header }
            let magicRead = try input.readFully(count: magic.count)
            guard magicRead == magic else { throw BackupError.badMagic }
            let ver = try input.readInt32BE()
            guard ver == version else { throw BackupError.unsupportedVersion(Int(ver)) }
            let headerLen = try input.readInt32BE()
            guard headerLen > 0, headerLen <= 32 * 1024 * 1024 else {
                throw BackupError.invalidHeaderLength(Int(headerLen))
            }
            let headerBytes = try input.readFully(count: Int(headerLen))
            let parsed = try parseHeaderJson(String(decoding: headerBytes, as: UTF8.self))
            header = parsed
            return parsed
        }

        func attachHeader(_ h: Header) {
            precondition(header == nil)
            header = h
        }

        func readNextChunk() throws -> Data? {
            let h = try header ?? readHeader()
            let totalFrames = h.assets.reduce(0) { $0 + $1.frameCount }
            if framesRead >= totalFrames { return nil }
            let iv = try input.readFully(count: ivLength)
            let cipherLen = Int(try input.readInt32BE())
            guard cipherLen >= 0, cipherLen <= chunkMaxPlainBytes else { throw BackupError.invalidCipherLength(cipherLen) }
            let ciphertext = try input.readFully(count: cipherLen)
            let tag = try input.readFully(count: gcmTagLength)
            let nonce = try AES.GCM.Nonce(data: iv)
            let box = try AES.GCM.SealedBox(nonce: nonce, ciphertext: ciphertext, tag: tag)
            let plain = try AES.GCM.open(box, using: backupKey)
            framesRead += 1
            return plain
        }
    }

    static func newReader(input: InputStream, backupKey: Data) -> Reader {
        Reader(input: input, backupKey: backupKey)
    }

    // MARK: - JSON

    static func buildHeaderJson(base: HeaderBase, assets: [AssetHeader]) -> String {
        let assetArr = assets.map { a -> String in
            """
            {"relativePath":"\(escapeJson(a.relativePath))","sha256Hex":"\(a.sha256Hex)","sizeBytes":\(a.sizeBytes),"chunkRange":{"fromFrame":\(a.fromFrame),"count":\(a.frameCount)}}
            """
        }.joined(separator: ",")
        return """
        {"version":\(version),"backupId":"\(escapeJson(base.backupId))","createdAtMs":\(base.createdAtMs),"kind":"\(base.kind.rawValue)","kdfParams":{"algorithm":"\(escapeJson(base.kdfAlgorithm))","saltHex":"\(base.kdfSaltHex)","iterations":\(base.kdfIterations),"memoryKb":\(base.kdfMemoryKb),"parallelism":\(base.kdfParallelism)},"keyFingerprintHex":"\(base.keyFingerprintHex)","cipher":"\(cipherSuite)","assets":[\(assetArr)]}
        """
    }

    static func parseHeaderJson(_ raw: String) throws -> Header {
        guard let data = raw.data(using: .utf8),
              let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let kdf = root["kdfParams"] as? [String: Any],
              let assetsArr = root["assets"] as? [[String: Any]] else {
            throw BackupError.invalidHeaderJson
        }
        let assets: [AssetHeader] = try assetsArr.map { a in
            guard let range = a["chunkRange"] as? [String: Any] else { throw BackupError.invalidHeaderJson }
            return AssetHeader(
                relativePath: a["relativePath"] as? String ?? "",
                sha256Hex: a["sha256Hex"] as? String ?? "",
                sizeBytes: (a["sizeBytes"] as? NSNumber)?.int64Value ?? 0,
                fromFrame: range["fromFrame"] as? Int ?? 0,
                frameCount: range["count"] as? Int ?? 0
            )
        }
        return Header(
            version: root["version"] as? Int ?? version,
            backupId: root["backupId"] as? String ?? "",
            createdAtMs: (root["createdAtMs"] as? NSNumber)?.int64Value ?? 0,
            kind: Kind(rawValue: root["kind"] as? String ?? "MANUAL") ?? .MANUAL,
            kdfAlgorithm: kdf["algorithm"] as? String ?? BackupKeyManager.KdfParams.argon2id,
            kdfSaltHex: kdf["saltHex"] as? String ?? "",
            kdfIterations: kdf["iterations"] as? Int ?? 3,
            kdfMemoryKb: kdf["memoryKb"] as? Int ?? Argon2idKdf.defaultMemoryKb,
            kdfParallelism: kdf["parallelism"] as? Int ?? 1,
            keyFingerprintHex: root["keyFingerprintHex"] as? String ?? "",
            cipher: root["cipher"] as? String ?? cipherSuite,
            assets: assets
        )
    }

    private static func escapeJson(_ s: String) -> String {
        s.replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\"", with: "\\\"")
    }

    private static func int32ToBytes(_ v: Int) -> Data {
        Data([
            UInt8((v >> 24) & 0xFF),
            UInt8((v >> 16) & 0xFF),
            UInt8((v >> 8) & 0xFF),
            UInt8(v & 0xFF),
        ])
    }
}

enum BackupError: LocalizedError {
    case badMagic
    case unsupportedVersion(Int)
    case invalidHeaderLength(Int)
    case invalidHeaderJson
    case invalidChunkLength(Int)
    case invalidCipherLength(Int)
    case noAssetInProgress
    case wrongPin
    case vaultEmpty
    case noBackupKey
    case alreadyRunning
    case io(String)

    var errorDescription: String? {
        switch self {
        case .badMagic: return L10n.tr("backup_error_bad_magic")
        case .unsupportedVersion(let v): return L10n.tr("backup_error_version_fmt", v)
        case .invalidHeaderLength(let n): return L10n.tr("backup_error_failed_fmt", "header len \(n)")
        case .wrongPin: return L10n.tr("restore_error_wrong_pin")
        case .vaultEmpty: return L10n.tr("backup_error_vault_empty")
        case .noBackupKey: return L10n.tr("backup_error_no_key")
        case .alreadyRunning: return L10n.tr("backup_error_running")
        case .io(let msg): return L10n.tr("backup_error_failed_fmt", msg)
        case .invalidHeaderJson: return L10n.tr("backup_error_failed_fmt", "header")
        case .invalidChunkLength(let n): return L10n.tr("backup_error_failed_fmt", "chunk \(n)")
        case .invalidCipherLength(let n): return L10n.tr("backup_error_failed_fmt", "cipher \(n)")
        case .noAssetInProgress: return L10n.tr("backup_error_failed_fmt", "asset")
        }
    }
}

// MARK: - Stream helpers

private extension OutputStream {
    func write(_ data: Data) throws {
        try data.withUnsafeBytes { buf in
            guard let base = buf.baseAddress?.assumingMemoryBound(to: UInt8.self) else { return }
            var offset = 0
            while offset < data.count {
                let n = write(base.advanced(by: offset), maxLength: data.count - offset)
                if n < 0 { throw BackupError.io("write failed") }
                if n == 0 { throw BackupError.io("write stalled") }
                offset += n
            }
        }
    }

    func writeInt32BE(_ v: Int32) throws {
        try write(Data([
            UInt8((v >> 24) & 0xFF),
            UInt8((v >> 16) & 0xFF),
            UInt8((v >> 8) & 0xFF),
            UInt8(v & 0xFF),
        ]))
    }
}

private extension InputStream {
    func readFully(count: Int) throws -> Data {
        var result = Data()
        result.reserveCapacity(count)
        var buffer = [UInt8](repeating: 0, count: min(count, 64 * 1024))
        while result.count < count {
            let need = count - result.count
            let toRead = min(need, buffer.count)
            let n = read(&buffer, maxLength: toRead)
            if n < 0 { throw BackupError.io("read failed") }
            if n == 0 { throw BackupError.io("unexpected EOF") }
            result.append(contentsOf: buffer.prefix(n))
        }
        return result
    }

    func readInt32BE() throws -> Int32 {
        let b = try readFully(count: 4)
        return (Int32(b[0]) << 24) | (Int32(b[1]) << 16) | (Int32(b[2]) << 8) | Int32(b[3])
    }
}
