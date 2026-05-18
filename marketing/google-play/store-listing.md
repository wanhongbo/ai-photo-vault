# Google Play Store Listing — LumaNox

> **Document version**: 2026-05-12  
> **Scope**: Google Play Console → Store listing (Main)  
> **Package name**: `com.xpx.vault`  
> **Current version**: 0.2.0 (versionCode 2)  
> **Default language**: English (United States) · **Additional**: 简体中文  
> **Owner**: LumaNox Team

---

## 0. 应用名优化建议 / App Name Optimization

**Original (待替换)**：`LumaNox - Private photo Vault`  
**问题**：`Vault` 重复、大小写不规范、破折号断行不专业。

### 推荐方案（按优先级） / Recommendations (by priority)

| # | English (≤30 chars) | 简体中文 (≤30字符) | 亮点 |
|---|---|---|---|
| 🥇 **1** | **LumaNox: Private Photo Safe** (29) | **LumaNox：隐私相册保险箱** | 保留品牌 LumaNox，用 **Safe** 消除 Vault 重复，语义与品牌互补 |
| 2 | `LumaNox — Private Photo Vault` (29) | `LumaNox：私密相册保险库` | 使用完整品牌，Vault 作为描述词 |
| 3 | `LumaNox: Encrypted Photos` (28) | `LumaNox：加密私密相册` | 突出「加密」功能，SEO 更友好 |
| 4 | `LumaNox · Secret Gallery` (26) | `LumaNox · 私密相册` | Gallery 替代 Vault，更大众化 |

**最终选定（本文通篇以此为准）**：  
- **英文**：`LumaNox: Private Photo Safe`  
- **中文**：`LumaNox：隐私相册保险箱`

> 理由：保留了工程代码、包名、RevenueCat Dashboard、营销素材中已固化的 **LumaNox** 品牌资产；`Safe` 与 `Vault` 同属「保险/保险箱」语义家族，读感一致但避免重复；首字母 L 与 S 形成韵律，商店搜索关键词 "photo safe / photo vault" 双覆盖。

---

## 1. 核心商品详情 / Store Listing Core

### 1.1 App name / 应用名

| Locale | Value | Count |
|---|---|---|
| `en-US` | `LumaNox: Private Photo Safe` | 29 / 30 |
| `zh-CN` | `LumaNox：隐私相册保险箱` | 14 / 30 |

### 1.2 Short description / 简短说明（≤80 characters）

**English (`en-US`)** — 80 / 80
```
Lock private photos & videos with AES-256. Offline. Biometric. Zero cloud.
```

**简体中文 (`zh-CN`)** — 33 / 80
```
AES-256 本地加密私密相册，指纹解锁，全程离线，零云端上传。
```

> Alternative (shorter, punchy):  
> EN: `Private photo vault. AES-256 encrypted. 100% on-device. No cloud.`  
> ZH: `离线加密的私密相册，照片视频本地加锁，拒绝云端。`

---

### 1.3 Full description / 完整说明（≤4000 characters）

#### 🇺🇸 English (`en-US`)

