import Foundation
import UIKit
import Vision

struct VaultAISummary: Equatable {
    var totalCount: Int = 0
    var scannedCount: Int = 0
    var sensitiveCount: Int = 0
    var cleanupCount: Int = 0
    var categoryCounts: [String: Int] = [:]

    var hasUnscanned: Bool {
        scannedCount < totalCount
    }
}

struct VaultAIProgress: Equatable {
    var running = false
    var done = 0
    var total = 0

    var fraction: Double {
        guard total > 0 else { return 0 }
        return min(1, Double(done) / Double(total))
    }
}

enum VaultAICategory {
    static let people = "people"
    static let documents = "documents"
    static let screenshots = "screenshots"
    static let food = "food"
    static let nature = "nature"
    static let videos = "videos"
    static let other = "other"
}

enum VaultAITag {
    static let blurry = "blurry"
    static let overexposed = "overexposed"
    static let duplicate = "duplicate"
    static let face = "face"
    static let text = "text"
    static let barcode = "barcode"
    static let idCard = "id_card"
    static let bankCard = "bank_card"
    static let contact = "contact"
    static let screenshot = "screenshot"
    static let video = "video"
}

private func vaultAINowMs() -> Int64 {
    Int64(Date().timeIntervalSince1970 * 1000)
}

@MainActor
final class VaultAIAnalysisService: ObservableObject {
    static let shared = VaultAIAnalysisService()
    nonisolated static let currentAnalyzerVersion = 3

    @Published private(set) var records: [VaultMediaRecord] = []
    @Published private(set) var summary = VaultAISummary()
    @Published private(set) var progress = VaultAIProgress()
    @Published private(set) var lastError: String?

    private let vaultStore = VaultStore.shared
    private let metadataStore = VaultMetadataStore.shared
    private let fileManager = FileManager.default

    private init() {}

    func refreshSummary() {
        do {
            let metadata = try metadataStore.reconcile(
                vaultRoot: try vaultStore.rootDirectory(),
                trashRoot: try vaultStore.trashDirectory()
            )
            records = metadata.activeMedia.sorted { $0.modifiedAtMs > $1.modifiedAtMs }
            summary = Self.makeSummary(from: records)
            lastError = nil
        } catch {
            records = metadataStore.activeMediaRecords()
            summary = Self.makeSummary(from: records)
            lastError = error.localizedDescription
        }
    }

    func scanVault() async {
        guard !progress.running else { return }
        refreshSummary()
        let targets = records
        guard !targets.isEmpty else { return }

        progress = VaultAIProgress(running: true, done: 0, total: targets.count)
        lastError = nil
        let documents = documentsDirectory()
        var results: [VaultAIAnalyzer.Result] = []

        for record in targets {
            do {
                let result: VaultAIAnalyzer.Result
                if Self.canReuseAnalysis(for: record) {
                    result = VaultAIAnalyzer.reuse(record: record)
                } else {
                    result = try await VaultAIAnalyzer.analyze(
                        record: record,
                        documentsDirectory: documents
                    )
                }
                results.append(result)
            } catch {
                var ai = record.ai
                ai.scannedAtMs = Self.nowMs()
                ai.analyzerVersion = Self.currentAnalyzerVersion
                ai.sourceFingerprint = VaultAIAnalyzer.sourceFingerprint(for: record)
                ai.category = record.mediaKind == .video ? VaultAICategory.videos : VaultAICategory.other
                ai.tags = Array(Set(ai.tags + (record.mediaKind == .video ? [VaultAITag.video] : []))).sorted()
                results.append(VaultAIAnalyzer.Result(recordID: record.id, ai: ai, dHash: nil, rgbFingerprint: nil))
            }
            progress.done += 1
        }

        markDuplicates(in: &results)

        do {
            try metadataStore.updateAiMetadata(Dictionary(uniqueKeysWithValues: results.map { ($0.recordID, $0.ai) }))
            refreshSummary()
        } catch {
            lastError = error.localizedDescription
        }
        progress = VaultAIProgress()
    }

    nonisolated static func isSensitive(_ record: VaultMediaRecord) -> Bool {
        (record.ai.sensitiveScore ?? 0) >= 0.45
    }

    nonisolated static func isCleanable(_ record: VaultMediaRecord) -> Bool {
        let tags = Set(record.ai.tags)
        return (record.ai.cleanupScore ?? 0) >= 0.5
            || tags.contains(VaultAITag.blurry)
            || tags.contains(VaultAITag.overexposed)
            || tags.contains(VaultAITag.duplicate)
    }

    nonisolated static func categoryLabelKey(_ category: String) -> String {
        "ai_category_\(category)"
    }

    nonisolated static func nowMs() -> Int64 {
        vaultAINowMs()
    }

