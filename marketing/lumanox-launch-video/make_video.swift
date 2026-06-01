import AppKit
import AVFoundation

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let workDir = root.appendingPathComponent("marketing/lumanox-launch-video")
let assetsDir = workDir.appendingPathComponent("assets")
let outputDir = workDir.appendingPathComponent("output")
try FileManager.default.createDirectory(at: outputDir, withIntermediateDirectories: true)

let width = 1920
let height = 1080
let fps: Int32 = 30
let frameDuration = CMTime(value: 1, timescale: fps)

let videoURL = outputDir.appendingPathComponent("lumanox_youtube_video_silent.mp4")
let finalURL = outputDir.appendingPathComponent("lumanox_youtube_video.mp4")
let audioURL = outputDir.appendingPathComponent("voiceover.aiff")
for url in [videoURL, finalURL] {
    try? FileManager.default.removeItem(at: url)
}

func color(_ hex: String, _ alpha: CGFloat = 1) -> NSColor {
    var raw = hex.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
    if raw.count == 3 {
        raw = raw.map { "\($0)\($0)" }.joined()
    }
    let value = Int(raw, radix: 16) ?? 0
    return NSColor(
        calibratedRed: CGFloat((value >> 16) & 0xff) / 255,
        green: CGFloat((value >> 8) & 0xff) / 255,
        blue: CGFloat(value & 0xff) / 255,
        alpha: alpha
    )
}

func image(_ name: String) -> NSImage {
    let url = assetsDir.appendingPathComponent(name)
    guard let img = NSImage(contentsOf: url) else {
        fatalError("Missing image: \(url.path)")
    }
    return img
}

let screenshots: [String: NSImage] = [
    "vault": image("latest-tabs/vault.png"),
    "ai": image("latest-tabs/ai.png"),
    "camera": image("latest-tabs/camera.png"),
    "settings": image("latest-tabs/settings.png")
]

struct Scene {
    let duration: Double
    let eyebrow: String
    let title: String
    let subtitle: String
    let bullets: [String]
    let images: [String]
    let cta: String?
}

let scenes = [
    Scene(
        duration: 6.4,
        eyebrow: "PRIVATE PHOTO + VIDEO VAULT",
        title: "LumaNox",
        subtitle: "A calmer place for sensitive media, with local encryption and on-device privacy tools.",
        bullets: ["Offline vault", "Local AI", "Encrypted backup"],
        images: ["vault", "ai", "camera"],
        cta: nil
    ),
    Scene(
        duration: 6.6,
        eyebrow: "VAULT",
        title: "Import only what needs protection.",
        subtitle: "Browse albums inside a local AES-256 vault, with clear offline and encrypted status.",
        bullets: ["Real albums", "Private thumbnails", "No cloud media sync"],
        images: ["vault"],
        cta: nil
    ),
    Scene(
        duration: 6.7,
        eyebrow: "AI ASSISTANT",
        title: "Private scans stay on this device.",
        subtitle: "Review sensitive items, hidden metadata, categories, and cleanup candidates without uploading the vault.",
        bullets: ["Sensitive review", "Classify", "Deduplicate"],
        images: ["ai"],
        cta: nil
    ),
    Scene(
        duration: 6.5,
        eyebrow: "PRIVATE CAMERA",
        title: "Capture straight into the vault.",
        subtitle: "Take photos or videos from inside LumaNox, with camera controls made for private capture.",
        bullets: ["Photo + video", "Flash, timer, grid", "Vault-first capture"],
        images: ["camera"],
        cta: nil
    ),
    Scene(
        duration: 6.6,
        eyebrow: "CONTROL",
        title: "Security, backup, storage, and support in one place.",
        subtitle: "Manage subscription, PIN, backup and restore, export, trash, language, and legal pages from Settings.",
        bullets: ["Security & Privacy", "Backup & Sync", "Data & Storage"],
        images: ["settings"],
        cta: nil
    ),
    Scene(
        duration: 6.4,
        eyebrow: "PRIVACY PROMISE",
        title: "No ads. No vault media cloud upload. Your vault, your control.",
        subtitle: "A private photo workflow that keeps the important boundary obvious.",
        bullets: ["Android available now", "iOS version planned next"],
        images: ["vault", "ai", "settings"],
        cta: "Get it on Google Play"
    )
]

