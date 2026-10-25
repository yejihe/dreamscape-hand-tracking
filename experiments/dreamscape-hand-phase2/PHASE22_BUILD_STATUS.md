# Phase 2.2 Depth Probe · verified build status (2026-10-25)

## Phone evidence of 2.1.3 (user screenshots)
- 63 and 79 processed ARCore-associated camera frames; skipped 0, hand count 1.
- Index fingertip normalized coordinates (0.280,0.538) then (0.393,0.689).
- Tracked camera position changed across frames; **not** a fingertip world position.
- 153ms and 175ms inference (large latency for painting).
- Phone preview works; Android original camera-image timestamp offset ~9.76ms.

## Current 2.2 implementation
- Draw 21 MediaPipe hand landmarks on preview using ARCore `IMAGE_NORMALIZED→VIEW` coordinate transform; pink dot indicates fingertip 8, cyan skeleton and green dots.
- Check `Session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)`, enable when supported.
- Sample `Frame.acquireDepthImage16Bits` at fingertip-mapped depth-pixel location using `IMAGE_NORMALIZED→TEXTURE_NORMALIZED` per official guide. Display 3x3 candidate scene-depth median and validity; this is NOT verified finger-surface depth.
- Separate native app ID `com.dreamscape.hand.phase22.depth`, preserving V12 and earlier diagnostics.
- Development branch `experiment/phase2-2-depth-probe` and draft PR #2 (against Phase 2.1).

## Verified CI / artifact
- Workflow run: https://github.com/yejihe/dreamscape-hand-tracking/actions/runs/40156221863
- Job: `phase21-debug-apk` **success**, `:app:assembleDebug` **success**, artifact upload **success**.
- Artifact ID **12584820498**. Exported ZIP `Dreamscape-Hand-Phase2.2-DepthProbe.zip`.
- ZIP checked with `unzip -t`: all members OK. Contains `app-debug.apk` (55,669,968 bytes). ZIP sha256 `4a1954cb98f4d49e5c07d4bdfa56025cd54863886d338c5771ee8511f820b748`.

## Still not verified
- 2.2 APK device install/camera preview.
- Display overlay actually aligns with finger on vivo S30; rotation and mirroring.
- Depth support on this device, coverage, stability, latency, or suitability for moving fingertips.
- No verified fingertip optical depth, world XYZ, world-locked strokes, or MR stereo view yet.

## Next device test
Install 2.2 APK and visually check pink dot at index fingertip. Report whether screen displays `Depth API: 不支持`, `深度图暂不可用`, or `候选场景深度`. If candidate is available, test at ~30cm, 50cm, 80cm using a ruler and compare background vs hand; never equate candidate depth with hand depth without validation.
