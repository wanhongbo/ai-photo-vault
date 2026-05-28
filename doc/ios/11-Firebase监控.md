# Firebase 监控 — iOS

**状态**：已接入 Crashlytics + Analytics（无 `GoogleService-Info.plist` 时自动跳过运行时初始化与 dSYM 上传）。
**平台**：iOS（Swift）  
**关联总览**：[原生双端架构设计方案.md](../私密相册%20App（一期）原生双端架构设计方案.md)（第七节）· [Android 对应文档](../android/11-Firebase监控.md)  
**推荐库**：见总表《[成熟三方库推荐（Android-iOS）.md](../成熟三方库推荐（Android-iOS）.md)》**第 11 节**。

---

## 当前接入

- XcodeGen 通过 Swift Package Manager 接入 `FirebaseCore`、`FirebaseAnalytics`、`FirebaseCrashlytics`。
- `LumaNoxApp` 启动时调用 `FirebaseTelemetry.configure()`；若 bundle 内没有 `GoogleService-Info.plist`，静默跳过。
- `GoogleService-Info.plist` 被 `.gitignore` 排除，XcodeGen 会通过构建脚本在本地存在时复制进 app bundle，避免干净仓库缺少私密配置时构建失败。
- XcodeGen 添加 Crashlytics dSYM post-build script；Debug 构建跳过上传，Release/归档构建在配置文件和 SwiftPM checkout 可用时上传。
- `PaywallAnalytics` 会同步写入 Firebase Analytics；事件参数只允许功能、来源、商品 ID、结果等低敏字段。
- Debug 构建默认关闭 Analytics 与 Crashlytics 收集；Release 构建开启。

## 配置步骤

1. 在 Firebase Console 注册 iOS App，Bundle ID 使用 `com.xpx.vault`。
2. 下载 `GoogleService-Info.plist`，放到 `ios/LumaNox/Supporting/GoogleService-Info.plist` 或 `ios/LumaNox/GoogleService-Info.plist`，确保 Xcode target 资源中包含该文件。
3. 归档或发布构建时确认 Crashlytics dSYM script 执行成功，并同步更新 App Store 隐私披露与隐私政策。

## 隐私边界

- 经协议注入，Domain 不直接依赖 Firebase；Analytics 需 **隐私清单** 与隐私政策披露。
- 不记录 PIN、媒体明文路径、密钥、照片内容、OCR 文本或可识别用户内容。

---

## 分层落位（实施时）

- **Infrastructure**：SDK 初始化；**App** 生命周期内完成配置。
