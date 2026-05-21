import Foundation
import CoreImage
import Photos
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

enum PrivacyRedactionRegionSource: String {
    case automatic
    case manual
}

struct PrivacyRedactionRegion: Identifiable, Equatable {
    let id: UUID
    var normalizedRect: CGRect
    var style: PrivacyRedactionStyle
    var source: PrivacyRedactionRegionSource

    init(
        id: UUID = UUID(),
        normalizedRect: CGRect,
        style: PrivacyRedactionStyle = .mosaic,
        source: PrivacyRedactionRegionSource
    ) {
        self.id = id
        self.normalizedRect = normalizedRect
        self.style = style
        self.source = source
    }
}

struct PrivacyRedactionResult {
    let imported: Bool
    let detectedRegionCount: Int
}

@MainActor
final class PrivacyRedactionService: ObservableObject {
    static let shared = PrivacyRedactionService()

    @Published private(set) var isSaving = false
    @Published private(set) var isDetecting = false
    @Published private(set) var lastMessage: String?
    @Published private(set) var lastIsError = false

    private let metadataStore = VaultMetadataStore.shared
    private let vaultStore = VaultStore.shared
    private let tempManager = PlaintextTempFileManager.shared

    private init() {}

    func updateMessage(_ message: String, isError: Bool) {
        lastMessage = message
        lastIsError = isError
    }

    func detectRegions(path: String) async -> [PrivacyRedactionRegion] {
        guard !path.isEmpty else { return PrivacyRedactor.demoRegions() }

        isDetecting = true
        defer { isDetecting = false }

        do {
            let regions = try await PrivacyRedactor.detectNormalizedRegions(path: path)
            return regions
        } catch {
            lastMessage = error.localizedDescription
            lastIsError = true
            return []
        }
    }

    func redactAndImport(path: String, style: PrivacyRedactionStyle) async -> PrivacyRedactionResult {
        let detected = await detectRegions(path: path).map {
            PrivacyRedactionRegion(id: $0.id, normalizedRect: $0.normalizedRect, style: style, source: $0.source)
        }
        return await redactAndImport(path: path, regions: detected)
    }

    func redactAndImport(path: String, regions: [PrivacyRedactionRegion]) async -> PrivacyRedactionResult {
        guard !path.isEmpty else {
            lastMessage = L10n.tr("privacy_redact_select_first")
            lastIsError = true
            return PrivacyRedactionResult(imported: false, detectedRegionCount: 0)
        }
        guard !regions.isEmpty else {
            lastMessage = L10n.tr("privacy_redact_no_sensitive_toast")
            lastIsError = false
            return PrivacyRedactionResult(imported: false, detectedRegionCount: 0)
        }

        isSaving = true
        defer { isSaving = false }

        let albumName = metadataStore.mediaRecord(forPath: path)?.albumName ?? vaultDefaultAlbumName
        do {
            let output = try await PrivacyRedactor.renderRedactedJPEG(path: path, regions: regions)
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

    func exportToSystemPhotos(path: String, regions: [PrivacyRedactionRegion]) async -> Bool {
        guard let tempURL = await makeRedactedTemporaryFile(path: path, regions: regions, scene: .export) else { return false }
        defer { tempManager.removeItem(tempURL) }

        let status = await requestPhotoAddAuthorization()
        guard status == .authorized || status == .limited else {
            lastMessage = L10n.tr("privacy_redact_export_denied")
            lastIsError = true
            return false
        }

        do {
            try await PHPhotoLibrary.shared().performChanges {
                PHAssetCreationRequest.creationRequestForAssetFromImage(atFileURL: tempURL)
            }
            lastMessage = L10n.tr("privacy_redact_export_success")
            lastIsError = false
            return true
        } catch {
            lastMessage = L10n.tr("privacy_redact_export_failed")
            lastIsError = true
            return false
        }
    }

    func makeRedactedShareURL(path: String, regions: [PrivacyRedactionRegion]) async -> URL? {
        await makeRedactedTemporaryFile(path: path, regions: regions, scene: .share)
    }

    func renderPreviewImage(path: String, regions: [PrivacyRedactionRegion]) async -> UIImage? {
        guard !path.isEmpty, !regions.isEmpty else { return nil }
        do {
            return try await PrivacyRedactor.renderRedactedPreviewImage(path: path, regions: regions)
        } catch {
            return nil
        }
    }

    private func makeRedactedTemporaryFile(
        path: String,
        regions: [PrivacyRedactionRegion],
        scene: PlaintextTempScene
    ) async -> URL? {
        guard !path.isEmpty else {
            lastMessage = L10n.tr("privacy_redact_select_first")
            lastIsError = true
            return nil
        }
        guard !regions.isEmpty else {
            lastMessage = L10n.tr("privacy_redact_no_sensitive_toast")
            lastIsError = false
            return nil
        }

        do {
            let output = try await PrivacyRedactor.renderRedactedJPEG(path: path, regions: regions)
            let url = try tempManager.makeFileURL(
                for: scene,
                preferredBaseName: URL(fileURLWithPath: output.fileName).deletingPathExtension().lastPathComponent,
                fileExtension: "jpg"
            )
            try output.data.write(to: url, options: .atomic)
            return url
        } catch {
            lastMessage = error.localizedDescription
            lastIsError = true
            return nil
        }
    }

    private func requestPhotoAddAuthorization() async -> PHAuthorizationStatus {
        let current = PHPhotoLibrary.authorizationStatus(for: .addOnly)
        guard current == .notDetermined else { return current }
        return await withCheckedContinuation { continuation in
            PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
                continuation.resume(returning: status)
            }
        }
    }
}

private enum PrivacyRedactor {
    private static let ciContext = CIContext(options: [.cacheIntermediates: false])

