@testable import LumaNox
import XCTest

final class AIClassificationRuleTests: XCTestCase {
    func testVaultAiMetadataDecodesLegacyPayloadWithoutOptimizationFields() throws {
        let data = Data("""
        {
          "scannedAtMs": 1710000000000,
          "sensitiveScore": 0.66,
          "cleanupScore": 0.0,
          "category": "people",
          "tags": ["face"]
        }
        """.utf8)

        let metadata = try JSONDecoder().decode(VaultAiMetadata.self, from: data)

        XCTAssertEqual(metadata.category, VaultAICategory.people)
        XCTAssertEqual(metadata.tags, [VaultAITag.face])
        XCTAssertNil(metadata.analyzerVersion)
        XCTAssertNil(metadata.sourceFingerprint)
        XCTAssertTrue(metadata.labels.isEmpty)
        XCTAssertNil(metadata.perceptualHashHex)
        XCTAssertNil(metadata.colorFingerprintHex)
        XCTAssertNil(metadata.duplicateGroupId)
        XCTAssertNil(metadata.sensitiveIgnoredAtMs)
    }

    func testAnimalAndVehicleEvidenceDoNotBecomePeopleFromWeakAppearanceLabels() {
        let animalCategory = VaultAIAnalyzer.categoryForTesting(
            labels: [
                ("dog mammal animal", 0.88),
                ("hair", 0.74),
                ("skin", 0.43),
            ],
            visualStats: (
                blueRatio: 0.04,
                greenRatio: 0.10,
                warmRatio: 0.18,
                skinToneRatio: 0.11,
                darkRatio: 0.18,
                whiteRatio: 0.03,
                saturationAverage: 0.31
            )
        )
        XCTAssertNotEqual(animalCategory, VaultAICategory.people)

        let vehicleCategory = VaultAIAnalyzer.categoryForTesting(
            labels: [
                ("car vehicle wheel", 0.91),
                ("skin", 0.54),
                ("hair", 0.46),
            ],
            visualStats: (
                blueRatio: 0.06,
                greenRatio: 0.04,
                warmRatio: 0.16,
                skinToneRatio: 0.09,
                darkRatio: 0.20,
                whiteRatio: 0.04,
                saturationAverage: 0.28
            )
        )
        XCTAssertEqual(vehicleCategory, VaultAICategory.other)
    }

    func testAnimalFaceDetectionDoesNotClassifyAsPeopleWithoutHumanLabels() {
        let category = VaultAIAnalyzer.categoryForTesting(
            labels: [
                ("cat mammal animal", 0.86),
                ("hair", 0.68),
            ],
            tags: [VaultAITag.face],
            visualStats: (
                blueRatio: 0.04,
                greenRatio: 0.09,
                warmRatio: 0.20,
                skinToneRatio: 0.12,
                darkRatio: 0.19,
                whiteRatio: 0.02,
                saturationAverage: 0.32
            )
        )
        XCTAssertNotEqual(category, VaultAICategory.people)
    }

    func testFaceTagAloneDoesNotClassifyAsPeople() {
        let category = VaultAIAnalyzer.categoryForTesting(
            labels: [],
            tags: [VaultAITag.face],
            visualStats: (
                blueRatio: 0.02,
                greenRatio: 0.04,
                warmRatio: 0.08,
                skinToneRatio: 0.02,
                darkRatio: 0.04,
                whiteRatio: 0.03,
                saturationAverage: 0.18
            )
        )
        XCTAssertNotEqual(category, VaultAICategory.people)
    }

