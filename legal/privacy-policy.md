# Privacy Policy · 隐私政策

> **App**: LumaNox: Private Photo Safe *(app name, pending Play Console rollout)*  
> **Package**: `com.xpx.vault`  
> **Controller / Publisher**: LumaNox Team  
> **Contact**: `support@lumanox.app`  
> **Effective date · 生效日期**: 2026-05-12  
> **Last updated · 最后更新**: 2026-05-12  
> **Version · 版本**: 1.0

---

## 🇺🇸 English Version

### 1. Introduction

LumaNox ("**we**", "**our**", "**the app**") is a privacy-first, on-device photo safe. This Privacy Policy explains what information we collect (spoiler: almost nothing), what we do **not** collect, and the choices you have. By using LumaNox, you agree to the practices described below.

LumaNox is designed around a single principle: **your photos never leave your device**. We do not operate any servers that receive, store, or process your media. We do not have a cloud. We cannot see your vault — not even if we wanted to.

### 2. Our Role under Privacy Laws

For purposes of the EU GDPR, UK GDPR, and analogous laws, the **LumaNox Team** is the Data Controller for the very limited processing described below. For California residents under the CCPA/CPRA, LumaNox is the **Business**. Where we use third-party processors (see §6), they act on our documented instructions.

### 3. What We Collect

#### 3.1 Data the app handles **only on your device** (we do NOT receive this)

| Category | Examples | Where it lives |
|---|---|---|
| **Photos & videos** | Media you import or capture in-app | Encrypted (AES-256) in the app's private storage on your device |
| **Albums & metadata** | Album names, covers, photo counts, timestamps | Local Room database, device-only |
| **Lock credentials** | 6-digit PIN | Stored **only** as a SHA-256 hash on device; master key sealed inside Android Keystore |
| **Biometric templates** | Fingerprint / face | **Never accessed or stored by us** — handled entirely by Android's `BiometricPrompt` |
| **AI inference inputs/outputs** | On-device face detection, blur masks | Processed in-memory by TensorFlow Lite; never uploaded |
| **Backup archives** | Encrypted ZIP files you create | Saved to a location **you** pick (e.g., Google Drive, SD card) via Android's Storage Access Framework. We never see the file |

We do **not** transmit any of the above over the network.

#### 3.2 Data we genuinely collect

**None, with one narrow exception**: if you choose to purchase Premium, our payment processor (Google Play via RevenueCat, see §6) processes a minimal set of subscription-related identifiers. We receive only **aggregate entitlement status** ("premium: active / inactive") — never your payment details, name, email, or address.

### 4. What We Do **NOT** Collect

- ❌ We do not collect your photos, videos, or thumbnails  
- ❌ We do not collect contacts, call logs, SMS, or browsing history  
- ❌ We do not collect precise or coarse location  
- ❌ We do not collect your name, email, phone number, or mailing address  
- ❌ We do not use advertising identifiers, nor serve ads  
- ❌ We do not integrate Firebase Analytics, Google Analytics, Meta SDK, AppsFlyer, or comparable trackers (v1.x)  
- ❌ We do not build profiles, infer demographics, or engage in behavioral advertising

### 5. Permissions & Why

LumaNox requests **only** the permissions needed to deliver the feature you tapped:

