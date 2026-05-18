# LumaNox（一期）iOS 端开发计划

文档目标：在《私密相册 App（一期）原生双端架构设计方案》、`doc/ios/` 分项方案，以及 **Android 已实现功能与营销截图**（`marketing/snapshot-Android/`）基础上，输出 **LumaNox** iOS（Swift）一期可执行开发计划，含 **App Store 上架配置清单** 与 **RevenueCat 配置清单**。用于排期、验收与商店/内购后台对齐。

**产品名称（商店展示）**：**LumaNox**（与 Android、RevenueCat 项目、隐私政策一致。）

**UI / 功能参考**

- 营销截图（Android 实机）：`marketing/snapshot-Android/`（9 张，覆盖 Vault 空态/有内容、设置中心、安全与备份、AI 助手、私密相机、隐私打码等）
- Android 实现对照：`android/app/.../ui/`、`MainActivity.kt` 路由表
- 设计 token 参考：`android/.../ui/theme/UiTokens.kt`（深色主题、主色 `#4A9EFF`、卡片圆角与底部 Tab 样式）

**关联文档**

- 总览：[私密相册 App（一期）原生双端架构设计方案.md](./私密相册%20App（一期）原生双端架构设计方案.md)
- iOS 分项：`doc/ios/README.md`（01～11）
- Android 一期计划（里程碑对齐）：[LumaNox-一期-Android-开发计划.md](./LumaNox-一期-Android-开发计划.md)
- 内购（双端 RC）：[doc/ios/10-内购付费.md](./ios/10-内购付费.md)、[doc/android/支付功能-开发计划与配置清单.md](./android/支付功能-开发计划与配置清单.md)
- 三方库：[成熟三方库推荐（Android-iOS）.md](./成熟三方库推荐（Android-iOS）.md)

---

## 一、一期范围说明（In / Out of Scope）

### 1.1 一期 **包含**（与 Android 已交付能力对齐）

| 模块 | 能力要点 | 截图 / 屏幕参考 |
|------|----------|-----------------|
| **Vault 首页** | 标题「LumaNox」、照片/视频计数、AES-256 文案、Photos/Videos 筛选、相册卡片、Recent 横滑、未设锁警告条、空态 CTA | 截图 (1)(8) → `HomeScreen` |
| **底部导航** | Vault / Camera / AI / Settings 四 Tab；Camera Tab 进入全屏相机（非 Tab 内嵌） | 全部主流程截图 → `MainScreen` |
| **私密相机** | 前后摄、闪光灯、定时、网格、曝光、变焦、照片/视频模式；**不入系统相册** | 截图 (7) → `PrivateCameraScreen` |
| **AI 助手** | 扫描建议卡（敏感项 / All clear）、Privacy Blur / Smart Classify / Deduplicate 入口 | 截图 (5)(6) → `AiHomeScreen` + AI 子屏 |
| **AI 子功能** | 智能分类、保险箱搜索、敏感内容审查、隐私打码（多种样式）、清理（模糊/去重） | 截图 (4) → `AiClassifyScreen`、`PrivacyRedactScreen`、`AiSensitiveReviewScreen`、`AiCleanupScreen`、`VaultSearchScreen` |
| **相册与浏览** | 相册列表/详情、最近列表、照片/视频查看器、回收站 | `AlbumListScreen`、`AlbumScreen`、`RecentPhotosScreen`、`PhotoViewerScreen`、`TrashBinScreen` |
| **安全** | PIN（6 位）、生物识别优先、后台立即锁定 | 截图 (2) → `LockScreen`、`ChangePinScreen`、设置-安全 |
| **备份** | 自动增量备份开关、手动加密备份/恢复、进度与结果页 | 截图 (3) → `BackupRestoreScreen` 等；格式见 **BackupPackageV1** |
| **设置中心** | 订阅、安全与隐私、备份与同步、数据与存储、通用、关于与支持 | 截图 (0) → `SettingsHomeScreen` + `SettingsDetailScreens` |
| **商业化** | RevenueCat + App Store（月/年订阅 + 买断）、Paywall、配额门控、恢复购买 | `PaywallScreen`、`PaywallGatekeeper` |
| **分享与导出** | 打码图分享、导出到系统相册（含水印策略）、批量导出 | `07-海外社交分享` |
| **多语言** | 中文默认 + 英文；应用内语言切换 | `08-多语言国际化` |
| **合规页** | 隐私政策、服务条款（WebView / `SFSafariViewController` 加载本地 HTML） | `LegalWebViewScreen` 对应 |

