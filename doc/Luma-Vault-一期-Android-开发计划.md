# Luma Vault（一期）Android 端开发计划

文档目标：在《私密相册 App（一期）原生双端架构设计方案》与 `doc/android/` 分项方案基础上，输出 **Luma Vault** Android（Kotlin）一期可执行开发计划，含 **Google Play 上架配置清单** 与 **RevenueCat 配置清单**。用于排期、验收与商店/内购后台对齐。

**产品名称（商店展示）**：**Luma Vault**（与包名、控制台名称保持一致，避免与通用「Photo Vault」混淆。）

**关联文档**

- 总览：[私密相册 App（一期）原生双端架构设计方案.md](./私密相册%20App（一期）原生双端架构设计方案.md)
- Android 分项：`doc/android/README.md`（01～11）
- 内购技术说明：[doc/android/10-内购付费.md](./android/10-内购付费.md)

---

## 一、一期范围说明（In / Out of Scope）

### 1.1 一期 **包含**

- 本地离线、AES 加密存储、私密拍照、相册管理、回收站、导出到系统相册  
- AI 相关能力（与当前工程对齐：分类、搜索、隐私打码、压缩、去重等，以实际迭代为准）  
- 海外社交分享（系统分享面板）  
- 加密备份导出 / 恢复、多语言  
- **内购**：RevenueCat + Google Play（**月/年订阅** + **一次性买断**，商品策略见 [`支付功能-开发计划与配置清单.md`](./android/支付功能-开发计划与配置清单.md)）  
- 解锁：PIN/图案、生物识别、后台自动锁定

### 1.2 一期 **不包含（暂不实现）**


| 能力                                           | 说明                                                               |
| -------------------------------------------- | ---------------------------------------------------------------- |
| **伪装图标**（Alternate / Disguise launcher icon） | 不进入一期开发；**权益矩阵与 Paywall 文案中不得承诺**该能力。若历史文档或占位字符串提及，需删除或改为「后续版本」。 |
| **闯入抓拍**（解锁失败拍照等）                            | 不进入一期开发；同上，不与 Premium 权益绑定。                                      |


后续若纳入 roadmap，需单独 PRD、隐私与商店合规评审后再排期。

### 1.3 技术基线（摘要）


| 类别      | 选型                                                        |
| ------- | --------------------------------------------------------- |
| 语言 / 异步 | Kotlin，Coroutines + Flow                                  |
| UI      | Jetpack Compose、Material 3、Navigation Compose             |
| DI      | Hilt                                                      |
| 数据库     | Room + KSP                                                |
| 安全      | Android Keystore、加密存储抽象                                   |
| 相册 / 选图 | MediaStore、Photo Picker、Coil                              |
| 相机      | CameraX                                                   |
| 生物识别    | BiometricPrompt                                           |
| 本地 AI   | TensorFlow Lite（或工程当前方案）                                  |
| 分享      | FileProvider + `ACTION_SEND`                              |
| 重任务     | WorkManager（备份等按政策处理前台服务）                                 |
| 内购      | **RevenueCat**（底层 Play Billing，业务层不接 `BillingClient` 主流程） |
| 监控      | **一期不接 Firebase**；依赖 Play 控制台崩溃与本地日志策略                    |


**双端约定**：密文/备份格式、Room 字段、**免费版限额与 Premium 权益** 与 iOS 及产品文档一致；**不包含**伪装/闯入抓拍相关字段或开关。

---

## 二、工程与模块规划

- `:app`：Application、主题、导航、DI  
- `:core:domain` / `:core:data`：用例、Repository、Room、加密管线  
- 功能可按垂直模块演进，与现有仓库结构对齐即可  
- **Build 变体**：`dev` / `prod` 注入 `REVENUECAT_API_KEY` 等；Release 保留 R8 映射

---

## 三、里程碑计划（建议 8 周，可按人力调整）

### W1：基建与安全底座

多模块工程、Compose、Navigation、Hilt、Logger；Keystore + AES 工具与单测；Room 与迁移策略。

**验收**：冷启动稳定；加密单测通过；日志不含照片与密钥。

### W2：认证门禁、权限、生命周期锁定

锁屏、PIN/图案、生物识别降级；`ProcessLifecycleOwner` 后台锁；相册读权限与 Photo Picker。

**验收**：杀进程/切后台再进需解锁；权限拒绝路径可引导。

### W3：导入加密与私密拍照

