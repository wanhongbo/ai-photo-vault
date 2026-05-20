import Foundation
import SwiftUI

enum LockStage: Equatable {
    case loading
    case setupEnter
    case setupConfirm
    case setupConfirmError
    case restoreLogin
    case unlock
}

struct LockUiState: Equatable {
    var stage: LockStage = .loading
    var enteredPin: String = ""
    var firstPin: String = ""
    var title: String = ""
    var subtitle: String = ""
    var stepLabel: String?
    var error: String?
    var success: Bool = false
    var unlockSuccess: Bool = false
    var biometricEnabled: Bool = false
    var showBiometricSetupPrompt: Bool = false
    var isLoading: Bool = false
    var restoreFailCount: Int = 0
    var showAbandonBackupEntry: Bool = false
}

@MainActor
final class LockViewModel: ObservableObject {
    @Published private(set) var state = LockUiState()

    private let securityStore = SecuritySettingsStore.shared
    private let appLock = AppLockManager.shared
    private let pinLength = 6

    func onAppear() {
        securityStore.reload()
        state = LockUiState(stage: .loading, title: L10n.commonLoading)
        Task { await resolveFirstLaunchBranch() }
    }

    private func resolveFirstLaunchBranch() async {
        let branch = FirstLaunchRouter.detect()
        switch branch {
        case .unlock:
            let bio = securityStore.biometricEnabled
            state = LockUiState(
                stage: .unlock,
                title: L10n.tr("lock_title"),
                subtitle: L10n.tr("lock_subtitle"),
                biometricEnabled: bio
            )
            #if DEBUG
            if let debugPin = ProcessInfo.processInfo.environment["LUMANOX_DEBUG_UNLOCK_PIN"],
               debugPin.count == pinLength {
                handlePinComplete(debugPin)
            }
            #endif
        case .restoreLogin:
            state = restoreLoginState()
        case .fresh:
            state = freshSetupState()
        }
    }

    func onDigit(_ digit: String) {
        guard state.stage != .loading, !state.success, !state.isLoading else { return }
        guard state.enteredPin.count < pinLength else { return }
        var s = state
        s.enteredPin += digit
        s.error = nil
        state = s
        if s.enteredPin.count == pinLength {
            handlePinComplete(s.enteredPin)
        }
    }

    func onDeleteLast() {
        guard !state.enteredPin.isEmpty, !state.isLoading else { return }
        var s = state
        s.enteredPin.removeLast()
        s.error = nil
        state = s
    }

    func resetSetup() {
        state = freshSetupState()
    }

    func onBiometricUnlockSuccess() {
        appLock.onUnlockSucceeded()
        state.unlockSuccess = true
    }

    func onBiometricUnlockFailed(_ message: String) {
        state.error = message
    }

    func confirmBiometricSetup(enable: Bool) {
        if enable {
            try? securityStore.setBiometricEnabled(true)
        }
        state.showBiometricSetupPrompt = false
        finishUnlockAfterSetup()
    }

    func skipBiometricSetup() {
        state.showBiometricSetupPrompt = false
        finishUnlockAfterSetup()
    }

    /// 重装后无书签：用户选择含 `backup.dat` 的文件夹。
    func onRestoreFolderPicked(_ url: URL) {
        guard url.startAccessingSecurityScopedResource() else {
            state.error = L10n.tr("backup_folder_pick_failed")
            return
        }
        defer { url.stopAccessingSecurityScopedResource() }
        do {
            try ExternalBackupLocation.persistFolder(url)
            ExternalBackupLocation.sanitizeOnStartup()
            if ExternalBackupLocation.findAutoBackup() {
                state = restoreLoginState()
            } else {
                state = LockUiState(
                    stage: .setupEnter,
                    title: L10n.tr("lock_setup_enter_title"),
                    subtitle: L10n.tr("lock_restore_no_backup_in_folder"),
                    stepLabel: "1 / 2",
                    error: L10n.tr("lock_restore_no_backup_in_folder")
                )
            }
        } catch {
            state.error = error.localizedDescription
        }
    }

    func abandonBackupAndCreateFresh() {
        state = freshSetupState()
    }

