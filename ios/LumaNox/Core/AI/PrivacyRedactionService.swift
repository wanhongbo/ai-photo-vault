import Foundation
import UIKit
import Vision

enum PrivacyRedactionStyle: String, CaseIterable, Identifiable {
    case mosaic
    case blur
    case blackBar
    case whiteBar
    case ovalBlur
    case emoji

    var id: String { rawValue }
}

struct PrivacyRedactionResult {
    let imported: Bool
    let detectedRegionCount: Int
}

@MainActor
final class PrivacyRedactionService: ObservableObject {
    static let shared = PrivacyRedactionService()

    @Published private(set) var isSaving = false
    @Published private(set) var lastMessage: String?
    @Published private(set) var lastIsError = false

    private let metadataStore = VaultMetadataStore.shared
    private let vaultStore = VaultStore.shared

    private init() {}

    func updateMessage(_ message: String, isError: Bool) {
        lastMessage = message
        lastIsError = isError
    }

    func redactAndImport(path: String, style: PrivacyRedactionStyle) async -> PrivacyRedactionResult {
        guard !path.isEmpty else {
            lastMessage = L10n.tr("privacy_redact_select_first")
            lastIsError = true
            return PrivacyRedactionResult(imported: false, detectedRegionCount: 0)
        }

        isSaving = true
        defer { isSaving = false }

        let albumName = metadataStore.mediaRecord(forPath: path)?.albumName ?? vaultDefaultAlbumName
        do {
            let output = try await PrivacyRedactor.renderRedactedJPEG(path: path, style: style)
            let result = await vaultStore.importPlainData(
                output.data,
                fileExtension: "jpg",
                albumName: albumName,
                originalFileName: output.fileName
            )
            await vaultStore.loadSnapshot()

            switch result {
            case .added:
                lastMessage = L10n.tr("privacy_redact_save_success", output.regionCount)
                lastIsError = false
                return PrivacyRedactionResult(imported: true, detectedRegionCount: output.regionCount)
            case .duplicate:
                lastMessage = L10n.tr("privacy_redact_save_duplicate")
                lastIsError = false
                return PrivacyRedactionResult(imported: true, detectedRegionCount: output.regionCount)
            case .failed:
                lastMessage = L10n.tr("privacy_redact_save_failed")
                lastIsError = true
                return PrivacyRedactionResult(imported: false, detectedRegionCount: output.regionCount)
            }
        } catch {
            lastMessage = error.localizedDescription
            lastIsError = true
            return PrivacyRedactionResult(imported: false, detectedRegionCount: 0)
        }
    }
}

private enum PrivacyRedactor {
    struct Output {
        let data: Data
        let fileName: String
        let regionCount: Int
    }

    static func renderRedactedJPEG(path: String, style: PrivacyRedactionStyle) async throws -> Output {
        try await Task.detached(priority: .utility) {
            let encryptedURL = URL(fileURLWithPath: path)
            let data = try VaultCipher.shared.decryptFile(at: encryptedURL)
            guard let image = UIImage(data: data),
                  let cgImage = image.cgImage else {
                throw RedactionError.unsupportedMedia
            }

            let regions = detectRegions(cgImage: cgImage, imageSize: image.size)
            let effectiveRegions = regions.isEmpty ? [fallbackRegion(size: image.size)] : regions
            let renderer = UIGraphicsImageRenderer(size: image.size)
            let redacted = renderer.image { context in
                image.draw(in: CGRect(origin: .zero, size: image.size))
                for region in effectiveRegions {
                    drawRedaction(in: context.cgContext, rect: region, style: style)
                }
            }

            guard let jpeg = redacted.jpegData(compressionQuality: 0.92) else {
                throw RedactionError.renderFailed
            }
            let base = encryptedURL.deletingPathExtension().lastPathComponent
            return Output(
                data: jpeg,
                fileName: "\(base)_redacted.jpg",
                regionCount: effectiveRegions.count
            )
        }.value
    }