**免费版配额（须与 Android `FreeQuota` 一致）**

| 项 | 常量 |
|----|------|
| 保险箱条目上限 | 50（照片+视频合计） |
| 备份次数上限 | 1（含自动+手动） |
| AI 每月调用上限 | 10 |

**Premium 门控特性（`ProFeature` 对齐）**：`VAULT_IMPORT`、`BACKUP_CREATE`、`AI_*`、`EXPORT_NO_WATERMARK`。

### 1.2 一期 **不包含（与 Android 计划一致）**

| 能力 | 说明 |
|------|------|
| **伪装图标**（Alternate App Icon 作诱饵） | 不进入一期；Paywall / 权益文案不得承诺 |
| **闯入抓拍** | 不进入一期；设置页「Privacy protection」仅保留 **Coming later** 占位（与截图 (2) 一致） |
| **Firebase** | 一期不接；见 `doc/ios/11-Firebase监控.md` |

### 1.3 技术基线（摘要）

| 类别 | 选型 |
|------|------|
| 语言 / 最低版本 | Swift 5.9+；**iOS 16+**（与 PhotosPicker、SwiftUI 成熟度平衡；若需 iOS 17-only API 在 W1 评审） |
| UI | **SwiftUI** 为主；大图库网格可 `UICollectionView` + `UIViewRepresentable` |
| 架构 | 同构分层：Presentation → Application → Domain → Data → Platform；**Swift Package** 多模块 |
| 依赖注入 | 可选 **Factory** / 手写 `Environment` + `@MainActor` ViewModel；或 **swift-dependencies**（团队选型一次定稿） |
| 数据库 | **GRDB**（推荐）或 Core Data；schema 字段与 Android Room **文档对齐** |
| 安全 | **Keychain** + **CryptoKit**；PIN 摘要算法与 Android 一致 |
| 相册 / 选图 | **Photos**、**PHPicker**、缩略图 **Nuke** 或 **Kingfisher** |
| 相机 | **AVFoundation**（`AVCaptureSession`） |
| 生物识别 | **LocalAuthentication** |
| 本地 AI | **Vision** + **Core ML**（敏感检测）与/或 **TensorFlow Lite Swift**（与 Android TFLite 模型对齐，见第六节） |
| 分享 | `UIActivityViewController` + 临时解密文件 |
| 备份 | **BackupPackageV1** 二进制格式逐字节兼容；`UIDocumentPicker`、**BackgroundTasks**（自动备份） |
| 内购 | **RevenueCat**（`Purchases` iOS SDK）；业务层不直接写 StoreKit 2 主流程 |
| 监控 | 一期仅 **OSLog** + Xcode Organizer / App Store Connect 崩溃 |

**双端硬约定（实现前写入 `doc/backup-format-v1.md` 或扩展现有注释）**

- 单文件加密：与 Android `VaultCipher` 相同的 AES-256-GCM、文件布局
- 备份包：`AIVAULT\x01` + JSON Header + GCM Chunk + SHA256 Trailer（见 `BackupPackageV1.kt`）
- KDF：Argon2id 参数与 `keyFingerprintHex` 规则一致
- Entitlement ID：如 `premium`（与 RevenueCat Dashboard 一致）

---

## 二、工程与模块规划

### 2.1 仓库布局（建议新建 `ios/` 目录）

```
ios/
├── LumaNox.xcodeproj
├── LumaNox/                    # App Target：@main、Router、Assets、Localizable
├── Packages/
│   ├── VaultDomain/              # Entity、UseCase、Repository 协议、FreeQuota、ProFeature
│   ├── VaultData/                # GRDB、VaultCipher、BackupPackageV1、Repository 实现
│   ├── VaultAI/                  # Vision/TFLite 封装、PrivacyRenderer 对等
│   └── VaultPlatform/          # Keychain、Photos、Camera、Biometric、RevenueCat 适配
└── LumaNoxTests/
```

