import SwiftUI

enum LNColor {
    static let brandBlue = Color(hex: 0x4A9EFF)
    static let bgBottom = Color(hex: 0x05080D)
    static let bgTop = Color(hex: 0x0B1324)
    static let sectionBg = Color(hex: 0x0C1523)
    static let emptyCardBg = Color(hex: 0x0E1624)
    static let emptyIconBg = Color(hex: 0x4A9EFF, alpha: 0.12)
    static let title = Color(hex: 0xEAF1FF)
    static let subtitle = Color(hex: 0x8EA2C0)
    static let navItemIdle = Color(hex: 0x7E90AB)
    static let navItemActive = Color(hex: 0xB7D7FF)
    static let stroke = Color(hex: 0x223247)
    static let strokeStrong = Color(hex: 0x243348)
    static let navBarBg = Color(hex: 0x0E1726)
    static let dialogBg = Color(hex: 0x101722)
    static let dialogBody = Color(hex: 0x97A8C0)
    static let error = Color(hex: 0xFF4372)
    static let success = Color(hex: 0x21C277)
    static let amberWarning = Color(hex: 0xE8C547)
    static let cleanupOrange = Color(hex: 0xFFB547)
    static let allClearTeal = Color(hex: 0x5BC0D4)
    static let paywallGold = Color(hex: 0xE8C547)

    static let buttonPrimaryBg = brandBlue
    static let buttonPrimaryFg = Color.white
    static let buttonSecondaryBg = Color(hex: 0x1A202C)
    static let buttonSecondaryFg = title
    static let buttonDangerBg = Color(hex: 0x2A1820)
    static let buttonDangerFg = Color(hex: 0xFF6B8F)
    static let buttonDisabledBg = Color(hex: 0x2A3240)
    static let buttonDisabledFg = Color(hex: 0x7788A1)

    static let lockBg = Color(hex: 0x05080D)
    static let splashLeft = Color(hex: 0x0D1A2E)
    static let splashRight = Color(hex: 0x0D0D0D)
    static let paywallTop = Color(hex: 0x070A10)
    static let paywallBottom = Color.black

    static let aiGradStart = Color(hex: 0x1A3A6E)
    static let aiGradEnd = Color(hex: 0x0D1B2E)
    static let aiSensitiveStart = Color(hex: 0x6E4A1A)
    static let aiAllClearStart = Color(hex: 0x1A5A6E)
    static let aiBlurIconBg = Color(hex: 0xE8C547, alpha: 0.20)
    static let aiClassifyIconBg = Color(hex: 0x4A9EFF, alpha: 0.20)
    static let aiDedup = Color(hex: 0xC850C0)
    static let aiDedupIconBg = Color(hex: 0xC850C0, alpha: 0.20)

    static let scrim = Color.black.opacity(0.8)
}

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        let r = Double((hex >> 16) & 0xFF) / 255
        let g = Double((hex >> 8) & 0xFF) / 255
        let b = Double(hex & 0xFF) / 255
        self.init(.sRGB, red: r, green: g, blue: b, opacity: alpha)
    }
}
