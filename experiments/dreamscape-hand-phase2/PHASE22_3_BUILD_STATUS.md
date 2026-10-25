# Phase 2.2.3 · 实机反馈、深度诊断和版本交付

## 本次手机截图的真实结论（2026-10-25）
- 已追踪到1只手，处理392帧，跳过0；颜色骨架显示在真实手附近；粉色食指点在画面左边缘，食指顶部部分在视野外。
- `MediaPipe推理 83ms`、`结果年龄84ms`：此为截图时的测量，尚未得到全程均值或端到端视觉延迟分布。
- 手机的ARCore相机位置 `(-0.19,-0.02,0.47)m`，不是手指世界坐标。
- `候选场景深度18639mm`=18.639m；与画面中眼前的手明显不符。3×3读到9/9非零值，并不意味着它测到了手指表面或具有准确性。
- 可能原因：Depth API场景补全/背景值、像素落入背景、手指细小导致深度缺失、坐标映射或延迟。尚不能锁定具体一种。

## 2.2.3 独立修订
- Source branch `experiment/phase2-2-depth-validation` (draft PR #4), preserved 2.2.2 async renderer and older build.
- Keep raw ARCore depth in mm visible, label as **scene depth candidate**; do not treat 18.639m as finger physical Z.
- Project-specific near-hand **150–2000mm heuristic** flags obviously incompatible values; this is NOT ARCore hardware range and must not silently cap to 2m.
- Add a **75mm hypothetical** palm breadth (landmark MCP #5–#17) intrinsics-based distance proxy, explicitly uncalibrated: side-on palm angle and user palm size affect the result. Can detect gross discrepancy, cannot be promoted to world XYZ or true hand depth.
- Still need ARCore IMAGE-to-Depth mapping validation, color/depth crop, timing comparison, fingertip foreground depth confidence, and physical calibration.

## Verified build
- GitHub Actions run: https://github.com/yejihe/dreamscape-hand-tracking/actions/runs/40157949978
- Android build job **completed success**, artifact uploaded.
- Artifact ID **12585687327** `dreamscape-hand-phase21-debug-apk`.
- ZIP `Dreamscape-Hand-2.2.3-DepthCheck-ARM64.zip` verified by unzip -t: no errors, one `app-debug.apk` (19,746,808 bytes).
- Release publishing workflow committed; GitHub main now includes `downloads/index.html` and `downloads/Dreamscape-Hand-2.2.3-arm64.apk`. GitHub Pages public HTTP accessibility not independently confirmed.
- **No phone test of 2.2.3 yet.** Showing a numeric rough distance is not evidence of 3D localisation.

## Next device checks
- Keep full index fingertip inside the camera frame, not clipped on the left border; test hand phone distance ~30, 50, 80cm while viewing depth and palm proxy.
- If ARCore scene depth remains 18m range, stop using it for finger depth. Future raw depth-confidence analysis or physical palm-size calibration is required.