| Permission | Purpose | Triggered by |
|---|---|---|
| `CAMERA` | Shoot photos/videos directly into your encrypted vault | Opening the in-app camera |
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` | Import from your system gallery into the vault (API 33+) | Tapping "Import from gallery" |
| `READ_EXTERNAL_STORAGE` *(maxSdkVersion=32)* | Legacy equivalent of the above on Android ≤ 12 | Same as above, legacy devices |
| Biometric *(runtime)* | Unlock the vault with fingerprint/face | You enable biometrics in Settings |
| SAF document picker *(per-use)* | Save/read encrypted backup archives to a location you choose | Tapping Backup or Restore |

We do **not** request: internet-always permissions beyond what the payment SDK needs, location, contacts, SMS, or microphone.

### 6. Third-Party Services

#### 6.1 Google Play Billing

If you purchase a subscription or lifetime upgrade, Google Play processes the payment under **Google's** privacy policy (<https://policies.google.com/privacy>). We never receive your credit card, bank account, or identity details.

#### 6.2 RevenueCat

We use **RevenueCat, Inc.** ("RevenueCat") to reconcile subscription status and support "Restore purchases" across reinstalls. RevenueCat acts as our data processor under a signed Data Processing Addendum.

- **Data shared with RevenueCat**: an anonymous App User ID (generated on your device), the product ID you purchased, and Google Play's subscription receipt  
- **Not shared**: your photos, videos, name, email, precise location, or any vault content  
- **Purpose**: deliver, validate, and restore your Premium entitlement  
- **Retention**: as long as your entitlement is active, plus audit periods required by law  
- **RevenueCat's privacy policy**: <https://www.revenuecat.com/privacy>

No other third-party SDKs, trackers, or ad networks are embedded in v1.

### 7. Data Security

- **AES-256-CBC** encryption for every photo and video, applied at import time  
- Master encryption key generated and sealed inside **Android Keystore** (hardware-backed on devices that support StrongBox) — the key is not exportable by LumaNox or by us  
- PIN stored as a **SHA-256** hash; plaintext never persisted  
- Auto-lock when the app is backgrounded; app icon hidden from recent-task thumbnails where supported  
- No plaintext photos are written outside the app's private directory; temporary files created for sharing are deleted immediately after the share completes  
- We do not operate servers that could be breached to expose your media, because we do not operate any such servers

No method of storage or processing is 100% secure; we encourage you to keep your device updated, use a strong PIN, and protect your backup archive password.

### 8. Data Retention & Deletion

Because your vault lives on your device, **you** control retention:

- **Delete a photo**: move to Trash → permanent delete (or auto-purge after 30 days)  
- **Delete all local data**: uninstall LumaNox — Android removes the app's private directory in full  
- **Subscription records at RevenueCat**: email `support@lumanox.app` referencing your anonymous App User ID (visible in Settings → About) and we will forward your deletion request to RevenueCat

### 9. Children's Privacy

LumaNox is **not directed to children under 18** and we do not knowingly collect information from anyone under 18. If you believe a minor has used the app, contact us and we will act accordingly.

### 10. International Data Transfers

The only data that crosses borders in the course of using LumaNox is the subscription metadata handled by Google and RevenueCat. Both rely on **Standard Contractual Clauses** and comparable safeguards for transfers out of the EEA/UK/Switzerland. We ourselves do not transfer any personal data internationally because we do not collect any.

### 11. Your Rights

#### 11.1 EEA / UK / Swiss residents (GDPR / UK GDPR)

You have the right to **access**, **rectify**, **erase**, **restrict**, **port**, and **object to** processing of your personal data, and to lodge a complaint with a supervisory authority. Since the data we hold is limited to anonymous purchase metadata, most requests will be forwarded to our processors (RevenueCat, Google).

#### 11.2 California residents (CCPA / CPRA)

You may request to **know**, **delete**, or **correct** the limited personal information we process, and to **opt-out of the sale or sharing** of personal information. **We do not sell or share personal information.** We will not discriminate against you for exercising these rights.

#### 11.3 How to exercise

Email `support@lumanox.app`. We will respond within **30 days** (GDPR) or **45 days** (CCPA), and may extend by another period as permitted by law.

### 12. Changes to this Policy

We may update this Policy to reflect product, legal, or operational changes. The "Last updated" date at the top always reflects the current revision. Material changes will be announced in-app before they take effect. Continued use after the effective date of an update constitutes acceptance.

### 13. Contact Us

> LumaNox Team  
> Email: `support@lumanox.app`  
> Response target: within 5 business days

---

## 🇨🇳 简体中文版

### 1. 引言

LumaNox（以下简称"**我们**"、"**本应用**"）是一款以隐私为核心的本地照片保险箱。本隐私政策用来说明我们**收集什么信息**（剧透：几乎什么都不收）、**不收集什么**、以及你享有哪些选择权。使用 LumaNox 即表示你同意下述做法。

LumaNox 只围绕一条原则设计：**你的照片永远不离开你的设备**。我们没有任何接收、存储或处理你媒体内容的服务器。我们没有云端。即便我们想，也看不到你保险箱里的内容。

### 2. 我们的法律身份

就欧盟《通用数据保护条例》（GDPR）、英国 UK GDPR 及类似法律而言，**LumaNox Team** 是下文所述有限处理活动的 **数据控制者（Data Controller）**。就美国加利福尼亚州 CCPA/CPRA 而言，LumaNox 是 **Business**。我们使用的第三方处理方（见第 6 节）均根据书面指示行事。

### 3. 我们会处理哪些数据

#### 3.1 仅在你的设备上本地处理（**我们不会收到**）

| 类别 | 示例 | 存储位置 |
|---|---|---|
| **照片与视频** | 你导入或在应用内拍摄的媒体 | 使用 AES-256 加密，保存在本应用的私有目录内 |
| **相册与元数据** | 相册名、封面、照片计数、时间戳 | 本地 Room 数据库，仅设备可见 |
| **解锁凭据** | 6 位 PIN | **仅以 SHA-256 哈希**形式存储；主密钥封存于 Android Keystore |
| **生物识别模板** | 指纹 / 面容 | **我们从不访问或存储** —— 完全由 Android 的 `BiometricPrompt` 系统接管 |
| **AI 推理输入/输出** | 本地人脸检测、打码遮罩 | 仅由 TensorFlow Lite 在内存中处理，绝不上传 |
| **备份归档** | 你创建的加密 ZIP 文件 | 通过 Android 存储访问框架保存到 **你选择的位置**（如 Google Drive、SD 卡）。我们从未看到这些文件 |

以上内容**不会**经由我们传输到网络上。

#### 3.2 我们真正会收到的数据

**基本没有，除一个狭窄例外**：若你选择购买 Premium，我们的付费处理方（Google Play，经由 RevenueCat，见第 6 节）会处理与订阅相关的最小标识符集合。我们接收到的仅是**聚合权益状态**（"premium: active / inactive"），**不会**接收你的付款信息、姓名、邮箱或地址。

### 4. 我们**不会**收集的数据

- ❌ 不收集你的照片、视频或缩略图  
- ❌ 不收集通讯录、通话记录、短信或浏览历史  
- ❌ 不收集精确或粗略位置  
- ❌ 不收集你的姓名、邮箱、电话或邮寄地址  
- ❌ 不使用广告标识符，也不投放广告  
- ❌ 不集成 Firebase Analytics、Google Analytics、Meta SDK、AppsFlyer 等追踪 SDK（v1.x 阶段）  
- ❌ 不构建用户画像，不做人群推断，不进行行为广告

### 5. 权限说明

LumaNox **仅**申请你所点功能真正需要的权限：

| 权限 | 用途 | 触发时机 |
|---|---|---|
| `CAMERA` | 在应用内拍摄，直接进入加密保险箱 | 打开"私密相机"入口 |
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` | 从系统相册导入照片/视频到保险箱（API 33+） | 点击"从相册导入" |
| `READ_EXTERNAL_STORAGE`（maxSdkVersion=32） | Android ≤ 12 上述功能的兼容实现 | 同上，仅老版本设备 |
| 生物识别（运行时） | 以指纹/面容解锁保险箱 | 你在设置中启用生物识别 |
| SAF 文档选择器（每次使用） | 读/写加密备份归档到你选择的位置 | 点击"备份"或"恢复" |

