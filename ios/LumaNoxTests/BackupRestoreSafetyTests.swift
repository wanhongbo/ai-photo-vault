import CryptoKit
import Foundation
@testable import LumaNox
import XCTest

final class BackupRestoreSafetyTests: XCTestCase {
    private let pin = "123456"
    private var tempDirectory: URL!
    private var vaultRoot: URL!

    override func setUpWithError() throws {
        tempDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("lumanox-backup-tests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: tempDirectory, withIntermediateDirectories: true)
        try VaultCipher.shared.installTestingKeyForUnitTests(deterministicData(byteCount: 32, seed: 7))

        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        vaultRoot = documents.appendingPathComponent("vault_albums", isDirectory: true)
        try? FileManager.default.removeItem(at: vaultRoot)
        try FileManager.default.createDirectory(
            at: vaultRoot.appendingPathComponent(vaultDefaultAlbumName, isDirectory: true),
            withIntermediateDirectories: true
        )
    }

    override func tearDownWithError() throws {
        try? VaultCipher.shared.installTestingKeyForUnitTests(nil)
        BackupSecretsStore.clearPersistentSecrets()
        if let vaultRoot {
            try? FileManager.default.removeItem(at: vaultRoot)
        }
        if let tempDirectory {
            try? FileManager.default.removeItem(at: tempDirectory)
        }
        tempDirectory = nil
        vaultRoot = nil
    }

    func testRestoreChecksumMismatchPreservesExistingAssetAtSamePath() async throws {
        let existingPlain = deterministicData(byteCount: 8_192, seed: 11)
        let backupPlain = deterministicData(byteCount: 8_192, seed: 23)
        let expectedBackupHash = sha256Hex(deterministicData(byteCount: 8_192, seed: 37))
        let relativePath = "\(vaultDefaultAlbumName)/asset_conflict.jpg"
        let target = vaultRoot.appendingPathComponent(relativePath)

        try VaultCipher.shared.encryptFileFromChunks(to: target) { emit in
            try emit(existingPlain)
        }

        let package = try makeBackupPackage(
            relativePath: relativePath,
            plain: backupPlain,
            advertisedSha256Hex: expectedBackupHash
        )

        let result = await LocalBackupService.shared.restore(from: package, pin: pin)

        XCTAssertTrue(result.success)
        XCTAssertEqual(result.restored, 0)
        XCTAssertEqual(result.failed, 1)
        XCTAssertEqual(try VaultCipher.shared.decryptFile(at: target), existingPlain)
    }

    func testBodyWriterCancelsAssetBeforeAnyFrameIsWritten() throws {
        let params = BackupKeyManager.KdfParams(
            algorithm: BackupKeyManager.KdfParams.argon2id,
            saltHex: deterministicData(byteCount: 32, seed: 41).hexString,
            iterations: 1,
            memoryKb: 1_024,
            parallelism: 1
        )
        let material = try BackupKeyManager().deriveKey(password: pin, params: params)
        let bodyFile = tempDirectory.appendingPathComponent("body_cancel.bin")

        FileManager.default.createFile(atPath: bodyFile.path, contents: nil)
        let bodyStream = try XCTUnwrap(OutputStream(url: bodyFile, append: false))
        bodyStream.open()
        let writer = BackupPackageV1.newBodyWriter(output: bodyStream, backupKey: material.key)

        writer.beginAsset(
            relativePath: "\(vaultDefaultAlbumName)/asset_missing.jpeg",
            sha256Hex: sha256Hex(Data([1])),
            sizeBytes: 1
        )
        XCTAssertTrue(writer.cancelAssetIfNoFramesWritten())

        writer.beginAsset(
            relativePath: "\(vaultDefaultAlbumName)/asset_valid.jpeg",
            sha256Hex: sha256Hex(Data([2])),
            sizeBytes: 1
        )
        try writer.writeChunk(Data([2]))
        _ = try writer.endAsset()
        bodyStream.close()

        XCTAssertEqual(writer.snapshot().map(\.relativePath), ["\(vaultDefaultAlbumName)/asset_valid.jpeg"])
    }

    func testManualBackupLegacyPlaintextMedia() throws {
        let legacyMedia = Data([0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0xFF, 0xD9])
        let legacyURL = vaultRoot
            .appendingPathComponent(vaultDefaultAlbumName, isDirectory: true)
            .appendingPathComponent("asset_legacy_plaintext.jpeg")
        try legacyMedia.write(to: legacyURL, options: .atomic)

        try BackupSecretsStore.cache(backupKey: deterministicData(byteCount: 32, seed: 53))
        let output = tempDirectory.appendingPathComponent("legacy_plaintext_backup.aivb")
        let expectation = expectation(description: "manual backup completes")
        var result: BackupExecutionResult?

        Task {
            result = await LocalBackupService.shared.createManualBackup(to: output)
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 10)

        let unwrapped = try XCTUnwrap(result)
        XCTAssertTrue(unwrapped.success, unwrapped.message)
        XCTAssertEqual(unwrapped.assetCount, 1)
        XCTAssertTrue(FileManager.default.fileExists(atPath: output.path))
    }

    private func makeBackupPackage(
        relativePath: String,
        plain: Data,
        advertisedSha256Hex: String
    ) throws -> URL {
        let params = BackupKeyManager.KdfParams(
            algorithm: BackupKeyManager.KdfParams.argon2id,
            saltHex: deterministicData(byteCount: 32, seed: 3).hexString,
            iterations: 1,
            memoryKb: 1_024,
            parallelism: 1
        )
        let manager = BackupKeyManager()
        let material = try manager.deriveKey(password: pin, params: params)
        let bodyFile = tempDirectory.appendingPathComponent("body.bin")
        let packageFile = tempDirectory.appendingPathComponent("backup.aivb")

        FileManager.default.createFile(atPath: bodyFile.path, contents: nil)
        let bodyStream = try XCTUnwrap(OutputStream(url: bodyFile, append: false))
        bodyStream.open()
        let writer = BackupPackageV1.newBodyWriter(output: bodyStream, backupKey: material.key)
        writer.beginAsset(
            relativePath: relativePath,
            sha256Hex: advertisedSha256Hex,
            sizeBytes: Int64(plain.count)
        )
        try writer.writeChunk(plain)
        _ = try writer.endAsset()
        bodyStream.close()

        FileManager.default.createFile(atPath: packageFile.path, contents: nil)
        let packageStream = try XCTUnwrap(OutputStream(url: packageFile, append: false))
        packageStream.open()
        _ = try BackupPackageV1.finalizePackage(
            bodyFile: bodyFile,
            bodyWriter: writer,
            headerBase: BackupPackageV1.HeaderBase(
                backupId: "bkp_test",
                createdAtMs: 1_700_000_000_000,
                kind: .MANUAL,
                kdfAlgorithm: params.algorithm,
                kdfSaltHex: params.saltHex,
                kdfIterations: params.iterations,
                kdfMemoryKb: params.memoryKb,
                kdfParallelism: params.parallelism,
                keyFingerprintHex: material.fingerprintHex
            ),
            finalOutput: packageStream
        )
        packageStream.close()
        return packageFile
    }

    private func sha256Hex(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    private func deterministicData(byteCount: Int, seed: Int) -> Data {
        var data = Data(capacity: byteCount)
        for index in 0..<byteCount {
            data.append(UInt8((index * 31 + seed) % 251))
        }
        return data
    }
}
