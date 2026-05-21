# AGENTS.md — LumaNox (AI Photo Vault)

> This file provides AI coding agents with the project context needed to work effectively on this codebase.

## Mandatory Session Bootstrap

Every new coding session must read this file first. For iOS work, also read these files before making design or code changes:

- `docs/ios-session-handoff.md` — latest iOS implementation summary and handoff notes
- `docs/ios-technical-plan.md` — iOS product-function analysis, architecture plan, milestones, and module implementation strategy
- `docs/ios-ux-spec.md` — iOS product UX specification for information architecture, page states, interactions, content, privacy, and accessibility
- `docs/ios-ux-guidelines.md` — iOS visual, interaction, accessibility, and validation rules
- `docs/ios-pen-code-workflow.md` — one-page-one-Pen workflow and Pen-to-SwiftUI implementation rules
- `docs/ios-parity-spec.md` — Android-to-iOS page, route, and component parity reference
- `docs/ios-data-model-layer.md` — current iOS metadata/data-model design
- `docs/ios-self-test.md` — manual iOS self-test checklist

For any iOS code implementation, first align the intended behavior and module boundaries with `docs/ios-technical-plan.md`, then align the user-facing interaction, state handling, visual hierarchy, privacy copy, and accessibility behavior with `docs/ios-ux-spec.md`.

When a user asks for iOS UI implementation, treat `.pen` files as the visual source of truth: update or create the corresponding Pen file first, then implement SwiftUI from it, then validate in the iOS simulator.

## Project Overview

**LumaNox** (internal code name: `xpx.vault`) is a privacy-first, fully offline photo & video vault app. Android is the mature reference implementation; iOS is being built to match Android's product behavior while using native SwiftUI and iOS platform conventions. All data is AES-256 encrypted on-device — zero cloud, zero data collection. The app features AI-powered privacy redaction, smart cleanup/classification, encrypted backup/restore, biometric unlock, and a Premium subscription via RevenueCat.

- **Package**: `com.xpx.vault`
- **iOS Bundle ID**: `com.xpx.vault`
- **Min SDK**: 26 (Android 8.0) / **Target SDK**: 35 / **Compile SDK**: 35
- **iOS Target**: iOS 16.0+
- **JDK Toolchain**: 21 (required for compilation — JDK 17 will fail)
- **Kotlin**: 2.0.21 / **Gradle**: 8.11.1 / **AGP**: 8.7.2
- **iOS Stack**: SwiftUI / XcodeGen / CryptoKit / PhotosUI / AVFoundation / LocalAuthentication / RevenueCat

## Module Structure

```
android/
├── app/                        # Main application (UI, navigation, billing, backup)
├── core/
│   ├── domain/                 # Domain layer: models, repository interfaces, quota, billing contracts
│   ├── data/                   # Data layer: Room DB, crypto (VaultCipher), AI result DAOs, DI
│   ├── ai/                     # AI algorithms: sensitive regex, duplicate clustering, privacy renderer
│   └── ai-mlkit/               # ML Kit integration: face/text/barcode/image labeling analyzers
└── feature/
    └── ai/                     # Feature-level AI placeholder (minimal)
ios/
├── LumaNox/App                 # SwiftUI app entry and root flow
├── LumaNox/Core                # design system, navigation, crypto, vault, backup, metadata
├── LumaNox/Components          # reusable SwiftUI components
├── LumaNox/Features            # feature screens, each UI page should have a matching .pen
└── LumaNox/Resources           # localization and assets
```

### Key Directories within `app/`

| Path | Description |
|---|---|
| `ui/` | All Compose screens (HomeScreen, AlbumScreen, PaywallScreen, etc.) |
| `ui/ai/` | AI feature screens (AiCleanupScreen, AiClassifyScreen, AiSensitiveReviewScreen, PrivacyRedactScreen) |
| `ui/backup/` | Backup engine (LocalBackupMvpService, BackupPackageV1, BackupMeta, ExternalBackupLocation) |
| `ui/components/` | Reusable UI components (AppTopBar, AppButton, AppDialog, PinInputDialog, MediaInfoDialog) |
| `ui/settings/` | Settings screens (SettingsDetailScreens, LegalWebViewScreen) |
| `ui/lock/` | PIN/biometric lock screens |
| `ui/export/` | Media export & share helpers |
| `billing/` | RevenueCat integration, paywall gatekeeper, subscription repo, quota manager |
| `ui/theme/` | UiTokens (colors, sizes, text sizes), AppFontFamily, AppTypography, AppShapes |

