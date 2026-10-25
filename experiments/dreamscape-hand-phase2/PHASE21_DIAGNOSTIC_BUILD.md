# Phase 2.1.1 Diagnostic APK build result (2026-10-25)

## Confirmed
- User's Phase 2.1 device screenshot: installation and app launch succeeded on vivo S30; screen black, status "ARCore camera not TRACKING". Real cause remains **undetermined**.
- New source adds ARCore TrackingState + TrackingFailureReason, Frame.timestamp, paused-state CPU image availability and GL errors, and Android status bar insets.
- New applicationId: `com.dreamscape.hand.phase21.diag` (can install side-by-side with prior debug APK signed by a different CI runner).
- GitHub Actions run **40153963018**, job **121051197807**: **success**; build and artifact upload steps both succeeded.
- Artifact id **12584220665**; archive `dreamscape-hand-phase21-1-diagnostic.zip`, containing `app-debug.apk`.
- Extracted APK size **55,669,964 bytes**; SHA-256 **30fda560540482e411c3a302f0cea39fe7c536e59bb1e6fb23082e11b571b448**.
- The APK is a native diagnostic prototype, not proof that camera tracking or 3D fingertip drawing works.

## Next phone gate
1. Install the new diagnostic APK as a separate app (do not uninstall V12 or prior diagnostic if not necessary).
2. Turn on Android system-wide camera access. In a reasonably bright area, aim at objects with visible texture; move phone slowly for visual tracking initialization.
3. Capture displayed **追踪 / 原因 / 帧时间戳 / CPU图像 / OpenGL错误码** in screenshot.
4. If tracking reports TRACKING but preview still black, investigate OES graphics path. If CPU image also unavailable and frame timestamp stays zero, investigate ARCore camera startup/session permissions. If failure reason explains paused tracking, resolve it first.
5. Do not claim Phase 2.1 same-frame integration or fingertip XYZ validated until samples have been captured on device.

## Isolation
- Original V12 Android and web-based R16.3 are unchanged.
- Source remains in draft PR #1, not merged into main.
