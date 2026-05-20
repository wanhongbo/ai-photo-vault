import SwiftUI
import UniformTypeIdentifiers

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
    @State private var biometric = true

    var body: some View {
        LNScreenScaffold(title: L10n.settingsSecurity, onBack: { dismiss() }) {
            LNSettingsGroupCard(title: L10n.settingsSecurity) {
                LNSettingsSwitchRow(title: "Face ID", subtitle: "Quick unlock", isOn: $biometric)
                LNSettingsRow(title: L10n.changePinTitle) { router.pushSettings(.changePin) }
            }
        }
    }
}

struct SettingsBackupSyncView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel = SettingsBackupSyncViewModel()
    @State private var showFolderPicker = false

    var body: some View {
        LNScreenScaffold(title: L10n.settingsBackup, onBack: { dismiss() }) {
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
}

struct SettingsDataStorageView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        LNScreenScaffold(title: L10n.settingsData, onBack: { dismiss() }) {
            LNSettingsRow(title: L10n.storageUsageTitle) { router.pushSettings(.storageUsage) }
            LNSettingsRow(title: L10n.tr("bulk_export_title")) {
                guard router.guardProFeature(.exportNoWatermark) else { return }
                router.pushSettings(.bulkExport)
            }
            LNSettingsRow(title: L10n.trashTitle) { router.pushSettings(.trashBin) }
        }
    }
}

struct SettingsGeneralView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        LNScreenScaffold(title: L10n.settingsGeneral, onBack: { dismiss() }) {
            LNSettingsRow(title: L10n.languageTitle) { router.pushSettings(.languageSettings) }
        }
    }
}

struct SettingsAboutView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        LNScreenScaffold(title: L10n.settingsAbout, onBack: { dismiss() }) {
            LNSettingsRow(title: L10n.privacyPolicyTitle) { router.pushSettings(.privacyPolicy) }
            LNSettingsRow(title: L10n.termsTitle) { router.pushSettings(.termsOfService) }
            Text("LumaNox v0.2.0")
                .font(LNTypography.labelMedium())
                .foregroundStyle(LNColor.subtitle)
        }
    }
}

struct ChangePinView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var pin = ""
    @State private var confirmPin = ""
    @State private var step = 0
    @State private var showSuccess = false
    @State private var showError = false
    @State private var errorMessage = ""

    private let pinLength = 6
    private let securityStore = SecuritySettingsStore.shared

    var body: some View {
        LNScreenScaffold(title: L10n.changePinTitle, onBack: { dismiss() }) {
            Text(step == 0 ? L10n.tr("lock_setup_enter_subtitle") : L10n.tr("lock_setup_confirm_subtitle"))
                .foregroundStyle(LNColor.subtitle)
            HStack(spacing: 8) {
                ForEach(0..<pinLength, id: \.self) { i in
                    Circle()
                        .fill(i < currentPin.count ? LNColor.brandBlue : LNColor.stroke)
                        .frame(width: 10, height: 10)
                }
            }
            keypad
        }
        .overlay { pinDialogs }
    }

    private var currentPin: String { step == 0 ? pin : confirmPin }

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
        if step == 0 {
            guard pin.count < pinLength else { return }
            pin.append(d)
            if pin.count == pinLength { step = 1 }
        } else {
            guard confirmPin.count < pinLength else { return }
            confirmPin.append(d)
            if confirmPin.count == pinLength { submitChange() }
        }
    }

    private func deleteLast() {
        if step == 0, !pin.isEmpty { pin.removeLast() }
        else if step == 1, !confirmPin.isEmpty { confirmPin.removeLast() }
    }

    private func submitChange() {
        guard pin == confirmPin else {
            errorMessage = L10n.tr("lock_error_mismatch")
            showError = true
            pin = ""
            confirmPin = ""
            step = 0
            return
        }
        do {
            try securityStore.savePin(pin, enableBiometric: securityStore.biometricEnabled)
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
                message: L10n.tr("placeholder_feature"),
                confirmTitle: L10n.tr("settings_pin_error_action"),
                onConfirm: { showError = false }
            )
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
    @Environment(\.dismiss) private var dismiss
    @State private var selection = 0

    var body: some View {
        LNScreenScaffold(title: L10n.languageTitle, onBack: { dismiss() }) {
            Picker(L10n.languageTitle, selection: $selection) {
                Text("简体中文").tag(0)
                Text("English").tag(1)
            }
            .pickerStyle(.inline)
        }
    }
}

struct LegalWebView: View {
    @Environment(\.dismiss) private var dismiss
    let title: String

    var body: some View {
        LNScreenScaffold(title: title, onBack: { dismiss() }) {
            Text(L10n.tr("legal_placeholder"))
                .font(LNTypography.bodyMedium())
                .foregroundStyle(LNColor.subtitle)
        }
    }
}
