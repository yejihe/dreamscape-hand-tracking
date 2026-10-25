# Dreamscape Hand Phase 2.2.2 · latency experiment (2026-10-25)

## User feedback
User reports latest Dreamscape Hand Phase 2.2 app can open/run, but has some noticeable delay. The comment does not specify whether the camera image itself lags or the tracked skeleton/pink dot lags. **Do not claim frame-lag fixed without device measurements.**

## Source-verified bottleneck (baseline 2.2)
- `GLSurfaceView.Renderer.onDrawFrame` called `SyncedHandFrameProcessor.process(frame)` synchronously.
- That method did scalar YUV_420_888 → RGB conversion and **blocking** `HandLandmarker.detectForVideo` on the GL rendering thread.
- User had previously observed 153–175ms per inference in Phase 2.1.3, enough to freeze camera preview while each inference completes.
- Official MediaPipe Android guide explicitly recommends running synchronous VIDEO-mode hand inference on a background thread to avoid blocking rendering/UI:
  https://developers.google.com/edge/mediapipe/solutions/vision/hand_landmarker/android

## Experimental refactor
- Original 2.2 branch/ APK are preserved; new branch `experiment/phase2-2-async-performance`.
- The GL thread acquires a CPU image and copies YUV planes; separately copies ARCore **same Frame** pose, intrinsics, IMAGE_NORMALIZED→VIEW / TEXTURE_NORMALIZED projection bases, and optional scene depth image, then closes image resources promptly.
- A single worker thread performs pixel conversion and blocking VIDEO hand inference, with **at most one frame in flight** (drop excess requests while worker busy). Rendering continues uninterrupted by inference.
- Overlay drawn from CAPTURED frame's mapping, not "latest" live frame mapping; scene-depth sampled from capture's own copied depth map where available.
- GPU preview should be smoother *if inference was the bottleneck*. Landmark/pink-dot positions will still lag by the intrinsic inference processing delay and rendering/transmission overhead.
- No claim of reliable fingertip depth or 3D XYZ. The 0.31 pinch threshold remains part of web baseline and is NOT transplanted into native app yet.
- ARM64-only; unique application ID `com.dreamscape.hand.phase22.async` so old tested version stays installed.

## Device gates
1. Build passes, APK artifact exists.
2. On vivo S30, compare overall live camera preview smoothness against 2.2.1 while moving the phone slowly.
3. Move finger quickly: separately note if pink fingertip overlay visibly trails camera image. Check overlay orientation.
4. Record displayed `MediaPipe推理` and `结果年龄` before/after.
5. Confirm Depth API candidate data if device supplies it; don't equate candidate scene depth to true finger depth.
6. If overlay significantly lags, optimize inference speed (GPU or model input scaling) only after GL decoupling is confirmed on device.