func drawText(_ text: String, in rect: CGRect, size: CGFloat, weight: NSFont.Weight = .regular, color textColor: NSColor = color("#EAF1FF"), align: NSTextAlignment = .left, lineSpacing: CGFloat = 1.08) {
    let paragraph = NSMutableParagraphStyle()
    paragraph.alignment = align
    paragraph.lineBreakMode = .byWordWrapping
    paragraph.lineHeightMultiple = lineSpacing
    let font = NSFont.systemFont(ofSize: size, weight: weight)
    let attrs: [NSAttributedString.Key: Any] = [
        .font: font,
        .foregroundColor: textColor,
        .paragraphStyle: paragraph,
        .kern: 0
    ]
    (text as NSString).draw(with: rect, options: [.usesLineFragmentOrigin, .usesFontLeading], attributes: attrs)
}

func rounded(_ rect: CGRect, radius: CGFloat) -> CGPath {
    CGPath(roundedRect: rect, cornerWidth: radius, cornerHeight: radius, transform: nil)
}

func fillRound(_ ctx: CGContext, _ rect: CGRect, _ radius: CGFloat, _ fill: NSColor, stroke: NSColor? = nil, lineWidth: CGFloat = 1) {
    ctx.addPath(rounded(rect, radius: radius))
    ctx.setFillColor(fill.cgColor)
    ctx.fillPath()
    if let stroke {
        ctx.addPath(rounded(rect, radius: radius))
        ctx.setStrokeColor(stroke.cgColor)
        ctx.setLineWidth(lineWidth)
        ctx.strokePath()
    }
}

func drawGradient(_ ctx: CGContext) {
    let colors = [color("#05080D").cgColor, color("#0B1324").cgColor, color("#07121E").cgColor] as CFArray
    let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 0.45, 1])!
    ctx.drawLinearGradient(gradient, start: CGPoint(x: 0, y: 0), end: CGPoint(x: CGFloat(width), y: CGFloat(height)), options: [])
    ctx.setFillColor(color("#4A9EFF", 0.08).cgColor)
    ctx.fillEllipse(in: CGRect(x: 1280, y: -180, width: 560, height: 560))
    ctx.setFillColor(color("#21C277", 0.055).cgColor)
    ctx.fillEllipse(in: CGRect(x: -130, y: 650, width: 430, height: 430))
}

func drawChip(_ ctx: CGContext, _ text: String, x: CGFloat, y: CGFloat, accent: NSColor = color("#4A9EFF")) {
    let attrs: [NSAttributedString.Key: Any] = [.font: NSFont.systemFont(ofSize: 25, weight: .semibold)]
    let w = ceil((text as NSString).size(withAttributes: attrs).width) + 36
    let rect = CGRect(x: x, y: y, width: w, height: 48)
    fillRound(ctx, rect, 24, color("#122033", 0.86), stroke: color("#223247"))
    ctx.setFillColor(accent.cgColor)
    ctx.fillEllipse(in: CGRect(x: x + 16, y: y + 18, width: 12, height: 12))
    drawText(text, in: CGRect(x: x + 36, y: y + 10, width: w - 48, height: 32), size: 24, weight: .semibold, color: color("#C8D7EE"))
}

func drawPhone(_ ctx: CGContext, img: NSImage, rect: CGRect, rotation: CGFloat = 0, alpha: CGFloat = 1) {
    ctx.saveGState()
    ctx.translateBy(x: rect.midX, y: rect.midY)
    ctx.rotate(by: rotation)
    ctx.setAlpha(alpha)
    let body = CGRect(x: -rect.width / 2, y: -rect.height / 2, width: rect.width, height: rect.height)
    ctx.setShadow(offset: CGSize(width: 0, height: 26), blur: 42, color: color("#000000", 0.42).cgColor)
    fillRound(ctx, body, 46, color("#07101B"), stroke: color("#2B3D55", 0.9), lineWidth: 2)
    ctx.setShadow(offset: .zero, blur: 0, color: nil)
    let screen = body.insetBy(dx: 18, dy: 18)
    ctx.saveGState()
    ctx.addPath(rounded(screen, radius: 34))
    ctx.clip()
    img.draw(in: screen, from: .zero, operation: .sourceOver, fraction: 1, respectFlipped: true, hints: [.interpolation: NSImageInterpolation.high])
    ctx.restoreGState()
    fillRound(ctx, screen, 34, color("#000000", 0), stroke: color("#FFFFFF", 0.08), lineWidth: 1)
    ctx.restoreGState()
}

