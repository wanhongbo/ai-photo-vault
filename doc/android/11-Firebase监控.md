# Firebase 监控 — Android

**状态**：已接入 Firebase Analytics / Crashlytics（低敏事件 + 崩溃线索）。
**平台**：Android（Kotlin）  
**关联总览**：[原生双端架构设计方案.md](../私密相册%20App（一期）原生双端架构设计方案.md)（第七节）· [iOS 对应文档](../ios/11-Firebase监控.md)  
**推荐库**：见总表《[成熟三方库推荐（Android-iOS）.md](../成熟三方库推荐（Android-iOS）.md)》**第 11 节**。

---

## 接入方式

- 本机/CI 放置 `android/app/google-services.json` 或 flavor/buildType 对应路径；真实配置文件已在 `.gitignore` 中排除。
- `android/app/build.gradle.kts` 检测到配置文件后才应用 `com.google.gms.google-services` 与 `com.google.firebase.crashlytics`。
- Firebase SDK 通过 BOM 管理版本，当前接入 Analytics 与 Crashlytics。
- `AndroidManifest.xml` 显式移除 `com.google.android.gms.permission.AD_ID`，不使用广告标识符。
- `LumaApp.onCreate()` 调用 `FirebaseTelemetry.install()`，并通过 `LumaTelemetry` 上报启动事件。

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
- 文件名、相册名、绝对路径、备份 URI
- PIN、密钥、密文、KDF 参数、用户输入的自由文本

---

## 分层落位

- `telemetry/FirebaseTelemetry.kt`：Firebase SDK 适配层。
- `telemetry/LumaTelemetry.kt`：应用低敏事件接口。
- 业务代码只调用 `LumaTelemetry` 或已有 `PaywallAnalytics`，不直接依赖 Firebase API。
