# iOS AI 分类与检测优化落地计划

本文聚焦 iOS AI 分类、清理、敏感检测和隐私打码链路的持续优化。目标不是复刻苹果相册的私有索引，而是在 LumaNox 的离线、加密、本地优先约束下，逐步做出接近系统相册体验的可控能力。

## 1. 当前基线

- 扫描入口：`ios/LumaNox/Core/AI/VaultAIAnalysisService.swift`。
- UI 消费：`ios/LumaNox/Features/AI/AIViews.swift`。
- 结果落点：`VaultAiMetadata` 写入 `Application Support/LumaNox/vault_metadata_v1.json`。
- 已有能力：
  - Vision 分类：`VNClassifyImageRequest`。
  - 人体/人脸：`VNDetectHumanRectanglesRequest`、`VNDetectFaceRectanglesRequest`。
  - OCR：`VNRecognizeTextRequest`，中英文识别，结合证件、银行卡、联系方式规则。
  - 条码/二维码：`VNDetectBarcodesRequest`。
  - 清理：Laplacian 模糊、亮度过曝、dHash + 颜色指纹近重复。
  - 隐私打码：自动区域检测后渲染马赛克、模糊、黑白条、椭圆模糊、Emoji。

## 2. 优化原则

1. **本地优先**：所有分析在设备端完成，不上传媒体和 OCR 原文。
2. **可重建索引**：AI 结果是可重建 metadata，不改变加密文件事实源。
3. **摘要持久化**：只保存分类、标签、分数、版本和必要的轻量指纹；不长期保存敏感文本原文。
4. **渐进落地**：先稳定扫描、版本、增量和回归，再扩展人物/宠物/视频等高级分类。
5. **用户可控**：AI 清理和敏感处理只给建议，不自动删除或覆盖原图。

## 3. 分阶段计划

### P0：扫描稳定化

- 为 `VaultAiMetadata` 增加 `analyzerVersion` 和 `sourceFingerprint`。
- 已扫描且版本/输入未变化的媒体跳过重分析。
- 保存 Vision top labels 摘要，后续用于搜索、调参和回归。
- 保存轻量重复检测指纹，使增量扫描仍能和历史媒体比较。
- 失败项不阻塞整轮扫描，写入可恢复的 fallback metadata。

验收：

- 已有媒体二次扫描不再重复解密和 Vision 推理。
- 新导入媒体可增量扫描，并能与旧媒体参与重复检测。
- 旧 metadata 文件缺少新增字段时仍能正常加载。

### P1：准确率提升

- 将分类映射从服务文件拆到独立 `CategoryMapper`。
- 扩展分类：宠物、车辆、票据、聊天截图、自拍、地点/旅行。
- 引入更稳的相似图能力，优先评估 `VNGenerateImageFeaturePrintRequest`。
- 将敏感规则拆分为身份证、护照/MRZ、银行卡 Luhn、联系方式、聊天内容等独立检测器。

验收：

- 固定 100 张 fixture 数据集有稳定分类结果。
- 真实样本回归能输出 precision/recall 报告。
- 常见误判有单元测试覆盖。

### P2：体验增强

- 分类详情支持按标签二级筛选。
- 重复候选按组展示，支持选择保留项。
- 敏感审查展示命中类型和风险等级，支持忽略并持久化。
- 搜索支持本地化同义词，例如“狗/宠物/dog/pet”“证件/身份证/passport”。

验收：

- 同一媒体只进入一个主分类，但可通过多个标签被搜索。
- 用户忽略的敏感候选不会在下一次扫描后反复出现。
- 分类详情和清理候选都使用真实缩略图。

### P3：高级能力

- 视频抽关键帧分类，首期只做分类和敏感候选，不做视频打码。
- 人物/宠物聚类：本地 embedding、聚类、命名、合并/拆分。
- 独立加密 AI index：当 labels、regions、duplicate groups 增长到影响 metadata 体积时，从 `VaultAiMetadata` 摘要迁移到 `ai_results_v1`。

验收：

- 视频至少能按内容进入基础分类。
- 人物/宠物聚类不依赖系统相册私有索引。
- AI index 损坏时可从加密媒体重建。

## 4. 本轮落地范围

本轮先完成 P0 的基础数据和扫描优化：

- 新增 AI analyzer 版本号。
- 新增输入 fingerprint，支持跳过未变化媒体。
- 新增 Vision label 摘要保存。
- 新增重复检测指纹保存。
- 调整扫描结果聚合，让跳过项仍参与重复候选重算。

暂不做 UI/Pen 变更，因此无需更新 AI 页面 Pen；后续涉及分类展示、筛选或清理分组时必须先更新对应 `.pen`。

## 5. 后续验证建议

- 单元测试：
  - 旧 metadata JSON 缺少新增字段仍可 decode。
  - 分类规则误判回归继续通过。
  - 二次扫描跳过已是当前版本的媒体。
- 手动测试：
  - 导入 2-3 张图片后进入 AI 扫描。
  - 再次点击扫描，确认 UI 快速完成。
  - 新增一张重复图片后扫描，确认重复候选仍可出现。
- 性能指标：
  - 记录扫描总数、实际分析数、跳过数。
  - 100 张图片扫描期间 UI 保持可交互。
