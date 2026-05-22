import SwiftUI
import UniformTypeIdentifiers
import WebKit

struct SettingsHomeView: View {
    @EnvironmentObject private var router: AppRouter
    @EnvironmentObject private var subscription: SubscriptionService

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(L10n.homeNavSettings)
                .font(LNTypography.displaySmall())
                .foregroundStyle(LNColor.title)
                .padding(.horizontal, LNSpacing.screenHorizontal)
                .padding(.top, 8)

            ScrollView {
                VStack(spacing: 12) {
                    subscriptionCard
                    backupBanner
                    settingsHubRow(
                        title: L10n.settingsSecurity,
                        subtitle: L10n.tr("settings_l1_security_sub"),
                        systemImage: "shield",
                        route: .settingsSecurity
                    )
                    settingsHubRow(
                        title: L10n.settingsBackup,
                        subtitle: L10n.tr("settings_l1_backup_sub"),
                        systemImage: "externaldrive.badge.timemachine",
                        route: .settingsBackupSync
                    )
                    settingsHubRow(
                        title: L10n.settingsData,
                        subtitle: L10n.tr("settings_l1_data_sub"),
                        systemImage: "internaldrive",
                        route: .settingsDataStorage
                    )
                    settingsHubRow(
                        title: L10n.settingsGeneral,
                        subtitle: L10n.tr("settings_l1_general_sub"),
                        systemImage: "slider.horizontal.3",
                        route: .settingsGeneral
                    )
                    settingsHubRow(
                        title: L10n.settingsAbout,
                        subtitle: L10n.tr("settings_l1_about_sub"),
                        systemImage: "questionmark.circle",
                        route: .settingsAbout
                    )
                }
                .padding(LNSpacing.screenHorizontal)
                .padding(.bottom, LNSpacing.homeNavBarHeight + 16)
            }
        }
        .accessibilityIdentifier("settings_home_view")
    }

    private var subscriptionCard: some View {
        Button { router.pushSettings(.settingsSubscription) } label: {
            HStack(spacing: 16) {
                Image(systemName: subscription.isPremium ? "crown.fill" : "diamond.fill")
                    .font(.system(size: 28, weight: .semibold))
                    .foregroundStyle(subscription.isPremium ? Color.white : Color.black)
                    .frame(width: 72, height: 72)
                    .background(subscription.isPremium ? LNColor.paywallGold : LNColor.brandBlue)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                VStack(alignment: .leading, spacing: 4) {
                    Text(subscription.isPremium ? L10n.tr("settings_l1_subscription_premium_title") : L10n.settingsSubscription)
                        .font(LNTypography.titleLarge())
                        .foregroundStyle(subscription.isPremium ? LNColor.paywallGold : LNColor.title)
                    Text(subscription.isPremium ? L10n.tr("settings_l1_subscription_premium_sub") : L10n.tr("settings_l1_subscription_sub"))
                        .font(LNTypography.labelMedium())
                        .foregroundStyle(LNColor.subtitle)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(subscription.isPremium ? LNColor.paywallGold : LNColor.navItemActive)
            }
            .padding(LNSpacing.cardPadding)
            .lnOutlinedCard(stroke: subscription.isPremium ? LNColor.paywallGold : LNColor.stroke)
        }
        .buttonStyle(.plain)
    }

    private var backupBanner: some View {
        HStack(spacing: 10) {
            Image(systemName: "shield.lefthalf.filled")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(LNColor.navItemActive)
            Text(L10n.tr("settings_backup_banner"))
                .font(LNTypography.labelMedium())
                .foregroundStyle(LNColor.subtitle)
                .lineSpacing(2)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .lnOutlinedCard(cornerRadius: LNRadius.homeCard)
    }

    private func settingsHubRow(title: String, subtitle: String, systemImage: String, route: AppRoute) -> some View {
        Button { router.pushSettings(route) } label: {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(LNColor.navItemActive)
                    .frame(width: 42, height: 42)
                    .background(LNColor.brandBlue.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: LNRadius.topBarButton))
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(LNTypography.titleMedium())
                        .foregroundStyle(LNColor.title)
                    Text(subtitle)
                        .font(LNTypography.labelMedium())
                        .foregroundStyle(LNColor.subtitle)
                        .lineLimit(2)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(LNColor.subtitle)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
            .frame(minHeight: 70)
            .lnOutlinedCard(cornerRadius: LNRadius.homeAlbumCard, fill: LNColor.sectionBg)
        }
        .buttonStyle(.plain)
    }
}