    nonisolated static func canReuseAnalysis(for record: VaultMediaRecord) -> Bool {
        record.ai.scannedAtMs != nil
            && record.ai.analyzerVersion == currentAnalyzerVersion
            && record.ai.sourceFingerprint == VaultAIAnalyzer.sourceFingerprint(for: record)
    }

    private static func makeSummary(from records: [VaultMediaRecord]) -> VaultAISummary {
        var categoryCounts: [String: Int] = [:]
        var scanned = 0
        var sensitive = 0
        var cleanup = 0

        for record in records {
            if record.ai.scannedAtMs != nil { scanned += 1 }
            if isSensitive(record) { sensitive += 1 }
            if isCleanable(record) { cleanup += 1 }
            if let category = record.ai.category {
                categoryCounts[category, default: 0] += 1
            }
        }

        return VaultAISummary(
            totalCount: records.count,
            scannedCount: scanned,
            sensitiveCount: sensitive,
            cleanupCount: cleanup,
            categoryCounts: categoryCounts
        )
    }

    private func markDuplicates(in results: inout [VaultAIAnalyzer.Result]) {
        for index in results.indices {
            guard results[index].ai.tags.contains(VaultAITag.duplicate) else { continue }
            var ai = results[index].ai
            ai.tags.removeAll { $0 == VaultAITag.duplicate }
            let tags = Set(ai.tags)
            if !tags.contains(VaultAITag.blurry), !tags.contains(VaultAITag.overexposed), (ai.cleanupScore ?? 0) <= 0.82 {
                ai.cleanupScore = 0
            }
            results[index].ai = ai
        }

        let hashes = results.enumerated().compactMap { index, result -> (Int, UInt64, [UInt8])? in
            guard let hash = result.dHash,
                  let rgbFingerprint = result.rgbFingerprint else { return nil }
            return (index, hash, rgbFingerprint)
        }
        guard hashes.count > 1 else { return }

        var duplicateIndexes = Set<Int>()
        for left in 0..<hashes.count {
            for right in (left + 1)..<hashes.count {
                let leftResult = results[hashes[left].0]
                let rightResult = results[hashes[right].0]
                guard isDuplicateEligible(leftResult.ai), isDuplicateEligible(rightResult.ai) else { continue }
                if (hashes[left].1 ^ hashes[right].1).nonzeroBitCount <= 2,
                   rgbDistance(hashes[left].2, hashes[right].2) <= 5.0 {
                    duplicateIndexes.insert(hashes[right].0)
                }
            }
        }

        for index in duplicateIndexes {
            var ai = results[index].ai
            ai.cleanupScore = max(ai.cleanupScore ?? 0, 0.82)
            ai.tags = Array(Set(ai.tags + [VaultAITag.duplicate])).sorted()
            results[index].ai = ai
        }
    }

    private func isDuplicateEligible(_ ai: VaultAiMetadata) -> Bool {
        let tags = Set(ai.tags)
        return ai.category == VaultAICategory.other
            && !tags.contains(VaultAITag.idCard)
            && !tags.contains(VaultAITag.bankCard)
            && !tags.contains(VaultAITag.barcode)
            && !tags.contains(VaultAITag.screenshot)
    }

    private func rgbDistance(_ left: [UInt8], _ right: [UInt8]) -> Double {
        guard left.count == right.count, !left.isEmpty else { return .greatestFiniteMagnitude }
        let total = zip(left, right).reduce(0) { partial, pair in
            partial + abs(Int(pair.0) - Int(pair.1))
        }
        return Double(total) / Double(left.count)
    }

    private func documentsDirectory() -> URL {
        fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }
}

enum VaultAIAnalyzer {
    struct Result {
        let recordID: String
        var ai: VaultAiMetadata
        let dHash: UInt64?
        let rgbFingerprint: [UInt8]?
    }

    static func reuse(record: VaultMediaRecord) -> Result {
        Result(
            recordID: record.id,
            ai: record.ai,
            dHash: uint64(fromHex: record.ai.perceptualHashHex),
            rgbFingerprint: bytes(fromHex: record.ai.colorFingerprintHex)
        )
    }

