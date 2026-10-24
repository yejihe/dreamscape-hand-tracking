# Dreamscape Hand · Phase 2.1 build status (2026-10-24)

## Confirmed
- **Isolated Android host compiled successfully** on GitHub Actions run #6:
  https://github.com/yejihe/dreamscape-hand-tracking/actions/runs/40086382883
- Build job `phase21-debug-apk`: **success**; Gradle `:app:assembleDebug`: **success**; artifact upload: **success**.
- Artifact: `dreamscape-hand-phase21-debug-apk` (ZIP containing `app-debug.apk`, 55,669,948 bytes).
- SHA-256 of extracted APK: `a62d3b885c03dfcdc81c29e37dcd60668f509f27cd4472070df04802bd0fcefe`.
- APK contains `assets/hand_landmarker.task`. Artifact integrity verified as ZIP with no errors.
- GitHub Actions' first attempt failed **before compiling**, because `android-actions/setup-android@v3` asked `sdkmanager tools` (obsolete package). Fixed by using the hosted runner's existing Android SDK.

## Not confirmed
- No real-device install or camera preview test on vivo S30 yet.
- No 100-frame ARCore/MediaPipe synchronized detection device verification yet.
- No measured physical depth for fingertip. No world-space 3D fingertip position or drawing.
- The native app is a diagnostics prototype; not a user-ready VR/MR painting app.
- Portrait image coordinate rotation and index finger overlay registration remain to be tested.

## Release constraints
- Do not merge draft PR #1 without phone testing and ARCore image/MediaPipe frame synchronization validation.
- Do not modify V12 Android mainline or stable R16.3 web page.
- On-device next gates: camera permission, camera preview, tracking indicator, detected hand count, index coordinate output; record screenshot/log for failures.
