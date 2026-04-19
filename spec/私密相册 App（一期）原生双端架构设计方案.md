# 私密相册 App（一期）原生双端架构设计方案

文档说明：本方案在一期功能范围与《核心技术方案及实现说明》保持一致的前提下，将技术栈从 Flutter 调整为 **Android（Kotlin）与 iOS（Swift）原生** 双端实现。核心原则不变：**本地离线处理、不上传云端、合规安全、精简可落地**。本文描述分层架构、双端技术映射；**各核心功能点的 Android / iOS 分项说明已拆分为独立文档**（见 `doc/android/`、`doc/ios/` 及第九节索引），含本地 AI 推理选型。**Firebase 监控一期暂不实施**，后续迭代再评估（见第七节）。

---

## 一、设计目标

| 维度 | 要求 |
|------|------|
| **分层** | UI / 应用协调 / 领域 / 数据 / 平台基础设施，依赖单向向内 |
| **职责** | 每层只做一类事，系统差异收敛在 Platform 适配层 |
| **性能** | 主线程只做渲染与轻逻辑；加密、解码、AI、大批量 IO 在后台执行器 |
| **扩展** | 新功能通过用例与接口扩展，避免 UI 直接操作存储 |
| **维护** | 目录结构、命名、依赖注入约定统一；双端特性与接口语义对齐 |
| **隐私** | 业务照片与密钥仍不上传云端；一期不接入第三方监控 SDK；若后续接入 Firebase 等，须隐私披露与用户可控开关 |

---

## 二、总体架构（逻辑分层）

双端采用 **同构分层**，名称可随团队微调，职责建议保持一致。

```
┌─────────────────────────────────────────────────────────┐
│  Presentation（表现层）                                  │
│  Android: Jetpack Compose + ViewModel + Navigation       │
│  iOS:     SwiftUI + Observable / @Observable + Router   │
└──────────────────────────┬──────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────┐
│  Application / Coordinator（应用层）                     │
│  会话、导航、解锁状态机、生命周期锁定、权限引导流程          │
└──────────────────────────┬──────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────┐
│  Domain（领域层）— 强烈建议独立                           │
│  Entity、UseCase、Repository 接口、领域错误               │
│  无 UI、无具体存储与框架实现                               │
└──────────────────────────┬──────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────┐
│  Data（数据层）                                           │
│  Repository 实现、本地 DB、文件仓库、加密管线、DTO 映射     │
└──────────────────────────┬──────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────┐
│  Platform / Infra（平台与基础设施）                      │
│  相册、相机、生物识别、Keychain/Keystore、内购、系统分享    │
│  线程池、日志；（可选）监控 SDK 封装                       │
└─────────────────────────────────────────────────────────┘
```

**依赖规则**：`Presentation → Application → Domain →（接口）← Data`；`Data` 依赖 `Platform`；`Domain` 不依赖 `Data` 的具体实现。

---

## 三、Android 技术映射

| 一期能力 | 建议技术 |
|----------|----------|
| UI | Jetpack Compose、Material 3、Navigation Compose |
| 异步 | Kotlin Coroutines、Flow；加密与 AI 使用 `Dispatchers.Default` 并限流 |
| 本地结构化数据 | Room（或 SQLDelight）；敏感元数据可配合加密或文件级加密策略 |
| 密钥与安全存储 | Android Keystore + Jetpack Security（EncryptedFile / EncryptedSharedPreferences） |
| 系统相册 | MediaStore + Photo Picker（API 33+）等；缩略图缓存（如 Coil） |
| 相机 | CameraX |
| 生物识别 | BiometricPrompt |
| 内购 | **RevenueCat**（底层对接 Google Play Billing） |
| 分享 | `ACTION_SEND` + FileProvider |
| 后台任务 | WorkManager（备份打包、大批量导入收尾） |
| 本地 AI 推理 | TensorFlow Lite（NNAPI / GPU Delegate 按需） |
| 国际化 | `strings.xml` 与 `Locale`，语言列表与 iOS 对齐 |

**模块建议**：`:app`、`:core:domain`、`:core:data`、`:feature:*` 或 `:platform-android` 承载系统适配。

---

## 四、iOS 技术映射

