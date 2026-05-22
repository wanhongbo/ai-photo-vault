import SwiftUI
import UniformTypeIdentifiers

struct LockView: View {
    @EnvironmentObject private var router: AppRouter
    @StateObject private var viewModel = LockViewModel()
    @State private var biometricDismissedAt: Date?
    @State private var showAbandonDialog = false
    @State private var showRestoreFolderPicker = false

    private let pinLength = 6

    var body: some View {
        ZStack {
            LNColor.lockBg.ignoresSafeArea()
            if viewModel.state.stage == .loading {
                ProgressView().tint(LNColor.brandBlue)
            } else {
                lockContent
            }

            if viewModel.state.isLoading {
                Color.black.opacity(0.35).ignoresSafeArea()
                ProgressView(L10n.commonLoading)
                    .tint(LNColor.brandBlue)
            }

            if viewModel.state.showBiometricSetupPrompt {
                LNDialog(
                    title: L10n.tr("lock_biometric_setup_title"),
                    message: L10n.tr("lock_biometric_setup_message"),
                    confirmTitle: L10n.tr("lock_biometric_setup_confirm"),
                    dismissTitle: L10n.tr("lock_biometric_setup_later"),
                    onConfirm: { viewModel.confirmBiometricSetup(enable: true) },
                    onDismiss: { viewModel.skipBiometricSetup() }
                )
            }
        }
        .onAppear { viewModel.onAppear() }
        .fileImporter(
            isPresented: $showRestoreFolderPicker,
            allowedContentTypes: [.folder],
            allowsMultipleSelection: false
        ) { result in
            if case .success(let urls) = result, let url = urls.first {
                viewModel.onRestoreFolderPicked(url)
            }
        }
        .overlay {
            if showAbandonDialog {
                LNDialog(
                    title: L10n.tr("lock_abandon_backup_title"),
                    message: L10n.tr("lock_abandon_backup_message"),
                    confirmTitle: L10n.tr("lock_abandon_confirm"),
                    dismissTitle: L10n.tr("lock_abandon_retry"),
                    onConfirm: {
                        showAbandonDialog = false
                        viewModel.abandonBackupAndCreateFresh()
                    },
                    onDismiss: { showAbandonDialog = false }
                )
            }
        }
        .onChange(of: viewModel.state.unlockSuccess) { success in
            if success { router.unlock() }
        }
        .onChange(of: viewModel.state.stage) { stage in
            if stage == .unlock, viewModel.state.biometricEnabled {
                tryAutoBiometric()
            }
        }
        .accessibilityIdentifier("lock_view")
    }

    private var lockContent: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            let height = proxy.size.height
            let keypadWidth: CGFloat = 305
            let keyTop = min(max(height * 0.45, 382), height - 470)