我们**不会**申请：超过支付 SDK 所需的常联网权限、位置、通讯录、短信、麦克风等。

### 6. 第三方服务

#### 6.1 Google Play Billing（谷歌支付）

当你购买订阅或终身买断时，Google Play 会按 **Google 自身的隐私政策**（<https://policies.google.com/privacy>）处理付款。我们绝不会收到你的银行卡、银行账户或身份信息。

#### 6.2 RevenueCat

我们使用 **RevenueCat, Inc.**（以下简称"RevenueCat"）核对订阅状态、支持用户在重装应用后"恢复购买"。RevenueCat 作为我们的数据处理方，依据已签署的《数据处理附录》履行义务。

- **与 RevenueCat 共享**：设备生成的匿名 App User ID、你购买的商品 ID、Google Play 的订阅收据  
- **不共享**：你的照片、视频、姓名、邮箱、精确位置以及保险箱内的任何内容  
- **目的**：交付、校验并恢复你的 Premium 权益  
- **留存期**：权益有效期间，加上法律要求的审计期  
- **RevenueCat 隐私政策**：<https://www.revenuecat.com/privacy>

v1 版本中不嵌入任何其他第三方 SDK、追踪器或广告网络。

### 7. 数据安全措施

- 每一张照片和视频在导入时即以 **AES-256-CBC** 加密  
- 主加密密钥由 **Android Keystore** 生成并托管（在支持 StrongBox 的设备上有硬件级支撑）—— LumaNox 与我们本人均无法导出该密钥  
- PIN 仅以 **SHA-256** 哈希形式存储，明文永不落盘  
- 应用退至后台即自动上锁；在系统支持的设备上，应用图标会在最近任务缩略图中被隐藏  
- 明文照片不会写出到应用私有目录以外；为分享而临时解密的文件，会在分享完成后立即擦除  
- 我们不运营任何可能泄露你媒体的服务器，因为我们根本没有服务器

