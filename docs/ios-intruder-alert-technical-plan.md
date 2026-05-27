# iOS 入侵者警报技术方案

## 1. 背景与目标

入侵者警报用于在 App 锁屏 PIN 解锁失败时，记录一次本机安全事件：失败时间、输入的错误 PIN，以及在用户明确开启并授权相机后的前置摄像头抓拍。该功能只在 iOS 本机离线运行，不上传、不同步、不进入系统相册。

参考截图展示的是一个记录列表页：顶部说明功能用途，列表展示抓拍缩略图、日期时间和尝试 PIN，并支持清除全部。iOS 侧实现应保持 LumaNox 深色安全视觉体系，入口放在「设置 > 安全与隐私」。

## 2. 产品范围

### 2.1 本期包含

- 设置入口：`SettingsSecurityView` 新增「入侵者警报」行，显示开启状态与记录数。
- 独立页面：`IntruderAlertView`，Push 进入，支持内容态、空态、相机权限态。
- 开关：用户在页面内明确开启/关闭入侵者警报。
- 权限：开启时请求相机权限；未授权时展示说明与「打开系统设置」入口。
- 触发：仅在已配置 PIN 且处于普通解锁页 `.unlock` 时，PIN 校验失败后记录。
- 记录字段：时间、尝试 PIN、抓拍状态、加密抓拍文件路径、失败原因。
- 列表：按时间倒序展示本地记录；支持逐条删除和清除全部。
- 安全存储：记录索引与抓拍文件使用本机加密，不写入系统相册。

### 2.2 本期不包含

- 云端同步、远程通知、邮件通知。
- 后台或锁屏系统外抓拍。
- 生物识别失败抓拍。Face ID / Touch ID 失败先保持系统语义，不混入 PIN 错误计数。
- PIN 设置、修改 PIN、恢复备份 PIN 输入失败抓拍。
- Android 侧同步实现。

## 3. UX 与页面结构

### 3.1 入口

`SettingsSecurityView` 的「隐私保护」分组由当前占位文案升级为可点击行：

- 标题：入侵者警报
- 副标题：记录错误 PIN 尝试和本机抓拍
- 右侧：开启/关闭状态或未授权提示

路由新增：

```swift
case intruderAlert
```

并在 `RouteDestinationView` 渲染 `IntruderAlertView()`。

### 3.2 IntruderAlertView 状态

| 状态 | 触发 | 展示 |
|---|---|---|
| Content | 已开启且存在记录 | 顶部说明卡、开关、记录列表、清除全部 |
| Empty | 无记录 | 安全图标、说明、保留开启开关 |
| Permission Denied | 用户开启但相机未授权 | 权限说明卡、打开系统设置、继续仅记录时间和 PIN |
| Disabled | 用户关闭 | 解释关闭后不再抓拍，保留历史记录管理入口 |
| Loading | 初次加载加密索引或缩略图 | 局部 loading，不阻塞页面返回 |
| Error | 索引解密/文件读取异常 | 提供重试和清除损坏记录入口 |

### 3.3 列表信息密度

- 缩略图：56pt 方形，圆角 12pt；加载失败显示 `person.crop.square` 占位。
- 主标题：今天 / 昨天 / 具体日期。
- 副标题：HH:mm。
- 右侧：尝试 PIN。产品需要 review 是否展示完整错误 PIN；默认方案在加密存储下展示完整 6 位错误 PIN，代码实现保留切换为 `••••12` 的能力。
- 删除：iOS 使用行左滑删除；清除全部需二次确认。

## 4. 数据设计

### 4.1 文件位置

| 数据 | 路径 | 说明 |
|---|---|---|
| 加密索引 | `Application Support/LumaNox/intruder_alerts_v1.json.lnx` | 记录数组，加密写入 |
| 加密抓拍 | `Application Support/LumaNox/intruder_alerts/capture_<uuid>.jpg.lnx` | 前置摄像头 JPEG，经 `VaultCipher` 加密 |
| 临时抓拍 | `Caches/LumaNox/intruder_capture_<uuid>.jpg` | 加密完成立即删除 |

### 4.2 模型

```swift
struct IntruderAlertRecord: Codable, Identifiable, Equatable {
    let id: UUID
    let createdAtMs: Int64
    let attemptedPin: String
    let capturePath: String?
    let captureStatus: CaptureStatus
    let failureReason: String?
}

enum CaptureStatus: String, Codable {
    case pending
    case captured
    case permissionDenied
    case unavailable
    case failed
}
```

`attemptedPin` 只保存 PIN 解锁页输入的错误 PIN，不保存正确 PIN，不参与日志输出。索引加密失败时不落盘明文降级。

### 4.3 设置项

扩展 `SecuritySettings`：

```swift
var intruderAlertEnabled: Bool = false
var intruderAlertCaptureEnabled: Bool = false
var intruderAlertRecordLimit: Int = 50
```

`intruderAlertEnabled` 控制是否记录安全事件；`intruderAlertCaptureEnabled` 仅在相机授权成功后为 true。保留 `recordLimit`，避免记录无限增长。

## 5. 模块设计

### 5.1 IntruderAlertStore

位置：`ios/LumaNox/Core/Security/IntruderAlertStore.swift`

职责：