struct SettingsSubscriptionView: View {
    @EnvironmentObject private var router: AppRouter
    @EnvironmentObject private var subscription: SubscriptionService
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        LNScreenScaffold(title: L10n.settingsSubscription, onBack: { dismiss() }) {
            if subscription.isPremium {
                Text(L10n.tr("paywall_premium_active"))
                    .font(LNTypography.bodyMedium())
                    .foregroundStyle(LNColor.success)
            } else {
                Text(L10n.tr("paywall_subtitle"))
                    .font(LNTypography.bodyMedium())
                    .foregroundStyle(LNColor.subtitle)
            }
            LNButton(title: L10n.tr("paywall_action_upgrade"), variant: .primary) {
                router.present(.paywall(dismissable: true, source: PaywallSource.manual))
            }
            LNButton(title: L10n.tr("paywall_restore"), variant: .secondary) {
                Task {
                    _ = await subscription.restorePurchases()
                }
            }
        }
    }
}

struct SettingsSecurityView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var securityStore = SecuritySettingsStore.shared
    @State private var errorMessage: String?

    var body: some View {
        LNScreenScaffold(title: L10n.settingsSecurity, onBack: { dismiss() }) {
            LNSettingsGroupCard(title: L10n.tr("settings_sec_unlock")) {
                LNSettingsSwitchRow(
                    title: L10n.tr("settings_biometric_title"),
                    subtitle: L10n.tr("settings_biometric_desc"),
                    isOn: Binding(
                        get: { securityStore.biometricEnabled },
                        set: { updateBiometric($0) }
                    )
                )
                LNSettingsRow(title: L10n.changePinTitle) { router.pushSettings(.changePin) }
                settingsInfoRow(
                    title: L10n.tr("settings_auto_lock_title"),
                    subtitle: L10n.tr("settings_auto_lock_desc")
                )
            }
            LNSettingsGroupCard(title: L10n.tr("settings_sec_privacy")) {
                Text(L10n.tr("settings_sec_privacy_hint"))
                    .font(LNTypography.bodyMedium())
                    .foregroundStyle(LNColor.subtitle)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .overlay {
            if let errorMessage {
                LNDialog(
                    title: L10n.tr("settings_pin_error_title"),
                    message: errorMessage,
                    confirmTitle: L10n.tr("settings_pin_error_action"),
                    onConfirm: { self.errorMessage = nil }
                )
            }
        }
    }

    private func updateBiometric(_ enabled: Bool) {
        do {
            try securityStore.setBiometricEnabled(enabled)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func settingsInfoRow(title: String, subtitle: String) -> some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(LNTypography.titleMedium())
                    .foregroundStyle(LNColor.title)
                Text(subtitle)
                    .font(LNTypography.labelMedium())
                    .foregroundStyle(LNColor.subtitle)
            }
            Spacer()
            Image(systemName: "lock.rotation")
                .foregroundStyle(LNColor.subtitle)
        }
        .frame(minHeight: LNSpacing.minTouchTarget)
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(LNColor.sectionBg.opacity(0.5))
        .clipShape(RoundedRectangle(cornerRadius: LNRadius.settingsRow))
    }
}