Picker → 加密写入 Room；CameraX 仅写入 Vault，**不写系统相册**。

**验收**：批量导入成功率与进度可见；抽查系统相册无新增。

### W4：相册管理、回收站与导出

相册 CRUD、预览、回收站与过期清理；解密导出到 MediaStore 并清理临时文件。

**验收**：回收站与导出路径正确。

### W5：AI 打码与相关流程

模型集成、检测与打码管线、后台线程与内存控制；与 Domain「次数 / 水印」预留挂钩。

**验收**：连续处理多张不 OOM；主线程无明显阻塞。

### W6：分享、备份恢复、多语言

FileProvider 分享；加密 ZIP 备份与 SAF；`strings.xml` 多语言与应用内语言策略。

**验收**：分享抽样通过；同版本备份可恢复；主要界面已本地化。

### W7：内购与权益（**不含**伪装 / 闯入抓拍）


| 任务                                                                                | 产出                |
| --------------------------------------------------------------------------------- | ----------------- |
| RevenueCat SDK：`Purchases.configure`、Offerings、购买/恢复、`CustomerInfo` → Entitlement | `10-内购付费` 落地      |
| Play Console 商品与 RevenueCat Product 绑定；Dashboard 与本文 **附录 B** 对齐                  | 可测试购买             |
| Domain：`SubscriptionRepository` + 权益矩阵（**存储上限、分享次数、水印、AI 配额、备份能力等**）              | UI 与用例统一入口        |
| Paywall（月/年/买断）、设置「会员 / 订阅」页、恢复购买、订阅用户跳转 Play 订阅管理                               | 与 Luma Vault 文案一致；买断与订阅区分展示 |


**验收**：内部测试轨道完成 **订阅（续费/取消）**、**买断购买**、**恢复购买**；免费限制与 Premium 与产品矩阵一致；**权益描述无伪装图标与闯入抓拍**；隐私政策与 Data Safety 披露 RevenueCat。

### W8：稳定性、性能与上架

Profiler 与内存；Release AAB；Data Safety、权限、商店素材；全量回归。

**验收**：满足一期核心验收项；**附录 A** 商店清单逐项完成。

---

## 四、任务拆解要点（与一期范围对齐）

### 4.1 安全与解锁（`03`）

- 锁屏状态机、失败计数与锁定策略（**不做**失败抓拍上传/存证类功能）。  
- **不做** `activity-alias` 伪装图标与多图标切换。

### 4.2 商业化（`10`）

- Entitlement 命名与 iOS / RevenueCat 项目一致（如 `premium`）。  
- 本地 `SubscriptionState` 仅作缓存展示，**以商店 + RevenueCat 为准**。

### 4.3 风险与预案（节选）


| 风险                  | 预案                      |
| ------------------- | ----------------------- |
| WorkManager 在厂商后台受限 | 备份/恢复进度可见；说明文档；合规使用前台服务 |
| Play 审核（权限、数据安全）    | 文案与实现一致；敏感权限逐项说明用途      |
| TFLite 机型差异         | 委托与回退 CPU；超时与输入尺寸固定     |


（一期已移除「误导性图标」类风险项对应功能。）

---

## 五、测试与交付物

- 单元测试：加密、哈希、权益计算、备份格式。  
- 真机矩阵：主流 OEM + 最低～目标 SDK 若干档。  
- 交付：Play **AAB**、Data Safety 与隐私政策链接、测试报告、与 iOS 对齐的备份/权益说明（若双端互备）。

---

## 附录 A：Google Play（应用市场）配置清单

在 **Google Play Console** 中按下列项勾选；负责人与日期建议在项目看板单独跟踪。

### A.1 应用身份与签名

- **应用名称**：Luma Vault（与品牌一致）  
- **默认语言**（商店列表）与 **应用内语言**策略一致  
- **软件包名**（`applicationId`）最终确定且与各环境一致  
- **上传密钥 / 应用签名**：Play App Signing 已启用；本地 CI 保存加密 keystore  
- **目标 API 级别**满足 Play 当期要求

### A.2 商店详情

- 短说明、完整说明（英文为主；若多市场再补本地化）  
- 图标 512×512、**功能图**、手机截图（按规范尺寸与设备框）  
- **隐私政策 URL**（可访问 HTTPS，内容与 Data Safety 一致）  
- **联系邮箱**（或支持 URL）

### A.3 政策与问卷

