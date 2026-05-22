import SwiftUI

struct LNPressableButtonStyle: ButtonStyle {
    var scale: CGFloat = 0.97
    var pressedOpacity: Double = 0.84
    var pressedBrightness: Double = 0.04

    func makeBody(configuration: Configuration) -> some View {
        LNPressableButtonBody(
            configuration: configuration,
            scale: scale,
            pressedOpacity: pressedOpacity,
            pressedBrightness: pressedBrightness
        )
    }
}

private struct LNPressableButtonBody: View {
    let configuration: ButtonStyleConfiguration
    let scale: CGFloat
    let pressedOpacity: Double
    let pressedBrightness: Double

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
            .animation(reduceMotion ? nil : .easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

extension ButtonStyle where Self == LNPressableButtonStyle {
    static func lnPressable(
        scale: CGFloat = 0.97,
        pressedOpacity: Double = 0.84,
        pressedBrightness: Double = 0.04
    ) -> LNPressableButtonStyle {
        LNPressableButtonStyle(
            scale: scale,
            pressedOpacity: pressedOpacity,
            pressedBrightness: pressedBrightness
        )
    }
}