### 2.2 与 Android 模块映射

| Android | iOS |
|---------|-----|
| `:core:domain` | `VaultDomain` |
| `:core:data` | `VaultData` |
| `:core:ai` + `:core:ai-mlkit` | `VaultAI`（Vision + 可选 TFLite） |
| `:app` UI + billing | `LumaNox` + `VaultPlatform` |

### 2.3 构建与环境

| 变体 | 用途 |
|------|------|
| **Debug** | `REVENUECAT_API_KEY` 来自 `ios/Config/Secrets.xcconfig`（gitignore）；RC `logLevel = debug` |
| **Release** | App Store / TestFlight；RC 仅 error 日志；启用 Bitcode 关闭（默认） |

**Bundle ID**：与 App Store Connect、`com.xpx.vault` 或产品最终 ID 一致（与 Android `com.xpx.vault` 包名对应关系在 W8 定稿）。

---

## 三、信息架构与屏幕清单（对照 Android 路由）

### 3.1 应用流程

```
Splash → (未解锁) Lock → Main(Tab) ⇄ 各类 Push 子屏
              ↓
         PrivateCamera（全屏，可从 Tab 或锁屏快捷进入）
```

### 3.2 Tab 内主屏（`MainScreen` 对等）

| Tab | SwiftUI 视图 | 说明 |
|-----|--------------|------|
| Vault | `HomeView` | 空态/有内容；导入、私密拍照入口 |
| Camera | `CameraHomeView` | 预览 + 进入 `PrivateCameraView` |
| AI | `AiHomeView` | 建议卡 + 功能网格 |
| Settings | `SettingsHomeView` | 订阅卡 + 四大设置入口 + 底部警告条 |

### 3.3 Push / Modal 子屏（须实现）

| 分组 | 屏幕 |
|------|------|
| 媒体 | `AlbumListView`、`AlbumView`、`RecentListView`、`PhotoViewerView`、`VideoPlayerView`、`VaultSearchView` |
| AI | `AiClassifyView`、`AiCleanupView`、`AiSensitiveReviewView`、`PrivacyRedactView` |
| 安全 | `ChangePinView` |
| 备份 | `BackupRestoreView`、`BackupProgressView`、`BackupResultView`、`RestoreProgressView`、`RestoreResultView` |
| 设置子页 | `SubscriptionSettingsView`、`SecuritySettingsView`、`BackupSyncSettingsView`、`DataStorageSettingsView`、`GeneralSettingsView`、`AboutSupportView`、`LanguageSettingsView`、`StorageUsageView`、`BulkExportView`、`TrashBinView` |
| 导出 | `ExportProgressView`、`ExportResultView` |
| 商业 | `PaywallView`（可 dismiss / 不可 dismiss 两模式） |
| 合规 | `LegalWebView`（privacy / terms） |
| 引导 | `SplashView`、`LockView` |

### 3.4 UI 设计规范（落地要求）

1. **视觉**：深色背景渐变（`#0B1324` → `#05080D`）、卡片 `#0C1523`、描边 `#223247`、主色 `#4A9EFF`、错误 `#FF4372`（与 `UiColors` 对齐）。
2. **组件**：统一 `AppTopBar`、`PrimaryButton` / `SecondaryButton`、`AppDialog`、`PinInputSheet`（禁止每页一套 Alert 样式）。
3. **无障碍**：Dynamic Type、VoiceOver label；PIN 键盘大号触控区。
4. **素材**：图标优先 SF Symbols + 导出矢量；品牌图从 Pixso / Android `drawable` 高保真导出 @2x/@3x。
5. **参考截图路径**：`marketing/snapshot-Android/*.png` — 验收时逐屏对比（允许 iOS 安全区与字体差异，信息架构须一致）。

---

## 四、里程碑计划（建议 8～10 周，可按人力调整）

> 与 [Android 8 周计划](./LumaNox-一期-Android-开发计划.md) 对齐；iOS 无现成 `ios/` 工程时 **W1 延长 1～2 周** 做 Xcode 基建亦可接受。