func drawScene(_ ctx: CGContext, scene: Scene, index: Int, localT: Double) {
    drawGradient(ctx)
    let progress = CGFloat(min(max(localT / scene.duration, 0), 1))
    let bob = sin(progress * .pi * 2) * 10
    let fade = min(min(progress / 0.12, (1 - progress) / 0.1), 1)

    drawText("LumaNox", in: CGRect(x: 86, y: 64, width: 260, height: 42), size: 31, weight: .bold, color: color("#EAF1FF"))
    drawText("Offline AI Privacy Safe", in: CGRect(x: 88, y: 106, width: 360, height: 34), size: 22, weight: .medium, color: color("#8EA2C0"))

    drawText(scene.eyebrow, in: CGRect(x: 86, y: 228, width: 700, height: 38), size: 25, weight: .bold, color: color("#62B1FF"))
    drawText(scene.title, in: CGRect(x: 84, y: 282, width: index == 0 ? 760 : 870, height: 210), size: index == 0 ? 90 : 62, weight: .bold, color: color("#EAF1FF"), lineSpacing: 0.96)
    drawText(scene.subtitle, in: CGRect(x: 88, y: index == 0 ? 466 : 500, width: 790, height: 124), size: 30, weight: .regular, color: color("#B7C8E2"), lineSpacing: 1.12)

    var chipX: CGFloat = 88
    var chipY: CGFloat = 665
    for (i, bullet) in scene.bullets.enumerated() {
        drawChip(ctx, bullet, x: chipX, y: chipY, accent: i == 0 ? color("#4A9EFF") : (i == 1 ? color("#21C277") : color("#E8C547")))
        chipY += 66
        if chipY > 820 {
            chipY = 665
            chipX += 390
        }
    }

    if let cta = scene.cta {
        fillRound(ctx, CGRect(x: 88, y: 886, width: 330, height: 66), 18, color("#4A9EFF"), stroke: color("#9BCBFF", 0.5))
        drawText(cta, in: CGRect(x: 116, y: 903, width: 278, height: 34), size: 25, weight: .bold, color: .white, align: .center)
    }

    let imgs = scene.images.compactMap { screenshots[$0] }
    switch imgs.count {
    case 1:
        drawPhone(ctx, img: imgs[0], rect: CGRect(x: 1218, y: 125 + bob, width: 432, height: 768), rotation: -0.015)
        fillRound(ctx, CGRect(x: 1112, y: 782, width: 520, height: 108), 28, color("#0C1523", 0.86), stroke: color("#223247"))
        drawText(index == 1 ? "A real local vault." : index == 2 ? "On-device privacy review." : index == 3 ? "Capture privately." : "Settings stay clear.", in: CGRect(x: 1148, y: 814, width: 448, height: 42), size: 28, weight: .semibold, color: color("#EAF1FF"), align: .center)
    case 2:
        drawPhone(ctx, img: imgs[0], rect: CGRect(x: 1065, y: 185 + bob, width: 360, height: 640), rotation: -0.055, alpha: 0.98)
        drawPhone(ctx, img: imgs[1], rect: CGRect(x: 1386, y: 122 - bob, width: 390, height: 694), rotation: 0.055)
    default:
        drawPhone(ctx, img: imgs[1], rect: CGRect(x: 1028, y: 252 + bob, width: 300, height: 533), rotation: -0.09, alpha: 0.78)
        drawPhone(ctx, img: imgs[2], rect: CGRect(x: 1480, y: 252 - bob, width: 300, height: 533), rotation: 0.09, alpha: 0.78)
        drawPhone(ctx, img: imgs[0], rect: CGRect(x: 1195, y: 110, width: 420, height: 746), rotation: 0)
    }

    if fade < 1 {
        ctx.setFillColor(color("#05080D", 1 - fade).cgColor)
        ctx.fill(CGRect(x: 0, y: 0, width: width, height: height))
    }
}

