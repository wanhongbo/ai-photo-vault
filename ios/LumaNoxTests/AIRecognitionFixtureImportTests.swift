import CoreImage
import Foundation
@testable import LumaNox
import UIKit
import XCTest

@MainActor
final class AIRecognitionFixtureImportTests: XCTestCase {
    func testImportOneHundredAIFixtureImagesAndScan() async throws {
        let albumName = "AI_Test_100_\(Int(Date().timeIntervalSince1970))"
        let fixtures = AIFixtureImageFactory.makeFixtures()
        XCTAssertEqual(fixtures.count, 100)

        var summary = VaultImportSummary()
        for fixture in fixtures {
            let result = await VaultStore.shared.importPlainData(
                fixture.data,
                fileExtension: "jpg",
                albumName: albumName,
                originalFileName: fixture.fileName
            )
            switch result {
            case .added: summary.added += 1
            case .duplicate: summary.duplicate += 1
            case .failed: summary.failed += 1
            }
        }
        await VaultStore.shared.loadSnapshot(recentLimit: 160)
        XCTAssertEqual(summary.added, 100)
        XCTAssertEqual(summary.failed, 0)

        await VaultAIAnalysisService.shared.scanVault()
        let records = VaultAIAnalysisService.shared.records.filter { $0.albumName == albumName }
        XCTAssertEqual(records.count, 100)

        let expectedByID = Dictionary(uniqueKeysWithValues: fixtures.map { ($0.fileName, $0.expectedKind) })
        var buckets: [String: AIEvalBucket] = [:]
        for record in records {
            let expected = expectedByID[record.originalFileName ?? ""] ?? "unknown"
            buckets[expected, default: AIEvalBucket()].observe(record)
        }

        let totalSensitive = records.filter { VaultAIAnalysisService.isSensitive($0) }.count
        let totalCleanup = records.filter { VaultAIAnalysisService.isCleanable($0) }.count
        let scanned = records.filter { $0.ai.scannedAtMs != nil }.count
        let categoryCounts = Dictionary(grouping: records, by: { $0.ai.category ?? "nil" })
            .mapValues(\.count)
            .sorted { $0.key < $1.key }
        let rawTagCounts = records
            .flatMap(\.ai.tags)
            .reduce(into: [String: Int]()) { $0[$1, default: 0] += 1 }
        let tagCounts = rawTagCounts
            .sorted { $0.key < $1.key }

        let categoryText = categoryCounts.map { "\($0.key)=\($0.value)" }.joined(separator: ",")
        let tagText = tagCounts.map { "\($0.key)=\($0.value)" }.joined(separator: ",")
        print("AI_EVAL_IMPORTED album=\(albumName) added=\(summary.added) duplicate=\(summary.duplicate) failed=\(summary.failed)")
        print("AI_EVAL_TOTAL scanned=\(scanned)/\(records.count) sensitive=\(totalSensitive) cleanup=\(totalCleanup)")
        print("AI_EVAL_CATEGORIES \(categoryText)")
        print("AI_EVAL_TAGS \(tagText)")
        for key in AIFixtureImageFactory.expectedKinds {
            let bucket = buckets[key] ?? AIEvalBucket()
            print("AI_EVAL_BUCKET \(key) \(bucket.summary)")
        }

        XCTAssertEqual(scanned, 100)
        XCTAssertGreaterThanOrEqual(totalSensitive, 40)
        XCTAssertLessThanOrEqual(rawTagCounts[VaultAITag.duplicate, default: 0], 20)
        XCTAssertEqual(buckets["screenshot"]?.categories[VaultAICategory.screenshots], 10)
        XCTAssertEqual(buckets["food"]?.categories[VaultAICategory.food], 12)
        XCTAssertEqual(buckets["nature"]?.categories[VaultAICategory.nature], 12)
        XCTAssertEqual(buckets["people"]?.categories[VaultAICategory.people], 10)
        XCTAssertEqual(buckets["id_document"]?.sensitive, 12)
        XCTAssertEqual(buckets["bank_card"]?.sensitive, 10)
        XCTAssertEqual(buckets["qr_barcode"]?.sensitive, 10)
        XCTAssertGreaterThanOrEqual(buckets["duplicate"]?.tags[VaultAITag.duplicate] ?? 0, 10)
    }
}

