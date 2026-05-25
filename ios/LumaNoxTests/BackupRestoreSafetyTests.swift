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
        configureLightweightBackupKdf()

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
        clearBackupKdf()
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

    func testManualBackupRestoreRoundTripCurrentEncryptedVault() async throws {
        let firstPlain = deterministicData(byteCount: 73_001, seed: 61)
        let secondPlain = deterministicData(byteCount: 2_048, seed: 67)
        let firstPath = "\(vaultDefaultAlbumName)/asset_roundtrip_1.jpg"
        let secondPath = "\(vaultDefaultAlbumName)/asset_roundtrip_2.png"
        let firstURL = vaultRoot.appendingPathComponent(firstPath)
        let secondURL = vaultRoot.appendingPathComponent(secondPath)

        try VaultCipher.shared.encryptFileFromChunks(to: firstURL) { emit in try emit(firstPlain) }
        try VaultCipher.shared.encryptFileFromChunks(to: secondURL) { emit in try emit(secondPlain) }
        try cacheBackupKeyForTestPin()

        let output = tempDirectory.appendingPathComponent("roundtrip.aivb")
        let backup = await LocalBackupService.shared.createManualBackup(to: output)
        XCTAssertTrue(backup.success, backup.message)
        XCTAssertEqual(backup.assetCount, 2)

        try FileManager.default.removeItem(at: vaultRoot)
        let restore = await LocalBackupService.shared.restore(from: output, pin: pin)

        XCTAssertTrue(restore.success, restore.message)
        XCTAssertEqual(restore.restored, 2)
        XCTAssertEqual(restore.skipped, 0)
        XCTAssertEqual(restore.failed, 0)
        XCTAssertEqual(try VaultCipher.shared.decryptFile(at: firstURL), firstPlain)
        XCTAssertEqual(try VaultCipher.shared.decryptFile(at: secondURL), secondPlain)
    }

    func testManualBackupFailsOnUnreadableVaultFileInsteadOfReportingEmpty() async throws {
        let invalidURL = vaultRoot
            .appendingPathComponent(vaultDefaultAlbumName, isDirectory: true)
            .appendingPathComponent("asset_invalid.jpg")
        try Data([0xFF, 0xD8, 0xFF, 0xD9]).write(to: invalidURL, options: .atomic)
        try cacheBackupKeyForTestPin()

        let result = await LocalBackupService.shared.createManualBackup(
            to: tempDirectory.appendingPathComponent("invalid.aivb")
        )

        XCTAssertFalse(result.success)
        XCTAssertNotEqual(result.message, BackupError.vaultEmpty.localizedDescription)
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

    private func cacheBackupKeyForTestPin() throws {
        let manager = BackupKeyManager()
        let params = manager.getOrCreateKdfParams()
        let material = try manager.deriveKey(password: pin, params: params)
        try BackupSecretsStore.cache(backupKey: material.key)
    }

    private func configureLightweightBackupKdf() {
        let defaults = UserDefaults.standard
        defaults.set(BackupKeyManager.KdfParams.argon2id, forKey: "backup_kdf_algorithm")
        defaults.set(deterministicData(byteCount: 32, seed: 43).hexString, forKey: "backup_kdf_salt_hex")
        defaults.set(1, forKey: "backup_kdf_iterations")
        defaults.set(1_024, forKey: "backup_kdf_memory_kb")
        defaults.set(1, forKey: "backup_kdf_parallelism")
    }

    private func clearBackupKdf() {
        let defaults = UserDefaults.standard
        for key in [
            "backup_kdf_algorithm",
            "backup_kdf_salt_hex",
            "backup_kdf_iterations",
            "backup_kdf_memory_kb",
            "backup_kdf_parallelism",
        ] {
            defaults.removeObject(forKey: key)
        }
    }

    private func deterministicData(byteCount: Int, seed: Int) -> Data {
        var data = Data(capacity: byteCount)
        for index in 0..<byteCount {
            data.append(UInt8((index * 31 + seed) % 251))
        }
        return data
    }
}