struct SettingsBackupSyncView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel = SettingsBackupSyncViewModel()
    @State private var showFolderPicker = false

    var body: some View {
        LNScreenScaffold(title: L10n.settingsBackup, onBack: { dismiss() }) {
            backupStatusCard
            LNSettingsGroupCard(title: L10n.tr("backup_auto_section")) {
                LNSettingsSwitchRow(
                    title: L10n.tr("backup_auto_enable"),
                    subtitle: L10n.tr("backup_auto_enable_desc"),
                    isOn: Binding(
                        get: { viewModel.autoBackupEnabled },
                        set: { viewModel.setAutoBackupEnabled($0) }
                    )
                )
                LNSettingsRow(
                    title: L10n.tr("backup_folder_pick"),
                    subtitle: viewModel.folderPath ?? L10n.tr("backup_folder_not_set")
                ) {
                    showFolderPicker = true
                }
                if viewModel.folderWritable {
                    LNSettingsRow(title: L10n.tr("backup_auto_run_now")) {
                        Task { await viewModel.runAutoBackupNow(router: router) }
                    }
                    if viewModel.isRunning {
                        HStack(spacing: 8) {
                            ProgressView().tint(LNColor.brandBlue)
                            Text(L10n.commonLoading)
                                .font(LNTypography.bodyMedium())
                                .foregroundStyle(LNColor.subtitle)
                        }
                        .padding(.vertical, 8)
                    }
                    if let last = viewModel.lastBackupText {
                        Text(L10n.tr("backup_auto_last_fmt", last))
                            .font(LNTypography.bodyMedium())
                            .foregroundStyle(LNColor.subtitle)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    Text(L10n.tr("backup_auto_file_hint", ExternalBackupLocation.autoFileName))
                        .font(LNTypography.bodyMedium())
                        .foregroundStyle(LNColor.subtitle.opacity(0.8))
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                if viewModel.folderPath != nil {
                    LNSettingsRow(title: L10n.tr("backup_folder_clear"), subtitle: nil) {
                        viewModel.clearFolder()
                    }
                }
            }
            LNSettingsGroupCard(title: L10n.backupRestoreTitle) {
                LNSettingsRow(title: L10n.backupRestoreTitle) { router.pushSettings(.backupRestore) }
            }
        }
        .onAppear { viewModel.refresh() }
        .fileImporter(
            isPresented: $showFolderPicker,
            allowedContentTypes: [.folder],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                guard let url = urls.first else { return }
                viewModel.onFolderPicked(url, router: router)
            case .failure:
                viewModel.statusMessage = L10n.tr("backup_folder_pick_failed")
            }
        }
        .overlay {
            if let msg = viewModel.statusMessage {
                LNDialog(
                    title: L10n.tr("backup_auto_section"),
                    message: msg,
                    confirmTitle: L10n.commonOk,
                    onConfirm: { viewModel.statusMessage = nil }
                )
            }
        }
    }

    private var backupStatusCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                Image(systemName: viewModel.statusIconName)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(viewModel.statusTint)
                Text(viewModel.statusTitle)
                    .font(LNTypography.titleMedium())
                    .foregroundStyle(LNColor.title)
                Spacer()
            }
            Text(viewModel.statusDetail)
                .font(LNTypography.bodyMedium())
                .foregroundStyle(LNColor.subtitle)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let last = viewModel.lastBackupText {
                Text(L10n.tr("backup_auto_last_fmt", last))
                    .font(LNTypography.labelMedium())
                    .foregroundStyle(LNColor.subtitle.opacity(0.85))
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(LNSpacing.cardPadding)
        .background(LNColor.sectionBg)
        .clipShape(RoundedRectangle(cornerRadius: LNRadius.homeCard))
        .overlay(
            RoundedRectangle(cornerRadius: LNRadius.homeCard)
                .stroke(LNColor.stroke, lineWidth: 1)
        )
    }
}

struct SettingsDataStorageView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        LNScreenScaffold(title: L10n.settingsData, onBack: { dismiss() }) {
            LNSettingsRow(title: L10n.storageUsageTitle) { router.pushSettings(.storageUsage) }
            LNSettingsRow(title: L10n.tr("bulk_export_title")) {
                ExportRuntimeState.prepareSource(albumName: nil)
                router.pushSettings(.bulkExport)
            }
            LNSettingsRow(title: L10n.trashTitle) { router.pushSettings(.trashBin) }
        }
    }
}

struct SettingsGeneralView: View {
    @EnvironmentObject private var router: AppRouter
    @EnvironmentObject private var languageManager: LanguageManager
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        LNScreenScaffold(title: L10n.settingsGeneral, onBack: { dismiss() }) {
            LNSettingsRow(
                title: L10n.languageTitle,
                subtitle: languageManager.effectiveLanguageLabel
            ) {
                router.pushSettings(.languageSettings)
            }
        }
    }
}