| 一期能力 | 建议技术 |
|----------|----------|
| UI | SwiftUI（复杂图库可局部采用 UICollectionView 桥接） |
| 异步 | `async/await`、`Task`；重计算使用 `Task.detached` 并控制并发 |
| 本地数据 | Core Data 或 GRDB/SQLite |
| 密钥 | Keychain + CryptoKit（与 Android 侧需统一密文格式与 KDF 约定） |
| 系统相册 | Photos、PHPicker；缩略图 `PHCachingImageManager` |
| 相机 | AVFoundation |
| 生物识别 | LocalAuthentication |
| 内购 | **RevenueCat**（底层对接 StoreKit / App Store） |
| 分享 | `UIActivityViewController` |
| 本地 AI 推理 | TensorFlow Lite 或 Core ML（见第六节） |
| 国际化 | Localizable.strings / String Catalog |

**结构建议**：以 Swift Package 或多 Target 划分 `Domain`、`Data`、`Features`、`PlatformIOS`。

---

## 五、核心功能与分层落位（与一期文档对应）

| 功能 | 落位说明 |
|------|----------|
| 相册导入与 AES 加密 | Domain：`ImportPhotos`、`EncryptAsset` 等用例；Data：复制 + AES 管线 + 事务；Platform：相册读取抽象 |
| 解锁与安全 | Application：锁定状态机（Lifecycle / ScenePhase）；Domain：认证策略；Data：哈希与安全存储 |
| 私密拍照 | Platform：相机；Domain：直拍直存、不入系统相册流程；Data：加密与索引 |
| AI 打码 | Domain：检测区域 + 图像处理接口；实现放在 Data 或独立 ML 子模块；推理在后台队列 |
| 海外分享 | Data：解密至临时目录；Platform：系统分享；可选删除临时文件 |
| 备份与恢复 | Domain：打包/解包契约；Data：ZIP + 加密，流式读写控制内存峰值 |
| 内购 | Platform：**RevenueCat**（购买/恢复/CustomerInfo）；Domain：Entitlement → 权益矩阵；Data：可选本地快照 |

加密算法与一期一致时可继续约定：**AES-256-CBC（或双端统一后的算法与参数）**、**SHA-256** 存储口令摘要；密钥仅存 Keychain / Keystore。**各功能在 Android / iOS 上的具体 API 与实现要点见第九节索引及 `doc/android/`、`doc/ios/` 下分项文档。**

---

## 六、本地 AI 推理：TensorFlow Lite 与 Core ML（不自训模型）

一期差异化能力为 **本地离线目标检测 + 图像打码**。在不自行训练模型的前提下，重点在 **现成模型转换与双端部署**，而非训练框架。

### 6.1 对比摘要

| | TensorFlow Lite | Core ML |
|---|-----------------|---------|
| 平台 | Google，**Android + iOS 均可** | **仅 Apple**（iOS/macOS 等） |
| 模型形态 | `.tflite` | `.mlmodel` / `.mlpackage` |
| 与双端关系 | **单一模型产物可服务双端**，维护成本低 | **无法覆盖 Android**；双端需 TFLite + Core ML 两套或 iOS 单独维护 |
| 生态（不自训） | PyTorch/TF/ONNX → TFLite 路径成熟，INT8 量化与样例多 | `coremltools` 转换，部分算子需调整网络或固定 shape |
| iOS 性能 | Metal Delegate 等，需实测 | 通常易发挥 Neural Engine + Metal，单 iOS 延迟/功耗往往更易优化 |
| Android | 主选，NNAPI/GPU 文档全 | 不适用 |

### 6.2 选型建议

- **默认推荐**：**双端统一使用 TensorFlow Lite**，只维护 **一种** `.tflite` 与同一套量化策略，双端行为对齐与回归成本最低。
- **可选进阶**：若 iOS 单独存在性能/功耗瓶颈且团队接受双份模型，可采用 **iOS Core ML + Android TFLite**，并用同一验证集对齐输出（阈值、NMS 等需统一文档化）。

---

## 七、可观测性与 Firebase（一期暂缓）

**一期决策**：**不集成 Firebase**（含 Crashlytics、Performance、Analytics、Remote Config）。上线前依赖 **系统日志（Logcat / OSLog）**、真机测试与商店崩溃报告（若有）；必要时在工程内保留 **Logger / 非敏感诊断接口**，便于后续接入。

**后续若接入 Firebase**，建议仍遵守以下原则（与历史方案一致，供复用）：

