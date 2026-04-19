# Firebase 监控 — Android

**状态**：**一期暂不实施**（不集成 Firebase SDK）。  
**平台**：Android（Kotlin）  
**关联总览**：[原生双端架构设计方案.md](../私密相册%20App（一期）原生双端架构设计方案.md)（第七节）· [iOS 对应文档](../ios/11-Firebase监控.md)  
**推荐库**：见总表《[成熟三方库推荐（Android-iOS）.md](../成熟三方库推荐（Android-iOS）.md)》**第 11 节**。

---

## 一期替代方案

- 开发期：**Logcat**、Android Studio Profiler、严格真机回归。
- 发布期：依赖 **Google Play 控制台** 提供的崩溃/ANR 报告（若可用）；必要时自建轻量日志文件（注意不落敏感数据）。

---

## 后续若接入（备忘）

- `google-services.json` + Firebase BOM；Crashlytics、Performance、Analytics（最小化）在 `Application` 或 DI 模块初始化。
- 通过 `AnalyticsService`、`CrashReporter`、`PerformanceTracer` 等 **接口** 注入，Domain 不直接依赖 Firebase。
- 不采集照片内容与密钥；更新 **数据安全** 表单与隐私政策。

---

## 分层落位（实施时）

- **Infrastructure**：Firebase 初始化与封装；**Application**：启动阶段挂载。