private struct AIEvalBucket {
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

private struct AIFixture {
    let fileName: String
    let expectedKind: String
    let data: Data
}

private enum AIFixtureImageFactory {
    static let expectedKinds = [
        "people",
        "id_document",
        "bank_card",
        "screenshot",
        "qr_barcode",
        "food",
        "nature",
        "duplicate",
        "low_quality",
    ]

    static func makeFixtures() -> [AIFixture] {
        var fixtures: [AIFixture] = []
        append(&fixtures, kind: "people", count: 10, renderer: drawPortrait)
        append(&fixtures, kind: "id_document", count: 12, renderer: drawIDDocument)
        append(&fixtures, kind: "bank_card", count: 10, renderer: drawBankCard)
        append(&fixtures, kind: "screenshot", count: 10, renderer: drawScreenshot)
        append(&fixtures, kind: "qr_barcode", count: 10, renderer: drawQRAndBarcode)
        append(&fixtures, kind: "food", count: 12, renderer: drawFood)
        append(&fixtures, kind: "nature", count: 12, renderer: drawNature)
        append(&fixtures, kind: "duplicate", count: 12, renderer: drawDuplicate)
        append(&fixtures, kind: "low_quality", count: 12, renderer: drawLowQuality)
        return fixtures
    }

    private static func append(
        _ fixtures: inout [AIFixture],
        kind: String,
        count: Int,
        renderer: (Int) -> UIImage
    ) {
        for index in 0..<count {
            let fileName = String(format: "%@_%03d_%d.jpg", kind, index + 1, Int(Date().timeIntervalSince1970))
            let image = addUniqueMarker(to: renderer(index), marker: fixtures.count + index)
            guard let data = image.jpegData(compressionQuality: 0.9) else { continue }
            fixtures.append(AIFixture(fileName: fileName, expectedKind: kind, data: data))
        }
    }

    private static func render(size: CGSize = CGSize(width: 900, height: 680), _ draw: (CGContext, CGSize) -> Void) -> UIImage {
        UIGraphicsImageRenderer(size: size).image { context in
            UIColor(red: 0.96, green: 0.97, blue: 0.98, alpha: 1).setFill()
            context.fill(CGRect(origin: .zero, size: size))
            draw(context.cgContext, size)
        }
    }

    private static func addUniqueMarker(to image: UIImage, marker: Int) -> UIImage {
        UIGraphicsImageRenderer(size: image.size).image { context in
            image.draw(at: .zero)
            UIColor(
                red: CGFloat((marker * 37) % 255) / 255,
                green: CGFloat((marker * 71) % 255) / 255,
                blue: CGFloat((marker * 113) % 255) / 255,
                alpha: 1
            ).setFill()
            context.fill(CGRect(x: image.size.width - 2, y: image.size.height - 2, width: 1, height: 1))
        }
    }

