# Android UI 设计规范沉淀（Pixso）

为避免后续 UI 风格漂移，本项目将以下 3 份设计规范作为 Android UI 默认基线：

- 设计基线：`https://pixso.cn/app/design/CG1WCBz9LzfzMukAplQObw?item-id=4:969`
- 弹窗规范：`https://pixso.cn/app/design/CG1WCBz9LzfzMukAplQObw?item-id=14:3`
- 按钮规范：`https://pixso.cn/app/design/CG1WCBz9LzfzMukAplQObw?item-id=33:1`

## 适用范围

- Android App 内所有新增/改造 UI 页面
- 组件级开发（含弹窗、按钮、空状态、提示）
- 视觉重构与主题升级

## 开发执行清单（每次 UI 开发前后自检）

1. 是否对照了对应规范的布局、间距、字体、颜色、圆角与阴影。
2. 弹窗是否遵循统一样式、统一层级与统一交互（含取消/确认语义）。
3. 按钮是否覆盖主/次/危险/禁用/加载态，并使用统一尺寸和文案样式。
4. 是否覆盖至少 `normal/pressed/disabled` 状态。
5. 若与规范不一致，是否在 PR 或提交说明标注原因与后续计划。

## 工程内生效方式

- AI 侧：通过 `.cursor/rules/android-ui-design-spec-baseline.mdc` 设为 always apply，后续 UI 任务默认参考。
- 代码侧：继续搭配 `.cursor/rules/android-image-assets.mdc`，同时满足视觉规范与高保真素材规范。
