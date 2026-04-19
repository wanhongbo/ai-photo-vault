# 私密相册 App（一期）Android 端开发计划

文档目标：基于《私密相册 App（一期）原生双端架构设计方案》与 `doc/android/` 分项技术方案，输出 **Android（Kotlin）原生** 可执行的开发计划，用于排期、任务拆解、测试验收与 Google Play 上架准备。

**关联文档**

- 总览：[私密相册 App（一期）原生双端架构设计方案.md](./私密相册%20App（一期）原生双端架构设计方案.md)
- Android 分项：`doc/android/README.md`（01～11）
- 产品范围参考：[私密相册 App（一期）开发计划与实施方案.md](./私密相册%20App（一期）开发计划与实施方案.md)（一期 In/Out of Scope 与数据模型）

---

## 一、一期目标与 Android 技术基线

### 1.1 目标

- 交付可上架 Google Play 的一期 MVP：**本地离线、AES 加密存储、私密拍照、相册管理、AI 打码、分享、备份恢复、内购、多语言**。
- 工程上落实 **分层架构**（Presentation / Application / Domain / Data / Platform），关键路径 **主线程不阻塞**。

### 1.2 技术基线（建议）


| 类别     | 选型                                                                            |
| ------ | ----------------------------------------------------------------------------- |
| 语言与异步  | Kotlin 1.9+，Coroutines + Flow                                                 |
| UI     | Jetpack Compose、Material 3、Navigation Compose                                 |
| 依赖注入   | Hilt（或 Koin，团队统一即可）                                                           |
| 本地数据库  | Room + KSP                                                                    |
| 安全     | Android Keystore、Jetpack Security（EncryptedSharedPreferences / EncryptedFile） |
| 相册与选图  | MediaStore、Photo Picker（`ActivityResultContracts`）、Coil                       |
| 相机     | CameraX                                                                       |
| 生物识别   | BiometricPrompt                                                               |
| 本地 AI  | TensorFlow Lite（NNAPI / GPU Delegate 按需）                                      |
| 分享     | FileProvider + `ACTION_SEND`                                                  |
| 备份大任务  | WorkManager（含前台服务策略按系统版本处理）                                                   |
| 内购     | **RevenueCat**（统一订阅；底层 Google Play Billing，不在业务层直接接 `BillingClient`） |
| 监控     | **一期不做 Firebase**；开发期用 Logcat/Profiler，上架后可用 Play 控制台崩溃报告 |
| 最低 SDK | 与产品一致（建议 API 26+，若需更广覆盖可再评估）                                                  |
| 目标 SDK | 随商店要求（当前以 Google Play 政策为准）                                                   |


### 1.3 与双端共用的约定

- **密文文件格式、Room 元数据字段、备份 ZIP 结构** 与 iOS 对齐文档化，避免备份无法跨端恢复（若产品需要）。
- **AES / SHA-256、免费版限额、会员权益矩阵** 与产品/《开发计划与实施方案》一致。

---

## 二、工程与模块规划

### 2.1 建议模块

- `:app`：Application、主题、导航、DI 入口
- `:core:domain`：实体、用例接口、Repository 抽象
- `:core:data`：Room、加密管线、Repository 实现、TFLite 封装
- `:core:ui`（可选）：通用 Compose 组件
- `:feature:`*：按功能垂直拆分（如 `feature-auth`、`feature-vault`、`feature-ai` 等），或先单模块迭代再拆分

### 2.2 一期交付工程要求

- `dev` / `prod` 环境与开关（含 **RevenueCat** API Key、商店内购测试账号；**不含 Firebase 一期**）
- ProGuard/R8 规则与 Release 映射文件保留，便于 Play 符号化与后续接监控
- 权限与 **数据安全（Data Safety）** 表单所需说明与代码行为一致

---

## 三、里程碑计划（建议 8 周，可按人力压缩/延长）

### W1：项目基建与安全底座


| 任务 | 产出 |
|------|------|
| 创建多模块工程、Compose + Navigation、Hilt、**统一 Logger 与全局异常边界**（不落敏感数据） | 可运行空壳 App |
| **不接 Firebase**（见 `11-Firebase监控` 暂缓说明） | — |
| Keystore 生成主密钥、封装 `Cipher`/`AES` 工具；口令 **SHA-256** 哈希 | 加密单测通过 |
| Room 初始化、迁移策略占位；实体与《开发计划》数据模型对齐 | DB 可写入 |