## Tech Stack

| Category | Technology |
|---|---|
| UI | Jetpack Compose + Material 3 (BOM 2024.10.01) |
| Navigation | Navigation Compose 2.8.4 |
| DI | Hilt 2.52 (with KSP) |
| Database | Room 2.6.1 |
| Crypto | Android Keystore + AES-256-GCM + Argon2id (Bouncy Castle) |
| Camera | CameraX 1.4.1 |
| AI/ML | ML Kit (image labeling, face detection, text recognition, barcode scanning) |
| Video | Media3 ExoPlayer 1.4.1 |
| Billing | RevenueCat Purchases SDK 9.14.0 |
| Background Work | WorkManager 2.9.1 |
| Coroutines | kotlinx.coroutines 1.9.0 |
| Biometrics | AndroidX Biometric 1.1.0 |

## Build & Run

### Android

```bash
# Compile (dev flavor — always use dev+debug)
./gradlew :app:compileDevDebugKotlin

# Install
./gradlew :app:installDevDebug

# Product flavors: dev (DEV_TOOLS=true) / prod (DEV_TOOLS=false)
# Build types: debug / release (minified, shrinkResources)
```

**Important**: The project requires **JDK 21**. Compilation will fail with "Cannot find a Java installation" if only JDK 17 or earlier is available.

### iOS

```bash
cd ios
xcodegen generate
xcodebuild -scheme LumaNox -project LumaNox.xcodeproj -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' build
```

After every iOS code change, install and launch the app on an iOS simulator and capture a screenshot. A passing compile is not enough. Prefer `iPhone 16` unless the task requires another device class.

## Architecture Patterns

### Clean Architecture
- **Domain layer** (`core/domain`): Pure Kotlin, no Android dependencies. Models, repository interfaces, quota definitions.
- **Data layer** (`core/data`): Room DAOs, VaultCipher, BackupKeyManager, DI modules.
- **AI layer** (`core/ai`, `core/ai-mlkit`): Algorithm implementations + ML Kit analyzer bridges.
- **App layer** (`app`): Compose UI, ViewModels, navigation, Hilt entry points.

### Dependency Injection
- **Hilt** is used throughout. `@HiltAndroidApp` in `PhotoVaultApp`, `@AndroidEntryPoint` in `MainActivity`.
- ViewModels use `@HiltViewModel` + `@Inject`.
- For non-ViewModel code that needs DI (Workers, static helpers), use `EntryPointAccessors` via provider objects (see `EntryPointProviders.kt`).

### Navigation
- Single-activity architecture via `MainActivity` with `NavHost`.
- Route constants defined in `MainActivity.Companion`.
- Screens receive navigation callbacks as lambda parameters (`onBack`, `onOpenXxx`).
- Route pattern: `composable("route_name") { Screen(onBack = { navController.popBackStack() }) }`.

### State Management
- Compose state hoisting pattern — UI state in ViewModels, collected via `collectAsStateWithLifecycle()`.
- `BackupRuntimeState` / `AppLockManager` are singleton objects for app-wide state.

### iOS Architecture
- SwiftUI screens live under `ios/LumaNox/Features/**`.
- Navigation route definitions live in `ios/LumaNox/Core/Navigation/AppRoute.swift`; route rendering lives in `RouteDestinationView.swift`.
- Shared design tokens live in `ios/LumaNox/Core/DesignSystem`.
- Vault data and metadata are local-only. Encrypted files under `Documents/vault_albums/` remain the source of truth; `vault_metadata_v1.json` is a rebuildable index.
- Real media grids must use decrypted thumbnails through `VaultMediaThumbnailView` / `LNMediaGrid`, not decorative placeholders except for loading, empty, or failure states.
- Any iOS code implementation must reference `docs/ios-technical-plan.md` for product/module strategy and `docs/ios-ux-spec.md` for UX behavior, page states, privacy wording, and accessibility requirements.

