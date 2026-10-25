# Dreamscape Hand Phase 2.1.2 · verified build (2026-10-25)

## Device observation from user
- Previous 2.1.1 native diagnostic APK **installed and started on vivo S30**.
- Camera preview **is now visible**, ARCore reports `已追踪` (TRACKING) and OpenGL=0.
- HUD reports `image/frame timestamp mismatch`; **MediaPipe result was never reached in this screenshot**.
- Prior source erroneously compared Android CPU `Image.timestamp` to `ARCore Frame.timestamp`; different timestamp domains cannot be assumed equal.

## Fix now committed
- Compare CPU `Image.timestamp` with ARCore `Frame.androidCameraTimestamp`. If unavailable or mismatched, skip and log all three timestamps and nanosecond delta. **No forced equality or unstated tolerance**.
- Continue using `Frame.timestamp` for fresh-frame gating and MediaPipe VIDEO monotonic inference milliseconds.
- Surface all three timestamps on successful detection; image and pose derive from same `Frame`.
- Distinct application ID `com.dreamscape.hand.phase21.sync` for a side-by-side install, leaving previous diagnostic and V12 alone.

## Actual CI and artifact evidence
- GitHub Actions **40154416698**: `phase21-debug-apk` job **success**, `assembleDebug` **success**, artifact upload **success**.
- Artifact ID **12584451008**: `dreamscape-hand-phase21-debug-apk`.
- Downloaded ZIP `Dreamscape-Hand-Phase2.1.2-SameFrame.zip` checked with `unzip -t`: **No errors**.
- ZIP contains one file: `app-debug.apk` (55,669,964 bytes).
- ZIP SHA-256: `733d5d06189afbda5ad59e993c7888da2057c474f275a8876c20cacc061c3e1f`.

## Pending device verification
1. Install new APK; expect camera preview and `同帧校验通过：N 帧` incrementing.
2. Hold hand in view; expect `手部数 1` and changing normalized fingertip coordinates.
3. If still skipped, record three timestamps and reported delta, distinguish separate device-specific timestamp problem.
4. Verify portrait orientation / 2D overlay before using intrinsics or estimating depth.
5. 3D finger depth, 3D world coordinates, and world-locked strokes **remain unimplemented**.

This is a verified **build**, NOT a verified device fix.