```
LumaNox is a zero-cloud, fully offline photo safe. Every photo and video you add is sealed with AES-256 encryption, unlocked only by your fingerprint, face, or PIN — and never leaves your device.

Built for people who take privacy seriously: travelers on public Wi-Fi, professionals handling sensitive imagery, and anyone tired of their "private" folder syncing to a server they don't own.

━━━━━━━━━━━━━━━━━━━━
🔒  TRUE ON-DEVICE ENCRYPTION
━━━━━━━━━━━━━━━━━━━━
• AES-256-CBC encryption on every file, every time
• Master key generated and sealed inside Android Keystore — it physically cannot leave your phone
• Your PIN is stored as a SHA-256 hash; the plaintext never touches disk
• No telemetry. No analytics SDK. No Firebase in v1

━━━━━━━━━━━━━━━━━━━━
👆  INSTANT BIOMETRIC UNLOCK
━━━━━━━━━━━━━━━━━━━━
• Fingerprint, face, and device credential support
• 6-digit PIN fallback with brute-force throttling
• Auto-lock the moment you switch apps — no leaks from the recents screen

━━━━━━━━━━━━━━━━━━━━
📸  PRIVATE CAMERA
━━━━━━━━━━━━━━━━━━━━
Shoot directly into your vault. Photos captured through LumaNox never touch the system gallery, never appear in backups, and never sync to Google Photos. Straight from lens to encrypted storage.

━━━━━━━━━━━━━━━━━━━━
🖼️  SMART ALBUMS & TRASH
━━━━━━━━━━━━━━━━━━━━
• Custom albums with covers, counts, and recent previews
• Drag-and-drop imports from the system Photo Picker
• 30-day recoverable trash bin
• Batch select, move, and export with one tap

━━━━━━━━━━━━━━━━━━━━
🤖  ON-DEVICE AI, ZERO UPLOADS
━━━━━━━━━━━━━━━━━━━━
TensorFlow Lite runs entirely on your phone. No pixel ever leaves the device.
• Face detection & privacy blur — redact IDs, faces, and sensitive text before sharing
• Smart dedup — find and remove duplicate shots
• Blur / junk cleanup — reclaim storage
• Semantic search — describe the photo, find it instantly

━━━━━━━━━━━━━━━━━━━━
📤  SHARE WITHOUT LEAKING
━━━━━━━━━━━━━━━━━━━━
Export to WhatsApp, Telegram, Signal, or any system share target. LumaNox decrypts to a temporary file, hands it to the share sheet, then wipes the temp file the moment you return. You stay in control.

━━━━━━━━━━━━━━━━━━━━
💾  ENCRYPTED BACKUP & RESTORE
━━━━━━━━━━━━━━━━━━━━
• Export an encrypted ZIP archive to Google Drive, your SD card, or any SAF-compatible location
• Restore on a new device with your password — no account, no server
• You own the backup file. Fully.

━━━━━━━━━━━━━━━━━━━━
🌏  MULTILINGUAL
━━━━━━━━━━━━━━━━━━━━
English, Simplified Chinese, with more locales rolling out.

━━━━━━━━━━━━━━━━━━━━
💎  PREMIUM (OPTIONAL)
━━━━━━━━━━━━━━━━━━━━
Free forever for core encryption and private camera. Premium unlocks:
• Unlimited imports and AI redaction quota
• Watermark-free sharing
• Priority support

Subscribe monthly, yearly, or unlock with a one-time Lifetime purchase. All plans share the same Premium entitlement — restore purchases any time.

Payments are processed by Google Play; subscription management happens through your Play account.

━━━━━━━━━━━━━━━━━━━━
🛡️  PRIVACY PROMISE
━━━━━━━━━━━━━━━━━━━━
• We do NOT collect your photos, videos, or metadata
• We do NOT upload anything to our servers — we don't run one
• We request only the permissions the feature you tap actually needs
• Uninstall = total wipe. Your vault stays on your device, always

Your memories belong to you. LumaNox just helps you keep them that way.

Full privacy policy: https://lumanox.app/privacy
Support: support@lumanox.app
```

**Character count (EN)**: ~3,450 / 4,000 ✅

---

#### 🇨🇳 简体中文 (`zh-CN`)

```
LumaNox 是一款完全离线、零云端上传的隐私相册保险箱。你添加的每一张照片和视频，都会使用 AES-256 加密并锁进本地保险箱，仅凭指纹、面容或 PIN 可以解锁 —— 数据永远不离开这部手机。

我们为真正在意隐私的人而生：公共 Wi-Fi 下的旅行者、需要管理敏感影像的专业人士，以及所有不愿自己的"私密文件夹"被悄悄同步到陌生服务器的用户。

━━━━━━━━━━━━━━━━━━━━
🔒  真正的本地加密
━━━━━━━━━━━━━━━━━━━━
• 每一个文件都使用 AES-256-CBC 加密，无一例外
• 主密钥由 Android Keystore 生成并托管，物理层面无法导出
• PIN 仅以 SHA-256 哈希形式存储，明文永不落盘
• 不采集任何行为数据，不集成 Firebase 等第三方监控 SDK（一期）

━━━━━━━━━━━━━━━━━━━━
👆  一秒生物识别解锁
━━━━━━━━━━━━━━━━━━━━
• 支持指纹、面容、设备凭证多通道
• 6 位数字 PIN 作为安全兜底，内置防暴力破解延迟
• 切换应用瞬间自动上锁 —— 最近任务列表里看不到任何预览

━━━━━━━━━━━━━━━━━━━━
📸  私密相机
━━━━━━━━━━━━━━━━━━━━
直接在应用内拍摄，照片/视频绕过系统相册，不进入系统备份，不同步到 Google 相册，从镜头直达加密保险箱。

━━━━━━━━━━━━━━━━━━━━
🖼️  智能相册与回收站
━━━━━━━━━━━━━━━━━━━━
• 自定义相册，支持封面、计数、最近预览
• 调用系统 Photo Picker 选图导入，权限最小化
• 30 天可恢复回收站
• 批量选择、移动、导出，一键完成

━━━━━━━━━━━━━━━━━━━━
🤖  纯本地 AI，零上传
━━━━━━━━━━━━━━━━━━━━
TensorFlow Lite 完全运行在你的手机上，像素永不外发。
• 人脸检测与隐私打码 —— 分享前一键遮挡证件、人脸、敏感文字
• 智能去重 —— 识别并清理重复照片
• 模糊/废片清理 —— 释放存储空间
• 语义搜索 —— 用文字描述就能找到照片

━━━━━━━━━━━━━━━━━━━━
📤  分享不泄漏
━━━━━━━━━━━━━━━━━━━━
可分享到 WhatsApp、Telegram、Signal 等任意系统分享目标。LumaNox 会先解密到临时文件、交给系统分享面板，用户返回应用后立即擦除临时文件，全程可控。

━━━━━━━━━━━━━━━━━━━━
💾  加密备份与恢复
━━━━━━━━━━━━━━━━━━━━
• 导出加密 ZIP 压缩包到 Google Drive、SD 卡或任意支持 SAF 的位置
• 在新设备上输入密码即可恢复 —— 无需账号，不经过服务器
• 备份文件的完整所有权 100% 属于你

━━━━━━━━━━━━━━━━━━━━
🌏  多语言
━━━━━━━━━━━━━━━━━━━━
已支持英文、简体中文，更多语言陆续推出。

━━━━━━━━━━━━━━━━━━━━
💎  Premium 会员（可选）
━━━━━━━━━━━━━━━━━━━━
核心加密与私密拍照永久免费。Premium 解锁：
• 无限导入与 AI 打码额度
• 无水印分享
• 优先支持

支持月度订阅、年度订阅、一次性终身买断三种方式，共用同一 Premium 权益；可随时恢复购买。

付款由 Google Play 处理，订阅管理请前往你的 Play 账户。

━━━━━━━━━━━━━━━━━━━━
🛡️  隐私承诺
━━━━━━━━━━━━━━━━━━━━
• 我们不收集你的照片、视频或元数据
• 我们不上传任何内容到服务器 —— 因为我们根本没有服务器
• 只申请你点击的功能所真正需要的权限
• 卸载即彻底清除，你的保险箱始终只在你的设备里

记忆属于你。LumaNox 只是帮你守住它。

完整隐私政策：https://lumanox.app/privacy
联系支持：support@lumanox.app
```