    private func handlePinComplete(_ pin: String) {
        switch state.stage {
        case .setupEnter:
            state = LockUiState(
                stage: .setupConfirm,
                firstPin: pin,
                title: L10n.tr("lock_setup_confirm_title"),
                subtitle: L10n.tr("lock_setup_confirm_subtitle"),
                stepLabel: "2 / 2"
            )
        case .setupConfirm, .setupConfirmError:
            if pin == state.firstPin {
                do {
                    try securityStore.savePin(pin, enableBiometric: false)
                    appLock.refreshPinConfigured()
                    BackupKeyRefresh.refresh(pin: pin, force: true, triggerAutoBackup: true)
                    let canBio = BiometricAuthService.shared.availability().canEvaluate
                    state = LockUiState(
                        stage: .setupConfirm,
                        title: L10n.tr("lock_success_title"),
                        subtitle: L10n.tr("lock_success_subtitle"),
                        success: true,
                        showBiometricSetupPrompt: canBio
                    )
                    if !canBio {
                        finishUnlockAfterSetup()
                    }
                } catch {
                    state.error = error.localizedDescription
                }
            } else {
                state = LockUiState(
                    stage: .setupConfirmError,
                    title: L10n.tr("lock_error_mismatch_title"),
                    subtitle: L10n.tr("lock_error_mismatch_subtitle"),
                    error: L10n.tr("lock_error_mismatch")
                )
            }
        case .restoreLogin:
            Task { await attemptRestoreLogin(pin: pin) }
        case .unlock:
            if securityStore.verifyPin(pin) {
                try? securityStore.resetFailCount()
                BackupKeyRefresh.refresh(pin: pin, force: false, triggerAutoBackup: false)
                appLock.onUnlockSucceeded()
                state.unlockSuccess = true
            } else {
                let fails = (try? securityStore.recordFailedAttempt()) ?? 1
                state = LockUiState(
                    stage: .unlock,
                    title: L10n.tr("lock_title"),
                    subtitle: L10n.tr("lock_subtitle"),
                    error: L10n.tr("lock_error_wrong_pin", fails),
                    biometricEnabled: securityStore.biometricEnabled
                )
            }
        default:
            break
        }
    }

    private func attemptRestoreLogin(pin: String) async {
        var s = state
        s.isLoading = true
        s.error = nil
        s.enteredPin = ""
        state = s

        let result = await LocalBackupService.shared.restoreFromAutoPackage(pin: pin)

        if result.success {
            do {
                try securityStore.savePin(pin, enableBiometric: false)
                appLock.refreshPinConfigured()
                BackupKeyRefresh.refresh(pin: pin, force: true, triggerAutoBackup: false)
                await VaultStore.shared.loadSnapshot()
                await AutoBackupScheduler.runOnceNow(reason: .manualRestoreSync)
                state = LockUiState(
                    stage: .restoreLogin,
                    title: L10n.tr("lock_success_title"),
                    subtitle: L10n.tr("restore_result_success"),
                    success: true,
                    unlockSuccess: true,
                    restoreFailCount: 0
                )
            } catch {
                state = LockUiState(
                    stage: .restoreLogin,
                    title: L10n.tr("lock_restore_title"),
                    subtitle: L10n.tr("lock_restore_subtitle"),
                    error: error.localizedDescription,
                    isLoading: false
                )
            }
        } else {
            let nextFail = state.restoreFailCount + 1
            state = LockUiState(
                stage: .restoreLogin,
                enteredPin: "",
                title: L10n.tr("lock_restore_title"),
                subtitle: L10n.tr("lock_restore_subtitle"),
                error: result.message.isEmpty ? L10n.tr("restore_error_wrong_pin") : result.message,
                isLoading: false,
                restoreFailCount: nextFail,
                showAbandonBackupEntry: nextFail >= 3
            )
        }
    }

    private func restoreLoginState() -> LockUiState {
        LockUiState(
            stage: .restoreLogin,
            title: L10n.tr("lock_restore_title"),
            subtitle: L10n.tr("lock_restore_subtitle")
        )
    }

    private func freshSetupState() -> LockUiState {
        LockUiState(
            stage: .setupEnter,
            title: L10n.tr("lock_setup_enter_title"),
            subtitle: L10n.tr("lock_setup_enter_subtitle"),
            stepLabel: "1 / 2"
        )
    }

    private func finishUnlockAfterSetup() {
        appLock.onUnlockSucceeded()
        state.unlockSuccess = true
    }
}
