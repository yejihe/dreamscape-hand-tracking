# Phase 2.1.2 · vivo S30 timestamp domain correction (2026-10-25)

## Real device evidence
User confirmed ARCore camera preview is now **visible**, tracked state shown as **已追踪**, GPU GL error code **0**, but on-device HUD continuously reports `image/frame timestamp mismatch`. This validates camera/preview/tracking progress **only**, NOT MediaPipe hand detection yet.

## Root cause in our code
Previous `SyncedHandFrameProcessor.kt` compared `android.media.Image.timestamp` from `Frame.acquireCameraImage()` directly with `Frame.timestamp`. This is an **incorrect timestamp-domain assumption**, not sufficient evidence that the CPU camera image is from a different frame. The screenshot cannot establish the numeric offset because old HUD did not print all timestamps.

ARCore Java Frame API exposes both:
- `Frame.timestamp`: ARCore's frame time (base explicitly not defined).
- `Frame.androidCameraTimestamp`: Android Camera timestamp corresponding to the frame.
- `Frame.acquireCameraImage().timestamp`: Android Camera image timestamp.

Reference: https://developers.google.com/ar/reference/java/com/google/ar/core/Frame

## Repair strategy
- Match `Image.timestamp` to `Frame.androidCameraTimestamp`; keep strict equality check, skip mismatches with all three values and ns delta. No guessed tolerance, no paired "latest" pose, and no pretending inconsistent frames match.
- Keep `Frame.timestamp` to detect fresh frames and build monotonic MediaPipe VIDEO timestamps.
- Always derive physical camera pose + intrinsics from **that same ARCore Frame**.
- Display matched camera/image timestamps, ARCore Frame time, detected hand count, normalized index fingertip and camera position.
- Preserve the original processor and the 2.1.1 APK via repository history and distinct Android `applicationId`.

## Next hard gates
1. CI compile the revised Kotlin app successfully; ZIP artifact is NOT a guarantee of correct real-device behavior.
2. On vivo S30, test rear-camera preview, look for `同帧校验通过：N帧` counter incrementing.
3. Move one hand into camera field of view; verify `手部数 1` and fingertip 2D coordinates change.
4. Before world-space finger XYZ, calibrate portrait image orientation / camera intrinsics and measure real fingertip depth.