    private static func drawPortrait(_ index: Int) -> UIImage {
        render { ctx, size in
            gradient(ctx, size: size, top: UIColor(red: 0.22, green: 0.32, blue: 0.52, alpha: 1), bottom: UIColor(red: 0.08, green: 0.10, blue: 0.16, alpha: 1))
            let skin = UIColor(red: 0.78, green: 0.54 + CGFloat(index % 3) * 0.04, blue: 0.42, alpha: 1)
            ctx.setFillColor(UIColor(red: 0.12, green: 0.08, blue: 0.05, alpha: 1).cgColor)
            ctx.fillEllipse(in: CGRect(x: 285, y: 82, width: 330, height: 380))
            ctx.setFillColor(skin.cgColor)
            ctx.fillEllipse(in: CGRect(x: 315, y: 130, width: 270, height: 320))
            ctx.setFillColor(UIColor.black.cgColor)
            ctx.fillEllipse(in: CGRect(x: 382, y: 250, width: 24, height: 18))
            ctx.fillEllipse(in: CGRect(x: 492, y: 250, width: 24, height: 18))
            ctx.setStrokeColor(UIColor(red: 0.46, green: 0.18, blue: 0.17, alpha: 1).cgColor)
            ctx.setLineWidth(12)
            ctx.addArc(center: CGPoint(x: 450, y: 338), radius: 56, startAngle: 0.15, endAngle: .pi - 0.15, clockwise: false)
            ctx.strokePath()
            ctx.setFillColor(UIColor(red: 0.12, green: 0.20, blue: 0.38, alpha: 1).cgColor)
            ctx.fill(CGRect(x: 230, y: 488, width: 440, height: 170))
        }
    }

    private static func drawIDDocument(_ index: Int) -> UIImage {
        render { ctx, _ in
            card(ctx, rect: CGRect(x: 70, y: 70, width: 760, height: 520), color: .white)
            drawText("PASSPORT / ID CARD", at: CGPoint(x: 110, y: 115), size: 38, weight: .bold)
            drawText("Name: Luma Test \(index)", at: CGPoint(x: 110, y: 205), size: 28)
            drawText("ID: 1101051990\(String(format: "%02d", index + 1))123X", at: CGPoint(x: 110, y: 255), size: 28)
            drawText("出生日期 1990-05-\(String(format: "%02d", index + 1))", at: CGPoint(x: 110, y: 305), size: 28)
            drawText("证件号码 身份证 护照", at: CGPoint(x: 110, y: 355), size: 28)
            ctx.setFillColor(UIColor(red: 0.82, green: 0.88, blue: 0.95, alpha: 1).cgColor)
            ctx.fill(CGRect(x: 610, y: 170, width: 135, height: 165))
        }
    }

    private static func drawBankCard(_ index: Int) -> UIImage {
        render { ctx, _ in
            card(ctx, rect: CGRect(x: 80, y: 145, width: 740, height: 390), color: UIColor(red: 0.12, green: 0.18, blue: 0.32, alpha: 1))
            drawText("LUMANOX BANK", at: CGPoint(x: 135, y: 198), size: 34, weight: .bold, color: .white)
            drawText("6222 0200 \(String(format: "%04d", index + 1200)) \(String(format: "%04d", index + 8765))", at: CGPoint(x: 135, y: 330), size: 42, weight: .semibold, color: .white)
            drawText("VALID 05/2\(index % 8 + 6)", at: CGPoint(x: 135, y: 430), size: 26, color: .white)
            ctx.setFillColor(UIColor(red: 0.91, green: 0.72, blue: 0.32, alpha: 1).cgColor)
            ctx.fill(CGRect(x: 132, y: 250, width: 86, height: 54))
        }
    }

    private static func drawScreenshot(_ index: Int) -> UIImage {
        render(size: CGSize(width: 720, height: 1280)) { ctx, size in
            gradient(ctx, size: size, top: UIColor(red: 0.05, green: 0.08, blue: 0.13, alpha: 1), bottom: UIColor.black)
            drawText("9:\(String(format: "%02d", 20 + index))", at: CGPoint(x: 44, y: 36), size: 30, weight: .bold, color: .white)
            for row in 0..<8 {
                let left = row % 2 == 0
                let rect = CGRect(x: left ? 46 : 210, y: 150 + row * 92, width: left ? 430 : 460, height: 64)
                card(ctx, rect: rect, color: left ? UIColor(red: 0.11, green: 0.18, blue: 0.30, alpha: 1) : UIColor(red: 0.13, green: 0.36, blue: 0.72, alpha: 1), radius: 18)
            }
            drawText("Chat screenshot \(index)", at: CGPoint(x: 60, y: 930), size: 34, weight: .bold, color: .white)
        }
    }

