import Foundation
import AVFoundation
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

func vaultAINowMs() -> Int64 {
    Int64(Date().timeIntervalSince1970 * 1000)
}

@MainActor
final class VaultAIAnalysisService: ObservableObject {
    static let shared = VaultAIAnalysisService()
    nonisolated static let currentAnalyzerVersion = 4

    @Published private(set) var records: [VaultMediaRecord] = []
    @Published private(set) var sensitiveRecords: [VaultMediaRecord] = []
    @Published private(set) var summary = VaultAISummary()
    @Published private(set) var progress = VaultAIProgress()
    @Published private(set) var lastError: String?

    private let vaultStore = VaultStore.shared
    private let metadataStore = VaultMetadataStore.shared
    private let indexStore = VaultAIIndexStore.shared
    private let fileManager = FileManager.default

    private init() {}

    func refreshSummary() {
        let cachedRecords = metadataStore.activeMediaRecords()
        publishRecords(cachedRecords)
        lastError = nil
    }

    func reconcileSummary() {
        do {
            let metadata = try metadataStore.reconcile(
                vaultRoot: try vaultStore.rootDirectory(),
                trashRoot: try vaultStore.trashDirectory()
            )
            publishRecords(metadata.activeMedia.sorted { $0.modifiedAtMs > $1.modifiedAtMs })
            lastError = nil
        } catch {
            publishRecords(metadataStore.activeMediaRecords())
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
        let previousIndexByID = Dictionary(uniqueKeysWithValues: indexStore.load().records.map { ($0.recordID, $0) })
        var results: [VaultAIAnalyzer.Result] = []
        results.reserveCapacity(targets.count)
        var completed = 0
        let progressStride = max(1, min(25, targets.count / 50))

        for record in targets {
            do {
                var result: VaultAIAnalyzer.Result
                if Self.canReuseAnalysis(for: record) {
                    result = VaultAIAnalyzer.reuse(record: record, indexRecord: previousIndexByID[record.id])
                } else {
                    result = try await VaultAIAnalyzer.analyze(
                        record: record,
                        documentsDirectory: documents
                    )
                }
                result.ai.sensitiveIgnoredAtMs = record.ai.sensitiveIgnoredAtMs
                results.append(result)
            } catch {
                var ai = record.ai
                ai.scannedAtMs = Self.nowMs()
                ai.analyzerVersion = Self.currentAnalyzerVersion
                ai.sourceFingerprint = VaultAIAnalyzer.sourceFingerprint(for: record)
                ai.category = record.mediaKind == .video ? VaultAICategory.videos : VaultAICategory.other
                ai.tags = Array(Set(ai.tags + (record.mediaKind == .video ? [VaultAITag.video] : []))).sorted()
                results.append(VaultAIAnalyzer.Result(
                    recordID: record.id,
                    mediaKind: record.mediaKind,
                    ai: ai,
                    dHash: nil,
                    rgbFingerprint: nil,
                    featurePrint: nil,
                    featurePrintBase64: nil,
                    subjectKind: nil
                ))
            }
            completed += 1
            if completed == targets.count || completed % progressStride == 0 {
                progress.done = completed
                await Task.yield()
            }
        }

        markDuplicates(in: &results)
        let indexPayload = makeAIIndexPayload(from: results)

        do {
            try metadataStore.updateAiMetadata(Dictionary(uniqueKeysWithValues: results.map { ($0.recordID, $0.ai) }))
            try indexStore.replace(records: indexPayload.records, subjectClusters: indexPayload.clusters)
            refreshSummary()
        } catch {
            lastError = error.localizedDescription
        }
        progress = VaultAIProgress()
    }

    nonisolated static func isSensitive(_ record: VaultMediaRecord) -> Bool {
        guard record.ai.sensitiveIgnoredAtMs == nil else { return false }
        return (record.ai.sensitiveScore ?? 0) >= 0.45
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

    func ignoreSensitiveCandidate(recordID: String) {
        guard let index = records.firstIndex(where: { $0.id == recordID }) else { return }
        var record = records[index]
        record.ai.sensitiveIgnoredAtMs = Self.nowMs()
        do {
            try metadataStore.updateAiMetadata([record.id: record.ai])
            records[index] = record
            sensitiveRecords.removeAll { $0.id == recordID }
            summary = Self.makeSummary(from: records)
        } catch {
            lastError = error.localizedDescription
        }
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

    private func publishRecords(_ nextRecords: [VaultMediaRecord]) {
        records = nextRecords
        sensitiveRecords = Self.makeSensitiveRecords(from: nextRecords)
        summary = Self.makeSummary(from: nextRecords)
    }

    private static func makeSensitiveRecords(from records: [VaultMediaRecord]) -> [VaultMediaRecord] {
        records
            .filter { isSensitive($0) }
            .sorted {
                let leftScore = $0.ai.sensitiveScore ?? 0
                let rightScore = $1.ai.sensitiveScore ?? 0
                if leftScore == rightScore {
                    return $0.modifiedAtMs > $1.modifiedAtMs
                }
                return leftScore > rightScore
            }
    }

    private func markDuplicates(in results: inout [VaultAIAnalyzer.Result]) {
        for index in results.indices {
            guard results[index].ai.tags.contains(VaultAITag.duplicate) else { continue }
            var ai = results[index].ai
            ai.tags.removeAll { $0 == VaultAITag.duplicate }
            ai.duplicateGroupId = nil
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

        var parent = Array(hashes.indices)
        func find(_ index: Int) -> Int {
            var current = index
            while parent[current] != current {
                current = parent[current]
            }
            return current
        }
        func union(_ left: Int, _ right: Int) {
            let leftRoot = find(left)
            let rightRoot = find(right)
            guard leftRoot != rightRoot else { return }
            parent[rightRoot] = leftRoot
        }

        var seenByHash: [UInt64: [Int]] = [:]
        for current in hashes.indices {
            let currentResult = results[hashes[current].0]
            guard isDuplicateEligible(currentResult.ai) else {
                seenByHash[hashes[current].1, default: []].append(current)
                continue
            }

            forEachNearbyDHash(hashes[current].1) { candidateHash in
                guard let previousIndexes = seenByHash[candidateHash] else { return }
                for previous in previousIndexes {
                    let previousResult = results[hashes[previous].0]
                    guard isDuplicateEligible(previousResult.ai) else { continue }
                    if rgbDistance(hashes[current].2, hashes[previous].2) <= 5.0 {
                        union(previous, current)
                    }
                }
            }
            seenByHash[hashes[current].1, default: []].append(current)
        }

        var groups: [Int: [Int]] = [:]
        for hashIndex in hashes.indices {
            groups[find(hashIndex), default: []].append(hashes[hashIndex].0)
        }

        for groupIndexes in groups.values where groupIndexes.count > 1 {
            let sortedGroup = groupIndexes.sorted { results[$0].recordID < results[$1].recordID }
            let keepIndex = sortedGroup[0]
            let groupID = "dup_\(stableGroupHash(sortedGroup.map { results[$0].recordID }))"
            for index in sortedGroup {
                var ai = results[index].ai
                ai.duplicateGroupId = groupID
                if index == keepIndex {
                    ai.tags.removeAll { $0 == VaultAITag.duplicate }
                    if !Set(ai.tags).contains(VaultAITag.blurry), !Set(ai.tags).contains(VaultAITag.overexposed), (ai.cleanupScore ?? 0) <= 0.82 {
                        ai.cleanupScore = 0
                    }
                } else {
                    ai.cleanupScore = max(ai.cleanupScore ?? 0, 0.82)
                    ai.tags = Array(Set(ai.tags + [VaultAITag.duplicate])).sorted()
                }
                results[index].ai = ai
            }
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

    private func stableGroupHash(_ ids: [String]) -> String {
        var hash: UInt64 = 0xcbf29ce484222325
        for byte in ids.sorted().joined(separator: "|").utf8 {
            hash ^= UInt64(byte)
            hash &*= 0x100000001b3
        }
        return String(format: "%016llx", hash)
    }

    private func forEachNearbyDHash(_ hash: UInt64, _ visit: (UInt64) -> Void) {
        visit(hash)
        for firstBit in 0..<64 {
            let firstMask = UInt64(1) << UInt64(firstBit)
            let oneBitHash = hash ^ firstMask
            visit(oneBitHash)
            for secondBit in (firstBit + 1)..<64 {
                visit(oneBitHash ^ (UInt64(1) << UInt64(secondBit)))
            }
        }
    }

    private func makeAIIndexPayload(from results: [VaultAIAnalyzer.Result]) -> (
        records: [VaultAIIndexRecord],
        clusters: [VaultAISubjectCluster]
    ) {
        var clusterIDsByRecordID: [String: String] = [:]
        let subjectEntries = results.enumerated().compactMap { index, result -> (Int, VaultAISubjectKind, VNFeaturePrintObservation)? in
            guard let kind = result.subjectKind,
                  let featurePrint = result.featurePrint else { return nil }
            return (index, kind, featurePrint)
        }

        for kind in [VaultAISubjectKind.people, .pet] {
            let entries = subjectEntries.filter { $0.1 == kind }
            guard !entries.isEmpty else { continue }
            var parent = Array(entries.indices)
            func find(_ index: Int) -> Int {
                var current = index
                while parent[current] != current {
                    current = parent[current]
                }
                return current
            }
            func union(_ left: Int, _ right: Int) {
                let leftRoot = find(left)
                let rightRoot = find(right)
                guard leftRoot != rightRoot else { return }
                parent[rightRoot] = leftRoot
            }

            for left in entries.indices {
                for right in entries.indices where right > left {
                    var distance: Float = .greatestFiniteMagnitude
                    try? entries[left].2.computeDistance(&distance, to: entries[right].2)
                    let threshold: Float = kind == .people ? 0.58 : 0.62
                    if distance <= threshold {
                        union(left, right)
                    }
                }
            }

            var groups: [Int: [Int]] = [:]
            for entryIndex in entries.indices {
                groups[find(entryIndex), default: []].append(entries[entryIndex].0)
            }
            for groupIndexes in groups.values {
                let memberIDs = groupIndexes.map { results[$0].recordID }.sorted()
                guard !memberIDs.isEmpty else { continue }
                let clusterID = "subject_\(kind.rawValue)_\(stableGroupHash(memberIDs))"
                for memberID in memberIDs {
                    clusterIDsByRecordID[memberID] = clusterID
                }
            }
        }

        let now = Self.nowMs()
        let clusters = Dictionary(grouping: clusterIDsByRecordID.keys) { recordID in
            clusterIDsByRecordID[recordID] ?? ""
        }
        .compactMap { clusterID, recordIDs -> VaultAISubjectCluster? in
            guard let firstID = recordIDs.sorted().first,
                  let result = results.first(where: { $0.recordID == firstID }),
                  let kind = result.subjectKind else { return nil }
            let members = recordIDs.sorted()
            return VaultAISubjectCluster(
                id: clusterID,
                kind: kind,
                name: nil,
                memberRecordIDs: members,
                representativeRecordID: members.first,
                createdAtMs: now,
                updatedAtMs: now
            )
        }
        .sorted { $0.id < $1.id }

        let records = results.map { result in
            VaultAIIndexRecord(
                recordID: result.recordID,
                sourceFingerprint: result.ai.sourceFingerprint ?? "",
                mediaKind: result.mediaKind,
                category: result.ai.category,
                tags: result.ai.tags,
                labels: result.ai.labels,
                sensitiveScore: result.ai.sensitiveScore,
                cleanupScore: result.ai.cleanupScore,
                duplicateGroupId: result.ai.duplicateGroupId,
                featurePrintBase64: result.featurePrintBase64,
                subjectKind: result.subjectKind,
                subjectClusterId: clusterIDsByRecordID[result.recordID]
            )
        }
        return (records, clusters)
    }

    private func documentsDirectory() -> URL {
        fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }
}

enum VaultAIAnalyzer {
    struct Result {
        let recordID: String
        let mediaKind: VaultMediaKind
        var ai: VaultAiMetadata
        let dHash: UInt64?
        let rgbFingerprint: [UInt8]?
        let featurePrint: VNFeaturePrintObservation?
        let featurePrintBase64: String?
        let subjectKind: VaultAISubjectKind?
    }

    static func reuse(record: VaultMediaRecord, indexRecord: VaultAIIndexRecord?) -> Result {
        Result(
            recordID: record.id,
            mediaKind: record.mediaKind,
            ai: record.ai,
            dHash: uint64(fromHex: record.ai.perceptualHashHex),
            rgbFingerprint: bytes(fromHex: record.ai.colorFingerprintHex),
            featurePrint: featurePrint(fromBase64: indexRecord?.featurePrintBase64),
            featurePrintBase64: indexRecord?.featurePrintBase64,
            subjectKind: indexRecord?.subjectKind
        )
    }

    static func analyze(record: VaultMediaRecord, documentsDirectory: URL) async throws -> Result {
        try await Task.detached(priority: .utility) {
            if record.mediaKind == .video {
                return try await analyzeVideo(record: record, documentsDirectory: documentsDirectory)
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
                return Result(
                    recordID: record.id,
                    mediaKind: record.mediaKind,
                    ai: ai,
                    dHash: nil,
                    rgbFingerprint: nil,
                    featurePrint: nil,
                    featurePrintBase64: nil,
                    subjectKind: nil
                )
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

            let category = hints.category ?? VaultAICategoryMapper.pickCategory(
                labels: vision.labels,
                tags: tags,
                stats: visualStats,
                hasProminentFace: vision.hasProminentFace,
                hasProminentHuman: vision.hasProminentHuman
            )
            let dHash = quality.dHash
            let colorFingerprint = rgbFingerprint(cgImage: cgImage)
            let featurePrint = featurePrint(cgImage: cgImage)
            let subjectKind = inferSubjectKind(
                category: category,
                labels: vision.labels,
                tags: tags,
                hasProminentFace: vision.hasProminentFace,
                hasProminentHuman: vision.hasProminentHuman
            )
            let ai = VaultAiMetadata(
                scannedAtMs: vaultAINowMs(),
                sensitiveScore: max(
                    vision.sensitiveScore,
                    hints.sensitiveScore,
                    subjectKind == .people ? 0.55 : 0
                ),
                cleanupScore: cleanupScore,
                category: category,
                tags: Array(tags).sorted(),
                analyzerVersion: VaultAIAnalysisService.currentAnalyzerVersion,
                sourceFingerprint: sourceFingerprint(for: record),
                labels: vision.labels.map { VaultAiLabelObservation(identifier: $0.identifier, confidence: $0.confidence) },
                perceptualHashHex: dHash.map(hexString),
                colorFingerprintHex: hexString(colorFingerprint)
            )
            return Result(
                recordID: record.id,
                mediaKind: record.mediaKind,
                ai: ai,
                dHash: dHash,
                rgbFingerprint: colorFingerprint,
                featurePrint: featurePrint,
                featurePrintBase64: archiveFeaturePrint(featurePrint),
                subjectKind: subjectKind
            )
        }.value
    }

    private static func analyzeVideo(record: VaultMediaRecord, documentsDirectory: URL) async throws -> Result {
        let encryptedURL = record.absoluteURL(documentsDirectory: documentsDirectory)
        let cacheDirectory = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("LumaNox/ai_video_frames", isDirectory: true)
        let safeName = record.id.map { $0.isLetter || $0.isNumber ? $0 : "_" }.map(String.init).joined()
        let tempURL = try VaultCipher.shared.decryptToTempFile(
            sourceURL: encryptedURL,
            cacheDirectory: cacheDirectory,
            fileName: "\(safeName)_\(UUID().uuidString).\(record.storagePath.fileExtensionFallback("mov"))"
        )
        defer { try? FileManager.default.removeItem(at: tempURL) }

        let asset = AVAsset(url: tempURL)
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: 1280, height: 1280)

        let duration = (try? await asset.load(.duration)) ?? .zero
        let durationSeconds = CMTimeGetSeconds(duration)
        let probeSeconds: [Double]
        if durationSeconds.isFinite, durationSeconds > 1 {
            probeSeconds = Array(Set([0.35, min(durationSeconds * 0.5, max(0.35, durationSeconds - 0.35)), max(0.35, durationSeconds - 0.35)])).sorted()
        } else {
            probeSeconds = [0.0]
        }

        var combinedTags = Set([VaultAITag.video])
        var labelByID: [String: VaultAIVisionLabel] = [:]
        var sensitiveScore: Double = 0
        var categoryScores: [String: Double] = [:]
        var selectedFeaturePrint: VNFeaturePrintObservation?
        var selectedSubjectKind: VaultAISubjectKind?

        for second in probeSeconds {
            let time = CMTime(seconds: second, preferredTimescale: 600)
            guard let cgImage = try? generator.copyCGImage(at: time, actualTime: nil) else { continue }
            let vision = analyzeVision(cgImage: cgImage, record: record)
            let stats = analyzeVisualStats(cgImage: cgImage)
            let tags = Set(vision.tags).union([VaultAITag.video])
            let category = VaultAICategoryMapper.pickCategory(
                labels: vision.labels,
                tags: tags,
                stats: stats,
                hasProminentFace: vision.hasProminentFace,
                hasProminentHuman: vision.hasProminentHuman
            )
            combinedTags.formUnion(tags)
            sensitiveScore = max(sensitiveScore, vision.sensitiveScore)
            for label in vision.labels {
                let existing = labelByID[label.identifier]
                if existing == nil || label.confidence > (existing?.confidence ?? 0) {
                    labelByID[label.identifier] = label
                }
            }
            if category != VaultAICategory.other {
                categoryScores[category, default: 0] += vision.labels.map(\.confidence).max() ?? 0.55
            }
            if selectedFeaturePrint == nil {
                selectedFeaturePrint = featurePrint(cgImage: cgImage)
            }
            if selectedSubjectKind == nil {
                selectedSubjectKind = inferSubjectKind(
                    category: category,
                    labels: vision.labels,
                    tags: tags,
                    hasProminentFace: vision.hasProminentFace,
                    hasProminentHuman: vision.hasProminentHuman
                )
            }
        }

        let labels = labelByID.values.sorted { left, right in
            if left.confidence == right.confidence { return left.identifier < right.identifier }
            return left.confidence > right.confidence
        }
        .prefix(8)
        .map { VaultAiLabelObservation(identifier: $0.identifier, confidence: $0.confidence) }
        let category = categoryScores.sorted { left, right in
            if left.value == right.value { return left.key < right.key }
            return left.value > right.value
        }.first?.key ?? VaultAICategory.videos

        let ai = VaultAiMetadata(
            scannedAtMs: vaultAINowMs(),
            sensitiveScore: max(sensitiveScore, selectedSubjectKind == .people ? 0.55 : 0),
            cleanupScore: 0,
            category: category,
            tags: Array(combinedTags).sorted(),
            analyzerVersion: VaultAIAnalysisService.currentAnalyzerVersion,
            sourceFingerprint: sourceFingerprint(for: record),
            labels: labels
        )
        return Result(
            recordID: record.id,
            mediaKind: record.mediaKind,
            ai: ai,
            dHash: nil,
            rgbFingerprint: nil,
            featurePrint: selectedFeaturePrint,
            featurePrintBase64: archiveFeaturePrint(selectedFeaturePrint),
            subjectKind: selectedSubjectKind
        )
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

    private static func archiveFeaturePrint(_ observation: VNFeaturePrintObservation?) -> String? {
        guard let observation,
              let data = try? NSKeyedArchiver.archivedData(
                withRootObject: observation,
                requiringSecureCoding: true
              ) else { return nil }
        return data.base64EncodedString()
    }

    private static func featurePrint(fromBase64 value: String?) -> VNFeaturePrintObservation? {
        guard let value,
              let data = Data(base64Encoded: value) else { return nil }
        return try? NSKeyedUnarchiver.unarchivedObject(ofClass: VNFeaturePrintObservation.self, from: data)
    }

    private static func featurePrint(cgImage: CGImage) -> VNFeaturePrintObservation? {
        let request = VNGenerateImageFeaturePrintRequest()
        let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
        try? handler.perform([request])
        return request.results?.first as? VNFeaturePrintObservation
    }

    private struct QualityResult {
        let isBlurry: Bool
        let isOverexposed: Bool
        let dHash: UInt64?
    }

    private struct VisionResult {
        let sensitiveScore: Double
        let labels: [VaultAIVisionLabel]
        let tags: [String]
        let hasProminentFace: Bool
        let hasProminentHuman: Bool
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
            .map { VaultAIVisionLabel(identifier: $0.identifier.lowercased(), confidence: Double($0.confidence)) }

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

    private static func analyzeVisualStats(cgImage: CGImage) -> VaultAIVisualStats {
        let samples = rgbPixels(cgImage: cgImage, width: 64, height: 64)
        guard !samples.isEmpty else {
            return VaultAIVisualStats(
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
        return VaultAIVisualStats(
            blueRatio: Double(blue) / total,
            greenRatio: Double(green) / total,
            warmRatio: Double(warm) / total,
            skinToneRatio: Double(skin) / total,
            darkRatio: Double(dark) / total,
            whiteRatio: Double(white) / total,
            saturationAverage: saturationTotal / total
        )
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

    private static func inferSubjectKind(
        category: String,
        labels: [VaultAIVisionLabel],
        tags: Set<String>,
        hasProminentFace: Bool,
        hasProminentHuman: Bool
    ) -> VaultAISubjectKind? {
        let petScore = labels.reduce(0.0) { partial, label in
            let tokens = tokens(in: label.identifier)
            guard containsAny(tokens, ["pet", "dog", "cat", "puppy", "kitten", "animal", "mammal"]) else {
                return partial
            }
            return partial + label.confidence
        }
        if petScore >= 0.45 {
            return .pet
        }
        if category == VaultAICategory.people
            || hasProminentHuman
            || (hasProminentFace && petScore < 0.25)
            || (tags.contains(VaultAITag.face) && petScore < 0.25) {
            return .people
        }
        return nil
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
        VaultAICategoryMapper.pickCategory(
            labels: labels.map { VaultAIVisionLabel(identifier: $0.identifier, confidence: $0.confidence) },
            tags: tags,
            stats: VaultAIVisualStats(
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

private extension String {
    func fileExtensionFallback(_ fallback: String) -> String {
        let ext = (self as NSString).pathExtension
        return ext.isEmpty ? fallback : ext
    }
}
