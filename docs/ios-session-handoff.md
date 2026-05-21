# iOS 会话交接摘要

本文记录最近一轮 iOS 侧工作的状态、已经达成的约定，以及后续会话启动时必须继承的上下文。后续 Agent 开始 iOS 任务时，应先读取本文件，再读取 `AGENTS.md` 中列出的 iOS 相关规范。

## 已完成事项

1. **底层数据模型层**
   - 新增 `VaultMetadataModels` / `VaultMetadataStore`，在 iOS 本地维护可重建的媒体元数据索引。
   - 索引文件位于 `Application Support/LumaNox/vault_metadata_v1.json`。
   - `VaultStore` 已接入元数据索引，用于首页快照、相册列表、最近媒体、搜索、回收站与存储统计。
   - 详细设计见 `docs/ios-data-model-layer.md`。

2. **真实媒体缩略图**
   - 媒体网格不再只使用装饰占位。
   - `VaultMediaThumbnailView` 会解密图片并下采样生成缩略图；视频通过临时解密文件抽帧生成缩略图，并在生成后清理临时文件。
   - 首页最近、最近列表、相册、搜索、导出等媒体列表应复用标准 `VaultMediaGridCard`，并优先展示真实缩略图。

3. **模拟器验证约定**
   - iOS 代码改动完成后，必须构建、安装、启动模拟器并截图验证。
   - 当前验证过的模拟器目标：`iPhone 16` / iOS Simulator。
   - 不能只以 `xcodebuild` 通过作为完成标准。

4. **Pen 先行的 UI 约定**
   - 已存在多份 `.pen` 高保真文件，例如 `VaultHomeView.pen`、`RecentPhotosListView.pen`、`AlbumView.pen`、`LockView.pen`。
   - 后续 UI 页面必须先补齐或更新对应 `.pen`，再根据 Pen 生成或调整 SwiftUI 代码。
   - 首页「最近」查看更多图片列表应以 Android `RecentPhotosScreen` 为行为参考，以 iOS 设计系统为视觉落点。

## 当前实现重点

- iOS 当前是 SwiftUI 工程，入口在 `ios/LumaNox/App/LumaNoxApp.swift`。
- 导航路由在 `ios/LumaNox/Core/Navigation/AppRoute.swift` 与 `RouteDestinationView.swift`。
- Vault 相关页面集中在 `ios/LumaNox/Features/Vault/`。
- 本地化资源位于 `ios/LumaNox/Resources/*.lproj/Localizable.strings`，用户可见文案不得硬编码在 SwiftUI 中。

## 后续任务优先级

1. 将已有分组式 `.pen` 逐步拆成一页一 Pen，尤其是 Settings、AI、Backup、Export 下的独立页面。
2. 所有新增 UI 先生成 `.pen`，并在代码评审时检查 SwiftUI 是否忠实落地 Pen 的布局、间距、层级、状态。
3. 继续替换 mock 数据路径，让 AI、导出、备份等页面消费真实 metadata / vault store。
4. 每次 iOS 代码变更后执行模拟器验证，并在最终回复中说明构建、安装、启动、截图结果。
