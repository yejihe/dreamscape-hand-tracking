# Phase 2.2.1 small ARM64 download (2026-10-25)

- User reported full Phase 2.2 ZIP too slow to download.
- The prior universal debug ZIP size was **29,948,659 bytes** (contained 55,669,968-byte APK), bundling arm64-v8a, armeabi-v7a, x86, x86_64 native libraries.
- Gradle defaultConfig now constrains `ndk.abiFilters` to `arm64-v8a` for the **vivo S30 ARM64 device-specific experiment**. No hand model, ARCore, depth-probe or landmark functionality is intentionally removed.
- New GitHub Actions run **40156591131**: build job success, artifact uploaded (ID **12585046052**).
- New ZIP: `Dreamscape-Hand-2.2-Lite-ARM64.zip`: **14,016,950 bytes**. Contains `app-debug.apk` **19,730,428 bytes**; single ABI `arm64-v8a`, 3 native libraries, hand_landmarker.task model present.
- ZIP integrity passed `ZipFile.testzip()`.
- ZIP SHA256: `ded24078b92ba91b... ` (truncated).
- This is **build/packaging validation only**. Phone installation, 2D overlay alignment and finger depth are not yet verified. It will not work on 32-bit-only Android devices.
- Prior ZIP artifact remains available so no existing download has to be discarded.