**字符数（ZH）**: ~1,250 / 4,000 ✅

---

## 2. 商店元数据 / Store Metadata

### 2.1 Category & Tags

| Field | Value |
|---|---|
| **Application type** | App |
| **Category** | Tools *(primary)* · Photography *(secondary candidate)* |
| **Tags (up to 5)** | `Photo vault` · `Privacy` · `Encryption` · `Photo locker` · `Secure gallery` |
| **Content rating** | Everyone (需完成 IARC 问卷；含本地 AI 处理，不涉及成人/暴力内容) |
| **Target audience** | Ages 18+（含可选内购；明确非儿童应用） |
| **Ads** | **Contains no ads** ✅ |
| **In-app purchases** | **Yes** — subscriptions + one-time (USD 0.99–59.99 range) |

### 2.2 Contact information

| Field | Value |
|---|---|
| **Email** | `support@lumanox.app` *(待配置真实邮箱)* |
| **Website** | `https://lumanox.app` |
| **Privacy policy URL** | `https://lumanox.app/privacy` *(必须 HTTPS 且与 Data Safety 一致)* |
| **Phone** | *(可选，留空)* |

---

## 3. What's new — 版本更新说明 / Release notes

> 每次发版使用；**≤500 字符/语言**。

### v0.2.0 — EN
```
Welcome to LumaNox! 🔒
• AES-256 on-device encryption
• Fingerprint & face unlock
• Private camera — photos never touch the gallery
• 30-day recoverable trash
• On-device AI face blur
• Encrypted backup to Google Drive
• 100% offline. Zero tracking.
```

### v0.2.0 — ZH
```
LumaNox 初版上线！🔒
• AES-256 本地加密
• 指纹 / 面容解锁
• 私密相机 —— 拍摄内容不入系统相册
• 30 天可恢复回收站
• 本地 AI 人脸打码
• 加密备份到 Google Drive
• 100% 离线，零追踪
```

---

## 4. Graphic assets / 视觉素材清单

| Asset | Spec | Status | File |
|---|---|---|---|
| **App icon** | 512×512 PNG, 32-bit, ≤1 MB | ⏳ 需重新导出高清版 | `ic_launcher-playstore-v2.png` (已存在于 app/src/main) |
| **Feature graphic** | 1024×500 PNG/JPG, no transparency | ✅ | `feature_graphic_1024x500.png` |
| **Phone screenshots** | 16:9/9:16, 1080×1920+ | ✅ 8 张英文版 | `phone_portrait_en/01_hero_welcome.png` … `08_trust_encryption.png` |
| **Phone screenshots (ZH)** | 同上 | ⏳ 待补 | `phone_portrait_zh/` *(待新增)* |
| **7" tablet screenshots** | ≥1024×600 | ⏳ 可选 | - |
| **10" tablet screenshots** | ≥1280×800 | ⏳ 可选 | - |
| **Promo video (YouTube)** | 30s horizontal | ⏳ 可选 | - |

