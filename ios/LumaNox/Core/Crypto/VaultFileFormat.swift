import Foundation

enum VaultFileCipherVersion {
    static let cbcV1 = 1
    static let aeadV2 = 2
}

enum VaultFileFormat {
    static let v2Magic = Data([0x4C, 0x4E, 0x56, 0x4C, 0x54, 0x32, 0x00, 0x00]) // LNVLT2\0\0
    static let v2Version: UInt8 = UInt8(VaultFileCipherVersion.aeadV2)
    static let v2AES256GCMAlgorithm: UInt8 = 1
    static let v2DefaultChunkSize = 64 * 1024
    static let v2BaseNonceLength = 12
    static let v2AuthenticationTagLength = 16
}
