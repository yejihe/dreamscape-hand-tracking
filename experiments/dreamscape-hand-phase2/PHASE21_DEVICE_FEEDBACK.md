# Phase 2.1 实机反馈与追踪诊断（2026-10-25）

## 已确认的设备观察
- 用户在 vivo S30 上成功安装、启动 Phase 2.1 原生 Android 应用（不再属于“打不开安装包”）。
- 设备截图显示深色/黑色摄像头区域，顶部文字为「跳过：ARCore camera not TRACKING」。
- 原版没有输出 `Camera.getTrackingFailureReason()`，因此**无法据此判定**黑屏一定由摄像头权限、过暗、纹理不足或 OpenGL 纹理错误引起。
- 画面在状态栏区域有文字重叠；属于 UI inset 适配缺陷。
- 未观察到手部关键点检出，更没有真实三维手指世界坐标。

## 改进：Phase 2.1.1 diagnostic
- 展示 ARCore `Camera.getTrackingState()` 与 `getTrackingFailureReason()`。
- 展示 `Frame.timestamp`，包括 0 时间戳和重复帧。
- 每约 1.6 秒在 PAUSED 状态下尝试读取一帧 CPU 相机图像，并显示是否可读、分辨率/时间戳或异常名称。
- 展示 OpenGL 错误码，避免将 GPU 背景绘制异常误认为定位问题。
- 状态栏上方留白，通过 WindowInsets 防止文字被系统状态栏遮挡。
- 新应用 ID `com.dreamscape.hand.phase21.diag`，与旧 APK 并排安装，不要求用户反复卸载原版。

## 仍待验证
- 新诊断 APK 实际编译、安装和运行结果。
- 黑屏究竟是 ARCore 没有新帧、仅视觉追踪暂停，还是背景纹理绘制问题。
- 同帧 ARCore+MediaPipe 真实设备验证（>=100 样本）。
- 指尖深度和三维世界坐标未完成。

## 实测指令
打开系统相机总开关；选择光线充足、有细节的场景；缓慢移动手机。记录新诊断页的「追踪」「原因」「帧时间戳」「CPU图像」「OpenGL错误码」，**不要预先假设诊断结果**。
