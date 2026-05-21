import Foundation

struct VaultAIVisionLabel: Hashable {
    let identifier: String
    let confidence: Double
}

struct VaultAIVisualStats: Hashable {
    let blueRatio: Double
    let greenRatio: Double
    let warmRatio: Double
    let skinToneRatio: Double
    let darkRatio: Double
    let whiteRatio: Double
    let saturationAverage: Double
}

enum VaultAICategoryMapper {
    static func pickCategory(
        labels: [VaultAIVisionLabel],
        tags: Set<String>,
        stats: VaultAIVisualStats,
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
        let hasPortraitColorLayout = stats.skinToneRatio > 0.06
            && stats.darkRatio > 0.06
            && stats.warmRatio < 0.42
            && stats.whiteRatio < 0.22
            && stats.saturationAverage > 0.18
            && nonHumanSubjectScore < 0.25
        let hasFaceBasedHumanEvidence = hasProminentFace
            || (hasFaceEvidence && hasSkinToneHumanEvidence && nonHumanSubjectScore < 0.45)

        if hasProminentHuman {
            scores[VaultAICategory.people, default: 0] += 1.2
        } else if hasFaceBasedHumanEvidence {
            scores[VaultAICategory.people, default: 0] += 0.95
        } else if hasPortraitColorLayout {
            scores[VaultAICategory.people, default: 0] += 0.9
        } else if hasFaceEvidence {
            scores[VaultAICategory.people, default: 0] += 0.25
        }
        if tags.contains(VaultAITag.text) {
            scores[VaultAICategory.documents, default: 0] += 0.45
        }

        if hasProminentHuman || hasFaceBasedHumanEvidence || hasPortraitColorLayout || humanLabelScore >= 0.45 {
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
            if !hasProminentHuman && !hasFaceBasedHumanEvidence && !hasPortraitColorLayout && humanLabelScore < 0.45 {
                return VaultAICategory.other
            }
        }
        return best.key
    }

    private static func addLabelEvidence(_ label: VaultAIVisionLabel, to scores: inout [String: Double]) {
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

    private static func humanEvidenceScore(_ labels: [VaultAIVisionLabel]) -> Double {
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

    private static func nonHumanEvidenceScore(_ labels: [VaultAIVisionLabel]) -> Double {
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
}