    private static func drawQRAndBarcode(_ index: Int) -> UIImage {
        render { ctx, _ in
            card(ctx, rect: CGRect(x: 80, y: 70, width: 740, height: 540), color: .white)
            drawText("Boarding pass / QR code", at: CGPoint(x: 125, y: 115), size: 34, weight: .bold)
            if let qr = qrImage("LumaNox fixture QR \(index)") {
                qr.draw(in: CGRect(x: 120, y: 190, width: 230, height: 230))
            }
            ctx.setFillColor(UIColor.black.cgColor)
            var x: CGFloat = 430
            for stripe in 0..<36 {
                let width = CGFloat((stripe * 7 + index) % 5 + 2)
                ctx.fill(CGRect(x: x, y: 210, width: width, height: 190))
                x += width + CGFloat((stripe % 3) + 2)
            }
            drawText("CODE 39 978020137\(String(format: "%03d", index))", at: CGPoint(x: 125, y: 480), size: 25)
        }
    }

    private static func drawFood(_ index: Int) -> UIImage {
        render { ctx, _ in
            gradient(ctx, size: CGSize(width: 900, height: 680), top: UIColor(red: 0.95, green: 0.83, blue: 0.62, alpha: 1), bottom: UIColor(red: 0.48, green: 0.18, blue: 0.12, alpha: 1))
            ctx.setFillColor(UIColor(red: 0.98, green: 0.75, blue: 0.24, alpha: 1).cgColor)
            ctx.fillEllipse(in: CGRect(x: 180, y: 125, width: 540, height: 430))
            ctx.setFillColor(UIColor(red: 0.88, green: 0.25, blue: 0.16, alpha: 1).cgColor)
            for i in 0..<18 {
                ctx.fillEllipse(in: CGRect(x: 240 + (i * 67 + index * 23) % 380, y: 175 + (i * 43) % 280, width: 34, height: 34))
            }
            ctx.setFillColor(UIColor(red: 0.16, green: 0.42, blue: 0.18, alpha: 1).cgColor)
            for i in 0..<12 {
                ctx.fillEllipse(in: CGRect(x: 230 + (i * 89) % 420, y: 190 + (i * 37 + index * 8) % 250, width: 28, height: 18))
            }
        }
    }

    private static func drawNature(_ index: Int) -> UIImage {
        render { ctx, size in
            gradient(ctx, size: size, top: UIColor(red: 0.43, green: 0.69, blue: 0.94, alpha: 1), bottom: UIColor(red: 0.12, green: 0.42, blue: 0.24, alpha: 1))
            ctx.setFillColor(UIColor(red: 0.30, green: 0.28, blue: 0.24, alpha: 1).cgColor)
            ctx.move(to: CGPoint(x: 80, y: 470))
            ctx.addLine(to: CGPoint(x: 280, y: 220))
            ctx.addLine(to: CGPoint(x: 490, y: 470))
            ctx.closePath()
            ctx.fillPath()
            ctx.setFillColor(UIColor(red: 0.20, green: 0.25, blue: 0.22, alpha: 1).cgColor)
            ctx.move(to: CGPoint(x: 330, y: 480))
            ctx.addLine(to: CGPoint(x: 610, y: 190))
            ctx.addLine(to: CGPoint(x: 850, y: 480))
            ctx.closePath()
            ctx.fillPath()
            ctx.setFillColor(UIColor(red: 0.95, green: 0.96, blue: 0.90, alpha: 1).cgColor)
            ctx.fillEllipse(in: CGRect(x: 650 + CGFloat(index % 3) * 20, y: 88, width: 78, height: 78))
            ctx.setStrokeColor(UIColor(red: 0.76, green: 0.92, blue: 1, alpha: 1).cgColor)
            ctx.setLineWidth(28)
            ctx.move(to: CGPoint(x: 535, y: 310))
            ctx.addCurve(to: CGPoint(x: 420, y: 610), control1: CGPoint(x: 560, y: 430), control2: CGPoint(x: 390, y: 455))
            ctx.strokePath()
        }
    }