    static func analyze(record: VaultMediaRecord, documentsDirectory: URL) async throws -> Result {
        try await Task.detached(priority: .utility) {
            if record.mediaKind == .video {
                let ai = VaultAiMetadata(
                    scannedAtMs: vaultAINowMs(),
                    sensitiveScore: 0,
                    cleanupScore: 0,
                    category: VaultAICategory.videos,
                    tags: [VaultAITag.video],
                    analyzerVersion: VaultAIAnalysisService.currentAnalyzerVersion,
                    sourceFingerprint: sourceFingerprint(for: record)
                )
                return Result(recordID: record.id, ai: ai, dHash: nil, rgbFingerprint: nil)
            }

            let encryptedURL = record.absoluteURL(documentsDirectory: documentsDirectory)
            let data = try VaultCipher.shared.decryptFile(at: encryptedURL)
            guard let image = UIImage(data: data),
                  let cgImage = image.cgImage else {
                let ai = VaultAiMetadata(
                    scannedAtMs: vaultAINowMs(),
                    sensitiveScore: 0,
                    cleanupScore: 0,
                    category: VaultAICategory.other,
                    tags: [],
                    analyzerVersion: VaultAIAnalysisService.currentAnalyzerVersion,
                    sourceFingerprint: sourceFingerprint(for: record)
                )
                return Result(recordID: record.id, ai: ai, dHash: nil, rgbFingerprint: nil)
            }

            let quality = analyzeQuality(cgImage: cgImage)
            let visualStats = analyzeVisualStats(cgImage: cgImage)
            let vision = analyzeVision(cgImage: cgImage, record: record)
            let hints = metadataHints(for: record)
            var tags = Set(vision.tags)
            hints.tags.forEach { tags.insert($0) }
            var cleanupScore: Double = 0

            if quality.isBlurry {
                tags.insert(VaultAITag.blurry)
                cleanupScore = max(cleanupScore, 0.76)
            }
            let documentLike = tags.contains(VaultAITag.idCard)
                || tags.contains(VaultAITag.bankCard)
                || tags.contains(VaultAITag.barcode)
            if quality.isOverexposed && !documentLike {
                tags.insert(VaultAITag.overexposed)
                cleanupScore = max(cleanupScore, 0.58)
            }
            if isLikelyScreenshot(record: record, image: image, visionTags: tags) {
                tags.insert(VaultAITag.screenshot)
            }

            let category = hints.category ?? pickCategory(
                record: record,
                labels: vision.labels,
                tags: tags,
                stats: visualStats,
                hasProminentFace: vision.hasProminentFace,
                hasProminentHuman: vision.hasProminentHuman
            )
            let dHash = quality.dHash
            let colorFingerprint = rgbFingerprint(cgImage: cgImage)
            let ai = VaultAiMetadata(
                scannedAtMs: vaultAINowMs(),
                sensitiveScore: max(vision.sensitiveScore, hints.sensitiveScore),
                cleanupScore: cleanupScore,
                category: category,
                tags: Array(tags).sorted(),
                analyzerVersion: VaultAIAnalysisService.currentAnalyzerVersion,
                sourceFingerprint: sourceFingerprint(for: record),
                labels: vision.labels.map { VaultAiLabelObservation(identifier: $0.identifier, confidence: $0.confidence) },
                perceptualHashHex: dHash.map(hexString),
                colorFingerprintHex: hexString(colorFingerprint)
            )
            return Result(recordID: record.id, ai: ai, dHash: dHash, rgbFingerprint: colorFingerprint)
        }.value
    }

    static func sourceFingerprint(for record: VaultMediaRecord) -> String {
        [
            record.storagePath,
            String(record.modifiedAtMs),
            String(record.encryptedSizeBytes),
            record.encryptedSha256Hex ?? "",
            record.originalSha256Hex ?? "",
            String(record.width ?? 0),
            String(record.height ?? 0),
            String(record.durationMs ?? 0),
        ].joined(separator: "|")
    }

    private static func hexString(_ value: UInt64) -> String {
        String(format: "%016llx", value)
    }

    private static func uint64(fromHex value: String?) -> UInt64? {
        guard let value, value.count <= 16 else { return nil }
        return UInt64(value, radix: 16)
    }

    private static func hexString(_ bytes: [UInt8]) -> String {
        bytes.map { String(format: "%02x", $0) }.joined()
    }

    private static func bytes(fromHex value: String?) -> [UInt8]? {
        guard let value, value.count % 2 == 0 else { return nil }
        var bytes: [UInt8] = []
        bytes.reserveCapacity(value.count / 2)
        var index = value.startIndex
        while index < value.endIndex {
            let next = value.index(index, offsetBy: 2)
            guard let byte = UInt8(value[index..<next], radix: 16) else { return nil }
            bytes.append(byte)
            index = next
        }
        return bytes
    }

    private struct QualityResult {
        let isBlurry: Bool
        let isOverexposed: Bool
        let dHash: UInt64?
    }

    private struct VisualStats {
        let blueRatio: Double
        let greenRatio: Double
        let warmRatio: Double
        let skinToneRatio: Double
        let darkRatio: Double
        let whiteRatio: Double
        let saturationAverage: Double
    }

    private struct VisionResult {
        let sensitiveScore: Double
        let labels: [VisionLabel]
        let tags: [String]
        let hasProminentFace: Bool
        let hasProminentHuman: Bool
    }

    private struct VisionLabel {
        let identifier: String
        let confidence: Double
    }

    private struct MetadataHints {
        let sensitiveScore: Double
        let category: String?
        let tags: Set<String>
    }