    func testPeopleFileNameDoesNotOverrideVisualCategory() {
        XCTAssertNil(VaultAIAnalyzer.metadataHintCategoryForTesting(originalFileName: "people_001_car.jpg"))
        XCTAssertNil(VaultAIAnalyzer.metadataHintCategoryForTesting(originalFileName: "person_dog_scene.jpg"))
        XCTAssertNil(VaultAIAnalyzer.metadataHintCategoryForTesting(originalFileName: "surface_texture.jpg"))

        let category = VaultAIAnalyzer.categoryForTesting(
            labels: [
                ("car vehicle wheel", 0.90),
            ],
            tags: [],
            visualStats: (
                blueRatio: 0.06,
                greenRatio: 0.04,
                warmRatio: 0.16,
                skinToneRatio: 0.09,
                darkRatio: 0.20,
                whiteRatio: 0.04,
                saturationAverage: 0.28
            )
        )
        XCTAssertNotEqual(category, VaultAICategory.people)
    }

    func testPeopleFileNameDoesNotSeedPeopleWithoutVisualEvidence() {
        let category = VaultAIAnalyzer.categoryForTesting(
            labels: []
        )
        XCTAssertNotEqual(category, VaultAICategory.people)
    }

    func testProminentHumanBodyClassifiesAsPeople() {
        let category = VaultAIAnalyzer.categoryForTesting(
            labels: [],
            hasProminentHuman: true
        )
        XCTAssertEqual(category, VaultAICategory.people)
    }

    func testProminentFaceClassifiesAsPeopleWhenNoDominantNonHumanSubject() {
        let category = VaultAIAnalyzer.categoryForTesting(
            labels: [],
            hasProminentFace: true
        )
        XCTAssertEqual(category, VaultAICategory.people)
    }

    func testFaceWithSkinToneEvidenceClassifiesAsPeopleWhenNoDominantNonHumanSubject() {
        let category = VaultAIAnalyzer.categoryForTesting(
            labels: [],
            tags: [VaultAITag.face],
            visualStats: (
                blueRatio: 0.03,
                greenRatio: 0.05,
                warmRatio: 0.24,
                skinToneRatio: 0.10,
                darkRatio: 0.15,
                whiteRatio: 0.03,
                saturationAverage: 0.31
            )
        )
        XCTAssertEqual(category, VaultAICategory.people)
    }

    func testPortraitColorLayoutClassifiesAsPeopleWithoutVisionFaceLabel() {
        let category = VaultAIAnalyzer.categoryForTesting(
            labels: [],
            visualStats: (
                blueRatio: 0.04,
                greenRatio: 0.06,
                warmRatio: 0.24,
                skinToneRatio: 0.18,
                darkRatio: 0.16,
                whiteRatio: 0.04,
                saturationAverage: 0.34
            )
        )
        XCTAssertEqual(category, VaultAICategory.people)
    }

    func testProminentHumanRectangleDoesNotOverrideDominantVehicleOrAnimalLabels() {
        let vehicleCategory = VaultAIAnalyzer.categoryForTesting(
            labels: [
                ("car vehicle wheel", 0.91),
                ("person", 0.38),
            ],
            hasProminentHuman: true
        )
        XCTAssertNotEqual(vehicleCategory, VaultAICategory.people)

        let animalCategory = VaultAIAnalyzer.categoryForTesting(
            labels: [
                ("dog mammal animal", 0.88),
                ("person", 0.42),
            ],
            hasProminentHuman: true
        )
        XCTAssertNotEqual(animalCategory, VaultAICategory.people)

        let animalFaceCategory = VaultAIAnalyzer.categoryForTesting(
            labels: [
                ("dog mammal animal", 0.88),
            ],
            hasProminentFace: true
        )
        XCTAssertNotEqual(animalFaceCategory, VaultAICategory.people)
    }

    func testExplicitHumanEvidenceStillClassifiesAsPeople() {
        let category = VaultAIAnalyzer.categoryForTesting(
            labels: [
                ("person portrait", 0.72),
                ("skin", 0.62),
            ],
            visualStats: (
                blueRatio: 0.04,
                greenRatio: 0.06,
                warmRatio: 0.22,
                skinToneRatio: 0.10,
                darkRatio: 0.17,
                whiteRatio: 0.04,
                saturationAverage: 0.34
            )
        )
        XCTAssertEqual(category, VaultAICategory.people)
    }
}