    struct Output {
        let data: Data
        let fileName: String
        let regionCount: Int
    }

    private struct RenderedImage {
        let image: UIImage
        let fileName: String
        let regionCount: Int
    }

    static func renderRedactedJPEG(path: String, style: PrivacyRedactionStyle) async throws -> Output {
        try await renderRedactedJPEG(path: path, regions: [])
    }

    static func renderRedactedJPEG(path: String, regions: [PrivacyRedactionRegion]) async throws -> Output {
        let rendered = try await renderRedactedImage(path: path, regions: regions)
        guard let jpeg = rendered.image.jpegData(compressionQuality: 0.92) else {
            throw RedactionError.renderFailed
        }
        return Output(
            data: jpeg,
            fileName: rendered.fileName,
            regionCount: rendered.regionCount
        )
    }

    static func renderRedactedPreviewImage(path: String, regions: [PrivacyRedactionRegion]) async throws -> UIImage {
        try await renderRedactedImage(path: path, regions: regions).image
    }

    private static func renderRedactedImage(path: String, regions: [PrivacyRedactionRegion]) async throws -> RenderedImage {
        try await Task.detached(priority: .utility) {
            let encryptedURL = URL(fileURLWithPath: path)
            let data = try VaultCipher.shared.decryptFile(at: encryptedURL)
            guard let image = UIImage(data: data),
                  let sourceImage = normalizedImage(from: image),
                  let cgImage = sourceImage.cgImage else {
                throw RedactionError.unsupportedMedia
            }
            let imageSize = CGSize(width: cgImage.width, height: cgImage.height)

            let effectiveRegions: [PrivacyRedactionRegion]
            if regions.isEmpty {
                let detected = detectRegions(cgImage: cgImage, imageSize: imageSize)
                let rects = detected.isEmpty ? [fallbackRegion(size: imageSize)] : detected
                effectiveRegions = rects.map {
                    PrivacyRedactionRegion(normalizedRect: normalize($0, imageSize: imageSize), style: .mosaic, source: .automatic)
                }
            } else {
                effectiveRegions = regions.map {
                    PrivacyRedactionRegion(
                        id: $0.id,
                        normalizedRect: clampNormalized($0.normalizedRect),
                        style: $0.style,
                        source: $0.source
                    )
                }
            }

            let renderer = UIGraphicsImageRenderer(size: imageSize, format: rendererFormat())
            let redacted = renderer.image { context in
                sourceImage.draw(in: CGRect(origin: .zero, size: imageSize))
                for region in effectiveRegions {
                    drawRedaction(
                        in: context.cgContext,
                        sourceImage: cgImage,
                        rect: denormalize(region.normalizedRect, imageSize: imageSize),
                        style: region.style
                    )
                }
            }
            let base = encryptedURL.deletingPathExtension().lastPathComponent
            return RenderedImage(
                image: redacted,
                fileName: "\(base)_redacted.jpg",
                regionCount: effectiveRegions.count
            )
        }.value
    }

