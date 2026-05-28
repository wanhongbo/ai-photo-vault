# Firebase 监控 — Android

**状态**：已接入 Crashlytics + Analytics（无 `google-services.json` 时自动跳过插件与运行时初始化）。
**平台**：Android（Kotlin）  
**关联总览**：[原生双端架构设计方案.md](../私密相册%20App（一期）原生双端架构设计方案.md)（第七节）· [iOS 对应文档](../ios/11-Firebase监控.md)  
**推荐库**：见总表《[成熟三方库推荐（Android-iOS）.md](../成熟三方库推荐（Android-iOS）.md)》**第 11 节**。

---

## 当前接入

- Gradle 使用 Firebase BoM `33.16.0`、`firebase-analytics`、`firebase-crashlytics`；该 BoM 与项目当前 Kotlin `2.0.21` 兼容。
- 仅当以下任一配置文件存在时应用 `google-services` 与 `firebase-crashlytics` Gradle 插件：
  - `android/app/google-services.json`
  - `android/app/src/dev/google-services.json`
  - `android/app/src/prod/google-services.json`
  - `android/app/src/<variant>/google-services.json`
- `AppLogger` 会把已脱敏的 warning/error breadcrumb 写入 Crashlytics。
- `PaywallAnalytics` 会同步写入 Firebase Analytics；事件参数只允许功能、来源、商品 ID、结果等低敏字段。
- Debug 构建默认关闭 Analytics 与 Crashlytics 收集；Release 构建开启。

## 配置步骤

1. 在 Firebase Console 注册 Android App，包名使用 `com.xpx.vault`。
2. 下载 `google-services.json`，按环境放入上述路径之一。
3. 构建 `devDebug` 用于本地验证，构建 Release 时确认 Play Console/隐私政策中的数据披露同步更新。

## 隐私边界

- 通过 `AnalyticsService`、`CrashReporter`、`PerformanceTracer` 等 **接口** 注入，Domain 不直接依赖 Firebase。
- 不采集照片内容与密钥；更新 **数据安全** 表单与隐私政策。
- 不记录 PIN、媒体明文路径、密钥、照片内容、OCR 文本或可识别用户内容。

---

## 分层落位（实施时）

- **Infrastructure**：Firebase 初始化与封装；**Application**：启动阶段挂载。