struct SettingsAboutView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var toastMessage: String?

    private let supportEmail = "service@xipengxin.com"

    var body: some View {
        LNScreenScaffold(title: L10n.settingsAbout, onBack: { dismiss() }) {
            LNSettingsRow(title: L10n.privacyPolicyTitle) { router.pushSettings(.privacyPolicy) }
            LNSettingsRow(title: L10n.termsTitle) { router.pushSettings(.termsOfService) }
            LNSettingsRow(title: L10n.tr("settings_contact_support"), subtitle: supportEmail) {
                contactSupport()
            }
            Text("LumaNox v0.2.0")
                .font(LNTypography.labelMedium())
                .foregroundStyle(LNColor.subtitle)
        }
        .overlay(alignment: .bottom) {
            if let toastMessage {
                SettingsToast(message: toastMessage)
                    .padding(.horizontal, LNSpacing.screenHorizontal)
                    .padding(.bottom, 28)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: toastMessage)
    }

    private func contactSupport() {
        guard let url = URL(string: "mailto:\(supportEmail)") else {
            showToast(L10n.tr("settings_mail_client_required"))
            return
        }
        openURL(url) { accepted in
            if !accepted {
                showToast(L10n.tr("settings_mail_client_required"))
            }
        }
    }

    private func showToast(_ message: String) {
        toastMessage = message
        Task {
            try? await Task.sleep(nanoseconds: 2_200_000_000)
            if toastMessage == message {
                toastMessage = nil
            }
        }
    }
}

private struct SettingsToast: View {
    let message: String

    var body: some View {
        Text(message)
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(LNColor.title)
            .lineLimit(2)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Color(hex: 0x101722, alpha: 0.94))
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color(hex: 0x2F4564), lineWidth: 1))
            .shadow(color: Color.black.opacity(0.25), radius: 12, y: 8)
            .accessibilityIdentifier("settings_toast")
    }
}