    static func detectNormalizedRegions(path: String) async throws -> [PrivacyRedactionRegion] {
        try await Task.detached(priority: .utility) {
            let encryptedURL = URL(fileURLWithPath: path)
            let data = try VaultCipher.shared.decryptFile(at: encryptedURL)
            guard let image = UIImage(data: data),
                  let normalized = normalizedImage(from: image),
                  let cgImage = normalized.cgImage else {
                throw RedactionError.unsupportedMedia
            }
            let imageSize = CGSize(width: cgImage.width, height: cgImage.height)
            return detectRegions(cgImage: cgImage, imageSize: imageSize).map {
                PrivacyRedactionRegion(
                    normalizedRect: normalize($0, imageSize: imageSize),
                    style: .mosaic,
                    source: .automatic
                )
            }
        }.value
    }

    static func demoRegions() -> [PrivacyRedactionRegion] {
        [
            PrivacyRedactionRegion(
                normalizedRect: CGRect(x: 0.57, y: 0.13, width: 0.26, height: 0.17),
                style: .mosaic,
                source: .automatic
            ),
            PrivacyRedactionRegion(
                normalizedRect: CGRect(x: 0.15, y: 0.77, width: 0.66, height: 0.08),
                style: .mosaic,
                source: .automatic
            ),
            PrivacyRedactionRegion(
                normalizedRect: CGRect(x: 0.17, y: 0.86, width: 0.52, height: 0.07),
                style: .mosaic,
                source: .automatic
            )
        ]
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

    private static func drawRedaction(
        in context: CGContext,
        sourceImage: CGImage,
        rect: CGRect,
        style: PrivacyRedactionStyle
    ) {
        guard let rect = pixelAlignedRect(rect, imageSize: CGSize(width: sourceImage.width, height: sourceImage.height)) else { return }
        context.saveGState()
        switch style {
        case .mosaic:
            drawMosaic(in: context, sourceImage: sourceImage, rect: rect)
        case .blur:
            drawBlur(in: context, sourceImage: sourceImage, rect: rect)
        case .blackBar:
            context.setFillColor(UIColor.black.cgColor)
            context.fill(rect)
        case .whiteBar:
            context.setFillColor(UIColor.white.cgColor)
            context.fill(rect)
        case .ovalBlur:
            context.addEllipse(in: rect)
            context.clip()
            drawBlur(in: context, sourceImage: sourceImage, rect: rect)
        case .emoji:
            context.setFillColor(UIColor.white.cgColor)
            context.fill(rect)
            drawEmoji(in: rect)
        }
        context.restoreGState()
    }

    private static func drawMosaic(in context: CGContext, sourceImage: CGImage, rect: CGRect) {
        guard let crop = sourceImage.cropping(to: rect) else { return }
        let shortSide = max(1, min(rect.width, rect.height))
        let longSide = max(1, max(rect.width, rect.height))
        let block = max(14, max(Int(shortSide * 0.10), Int(longSide * 0.04)))
        let smallSize = CGSize(
            width: max(1, Int(rect.width) / block),
            height: max(1, Int(rect.height) / block)
        )
        let cropImage = UIImage(cgImage: crop)
        let small = UIGraphicsImageRenderer(size: smallSize, format: rendererFormat()).image { rendererContext in
            rendererContext.cgContext.interpolationQuality = .medium
            cropImage.draw(in: CGRect(origin: .zero, size: smallSize))
        }
        context.interpolationQuality = .none
        small.draw(in: rect)
    }

    private static func drawBlur(in context: CGContext, sourceImage: CGImage, rect: CGRect) {
        guard let blurred = blurredCGImage(from: sourceImage, rect: rect) else { return }
        UIImage(cgImage: blurred).draw(in: rect)
    }

    private static func drawEmoji(in rect: CGRect) {
        let width = rect.width
        let height = rect.height
        let shortSide = max(1, min(width, height))
        let longSide = max(1, max(width, height))
        let emojiSize = max(14, shortSide * 0.92)
        let emoji = "🙈" as NSString
        let attributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: emojiSize)
        ]
        let size = emoji.size(withAttributes: attributes)
        let count = max(1, Int((longSide / shortSide).rounded()))
        let step = longSide / CGFloat(count)
        let isHorizontal = width >= height
        for index in 0..<count {
            let center = step * (CGFloat(index) + 0.5)
            let point: CGPoint
            if isHorizontal {
                point = CGPoint(x: rect.minX + center - size.width / 2, y: rect.midY - size.height / 2)
            } else {
                point = CGPoint(x: rect.midX - size.width / 2, y: rect.minY + center - size.height / 2)
            }
            emoji.draw(at: point, withAttributes: attributes)
        }
    }

