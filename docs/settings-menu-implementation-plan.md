# 设置菜单功能盘点与落地计划

本文以 Android 设置菜单为行为参考，以 iOS 当前实现和 iOS UX/Pen 规范为落地基准，记录设置菜单各入口的实现状态、缺口和后续开发顺序。

## 范围

- Android 参考：`SettingsHomeScreen`、`SettingsDetailScreens`、`ChangePinScreen`、`BackupRestoreScreen`、`StorageUsagePlaceholderScreen`、`LanguageSettingsScreen`、`LegalWebViewScreen`
- iOS 实现：`SettingsViews.swift`、`SettingsBackupSyncViewModel.swift`、`BackupViews.swift`、`PaywallView.swift`、`LanguageManager.swift`
- Pen 约束：设置页历史集中在 `SettingsViews.pen`，后续改动按 `docs/ios-pen-code-workflow.md` 拆为一页一 Pen。

## 当前状态

| 设置入口 | Android 状态 | iOS 状态 | 缺口 | 优先级 |
|---|---|---|---|---|
| 设置首页 | 已实现订阅卡、备份提醒、5 个一级入口 | 已实现同等入口 | 仍使用分组 Pen，需拆 `SettingsHomeView.pen` | P1 |
| 我的订阅 | 已接 RevenueCat 状态、免费配额、会员权益、跳转管理 | 可显示会员状态、升级、恢复购买 | iOS 仍缺免费配额明细、订阅管理深链、错误/加载态 | P1 |
| 安全与隐私 | PIN 修改入口、假状态生物识别/自动锁开关、隐私占位 | 本轮已接 `SecuritySettingsStore.biometricEnabled`，Change PIN 已改为先验证旧 PIN；自动锁展示固定 60s 策略 | 后续可扩展伪装模式、入侵者抓拍、防截图等隐私选项 | P1 |
| 备份与同步 | 自动备份开关、手动备份恢复入口 | 已接文件夹授权、立即自动备份、手动备份恢复入口；本轮补状态摘要和失败恢复提示 | 后续继续打磨一页一 Pen 与跨端备份自测 | P1 |
| 数据与存储 | 存储扫描、批量导出、回收站入口 | 已有 metadata 存储摘要、真实批量导出选择/进度/分享、回收站入口 | 存储摘要缺缓存/备份分项，导出还需更多取消/失败回归 | P1 |
| 通用/语言 | 已实现中英切换 | 已实现跟随系统/中英选择 | 需验证语言切换后导航栈刷新与法务 HTML 语言同步 | P1 |
| 关于与支持 | 版本、隐私政策、服务条款、联系支持 | 版本、隐私政策、服务条款 | 联系支持缺失；版本硬编码；法务页本轮已从占位改为本地 HTML | P1 |
| 法务页面 | 本地 HTML WebView，外链打开系统浏览器 | 本轮已同步本地 HTML + WKWebView | 需模拟器截图验证和后续内容更新机制 | P1 |

## 落地顺序

1. **P0 安全与数据出入口（本轮已完成首版）**
   - Change PIN：已改为旧 PIN 校验 → 新 PIN → 二次确认，不允许直接覆盖当前 PIN。
   - 备份与同步：保持现有真实自动备份能力，已补状态摘要和错误恢复提示；已拆 `SettingsBackupSyncView.pen`。
   - 批量导出：确认当前 iOS 已接真实 metadata selection/decrypt/share；后续继续做失败路径回归。

2. **P1 合规、订阅和设置完整性**
   - 法务页：使用当前语言加载本地 HTML，外链交给系统浏览器；本轮已完成首版。
   - 订阅页：补免费配额、会员权益对比、恢复购买状态、管理订阅入口。
   - 关于页：版本改读 Bundle，补联系支持策略。
   - Pen 拆分：`SettingsHomeView`、`SettingsSubscriptionView`、`SettingsDataStorageView`、`SettingsGeneralView`、`SettingsAboutView`、`StorageUsageView`、`LanguageSettingsView`。

3. **P2 边缘与危险操作**
   - 清空私密相册：已决定去掉该功能，不再作为设置菜单入口或后续计划项。
   - 存储占用：补备份文件、缓存、缩略图缓存分项。
   - 通用设置预留项：避免无业务占位长期暴露。

## 本轮已落地

- 新增 `LegalWebView.pen`，从分组 Settings Pen 中拆出法务页视觉源。
- iOS 隐私政策与服务条款同步 Android 本地 HTML 资源。
- iOS `LegalWebView` 从占位文案改为 `WKWebView` 加载本地 HTML，并拦截外部链接交给系统浏览器。
- 去掉 iOS 设置菜单中的“清空私密相册”计划项，不再作为设置入口或后续开发项。
- 新增 `SettingsSecurityView.pen`、`SettingsBackupSyncView.pen`、`ChangePinView.pen`，并落地 P0 安全/备份状态改动。

## 验收命令

```bash
cd ios
xcodegen generate
xcodebuild -scheme LumaNox -project LumaNox.xcodeproj -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' build
xcrun simctl install booted build/Build/Products/Debug-iphonesimulator/LumaNox.app
xcrun simctl launch booted com.xpx.vault
xcrun simctl io booted screenshot /tmp/lumanox-sim/settings-legal.png
```
