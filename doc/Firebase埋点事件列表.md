# Firebase 埋点事件列表

**状态**：当前双端 Firebase 接入后的事件清单。
**适用平台**：Android / iOS
**上报通道**：Firebase Analytics；Crashlytics 仅记录低敏 breadcrumb 与崩溃线索。
**代码入口**：Android `telemetry/LumaTelemetry.kt` / iOS `Core/Telemetry/LumaTelemetry.swift`

---

## 隐私边界

所有事件只允许携带低敏枚举、结果态、数量和版本信息。

禁止上报：

- 照片、视频、缩略图、OCR 文本
- 文件名、相册名、绝对路径、备份 URI / URL
- PIN、密钥、密文、KDF 参数
- 用户输入的自由文本

实现约束：

- Android 通过 `AndroidManifest.xml` 移除 `com.google.android.gms.permission.AD_ID`。
- iOS 通过 `FirebaseAnalyticsCore` 接入 Analytics，不使用广告标识符支持包。
- Debug 构建关闭 Analytics / Crashlytics 采集，仅保留本地调试日志。
- 事件名和参数名会被 SDK 适配层过滤为字母、数字、下划线，并限制长度。

---

## 公共参数说明

| 参数 | 含义 | 允许值 / 示例 | 备注 |
|---|---|---|---|
| `result` | 操作结果 | `success` / `failed` / `cancelled` / `empty` 等 | 按事件定义收敛取值 |
| `source` | 入口来源 | `picker` / `camera` / `manual` / `auto` 等 | 不传页面标题、文件路径、相册名 |
| `media_type` | 媒体类型 | `image` / `video` / `unknown` | 由扩展名或媒体来源推断 |
| `asset_count` | 备份资产数量 | 非负整数字符串 | 仅数量，不包含资产标识 |
| `total` | 扫描目标数量 | 非负整数字符串 | AI 扫描目标总数 |
| `processed` | 已处理数量 | 非负整数字符串 | AI 扫描已处理数量 |
| `restored` | 恢复成功数量 | 非负整数字符串 | 备份恢复统计 |
| `skipped` | 恢复跳过数量 | 非负整数字符串 | 备份恢复统计 |
| `failed` | 恢复失败数量 | 非负整数字符串 | 备份恢复统计 |

---

## 事件清单

| 事件名 | 已实现平台 | 触发时机 | 参数 | 允许值 / 备注 |
|---|---|---|---|---|
| `app_start` | Android / iOS | App 初始化后 | `version_name`, `version_code`, `build_type` | `build_type`: `debug` / `release`；iOS 的 `version_code` 对应 CFBundleVersion |
| `lock_setup` | Android / iOS | 设置 PIN 完成、失败或二次输入不一致 | `method`, `result` | `method`: `pin`; `result`: `success` / `failed` / `mismatch` |
| `lock_unlock` | Android / iOS | PIN 或生物识别解锁完成 | `method`, `result` | `method`: `pin` / `biometric`; `result`: `success` / `failed` |
| `lock_restore_login` | Android / iOS | 首启恢复登录 PIN 校验完成 | `method`, `result` | `method`: `pin`; `result`: `success` / `failed` |
| `vault_import` | Android / iOS | 媒体导入保险箱结束 | `source`, `media_type`, `result` | `source`: Android `picker` / `redact` / `metadata_safe`; iOS `picker` / `camera`; `result`: `added` / `duplicate` / `failed` / Android 另有 `quota_exceeded` |
| `camera_capture` | Android / iOS | 私密相机拍摄保存结束 | `media_type`, `result` | `media_type`: `image` / `video` / `unknown`; `result`: `success` / `failed` / Android 另有 `quota_exceeded` |
| `vault_item` | Android / iOS | 媒体移入回收站、恢复、永久删除结束 | `action`, `result` | `action`: `trash` / `restore` / `purge`; `result`: `success` / `failed` |
| `backup_create` | Android / iOS | 自动或手动备份结束，或备份任务被拒绝 | `trigger`, `kind`, `result`, `asset_count` | Android `trigger`: `auto` / `manual`; Android `kind`: `full` / `incremental`; iOS `trigger`: `auto` / `manual`; iOS `kind`: `auto` / `manual`; `result`: `success` / `failed` / Android 另有 `already_running` |
| `backup_restore` | Android / iOS | 自动或手动恢复结束，或自动恢复入口前置失败 | `source`, `result`, `restored`, `skipped`, `failed` | `source`: `auto` / `manual`; `result`: `success` / `failed`; 数量字段仅为统计 |
| `ai_scan` | Android / iOS | AI 本地扫描结束、取消或无目标 | `result`, `total`, `processed` | Android `result`: `success` / `failed` / `cancelled` / `empty` / `no_new_items`; iOS `result`: `success` / `failed` / `cancelled` / `empty` |
| `gate_triggered` | Android / iOS | Premium 功能门控触发 | `feature`, `reason` | `feature` 为功能枚举；`reason` 为门控原因枚举 |
| `paywall_purchase_start` | Android / iOS | 用户点击购买并发起购买流程 | `package`, `source` | `package` 为商品包 ID；`source` 为支付墙来源枚举 |
| `paywall_purchase_success` | Android / iOS | 购买成功 | `package` | 商品包 ID |
| `paywall_purchase_cancel` | Android / iOS | 用户取消购买 | 无 | 无参数 |
| `paywall_purchase_fail` | Android / iOS | 购买失败 | `error` | 当前为 SDK 错误摘要；不得包含用户输入、账号、订单详情 |
| `paywall_restore` | Android / iOS | 恢复购买完成 | `success` | `true` / `false` |
| `paywall_shown` | Android | 支付墙展示 | `source`, `trigger` | iOS 暂未接入 |
| `paywall_dismissed` | Android | 用户关闭或跳过支付墙 | `source` | iOS 暂未接入 |

---

## 代码落位

| 模块 | Android | iOS |
|---|---|---|
| Firebase 适配 | `android/app/src/main/kotlin/com/xpx/vault/telemetry/FirebaseTelemetry.kt` | `ios/LumaNox/Core/Telemetry/FirebaseTelemetry.swift` |
| 统一事件接口 | `android/app/src/main/kotlin/com/xpx/vault/telemetry/LumaTelemetry.kt` | `ios/LumaNox/Core/Telemetry/LumaTelemetry.swift` |
| App 启动 | `LumaApp.kt` | `LumaNoxApp.swift` |
| 付费墙 / 门控 | `billing/PaywallAnalytics.kt` | `Core/Billing/PaywallAnalytics.swift` |
| 保险箱导入 / 删除 | `ui/vault/VaultStore.kt` | `Core/Vault/VaultStore*.swift` |
| 备份与恢复 | `ui/backup/LocalBackupMvpService.kt` | `Core/Backup/LocalBackupService.swift` |
| 锁屏 / 恢复登录 | `ui/lock/LockViewModel.kt` | `Features/Lock/LockViewModel.swift` |
| AI 扫描 | `ai/AiLocalScanUseCase.kt` | `Core/AI/VaultAIAnalysisService.swift` |

---

## 后续补齐建议

- iOS 补齐 `paywall_shown` / `paywall_dismissed`，与 Android 对齐。
- Android / iOS 统一 `backup_create.kind` 的取值口径，建议收敛为 `full` / `incremental` / `manual`。
- 将 `paywall_purchase_fail.error` 收敛为错误类别枚举，避免 SDK 文本变化影响看板聚合。