### W1：基建与安全底座

- 创建 Xcode 工程、Swift Package 模块、最低部署目标、签名与 Scheme（Debug/Release）。
- `VaultCipher`（CryptoKit AES-256-GCM）+ 单测；Keychain 封装。
- GRDB schema v1（media、album、trash、subscription_state、quota_counters 等，字段名对照 Android Room）。
- 设计系统：`AppColors`、`AppTypography`、按钮/弹窗组件。

**验收**：冷启动稳定；加密 round-trip 单测通过；日志不输出密钥与明文路径。

### W2：认证门禁、权限、生命周期锁定

- `LockView` + PIN 设置/修改；`LocalAuthentication` 生物识别开关。
- `ScenePhase` / `UIApplication` 后台 → 立即展示锁屏（与 Android `ProcessLifecycleOwner` 行为一致）。
- `PHPicker`、相册权限引导（`NSPhotoLibraryUsageDescription` 等文案与 Android 语义一致）。

**验收**：切后台再进需解锁；拒绝权限有可理解引导。

### W3：导入加密与私密拍照

- PHPicker → 后台加密写入 → DB 索引；进度 UI。
- `PrivateCameraView`：仅写入 Vault 目录，**禁止** `PHPhotoLibrary` 保存。
- Vault 空态 / 有内容 UI（对齐截图 8 / 1）。

**验收**：批量导入成功率与进度可见；系统相册无新增条目。

### W4：相册管理、回收站与导出

- 相册 CRUD、网格预览、最近列表、照片/视频查看器。
- 回收站与保留策略；解密导出到 Photos（`PHAssetCreationRequest`）。
- 底部 Tab 与 `MainView` 叠层保活策略（对等 Android `alpha` + 指针吞噬，避免隐藏 Tab 误触）。

**验收**：回收站恢复/永久删除正确；导出后临时文件清理。

### W5：AI 管线

- **敏感审查**：Vision 人脸/文本 + 规则（与 `SensitiveRegexMatcher` 语义对齐的数据集）。
- **隐私打码**：Core Image 马赛克/高斯/条带等（对齐 `PrivacyRenderer` 六种样式）。
- **分类 / 去重 / 清理**：对标 `AiClassify`、`AiCleanup`；并发 `TaskGroup` + 内存上限。
- `AiHomeView` 建议卡与 Snooze（7 天）本地持久化。

**验收**：连续处理 20+ 张不 OOM；主线程无 >16ms 长阻塞。

### W6：分享、备份恢复、多语言

- `UIActivityViewController` 分享打码/解密临时文件。
- **BackupPackageV1** 读写（与 Android 互测：Android 备份 → iOS 恢复，反向亦然）。
- 自动备份：`BGAppRefreshTask` 或 `BGProcessingTask`（需说明 iOS 后台限制）；手动备份 `UIDocumentPicker`。
- `Localizable.xcstrings` 中英文；应用内语言（`AppleLanguages` / 自定义覆盖）。

**验收**：同版本备份包双端可恢复；主要界面双语完整。

### W7：内购与权益

| 任务 | 产出 |
|------|------|
| RevenueCat `Purchases.configure`、Offerings、购买/恢复、`CustomerInfo` → `premium` | `SubscriptionRepository` |
| `PaywallGatekeeper` + `QuotaManager`（数值同 `FreeQuota`） | 业务入口统一门控 |
| Paywall、订阅管理、恢复购买、跳转 App Store 订阅管理 | UI 与 Android 一致 |
| 价格/周期 **禁止写死** | 仅用 `StoreProduct` 本地化字段 |

**验收**：Sandbox 完成订阅/买断/恢复；免费配额与硬墙行为与 Android 一致；权益文案无伪装/闯入抓拍。

### W8：打磨、TestFlight 与上架

- Instruments：启动、滚动、加密导入内存。
- 隐私清单（Privacy Nutrition Labels）、加密出口合规（CCATS/豁免自查）。
- App Store 截图（可基于 `marketing/snapshot-Android` 重制 iOS 帧）、描述、审核备注。
- 全量回归 + **附录 A** 清单。

