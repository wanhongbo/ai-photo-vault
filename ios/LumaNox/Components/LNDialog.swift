import SwiftUI

struct LNDialog: View {
    let title: String
    let message: String
    let confirmTitle: String
    var dismissTitle: String? = nil
    var confirmVariant: LNButtonVariant = .primary
    let onConfirm: () -> Void
    var onDismiss: (() -> Void)? = nil

    var body: some View {
        ZStack {
            LNColor.scrim.ignoresSafeArea()
            dialogCard
                .frame(maxWidth: LNSpacing.dialogMaxWidth)
                .padding(.horizontal, LNSpacing.screenHorizontal)
        }
        .accessibilityIdentifier("ln_dialog")
    }

    private var dialogCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title)
                .font(LNTypography.dialogTitle())
                .foregroundStyle(LNColor.title)
            Text(message)
                .font(LNTypography.dialogBody())
                .foregroundStyle(LNColor.dialogBody)
                .padding(.top, LNSpacing.dialogBodyTopGap)

            HStack(spacing: LNSpacing.dialogButtonGap) {
                if let dismissTitle, let onDismiss {
                    LNButton(title: dismissTitle, variant: .secondary, action: onDismiss)
                }
                LNButton(title: confirmTitle, variant: confirmVariant, action: onConfirm)
            }
            .padding(.top, LNSpacing.dialogButtonTopGap)
        }
        .padding(LNSpacing.dialogPadding)
        .background(LNColor.dialogBg)
        .clipShape(RoundedRectangle(cornerRadius: LNRadius.dialog))
        .overlay(
            RoundedRectangle(cornerRadius: LNRadius.dialog)
                .stroke(LNColor.stroke, lineWidth: 1)
        )
    }
}

struct LNInputDialog: View {
    let title: String
    @Binding var text: String
    let placeholder: String
    let confirmTitle: String
    let dismissTitle: String
    let onConfirm: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        ZStack {
            LNColor.scrim.ignoresSafeArea()
            VStack(spacing: 0) {
                Text(title)
                    .font(LNTypography.dialogTitle())
                    .foregroundStyle(LNColor.title)
                    .frame(maxWidth: .infinity)

                TextField(placeholder, text: $text)
                    .textFieldStyle(.plain)
                    .padding(.horizontal, 12)
                    .frame(height: 52)
                    .background(LNColor.sectionBg)
                    .clipShape(RoundedRectangle(cornerRadius: LNRadius.homeThumb))
                    .overlay(
                        RoundedRectangle(cornerRadius: LNRadius.homeThumb)
                            .stroke(LNColor.stroke, lineWidth: 1)
                    )
                    .foregroundStyle(LNColor.title)
                    .padding(.top, LNSpacing.dialogBodyTopGap)

                HStack(spacing: LNSpacing.dialogButtonGap) {
                    LNButton(title: dismissTitle, variant: .secondary, action: onDismiss)
                    LNButton(
                        title: confirmTitle,
                        variant: .primary,
                        enabled: !text.trimmingCharacters(in: .whitespaces).isEmpty,
                        action: onConfirm
                    )
                }
                .padding(.top, LNSpacing.dialogButtonTopGap)
            }
            .padding(LNSpacing.dialogPadding)
            .frame(maxWidth: LNSpacing.dialogMaxWidth)
            .background(LNColor.dialogBg)
            .clipShape(RoundedRectangle(cornerRadius: LNRadius.dialog))
            .overlay(
                RoundedRectangle(cornerRadius: LNRadius.dialog)
                    .stroke(LNColor.stroke, lineWidth: 1)
            )
            .padding(.horizontal, LNSpacing.screenHorizontal)
        }
        .accessibilityIdentifier("ln_input_dialog")
    }
}

struct LNPinDialog: View {
    let title: String
    let subtitle: String
    let confirmTitle: String
    let dismissTitle: String
    var errorMessage: String? = nil
    var busy: Bool = false
    let onConfirm: (String) -> Void
    let onDismiss: () -> Void

