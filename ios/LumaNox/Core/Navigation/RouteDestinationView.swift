import SwiftUI

struct RouteDestinationView: View {
    @EnvironmentObject private var router: AppRouter
    let route: AppRoute

    var body: some View {
        Group {
            switch route {
            case .vaultSearch: VaultSearchView()
            case .albumList: AlbumListView()
            case .recentList: RecentPhotosView()
            case .album(let name): AlbumView(albumName: name)
            case .photoViewer(let path, let isTrash, let source):
                PhotoViewerView(
                    path: path,
                    isTrash: isTrash,
                    source: source,
                    onOpenAlbum: isTrash ? { album in
                        router.selectedTab = .vault
                        router.pushVault(.album(name: album))
                    } : nil
                )
            case .videoPlayer(let path, let isTrash):
                VideoPlayerView(
                    path: path,
                    isTrash: isTrash,
                    onOpenAlbum: isTrash ? { album in
                        router.selectedTab = .vault
                        router.pushVault(.album(name: album))
                    } : nil
                )
            case .privateCamera: PrivateCameraView()
            case .aiCleanup: AICleanupView()
            case .aiSensitive: AISensitiveReviewView()
            case .aiClassify: AIClassifyView()
            case .aiClassifyDetail(let category): AIClassifyDetailView(category: category)
            case .privacyRedact(let path): PrivacyRedactView(path: path)
            case .backupRestore: BackupRestoreView()
            case .backupProgress(let uri): BackupProgressView(outputUri: uri)
            case .backupResult: BackupResultView()
            case .restoreProgress(let uri, let pin): RestoreProgressView(inputUri: uri, pin: pin)
            case .restoreResult: RestoreResultView()
            case .bulkExport: BulkExportView()
            case .exportProgress: ExportProgressView()
            case .exportResult: ExportResultView()
            case .changePin: ChangePinView()
            case .storageUsage: StorageUsageView()
            case .languageSettings: LanguageSettingsView()
            case .settingsSubscription: SettingsSubscriptionView()
            case .settingsSecurity: SettingsSecurityView()
            case .settingsBackupSync: SettingsBackupSyncView()
            case .settingsDataStorage: SettingsDataStorageView()
            case .settingsGeneral: SettingsGeneralView()
            case .settingsAbout: SettingsAboutView()
            case .privacyPolicy: LegalWebView(title: L10n.privacyPolicyTitle, document: .privacyPolicy)
            case .termsOfService: LegalWebView(title: L10n.termsTitle, document: .termsOfService)
            case .trashBin: TrashBinView()
            case .paywall(let dismissable, let source):
                PaywallView(dismissable: dismissable, source: source)
            }
        }
        .toolbar(.hidden, for: .navigationBar)
    }
}

extension View {
    func routeNavigationDestinations() -> some View {
        navigationDestination(for: AppRoute.self) { route in
            RouteDestinationView(route: route)
        }
    }
}