**验收**：TestFlight 内测通过；附录 A/B 完成。

### W9～W10（可选缓冲）

- 与 Android 交叉测试备份/恢复、RC 权益同步。
- 性能弱机（iPhone SE 2）、大库（5000+ 条）抽样。

---

## 五、分项任务要点（链接 `doc/ios/`）

### 5.1 本地图片库（`01`）

- `PHPickerConfiguration` 多选；`PHCachingImageManager` 缩略图；分页 `LIMIT/OFFSET` 查询。

### 5.2 加密（`02`）

- 导入流式加密，避免整图进内存；临时文件 `FileProtectionType.complete`.
- **必须**阅读 Android `VaultCipher.kt` 再实现 iOS 对等类。

### 5.3 安全（`03`）

- PIN：6 位、失败次数与冷却（无抓拍）。
- 设置页「Privacy protection」静态文案：*Coming later: decoy mode, intruder capture…*（与营销截图一致）。

### 5.4 私密拍照（`04`）

- `NSCameraUsageDescription`、`NSMicrophoneUsageDescription`（若录视频）。
- 锁屏快捷入口：与 Android `LockScreen` → Camera 路由一致。

### 5.5 相册管理（`05`）

- 默认相册 + 用户相册；`Recent` 限制条数可配置（Android 默认行为对齐即可）。

### 5.6 AI（`06`）

- 默认 **TFLite 同模型** 或 Vision 组合；选型记录在 PR。
- 打码页：检测条、手动框选、`Save to secure album` / `Export` / `Share`（截图 4）。

### 5.7 分享（`07`）

- 分享完成回调删除 `tmp/`；免费用户导出带水印策略与 Android 一致。

### 5.8 备份（`09`）

- 实现 `BackupPackageV1Reader/Writer` Swift 版；Argon2id 使用成熟 Swift 绑定（如 **Catena** / **libargon2** SPM，需安全审计）。
- 自动备份：默认开启、仅变更文件（与截图 3 文案一致）；需处理 iCloud Drive 可选路径。

### 5.9 内购（`10`）

- Entitlement、`Offering` identifier 与 Android RevenueCat **同一 Project** 下 iOS App 配置。
- 设置页「My Subscription」卡片样式对齐截图 0。

### 5.10 国际化（`08`）

- 字符串键名建议与 Android `strings.xml` **同名 key**（如 `vault_empty_title`），便于 TMS 同步。

---

## 六、本地 AI 选型（iOS 专项）

| 方案 | 适用 | 说明 |
|------|------|------|
| **A（推荐首期）** | Vision + 规则 + Core Image | 人脸 `VNDetectFaceRectanglesRequest`、文本 `VNRecognizeTextRequest`；快速落地敏感审查与打码 |
| **B（对齐 Android）** | TensorFlow Lite Swift + 同 `.tflite` | 行为一致、双端回归成本低；需集成 Metal Delegate |
| **C（进阶）** | Core ML 单独 `.mlpackage` | 仅当 A/B 性能不足；维护双模型成本高 |

**建议路径**：W5 先 **A** 达到产品可用 → 若指标不足再引入 **B**，用同一验证集对齐阈值。

---

## 七、风险与预案

| 风险 | 预案 |
|------|------|
| iOS 后台备份受限 | 用户可见上次备份时间；进入前台时补偿增量；设置页说明系统限制 |
| 备份跨端解密失败 | W6 设立双端 CI 夹具包；Header/Chunk 单测对齐 `BackupPackageV1` |
| 大图内存峰值 | `downsample` + `autoreleasepool`；导入并发度 ≤3 |
| App Store 审核（加密、相册、订阅） | 审核备注说明离线加密用途；订阅披露与 Paywall 链接隐私政策 |
| RC / StoreKit 沙箱不稳定 | 内测清单固定测试账号；日志仅记 error code |

---

## 八、测试策略

| 层级 | 内容 |
|------|------|
| Unit | Domain 配额、门控、备份 Header 解析、PIN 哈希 |
| Integration | 导入→加密→列表→导出；备份 round-trip |
| UI | XCUITest 关键路径；或 **Maestro**（`.maestro/`，`accessibilityIdentifier` 与 Android `testTag` 同名） |
| 手工 | 对照 `marketing/snapshot-Android` 逐屏；Sandbox 购买全流程 |