    private static func analyzeVision(cgImage: CGImage, record: VaultMediaRecord) -> VisionResult {
        let humanRequest = VNDetectHumanRectanglesRequest()
        let faceRequest = VNDetectFaceRectanglesRequest()
        let barcodeRequest = VNDetectBarcodesRequest()
        let textRequest = VNRecognizeTextRequest()
        textRequest.recognitionLevel = .accurate
        textRequest.usesLanguageCorrection = false
        textRequest.recognitionLanguages = ["zh-Hans", "en-US"]
        textRequest.customWords = ["身份证", "证件号码", "银行卡", "护照", "PASSPORT", "ID CARD", "BANK", "QR"]
        textRequest.minimumTextHeight = 0.01
        let classifyRequest = VNClassifyImageRequest()

        let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
        try? handler.perform([humanRequest, faceRequest, barcodeRequest, textRequest, classifyRequest])

        let faceObservations = faceRequest.results ?? []
        let faceCount = faceObservations.count
        let barcodeCount = barcodeRequest.results?.count ?? 0
        let hasProminentFace = faceObservations.contains { observation in
            let box = observation.boundingBox
            return box.width * box.height >= 0.012 && box.height >= 0.10
        }
        let hasProminentHuman = (humanRequest.results ?? []).contains { observation in
            let box = observation.boundingBox
            return box.width * box.height >= 0.035 && box.height >= 0.16
        }
        let recognizedText = (textRequest.results ?? [])
            .compactMap { $0.topCandidates(1).first?.string }
            .joined(separator: " ")
        let labels = (classifyRequest.results ?? [])
            .prefix(8)
            .filter { $0.confidence >= 0.22 }
            .map { VisionLabel(identifier: $0.identifier.lowercased(), confidence: Double($0.confidence)) }

        var tags = Set<String>()
        var score: Double = 0
        if faceCount > 0 {
            tags.insert(VaultAITag.face)
            score = max(score, 0.66)
        }
        if barcodeCount > 0 {
            tags.insert(VaultAITag.barcode)
            score = max(score, 0.72)
        }
        if containsBarcodeText(recognizedText) {
            tags.insert(VaultAITag.barcode)
            score = max(score, 0.68)
        }
        if !recognizedText.isEmpty {
            tags.insert(VaultAITag.text)
            score = max(score, 0.35)
        }
        if containsIDCardText(recognizedText) {
            tags.insert(VaultAITag.idCard)
            score = max(score, 0.86)
        }
        if containsBankCardText(recognizedText) {
            tags.insert(VaultAITag.bankCard)
            score = max(score, 0.80)
        }
        if containsContactText(recognizedText) {
            tags.insert(VaultAITag.contact)
            score = max(score, 0.62)
        }
        if isLikelyScreenshotName(record) {
            tags.insert(VaultAITag.screenshot)
        }

        return VisionResult(
            sensitiveScore: score,
            labels: labels,
            tags: Array(tags),
            hasProminentFace: hasProminentFace,
            hasProminentHuman: hasProminentHuman
        )
    }

    private static func analyzeQuality(cgImage: CGImage) -> QualityResult {
        let gray = grayscalePixels(cgImage: cgImage, width: 64, height: 64) ?? []
        let laplacianVariance = laplacianVariance(gray, width: 64, height: 64)
        let brightRatio = gray.isEmpty ? 0 : Double(gray.filter { $0 > 245 }.count) / Double(gray.count)
        let averageBrightness = gray.isEmpty ? 0 : Double(gray.reduce(0) { $0 + Int($1) }) / Double(gray.count * 255)
        let dHash = differenceHash(cgImage: cgImage)

        return QualityResult(
            isBlurry: !gray.isEmpty && laplacianVariance < 85,
            isOverexposed: !gray.isEmpty && averageBrightness > 0.76 && brightRatio > 0.18,
            dHash: dHash
        )
    }

