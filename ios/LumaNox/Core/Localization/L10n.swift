import Foundation

enum L10n {
    static func tr(_ key: String, _ args: CVarArg...) -> String {
        let format = LanguageManager.shared.localizedString(key)
        guard !args.isEmpty else { return format }
        return String(format: format, locale: LanguageManager.shared.effectiveLocale, arguments: args)
    }

    static var appName: String { tr("app_name") }
    static var commonBack: String { tr("common_back") }
    static var commonCancel: String { tr("common_cancel") }
    static var commonConfirm: String { tr("common_confirm") }
    static var commonOk: String { tr("common_ok") }
    static var commonLoading: String { tr("common_loading") }

    static var splashTagline: String { tr("splash_tagline") }
    static var homeTitle: String { tr("home_title") }
    static var homeNavVault: String { tr("home_nav_vault") }
    static var homeNavCamera: String { tr("home_nav_camera") }
    static var homeNavAI: String { tr("home_nav_ai") }
    static var homeNavSettings: String { tr("home_nav_settings") }
    static var homeEmptyTitle: String { tr("home_vault_empty_title") }
    static var homeEmptyDesc: String { tr("home_vault_empty_desc") }
    static var homeEmptyAction: String { tr("home_vault_empty_action") }
    static var homeAlbumCreateTitle: String { tr("home_album_create_title") }
    static var homeAlbumCreateHint: String { tr("home_album_create_input_hint") }
    static var homeAlbumCreateConfirm: String { tr("home_album_create_confirm") }

    static var aiTitle: String { tr("ai_title") }
    static var aiFeaturesTitle: String { tr("ai_features_title") }
    static var aiCleanupTitle: String { tr("ai_cleanup_title") }
    static var aiSensitiveTitle: String { tr("ai_sensitive_review_title") }
    static var aiClassifyTitle: String { tr("ai_classify_title") }
    static var privacyRedactTitle: String { tr("privacy_redact_title") }

    static var settingsSubscription: String { tr("settings_subscription_title") }
    static var settingsSecurity: String { tr("settings_l1_security") }
    static var settingsBackup: String { tr("settings_l1_backup") }
    static var settingsData: String { tr("settings_l1_data") }
    static var settingsGeneral: String { tr("settings_l1_general") }
    static var settingsAbout: String { tr("settings_l1_about") }

    static var albumListTitle: String { tr("album_list_title") }
    static var recentListTitle: String { tr("recent_list_title") }
    static var vaultSearchTitle: String { tr("vault_search_title") }
    static var photoViewerTitle: String { tr("photo_viewer_title") }
    static var trashTitle: String { tr("trash_title") }
    static var backupRestoreTitle: String { tr("backup_restore_title") }
    static var exportProgressTitle: String { tr("export_progress_title") }
    static var paywallTitle: String { tr("paywall_title") }
    static var changePinTitle: String { tr("settings_pin_title") }
    static var storageUsageTitle: String { tr("storage_usage_title") }
    static var languageTitle: String { tr("language_settings_title") }
    static func languageCurrentLanguage(_ language: String) -> String { tr("language_effective_label", language) }
    static var privacyPolicyTitle: String { tr("privacy_policy_title") }
    static var termsTitle: String { tr("terms_title") }
}
