import CryptoKit
import Foundation

enum BackupIntegrityVerifier {
    static func verifyPackage(at url: URL, vaultRoot: URL? = nil) throws -> BackupPackageV1.Header {
        let fileSize = try fileSize(of: url)
        let minimumSize = Int64(BackupPackageV1.magic.count + 4 + 4 + BackupPackageV1.trailerLength)
        guard fileSize >= minimumSize else { throw BackupError.io("backup too small") }

        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }

        var digest = SHA256()
        var offset: Int64 = 0

        func readAndDigest(_ count: Int) throws -> Data {
            let data = try readExactly(handle: handle, count: count)
            digest.update(data: data)
            offset += Int64(data.count)
            return data
        }

        let magic = try readAndDigest(BackupPackageV1.magic.count)
        guard magic == BackupPackageV1.magic else { throw BackupError.badMagic }

        let version = int32BE(try readAndDigest(4))
        guard version == BackupPackageV1.version else {
            throw BackupError.unsupportedVersion(Int(version))
        }

        let headerLen = Int(int32BE(try readAndDigest(4)))
        guard headerLen > 0, headerLen <= 32 * 1024 * 1024 else {
            throw BackupError.invalidHeaderLength(headerLen)
        }
        guard offset + Int64(headerLen) + Int64(BackupPackageV1.trailerLength) <= fileSize else {
            throw BackupError.invalidHeaderLength(headerLen)
        }

        let headerBytes = try readAndDigest(headerLen)
        let header = try BackupPackageV1.parseHeaderJson(String(decoding: headerBytes, as: UTF8.self))
        try validateHeader(header, vaultRoot: vaultRoot)

        let bodyLength = fileSize - offset - Int64(BackupPackageV1.trailerLength)
        guard bodyLength >= 0 else { throw BackupError.io("invalid body length") }
        var remaining = bodyLength
        while remaining > 0 {
            let count = min(64 * 1024, Int(remaining))
            _ = try readAndDigest(count)
            remaining -= Int64(count)
        }

        let trailer = try readExactly(handle: handle, count: BackupPackageV1.trailerLength)
        let expected = Data(digest.finalize())
        guard trailer == expected else { throw BackupError.io("backup checksum mismatch") }
        return header
    }

    static func resolvedAssetURL(relativePath: String, vaultRoot: URL) throws -> URL {
        try validateRelativePath(relativePath, vaultRoot: vaultRoot)
    }

    private static func validateHeader(_ header: BackupPackageV1.Header, vaultRoot: URL?) throws {
        guard header.version == BackupPackageV1.version else {
            throw BackupError.unsupportedVersion(header.version)
        }
        guard header.cipher == BackupPackageV1.cipherSuite else {
            throw BackupError.io("unsupported cipher")
        }

        var expectedFrame = 0
        for asset in header.assets {
            guard asset.frameCount >= 0, asset.fromFrame >= 0 else {
                throw BackupError.invalidHeaderJson
            }
            guard asset.fromFrame == expectedFrame else {
                throw BackupError.invalidHeaderJson
            }
            guard asset.sizeBytes >= 0, !asset.sha256Hex.isEmpty else {
                throw BackupError.invalidHeaderJson
            }
            if let vaultRoot {
                _ = try validateRelativePath(asset.relativePath, vaultRoot: vaultRoot)
            } else {
                try validateRelativePathSyntax(asset.relativePath)
            }
            expectedFrame += asset.frameCount
        }
    }

    private static func validateRelativePath(_ relativePath: String, vaultRoot: URL) throws -> URL {
        try validateRelativePathSyntax(relativePath)
        let root = vaultRoot.standardizedFileURL
        let target = root.appendingPathComponent(relativePath).standardizedFileURL
        guard target.path.hasPrefix(root.path + "/") else {
            throw BackupError.io("unsafe backup path")
        }
        return target
    }

    private static func validateRelativePathSyntax(_ relativePath: String) throws {
        guard !relativePath.isEmpty else { throw BackupError.io("empty backup path") }
        guard !relativePath.hasPrefix("/") else { throw BackupError.io("absolute backup path") }
        guard !relativePath.hasPrefix(".") else { throw BackupError.io("hidden backup path") }
        let components = relativePath.split(separator: "/", omittingEmptySubsequences: false)
        guard !components.isEmpty else { throw BackupError.io("empty backup path") }
        for component in components {
            if component.isEmpty || component == "." || component == ".." || component.hasPrefix(".") {
                throw BackupError.io("unsafe backup path")
            }
        }
    }

    private static func fileSize(of url: URL) throws -> Int64 {
        let size = try FileManager.default.attributesOfItem(atPath: url.path)[.size] as? NSNumber
        return size?.int64Value ?? 0
    }

    private static func readExactly(handle: FileHandle, count: Int) throws -> Data {
        let data = try handle.read(upToCount: count) ?? Data()
        guard data.count == count else { throw BackupError.io("unexpected EOF") }
        return data
    }

    private static func int32BE(_ data: Data) -> Int32 {
        (Int32(data[0]) << 24) | (Int32(data[1]) << 16) | (Int32(data[2]) << 8) | Int32(data[3])
    }
}
