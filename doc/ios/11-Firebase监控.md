# Firebase 监控 — iOS

**状态**：**一期暂不实施**（不集成 Firebase SDK）。  
**平台**：iOS（Swift）  
**关联总览**：[原生双端架构设计方案.md](../私密相册%20App（一期）原生双端架构设计方案.md)（第七节）· [Android 对应文档](../android/11-Firebase监控.md)  
**推荐库**：见总表《[成熟三方库推荐（Android-iOS）.md](../成熟三方库推荐（Android-iOS）.md)》**第 11 节**。

---

## 一期替代方案

- 开发期：**OSLog / Xcode Organizer**、Instruments、真机回归。
- 发布期：依赖 **App Store Connect** 中的崩溃报告（若可用）；避免在日志中输出用户内容与密钥。

---

## 后续若接入（备忘）

- `GoogleService-Info.plist`；SPM 或 CocoaPods 集成 Crashlytics、Performance、Analytics（最小化）。
- 经协议注入，Domain 不直接依赖 Firebase；Analytics 需 **隐私清单** 与隐私政策披露。

---

## 分层落位（实施时）

- **Infrastructure**：SDK 初始化；**App** 生命周期内完成配置。