    private static func metadataHints(for record: VaultMediaRecord) -> MetadataHints {
        let name = (record.originalFileName ?? record.fileName).lowercased()
        let nameTokens = tokens(in: name)
        var tags = Set<String>()
        var sensitiveScore: Double = 0
        var category: String?

        if name.contains("id_document")
            || name.contains("idcard")
            || name.contains("id_card")
            || name.contains("passport")
            || name.contains("身份证")
            || name.contains("证件") {
            tags.formUnion([VaultAITag.text, VaultAITag.idCard])
            sensitiveScore = max(sensitiveScore, 0.86)
            category = VaultAICategory.documents
        }
        if name.contains("bank_card")
            || name.contains("bankcard")
            || name.contains("credit_card")
            || name.contains("银行卡") {
            tags.formUnion([VaultAITag.text, VaultAITag.bankCard])
            sensitiveScore = max(sensitiveScore, 0.80)
            category = VaultAICategory.documents
        }
        if name.contains("qr")
            || name.contains("barcode")
            || name.contains("二维码")
            || name.contains("条码") {
            tags.insert(VaultAITag.barcode)
            sensitiveScore = max(sensitiveScore, 0.72)
            category = VaultAICategory.documents
        }
        if name.contains("screenshot")
            || name.contains("screen_shot")
            || name.contains("screen shot")
            || name.contains("截屏")
            || name.contains("截图")
            || name.contains("截圖") {
            tags.insert(VaultAITag.screenshot)
            category = VaultAICategory.screenshots
        }
        if containsAny(nameTokens, ["portrait", "selfie", "face", "avatar"])
            || name.contains("人物")
            || name.contains("人像") {
            tags.insert(VaultAITag.face)
            sensitiveScore = max(sensitiveScore, 0.66)
        }
        if name.contains("food")
            || name.contains("meal")
            || name.contains("dish")
            || name.contains("drink")
            || name.contains("restaurant")
            || name.contains("美食")
            || name.contains("餐") {
            category = VaultAICategory.food
        }
        if name.contains("nature")
            || name.contains("landscape")
            || name.contains("mountain")
            || name.contains("forest")
            || name.contains("beach")
            || name.contains("sea")
            || name.contains("自然")
            || name.contains("风景")
            || name.contains("風景") {
            category = VaultAICategory.nature
        }
        if name.contains("receipt")
            || name.contains("invoice")
            || name.contains("document")
            || name.contains("paper")
            || name.contains("scan")
            || name.contains("合同")
            || name.contains("票据")
            || name.contains("发票") {
            tags.insert(VaultAITag.text)
            category = VaultAICategory.documents
        }

        return MetadataHints(sensitiveScore: sensitiveScore, category: category, tags: tags)
    }

    private static func analyzeVisualStats(cgImage: CGImage) -> VisualStats {
        let samples = rgbPixels(cgImage: cgImage, width: 64, height: 64)
        guard !samples.isEmpty else {
            return VisualStats(
                blueRatio: 0,
                greenRatio: 0,
                warmRatio: 0,
                skinToneRatio: 0,
                darkRatio: 0,
                whiteRatio: 0,
                saturationAverage: 0
            )
        }

        var blue = 0
        var green = 0
        var warm = 0
        var skin = 0
        var dark = 0
        var white = 0
        var saturationTotal = 0.0

        for sample in samples {
            let r = Double(sample.r) / 255
            let g = Double(sample.g) / 255
            let b = Double(sample.b) / 255
            let maxValue = max(r, g, b)
            let minValue = min(r, g, b)
            let saturation = maxValue == 0 ? 0 : (maxValue - minValue) / maxValue
            saturationTotal += saturation

            if b > 0.42 && b > r * 1.08 && b > g * 0.88 { blue += 1 }
            if g > 0.28 && g > r * 0.86 && g > b * 0.86 { green += 1 }
            if r > 0.55 && g > 0.22 && r > b * 1.18 && saturation > 0.22 { warm += 1 }
            if r > 0.42 && g > 0.24 && b > 0.16 && r > g && g >= b && (r - g) > 0.06 && saturation > 0.18 && maxValue < 0.94 {
                skin += 1
            }
            if maxValue < 0.22 { dark += 1 }
            if minValue > 0.82 && saturation < 0.18 { white += 1 }
        }

        let total = Double(samples.count)
        return VisualStats(
            blueRatio: Double(blue) / total,
            greenRatio: Double(green) / total,
            warmRatio: Double(warm) / total,
            skinToneRatio: Double(skin) / total,
            darkRatio: Double(dark) / total,
            whiteRatio: Double(white) / total,
            saturationAverage: saturationTotal / total
        )
    }

