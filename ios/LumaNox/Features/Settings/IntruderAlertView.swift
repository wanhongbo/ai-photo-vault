import SwiftUI

struct IntruderAlertView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @StateObject private var store = IntruderAlertStore.shared
    @State private var showClearAllDialog = false

    var body: some View {
        LNScreenScaffold(title: L10n.tr("intruder_alert_title"), onBack: { dismiss() }) {
            introCard
            toggleCard
            if store.isEnabled && !store.cameraAuthorized {
                permissionCard
            }
            recordsCard
            Text(L10n.tr("intruder_alert_footer"))
                .font(LNTypography.labelMedium())
                .foregroundStyle(LNColor.subtitle)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .onAppear { store.load() }
        .overlay {
            if showClearAllDialog {
                LNDialog(
                    title: L10n.tr("intruder_alert_clear_title"),
                    message: L10n.tr("intruder_alert_clear_desc"),
                    confirmTitle: L10n.tr("intruder_alert_clear_all"),
                    dismissTitle: L10n.commonCancel,
                    confirmVariant: .danger,
                    onConfirm: {
                        store.clearAll()
                        showClearAllDialog = false
                    },
                    onDismiss: { showClearAllDialog = false }
                )
            }
        }
        .accessibilityIdentifier("intruder_alert_view")
    }

    private var introCard: some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: "lock.shield")
                .font(.system(size: 24, weight: .semibold))
                .foregroundStyle(store.isEnabled ? LNColor.success : LNColor.subtitle)
                .frame(width: 48, height: 48)
                .background((store.isEnabled ? LNColor.success : LNColor.subtitle).opacity(0.14))
                .clipShape(RoundedRectangle(cornerRadius: 14))
            VStack(alignment: .leading, spacing: 6) {
                Text(L10n.tr("intruder_alert_intro_title"))
                    .font(LNTypography.titleLarge())
                    .foregroundStyle(LNColor.title)
                Text(L10n.tr("intruder_alert_intro_desc"))
                    .font(LNTypography.bodyMedium())
                    .foregroundStyle(LNColor.subtitle)
                    .lineSpacing(2)
            }
        }
        .padding(LNSpacing.cardPadding)
        .lnOutlinedCard()
    }

    private var toggleCard: some View {
        LNSettingsGroupCard(title: L10n.tr("settings_sec_privacy")) {
            LNSettingsSwitchRow(
                title: L10n.tr("intruder_alert_enable_title"),
                subtitle: L10n.tr("intruder_alert_enable_desc"),
                isOn: Binding(
                    get: { store.isEnabled },
                    set: { enabled in
                        store.setEnabled(enabled)
                        if enabled {
                            Task { await store.requestCameraPermissionIfNeeded() }
                        }
                    }
                )
            )
        }
    }

    private var permissionCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: "camera.badge.ellipsis")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(LNColor.amberWarning)
                    .frame(width: 42, height: 42)
                    .background(LNColor.amberWarning.opacity(0.14))
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                VStack(alignment: .leading, spacing: 3) {
                    Text(L10n.tr("intruder_alert_permission_title"))
                        .font(LNTypography.titleMedium())
                        .foregroundStyle(LNColor.title)
                    Text(L10n.tr("intruder_alert_permission_desc"))
                        .font(LNTypography.labelMedium())
                        .foregroundStyle(LNColor.subtitle)
                }
            }
            LNButton(title: L10n.tr("intruder_alert_open_settings"), variant: .primary) {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    openURL(url)
                }
            }
        }
        .padding(LNSpacing.cardPadding)
        .lnOutlinedCard(stroke: LNColor.amberWarning.opacity(0.6))
    }

    private var recordsCard: some View {
        LNSettingsGroupCard(title: L10n.tr("intruder_alert_records")) {
            if store.records.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "checkmark.shield.fill")
                        .font(.system(size: 32, weight: .semibold))
                        .foregroundStyle(LNColor.success)
                        .frame(width: 62, height: 62)
                        .background(LNColor.success.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 20))
                    Text(L10n.tr("intruder_alert_empty_title"))
                        .font(LNTypography.headlineSmall())
                        .foregroundStyle(LNColor.title)
                    Text(L10n.tr("intruder_alert_empty_desc"))
                        .font(LNTypography.bodyMedium())
                        .foregroundStyle(LNColor.subtitle)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 18)
            } else {
                LNButton(title: L10n.tr("intruder_alert_clear_all"), variant: .danger) {
                    showClearAllDialog = true
                }
                ForEach(store.records) { record in
                    IntruderAlertRecordRow(record: record)
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) {
                                store.delete(record)
                            } label: {
                                Label(L10n.tr("intruder_alert_delete"), systemImage: "trash")
                            }
                        }
                }
            }
        }
    }
}

private struct IntruderAlertRecordRow: View {
    let record: IntruderAlertRecord
    @State private var thumbnail: UIImage?

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(LNColor.sectionBg.opacity(0.9))
                if let thumbnail {
                    Image(uiImage: thumbnail)
                        .resizable()
                        .scaledToFill()
                        .frame(width: 56, height: 56)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                } else {
                    Image(systemName: "person.crop.square")
                        .font(.system(size: 24, weight: .medium))
                        .foregroundStyle(LNColor.subtitle)
                }
            }
            .frame(width: 56, height: 56)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(LNColor.stroke, lineWidth: 1))

            VStack(alignment: .leading, spacing: 2) {
                Text(dayText)
                    .font(LNTypography.titleMedium())
                    .foregroundStyle(LNColor.title)
                Text(timeText)
                    .font(LNTypography.labelMedium())
                    .foregroundStyle(LNColor.subtitle)
                Text(statusText)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(LNColor.subtitle)
            }
            Spacer()
            Text(record.attemptedPin)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(LNColor.subtitle)
        }
        .frame(maxWidth: .infinity, minHeight: 76, alignment: .leading)
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(LNColor.sectionBg.opacity(0.5))
        .clipShape(RoundedRectangle(cornerRadius: LNRadius.settingsRow))
        .task(id: record.id) {
            thumbnail = await IntruderAlertStore.shared.thumbnail(for: record)
        }
        .accessibilityIdentifier("intruder_alert_record_row")
    }

    private var date: Date {
        Date(timeIntervalSince1970: Double(record.createdAtMs) / 1000)
    }

    private var dayText: String {
        let calendar = Calendar.current
        if calendar.isDateInToday(date) { return L10n.tr("common_today") }
        if calendar.isDateInYesterday(date) { return L10n.tr("common_yesterday") }
        return DateFormatter.localizedString(from: date, dateStyle: .medium, timeStyle: .none)
    }

    private var timeText: String {
        DateFormatter.localizedString(from: date, dateStyle: .none, timeStyle: .short)
    }

    private var statusText: String {
        switch record.captureStatus {
        case .captured: return L10n.tr("intruder_alert_status_captured")
        case .permissionDenied: return L10n.tr("intruder_alert_status_permission")
        case .unavailable: return L10n.tr("intruder_alert_status_unavailable")
        case .failed: return L10n.tr("intruder_alert_status_failed")
        }
    }
}
