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
                let result = try await VaultAIAnalyzer.analyze(
                    record: record,
                    documentsDirectory: documents
                )
                results.append(result)
            } catch {
                var ai = record.ai
                ai.scannedAtMs = Self.nowMs()
                ai.category = record.mediaKind == .video ? VaultAICategory.videos : VaultAICategory.other
                ai.tags = Array(Set(ai.tags + (record.mediaKind == .video ? [VaultAITag.video] : []))).sorted()
                results.append(VaultAIAnalyzer.Result(recordID: record.id, ai: ai, dHash: nil))
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
        let hashes = results.enumerated().compactMap { index, result -> (Int, UInt64)? in
            guard let hash = result.dHash else { return nil }
            return (index, hash)
        }
        guard hashes.count > 1 else { return }

        var duplicateIndexes = Set<Int>()
        for left in 0..<hashes.count {
            for right in (left + 1)..<hashes.count {
                if (hashes[left].1 ^ hashes[right].1).nonzeroBitCount <= 8 {
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

    private func documentsDirectory() -> URL {
        fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }
}

enum VaultAIAnalyzer {
    struct Result {
        let recordID: String
        var ai: VaultAiMetadata
        let dHash: UInt64?
    }

    static func analyze(record: VaultMediaRecord, documentsDirectory: URL) async throws -> Result {
        try await Task.detached(priority: .utility) {
            if record.mediaKind == .video {
                let ai = VaultAiMetadata(
                    scannedAtMs: vaultAINowMs(),
                    sensitiveScore: 0,
                    cleanupScore: 0,
                    category: VaultAICategory.videos,
                    tags: [VaultAITag.video]
                )
                return Result(recordID: record.id, ai: ai, dHash: nil)
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
                    tags: []
                )
                return Result(recordID: record.id, ai: ai, dHash: nil)
            }

            let quality = analyzeQuality(cgImage: cgImage)
            let vision = analyzeVision(cgImage: cgImage, record: record)
            var tags = Set(vision.tags)
            var cleanupScore: Double = 0

            if quality.isBlurry {
                tags.insert(VaultAITag.blurry)
                cleanupScore = max(cleanupScore, 0.76)
            }
            if quality.isOverexposed {
                tags.insert(VaultAITag.overexposed)
                cleanupScore = max(cleanupScore, 0.58)
            }
            if isLikelyScreenshot(record: record, image: image, visionTags: tags) {
                tags.insert(VaultAITag.screenshot)
            }

            let category = pickCategory(record: record, labels: vision.labels, tags: tags)
            let ai = VaultAiMetadata(
                scannedAtMs: vaultAINowMs(),
                sensitiveScore: vision.sensitiveScore,
                cleanupScore: cleanupScore,
                category: category,
                tags: Array(tags).sorted()
            )
            return Result(recordID: record.id, ai: ai, dHash: quality.dHash)
        }.value
    }

    private struct QualityResult {
        let isBlurry: Bool
        let isOverexposed: Bool
        let dHash: UInt64?
    }

    private struct VisionResult {
        let sensitiveScore: Double
        let labels: [String]
        let tags: [String]
    }

    private static func analyzeVision(cgImage: CGImage, record: VaultMediaRecord) -> VisionResult {
        let faceRequest = VNDetectFaceRectanglesRequest()
        let barcodeRequest = VNDetectBarcodesRequest()
        let textRequest = VNRecognizeTextRequest()
        textRequest.recognitionLevel = .fast
        textRequest.usesLanguageCorrection = false
        let classifyRequest = VNClassifyImageRequest()

        let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
        try? handler.perform([faceRequest, barcodeRequest, textRequest, classifyRequest])

        let faceCount = faceRequest.results?.count ?? 0
        let barcodeCount = barcodeRequest.results?.count ?? 0
        let recognizedText = (textRequest.results ?? [])
            .compactMap { $0.topCandidates(1).first?.string }
            .joined(separator: " ")
        let labels = (classifyRequest.results ?? [])
            .prefix(5)
            .filter { $0.confidence >= 0.18 }
            .map { $0.identifier.lowercased() }

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

        return VisionResult(sensitiveScore: score, labels: labels, tags: Array(tags))
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

    private static func pickCategory(record: VaultMediaRecord, labels: [String], tags: Set<String>) -> String {
        let joined = labels.joined(separator: " ")
        if tags.contains(VaultAITag.screenshot) { return VaultAICategory.screenshots }
        if tags.contains(VaultAITag.face) || joined.contains("person") || joined.contains("people") || joined.contains("portrait") {
            return VaultAICategory.people
        }
        if tags.contains(VaultAITag.text) || joined.contains("document") || joined.contains("paper") || joined.contains("receipt") {
            return VaultAICategory.documents
        }
        if joined.contains("food") || joined.contains("dish") || joined.contains("meal") || joined.contains("drink") {
            return VaultAICategory.food
        }
        if joined.contains("landscape") || joined.contains("sky") || joined.contains("mountain") || joined.contains("water") || joined.contains("plant") {
            return VaultAICategory.nature
        }
        return VaultAICategory.other
    }

    private static func isLikelyScreenshot(record: VaultMediaRecord, image: UIImage, visionTags: Set<String>) -> Bool {
        if isLikelyScreenshotName(record) { return true }
        let scale = max(image.size.width, image.size.height) / max(1, min(image.size.width, image.size.height))
        return visionTags.contains(VaultAITag.text) && scale > 1.7
    }

    private static func isLikelyScreenshotName(_ record: VaultMediaRecord) -> Bool {
        let name = (record.originalFileName ?? record.fileName).lowercased()
        return name.contains("screenshot") || name.contains("screen_shot") || name.contains("截屏") || name.contains("截图")
    }

    private static func containsIDCardText(_ text: String) -> Bool {
        matches(text, pattern: #"\b\d{17}[\dXx]\b"#)
            || text.localizedCaseInsensitiveContains("身份证")
            || text.localizedCaseInsensitiveContains("passport")
    }

    private static func containsBankCardText(_ text: String) -> Bool {
        matches(text, pattern: #"\b(?:\d[ -]?){13,19}\b"#)
            || text.localizedCaseInsensitiveContains("bank")
            || text.localizedCaseInsensitiveContains("银行卡")
    }

    private static func containsContactText(_ text: String) -> Bool {
        matches(text, pattern: #"\b1[3-9]\d{9}\b"#)
            || matches(text, pattern: #"[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}"#, options: [.caseInsensitive])
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
}