    private static func pickCategory(
        record: VaultMediaRecord,
        labels: [VisionLabel],
        tags: Set<String>,
        stats: VisualStats,
        hasProminentFace: Bool,
        hasProminentHuman: Bool
    ) -> String {
        if tags.contains(VaultAITag.idCard) || tags.contains(VaultAITag.bankCard) || tags.contains(VaultAITag.barcode) {
            return VaultAICategory.documents
        }
        if tags.contains(VaultAITag.screenshot) {
            return VaultAICategory.screenshots
        }

        var scores = [
            VaultAICategory.people: 0.0,
            VaultAICategory.documents: 0.0,
            VaultAICategory.food: 0.0,
            VaultAICategory.nature: 0.0,
            VaultAICategory.screenshots: 0.0,
        ]

        for label in labels {
            addLabelEvidence(label, to: &scores)
        }

        let humanLabelScore = humanEvidenceScore(labels)
        let nonHumanSubjectScore = nonHumanEvidenceScore(labels)
        let hasFaceEvidence = tags.contains(VaultAITag.face)
        let hasSkinToneHumanEvidence = stats.skinToneRatio > 0.045 && stats.darkRatio > 0.06 && stats.warmRatio < 0.50
        let hasFaceBasedHumanEvidence = hasProminentFace
            || (hasFaceEvidence && hasSkinToneHumanEvidence && nonHumanSubjectScore < 0.45)

        if hasProminentHuman {
            scores[VaultAICategory.people, default: 0] += 1.2
        } else if hasFaceBasedHumanEvidence {
            scores[VaultAICategory.people, default: 0] += 0.95
        } else if hasFaceEvidence {
            scores[VaultAICategory.people, default: 0] += 0.25
        }
        if tags.contains(VaultAITag.text) {
            scores[VaultAICategory.documents, default: 0] += 0.45
        }

        if hasProminentHuman || hasFaceBasedHumanEvidence || humanLabelScore >= 0.45 {
            if stats.skinToneRatio > 0.08 && stats.darkRatio > 0.14 && stats.warmRatio < 0.42 {
                scores[VaultAICategory.people, default: 0] += 0.55
            } else if stats.skinToneRatio > 0.05 && stats.darkRatio > 0.08 {
                scores[VaultAICategory.people, default: 0] += 0.25
            }
        }
        if stats.greenRatio > 0.22 && stats.blueRatio > 0.12 {
            scores[VaultAICategory.nature, default: 0] += 0.8
        } else if stats.greenRatio > 0.28 || stats.blueRatio > 0.26 {
            scores[VaultAICategory.nature, default: 0] += 0.45
        }
        if stats.warmRatio > 0.30 && stats.saturationAverage > 0.22 && stats.whiteRatio < 0.42 {
            scores[VaultAICategory.food, default: 0] += 0.8
        } else if stats.warmRatio > 0.24 && stats.saturationAverage > 0.30 {
            scores[VaultAICategory.food, default: 0] += 0.45
        }

        let ranked = scores
            .filter { $0.value > 0 }
            .sorted { left, right in
                if left.value == right.value { return categoryPriority(left.key) < categoryPriority(right.key) }
                return left.value > right.value
            }
        guard let best = ranked.first else { return VaultAICategory.other }
        let runnerUp = ranked.dropFirst().first?.value ?? 0
        if best.value < 0.65 { return VaultAICategory.other }
        if best.value < 1.4 && best.value - runnerUp < 0.20 { return VaultAICategory.other }
        if best.key == VaultAICategory.people {
            if nonHumanSubjectScore >= 0.45
                && humanLabelScore < max(0.65, nonHumanSubjectScore + 0.20) {
                return VaultAICategory.other
            }
            if !hasProminentHuman && !hasFaceBasedHumanEvidence && humanLabelScore < 0.45 {
                return VaultAICategory.other
            }
        }
        return best.key
    }

    private static func isLikelyScreenshot(record: VaultMediaRecord, image: UIImage, visionTags: Set<String>) -> Bool {
        if isLikelyScreenshotName(record) { return true }
        return visionTags.contains(VaultAITag.text) && isScreenLikeAspect(record: record, image: image)
    }

    private static func isLikelyScreenshotName(_ record: VaultMediaRecord) -> Bool {
        let name = (record.originalFileName ?? record.fileName).lowercased()
        return name.contains("screenshot")
            || name.contains("screen_shot")
            || name.contains("screen shot")
            || name.contains("截屏")
            || name.contains("截图")
            || name.contains("截圖")
    }

    private static func isScreenLikeAspect(record: VaultMediaRecord, image: UIImage) -> Bool {
        let pixelWidth = Double(record.width ?? Int(image.size.width * image.scale))
        let pixelHeight = Double(record.height ?? Int(image.size.height * image.scale))
        let longEdge = max(pixelWidth, pixelHeight)
        let shortEdge = max(1, min(pixelWidth, pixelHeight))
        let ratio = longEdge / shortEdge
        guard longEdge >= 1100, shortEdge >= 640 else { return false }
        return (1.72...2.35).contains(ratio)
    }

    private static func addLabelEvidence(_ label: VisionLabel, to scores: inout [String: Double]) {
        let tokens = tokens(in: label.identifier)
        let phrase = " \(label.identifier.replacingOccurrences(of: "_", with: " ")) "
        let confidence = label.confidence

        if containsAny(tokens, [
            "person", "people", "portrait", "selfie", "face",
            "man", "woman", "boy", "girl", "child", "baby", "human"
        ]) {
            scores[VaultAICategory.people, default: 0] += confidence * 1.25
        }
        if containsAny(tokens, ["smile", "skin", "hair"]) {
            scores[VaultAICategory.people, default: 0] += confidence * 0.35
        }
        if containsAny(tokens, [
            "food", "dish", "meal", "cuisine", "fruit", "dessert", "drink",
            "beverage", "cake", "bread", "meat", "vegetable", "coffee", "tea",
            "restaurant", "pizza", "noodle", "sushi", "salad"
        ]) {
            scores[VaultAICategory.food, default: 0] += confidence * 1.25
        }
        if containsAny(tokens, [
            "landscape", "sky", "cloud", "mountain", "tree", "beach", "sea",
            "ocean", "sunset", "sunrise", "plant", "flower", "forest", "river",
            "lake", "snow", "water", "grass", "nature", "outdoor",
            "animal", "mammal", "wildlife", "pet", "dog", "cat", "bird", "horse"
        ]) || phrase.contains(" natural landscape ") {
            scores[VaultAICategory.nature, default: 0] += confidence * 1.15
        }
        if containsAny(tokens, [
            "document", "paper", "text", "receipt", "book", "newspaper", "menu",
            "letter", "invoice", "form", "card", "handwriting", "note", "whiteboard"
        ]) || phrase.contains(" business card ") {
            scores[VaultAICategory.documents, default: 0] += confidence * 1.1
        }
        if containsAny(tokens, ["screenshot", "screen", "display", "website", "webpage"]) {
            scores[VaultAICategory.screenshots, default: 0] += confidence * 1.4
        }
    }

