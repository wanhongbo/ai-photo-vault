# Firebase 监控 — iOS

**状态**：已接入 Firebase Analytics / Crashlytics（低敏事件 + 崩溃线索）。
**平台**：iOS（Swift）  
**关联总览**：[原生双端架构设计方案.md](../私密相册%20App（一期）原生双端架构设计方案.md)（第七节）· [Android 对应文档](../android/11-Firebase监控.md)  
**推荐库**：见总表《[成熟三方库推荐（Android-iOS）.md](../成熟三方库推荐（Android-iOS）.md)》**第 11 节**。

---

## 接入方式

- 本机/CI 放置 `ios/LumaNox/Supporting/GoogleService-Info.plist` 或 `ios/LumaNox/GoogleService-Info.plist`；真实配置文件已在 `ios/.gitignore` 中排除。
- `ios/project.yml` 通过 Swift Package Manager 接入 FirebaseCore、FirebaseAnalyticsCore、FirebaseCrashlytics，不使用广告标识符支持包。
- XcodeGen 生成工程后，构建脚本会把本地 `GoogleService-Info.plist` 复制到 app bundle；Release 构建会尝试运行 Crashlytics dSYM 上传脚本。
- `LumaNoxApp.init()` 调用 `FirebaseTelemetry.configure()`，并通过 `LumaTelemetry` 上报启动事件。

---

## 事件边界

只允许上报低敏枚举、结果态、数量和版本信息，例如：

- `app_start`
- `lock_setup` / `lock_unlock` / `lock_restore_login`
- `vault_import`
- `vault_item`
- `camera_capture`
- `backup_create`
- `backup_restore`
- `ai_scan`
- `paywall_*` / `gate_triggered`

禁止上报：

- 照片、视频、缩略图、OCR 文本
- 文件名、相册名、绝对路径、备份 URL
- PIN、密钥、密文、KDF 参数、用户输入的自由文本

---

## 分层落位

- `Core/Telemetry/FirebaseTelemetry.swift`：Firebase SDK 适配层。
- `Core/Telemetry/LumaTelemetry.swift`：应用低敏事件接口。
- 业务代码只调用 `LumaTelemetry` 或已有 `PaywallAnalytics`，不直接依赖 Firebase API。
