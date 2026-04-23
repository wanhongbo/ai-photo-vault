# Android 高保真素材替换清单（一期）

## 背景

当前项目采用了「矢量图标优先 + 部分位图占位」方案，尚未完成业务插图的高保真资源替换。  
本文档用于明确替换范围、优先级、资源命名与验收标准，便于按批次落地。

相关规范：
- 视觉与交互基线：`doc/android-ui-design-spec-baseline.md`
- 工程规则：`.cursor/rules/android-image-assets.mdc`

## 当前现状（简版）

- 已完成：启动图标链路（`drawable-nodpi` 源图 + `mipmap-*` 产物）
- 未完成：闪屏/空态业务插图仍为临时占位资源
- 可保留：导航/功能小图标大多是 `VectorDrawable`，无需强制位图化

## 优先级清单

### P0（首批，直接影响首屏观感）

1. 闪屏主视觉图
2. 首页空态核心插图（当前复用 `shield_check` 的位置）

### P1（第二批，核心页面品牌一致性）

1. 各 Tab 空态插图（Vault/Camera/AI/Settings）
2. 相册权限引导插图（若设计稿定义为品牌插图）

### P2（按需微调，通常可维持矢量）

1. 导航与操作图标（`ic_home_nav_*`, `ic_home_action_*`）
2. 仅在与设计稿有明显差异时调整颜色、线宽、留白

## 替换映射表（旧 -> 新）

> 说明：新资源名为建议命名，可按最终设计稿命名规范微调。

| 优先级 | 现有资源（旧） | 建议新资源（高保真） | 目标目录 | 主要引用页面/位置 |
|---|---|---|---|---|
| P0 | `drawable/shield_check.webp` | `drawable-nodpi/splash_shield_hd.webp` | `drawable-nodpi` | `SplashScreen` 图标主视觉 |
| P0 | `drawable/shield_check.webp` | `drawable-nodpi/home_empty_security_hd.webp` | `drawable-nodpi` | `HomeScreen` 空态插图 |
| P1 | `drawable/ic_home_album_permission.xml` | `drawable-nodpi/home_album_permission_hd.webp`（若设计要求插图化） | `drawable-nodpi` | 首页权限引导区 |
| P1 | 当前空态使用图标+文案组合 | `drawable-nodpi/home_empty_vault_hd.webp` | `drawable-nodpi` | Vault 空态 |
| P1 | 当前空态使用图标+文案组合 | `drawable-nodpi/home_empty_camera_hd.webp` | `drawable-nodpi` | Camera 空态 |
| P1 | 当前空态使用图标+文案组合 | `drawable-nodpi/home_empty_ai_hd.webp` | `drawable-nodpi` | AI 空态 |
| P1 | 当前空态使用图标+文案组合 | `drawable-nodpi/home_empty_settings_hd.webp` | `drawable-nodpi` | Settings 空态 |

## 不建议替换为位图的资源

以下资源建议继续使用矢量，保持清晰度与可维护性：

- `drawable/ic_home_nav_vault.xml`
- `drawable/ic_home_nav_camera.xml`
- `drawable/ic_home_nav_ai.xml`
- `drawable/ic_home_nav_settings.xml`
- `drawable/ic_home_action_add.xml`
- `drawable/ic_home_action_search.xml`
- `drawable/ic_splash_shield.xml`（可作为降级或占位保留）

## 设计导出规范（给设计/资源同学）

- 格式：优先 `WebP`，必要时 `PNG`
- 分辨率：提供 `2x/3x` 或单张高分辨率主视觉（推荐放 `drawable-nodpi`）
- 禁止：低清图放大全屏使用
- 交付信息：附设计稿节点、像素尺寸、推荐展示尺寸（dp）

## Android 落地规范

1. 主视觉/插图优先放 `drawable-nodpi`
2. Compose 中明确设置 `Modifier.size/width/height`，避免隐式拉伸
3. 不在不同语义场景复用同一插图（例如 splash 与空态分开资源）
4. 若仅有临时低清素材：控制展示尺寸，并在提交说明标注“待设计补高清”

## 验收标准

- 清晰度：主流机型（xxhdpi/xxxhdpi）无糊图、无锯齿
- 一致性：与 Pixso 色彩、比例、留白一致
- 工程性：资源命名可读、引用点单一明确
- 可回归：替换后页面布局不抖动，暗色/亮色（如适用）表现正确

## 执行顺序建议

1. 先替换 `P0` 并完成页面回归（Splash + Home）
2. 再替换 `P1` 并对齐空态插图风格
3. 最后审查 `P2` 是否存在与设计稿偏差，再做小范围矢量修正

---

维护建议：每次新增业务插图时，先在此文档补一行映射记录，再落资源与代码引用，避免后续素材来源不可追溯。