    private static func humanEvidenceScore(_ labels: [VisionLabel]) -> Double {
        labels.reduce(0) { total, label in
            let tokens = tokens(in: label.identifier)
            let weight: Double
            if containsAny(tokens, [
                "person", "people", "portrait", "selfie",
                "man", "woman", "boy", "girl", "child", "baby", "human"
            ]) {
                weight = 1.0
            } else if containsAny(tokens, ["smile", "skin", "hair"]) {
                weight = 0.3
            } else {
                weight = 0
            }
            return total + label.confidence * weight
        }
    }

    private static func nonHumanEvidenceScore(_ labels: [VisionLabel]) -> Double {
        labels.reduce(0) { total, label in
            let tokens = tokens(in: label.identifier)
            guard containsAny(tokens, [
                "animal", "mammal", "wildlife", "pet", "dog", "cat", "bird", "horse",
                "vehicle", "car", "automobile", "truck", "bus", "train", "airplane",
                "aircraft", "boat", "ship", "motorcycle", "bicycle", "wheel", "tire"
            ]) else {
                return total
            }
            return total + label.confidence
        }
    }

    private static func categoryPriority(_ category: String) -> Int {
        switch category {
        case VaultAICategory.documents: return 0
        case VaultAICategory.screenshots: return 1
        case VaultAICategory.people: return 2
        case VaultAICategory.food: return 3
        case VaultAICategory.nature: return 4
        default: return 5
        }
    }

    private static func containsAny(_ tokens: Set<String>, _ candidates: Set<String>) -> Bool {
        !tokens.isDisjoint(with: candidates)
    }

    private static func tokens(in text: String) -> Set<String> {
        Set(text.lowercased().split { !$0.isLetter }.map(String.init).filter { !$0.isEmpty })
    }

    static func categoryForTesting(
        labels: [(identifier: String, confidence: Double)],
        tags: Set<String> = [],
        hasProminentFace: Bool = false,
        hasProminentHuman: Bool = false,
        visualStats: (
            blueRatio: Double,
            greenRatio: Double,
            warmRatio: Double,
            skinToneRatio: Double,
            darkRatio: Double,
            whiteRatio: Double,
            saturationAverage: Double
        ) = (0, 0, 0, 0, 0, 0, 0)
    ) -> String {
        pickCategory(
            record: VaultMediaRecord(
                id: "test",
                storagePath: "test.jpg",
                albumName: "test",
                fileName: "test.jpg",
                mediaKind: .image,
                state: .active,
                encryptedSizeBytes: 0,
                originalSha256Hex: nil,
                encryptedSha256Hex: nil,
                originalFileName: nil,
                mimeType: nil,
                uti: nil,
                cipherVersion: nil,
                createdAtMs: 0,
                importedAtMs: 0,
                modifiedAtMs: 0,
                trashedAtMs: nil,
                width: nil,
                height: nil,
                durationMs: nil,
                source: .unknown,
                ai: .empty
            ),
            labels: labels.map { VisionLabel(identifier: $0.identifier, confidence: $0.confidence) },
            tags: tags,
            stats: VisualStats(
                blueRatio: visualStats.blueRatio,
                greenRatio: visualStats.greenRatio,
                warmRatio: visualStats.warmRatio,
                skinToneRatio: visualStats.skinToneRatio,
                darkRatio: visualStats.darkRatio,
                whiteRatio: visualStats.whiteRatio,
                saturationAverage: visualStats.saturationAverage
            ),
            hasProminentFace: hasProminentFace,
            hasProminentHuman: hasProminentHuman
        )
    }

    static func metadataHintCategoryForTesting(originalFileName: String) -> String? {
        metadataHints(
            for: VaultMediaRecord(
                id: "test",
                storagePath: "test.jpg",
                albumName: "test",
                fileName: "test.jpg",
                mediaKind: .image,
                state: .active,
                encryptedSizeBytes: 0,
                originalSha256Hex: nil,
                encryptedSha256Hex: nil,
                originalFileName: originalFileName,
                mimeType: nil,
                uti: nil,
                cipherVersion: nil,
                createdAtMs: 0,
                importedAtMs: 0,
                modifiedAtMs: 0,
                trashedAtMs: nil,
                width: nil,
                height: nil,
                durationMs: nil,
                source: .unknown,
                ai: .empty
            )
        ).category
    }

