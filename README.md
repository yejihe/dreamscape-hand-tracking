# 🌌 Dreamscape Hand

独立的手势空间绘画研究仓库。目标：将捏合手势与 ARCore 世界坐标结合，实现真实三维空间笔触。

## 基线与状态
- **R10 (已用户实测)：** `hand-test.html`，MediaPipe 21 点、拇指/食指捏合、Canvas 2D 绘画。
- **Phase 2 / Geometry Spike 0.1.0 (代码已上传)：** `experiments/dreamscape-hand-phase2/`，已知真实深度时计算相机/世界坐标。
- **未完成：** 原生 Android MediaPipe+ARCore 同帧集成、指尖实测深度、世界空间手指绘画、头显 MR。

## 开发隔离
原仓库 `yejihe/dreamscape-v12-spatial-pen` 的 V12 Android 手机空间画笔不受本仓库管理，不应直接从这里修改。

## 依赖与部署
`.github/workflows/vendor-hand-tracking.yml` 通过 GitHub Actions 生成 `vendor/mediapipe/` 的 WASM、JS、模型本地副本。R10 在这些文件进入仓库且 GitHub Pages 启用后才能运行。

如未出现 `vendor/mediapipe/hand_landmarker.task`，需要运行 **Vendor Hand Tracking Assets** workflow 并检查日志；不要把尚未部署的页面视为已验证。

本仓库 R10 迁移后的手机复测仍待完成。历史用户实测发生在原 V12 仓库，不等于新仓库已测试通过。