    private static func drawDuplicate(_ index: Int) -> UIImage {
        let variant = index % 2
        return render { ctx, size in
            gradient(ctx, size: size, top: UIColor(red: 0.22, green: 0.56, blue: 0.78, alpha: 1), bottom: UIColor(red: 0.08, green: 0.15, blue: 0.28, alpha: 1))
            ctx.setFillColor(UIColor(red: 0.86, green: 0.76, blue: 0.34, alpha: 1).cgColor)
            ctx.fillEllipse(in: CGRect(x: 185 + CGFloat(variant * 4), y: 110, width: 170, height: 170))
            ctx.setFillColor(UIColor(red: 0.18, green: 0.52, blue: 0.27, alpha: 1).cgColor)
            for i in 0..<9 {
                ctx.fill(CGRect(x: 110 + i * 88, y: 470 + (i % 3) * 12 + variant, width: 60, height: 130))
            }
        }
    }

    private static func drawLowQuality(_ index: Int) -> UIImage {
        let base = drawNature(index)
        if index % 2 == 0 {
            return applyGaussianBlur(base) ?? base
        }
        return render { ctx, size in
            UIColor.white.setFill()
            ctx.fill(CGRect(origin: .zero, size: size))
            ctx.setFillColor(UIColor(red: 1, green: 0.96, blue: 0.70, alpha: 1).cgColor)
            ctx.fillEllipse(in: CGRect(x: 100, y: 90, width: 700, height: 460))
            drawText("OVEREXPOSED \(index)", at: CGPoint(x: 250, y: 310), size: 34, weight: .bold, color: UIColor(white: 0.82, alpha: 1))
        }
    }

    private static func qrImage(_ text: String) -> UIImage? {
        let data = text.data(using: .utf8)
        guard let filter = CIFilter(name: "CIQRCodeGenerator") else { return nil }
        filter.setValue(data, forKey: "inputMessage")
        filter.setValue("M", forKey: "inputCorrectionLevel")
        guard let output = filter.outputImage?.transformed(by: CGAffineTransform(scaleX: 8, y: 8)),
              let cgImage = CIContext().createCGImage(output, from: output.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }

    private static func applyGaussianBlur(_ image: UIImage) -> UIImage? {
        guard let ci = CIImage(image: image),
              let filter = CIFilter(name: "CIGaussianBlur") else { return nil }
        filter.setValue(ci, forKey: kCIInputImageKey)
        filter.setValue(12, forKey: kCIInputRadiusKey)
        guard let output = filter.outputImage,
              let cgImage = CIContext().createCGImage(output, from: ci.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }

    private static func gradient(_ ctx: CGContext, size: CGSize, top: UIColor, bottom: UIColor) {
        let colors = [top.cgColor, bottom.cgColor] as CFArray
        let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 1])!
        ctx.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: 0, y: size.height), options: [])
    }

    private static func card(_ ctx: CGContext, rect: CGRect, color: UIColor, radius: CGFloat = 28) {
        ctx.setFillColor(color.cgColor)
        let path = UIBezierPath(roundedRect: rect, cornerRadius: radius)
        ctx.addPath(path.cgPath)
        ctx.fillPath()
    }

    private static func drawText(
        _ text: String,
        at point: CGPoint,
        size: CGFloat,
        weight: UIFont.Weight = .regular,
        color: UIColor = .black
    ) {
        let attrs: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: size, weight: weight),
            .foregroundColor: color,
        ]
        text.draw(at: point, withAttributes: attrs)
    }
}