    private static func containsIDCardText(_ text: String) -> Bool {
        matches(text, pattern: #"\b\d{17}[\dXx]\b"#)
            || text.localizedCaseInsensitiveContains("身份证")
            || text.localizedCaseInsensitiveContains("passport")
    }

    private static func containsBankCardText(_ text: String) -> Bool {
        let digitCount = text.filter(\.isNumber).count
        return matches(text, pattern: #"\b(?:\d[ -]?){13,19}\b"#)
            || (digitCount >= 13 && digitCount <= 19 && text.localizedCaseInsensitiveContains("card"))
            || text.localizedCaseInsensitiveContains("bank")
            || text.localizedCaseInsensitiveContains("银行卡")
    }

    private static func containsContactText(_ text: String) -> Bool {
        matches(text, pattern: #"\b1[3-9]\d{9}\b"#)
            || matches(text, pattern: #"[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}"#, options: [.caseInsensitive])
    }

    private static func containsBarcodeText(_ text: String) -> Bool {
        text.localizedCaseInsensitiveContains("qr")
            || text.localizedCaseInsensitiveContains("barcode")
            || text.localizedCaseInsensitiveContains("boarding pass")
            || text.localizedCaseInsensitiveContains("code 39")
    }

    private static func matches(_ text: String, pattern: String, options: NSRegularExpression.Options = []) -> Bool {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: options) else { return false }
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        return regex.firstMatch(in: text, range: range) != nil
    }

    private static func grayscalePixels(cgImage: CGImage, width: Int, height: Int) -> [UInt8]? {
        var pixels = [UInt8](repeating: 0, count: width * height)
        let ok = pixels.withUnsafeMutableBytes { ptr -> Bool in
            guard let context = CGContext(
                data: ptr.baseAddress,
                width: width,
                height: height,
                bitsPerComponent: 8,
                bytesPerRow: width,
                space: CGColorSpaceCreateDeviceGray(),
                bitmapInfo: CGImageAlphaInfo.none.rawValue
            ) else { return false }
            context.interpolationQuality = .low
            context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        return ok ? pixels : nil
    }

    private static func rgbPixels(cgImage: CGImage, width: Int, height: Int) -> [(r: UInt8, g: UInt8, b: UInt8)] {
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let ok = pixels.withUnsafeMutableBytes { ptr -> Bool in
            guard let context = CGContext(
                data: ptr.baseAddress,
                width: width,
                height: height,
                bitsPerComponent: 8,
                bytesPerRow: width * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return false }
            context.interpolationQuality = .low
            context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        guard ok else { return [] }
        var result: [(r: UInt8, g: UInt8, b: UInt8)] = []
        result.reserveCapacity(width * height)
        for index in stride(from: 0, to: pixels.count, by: 4) {
            result.append((r: pixels[index], g: pixels[index + 1], b: pixels[index + 2]))
        }
        return result
    }

    private static func laplacianVariance(_ pixels: [UInt8], width: Int, height: Int) -> Double {
        guard pixels.count == width * height, width > 2, height > 2 else { return 0 }
        var values: [Double] = []
        values.reserveCapacity((width - 2) * (height - 2))
        for y in 1..<(height - 1) {
            for x in 1..<(width - 1) {
                let c = Int(pixels[y * width + x])
                let l = Int(pixels[y * width + x - 1])
                let r = Int(pixels[y * width + x + 1])
                let t = Int(pixels[(y - 1) * width + x])
                let b = Int(pixels[(y + 1) * width + x])
                values.append(Double(l + r + t + b - 4 * c))
            }
        }
        let mean = values.reduce(0, +) / Double(values.count)
        return values.reduce(0) { $0 + pow($1 - mean, 2) } / Double(values.count)
    }

    private static func differenceHash(cgImage: CGImage) -> UInt64? {
        guard let pixels = grayscalePixels(cgImage: cgImage, width: 9, height: 8), pixels.count == 72 else {
            return nil
        }
        var hash: UInt64 = 0
        for y in 0..<8 {
            for x in 0..<8 {
                hash <<= 1
                if pixels[y * 9 + x] > pixels[y * 9 + x + 1] {
                    hash |= 1
                }
            }
        }
        return hash
    }

    private static func rgbFingerprint(cgImage: CGImage) -> [UInt8] {
        let samples = rgbPixels(cgImage: cgImage, width: 16, height: 16)
        var result: [UInt8] = []
        result.reserveCapacity(samples.count * 3)
        for sample in samples {
            result.append(sample.r)
            result.append(sample.g)
            result.append(sample.b)
        }
        return result
    }
}