**验收**：冷启动无崩溃；加密/解密单元测试通过；日志策略符合「不含照片与密钥」。

---

### W2：认证门禁、权限与生命周期锁定


| 任务                                                       | 产出                |
| -------------------------------------------------------- | ----------------- |
| 锁屏 UI、PIN/图案输入、密码哈希持久化                                   | `03-解锁与安全模块` 基础能力 |
| `BiometricPrompt` 接入，失败降级 PIN                            | 生物识别闭环            |
| `ProcessLifecycleOwner`：进入后台锁定（策略与产品确认 ON_STOP/ON_PAUSE） | 后台锁稳定             |
| 相册读权限 / Photo Picker 流程与文案                               | `01-本地图片库` 权限链路   |


**验收**：杀进程/切后台再进需解锁；生物识别失败可回退；权限拒绝路径有引导。

---

### W3：导入加密与私密拍照


| 任务                                          | 产出                |
| ------------------------------------------- | ----------------- |
| MediaStore / Picker 选图、分页或分批加载列表、Coil 缩略图   | 选图列表流畅            |
| `Uri` → 临时文件 → AES 加密 → Room 元数据事务          | `02-照片导入与AES本地加密` |
| CameraX 预览与拍照，JPEG 仅进加密管线，**不写 MediaStore** | `04-私密拍照`         |
| 主线程仅 UI；加密在 `Dispatchers.IO`/`Default`      | 无明显卡顿             |


**验收**：批量导入 50～100 张成功率与进度可见；私密拍照不入系统相册（用文件浏览器/相册 App 抽查）。

---

### W4：相册管理、回收站与导出


| 任务                                              | 产出        |
| ----------------------------------------------- | --------- |
| 自定义相册 CRUD、照片列表、预览（缩放）                          | `05-相册管理` |
| 批量移动、删除进回收站；`deletedAt` + 30 天；WorkManager 过期清理 | 回收站逻辑     |
| 解密到临时文件 → `MediaStore` 导出 → 删临时文件               | 导出到系统相册   |


**验收**：回收站恢复与过期删除正确；导出后系统相册可见且临时文件删除。

---

### W5：AI 打码


| 任务                                            | 产出        |
| --------------------------------------------- | --------- |
| 集成 INT8 TFLite 模型、输入输出 tensor 与预处理            | `06-AI打码` |
| NMS、坐标映射回原图；ROI 模糊/马赛克（RenderEffect/Canvas 等） | 打码强度可调    |
| 新图保存策略（不覆盖原密文）、可选入相册或仅分享                      | 与产品一致     |


**验收**：推理在后台线程；多张连续打码不 OOM；与 Domain 层「每日次数/水印」挂钩（见 W7 可提前占位）。

---

### W6：分享、备份恢复、多语言


| 任务                                                                                 | 产出          |
| ---------------------------------------------------------------------------------- | ----------- |
| FileProvider + `ACTION_SEND`；分享后删临时文件                                              | `07-海外社交分享` |
| 加密 ZIP 导出/导入；SAF `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`；大文件 WorkManager | `09-备份与恢复`  |
| `strings.xml` 多语言包；`AppCompatDelegate.setApplicationLocales`（API 26+ 策略统一）         | `08-多语言国际化` |


**验收**：分享 WhatsApp/Telegram 等抽样通过；备份包在卸载重装后可恢复（同版本格式）；语言切换覆盖主要页面。

---

### W7：内购与权益


| 任务 | 产出 |
|------|------|
| 接入 **RevenueCat** Android SDK：`Purchases.configure`、Offerings、购买/恢复、`CustomerInfo` → Entitlement | `10-内购付费` |
| RevenueCat 后台与 Play Console 商品 ID、订阅基础方案绑定；与 iOS **同一项目** | Dashboard 配置文档化 |
| Domain 层根据 Entitlement 控制存储张数、每日 AI 次数、水印、伪装图标/闯入抓拍等 | 与产品矩阵一致 |
| 可选：RevenueCat **Webhooks** 或服务端 API 做二次校验（视风控） | 视需求 |