- **范围**：仅可观测性与稳定性，**不上传用户照片与密钥**；Analytics 最小化并支持退出。
- **集成方式**：放在 Infrastructure 模块，通过 `AnalyticsService`、`CrashReporter`、`PerformanceTracer` 等 **接口** 注入，`Domain` 不直接依赖 Firebase。
- **双端约定**：业务事件名与 Performance Trace 名使用统一常量表；隐私政策与 Data Safety / 隐私清单同步更新。

分项说明仍保留在 `doc/android/11-Firebase监控.md`、`doc/ios/11-Firebase监控.md`，标注为暂缓，实施时以当时 SDK 文档为准。

---

## 八、高性能与可维护性要点

1. **线程**：主线程仅 UI；加密、TFLite、图像编解码在后台队列；相册列表分页 + 缩略图缓存。
2. **数据与事务**：批量导入分批提交或单事务；避免每张照片触发全表扫描。
3. **内存**：大图按目标尺寸解码；AI 输入 tensor 尽量复用缓冲区。
4. **测试**：Domain 纯逻辑单测；Data 使用内存 Fake；Platform 使用假相册/相机。
5. **双端对齐**：加密格式、数据库 schema 版本、备份 ZIP 规范、错误码表 **文档化**，保证备份可跨端恢复（若产品需要）。

---

## 九、核心功能点双端技术方案（分项文档索引）

按一期《核心技术方案及实现说明》中的功能模块，**Android**、**iOS** 各一份独立说明；双端 **业务语义、数据契约（密文格式、DB 字段、备份结构）** 须保持一致，仅系统 API 与工程细节不同。

**目录入口**

- **Android（Kotlin）**：[doc/android/README.md](./android/README.md)
- **iOS（Swift）**：[doc/ios/README.md](./ios/README.md)

**功能与文档对照（同编号双端各一份）**

| 编号 | 功能 | Android | iOS |
|------|------|---------|-----|
| 01 | 本地图片库（系统相册读取） | [01-本地图片库-系统相册读取.md](./android/01-本地图片库-系统相册读取.md) | [01-本地图片库-系统相册读取.md](./ios/01-本地图片库-系统相册读取.md) |
| 02 | 照片导入与 AES 本地加密 | [02-照片导入与AES本地加密.md](./android/02-照片导入与AES本地加密.md) | [02-照片导入与AES本地加密.md](./ios/02-照片导入与AES本地加密.md) |
| 03 | 解锁与安全模块 | [03-解锁与安全模块.md](./android/03-解锁与安全模块.md) | [03-解锁与安全模块.md](./ios/03-解锁与安全模块.md) |
| 04 | 私密拍照 | [04-私密拍照.md](./android/04-私密拍照.md) | [04-私密拍照.md](./ios/04-私密拍照.md) |
| 05 | 相册管理 | [05-相册管理.md](./android/05-相册管理.md) | [05-相册管理.md](./ios/05-相册管理.md) |
| 06 | AI 打码 | [06-AI打码.md](./android/06-AI打码.md) | [06-AI打码.md](./ios/06-AI打码.md) |
| 07 | 海外社交分享 | [07-海外社交分享.md](./android/07-海外社交分享.md) | [07-海外社交分享.md](./ios/07-海外社交分享.md) |
| 08 | 多语言国际化 | [08-多语言国际化.md](./android/08-多语言国际化.md) | [08-多语言国际化.md](./ios/08-多语言国际化.md) |
| 09 | 备份与恢复 | [09-备份与恢复.md](./android/09-备份与恢复.md) | [09-备份与恢复.md](./ios/09-备份与恢复.md) |
| 10 | 内购付费 | [10-内购付费.md](./android/10-内购付费.md) | [10-内购付费.md](./ios/10-内购付费.md) |
| 11 | Firebase 监控（**一期暂缓**） | [11-Firebase监控.md](./android/11-Firebase监控.md) | [11-Firebase监控.md](./ios/11-Firebase监控.md) |

---

## 十、与 Flutter 版说明文档的关系

- 《核心技术方案及实现说明》中的 **功能范围、合规原则、算法与安全策略** 仍适用。
- 本文替代其中 **跨平台技术栈（Flutter/插件）** 的描述，改为 **原生双端实现路径**；插件名映射见第三、四节，**分项实现见第九节索引及 `doc/android/`、`doc/ios/`**。

> 本文档随实现迭代更新；若加密算法或 AI 管线变更，须同步修订第六节、第九节索引及对应分项文档与备份格式说明。若恢复 Firebase 接入，同步修订第七节与 `11-Firebase监控` 分项。
