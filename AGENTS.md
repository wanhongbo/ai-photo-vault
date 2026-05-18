# AGENTS.md — LumaNox (AI Photo Vault)

> This file provides AI coding agents with the project context needed to work effectively on this codebase.

## Project Overview

**LumaNox** (internal code name: `xpx.vault`) is a privacy-first, fully offline photo & video vault app for Android. All data is AES-256 encrypted on-device — zero cloud, zero data collection. The app features AI-powered privacy redaction, smart cleanup/classification, encrypted backup/restore, biometric unlock, and a Premium subscription via RevenueCat.

- **Package**: `com.xpx.vault`
- **Min SDK**: 26 (Android 8.0) / **Target SDK**: 35 / **Compile SDK**: 35
- **JDK Toolchain**: 21 (required for compilation — JDK 17 will fail)
- **Kotlin**: 2.0.21 / **Gradle**: 8.11.1 / **AGP**: 8.7.2

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

```bash
# Compile (dev flavor — always use dev+debug)
./gradlew :app:compileDevDebugKotlin

# Install
./gradlew :app:installDevDebug

# Product flavors: dev (DEV_TOOLS=true) / prod (DEV_TOOLS=false)
# Build types: debug / release (minified, shrinkResources)
```

**Important**: The project requires **JDK 21**. Compilation will fail with "Cannot find a Java installation" if only JDK 17 or earlier is available.

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