    private static func blurredCGImage(from sourceImage: CGImage, rect: CGRect) -> CGImage? {
        guard let crop = sourceImage.cropping(to: rect) else { return nil }
        let input = CIImage(cgImage: crop)
        let radius = blurRadius(for: rect)
        let output = input
            .clampedToExtent()
            .applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: radius])
            .cropped(to: input.extent)
        return ciContext.createCGImage(output, from: input.extent)
    }

    private static func blurRadius(for rect: CGRect) -> CGFloat {
        let longestSide = max(rect.width, rect.height)
        return min(80, max(20, longestSide * 0.08))
    }

    private static func pixelAlignedRect(_ rect: CGRect, imageSize: CGSize) -> CGRect? {
        let bounds = CGRect(origin: .zero, size: imageSize)
        let clipped = rect.standardized.intersection(bounds)
        guard !clipped.isNull, clipped.width >= 1, clipped.height >= 1 else { return nil }
        let integral = clipped.integral.intersection(bounds)
        guard !integral.isNull, integral.width >= 1, integral.height >= 1 else { return nil }
        return integral
    }

    private static func normalizedImage(from image: UIImage) -> UIImage? {
        guard image.size.width > 0, image.size.height > 0 else { return nil }
        let renderer = UIGraphicsImageRenderer(size: image.size, format: rendererFormat())
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: image.size))
        }
    }

    private static func rendererFormat() -> UIGraphicsImageRendererFormat {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        return format
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

    private static func normalize(_ rect: CGRect, imageSize: CGSize) -> CGRect {
        guard imageSize.width > 0, imageSize.height > 0 else { return .zero }
        return clampNormalized(CGRect(
            x: rect.minX / imageSize.width,
            y: rect.minY / imageSize.height,
            width: rect.width / imageSize.width,
            height: rect.height / imageSize.height
        ))
    }

    private static func denormalize(_ rect: CGRect, imageSize: CGSize) -> CGRect {
        let normalized = clampNormalized(rect)
        return CGRect(
            x: normalized.minX * imageSize.width,
            y: normalized.minY * imageSize.height,
            width: normalized.width * imageSize.width,
            height: normalized.height * imageSize.height
        )
    }

    private static func clampNormalized(_ rect: CGRect) -> CGRect {
        let bounds = CGRect(x: 0, y: 0, width: 1, height: 1)
        return rect.standardized.intersection(bounds)
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
