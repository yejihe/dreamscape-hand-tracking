package com.dreamscape.hand.phase21

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowInsets
import android.os.Build
import com.google.ar.core.TrackingState
import com.google.ar.core.TrackingFailureReason
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.ResourceExhaustedException
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.Locale
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Dedicated Phase 2.1 Android host. NOT Dreamscape V12.
 * Camera image -> MediaPipe and camera pose/intrinsics come from the SAME Frame.
 * Preview is one monocular camera, not VR/MR or 3D hand location.
 */
class MainActivity : Activity() {
    private lateinit var glView: GLSurfaceView
    private lateinit var statusLabel: TextView
    @Volatile private var arSession: Session? = null
    private var installRequested = false
    private var processor: SyncedHandFrameProcessor? = null
    private var landmarker: HandLandmarker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
            setRenderer(HandRenderer())
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        statusLabel = TextView(this).apply {
            text = "🌌 Phase 2.1.2｜等待 ARCore 摄像头…\n仅诊断摄像头和同帧追踪，非三维绘画"
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xD9092035.toInt())
            setPadding(22, 20, 22, 20)
        }
        val root = FrameLayout(this)
        root.addView(glView, FrameLayout.LayoutParams(-1, -1))
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP)
        root.addView(statusLabel, params)
        // Android 15+ edge-to-edge: keep diagnostic text out of the status bar.
        root.setOnApplyWindowInsetsListener { _, insets ->
            val top = if (Build.VERSION.SDK_INT >= 30)
                insets.getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top
            else @Suppress("DEPRECATION") insets.systemWindowInsetTop
            val lp = statusLabel.layoutParams as FrameLayout.LayoutParams
            if (lp.topMargin != top) {
                lp.topMargin = top
                statusLabel.layoutParams = lp
            }
            insets
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        startArIfPermitted()
    }

    private fun startArIfPermitted() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 71)
            return
        }
        try {
            if (arSession == null) {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        report("请完成系统提示的 ARCore 安装/更新")
                        return
                    }
                    else -> Unit
                }
                arSession = Session(this)
            }
            if (landmarker == null) {
                val options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
                    .setRunningMode(RunningMode.VIDEO)
                    .setNumHands(1)
                    .setMinHandDetectionConfidence(0.55f)
                    .setMinTrackingConfidence(0.5f)
                    .build()
                landmarker = HandLandmarker.createFromOptions(this, options)
                processor = SyncedHandFrameProcessor(requireNotNull(landmarker))
            }
            arSession?.resume()
            glView.onResume()
        } catch (e: Exception) {
            report("原生启动失败：${e.javaClass.simpleName}\n${e.message ?: "未知错误"}")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 71) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startArIfPermitted()
            else report("需要相机权限才能启动；请在系统设置授权")
        }
    }

    override fun onPause() {
        if (::glView.isInitialized) glView.onPause()
        arSession?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        arSession?.close()
        arSession = null
        landmarker?.close()
        landmarker = null
        processor = null
        super.onDestroy()
    }

    private fun report(message: String) {
        runOnUiThread { statusLabel.text = message }
    }

    private inner class HandRenderer : GLSurfaceView.Renderer {
        private var preview: CameraBackground? = null
        private var textureBound = false
        private var width = 0
        private var height = 0
        private var lastSampleNs = -1L
        private var lastReportMs = 0L
        private var captured = 0
        private var skipped = 0
        private var lastFrameTimestamp = -1L
        private var lastCpuProbeMs = 0L
        private var cpuProbe = "CPU图像：尚未测试"

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            preview = CameraBackground()
            textureBound = false
            GLES20.glClearColor(0f, 0f, 0f, 1f)
        }

        override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
            width = w
            height = h
            GLES20.glViewport(0, 0, w, h)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            val session = arSession ?: return
            val background = preview ?: return
            try {
                if (!textureBound) {
                    session.setCameraTextureName(background.textureId)
                    textureBound = true
                }
                if (width <= 0 || height <= 0) return
                @Suppress("DEPRECATION")
                session.setDisplayGeometry(windowManager.defaultDisplay.rotation, width, height)
                val frame = session.update()
                background.draw(frame)
                val glErr = GLES20.glGetError()
                val frameNs = frame.timestamp
                val camera = frame.camera
                val tracking = camera.trackingState
                val failure = camera.trackingFailureReason
                val repeats = frameNs == lastFrameTimestamp
                lastFrameTimestamp = frameNs

                // The old app merely displayed "not TRACKING". That masked
                // whether the camera never produced images, or visual SLAM had
                // insufficient light/features, or a competing app used the camera.
                if (tracking != TrackingState.TRACKING) {
                    val now = SystemClock.elapsedRealtime()
                    if (frameNs > 0 && now - lastCpuProbeMs > 1600) {
                        lastCpuProbeMs = now
                        cpuProbe = try {
                            frame.acquireCameraImage().use { im ->
                                "CPU图像：可读 ${im.width}×${im.height} / t=${im.timestamp}"
                            }
                        } catch (e: NotYetAvailableException) {
                            "CPU图像：尚未就绪"
                        } catch (e: Exception) {
                            "CPU图像：${e.javaClass.simpleName}"
                        }
                    }
                    val advice = when (failure) {
                        TrackingFailureReason.INSUFFICIENT_LIGHT -> "请打开灯光，检查系统相机总开关"
                        TrackingFailureReason.INSUFFICIENT_FEATURES -> "对准桌面等有纹理物体，缓慢移动手机"
                        TrackingFailureReason.EXCESSIVE_MOTION -> "请减慢手机移动速度"
                        TrackingFailureReason.CAMERA_UNAVAILABLE -> "请关闭其他使用摄像头的应用"
                        TrackingFailureReason.BAD_STATE -> "ARCore内部状态异常，重新进入应用"
                        TrackingFailureReason.NONE -> "正在初始化；保持手机稳定，对准明亮有纹理的场景"
                    }
                    update("追踪：${tracking}｜原因：${failure}\n帧时间戳：${frameNs} ${if(repeats) "(重复帧)" else ""}\n${cpuProbe}\nOpenGL错误码：${glErr}\n${advice}\n⚠ 未定位手指")
                    return
                }
                if (frameNs == 0L || (lastSampleNs > 0 && frameNs - lastSampleNs < 120_000_000L))
                    return
                lastSampleNs = frameNs
                val worker = processor ?: return
                val outcome = worker.process(frame)
                when (outcome) {
                    is SyncedHandFrameProcessor.Outcome.Skipped -> {
                        skipped++
                        update("已追踪，但还未完成同帧检测：${outcome.reason}\n帧=${frameNs}｜OpenGL=${glErr}")
                    }
                    is SyncedHandFrameProcessor.Outcome.Captured -> {
                        captured++
                        val sample = outcome.sample
                        val hands = sample.landmarks.landmarks().size
                        val p = sample.translation
                        val tip = sample.landmarks.landmarks().firstOrNull()?.getOrNull(8)
                        val tipText = if (tip == null) "没有识别到手"
                          else String.format(Locale.US, "食指图像归一化坐标 (%.3f, %.3f)", tip.x(), tip.y())
                        update(String.format(Locale.US,
                            "同帧校验通过：%d 帧｜跳过 %d\n手部数 %d ｜ %s\n相机 XYZ (米): %.2f, %.2f, %.2f\n图像 %dx%d｜推理 %dms\nFrame时间戳：%d\nAndroid相机时间戳：%d\nCPU图像时间戳：%d\n⚠ 尚无手指深度，不能称为三维手指定位",
                            captured, skipped, hands, tipText, p[0], p[1], p[2],
                            sample.imageWidth, sample.imageHeight,
                            sample.processingMs, sample.frameTimestampNs,
                            sample.androidCameraTimestampNs, sample.imageTimestampNs))
                    }
                }
            } catch (e: NotYetAvailableException) {
                update("ARCore 图像暂不可用：继续等待")
            } catch (e: ResourceExhaustedException) {
                update("CPU 图像缓冲不足，继续等待")
            } catch (e: CameraNotAvailableException) {
                update("ARCore 相机已不可用：${e.message}")
            } catch (e: Exception) {
                update("Phase 2.1 错误：${e.javaClass.simpleName}：${e.message}")
            }
        }

        private fun update(value: String) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastReportMs >= 500L) {
                lastReportMs = now
                report("🌌 Dreamscape Hand｜Phase 2.1.2 同帧实验\n" + value)
            }
        }
    }
}