### 推荐截图叙事顺序（已匹配现有素材）

1. **Hero / Welcome** — 开门见山宣示"Private. Encrypted. Yours."  
2. **Biometric unlock** — 指纹/面容解锁瞬间，建立"秒开"印象  
3. **Private albums** — 相册网格，真实使用场景  
4. **Secure import** — 加密导入流程，强调"from gallery → encrypted"  
5. **AI privacy blur** — AI 打码 before/after，差异化卖点  
6. **Security center** — 密码/生物识别/自动上锁设置面板  
7. **Dark gallery** — 深色主题、视觉品质  
8. **Trust / Encryption badge** — 落版强调"100% on-device"

---

## 5. 数据安全表单（Data Safety form）对齐要点

> Play Console → App content → Data safety — **必须与隐私政策一一对应**，虚假披露会下架。

| 类别 | 是否收集 | 说明 |
|---|---|---|
| **Personal info** (name, email, address, phone, ID...) | ❌ No | 应用本身不收集 |
| **Financial info** | ⚠️ Yes — *Purchase history* | **由 Google Play + RevenueCat** 处理订阅/交易元数据；不收集信用卡号 |
| **Location** | ❌ No | |
| **Photos and videos** | ❌ No (collected) | 用户媒体**全程本地加密**，不上传、不访问服务器 |
| **Files and docs** | ❌ No | 备份文件由用户手动导出至其选择的位置 |
| **Audio** | ❌ No | |
| **Messages** | ❌ No | |
| **Health and fitness** | ❌ No | |
| **Contacts / Calendar** | ❌ No | |
| **App activity** (interactions, crashes) | ❌ No (v1) | 一期不集成 Firebase/Crashlytics；仅依赖 Play Console 原生崩溃 |
| **Web browsing** | ❌ No | |
| **App info and performance** | ⚠️ *Crash logs via Play Console only* | Google Play Console 原生 |
| **Device or other IDs** | ⚠️ Yes — *RevenueCat* | 匿名 App User ID，用于同步订阅权益 |

**Data handling declarations**：
- Data is encrypted in transit: **N/A — no data sent by app** (RevenueCat SDK uses HTTPS internally)
- Users can request data deletion: **Yes — uninstall removes all data; RevenueCat contact on request**
- Complies with the Play Families Policy: **Not applicable (18+)**

---

## 6. 权限声明 Purpose Statement（app-level permission purposes）

| Permission | Purpose (Play Console 和应用内弹窗保持一致) |
|---|---|
| `CAMERA` | 供"私密相机"拍摄，拍摄结果仅落入本地加密库 |
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` | 从系统相册导入照片/视频到加密保险箱 |
| `READ_EXTERNAL_STORAGE` (maxSdkVersion=32) | 仅 Android ≤ 12 兼容上述功能 |
| `USE_BIOMETRIC` *(runtime, via BiometricPrompt)* | 用户可选的指纹/面容解锁 |
| `POST_NOTIFICATIONS` *(若后续启用)* | 备份任务完成提示（用户可关闭） |

---

## 7. 审核加速 Checklist

- [ ] Play Console 中**应用名**/**开发者名**/**支持邮箱**与隐私政策一致
- [ ] Privacy policy URL 可公开访问（HTTPS，无登录墙）
- [ ] Data Safety 表单逐项核对（尤其 RevenueCat 的 App user ID 披露）
- [ ] IARC 内容分级问卷完成 → Everyone
- [ ] 目标受众：18+（**不**勾选"儿童"）
- [ ] 广告：**No ads**
- [ ] 应用内商品与 RevenueCat Product ID 一致（`luma_vault_premium_monthly/annual/lifetime`）
- [ ] 商店截图文本**不写死价格**（避免与动态定价矛盾）
- [ ] 关键词：photo vault, photo lock, private gallery, encrypted photos, secret album
- [ ] 签名：Play App Signing 已启用
- [ ] 目标 API 级别 ≥ 当期要求（目前 targetSdk = 35 ✅）

---

## 8. SEO 关键词（全球市场）

**英文主关键词**（title + short desc + first 150 chars of full desc 覆盖）  
`photo vault` · `private photos` · `hide photos` · `secret photo album` · `encrypted gallery` · `lock photos` · `photo safe` · `private gallery`

**中文主关键词**  
`私密相册` · `加密相册` · `照片保险箱` · `隐私相册` · `照片加锁` · `相册锁`

**长尾**  
`AES-256 encrypted photo app` · `offline photo vault no cloud` · `biometric photo locker` · `AI face blur offline`

---

## 9. 变更记录 / Changelog

| Date | Version | Author | Change |
|---|---|---|---|
| 2026-05-12 | 1.0 | Marketing | 初版；确定英文 app name 为 "LumaNox: Private Photo Safe" |
