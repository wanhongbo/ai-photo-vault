# 成熟三方库推荐（Android / iOS）

文档目的：对应一期各**功能点**，列出可优先采用的**成熟开源或官方 SDK**，减少从零实现成本。原则：**优先官方组件**，三方库选维护活跃、许可清晰（MIT/Apache 等）的项目；敏感路径（密钥、加密）仍以 **Keystore / Keychain + 团队审计过的用法** 为准。

**说明**：下列「必选/推荐/可选」为实施建议，最终以团队评审与许可合规为准。

---

## 1. 本地图片库（系统相册读取、缩略图）

| 端 | 推荐 | 说明 |
|----|------|------|
| **Android** | **Coil**（或 **Glide**） | 异步加载 `content://` 缩略图、内存/磁盘缓存、与 Compose 配合成熟。系统 **Photo Picker**、**MediaStore** 为官方能力，无需自研相册协议。 |
| **Android** | **AndroidX Paging 3** | 大量照片分页，官方方案。 |
| **iOS** | **Nuke** / **Kingfisher** / **SDWebImage** | 缩略图缓存与解码；**Photos** + **PHCachingImageManager** 仍为展示与枚举主路径，库负责图像管线优化。 |

---

## 2. 照片导入与 AES / 安全存储

| 端 | 推荐 | 说明 |
|----|------|------|
| **Android** | **Jetpack Security**（`EncryptedFile`、`EncryptedSharedPreferences`） | Google 官方，与 Keystore 结合。 |
| **Android** | **Google Tink**（可选） | 更高层加密 API，需评估与现有 AES 文件格式是否一致。 |
| **iOS** | **CryptoKit**（系统） | 首选；与 Android 字节级对齐时用 **CommonCrypto** 封装层。 |
| **双端** | 避免自研「新加密算法」 | AES 模式、IV、文件头格式在文档中固定，用系统/经过验证的库实现。 |

---

## 3. 解锁与安全（生物识别以外）

| 端 | 推荐 | 说明 |
|----|------|------|
| **Android** | **AndroidX Biometric**（`BiometricPrompt`） | 官方。 |
| **iOS** | **LocalAuthentication**（系统） | 官方。 |
| **PIN/图案 UI** | 可选用成熟 **PIN 键盘/图案锁组件**（GitHub 检索 `pinview` `pattern lock` + Stars/License）或自绘 | 注意 accessibility 与防截屏策略需产品定。 |

---

## 4. 私密拍照

| 端 | 推荐 | 说明 |
|----|------|------|
| **Android** | **CameraX** | 官方，覆盖预览/拍照/用例绑定。 |
| **iOS** | **AVFoundation**（系统） | 官方；如需快速封装可参考 **NextLevel** 等（评估维护状态）。 |

---

## 5. 相册管理（数据库、列表）

| 端 | 推荐 | 说明 |
|----|------|------|
| **Android** | **Room** + **KSP** | 官方 ORM；迁移用 **AutoMigration** 或手写 Migration。 |
| **iOS** | **GRDB**（或 **Realm**） | GRDB：SQLite 封装强、Swift 友好；Realm 省心但迁移与包体需评估。 |
| **iOS** | **Core Data** | Apple 官方，学习曲线略高。 |

---

## 6. AI 打码（推理 + 图像处理）

| 端 | 推荐 | 说明 |
|----|------|------|
| **双端** | **TensorFlow Lite** 官方 Runtime | 模型推理；Task Library 视模型类型选用。 |
| **Android** | **TensorFlow Lite Support** / **Task Vision** | 图像预处理辅助。 |
| **iOS** | **TensorFlow Lite Swift**（或 **Core ML** 若单独转换） | 与总览文档选型一致。 |
| **Android 图像** | **RenderEffect**（API 31+）/ **Blurry** 等模糊库（需评估） | ROI 马赛克常需少量自研像素处理 + Canvas。 |
| **iOS 图像** | **Core Image**（`CIFilter`） | 系统级模糊/像素化，减少自研图像管线。 |

---

## 7. 海外社交分享

| 端 | 推荐 | 说明 |
|----|------|------|
| **Android** | **FileProvider** + `Intent.ACTION_SEND`（系统） | 标配；注意 `FileProvider` 模板与路径权限。 |
| **iOS** | **`UIActivityViewController`**（系统） | 标配。 |

---

## 8. 多语言国际化

| 端 | 推荐 | 说明 |
|----|------|------|
| **Android** | **资源限定符** + **AppCompat 多语言**（`setApplicationLocales`） | 官方。 |
| **iOS** | **String Catalog** / **Localizable.strings** | 官方。 |
| **双端（可选）** | **Lokalise** / **Phrase** 等 TMS | 翻译流程与 CI 拉文案，非运行时库。 |

---

## 9. 备份与恢复（ZIP、加密）

| 端 | 推荐 | 说明 |
|----|------|------|
| **Android** | **zip4j**（密码 ZIP）或 **Apache Commons Compress** + 自定义 AES 封装 | 按与 iOS 统一的格式选型；大文件注意流式读写。 |
| **Android** | **存储访问框架（SAF）** | 系统 API，非库。 |
| **iOS** | **ZIPFoundation** | Swift 常用；加密层需与 Android 对齐。 |
| **iOS** | **UIDocumentPicker** | 系统 API。 |

---

## 10. 内购（已选 RevenueCat）

| 端 | 推荐 | 说明 |
|----|------|------|
| **双端** | **RevenueCat** 官方 SDK | 已定为统一方案，避免自研收据与状态机。 |

---

## 11. 可观测性（一期不接 Firebase）

| 端 | 推荐 | 说明 |
|----|------|------|
| **Android** | **Timber**（日志）、**Play 控制台** 崩溃 | 一期替代 Firebase；后续可换接 Crashlytics。 |
| **iOS** | **OSLog** 封装、**MetricKit**（可选，系统） | 同上。 |

---

## 横切能力（工程化）

| 能力 | Android | iOS |
|------|---------|-----|
| 依赖注入 | **Hilt** / **Koin** | **Factory** / **Swinject** |
| 异步 | **Coroutines** + **Flow** | **Swift Concurrency** |
| UI | **Jetpack Compose** | **SwiftUI** |
| 网络（若仅 RevenueCat 等 SDK 自带则够用） | **OkHttp**（如后续有自建 API） | **URLSession** / **Alamofire**（按需） |
| JSON | **Kotlinx Serialization** / **Moshi** | **Codable** |

---

## 使用建议

1. **控制依赖数量**：每个领域 1～2 个主力库即可，避免重复能力（例如图片只选 Coil 或 Glide 其一）。  
2. **安全与隐私**：三方库不得替代你对「密钥不落盘明文、照片不出域」的审查；接入前看 **Privacy Policy / Data Safety** 要求。  
3. **与分项文档对齐**：实现细节仍以 `doc/android/*`、`doc/ios/*` 为准，本文仅作选型加速。

---

> 文档随技术栈迭代更新；替换或升级库时请在《原生双端架构设计方案》或分项中备注版本与 breaking change。
