import SwiftUI

struct LNPressableButtonStyle: ButtonStyle {
    var scale: CGFloat = 0.97
    var pressedOpacity: Double = 0.84
    var pressedBrightness: Double = 0.04
    var pressedOverlayOpacity: Double = 0
    var cornerRadius: CGFloat?

    func makeBody(configuration: Configuration) -> some View {
        LNPressableButtonBody(
            configuration: configuration,
            scale: scale,
            pressedOpacity: pressedOpacity,
            pressedBrightness: pressedBrightness,
            pressedOverlayOpacity: pressedOverlayOpacity,
            cornerRadius: cornerRadius
        )
    }
}

private struct LNPressableButtonBody: View {
    let configuration: ButtonStyleConfiguration
    let scale: CGFloat
    let pressedOpacity: Double
    let pressedBrightness: Double
    let pressedOverlayOpacity: Double
    let cornerRadius: CGFloat?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.isEnabled) private var isEnabled

    private var isPressed: Bool {
        isEnabled && configuration.isPressed
    }

    var body: some View {
        configuration.label
            .scaleEffect(reduceMotion || !isPressed ? 1 : scale)
            .opacity(isPressed ? pressedOpacity : 1)
            .brightness(isPressed ? pressedBrightness : 0)
            .overlay {
                if isPressed, pressedOverlayOpacity > 0 {
                    pressedOverlay
                }
            }
            .animation(reduceMotion ? nil : .easeOut(duration: 0.12), value: configuration.isPressed)
    }

    @ViewBuilder
    private var pressedOverlay: some View {
        let fill = Color.white.opacity(pressedOverlayOpacity)
        if let cornerRadius {
            RoundedRectangle(cornerRadius: cornerRadius)
                .fill(fill)
                .allowsHitTesting(false)
        } else {
            Rectangle()
                .fill(fill)
                .allowsHitTesting(false)
        }
    }
}

extension ButtonStyle where Self == LNPressableButtonStyle {
    static func lnPressable(
        scale: CGFloat = 0.97,
        pressedOpacity: Double = 0.84,
        pressedBrightness: Double = 0.04,
        pressedOverlayOpacity: Double = 0,
        cornerRadius: CGFloat? = nil
    ) -> LNPressableButtonStyle {
        LNPressableButtonStyle(
            scale: scale,
            pressedOpacity: pressedOpacity,
            pressedBrightness: pressedBrightness,
            pressedOverlayOpacity: pressedOverlayOpacity,
            cornerRadius: cornerRadius
        )
    }
}