            ZStack(alignment: .top) {
                topContent
                    .frame(width: width)
                    .position(x: width / 2, y: min(248, height * 0.30))

                if !viewModel.state.success {
                    keypad
                        .frame(width: keypadWidth)
                        .position(x: width / 2, y: keyTop + 174)

                    lockActionButtons
                        .frame(width: width - 32)
                        .position(x: width / 2, y: min(keyTop + 394, height - 58))
                }
            }
            .frame(width: width, height: height)
        }
        .ignoresSafeArea()
    }

    private var topContent: some View {
        VStack(spacing: 14) {
            Image(systemName: stateIconName)
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(stateIconColor)
                .frame(width: 86, height: 86)
                .background(stateIconColor.opacity(0.18))
                .clipShape(RoundedRectangle(cornerRadius: 26))
                .overlay(RoundedRectangle(cornerRadius: 26).stroke(stateIconColor.opacity(0.85), lineWidth: 1))

            if let step = viewModel.state.stepLabel {
                Text(step)
                    .font(LNTypography.labelMedium())
                    .foregroundStyle(LNColor.subtitle)
            }
            Text(viewModel.state.title)
                .font(LNTypography.pinTitle())
                .foregroundStyle(LNColor.title)
            Text(viewModel.state.subtitle)
                .font(LNTypography.bodyMedium())
                .foregroundStyle(LNColor.subtitle)
                .multilineTextAlignment(.center)
                .lineSpacing(2)
                .frame(width: 300)

            if !viewModel.state.success {
                pinDots
                if let error = viewModel.state.error {
                    Text(error)
                        .font(LNTypography.bodyMedium())
                        .foregroundStyle(LNColor.error)
                        .multilineTextAlignment(.center)
                        .frame(width: 320)
                }
                if !SecuritySettingsStore.shared.hasPinConfigured,
                   viewModel.state.stage != .loading,
                   viewModel.state.stage != .restoreLogin {
                    Text(L10n.tr("lock_setup_required_hint"))
                        .font(LNTypography.labelMedium())
                        .foregroundStyle(LNColor.amberWarning)
                        .multilineTextAlignment(.center)
                        .frame(width: 320)
                }
            }
        }
    }

    private var pinDots: some View {
        HStack(spacing: 12) {
            ForEach(0..<pinLength, id: \.self) { i in
                Circle()
                    .fill(i < viewModel.state.enteredPin.count ? dotColor : Color.clear)
                    .frame(width: 12, height: 12)
                    .overlay(Circle().stroke(dotColor, lineWidth: 1.5))
            }
        }
    }

    private var keypad: some View {
        VStack(spacing: 12) {
            ForEach([[ "1", "2", "3" ], [ "4", "5", "6" ], [ "7", "8", "9" ]], id: \.self) { row in
                HStack(spacing: 16) {
                    ForEach(row, id: \.self) { key in
                        keypadButton(key)
                    }
                }
            }
            HStack(spacing: 16) {
                keypadButton("camera")
                keypadButton("0")
                keypadButton("delete")
            }
            if viewModel.state.stage == .unlock && viewModel.state.biometricEnabled {
                biometricButton
            }
        }
    }

    @ViewBuilder
    private var lockActionButtons: some View {
        if viewModel.state.stage == .setupConfirmError {
            LNButton(title: L10n.tr("lock_retry_setup"), variant: .secondary) {
                viewModel.resetSetup()
            }
        } else if viewModel.state.stage == .setupEnter {
            LNButton(title: L10n.tr("lock_restore_pick_folder"), variant: .secondary) {
                showRestoreFolderPicker = true
            }
        } else if viewModel.state.stage == .restoreLogin, viewModel.state.showAbandonBackupEntry {
            LNButton(title: L10n.tr("lock_abandon_backup_action"), variant: .secondary) {
                showAbandonDialog = true
            }
        }
    }

    private func keypadButton(_ key: String) -> some View {
        Button {
            switch key {
            case "delete": viewModel.onDeleteLast()
            case "camera": router.openPrivateCamera()
            default: viewModel.onDigit(key)
            }
        } label: {
            ZStack {
                Circle()
                    .fill(Color(hex: 0x131C29))
                    .overlay(Circle().stroke(LNColor.brandBlue, lineWidth: 1.5))
                if key == "camera" {
                    Image(systemName: "camera")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(LNColor.title)
                } else if key == "delete" {
                    Image(systemName: "delete.left")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(LNColor.title)
                } else {
                    Text(key)
                        .font(LNTypography.pinDigit())
                        .foregroundStyle(LNColor.title)
                }
            }
            .frame(width: 78, height: 78)
        }
        .buttonStyle(.lnPressable(scale: 0.94, pressedOpacity: 0.78))
        .disabled(viewModel.state.success || viewModel.state.isLoading)
    }

    private var biometricButton: some View {
        Button {
            Task { await runBiometric(userInitiated: true) }
        } label: {
            Label(L10n.tr("lock_biometric_button"), systemImage: "faceid")
                .font(LNTypography.titleMedium())
                .foregroundStyle(LNColor.brandBlue)
                .frame(minHeight: LNSpacing.minTouchTarget)
        }
        .buttonStyle(.lnPressable(scale: 0.98, pressedOpacity: 0.78))
        .accessibilityIdentifier("lock_biometric_button")
    }

    private var dotColor: Color {
        viewModel.state.error == nil && viewModel.state.stage != .setupConfirmError ? LNColor.brandBlue : LNColor.error
    }

    private var stateIconName: String {
        viewModel.state.success ? "checkmark" : "lock"
    }

    private var stateIconColor: Color {
        viewModel.state.success ? LNColor.success : LNColor.brandBlue
    }

    private func tryAutoBiometric() {
        guard viewModel.state.stage == .unlock else { return }
        if let dismissed = biometricDismissedAt, Date().timeIntervalSince(dismissed) < 4 { return }
        Task { await runBiometric(userInitiated: false) }
    }

    private func runBiometric(userInitiated: Bool) async {
        let availability = BiometricAuthService.shared.availability()
        guard availability.canEvaluate else {
            if userInitiated {
                viewModel.onBiometricUnlockFailed(availability.errorMessage ?? L10n.tr("lock_biometric_unavailable"))
            }
            return
        }
        let result = await BiometricAuthService.shared.authenticate(
            reason: L10n.tr("lock_biometric_prompt_subtitle")
        )
        switch result {
        case .success:
            viewModel.onBiometricUnlockSuccess()
        case .failure(let error):
            biometricDismissedAt = Date()
            if userInitiated {
                viewModel.onBiometricUnlockFailed(error.localizedDescription)
            }
        }
    }
}
