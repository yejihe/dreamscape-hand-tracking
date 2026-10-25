# Phase 2.1.3 · vivo S30 camera-image timestamp diagnosis

## Observed on the vivo S30, user screenshot
- ARCore tracking **TRACKING**, camera preview clearly displayed, OpenGL error **0**.
- Reported ARCore Frame timestamp `112506336913053` ns.
- Reported Frame.androidCameraTimestamp `112506325663513` ns.
- Reported CPU Image.timestamp `112506335419573` ns.
- CPU image minus Android Camera timestamp **9,756,060 ns = 9.75606 ms**.
- CPU image minus ARCore Frame timestamp **-1,493,480 ns = -1.49348 ms**.
- Those numbers are from ONE screenshot only. They do **not** establish a constant hardware offset or prove equivalence for all frames.
- The earlier strict rejection `image.timestamp != androidCameraTimestamp` blocked all MediaPipe inference on this device.

## Evidence and decision
ARCore Java `Frame.acquireCameraImage()` documentation states the method **attempts to acquire a camera image that corresponds to the current Frame**. Its contract is the API-level pairing source; it does not promise exact numerical equality between the image timestamp and `Frame.getAndroidCameraTimestamp()` on all devices.
Source: https://developers.google.com/ar/reference/java/com/google/ar/core/Frame

Therefore phase 2.1.3:
- Uses **the CPU Image obtained directly from the current ARCore Frame** together with *that exact Frame* camera pose/intrinsics.
- Preserves the three timestamp values and logs the camera–image difference (ms).
- Checks non-zero timestamp and strictly increasing MediaPipe VIDEO timestamps, and skips unready frames as before.
- **Does not** invent a hard-coded 10ms correction, treat timestamps as numerically equal, or claim precise hardware time alignment.
- Source-only method is a proof of concept. If visual lag or motion causes substantial disagreement, rigorous calibration, timing tests and pose interpolation are still needed before 3D.

## Gates to pass
1. Android CI compiles and uploads APK.
2. Install on vivo S30; preview and ARCore TRACKING still work.
3. The `ARCore关联图像已处理` counter increments.
4. Hand detected (`手部数 1`), fingertip normalized 2D points change when moved.
5. Collect offsets from multiple frames to characterize jitter/variation and image-to-preview lag.
6. Rotate/overlay image and validate intrinsic mapping; then investigate finger depth. **No world XYZ hand claim yet.**
