import Foundation

enum MainTab: String, CaseIterable, Identifiable {
    case vault
    case camera
    case ai
    case settings

    var id: String { rawValue }
}

enum AppRoute: Hashable {
    // Vault / media
    case vaultSearch
    case albumList
    case recentList
    case album(name: String)
    case photoViewer(path: String, isTrash: Bool = false)
    case videoPlayer(path: String, isTrash: Bool = false)

    // Camera
    case privateCamera

    // AI
    case aiCleanup
    case aiSensitive
    case aiClassify
    case aiClassifyDetail(category: String)
    case privacyRedact(path: String)

    // Backup
    case backupRestore
    case backupProgress(outputUri: String)
    case backupResult
    case restoreProgress(inputUri: String, pin: String)
    case restoreResult

    // Export
    case bulkExport
    case exportProgress
    case exportResult

    // Settings
    case changePin
    case storageUsage
    case languageSettings
    case settingsSubscription
    case settingsSecurity
    case settingsBackupSync
    case settingsDataStorage
    case settingsGeneral
    case settingsAbout
    case privacyPolicy
    case termsOfService
    case trashBin

    // Paywall
    case paywall(dismissable: Bool, source: String)
}

enum AppPhase: Equatable {
    case splash
    case lock
    case main
}
