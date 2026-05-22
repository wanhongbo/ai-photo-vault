import SwiftUI

struct LNSettingsGroupCard<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(LNTypography.titleMedium())
                .foregroundStyle(LNColor.title)
            content()
        }
        .lnCard()
    }
}

struct LNSettingsRow: View {
    let title: String
    var subtitle: String? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(LNTypography.titleMedium())
                        .foregroundStyle(LNColor.title)
                    if let subtitle {
                        Text(subtitle)
                            .font(LNTypography.labelMedium())
                            .foregroundStyle(LNColor.subtitle)
                    }
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundStyle(LNColor.subtitle)
            }
            .frame(maxWidth: .infinity, minHeight: LNSpacing.minTouchTarget, alignment: .leading)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(LNColor.sectionBg.opacity(0.5))
            .clipShape(RoundedRectangle(cornerRadius: LNRadius.settingsRow))
            .contentShape(RoundedRectangle(cornerRadius: LNRadius.settingsRow))
        }
        .buttonStyle(.lnPressable())
        .accessibilityIdentifier("ln_settings_row_\(title)")
    }
}

struct LNSettingsSwitchRow: View {
    let title: String
    let subtitle: String
    @Binding var isOn: Bool

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(LNTypography.titleMedium()).foregroundStyle(LNColor.title)
                Text(subtitle).font(LNTypography.labelMedium()).foregroundStyle(LNColor.subtitle)
            }
            Spacer()
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(LNColor.brandBlue)
        }
        .frame(minHeight: LNSpacing.minTouchTarget)
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(LNColor.sectionBg.opacity(0.5))
        .clipShape(RoundedRectangle(cornerRadius: LNRadius.settingsRow))
    }
}

struct LNSettingsDangerRow: View {
    let title: String
    let subtitle: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(LNTypography.titleMedium()).foregroundStyle(LNColor.error)
                    Text(subtitle).font(LNTypography.labelMedium()).foregroundStyle(LNColor.error.opacity(0.8))
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(LNColor.error)
            }
            .frame(maxWidth: .infinity, minHeight: LNSpacing.minTouchTarget, alignment: .leading)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(LNColor.buttonDangerBg)
            .clipShape(RoundedRectangle(cornerRadius: LNRadius.settingsRow))
            .contentShape(RoundedRectangle(cornerRadius: LNRadius.settingsRow))
        }
        .buttonStyle(.lnPressable())
    }
}
