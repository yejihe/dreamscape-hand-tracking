# Dreamscape Hand / Phase 2.1 · same-frame bridge (source-only spike)

Status: **submitted code only**. No APK, Gradle build, phone test, or verified 3D fingertip position yet.

## Scope
`SyncedHandFrameProcessor.kt` consumes **one ARCore Frame** (from an Android app managing Session / GL camera texture) and passes its **CPU camera image** to an Android MediaPipe HandLandmarker in VIDEO mode. It returns the matching image/frame nanosecond timestamps, image intrinsics, camera pose and 21 landmarks, or a skip reason.

This experiment does **not** change V12 Android, the browser hand-test, or the main branch. It does not derive fingertip depth or treat normalized landmarks as world coordinates.

## Host application prerequisites
- An Android app with a working ARCore Session and camera preview; call `session.update()` from the appropriate GL render thread after configuring camera texture.
- Include ARCore Android SDK and MediaPipe Tasks Vision Android SDK, plus a compatible hand_landmarker.task file in the Android assets (the GitHub Pages .task file is not automatically an Android asset).
- Create Android `HandLandmarker` with `RunningMode.VIDEO` and `numHands=1`.
- Call `processor.process(frame)` serially; log Outcome instead of blindly assuming every frame is usable.
- Handle `NotYetAvailableException`, `ResourceExhaustedException`, and session lifecycle at the host callsite. Release ARCore and MediaPipe resources on shutdown.

## Correctness conditions
1. The supplied image and ARCore Frame must share the **same nanosecond timestamp**. Mismatches are dropped, never paired with latest pose.
2. MediaPipe VIDEO timestamps are converted from the same ARCore timestamp and must increase strictly.
3. Landmarks are reported in the unrotated camera CPU image pixel geometry (MediaPipe normalized coordinates), **not** device display coordinates. Validate 0/90/180/270-degree transformations with diagnostic markers before using Geometry Spike.
4. `frame.camera.pose` refers to ARCore physical camera pose; do not substitute display-oriented pose without transforming image coordinates.
5. Do not infer physical finger depth from normalized MediaPipe z or an ARCore environment depth at a hand pixel without validation.

## Test gates (NOT passed yet)
- Build host application successfully on a clean Android Gradle setup.
- On vivo S30, log 100+ timestamp-matched captures and landmark count, include tracking state.
- Visualize the raw image and 21 landmarks to verify portrait/landscape orientation and mirroring.
- Benchmark inference time, CPU conversion time, frame drop rate and thermals.
- Once image-space points are calibrated, validate known-depth projection and forward/back finger displacement.

## Performance caution
The stride-aware YUV conversion is intentionally scalar and may be too slow for a real-time experience. First validate correctness, then profile before optimizing.

## Pages migration caution (2026-10-23)
The repository's current `hand-test.html` identifies itself as **R12**, and is **not byte-identical** to the original repository's verified **R10**. The R10 gesture hysteresis remains .42/.65; camera switching and mirroring were subsequently revised. Check Pages publishing/deployment status via GitHub Settings → Pages and Actions before declaring the new site live. Do not change Pages settings blindly.
