# Dreamscape Hand Phase 2.1.3 — verified APK build, device test pending

## Screenshot from vivo S30 (2026-10-25)
- ARCore TRACKING, camera preview visible, OpenGL=0.
- ARCore frame timestamp: 112506336913053 ns.
- Android camera timestamp: 112506325663513 ns.
- CPU Image timestamp: 112506335419573 ns.
- CPU minus Android camera: **9756060ns / 9.75606ms** (one screenshot only).
- Previous code incorrectly treated exact timestamp equality as mandatory. No hand inference reached.

## Source change (current)
- The Android CPU image comes from `Frame.acquireCameraImage()` on the same ARCore frame that supplies camera pose/intrinsics; Google docs describe this as the corresponding camera image.
- Do not silently force `Image.timestamp == Frame.androidCameraTimestamp`. Preserve all original timestamps and display measured offset as a diagnostic, not a claim of precise hardware synchronisation.
- Still reject non-positive timestamps, non-increasing VIDEO timestamps, unsupported YUV format, and bad camera intrinsics.
- Debug UI now labels result “ARCore关联图像已处理” and prints actual offset in milliseconds.
- Application ID isolated as `com.dreamscape.hand.phase21.assoc`.

## Actual CI verification
- GitHub Actions run **40155238783** (`phase21-debug-apk`): **success**. Both `:app:assembleDebug` and artifact upload succeeded.
- Artifact ID **12584422596**, name `dreamscape-hand-phase21-debug-apk`.
- ZIP exported as `Dreamscape-Hand-Phase2.1.3-FrameAssociation.zip`, size ~29MB.
- ZIP test: **No errors**, contains `app-debug.apk` (55,669,964 bytes).
- ZIP SHA256: `24f6291a6bd5a2e98271e8f75b81d364b289a2db4c7c3ea97dd61c24fa0e99de`.

## Still pending, do not overclaim
- Vivo S30 installation and MediaPipe hand inference on **2.1.3**.
- `ARCore关联图像已处理: N` counter, `手部数 1`, fingertip coordinates.
- Time offset stability across many frames, preview alignment/rotation, and actual 3D fingertip depth estimation.
- World-locked 3D drawing **not yet developed**.

Source reference: https://developers.google.com/ar/reference/java/com/google/ar/core/Frame
