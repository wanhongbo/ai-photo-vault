# LumaNox iOS

SwiftUI scaffold aligned with [docs/ios-parity-spec.md](../docs/ios-parity-spec.md).

**自测**：见 [docs/ios-self-test.md](../docs/ios-self-test.md)。

## Requirements

- Xcode 15+
- iOS 16.0+
- [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`)

## Setup

```bash
cd ios
xcodegen generate
open LumaNox.xcodeproj
```

## Build (CLI)

```bash
cd ios
xcodegen generate
xcodebuild -scheme LumaNox -project LumaNox.xcodeproj -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' build
```

## Scope (current)

- P0–P3 page shells with navigation parity
- **Real PIN lock**: Keychain-stored SHA-256 hash, 6-digit setup/unlock, optional Face ID / Touch ID
- **Real vault import**: `PhotosPicker` → SHA-256 dedupe → AES-256-CBC encrypted files under `Documents/vault_albums/`
- App background lock (60s timeout) via `AppLockManager`
- Video playback: decrypt to cache → AVPlayer; temp files pruned after 1h / on exit
- First launch: Splash always routes to lock setup; main tab blocked until PIN configured
- Trash: move / list / restore / purge aligned with Android `VaultStore` (30-day retention)
- Manual backup/restore: Android-compatible `BackupPackageV1` + Argon2id backup key (same PIN as vault)
- Auto backup: user picks a folder in Files app → `backup.dat` (security-scoped bookmark, BGAppRefresh ~24h, cold-start catch-up)
- Private camera: AVFoundation photo capture + long-press video, encrypted via `VaultStore`
- RevenueCat: `ios/Config/Local.xcconfig` → `REVENUECAT_API_KEY`（见 `Local.xcconfig.example`）

## Structure

- `LumaNox/App` — entry & root flow
- `LumaNox/Core` — design system, navigation, models
- `LumaNox/Components` — reusable UI
- `LumaNox/Features` — feature screens by domain
- `LumaNox/Resources` — localization & assets
