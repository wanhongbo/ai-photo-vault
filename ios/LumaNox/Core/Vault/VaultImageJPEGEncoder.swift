import UIKit

enum VaultImageJPEGEncoder {
    static func opaqueJPEGData(
        from image: UIImage,
        compressionQuality: CGFloat,
        backgroundColor: UIColor = .black
    ) -> Data? {
        guard let opaqueImage = opaqueImage(from: image, backgroundColor: backgroundColor) else {
            return image.jpegData(compressionQuality: compressionQuality)
        }
        return opaqueImage.jpegData(compressionQuality: compressionQuality)
    }

    static func opaqueImage(from image: UIImage, backgroundColor: UIColor = .black) -> UIImage? {
        guard image.size.width > 0, image.size.height > 0 else { return nil }

        let format = UIGraphicsImageRendererFormat()
        format.scale = image.scale > 0 ? image.scale : 1
        format.opaque = true

        return UIGraphicsImageRenderer(size: image.size, format: format).image { context in
            backgroundColor.setFill()
            context.fill(CGRect(origin: .zero, size: image.size))
            image.draw(in: CGRect(origin: .zero, size: image.size))
        }
    }
}
