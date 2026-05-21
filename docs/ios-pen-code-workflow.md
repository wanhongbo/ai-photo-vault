# iOS Pen 到代码工作流

本文定义 LumaNox iOS 的 UI 生产流程：先有 `.pen` 高保真，再生成或调整 SwiftUI 代码，最后在模拟器验证。

## 硬性规则

1. **一页一 Pen**
   - 每个可路由页面都必须有独立 `.pen` 文件。
   - 文件优先放在对应 Swift 文件同目录，命名与页面一致，例如 `RecentPhotosView.swift` 对应 `RecentPhotosView.pen`。
   - 历史上合并在 `AIViews.pen`、`SettingsViews.pen`、`CameraViews.pen` 里的页面，后续改动时应拆出独立 Pen。

2. **Pen 先于代码**
   - 新增页面：先创建 `.pen`，再写 SwiftUI。
   - 修改页面：先更新 `.pen`，再更新 SwiftUI。
   - 修 bug 若涉及 UI 结构、状态、文案密度、交互反馈，也必须同步 Pen。

3. **Android 是功能参考**
   - 对齐 Android 的页面入口、列表密度、状态顺序、弹窗语义和业务流。
   - 视觉不直接复制 Android 系统元素；落到 iOS 的安全区、触摸目标、导航和系统控件习惯。

4. **运行截图反哺 Pen**
   - 如果模拟器截图发现布局与 Pen 不一致，不能只修代码或只修 Pen。
   - 产品意图正确的一方作为基准，另一方同步。

## 推荐步骤

1. 阅读上下文
   - `AGENTS.md`
   - `docs/ios-session-handoff.md`
   - `docs/ios-ux-guidelines.md`
   - `docs/ios-parity-spec.md`
   - 目标 Android Compose 页面
   - 目标 iOS SwiftUI 页面与现有 `.pen`

2. 生成或更新 Pen
   - 使用 Pencil 读取当前 `.pen`。
   - 如果缺失，创建与 SwiftUI 页面同名的 `.pen`。
   - 画布以 iPhone 16 / 393x852 为默认基准。
   - 使用 `Core/DesignSystem/LumaNoxStyleGuide.pen` 中的颜色、字号、圆角、间距。

3. 从 Pen 落地 SwiftUI
   - 将 Pen 的分组映射为 SwiftUI 子 View。
   - 将 Pen 的 token 映射到 `LNColor`、`LNSpacing`、`LNRadius`、`LNTypography`。
   - 所有文本进入 `Localizable.strings`。
   - 媒体网格使用标准 `VaultMediaGridCard` / `VaultMediaThumbnailView`，不要回退到装饰缩略图。

4. 验证
   - 运行 `xcodegen generate`。
   - 使用 `xcodebuild` 构建。
   - 安装并启动 iOS 模拟器。
   - 截图保存到 `/tmp/lumanox-sim/`，和 Pen 视觉核对。

5. 交付说明
   - 最终回复必须说明改了哪些 Pen、哪些 SwiftUI 页面、是否已模拟器验证。
   - 如果本次只改文档，可说明未运行模拟器以及原因。

## 当前 Pen 覆盖快照

| 页面 / 区域 | 当前 Pen | 状态 |
|---|---|---|
| Design System | `ios/LumaNox/Core/DesignSystem/LumaNoxStyleGuide.pen` | 已有 |
| Vault Home | `ios/LumaNox/Features/Vault/VaultHomeView.pen` | 已有 |
| Recent Photos List | `ios/LumaNox/Features/Vault/RecentPhotosListView.pen` | 已有，后续建议改名对齐 `RecentPhotosView.pen` |
| Album List | `ios/LumaNox/Features/Vault/AlbumListView.pen` | 已有 |
| Album Detail | `ios/LumaNox/Features/Vault/AlbumView.pen` | 已有 |
| Photo Viewer | `ios/LumaNox/Features/Vault/PhotoViewerView.pen` | 已有 |
| Lock | `ios/LumaNox/Features/Lock/LockView.pen` | 已有 |
| Camera Home / Private Camera | `ios/LumaNox/Features/Camera/CameraViews.pen` | 分组 Pen，待拆 |
| AI Home / AI 子页 | `ios/LumaNox/Features/AI/AIViews.pen` | 分组 Pen，待拆 |
| Settings Home / Settings 子页 | `ios/LumaNox/Features/Settings/SettingsViews.pen` | 分组 Pen，待拆 |

