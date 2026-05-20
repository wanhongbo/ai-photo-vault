import Foundation

enum L10n {
    static func tr(_ key: String, _ args: CVarArg...) -> String {
        let format = NSLocalizedString(key, comment: "")
        guard !args.isEmpty else { return format }
        return String(format: format, locale: Locale.current, arguments: args)
    }

    static let appName = tr("app_name")
    static let commonBack = tr("common_back")
    static let commonCancel = tr("common_cancel")
    static let commonConfirm = tr("common_confirm")
    static let commonOk = tr("common_ok")
    static let commonLoading = tr("common_loading")

    static let splashTagline = tr("splash_tagline")
    static let homeTitle = tr("home_title")
    static let homeNavVault = tr("home_nav_vault")
    static let homeNavCamera = tr("home_nav_camera")
    static let homeNavAI = tr("home_nav_ai")
    static let homeNavSettings = tr("home_nav_settings")
    static let homeEmptyTitle = tr("home_vault_empty_title")
    static let homeEmptyDesc = tr("home_vault_empty_desc")
    static let homeEmptyAction = tr("home_vault_empty_action")
    static let homeAlbumCreateTitle = tr("home_album_create_title")
    static let homeAlbumCreateHint = tr("home_album_create_input_hint")
    static let homeAlbumCreateConfirm = tr("home_album_create_confirm")

    static let aiTitle = tr("ai_title")
    static let aiFeaturesTitle = tr("ai_features_title")
    static let aiCleanupTitle = tr("ai_cleanup_title")
    static let aiSensitiveTitle = tr("ai_sensitive_review_title")
    static let aiClassifyTitle = tr("ai_classify_title")
    static let privacyRedactTitle = tr("privacy_redact_title")

    static let settingsSubscription = tr("settings_subscription_title")
    static let settingsSecurity = tr("settings_l1_security")
    static let settingsBackup = tr("settings_l1_backup")
    static let settingsData = tr("settings_l1_data")
    static let settingsGeneral = tr("settings_l1_general")
    static let settingsAbout = tr("settings_l1_about")

    static let albumListTitle = tr("album_list_title")
    static let recentListTitle = tr("recent_list_title")
    static let vaultSearchTitle = tr("vault_search_title")
    static let photoViewerTitle = tr("photo_viewer_title")
    static let trashTitle = tr("trash_title")
    static let backupRestoreTitle = tr("backup_restore_title")
    static let exportProgressTitle = tr("export_progress_title")
    static let paywallTitle = tr("paywall_title")
    static let changePinTitle = tr("settings_pin_title")
    static let storageUsageTitle = tr("storage_usage_title")
    static let languageTitle = tr("language_settings_title")
    static let privacyPolicyTitle = tr("privacy_policy_title")
    static let termsTitle = tr("terms_title")
}