struct ChangePinView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var currentPin = ""
    @State private var newPin = ""
    @State private var confirmPin = ""
    @State private var step: ChangePinStep = SecuritySettingsStore.shared.hasPinConfigured ? .verifyCurrent : .enterNew
    @State private var showSuccess = false
    @State private var showError = false
    @State private var errorMessage = ""

    private let pinLength = 6
    private let securityStore = SecuritySettingsStore.shared

    var body: some View {
        LNScreenScaffold(title: L10n.changePinTitle, onBack: { dismiss() }) {
            Text(step.subtitle)
                .font(LNTypography.bodyMedium())
                .foregroundStyle(LNColor.subtitle)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(step.progressText)
                .font(LNTypography.labelMedium())
                .foregroundStyle(LNColor.subtitle.opacity(0.85))
                .frame(maxWidth: .infinity, alignment: .leading)
            HStack(spacing: 8) {
                ForEach(0..<pinLength, id: \.self) { i in
                    Circle()
                        .fill(i < activeInput.count ? LNColor.brandBlue : LNColor.stroke)
                        .frame(width: 10, height: 10)
                }
            }
            keypad
        }
        .overlay { pinDialogs }
    }

    private var activeInput: String {
        switch step {
        case .verifyCurrent: currentPin
        case .enterNew: newPin
        case .confirmNew: confirmPin
        }
    }

    private var keypad: some View {
        let keys = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫"]
        return LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 3), spacing: 8) {
            ForEach(keys, id: \.self) { key in
                Button {
                    if key == "⌫" { deleteLast() }
                    else if !key.isEmpty { appendDigit(key) }
                } label: {
                    Text(key)
                        .font(LNTypography.titleMedium())
                        .frame(width: 64, height: 64)
                        .background(LNColor.sectionBg)
                        .clipShape(Circle())
                }
                .disabled(key.isEmpty)
                .opacity(key.isEmpty ? 0 : 1)
            }
        }
    }

    private func appendDigit(_ d: String) {
        switch step {
        case .verifyCurrent:
            guard currentPin.count < pinLength else { return }
            currentPin.append(d)
            if currentPin.count == pinLength { verifyCurrentPin() }
        case .enterNew:
            guard newPin.count < pinLength else { return }
            newPin.append(d)
            if newPin.count == pinLength { step = .confirmNew }
        case .confirmNew:
            guard confirmPin.count < pinLength else { return }
            confirmPin.append(d)
            if confirmPin.count == pinLength { submitChange() }
        }
    }

    private func deleteLast() {
        switch step {
        case .verifyCurrent where !currentPin.isEmpty:
            currentPin.removeLast()
        case .enterNew where !newPin.isEmpty:
            newPin.removeLast()
        case .confirmNew where !confirmPin.isEmpty:
            confirmPin.removeLast()
        default:
            break
        }
    }

    private func verifyCurrentPin() {
        guard securityStore.verifyPin(currentPin) else {
            errorMessage = L10n.tr("settings_pin_current_wrong")
            showError = true
            currentPin = ""
            return
        }
        step = .enterNew
    }

    private func submitChange() {
        guard newPin == confirmPin else {
            errorMessage = L10n.tr("lock_error_mismatch")
            showError = true
            newPin = ""
            confirmPin = ""
            step = .enterNew
            return
        }
        do {
            try securityStore.savePin(newPin, enableBiometric: securityStore.biometricEnabled)
            showSuccess = true
        } catch {
            errorMessage = error.localizedDescription
            showError = true
        }
    }

    @ViewBuilder
    private var pinDialogs: some View {
        if showSuccess {
            LNDialog(
                title: L10n.tr("settings_pin_success_title"),
                message: L10n.tr("settings_pin_success_message"),
                confirmTitle: L10n.commonOk,
                onConfirm: { showSuccess = false; dismiss() }
            )
        }
        if showError {
            LNDialog(
                title: L10n.tr("settings_pin_error_title"),
                message: errorMessage,
                confirmTitle: L10n.tr("settings_pin_error_action"),
                onConfirm: { showError = false }
            )
        }
    }
}

private enum ChangePinStep {
    case verifyCurrent
    case enterNew
    case confirmNew

    var subtitle: String {
        switch self {
        case .verifyCurrent:
            return L10n.tr("settings_pin_step_verify_desc")
        case .enterNew:
            return L10n.tr("settings_pin_step_new_desc")
        case .confirmNew:
            return L10n.tr("settings_pin_step_confirm_desc")
        }
    }

    var progressText: String {
        switch self {
        case .verifyCurrent:
            return L10n.tr("settings_pin_step_verify_title")
        case .enterNew:
            return L10n.tr("settings_pin_step_new_title")
        case .confirmNew:
            return L10n.tr("settings_pin_step_confirm_title")
        }
    }
}

struct StorageUsageView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var summary = VaultMetadataStore.shared.storageSummary()

    var body: some View {
        LNScreenScaffold(title: L10n.storageUsageTitle, onBack: { dismiss() }) {
            LNSettingsGroupCard(title: L10n.storageUsageTitle) {
                storageRow(
                    title: L10n.tr("storage_usage_active_count"),
                    value: "\(summary.activeCount)"
                )
                storageRow(
                    title: L10n.tr("storage_usage_trash_count"),
                    value: "\(summary.trashCount)"
                )
                storageRow(
                    title: L10n.tr("storage_usage_album_count"),
                    value: "\(summary.albumCount)"
                )
                storageRow(
                    title: L10n.tr("storage_usage_encrypted_size"),
                    value: ByteCountFormatter.string(fromByteCount: summary.encryptedBytes, countStyle: .file)
                )
                storageRow(
                    title: L10n.tr("storage_usage_metadata_updated"),
                    value: formattedDate(ms: summary.updatedAtMs)
                )
            }
        }
        .onAppear {
            Task {
                await VaultStore.shared.loadSnapshot()
                summary = VaultStore.shared.storageSummary()
            }
        }
    }

    private func storageRow(title: String, value: String) -> some View {
        HStack {
            Text(title)
                .font(LNTypography.bodyMedium())
                .foregroundStyle(LNColor.subtitle)
            Spacer()
            Text(value)
                .font(LNTypography.bodyMedium().weight(.semibold))
                .foregroundStyle(LNColor.title)
        }
    }

    private func formattedDate(ms: Int64) -> String {
        guard ms > 0 else { return "-" }
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        return formatter.string(from: Date(timeIntervalSince1970: Double(ms) / 1000))
    }
}