    @State private var pin = ""
    private let pinLength = 6

    var body: some View {
        ZStack {
            LNColor.scrim.ignoresSafeArea()
            VStack(alignment: .leading, spacing: 0) {
                Text(title).font(LNTypography.dialogTitle()).foregroundStyle(LNColor.title)
                Text(subtitle).font(LNTypography.dialogBody()).foregroundStyle(LNColor.dialogBody)
                    .padding(.top, LNSpacing.dialogBodyTopGap)

                HStack(spacing: 10) {
                    ForEach(0..<pinLength, id: \.self) { i in
                        Circle()
                            .fill(i < pin.count ? LNColor.brandBlue : LNColor.stroke)
                            .frame(width: 12, height: 12)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)

                if let errorMessage {
                    Text(errorMessage)
                        .font(LNTypography.dialogBody())
                        .foregroundStyle(LNColor.error)
                }

                TextField("", text: $pin)
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .opacity(0.01)
                    .frame(height: 1)
                    .onChange(of: pin) { newValue in
                        let filtered = String(newValue.filter(\.isNumber).prefix(pinLength))
                        if filtered != pin { pin = filtered }
                        if pin.count == pinLength { onConfirm(pin) }
                    }

                HStack(spacing: LNSpacing.dialogButtonGap) {
                    LNButton(title: dismissTitle, variant: .secondary, enabled: !busy, action: onDismiss)
                    LNButton(title: confirmTitle, variant: .primary, enabled: !busy, loading: busy) {}
                }
                .padding(.top, LNSpacing.dialogButtonTopGap)
            }
            .padding(LNSpacing.dialogPadding)
            .frame(maxWidth: LNSpacing.dialogMaxWidth)
            .background(LNColor.dialogBg)
            .clipShape(RoundedRectangle(cornerRadius: LNRadius.dialog))
            .overlay(RoundedRectangle(cornerRadius: LNRadius.dialog).stroke(LNColor.stroke, lineWidth: 1))
            .padding(.horizontal, LNSpacing.screenHorizontal)
        }
        .accessibilityIdentifier("ln_pin_dialog")
    }
}

struct LNMediaInfoDialog: View {
    let title: String
    let items: [(String, String)]
    let confirmTitle: String
    let onDismiss: () -> Void

    var body: some View {
        ZStack {
            LNColor.scrim.ignoresSafeArea()
            VStack(alignment: .leading, spacing: 0) {
                Text(title).font(LNTypography.dialogTitle()).foregroundStyle(LNColor.title)
                VStack(spacing: 8) {
                    ForEach(Array(items.enumerated()), id: \.offset) { _, pair in
                        HStack(alignment: .top) {
                            Text(pair.0)
                                .font(LNTypography.dialogBody())
                                .foregroundStyle(LNColor.dialogBody.opacity(0.7))
                                .frame(maxWidth: .infinity, alignment: .leading)
                            Text(pair.1)
                                .font(LNTypography.dialogBody())
                                .foregroundStyle(LNColor.dialogBody)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                }
                .padding(.top, LNSpacing.dialogBodyTopGap)
                LNButton(title: confirmTitle, variant: .secondary, action: onDismiss)
                    .padding(.top, LNSpacing.dialogButtonTopGap)
            }
            .padding(LNSpacing.dialogPadding)
            .frame(maxWidth: LNSpacing.dialogMaxWidth)
            .background(LNColor.dialogBg)
            .clipShape(RoundedRectangle(cornerRadius: LNRadius.dialog))
            .overlay(RoundedRectangle(cornerRadius: LNRadius.dialog).stroke(LNColor.stroke, lineWidth: 1))
            .padding(.horizontal, LNSpacing.screenHorizontal)
        }
        .accessibilityIdentifier("ln_media_info_dialog")
    }
}
