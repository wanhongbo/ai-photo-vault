import AVFoundation
import Foundation
import ImageIO
import UniformTypeIdentifiers

struct MediaMetadataDetails: Hashable {
    var width: Int?
    var height: Int?
    var durationMs: Int64?
    var mimeType: String?
    var uti: String?
}

enum MediaMetadataExtractor {
    static func extract(from url: URL) -> MediaMetadataDetails {
        let type = UTType(filenameExtension: url.pathExtension)
        var details = MediaMetadataDetails(
            width: nil,
            height: nil,
            durationMs: nil,
            mimeType: type?.preferredMIMEType,
            uti: type?.identifier
        )

        if fillImageDetails(url: url, details: &details) {
            return details
        }
        fillVideoDetails(url: url, details: &details)
        return details
    }

    @discardableResult
    private static func fillImageDetails(url: URL, details: inout MediaMetadataDetails) -> Bool {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any] else {
            return false
        }
        details.width = intValue(properties[kCGImagePropertyPixelWidth])
        details.height = intValue(properties[kCGImagePropertyPixelHeight])
        if let typeIdentifier = CGImageSourceGetType(source) as String? {
            details.uti = typeIdentifier
            details.mimeType = UTType(typeIdentifier)?.preferredMIMEType ?? details.mimeType
        }
        return details.width != nil || details.height != nil
    }

    private static func fillVideoDetails(url: URL, details: inout MediaMetadataDetails) {
        let asset = AVURLAsset(url: url)
        if let track = asset.tracks(withMediaType: .video).first {
            let transformed = track.naturalSize.applying(track.preferredTransform)
            details.width = Int(abs(transformed.width).rounded())
            details.height = Int(abs(transformed.height).rounded())
        }
        let seconds = CMTimeGetSeconds(asset.duration)
        if seconds.isFinite, seconds > 0 {
            details.durationMs = Int64((seconds * 1000).rounded())
        }
    }

    private static func intValue(_ value: Any?) -> Int? {
        if let number = value as? NSNumber {
            return number.intValue
        }
        return value as? Int
    }
}