任何存储或处理方式都无法声称 100% 安全；我们建议你保持设备系统更新、设置强 PIN、并妥善保管备份归档的密码。

### 8. 数据留存与删除

由于保险箱位于你的设备上，**留存由你控制**：

- **删除单张照片**：移入回收站 → 彻底删除（或 30 天后自动清除）  
- **删除全部本地数据**：卸载 LumaNox —— Android 会一并移除应用私有目录  
- **RevenueCat 处的订阅记录**：可将你的匿名 App User ID（"设置 → 关于"可见）发送至 `support@lumanox.app`，我们将代你向 RevenueCat 转发删除请求

### 9. 儿童隐私

LumaNox **并非面向 18 岁以下用户**，我们也不会明知故犯地收集 18 岁以下用户的任何信息。若你认为未成年人使用了本应用，请联系我们，我们将妥善处理。

### 10. 跨境数据传输

使用 LumaNox 过程中唯一跨境的数据，是由 Google 与 RevenueCat 处理的订阅元数据。二者在将数据传出欧盟/英国/瑞士时均采用 **标准合同条款（SCCs）** 等同等保护措施。我们自身不跨境传输任何个人数据 —— 因为我们根本不收集这些数据。

### 11. 你的权利

#### 11.1 欧盟 / 英国 / 瑞士用户（GDPR / UK GDPR）

你享有对个人数据的**访问、更正、删除、限制处理、可携带、反对处理**的权利，以及向监管机构**投诉**的权利。由于我们实际持有的数据仅限匿名的购买元数据，大多数请求将被转发至我们的处理方（RevenueCat、Google）。

#### 11.2 美国加州用户（CCPA / CPRA）

你可以请求**知晓、删除、更正**我们处理的有限个人信息，并**选择退出出售或共享**个人信息。**我们不出售、不共享你的个人信息。** 我们不会因为你行使权利而对你进行任何歧视性对待。

#### 11.3 中国大陆用户（PIPL）

你对个人信息享有知情、决定、查阅、复制、更正、删除、撤回同意、注销账户（如适用）等权利。由于本应用的处理活动极其有限，请通过下方邮箱联系我们，我们会在法定期限内回复。

#### 11.4 行权方式

发送邮件至 `support@lumanox.app`。我们将在 **30 日内**（GDPR）或 **45 日内**（CCPA）回复，必要时按法律允许的期限延长。

### 12. 本政策的变更

我们可能基于产品、法律或运营调整而更新本政策。页首的"最后更新"日期始终反映当前版本。发生重大变更时，我们会在应用内提前公告。生效日期之后继续使用本应用，即视为接受变更。

### 13. 联系我们

> LumaNox Team  
> 邮箱：`support@lumanox.app`  
> 响应时效：5 个工作日内

---

## Deployment Note · 部署说明

- **Google Play Console → Store listing → Privacy policy URL** should point to an HTTPS-hosted rendering of this document (e.g., GitHub Pages, Netlify, Cloudflare Pages).
- Recommended public URL: `https://lumanox.app/privacy`
- When localized Play listings are enabled, host two mirror URLs and select per-locale:  
  - `https://lumanox.app/privacy` — English default  
  - `https://lumanox.app/privacy/zh-CN` — Simplified Chinese
- Keep this `legal/privacy-policy.md` as the **single source of truth**; the hosted pages must be regenerated from this file on every revision. Update the "Effective date" & "Last updated" atomically with any release that changes data practices.