## 一页一 Pen 目标索引

| SwiftUI 页面 | 目标 Pen |
|---|---|
| `SplashView` | `ios/LumaNox/Features/Launch/SplashView.pen` |
| `MainTabView` | `ios/LumaNox/Features/MainTabs/MainTabView.pen` |
| `VaultHomeView` | `ios/LumaNox/Features/Vault/VaultHomeView.pen` |
| `AlbumListView` | `ios/LumaNox/Features/Vault/AlbumListView.pen` |
| `RecentPhotosView` | `ios/LumaNox/Features/Vault/RecentPhotosView.pen` |
| `AlbumView` | `ios/LumaNox/Features/Vault/AlbumView.pen` |
| `VaultSearchView` | `ios/LumaNox/Features/Vault/VaultSearchView.pen` |
| `PhotoViewerView` | `ios/LumaNox/Features/Vault/PhotoViewerView.pen` |
| `VideoPlayerView` | `ios/LumaNox/Features/Vault/VideoPlayerView.pen` |
| `TrashBinView` | `ios/LumaNox/Features/Vault/TrashBinView.pen` |
| `CameraHomeView` | `ios/LumaNox/Features/Camera/CameraHomeView.pen` |
| `PrivateCameraView` | `ios/LumaNox/Features/Camera/PrivateCameraView.pen` |
| `AIHomeView` | `ios/LumaNox/Features/AI/AIHomeView.pen` |
| `AICleanupView` | `ios/LumaNox/Features/AI/AICleanupView.pen` |
| `AISensitiveReviewView` | `ios/LumaNox/Features/AI/AISensitiveReviewView.pen` |
| `AIClassifyView` | `ios/LumaNox/Features/AI/AIClassifyView.pen` |
| `AIClassifyDetailView` | `ios/LumaNox/Features/AI/AIClassifyDetailView.pen` |
| `PrivacyRedactView` | `ios/LumaNox/Features/AI/PrivacyRedactView.pen` |
| `BackupRestoreView` | `ios/LumaNox/Features/Backup/BackupRestoreView.pen` |
| `BackupProgressView` | `ios/LumaNox/Features/Backup/BackupProgressView.pen` |
| `BackupResultView` | `ios/LumaNox/Features/Backup/BackupResultView.pen` |
| `RestoreProgressView` | `ios/LumaNox/Features/Backup/RestoreProgressView.pen` |
| `RestoreResultView` | `ios/LumaNox/Features/Backup/RestoreResultView.pen` |
| `BulkExportView` | `ios/LumaNox/Features/Export/BulkExportView.pen` |
| `ExportProgressView` | `ios/LumaNox/Features/Export/ExportProgressView.pen` |
| `ExportResultView` | `ios/LumaNox/Features/Export/ExportResultView.pen` |
| `SettingsHomeView` | `ios/LumaNox/Features/Settings/SettingsHomeView.pen` |
| `SettingsSubscriptionView` | `ios/LumaNox/Features/Settings/SettingsSubscriptionView.pen` |
| `SettingsSecurityView` | `ios/LumaNox/Features/Settings/SettingsSecurityView.pen` |
| `SettingsBackupSyncView` | `ios/LumaNox/Features/Settings/SettingsBackupSyncView.pen` |
| `SettingsDataStorageView` | `ios/LumaNox/Features/Settings/SettingsDataStorageView.pen` |
| `SettingsGeneralView` | `ios/LumaNox/Features/Settings/SettingsGeneralView.pen` |
| `SettingsAboutView` | `ios/LumaNox/Features/Settings/SettingsAboutView.pen` |
| `ChangePinView` | `ios/LumaNox/Features/Settings/ChangePinView.pen` |
| `StorageUsageView` | `ios/LumaNox/Features/Settings/StorageUsageView.pen` |
| `LanguageSettingsView` | `ios/LumaNox/Features/Settings/LanguageSettingsView.pen` |
| `LegalWebView` | `ios/LumaNox/Features/Settings/LegalWebView.pen` |
| `PaywallView` | `ios/LumaNox/Features/Paywall/PaywallView.pen` |