- 加载、解密、发布记录列表。
- 原子写入加密索引。
- 删除单条记录时同步删除加密抓拍文件。
- 清除全部时删除索引与抓拍目录。
- 写入前按 `recordLimit` 裁剪最旧记录。

并发：

- `@MainActor ObservableObject` 提供 UI snapshot。
- 文件读写通过串行 `actor IntruderAlertFileStore` 或单独 serial queue，避免并发写坏索引。

### 5.2 IntruderCaptureService

位置：`ios/LumaNox/Core/Security/IntruderCaptureService.swift`

职责：

- 检查 `AVCaptureDevice.authorizationStatus(for: .video)`。
- 开启时请求权限；触发抓拍时不弹权限框。
- 使用前置摄像头创建一次性 `AVCaptureSession` + `AVCapturePhotoOutput`。
- 输出 JPEG 临时文件，交给 `IntruderAlertStore` 加密保存。

失败降级：

- 权限未授权：记录 `permissionDenied`，不抓拍。
- 设备无前置摄像头或会话启动失败：记录 `unavailable` / `failed`。
- 抓拍异步执行，不阻塞 PIN 输入反馈。

### 5.3 LockViewModel 接入

现有错误 PIN 分支：

```swift
case .unlock:
    if securityStore.verifyPin(pin) {
        ...
    } else {
        let fails = (try? securityStore.recordFailedAttempt()) ?? 1
        ...
    }
```

新增调用点：

```swift
IntruderAlertStore.shared.recordFailedPinAttempt(pin)
```

约束：

- 只在 `.unlock` 分支触发。
- 在 `state` 复位后异步记录，不能延迟错误提示。
- Debug 自动解锁环境变量不触发。
- 日志仅输出 record id 和状态，不输出 PIN。

## 6. 本地化与权限文案

新增本地化 key 进入 `zh-Hans` 与 `en`：

- `settings_intruder_alert_title`
- `settings_intruder_alert_desc`
- `intruder_alert_title`
- `intruder_alert_intro`
- `intruder_alert_enabled`
- `intruder_alert_records`
- `intruder_alert_clear_all`
- `intruder_alert_empty_title`
- `intruder_alert_permission_title`
- `intruder_alert_open_settings`
- `intruder_alert_delete_confirm_title`

`Info.plist` 已有私密相机所需的 `NSCameraUsageDescription` 时复用；如果文案不够覆盖「错误 PIN 后抓拍」，需要更新为更明确的说明，避免 App Review 认为用户未被告知。

## 7. 隐私与安全约束

- 必须用户主动开启；默认关闭。
- 开启页面明确说明「只保存在本机，不进入系统相册」。
- 错误 PIN 与抓拍文件均加密落盘。
- 任何日志、错误消息、analytics 都不得包含 attempted PIN。
- 清除全部必须删除索引和所有抓拍密文文件。
- 记录不进入备份包，除非后续产品明确要求。当前建议不备份，避免把安全事件扩散到外部归档。
- App 切后台保护仍由 `PrivacySnapshotLockView` 负责，警报列表不得出现在 app switcher 明文快照中。

## 8. 实施步骤

1. Review 本方案与 `IntruderAlertView.pen`，确认 PIN 展示策略和默认阈值。
2. 新增 `IntruderAlertView.pen` 已完成，本轮不写 SwiftUI 实现。
3. 通过 review 后实现数据层：`IntruderAlertStore`、加密索引、文件清理测试。
4. 实现抓拍服务：权限、一次性前置抓拍、失败降级。
5. 接入 `LockViewModel` 错误 PIN 分支。
6. 新增 `IntruderAlertView` 和设置入口路由。
7. 补齐本地化、可访问性 identifier、删除确认弹窗。
8. 验证：`xcodegen generate`、`xcodebuild`、iPhone 16 模拟器截图；真机补测相机授权与抓拍。

## 9. 测试计划

| 用例 | 步骤 | 预期 |
|---|---|---|
| IA-01 默认关闭 | 新安装进入设置 | 入侵者警报显示关闭，不会记录错误 PIN |
| IA-02 开启授权 | 页面打开开关并允许相机 | 状态显示已开启 |
| IA-03 错误 PIN 记录 | 锁屏页输入错误 PIN | 列表新增记录，时间和 PIN 正确 |
| IA-04 抓拍成功 | 真机前置摄像头可用 | 记录显示缩略图，文件不进系统相册 |
| IA-05 权限拒绝 | 拒绝相机后输入错误 PIN | 仍记录时间/PIN，缩略图显示权限状态 |
| IA-06 清除全部 | 点击清除全部并确认 | 列表为空，抓拍目录清空 |
| IA-07 正确 PIN | 输入正确 PIN 解锁 | 不新增警报记录 |
| IA-08 非解锁 PIN | 修改 PIN/恢复 PIN 输入错误 | 不新增警报记录 |
| IA-09 记录上限 | 生成超过 50 条 | 自动保留最新 50 条并删除旧抓拍 |

## 10. Review 待确认

1. 错误 PIN 展示：完整显示 6 位，还是默认脱敏只露后 2 位。
2. 记录触发频率：每次错误 PIN 都记录，还是从第 2/3 次失败开始记录。
3. 记录是否进入备份包：当前建议不备份。
4. 设置入口是否仅放在「安全与隐私」，还是首页安全状态也提示记录数。
5. 用户清除记录是否需要 Face ID / PIN 二次验证。