**验收**：Google Play 测试轨道订阅/升级/取消；**恢复购买**；免费版限制与付费权益与 iOS 一致；隐私政策与 Data Safety 含 RevenueCat 披露。

---

### W8：稳定性、性能与上架


| 任务                                       | 产出          |
| ---------------------------------------- | ----------- |
| 启动、列表滚动、导入、打码的 Performance Trace；内存与泄漏排查 | 指标达标        |
| 混淆、ABI 分包、资源压缩；包体积评估                     | Release AAB |
| Data Safety、权限声明、商店截图与英文说明               | 提审材料        |
| 全量回归与 Blocker 修复                         | 提审版         |


**验收**：满足《开发计划与实施方案》5.2 中与 Android 相关项（批量导入成功率、锁定、回收站、内购等）。

---

## 四、任务拆解（Android 按模块）

### 4.1 安全与解锁（`03`）

- 锁屏与解锁状态机（含应用从后台恢复）
- 连续失败计数、闯入抓拍（付费，CameraX 前置）
- 伪装图标（付费，activity-alias / 组件开关），注意政策合规

### 4.2 导入与加密（`01` `02`）

- Photo Picker 与全库浏览两种模式的产品分支
- 批量导入队列、失败重试、事务边界

### 4.3 相册与媒体（`04` `05`）

- 大图解码尺寸控制、缩略图缓存
- 预览页手势与解密临时文件生命周期

### 4.4 AI（`06`）

- 模型版本与功能开关：**本地配置为主**（一期不接 Firebase Remote Config）
- 与相册流水线衔接：选图 → 检测 → 打码 → 存新密文

### 4.5 商业化（`10`）

- **RevenueCat**：`CustomerInfo` 监听与 Entitlement 映射；Offering 展示与购买错误处理
- 与 iOS 共用 Entitlement 命名；本地仅作体验用缓存，以 RevenueCat + 商店为准

### 4.6 可观测性（`11`，一期）

- **不集成 Firebase**；关键路径用 Logger + 必要时的 **非致命错误本地计数**（不含用户内容）
- 后续若接 Firebase：再实现 `11-Firebase监控` 中的接口注入方案

---

## 五、测试与验收（Android）

### 5.1 测试类型

- **单元测试**：加密解密、哈希、权益计算、备份包头解析
- **仪器测试**：Room 迁移、FileProvider、Biometric 模拟（环境允许时）
- **真机矩阵**：Pixel + 三星/小米/OPPO/vivo 中低端各 1；系统覆盖最低 SDK～最新稳定版若干档

### 5.2 一期建议验收项（摘录）

- 批量导入 100 张成功率 ≥ 99%
- 加密、AI 打码时主线程无持续阻塞（Systrace/Profiler）
- 后台锁定无遗漏（含分屏、小窗若支持）
- 内购与恢复购买稳定

---

## 六、风险与预案（Android）


| 风险                      | 预案                                |
| ----------------------- | --------------------------------- |
| 厂商后台限制导致 WorkManager 不准 | 关键任务用户可见进度；备份/恢复说明；前台服务按政策使用      |
| 分区存储与权限变更               | 跟踪 targetSdk；优先 Photo Picker 降低权限 |
| TFLite 机型差异             | NNAPI/GPU 回退 CPU；固定输入尺寸与超时        |
| Play 审核（权限、误导性图标、数据安全）  | 文案与实现一致；备用图标功能说明充分                |
| OEM ROM 生物识别差异          | 充分真机测；降级路径始终可用                    |


---

## 七、人力与协作（建议）

- **Android 开发**：1～2 人（视是否并行 iOS）
- **测试**：0.5～1 人（真机为主）
- **产品/设计**：沿用《开发计划与实施方案》节奏；W2 末 **范围冻结评审** 建议保留

---

## 八、交付物（Android）

- Google Play **AAB**（含内购测试轨道说明）
- 权限与 **Data Safety**、隐私政策（英文）链接
- 测试报告（矩阵、缺陷清单）
- 与 iOS 对齐的 **备份格式与版本号** 说明（若双端互备）

---

> 本文档随实现迭代更新；里程碑周数可根据团队规模调整，但 **依赖顺序**（安全底座 → 权限与锁 → 导入加密 → 管理 → AI → 分享备份语言 → 内购 → 上架）不建议倒置。

