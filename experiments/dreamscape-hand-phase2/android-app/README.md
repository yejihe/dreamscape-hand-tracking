# Dreamscape Hand Phase 2.1 · isolated native Android host

> This is an **experimental source branch**, not a verified APK. Original V12 and browser tests remain untouched.

**Goal:** the same ARCore `Frame` supplies both CPU image for MediaPipe Hand Landmarker and tracking pose/intrinsics with matching frame timestamps. Camera preview on OpenGL ES is *monocular* and only a diagnostic view — not VR/MR 3D fingertip drawing.

**Branch:** `experiment/phase2-1-arcore-mediapipe-sync`.

**Toolchain:** Android Gradle Plugin 8.7.3, Gradle 8.9, JDK 17; minSdk 24, compileSdk 35; ARCore 1.56.0, MediaPipe Android Tasks Vision 0.10.35. Uses the previously checked-in `vendor/mediapipe/hand_landmarker.task` from the independent repo (browser JS version is not the Android SDK).

**Build:** from this directory run `gradle :app:assembleDebug` with Android 35 SDK installed. GitHub Actions workflow `phase21-android-build.yml` attempts a build and uploads a debug APK only if it succeeds. A submitted workflow is NOT evidence that the build has passed.

**Diagnostics shown on screen:** live ARCore camera preview, matched image/frame timestamp, hand count, normalized (raw image) index fingertip 2D coordinate, tracked camera XYZ, processing time.

**Gates before claiming Phase 2.1 success:**
1. GitHub Actions Android APK compilation passes and artifact exists.
2. Install on vivo S30 and grant camera permission; confirm preview shows the rear environment (not black).
3. Move a hand into frame and confirm 21 MediaPipe landmarks are detected consistently, including rotation check in portrait.
4. Capture >=100 matched samples: image.timestamp == frame.timestamp with monotonic timestamps. Show camera tracking state and record failures.
5. Confirm 2D landmark geometry mapping from `IMAGE_NORMALIZED/IMAGE_PIXELS` -> `VIEW` with ARCore's `frame.transformCoordinates2d`. Do not rely on web mirroring in the native app.
6. Separately measure fingertip depth before using `FingertipProjection` for ARCore world position.
7. Validate 20 cm forward/back finger movement and world-coordinate invariance when moving the phone around a stationary fingertip.

**Implementation compromises:** synchronous CPU YUV-to-RGB conversion and MediaPipe VIDEO inference run on the GLSurfaceView render thread, deliberately throttled to about 8 Hz. This can visibly lag/stutter; correct-frame logging is the goal. Next optimize threading and image rotation only after inspecting logs/device results.

**Risk:** the app has not been compiled, installed or tested on a phone at source submission time. If GitHub Actions fails, do not distribute the APK as functional. Check the actual failed step first.
