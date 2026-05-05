# AI Tab 技术方案（Android）

## 一、设计目标与约束

- 扫描范围：**仅 Vault 内照片 + 导入管道同步分析**（方案 C），不主动读系统相册，不新增权限。
- 包体：ML Kit 模型**按需下载**，APK 增量 ≈ 0；算法类检测（pHash、Laplacian）零依赖。
- 性能：分析作用于**缩略图**（不解密原图），WorkManager 受约束后台队列（需充电+空闲可关）。
- 可扩展：统一 `ImageAnalyzer` 策略接口，后续替换为 TFLite / MediaPipe 只改实现。
- 降级：GMS 不可用时，AI 能力自动置灰或降级（模糊/去重不受影响）。

## 二、模块与分层

现有：`:app`、`:core:domain`、`:core:data`

新增：

```
:core:ai            // AI 抽象接口 + 纯算法实现（pHash/Laplacian/Regex）
:core:ai-mlkit      // ML Kit 适配层（可选依赖，按 flavor 接入）
:feature:ai         // AI Tab UI + ViewModel + 导航
```

关键包：

- `com.xpx.vault.ai.core`：`ImageAnalyzer`、`AiResult`、`SensitiveCategory`、`ClassifyLabel`
- `com.xpx.vault.ai.engine.mlkit`：`MlKitLabeler`、`MlKitFaceDetector`、`MlKitOcr`、`MlKitBarcode`
- `com.xpx.vault.ai.engine.local`：`PerceptualHasher`、`SharpnessAnalyzer`、`SensitiveRegexMatcher`
- `com.xpx.vault.feature.ai.ui`：替换现有 [AiHomeScreen.kt](file:///Users/wanhongbo/workspace/ai-photo-vault/android/app/src/main/kotlin/com/xpx/vault/ui/AiHomeScreen.kt) 为真实可交互页面

## 三、数据模型（Room 新增）

`core:data` 新增实体与 DAO：

```kotlin
@Entity("ai_tag") // 每张照片的多标签
data class AiTagEntity(photoId, tag, category, confidence, source, createdAtMs)

@Entity("ai_phash") // 感知哈希用于去重
data class AiPhashEntity(photoId, phash: Long, dhash: Long)

@Entity("ai_quality") // 清晰度/过曝/构图评分
data class AiQualityEntity(photoId, sharpness, brightness, isBlurry, isDuplicate, duplicateGroupId)

@Entity("ai_sensitive") // 敏感命中记录
data class AiSensitiveEntity(photoId, kind /*idCard/bank/phone/face/privateChat*/, regionsJson, status /*pending/moved/ignored*/)
```

Migration 递增版本号；不改动现有 `PhotoAsset` 表结构（关联以 `photoId` 外键）。

## 四、四大功能实现

### 1. 敏感内容识别（导入时主触发 + 历史补扫）

- 触发点：`ImportPhotosUseCase` 中解密写入 Vault 后，投递 `SensitiveAnalyzeWorker`（单条）。
- 实现：
  - 人脸数量：ML Kit Face Detection（`FaceDetectorOptions.PERFORMANCE_MODE_FAST`）
  - 文本：ML Kit Text Recognition → 正则匹配身份证/银行卡/手机号（本地 `SensitiveRegexMatcher`）
  - 条码：ML Kit Barcode Scanning（二维码）
- 反馈：首页顶部"待处理敏感照片"徽章消费 `AiSensitiveEntity.status=pending`，点击 → 列表页 → 批量「移入子空间」或「忽略」（目前项目 Vault 本身就是加密空间，这里用"敏感分组"打标而非二次加密，避免破坏现有存储模型）。

### 2. 自动相册分类（六分类）

- 实现：ML Kit Image Labeling（通用 400+ 标签，0 下载体积）+ 自定义映射表：
  - 人像：`Person/Portrait/Selfie`
  - 风景：`Sky/Mountain/Beach/Tree`
  - 美食：`Food/Dessert/Meal`
  - 证件/文档/截图：OCR + 尺寸+来源判断（拍照 EXIF 缺失 & 宽高比贴近屏幕 → 截图）
- 产物：`AiTagEntity` 打标；"分类相册" Tab 内基于标签做 Virtual Album，不复制文件。
- 扩展：规则映射表抽为 JSON 配置（`assets/ai_classify_map.json`），不改代码即可增分类。

### 3. 垃圾清理（纯算法为主，零模型下载）

- 模糊检测：Laplacian 方差（RenderScript/Kotlin 纯实现）→ `sharpness < threshold` 判废。
- 过曝：直方图峰值检测。
- 重复/连拍：pHash + dHash（8x8 DCT，64 bit Long），Hamming 距离 ≤ 5 归为一簇；簇内按 sharpness 选最优保留、其余标 `isDuplicate=true`。
- 入口：AI Tab「垃圾清理」卡片 → 分组展示 → 批量移入现有「临时垃圾桶」（复用 TrashBin，保留 30 天）。

### 4. 隐私脱敏

- 检测：Face + Text + Barcode（复用 1 的检测结果，缓存命中跳过重新推理）。
- 渲染：`Canvas` 绘制 → 支持马赛克/高斯模糊/黑条三种样式，在 `PrivacyRedactScreen` 预览对比。
- 输出：生成脱敏副本 → 走现有「导出到系统相册」通道（`AIPhotoVault/Redacted` 子目录），**不改原图**。
- 手动触发：照片详情页右上角新增「脱敏」按钮（[PhotoViewerScreen.kt](file:///Users/wanhongbo/workspace/ai-photo-vault/android/app/src/main/kotlin/com/xpx/vault/ui/PhotoViewerScreen.kt) 内加入口）。

## 五、并发与性能

- 调度层：`AiDispatcher` 单例，使用 `Dispatchers.Default.limitedParallelism(2)`，避免同时多任务挤占。
- 分析对象：**thumbPath 缩略图**（256 边长），原图仅在脱敏输出时解密。
- Worker：
  - `SensitiveAnalyzeWorker`（单条，ExpeditedWork）
  - `BulkScanWorker`（全量补扫，Constraints：低电暂停、空闲执行，UniqueWork）
- 缓存：`AiTagEntity` 持久化，已分析照片 `version=models_version` 匹配则跳过。

## 六、包体与依赖

`libs.versions.toml` 增项（ML Kit 采用 bundled=false，模型走 Play 动态下载）：

```toml
mlkitImageLabeling = "17.0.9"
mlkitFace          = "16.1.7"
mlkitText          = "16.0.1"
mlkitBarcode       = "17.3.0"
```

包体增量（以动态下载模型版为准）：约 +2~3 MB 代码；模型 0 预置。

GMS 不可用探测：`GoogleApiAvailability.isGooglePlayServicesAvailable()`，失败时 `AiFeatureRegistry` 将 labeler/face/ocr 注册为 `NoopAnalyzer`，UI 上对应卡片显示"需要 Google Play 服务"。

## 七、UI 改造

- 替换 [AiHomeScreen.kt](file:///Users/wanhongbo/workspace/ai-photo-vault/android/app/src/main/kotlin/com/xpx/vault/ui/AiHomeScreen.kt)：建议卡片绑定真实 pending 数；6 项功能卡片接入跳转。
- 新增页面：
  - `SensitiveReviewScreen`（待处理敏感列表 + 批量操作）
  - `CleanupScreen`（重复/模糊/废片分组 Tab）
  - `PrivacyRedactScreen`（对比预览 + 样式切换 + 导出）
  - `AiClassifyScreen`（六分类网格，基于标签 Virtual Album）
- ViewModel：每个页面一个，`AiHomeViewModel` 聚合未处理计数 Flow。

## 八、开发落地顺序（建议分 4 个 PR）

1. **PR1 骨架**：新增 3 个模块、DI 绑定、Room 迁移、`ImageAnalyzer` 抽象 + `NoopAnalyzer`，AiHomeScreen 接入真实计数（全为 0，验证通路）。
2. **PR2 算法兜底**：pHash + 模糊检测 + 垃圾清理页面。此步无 ML Kit 依赖，先保证"零依赖能用"。
3. **PR3 ML Kit 接入**：Image Labeling + Face + Text + Barcode，敏感识别闭环 + 分类 Virtual Album。
4. **PR4 隐私脱敏**：Canvas 渲染 + 预览 + 导出；Detail 页增入口。

## 九、风险与缓解

- ML Kit 首次需下载模型：首次进入分类/敏感前弹一次进度提示；`WifiOnly` 约束（可设置开关）。
- OCR 把私密聊天截图误判敏感：仅当同时命中 2 个以上关键词或身份证/卡号正则才标 `pending`。
- pHash 误合并：Hamming 阈值用 **≤5**（较严），并用 sharpness 打分保留最高质量。
- 后台电量：WorkManager 全部加 `setRequiresBatteryNotLow(true)`；设置页提供"仅充电时扫描"开关。

## 十、不在本期范围

- 语义文搜图、伪装相册、跨设备 AI 同步。
- 视频 AI（一期仅图片；视频只抽首帧）。
- 自训练模型、TFLite 迁移（预留接口，不实现）。