struct LanguageSettingsView: View {
    @EnvironmentObject private var languageManager: LanguageManager
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        LNScreenScaffold(title: L10n.languageTitle, onBack: { dismiss() }) {
            Text(L10n.languageCurrentLanguage(languageManager.effectiveLanguageLabel))
                .font(LNTypography.bodyMedium())
                .foregroundStyle(LNColor.subtitle)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.bottom, 4)
            Picker(L10n.languageTitle, selection: Binding(
                get: { languageManager.selection },
                set: { languageManager.setSelection($0) }
            )) {
                ForEach(AppLanguage.allCases) { language in
                    Text(L10n.tr(language.labelKey)).tag(language)
                }
            }
            .pickerStyle(.inline)
        }
    }
}

enum LegalDocumentKind {
    case privacyPolicy
    case termsOfService

    var fileName: String {
        switch self {
        case .privacyPolicy:
            return "privacy_policy"
        case .termsOfService:
            return "terms_of_service"
        }
    }
}

struct LegalWebView: View {
    @Environment(\.dismiss) private var dismiss
    let title: String
    let document: LegalDocumentKind

    var body: some View {
        VStack(spacing: 16) {
            LNNavigationBar(title: title, onBack: { dismiss() })

            LegalHTMLView(document: document)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(LNColor.sectionBg)
                .clipShape(RoundedRectangle(cornerRadius: LNRadius.homeCard))
                .overlay(
                    RoundedRectangle(cornerRadius: LNRadius.homeCard)
                        .stroke(LNColor.stroke, lineWidth: 1)
                )
                .padding(.horizontal, LNSpacing.screenHorizontal)
        }
        .lnScreenBackground()
    }
}

private struct LegalHTMLView: UIViewRepresentable {
    let document: LegalDocumentKind
    @ObservedObject private var languageManager = LanguageManager.shared

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = context.coordinator
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        let localeIdentifier = languageManager.effectiveLocaleIdentifier
        guard let htmlURL = Bundle.main.url(
            forResource: document.fileName,
            withExtension: "html",
            subdirectory: nil,
            localization: localeIdentifier
        ) else {
            webView.loadHTMLString(Self.errorHTML(message: L10n.tr("legal_load_failed")), baseURL: nil)
            return
        }

        do {
            let html = try String(contentsOf: htmlURL, encoding: .utf8)
            webView.loadHTMLString(Self.injectAppChrome(into: html), baseURL: htmlURL.deletingLastPathComponent())
        } catch {
            webView.loadHTMLString(Self.errorHTML(message: L10n.tr("legal_load_failed")), baseURL: nil)
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    private static func injectAppChrome(into html: String) -> String {
        let css = """
        <style>
          body { background-color: #05080D !important; color: #EAF1FF !important; }
          .container { padding: 24px 18px 64px !important; }
          a { color: #4A9EFF !important; }
          table, th, td { border-color: #223247 !important; }
        </style>
        """
        if html.contains("</head>") {
            return html.replacingOccurrences(of: "</head>", with: "\(css)\n</head>")
        }
        return "\(css)\n\(html)"
    }

    private static func errorHTML(message: String) -> String {
        """
        <html>
          <body style="background:#05080D;color:#8EA2C0;font:-apple-system-body;margin:0;padding:24px;line-height:1.5;">
            \(message)
          </body>
        </html>
        """
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            guard navigationAction.navigationType == .linkActivated,
                  let url = navigationAction.request.url,
                  ["http", "https"].contains(url.scheme?.lowercased() ?? "") else {
                decisionHandler(.allow)
                return
            }

            UIApplication.shared.open(url)
            decisionHandler(.cancel)
        }
    }
}
