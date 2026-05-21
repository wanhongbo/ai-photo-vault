# LumaNox iOS UX 规范

本文是 iOS 页面设计与实现的直接规范。Android 是功能与交互参考源，iOS 需要保持产品一致性，同时遵守 Apple 平台习惯。

## 设计原则

1. **隐私感优先**
   - 页面应传达本地、加密、克制、可靠的感受。
   - 避免营销化大卡片堆叠、夸张渐变、装饰性插画和无意义氛围图。
   - 媒体类页面必须优先展示真实内容缩略图；占位只能用于加载、失败、空态。

2. **Android 对齐，iOS 落地**
   - 功能入口、页面结构、业务状态与 Android Compose 保持同源。
   - 顶部导航、系统权限、Photo Picker、Document Picker、Face ID 等使用 iOS 原生习惯。
   - Android `AppTopBar` 对应 iOS `LNScreenScaffold` / `LNNavigationBar`。

3. **Pen 是视觉源**
   - 每个 UI 页面必须有对应 `.pen`。
   - SwiftUI 代码不得凭空创造与 Pen 不一致的新布局。
   - 当运行截图与 Pen 偏差明显时，先判断产品意图，再同步修正 Pen 与代码。

4. **真实状态完整**
   - 页面至少覆盖 loading、empty、content、error、permission denied、paywall/locked 等必要状态。
   - 危险操作必须有确认或可恢复路径。
   - 媒体解密、导出、备份、恢复期间不得泄露明文路径、密钥或敏感日志。

## 视觉系统

### 色彩

| Token | Hex | 用途 |
|---|---|---|
| `brandBlue` | `#4A9EFF` | 主 CTA、选中态、进度 |
| `bgBottom` | `#05080D` | 全局背景底色 |
| `bgTop` | `#0B1324` | 首页/主页面顶部暗蓝 |
| `sectionBg` | `#0C1523` | 卡片、分组、列表容器 |
| `navBarBg` | `#0E1726` | 浮动底栏、顶栏按钮 |
| `title` | `#EAF1FF` | 主文字 |
| `subtitle` | `#8EA2C0` | 次级文字 |
| `stroke` | `#223247` | 描边、分割 |
| `error` | `#FF4372` | 错误、危险 |
| `success` | `#21C277` | 成功 |
| `amberWarning` | `#E8C547` | 敏感内容、会员强调 |

### 字体与字号

使用系统字体，保证中文、英文与系统动态字号兼容。

| 角色 | 建议 pt | 字重 | 用途 |
|---|---:|---|---|
| Display | 28-32 | Bold | 启动页、关键标题 |
| Page Title | 24 | Bold | 页面标题 |
| Section Title | 16-18 | Semibold | 分组标题 |
| Body | 14-15 | Regular | 正文、列表说明 |
| Label | 11-12 | Medium | 计数、标签、辅助状态 |

### 圆角与间距

| Token | 建议值 | 用途 |
|---|---:|---|
| `screenHorizontal` | 16pt | 页面左右边距 |
| `gridGap` | 8pt | 媒体网格间距 |
| `homeCard` | 20pt | 首页主要容器 |
| `homeThumb` | 12pt | 媒体缩略图 |
| `settingsRow` | 12pt | 设置行 |
| `homeNavBar` | 24pt | 底部浮动 Tab |
| `dialog` | 22pt | 弹窗 |

### 媒体网格

- 最近列表、相册详情、搜索结果、回收站、AI 清理/分类/敏感候选、隐私打码媒体选择统一使用标准媒体 Grid 列表。
- 标准媒体 Grid 列表在 iPhone 上固定 3 列；容器宽度为可用屏宽减去左右 `screenHorizontal`，卡片内边距为 16pt，列间距与行间距均为 8pt。
- Grid 必须使用固定单元格宽度计算，不使用会随内容或父级嵌套漂移的弹性网格；当前 SwiftUI 标准组件为 `VaultMediaGridCard`，基础缩略图能力复用 `VaultMediaThumbnailView`。
- 缩略图必须为 1:1 方形，圆角 10-12pt，描边使用 `stroke` / `strokeStrong`，内容使用 `.fill` 裁切；失败态显示统一暗色底和媒体类型图标。
- 视频缩略图需叠加播放图标或时长徽标。
- 内容态不得在 Grid 卡片内部额外插入标题行、统计行或说明文案；标题/筛选/说明应放在 Grid 卡片外部，避免不同页面的列表高度和列宽不一致。
- 允许的特殊项只有导入入口 tile，且必须占用同一网格单元格，不改变已有媒体缩略图尺寸。
- 多选态使用右上角选择标记，不改变网格尺寸，避免布局跳动。

## 交互规范

1. **导航**
   - 主 Tab 使用底部浮动栏：Vault、Camera、AI、Settings。
   - 二级页面使用返回按钮 + 页面标题；返回按钮触摸区域不小于 44pt。
   - 全屏相机、Paywall、系统 Picker 按平台惯例使用 modal 或 full screen。

2. **按钮**
   - 主按钮高度 54pt，圆角 16pt。
   - 次按钮高度 48pt，圆角 14pt。
   - 危险按钮必须使用危险色系，并与普通确认按钮视觉区分。

3. **权限与空态**
   - 相册权限未授权时，解释为什么需要权限，并提供打开系统设置入口。
   - 空保险箱应把导入作为主操作，把私密相机作为次操作。
   - 空态不使用夸张插画，以图标、短文案、操作按钮为主。

4. **加载与错误**
   - 加载态不阻塞页面时使用局部 progress；导入、备份、恢复等长任务使用独立进度页。
   - 错误文案必须可恢复，提供重试、设置、返回或取消路径。

## 本地化与可访问性

- 所有用户可见字符串必须进入 `Localizable.strings`。
- SwiftUI 视图应设置必要的 `accessibilityLabel` / `accessibilityIdentifier`，便于 Maestro 或 XCTest。
- 触摸目标不小于 44x44pt。
- 文本不得在 393pt 宽度与常见动态字号下重叠或截断关键操作。

## 验证标准

每次 iOS UI 或行为变更完成后，至少执行：

```bash
cd ios
xcodegen generate
xcodebuild -scheme LumaNox -project LumaNox.xcodeproj -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' build
xcrun simctl install booted build/Build/Products/Debug-iphonesimulator/LumaNox.app
xcrun simctl launch booted com.xpx.vault
xcrun simctl io booted screenshot /tmp/lumanox-sim/latest.png
```

如果构建目录不同，可使用 `xcodebuild -showBuildSettings` 或 DerivedData 实际路径定位 `.app`。