    private static func detectRegions(cgImage: CGImage, imageSize: CGSize) -> [CGRect] {
        let faceRequest = VNDetectFaceRectanglesRequest()
        let barcodeRequest = VNDetectBarcodesRequest()
        let textRequest = VNRecognizeTextRequest()
        textRequest.recognitionLevel = .fast
        textRequest.usesLanguageCorrection = false

        let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
        try? handler.perform([faceRequest, barcodeRequest, textRequest])

        var regions: [CGRect] = []
        regions += (faceRequest.results ?? []).map { convert($0.boundingBox, imageSize: imageSize).insetBy(dx: -12, dy: -12) }
        regions += (barcodeRequest.results ?? []).map { convert($0.boundingBox, imageSize: imageSize).insetBy(dx: -10, dy: -10) }
        regions += (textRequest.results ?? [])
            .filter { $0.confidence >= 0.25 }
            .map { convert($0.boundingBox, imageSize: imageSize).insetBy(dx: -8, dy: -6) }
        return mergeOverlapping(regions.map { clamp($0, to: imageSize) }, imageSize: imageSize)
    }

    private static func drawRedaction(in context: CGContext, rect: CGRect, style: PrivacyRedactionStyle) {
        context.saveGState()
        switch style {
        case .mosaic:
            context.setFillColor(UIColor.black.withAlphaComponent(0.86).cgColor)
            context.fill(rect)
            context.setFillColor(UIColor(red: 0.29, green: 0.62, blue: 1, alpha: 0.35).cgColor)
            let block: CGFloat = max(10, min(rect.width, rect.height) / 4)
            var y = rect.minY
            var toggle = false
            while y < rect.maxY {
                var x = rect.minX
                while x < rect.maxX {
                    if toggle {
                        context.fill(CGRect(x: x, y: y, width: min(block, rect.maxX - x), height: min(block, rect.maxY - y)))
                    }
                    toggle.toggle()
                    x += block
                }
                y += block
            }
        case .blur:
            context.setFillColor(UIColor(red: 0.72, green: 0.84, blue: 1, alpha: 0.78).cgColor)
            context.fill(rect)
        case .blackBar:
            context.setFillColor(UIColor.black.cgColor)
            context.fill(rect)
        case .whiteBar:
            context.setFillColor(UIColor.white.cgColor)
            context.fill(rect)
        case .ovalBlur:
            context.setFillColor(UIColor(red: 0.72, green: 0.84, blue: 1, alpha: 0.76).cgColor)
            context.fillEllipse(in: rect)
        case .emoji:
            context.setFillColor(UIColor.black.withAlphaComponent(0.28).cgColor)
            context.fill(rect)
            let emoji = "🙂" as NSString
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: max(22, min(rect.width, rect.height) * 0.55))
            ]
            let size = emoji.size(withAttributes: attributes)
            emoji.draw(
                at: CGPoint(x: rect.midX - size.width / 2, y: rect.midY - size.height / 2),
                withAttributes: attributes
            )
        }
        context.restoreGState()
    }

    private static func convert(_ normalized: CGRect, imageSize: CGSize) -> CGRect {
        CGRect(
            x: normalized.minX * imageSize.width,
            y: (1 - normalized.maxY) * imageSize.height,
            width: normalized.width * imageSize.width,
            height: normalized.height * imageSize.height
        )
    }

    private static func fallbackRegion(size: CGSize) -> CGRect {
        CGRect(
            x: size.width * 0.18,
            y: size.height * 0.38,
            width: size.width * 0.64,
            height: size.height * 0.18
        )
    }

    private static func clamp(_ rect: CGRect, to size: CGSize) -> CGRect {
        let bounds = CGRect(origin: .zero, size: size)
        return rect.intersection(bounds)
    }

    private static func mergeOverlapping(_ regions: [CGRect], imageSize: CGSize) -> [CGRect] {
        var merged: [CGRect] = []
        for region in regions where !region.isNull && region.width > 4 && region.height > 4 {
            if let index = merged.firstIndex(where: { $0.intersects(region) || $0.insetBy(dx: -12, dy: -12).intersects(region) }) {
                merged[index] = clamp(merged[index].union(region), to: imageSize)
            } else {
                merged.append(region)
            }
        }
        return merged
    }
}

private enum RedactionError: LocalizedError {
    case unsupportedMedia
    case renderFailed

    var errorDescription: String? {
        switch self {
        case .unsupportedMedia: return L10n.tr("privacy_redact_unsupported")
        case .renderFailed: return L10n.tr("privacy_redact_save_failed")
        }
    }
}