### iOS Pen-to-Code Rule
- Every routable iOS UI page must have a corresponding `.pen` file. Use the target index in `docs/ios-pen-code-workflow.md`.
- For new or changed UI, update/create the Pen first, then generate or adapt SwiftUI from that Pen.
- Android Compose screens are the behavior reference; iOS Pen files are the visual source of truth.
- If simulator screenshots diverge from the Pen, reconcile both before marking the task complete.

## Coding Conventions

### String Resources (i18n)
**ALL user-visible strings must be in `strings.xml`** — no hardcoded Chinese or English in code.

| Context | API |
|---|---|
| Compose `@Composable` | `stringResource(R.string.xxx)` |
| ViewModel / Service (has Context) | `context.getString(R.string.xxx)` |
| Format strings | `%1$d`, `%1$s` etc. in XML, pass args to `stringResource()` / `getString()` |

- Chinese default: `res/values/strings.xml`
- English translation: `res/values-en/strings.xml`
- Comment convention: group by feature with `<!-- ========== 分类 ========== -->`
- iOS user-visible strings must be in `ios/LumaNox/Resources/*.lproj/Localizable.strings` and accessed via `L10n`.

### Typography
- Font: `AppFontFamily` (= `FontFamily.SansSerif`, maps to Noto Sans CJK/思源黑体 on Chinese devices).
- All `TextStyle` must explicitly declare `fontFamily = AppFontFamily`.
- Design tokens: `UiColors`, `UiSize`, `UiTextSize` from `ui/theme/UiTokens.kt`.

### UI Components
Use the project's custom components instead of Material defaults:

| Need | Use | Not |
|---|---|---|
| Top bar | `AppTopBar(title, onBack)` | `TopAppBar` |
| Button | `AppButton(text, onClick, variant)` | `Button` |
| Dialog | `AppDialog(...)` | `AlertDialog` |
| Input dialog | `AppInputDialog(...)` or `PinInputDialog(...)` | Custom dialogs |
| Loading indicator | `stringResource(R.string.common_loading)` | Hardcoded "加载中" |

### Enum String Labels
When enums carry display text, use `@StringRes val labelRes: Int` instead of `val label: String`:

```kotlin
enum class MyTab(@StringRes val labelRes: Int) {
    ALL(R.string.filter_all),
    BLURRY(R.string.filter_blurry),
}
// Usage in Composable: stringResource(tab.labelRes)
```

### Context in Non-Composable Code
- Services/objects without Context: pass `context: Context` as a method parameter.
- Singletons needing app-wide Context: use `@ApplicationContext` via Hilt injection.
- Companion object factory methods: add `context: Context` parameter.