let writer = try AVAssetWriter(outputURL: videoURL, fileType: .mp4)
let input = AVAssetWriterInput(mediaType: .video, outputSettings: [
    AVVideoCodecKey: AVVideoCodecType.h264,
    AVVideoWidthKey: width,
    AVVideoHeightKey: height,
    AVVideoCompressionPropertiesKey: [
        AVVideoAverageBitRateKey: 9_000_000,
        AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel
    ]
])
input.expectsMediaDataInRealTime = false
let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [
    kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
    kCVPixelBufferWidthKey as String: width,
    kCVPixelBufferHeightKey as String: height
])
writer.add(input)
writer.startWriting()
writer.startSession(atSourceTime: .zero)

let totalFrames = Int(scenes.reduce(0.0) { $0 + $1.duration } * Double(fps))
var frameIndex = 0
for (sceneIndex, scene) in scenes.enumerated() {
    let frames = Int(scene.duration * Double(fps))
    for f in 0..<frames {
        while !input.isReadyForMoreMediaData {
            Thread.sleep(forTimeInterval: 0.005)
        }
        var maybeBuffer: CVPixelBuffer?
        CVPixelBufferPoolCreatePixelBuffer(nil, adaptor.pixelBufferPool!, &maybeBuffer)
        guard let buffer = maybeBuffer else { fatalError("Could not create pixel buffer") }
        CVPixelBufferLockBaseAddress(buffer, [])
        let base = CVPixelBufferGetBaseAddress(buffer)!
        let bytesPerRow = CVPixelBufferGetBytesPerRow(buffer)
        let ctx = CGContext(
            data: base,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: bytesPerRow,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue
        )!
        ctx.translateBy(x: 0, y: CGFloat(height))
        ctx.scaleBy(x: 1, y: -1)
        NSGraphicsContext.saveGraphicsState()
        NSGraphicsContext.current = NSGraphicsContext(cgContext: ctx, flipped: true)
        drawScene(ctx, scene: scene, index: sceneIndex, localT: Double(f) / Double(fps))
        NSGraphicsContext.restoreGraphicsState()
        CVPixelBufferUnlockBaseAddress(buffer, [])
        adaptor.append(buffer, withPresentationTime: CMTimeMultiply(frameDuration, multiplier: Int32(frameIndex)))
        frameIndex += 1
        if frameIndex % 150 == 0 {
            print("Rendered \(frameIndex)/\(totalFrames) frames")
        }
    }
}
input.markAsFinished()
let finishSemaphore = DispatchSemaphore(value: 0)
writer.finishWriting {
    finishSemaphore.signal()
}
finishSemaphore.wait()
if writer.status != .completed {
    fatalError("Video writer failed: \(String(describing: writer.error))")
}

if FileManager.default.fileExists(atPath: audioURL.path) {
    let mix = AVMutableComposition()
    let videoAsset = AVURLAsset(url: videoURL)
    let audioAsset = AVURLAsset(url: audioURL)
    guard let videoTrack = videoAsset.tracks(withMediaType: .video).first else {
        fatalError("Missing video track")
    }
    let compositionVideo = mix.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid)!
    try compositionVideo.insertTimeRange(CMTimeRange(start: .zero, duration: videoAsset.duration), of: videoTrack, at: .zero)
    compositionVideo.preferredTransform = videoTrack.preferredTransform
    if let audioTrack = audioAsset.tracks(withMediaType: .audio).first {
        let compositionAudio = mix.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid)!
        let audioDuration = min(audioAsset.duration.seconds, videoAsset.duration.seconds)
        try compositionAudio.insertTimeRange(CMTimeRange(start: .zero, duration: CMTime(seconds: audioDuration, preferredTimescale: 600)), of: audioTrack, at: .zero)
    }
    guard let exporter = AVAssetExportSession(asset: mix, presetName: AVAssetExportPresetHighestQuality) else {
        fatalError("Could not create exporter")
    }
    exporter.outputURL = finalURL
    exporter.outputFileType = .mp4
    let exportSemaphore = DispatchSemaphore(value: 0)
    exporter.exportAsynchronously {
        exportSemaphore.signal()
    }
    exportSemaphore.wait()
    if exporter.status != .completed {
        fatalError("Export failed: \(String(describing: exporter.error))")
    }
} else {
    try FileManager.default.copyItem(at: videoURL, to: finalURL)
}

print("Wrote \(finalURL.path)")
