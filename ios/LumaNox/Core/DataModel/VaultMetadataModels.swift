import Foundation

let vaultMetadataSchemaVersion = 1
let vaultCipherVersionCBCv1 = VaultFileCipherVersion.cbcV1
let vaultCipherVersionAEADv2 = VaultFileCipherVersion.aeadV2

enum VaultMediaKind: String, Codable, Hashable {
    case image
    case video
    case other

    static func infer(from fileExtension: String) -> VaultMediaKind {
        let ext = fileExtension.lowercased()
        if ["mp4", "mov", "m4v", "mkv", "webm", "avi", "3gp", "flv"].contains(ext) {
            return .video
        }
        if ["jpg", "jpeg", "png", "heic", "heif", "gif", "webp", "tiff"].contains(ext) {
            return .image
        }
        return .other
    }
}

enum VaultMediaState: String, Codable, Hashable {
    case active
    case trashed
}

enum VaultMediaSource: String, Codable, Hashable {
    case picker
    case camera
    case restore
    case unknown
}

struct VaultAiLabelObservation: Codable, Hashable {
    var identifier: String
    var confidence: Double
}

enum VaultAISubjectKind: String, Codable, Hashable {
    case people
    case pet
}

struct VaultAIIndexRecord: Codable, Hashable {
    var recordID: String
    var sourceFingerprint: String
    var mediaKind: VaultMediaKind
    var category: String?
    var tags: [String]
    var labels: [VaultAiLabelObservation]
    var sensitiveScore: Double?
    var cleanupScore: Double?
    var duplicateGroupId: String?
    var featurePrintBase64: String?
    var subjectKind: VaultAISubjectKind?
    var subjectClusterId: String?
}

struct VaultAISubjectCluster: Identifiable, Codable, Hashable {
    var id: String
    var kind: VaultAISubjectKind
    var name: String?
    var memberRecordIDs: [String]
    var representativeRecordID: String?
    var createdAtMs: Int64
    var updatedAtMs: Int64
}

struct VaultAIIndexSnapshot: Codable, Hashable {
    var schemaVersion: Int
    var analyzerVersion: Int
    var records: [VaultAIIndexRecord]
    var subjectClusters: [VaultAISubjectCluster]
    var updatedAtMs: Int64

    static let empty = VaultAIIndexSnapshot(
        schemaVersion: 1,
        analyzerVersion: 0,
        records: [],
        subjectClusters: [],
        updatedAtMs: 0
    )
}

struct VaultAiMetadata: Codable, Hashable {
    var scannedAtMs: Int64?
    var sensitiveScore: Double?
    var cleanupScore: Double?
    var category: String?
    var tags: [String]
    var analyzerVersion: Int?
    var sourceFingerprint: String?
    var labels: [VaultAiLabelObservation]
    var perceptualHashHex: String?
    var colorFingerprintHex: String?
    var duplicateGroupId: String?
    var sensitiveIgnoredAtMs: Int64?

    static let empty = VaultAiMetadata(
        scannedAtMs: nil,
        sensitiveScore: nil,
        cleanupScore: nil,
        category: nil,
        tags: [],
        analyzerVersion: nil,
        sourceFingerprint: nil,
        labels: [],
        perceptualHashHex: nil,
        colorFingerprintHex: nil,
        duplicateGroupId: nil,
        sensitiveIgnoredAtMs: nil
    )

    init(
        scannedAtMs: Int64?,
        sensitiveScore: Double?,
        cleanupScore: Double?,
        category: String?,
        tags: [String],
        analyzerVersion: Int? = nil,
        sourceFingerprint: String? = nil,
        labels: [VaultAiLabelObservation] = [],
        perceptualHashHex: String? = nil,
        colorFingerprintHex: String? = nil,
        duplicateGroupId: String? = nil,
        sensitiveIgnoredAtMs: Int64? = nil
    ) {
        self.scannedAtMs = scannedAtMs
        self.sensitiveScore = sensitiveScore
        self.cleanupScore = cleanupScore
        self.category = category
        self.tags = tags
        self.analyzerVersion = analyzerVersion
        self.sourceFingerprint = sourceFingerprint
        self.labels = labels
        self.perceptualHashHex = perceptualHashHex
        self.colorFingerprintHex = colorFingerprintHex
        self.duplicateGroupId = duplicateGroupId
        self.sensitiveIgnoredAtMs = sensitiveIgnoredAtMs
    }

    private enum CodingKeys: String, CodingKey {
        case scannedAtMs
        case sensitiveScore
        case cleanupScore
        case category
        case tags
        case analyzerVersion
        case sourceFingerprint
        case labels
        case perceptualHashHex
        case colorFingerprintHex
        case duplicateGroupId
        case sensitiveIgnoredAtMs
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        scannedAtMs = try container.decodeIfPresent(Int64.self, forKey: .scannedAtMs)
        sensitiveScore = try container.decodeIfPresent(Double.self, forKey: .sensitiveScore)
        cleanupScore = try container.decodeIfPresent(Double.self, forKey: .cleanupScore)
        category = try container.decodeIfPresent(String.self, forKey: .category)
        tags = try container.decodeIfPresent([String].self, forKey: .tags) ?? []
        analyzerVersion = try container.decodeIfPresent(Int.self, forKey: .analyzerVersion)
        sourceFingerprint = try container.decodeIfPresent(String.self, forKey: .sourceFingerprint)
        labels = try container.decodeIfPresent([VaultAiLabelObservation].self, forKey: .labels) ?? []
        perceptualHashHex = try container.decodeIfPresent(String.self, forKey: .perceptualHashHex)
        colorFingerprintHex = try container.decodeIfPresent(String.self, forKey: .colorFingerprintHex)
        duplicateGroupId = try container.decodeIfPresent(String.self, forKey: .duplicateGroupId)
        sensitiveIgnoredAtMs = try container.decodeIfPresent(Int64.self, forKey: .sensitiveIgnoredAtMs)
    }
}