### Error Messages
- User-facing errors → `strings.xml` resource + `context.getString()`.
- Log/debug messages → use `AppLogger` (not `Log` or `Timber`): `AppLogger.d(TAG, "...")`.
- Algorithm/business-logic keywords in Chinese (e.g., SensitiveRegexMatcher's "微信"/"朋友圈") are **data**, not UI strings — do NOT internationalize.

## Key Subsystems

### Encryption (VaultCipher)
- Located at `core/data/crypto/VaultCipher.kt`.
- Uses Android Keystore for master key, AES-256-GCM for file encryption.
- Backup uses separate Argon2id-derived key (via `BackupKeyManager`).
- **Never** log, expose, or persist raw key material.

### Backup & Restore
- `LocalBackupMvpService` (object) is the core engine — dual-key (Vault Master Key + Backup Key).
- `BackupPackageV1` handles the binary package format (magic + version + header JSON + encrypted body chunks).
- Auto backup: `AutoIncrementalBackupWorker` (WorkManager) → `ExternalBackupLocation` (SAF).
- Manual backup: user picks SAF target, always FULL.
- Results: `BackupExecutionResult` / `RestoreExecutionResult` data classes.

### Paywall & Quota
- `PaywallGatekeeper.checkAccess(ProFeature)` → `GateResult.Allowed | SoftWall | HardWall`.
- `RevenueCatSubscriptionRepository` wraps RevenueCat Purchases SDK.
- `QuotaManager` tracks usage limits for free-tier users.
- Routes through `ROUTE_PAYWALL?dismissable={bool}&source={string}`.

### AI Features
- **Cleanup**: `AiCleanupScreen` + `AiCleanupViewModel` — blurry/duplicate detection via `LocalAlgoAnalyzer`.
- **Classify**: `AiClassifyScreen` + `AiClassifyViewModel` — ML Kit image labeling → category mapping.
- **Sensitive Review**: `AiSensitiveReviewScreen` — regex + ML Kit for ID cards, bank cards, QR codes, faces, chat content.
- **Privacy Redact**: `PrivacyRedactScreen` + `PrivacyRedactViewModel` — canvas-based mosaic/blur/bar overlay with `PrivacyRenderer`.

### Legal Pages
- `LegalWebViewScreen` loads local HTML from `res/raw/` based on current locale.
- `isChineseLocale()` selects `_zh` vs `_en` HTML variants.
- External links in WebView are intercepted and opened in system browser.

## Common Pitfalls

1. **IDE "Unresolved reference" errors**: IntelliJ/Android Studio frequently shows false-positive "Unresolved reference: compose/dagger/R/Dispatchers" errors when the index is stale. **Do not "fix" these** — they compile fine. Verify with `./gradlew` before trusting IDE diagnostics.

2. **JDK version**: Must be JDK 21. If `./gradlew` fails with "Cannot find a Java installation matching {languageVersion=21}", install JDK 21 or configure `org.gradle.java.home` in `local.properties`.

3. **Duplicate strings in search_replace**: Some error messages appear in multiple locations (e.g., auto backup + manual backup paths). Use `replace_all: true` or include enough surrounding context for unique matching.

4. **PinInputDialog**: `confirmText` and `dismissText` have no default values — callers must always pass them explicitly.

5. **RevenueCat API Key**: Configured in `local.properties` as `revenuecat.apiKey.android=...` (dev) and `revenuecat.apiKey.prod=...`. Never commit `local.properties`.

6. **Dark mode**: The app uses a dark theme (`AppDarkColorScheme`). WebView HTML gets dark-mode CSS injected via `injectDarkModeCss()` — do not rely on HTML's own dark styles.

## File Reference

| What | Where |
|---|---|
| Navigation & routes | `app/.../MainActivity.kt` (companion object at bottom) |
| String resources (zh) | `app/src/main/res/values/strings.xml` |
| String resources (en) | `app/src/main/res/values-en/strings.xml` |
| Design tokens | `app/.../ui/theme/UiTokens.kt` (UiColors, UiSize, UiTextSize) |
| Typography | `app/.../ui/theme/Type.kt` (AppFontFamily, AppTypography) |
| App entry point | `app/.../PhotoVaultApp.kt` (@HiltAndroidApp) |
| Lock manager | `app/.../AppLockManager.kt` |
| Language manager | `app/.../LanguageManager.kt` |
| Billing module | `app/.../billing/BillingModule.kt` |
| Paywall gatekeeper | `app/.../billing/PaywallGatekeeper.kt` |
| Encryption core | `core/data/.../crypto/VaultCipher.kt` |
| Backup engine | `app/.../ui/backup/LocalBackupMvpService.kt` |
| AI scan use case | `app/.../ai/AiLocalScanUseCase.kt` |
| Privacy renderer | `core/ai/.../privacy/PrivacyRenderer.kt` |
| Legal HTML pages | `app/src/main/res/raw/` (privacy_policy_*, terms_of_service_*) |
| Proguard rules | `app/proguard-rules.pro` |
| Version catalog | `gradle/libs.versions.toml` |
| iOS parity / UX spec | `docs/ios-parity-spec.md` |
| iOS latest handoff | `docs/ios-session-handoff.md` |
| iOS technical plan | `docs/ios-technical-plan.md` |
| iOS product UX spec | `docs/ios-ux-spec.md` |
| iOS UX rules | `docs/ios-ux-guidelines.md` |
| iOS Pen-to-code workflow | `docs/ios-pen-code-workflow.md` |
| iOS data model layer | `docs/ios-data-model-layer.md` |
| iOS manual self-test cases | `docs/ios-self-test.md` |
| iOS SwiftUI app | `ios/LumaNox/` |
| iOS design system | `ios/LumaNox/Core/DesignSystem/` |
| iOS Pen files | `ios/LumaNox/**/*.pen` |
