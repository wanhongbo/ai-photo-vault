import Foundation
@testable import LumaNox
import XCTest

final class VaultCipherV2Tests: XCTestCase {
    private var tempDirectory: URL!

    override func setUpWithError() throws {
        tempDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("lumanox-vault-cipher-tests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: tempDirectory, withIntermediateDirectories: true)
        try VaultCipher.shared.installTestingKeyForUnitTests(deterministicData(byteCount: 32))
    }

    override func tearDownWithError() throws {
        try? VaultCipher.shared.installTestingKeyForUnitTests(nil)
        if let tempDirectory {
            try? FileManager.default.removeItem(at: tempDirectory)
        }
        tempDirectory = nil
    }

    func testAEADV2EncryptDecryptRoundTrip() throws {
        let plain = deterministicData(byteCount: 180_000)
        let source = try writePlain(plain, name: "source.bin")
        let encrypted = tempDirectory.appendingPathComponent("asset_v2.bin")

        try VaultCipher.shared.encryptFile(at: source, to: encrypted)

        XCTAssertEqual(VaultCipher.shared.cipherVersion(of: encrypted), vaultCipherVersionAEADv2)
        XCTAssertEqual(try VaultCipher.shared.decryptFile(at: encrypted), plain)
    }

    func testMixedCBCV1AndAEADV2FilesRemainReadable() throws {
        let v1Plain = deterministicData(byteCount: 32_513)
        let v2Plain = deterministicData(byteCount: 96_777)
        let v1Source = try writePlain(v1Plain, name: "v1-source.bin")
        let v2Source = try writePlain(v2Plain, name: "v2-source.bin")
        let v1Encrypted = tempDirectory.appendingPathComponent("asset_v1.bin")
        let v2Encrypted = tempDirectory.appendingPathComponent("asset_v2.bin")

        try VaultCipher.shared.encryptFile(at: v1Source, to: v1Encrypted, version: vaultCipherVersionCBCv1)
        try VaultCipher.shared.encryptFile(at: v2Source, to: v2Encrypted, version: vaultCipherVersionAEADv2)

        XCTAssertEqual(VaultCipher.shared.cipherVersion(of: v1Encrypted), vaultCipherVersionCBCv1)
        XCTAssertEqual(VaultCipher.shared.cipherVersion(of: v2Encrypted), vaultCipherVersionAEADv2)
        XCTAssertEqual(try VaultCipher.shared.decryptFile(at: v1Encrypted), v1Plain)
        XCTAssertEqual(try VaultCipher.shared.decryptFile(at: v2Encrypted), v2Plain)
    }

    func testAEADV2RejectsTamperedCiphertext() throws {
        let plain = deterministicData(byteCount: 70_000)
        let source = try writePlain(plain, name: "tamper-source.bin")
        let encrypted = tempDirectory.appendingPathComponent("asset_tampered.bin")

        try VaultCipher.shared.encryptFile(at: source, to: encrypted)
        var bytes = try Data(contentsOf: encrypted)
        XCTAssertGreaterThan(bytes.count, 48)
        bytes[bytes.count - 1] ^= 0x01
        try bytes.write(to: encrypted, options: .atomic)

        XCTAssertThrowsError(try VaultCipher.shared.decryptFile(at: encrypted))
    }

    private func writePlain(_ data: Data, name: String) throws -> URL {
        let url = tempDirectory.appendingPathComponent(name)
        try data.write(to: url, options: .atomic)
        return url
    }

    private func deterministicData(byteCount: Int) -> Data {
        var data = Data(capacity: byteCount)
        for index in 0..<byteCount {
            data.append(UInt8((index * 31 + 17) % 251))
        }
        return data
    }
}
