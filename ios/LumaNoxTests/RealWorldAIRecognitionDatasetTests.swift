import Foundation
@testable import LumaNox
import XCTest

@MainActor
final class RealWorldAIRecognitionDatasetTests: XCTestCase {
    func testImportRealWorldDatasetAndReportRecognition() async throws {
        let manifestPath = ProcessInfo.processInfo.environment["LUMANOX_REAL_AI_DATASET_MANIFEST"]
            ?? defaultManifestPath()
        guard let manifestPath, FileManager.default.fileExists(atPath: manifestPath) else {
            throw XCTSkip("Set LUMANOX_REAL_AI_DATASET_MANIFEST to run the real-world AI dataset regression.")
        }

        let manifestURL = URL(fileURLWithPath: manifestPath)
        let manifest = try JSONDecoder().decode(RealWorldAIManifest.self, from: Data(contentsOf: manifestURL))
        XCTAssertFalse(manifest.samples.isEmpty)

        let albumName = "AI_RealWorld_\(Int(Date().timeIntervalSince1970))"
        var importSummary = VaultImportSummary()
        for sample in manifest.samples {
            let url = URL(fileURLWithPath: sample.path)
            let data = try Data(contentsOf: url)
            let ext = url.pathExtension.isEmpty ? "jpg" : url.pathExtension
            let result = await VaultStore.shared.importPlainData(
                data,
                fileExtension: ext,
                albumName: albumName,
                originalFileName: sample.fileName
            )
            switch result {
            case .added: importSummary.added += 1
            case .duplicate: importSummary.duplicate += 1
            case .failed: importSummary.failed += 1
            }
        }

        await VaultStore.shared.loadSnapshot(recentLimit: manifest.samples.count + 20)
        XCTAssertEqual(importSummary.failed, 0)
        XCTAssertEqual(importSummary.added + importSummary.duplicate, manifest.samples.count)

        await VaultAIAnalysisService.shared.scanVault()
        let records = VaultAIAnalysisService.shared.records.filter { $0.albumName == albumName }
        XCTAssertEqual(records.count, importSummary.added)

        let expectedByName = Dictionary(uniqueKeysWithValues: manifest.samples.map { ($0.fileName, $0) })
        var buckets: [String: RealWorldAIBucket] = [:]
        var categoryHits = 0
        var categoryExpected = 0
        var sensitiveHits = 0
        var sensitiveExpected = 0
        var tagHits = 0
        var tagExpected = 0

        for record in records {
            guard let expected = expectedByName[record.originalFileName ?? ""] else { continue }
            buckets[expected.kind, default: RealWorldAIBucket()].observe(record)
            if let expectedCategory = expected.expectedCategory {
                categoryExpected += 1
                if record.ai.category == expectedCategory { categoryHits += 1 }
            }
            if expected.expectedSensitive == true {
                sensitiveExpected += 1
                if VaultAIAnalysisService.isSensitive(record) { sensitiveHits += 1 }
            }
            for expectedTag in expected.expectedTags {
                tagExpected += 1
                if record.ai.tags.contains(expectedTag) { tagHits += 1 }
            }
        }

        let scanned = records.filter { $0.ai.scannedAtMs != nil }.count
        let sensitive = records.filter { VaultAIAnalysisService.isSensitive($0) }.count
        let cleanup = records.filter { VaultAIAnalysisService.isCleanable($0) }.count
        let categories = Dictionary(grouping: records, by: { $0.ai.category ?? "nil" }).mapValues(\.count)
        let tags = records.flatMap(\.ai.tags).reduce(into: [String: Int]()) { $0[$1, default: 0] += 1 }

        print("AI_REAL_EVAL_IMPORTED album=\(albumName) added=\(importSummary.added) duplicate=\(importSummary.duplicate) failed=\(importSummary.failed)")
        print("AI_REAL_EVAL_TOTAL scanned=\(scanned)/\(records.count) sensitive=\(sensitive) cleanup=\(cleanup)")
        print("AI_REAL_EVAL_MATCH category=\(categoryHits)/\(categoryExpected) sensitive=\(sensitiveHits)/\(sensitiveExpected) tags=\(tagHits)/\(tagExpected)")
        print("AI_REAL_EVAL_CATEGORIES \(formatCounts(categories))")
        print("AI_REAL_EVAL_TAGS \(formatCounts(tags))")
        for key in buckets.keys.sorted() {
            print("AI_REAL_EVAL_BUCKET \(key) \(buckets[key]?.summary ?? "")")
        }

        XCTAssertEqual(scanned, records.count)
    }

    private func formatCounts(_ counts: [String: Int]) -> String {
        counts.sorted { $0.key < $1.key }
            .map { "\($0.key)=\($0.value)" }
            .joined(separator: ",")
    }

    private func defaultManifestPath() -> String? {
        var candidates: [URL] = []
        let cwd = URL(fileURLWithPath: FileManager.default.currentDirectoryPath, isDirectory: true)
        var cwdCursor = cwd
        for _ in 0..<8 {
            candidates.append(cwdCursor.appendingPathComponent(".tmp/real-ai-dataset/manifest.json"))
            cwdCursor.deleteLastPathComponent()
        }

        var sourceCursor = URL(fileURLWithPath: #filePath)
        sourceCursor.deleteLastPathComponent()
        for _ in 0..<8 {
            candidates.append(sourceCursor.appendingPathComponent(".tmp/real-ai-dataset/manifest.json"))
            sourceCursor.deleteLastPathComponent()
        }

        return candidates.first { FileManager.default.fileExists(atPath: $0.path) }?.path
    }
}

private struct RealWorldAIManifest: Decodable {
    let samples: [RealWorldAISample]
}

private struct RealWorldAISample: Decodable {
    let kind: String
    let fileName: String
    let path: String
    let expectedCategory: String?
    let expectedSensitive: Bool?
    let expectedTags: [String]
}

private struct RealWorldAIBucket {
    var total = 0
    var sensitive = 0
    var cleanup = 0
    var categories: [String: Int] = [:]
    var tags: [String: Int] = [:]

    mutating func observe(_ record: VaultMediaRecord) {
        total += 1
        if VaultAIAnalysisService.isSensitive(record) { sensitive += 1 }
        if VaultAIAnalysisService.isCleanable(record) { cleanup += 1 }
        categories[record.ai.category ?? "nil", default: 0] += 1
        record.ai.tags.forEach { tags[$0, default: 0] += 1 }
    }

    var summary: String {
        let categoryText = categories.sorted { $0.key < $1.key }.map { "\($0.key)=\($0.value)" }.joined(separator: ",")
        let tagText = tags.sorted { $0.key < $1.key }.map { "\($0.key)=\($0.value)" }.joined(separator: ",")
        return "total=\(total) sensitive=\(sensitive) cleanup=\(cleanup) categories=[\(categoryText)] tags=[\(tagText)]"
    }
}