---

## 九、一期核心验收清单（摘要）

- [ ] 四 Tab 信息架构与 Android 一致，Camera 全屏独立
- [ ] AES-256 本地加密，密钥仅 Keychain
- [ ] 私密拍照不入系统相册
- [ ] AI：敏感扫描、打码六样式、分类、去重/清理入口可用
- [ ] 备份包与 Android **可互恢复**（同备份密码）
- [ ] 免费配额 50 / 1 / 10 与 Paywall 硬墙
- [ ] 无伪装图标、无闯入抓拍承诺
- [ ] 中英文界面完整
- [ ] 隐私政策 / 条款可打开
- [ ] TestFlight 稳定无崩溃门限（团队自定，如连续 3 日无 P0）

---

## 附录 A — App Store Connect 上架清单（一期）

| 项 | 动作 |
|----|------|
| App 记录 | 名称 **LumaNox**、副标题、分类（Photo & Video / Utilities） |
| Bundle ID & 证书 | Distribution 证书、Provisioning Profile |
| 截图 | 6.7" / 6.5" / 5.5" 等必需尺寸；可从 `marketing/snapshot-Android` 重排 iOS 状态栏 |
| 描述 | 强调 **离线、AES-256、无上传**；与隐私政策一致 |
| 关键词 | vault, private photo, encrypt, privacy blur 等（与 ASO 文档同步） |
| 隐私问卷 | 数据不收集 / 仅设备端处理；照片权限用途说明 |
| 加密 | 若使用标准 AES，按指引填写出口合规 |
| 订阅 | 自动续订订阅组、月/年 SKU；买断 Non-Consumable 或 Non-Renewing（与 RC 一致） |
| 审核信息 | 演示账号（若需要）、PIN 000000 等测试说明 |
| 年龄分级 | 按内容与订阅填写 |
| 支持 URL | 落地页 / 联系邮箱 |

---

## 附录 B — RevenueCat（iOS）配置清单

| 项 | 说明 |
|----|------|
| Project | 与 Android **同一 RevenueCat Project** |
| iOS App | 绑定 App Store Connect、Shared Secret / App Store Server API |
| Entitlement | `premium`（与 Android 一致） |
| Products | 月订 / 年订 / 买断 Product ID 与 ASC 一致 |
| Offerings | `default` Offering 含上述 Packages |
| Paywall（可选） | 远程权益文案；客户端不写死价格 |
| API Key | `REVENUECAT_API_KEY` 仅 Debug/TestFlight xcconfig；**勿提交 git** |
| 验收 | Sandbox：购买、续费、取消、恢复、买断、跨设备恢复 |

---

## 附录 C — 权限与 Info.plist 键（草案）

| Key | 用途 |
|-----|------|
| `NSPhotoLibraryUsageDescription` | 导入照片到保险箱 |
| `NSPhotoLibraryAddUsageDescription` | 导出到系统相册 |
| `NSCameraUsageDescription` | 私密拍照 |
| `NSMicrophoneUsageDescription` | 私密录像（若一期包含视频） |
| `NSFaceIDUsageDescription` | 生物识别解锁 |

---

## 附录 D — 与 Android 并行协作建议

1. **契约先行**：每周同步 `VaultCipher`、Room/GRDB schema、`BackupPackageV1` 变更。
2. **共享 QA 资产**：备份夹具、`marketing/snapshot-Android` 更新时同步 iOS 验收截图任务。
3. **RC Dashboard**：Product ID 分平台配置，Entitlement 统一 `premium`。
4. **域名层**：可将 `core/domain` Kotlin 逻辑摘为 **KMM** 或维护 Swift 平行实现（一期建议 Swift 手写 + 对照测试，避免 KMM 引入排期风险）。

---

> 本文档随 iOS 工程创建与 Android 实现变更迭代更新。加密或备份格式变更时，须同步修订 `BackupPackageV1` 注释、`doc/ios/09` 与 Android 对应文档。