struct VaultMediaRecord: Identifiable, Codable, Hashable {
    var id: String
    var storagePath: String
    var albumName: String
    var fileName: String
    var mediaKind: VaultMediaKind
    var state: VaultMediaState
    var encryptedSizeBytes: Int64
    var originalSha256Hex: String?
    var encryptedSha256Hex: String?
    var originalFileName: String?
    var mimeType: String?
    var uti: String?
    var cipherVersion: Int?
    var createdAtMs: Int64
    var importedAtMs: Int64
    var modifiedAtMs: Int64
    var trashedAtMs: Int64?
    var width: Int?
    var height: Int?
    var durationMs: Int64?
    var source: VaultMediaSource
    var ai: VaultAiMetadata

    var isVideo: Bool { mediaKind == .video }

    func absoluteURL(documentsDirectory: URL) -> URL {
        documentsDirectory.appendingPathComponent(storagePath, isDirectory: false)
    }
}

struct VaultAlbumRecord: Identifiable, Codable, Hashable {
    var id: String
    var name: String
    var mediaCount: Int
    var createdAtMs: Int64
    var modifiedAtMs: Int64
}

struct VaultMetadataSnapshot: Codable, Hashable {
    var schemaVersion: Int
    var albums: [VaultAlbumRecord]
    var media: [VaultMediaRecord]
    var updatedAtMs: Int64

    static let empty = VaultMetadataSnapshot(
        schemaVersion: vaultMetadataSchemaVersion,
        albums: [],
        media: [],
        updatedAtMs: 0
    )

    var activeMedia: [VaultMediaRecord] {
        media.filter { $0.state == .active }
    }

    var trashedMedia: [VaultMediaRecord] {
        media.filter { $0.state == .trashed }
    }

    var totalActiveCount: Int {
        activeMedia.count
    }

    var totalEncryptedBytes: Int64 {
        media.reduce(Int64(0)) { $0 + $1.encryptedSizeBytes }
    }

    func activeMedia(in albumName: String) -> [VaultMediaRecord] {
        activeMedia
            .filter { $0.albumName == albumName }
            .sorted { $0.modifiedAtMs > $1.modifiedAtMs }
    }

    func recentActive(limit: Int) -> [VaultMediaRecord] {
        Array(activeMedia.sorted { $0.modifiedAtMs > $1.modifiedAtMs }.prefix(limit))
    }

    func searchActive(query: String, limit: Int = 200) -> [VaultMediaRecord] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let source = activeMedia.sorted { $0.modifiedAtMs > $1.modifiedAtMs }
        guard !trimmed.isEmpty else { return Array(source.prefix(limit)) }
        let queryTerms = vaultSearchExpandedTerms(for: trimmed)
        return Array(source.filter { record in
            let haystack = vaultSearchHaystack(for: record)
            return queryTerms.contains { query in
                haystack.contains { value in
                    value.localizedCaseInsensitiveContains(query)
                }
            }
        }.prefix(limit))
    }
}

private func vaultSearchHaystack(for record: VaultMediaRecord) -> [String] {
    var values = [
        record.fileName,
        record.albumName,
        record.originalFileName ?? "",
        record.ai.category ?? "",
    ]
    values.append(contentsOf: record.ai.tags)
    values.append(contentsOf: record.ai.labels.map(\.identifier))
    values.append(contentsOf: vaultSearchExpandedTerms(for: record.ai.category ?? ""))
    for tag in record.ai.tags {
        values.append(contentsOf: vaultSearchExpandedTerms(for: tag))
    }
    for label in record.ai.labels {
        values.append(contentsOf: vaultSearchExpandedTerms(for: label.identifier))
    }
    return values.filter { !$0.isEmpty }
}

private func vaultSearchExpandedTerms(for query: String) -> Set<String> {
    let lowered = query.lowercased()
    let tokens = Set(lowered.split { !$0.isLetter && !$0.isNumber && $0 != "_" }.map(String.init))
    var terms = tokens
    if !lowered.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
        terms.insert(lowered)
    }

    let groups: [[String]] = [
        ["狗", "犬", "宠物", "寵物", "dog", "pet", "animal", "mammal"],
        ["猫", "貓", "宠物", "寵物", "cat", "pet", "animal", "mammal"],
        ["证件", "證件", "身份证", "身份證", "护照", "護照", "passport", "id", "id_card", "document", "documents"],
        ["银行卡", "銀行卡", "信用卡", "bank", "bank_card", "credit_card", "card"],
        ["二维码", "二維碼", "条码", "條碼", "qr", "barcode", "code"],
        ["截图", "截屏", "螢幕截圖", "screenshot", "screen", "screenshots"],
        ["人脸", "人像", "自拍", "人物", "face", "selfie", "portrait", "people", "person"],
        ["风景", "風景", "自然", "旅行", "地点", "地點", "nature", "landscape", "travel", "place", "outdoor"],
        ["票据", "票據", "发票", "發票", "收据", "receipt", "invoice", "paper", "scan"],
        ["模糊", "虚焦", "失焦", "blurry", "blur", "low_quality"],
        ["重复", "重複", "相似", "duplicate", "similar", "dedup"],
    ]
    for group in groups where !terms.isDisjoint(with: group) {
        terms.formUnion(group)
    }
    return terms
}
