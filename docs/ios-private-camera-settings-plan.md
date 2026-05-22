# iOS Private Camera Settings Implementation Plan

## Goal

Bring the iOS Camera tab private camera closer to the Android reference while staying within Apple platform constraints. iOS cannot embed the system Camera app settings menu, so LumaNox will provide its own AVFoundation-backed settings panel with Android-aligned product behavior and iOS-native controls.

## Product Scope

The private camera must keep the existing privacy promise:

- Capture photos and videos directly into the encrypted vault.
- Never write captures to the system Photos library.
- Keep plaintext capture files in temporary/plaintext-managed storage only until vault import finishes.
- Preserve the full-screen, immersive capture experience.

## Android Parity Target

The Android reference is `PrivateCameraScreen.kt`. iOS should align with these controls where the device supports them:

| Capability | iOS Strategy | Notes |
|---|---|---|
| Photo / video mode | SwiftUI segmented mode switch | Keep tap shutter for selected mode. Long-press video may remain as a shortcut. |
| Front / rear camera | `AVCaptureDevice.Position` switch | Fall back to available camera when a requested camera is unavailable. |
| Flash | Photo flash via `AVCapturePhotoSettings.flashMode`; video light via torch | Video flash is torch-like on iOS, not the same as photo flash. |
| Timer | SwiftUI countdown before capture/start recording | Support off, 3s, 10s. |
| Grid | SwiftUI preview overlay | Pure UI overlay, no capture impact. |
| Exposure | `AVCaptureDevice.setExposureTargetBias` | Show only when the active device exposes a useful range. |
| Zoom | `videoZoomFactor` with preset buttons | Presets: 0.7x when supported, 1x, 2x when supported; pinch updates the same state. |
| Focus / metering | Tap preview point to focus and expose | Show a short focus reticle. |
| Video resolution | Session preset / active format selection | Support FHD and 4K when available; otherwise disable 4K. |
| Video FPS | Active format frame duration | Support 30 and 60 when available; otherwise disable 60. |
| Recording duration | SwiftUI timer | Show while recording. |
| Last capture preview | Vault-backed thumbnail follow-up | Out of scope for the first settings pass unless inexpensive. |

## Non-Goals

- Do not attempt to clone or invoke the Apple Camera app UI.
- Do not add Apple-only camera modes such as Cinematic, Action mode, Photographic Styles, Live Photo, Night mode, or system HDR toggles in this pass.
- Do not write media to Photos as part of private capture.

## Implementation Steps

1. Split or add camera Pen files:
   - `ios/LumaNox/Features/Camera/CameraHomeView.pen`
   - `ios/LumaNox/Features/Camera/PrivateCameraView.pen`
2. Extend `CameraSessionController`:
   - Camera mode, camera capabilities, zoom, exposure, focus/metering, flash/torch, timer-facing capture APIs.
   - Reconfiguration for video resolution and FPS with graceful fallback.
3. Extend `PrivateCameraViewModel`:
   - Own UI state for settings panel, mode, timer, grid, countdown, zoom, exposure, focus marker, recording duration, and selected video quality.
   - Keep vault quota guard at the SwiftUI entry point before new captures.
4. Update `PrivateCameraView`:
   - Add top settings button and settings sheet.
   - Add grid/countdown/focus overlays.
   - Add zoom rail, mode switch, recording duration, and settings-aware shutter behavior.
5. Add localizations in `zh-Hans` and `en`.
6. Validate:
   - Run `xcodegen generate`.
   - Build for `iPhone 16` simulator.
   - Install, launch, and capture simulator screenshot for UI validation.
   - Mark hardware-only checks for follow-up true-device QA.

## Acceptance Criteria

- App builds successfully after XcodeGen regeneration.
- `PrivateCameraView` has a corresponding updated Pen source.
- Settings panel appears in full-screen camera and does not overlap core controls at iPhone 16 size.
- Photo mode exposes flash, timer, grid, and exposure when supported.
- Video mode exposes torch/flash, timer, grid, resolution, FPS, and exposure when supported.
- Zoom presets and pinch route through a single zoom state.
- Captures still finalize through `VaultStore.finalizeCameraCapture`.
- All visible strings are localized through `L10n`.

## Follow-Up True Device QA

Simulator cannot verify camera hardware. Before release, run the iOS self-test camera cases on a real device:

- Permission denied and settings recovery.
- Photo capture and vault appearance.
- Video capture with and without microphone permission.
- Front/rear switching.
- Photo flash and video torch behavior.
- Tap focus, exposure bias, zoom presets, and pinch zoom.
- FHD/4K and 30/60fps availability/fallback.
- Confirm new captures do not appear in Photos.
