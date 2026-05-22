import SwiftUI

enum LNButtonVariant {
    case primary
    case secondary
    case danger
}

struct LNButton: View {
    let title: String
    let variant: LNButtonVariant
    var enabled: Bool = true
    var loading: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                if loading {
                    ProgressView()
                        .tint(foreground)
                } else {
                    Text(title)
                        .font(LNTypography.button())
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .foregroundStyle(foreground)
            .background(background)
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
        }
        .buttonStyle(.lnPressable(scale: 0.985, pressedOpacity: 0.88))
        .disabled(!enabled || loading)
        .accessibilityIdentifier("ln_button_\(title)")
    }

    private var height: CGFloat {
        variant == .secondary ? LNSpacing.buttonHeightSecondary : LNSpacing.buttonHeightPrimary
    }

    private var cornerRadius: CGFloat {
        variant == .secondary ? 14 : 16
    }

    private var background: Color {
        guard enabled && !loading else { return LNColor.buttonDisabledBg }
        switch variant {
        case .primary: return LNColor.buttonPrimaryBg
        case .secondary: return LNColor.buttonSecondaryBg
        case .danger: return LNColor.buttonDangerBg
        }
    }

    private var foreground: Color {
        guard enabled && !loading else { return LNColor.buttonDisabledFg }
        switch variant {
        case .primary: return LNColor.buttonPrimaryFg
        case .secondary: return LNColor.buttonSecondaryFg
        case .danger: return LNColor.buttonDangerFg
        }
    }
}