- **数据安全**表单：逐项对应实际 SDK 与权限（含 **RevenueCat** 数据类型说明）  
- **内容分级**问卷完成  
- **目标受众**与家庭政策（若适用）  
- **新闻应用 / COVID / 贷款等**声明均为「不适用」或如实填写  
- **广告声明**：若应用无广告，选择无广告，且与内购页文案一致

### A.4 权限与功能声明

- 声明的每个危险权限在说明与隐私政策中有 **使用目的**  
- Photo Picker 优先，减少 broad gallery 权限依赖（与实现一致）  
- 相机、生物识别、存储/媒体权限与功能一一对应

### A.5 应用内商品（与 RevenueCat 一致）

- 在 Play Console 创建 **订阅**（月 / 年，Base plan + 可选 offer：试用、introductory 等）  
- 创建 **一次性商品（非消耗型）** — **买断**，与订阅共用同一 RC Entitlement（商品 ID 与附录 B、[`支付功能-开发计划与配置清单.md`](./android/支付功能-开发计划与配置清单.md) §3.0 一致）  
- 各商品 ID 与 RevenueCat **Product identifier** 完全一致  
- **许可测试员** 账号已添加，用于沙盒购买验证

### A.6 发布轨道与版本

- **内部测试** → **封闭测试**（可选）→ **正式版** 流程跑通至少一轮  
- 版本号（`versionCode`）单调递增；Release notes  
- 国家/地区上架范围与定价策略确认

### A.7 法律与品牌

- 应用内「服务条款 / 用户协议」链接（若产品要求）  
- 商标「Luma Vault」在控制台与素材中拼写统一（避免 Valut 等笔误）

---

## 附录 B：RevenueCat 配置清单

在 [RevenueCat Dashboard](https://app.revenuecat.com/) 与 Play Console **交叉核对**；建议与 iOS 使用 **同一 RevenueCat 项目** 以便跨端 Entitlement 一致。

### B.1 项目与应用

- 创建/选定 **Project**（建议命名包含 Luma Vault 或内部代号）  
- 添加 **Google Play** 应用：包名与 Console 完全一致  
- 填写 **Service credentials**（Play Developer API / 服务端账号 JSON，按 RevenueCat 当前文档）  
- **Public API Key**：区分 **Test / Production**（或按 RevenueCat 推荐的环境键），写入 Android `BuildConfig` / 安全注入，**勿提交生产密钥到公开仓库**

### B.2 商品与权益

- 在 Play Console 创建 **订阅 + 一次性买断** 商品后，在 RevenueCat 添加 **Products** 并关联 Store  
- 创建 **Entitlement**（示例：`premium`）— 月订、年订、**买断** 三个 Product 均 attach 到此 Entitlement；一期权益**不包含**伪装/闯入抓拍子项  
- 创建 **Offering**（如 `default`），当前套餐包 **Available**  
- **Packages**：`$rc_monthly`、`$rc_annual`、**`$rc_lifetime`（买断）** 与客户端读取逻辑一致（以工程 `Offerings` 解析为准，详见 [`支付功能-开发计划与配置清单.md`](./android/支付功能-开发计划与配置清单.md) §3.0）

### B.3 客户端对齐（Android）

- `Purchases.configure` 在 `Application` 初始化一次  
- 购买、恢复、`CustomerInfo` 更新监听与 **Domain `SubscriptionRepository`** 映射完成  
- 错误码映射用户可读文案（网络、取消、已拥有等）  
- **不向 RevenueCat 发送**用户照片、密钥、备份内容

### B.4 双端与运营（可选）

- iOS App Store Connect 商品加入 **同一 Entitlement**  
- （可选）**Webhooks** / 服务端校验：视风控与团队规范  
- Dashboard 中 **Offering** 变更流程：谁可改、如何回归测试

### B.5 验收用例（内购）

- 新用户购买月订 / 年订成功，`CustomerInfo` 中 Entitlement active  
- 升级 / 降级 / 取消续费后状态符合商店规则（以 RevenueCat 文档为准）  
- **恢复购买** 成功  
- 离线短时：缓存行为可接受；恢复网络后状态刷新正确

---

## 文档维护

- 一期范围以本文 **第一节** 为准；若产品恢复「伪装/闯入抓拍」，需更新 In/Out、附录 A/B 及 Paywall 文案后再开发。  
- 商店与 RevenueCat 政策以 **Google Play / RevenueCat 官方文档当期版本** 为准，清单项可随政策增删。