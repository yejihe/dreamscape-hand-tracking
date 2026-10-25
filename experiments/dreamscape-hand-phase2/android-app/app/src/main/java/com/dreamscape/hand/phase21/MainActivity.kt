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
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import android.util.Log
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
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
    private lateinit var handOverlay: HandLandmarkOverlay
    @Volatile private var depthSupported = false
    @Volatile private var arSession: Session? = null
    private var installRequested = false
    private var processor: AsyncHandFrames? = null
    private val inferWorker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "dreamscape-mediapipe-worker")
    }
    private val inferenceBusy = AtomicBoolean(false)
    @Volatile private var active = false
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
            text = "🌌 Phase 2.2.2｜等待 ARCore 摄像头…\n绿色骨架、粉色指尖用于2D对齐测试；深度未验证"
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xD9092035.toInt())
            setPadding(22, 20, 22, 20)
        }
        val root = FrameLayout(this)
        root.addView(glView, FrameLayout.LayoutParams(-1, -1))
        handOverlay = HandLandmarkOverlay(this)
        root.addView(handOverlay, FrameLayout.LayoutParams(-1, -1))
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
                val session = requireNotNull(arSession)
                depthSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
                if (depthSupported) {
                    val config = session.config
                    config.depthMode = Config.DepthMode.AUTOMATIC
                    session.configure(config)
                }
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
                processor = AsyncHandFrames(requireNotNull(landmarker))
            }
            arSession?.resume()
            active = true
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
        active = false
        if (::glView.isInitialized) glView.onPause()
        arSession?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        active = false
        arSession?.close()
        arSession = null
        processor = null
        // Any pending infer() completes before model closure on its single worker.
        val toClose = landmarker
        landmarker = null
        inferWorker.execute { toClose?.close() }
        inferWorker.shutdown()
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
            if (!active) return
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
                val frameNs = frame.timestamp
                val camera = frame.camera
                val tracking = camera.trackingState

                // Camera preview no longer waits for MediaPipe's 150ms inference.
                if (tracking != TrackingState.TRACKING) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastReportMs > 800) {
                        runOnUiThread { handOverlay.setPoints(null) }
                        update("ARCore追踪状态：${tracking}｜原因：${camera.trackingFailureReason}\n" +
                            "画面仍正常刷新，正在等待空间追踪")
                    }
                    return
                }
                if (frameNs <= 0 ||
                    (lastSampleNs > 0 && frameNs - lastSampleNs < 75_000_000L))
                    return
                if (!inferenceBusy.compareAndSet(false, true)) return
                val engine = processor
                if (engine == null) {
                    inferenceBusy.set(false)
                    return
                }
                lastSampleNs = frameNs
                try {
                    // ARCore Session + Frame + Image remain on this GL thread.
                    // Copy raw byte planes and exact camera pose/intrinsics before
                    // advancing the Session or returning to the next GL frame.
                    val packet = engine.capture(frame, depthSupported)
                    inferWorker.execute {
                        try {
                            val result = engine.analyze(packet)
                            if (active) runOnUiThread {
                                if (active) {
                                    captured++
                                    handOverlay.setPoints(result.landmarkPointsInView)
                                    val tip = result.tip
                                    val p = result.packet
                                    val tipText = if (tip == null) "未检测到手"
                                        else String.format(Locale.US,
                                            "(%.3f, %.3f)", tip[0], tip[1])
                                    val msg = String.format(Locale.US,
                                        "📍手：%d｜处理：%d 帧｜跳过：%d\n" +
                                        "指尖二维位置：%s\n" +
                                        "%s\n" +
                                        "MediaPipe推理：%dms｜结果年龄：%dms\n" +
                                        "相机XYZ(米)：%.2f, %.2f, %.2f\n" +
                                        "相机-图像时间差：%.2fms\n" +
                                        "摄像头与识别分线程，避免画面被推理阻塞。\n" +
                                        "⚠ 深度候选非可靠手指深度，未完成三维绘画",
                                        result.handCount, captured, skipped, tipText,
                                        result.depthText, result.inferenceMs, result.resultAgeMs,
                                        p.worldCamera[0], p.worldCamera[1],
                                        p.worldCamera[2], (p.imageNs - p.cameraNs) / 1_000_000.0)
                                    report("🌌 Dreamscape Hand｜Phase 2.2.2 流畅度实验\n" + msg)
                                }
                            }
                        } catch (e: Exception) {
                            if (active) report("手部识别线程错误：${e.javaClass.simpleName}: ${e.message}")
                        } finally {
                            inferenceBusy.set(false)
                        }
                    }
                } catch (e: NotYetAvailableException) {
                    inferenceBusy.set(false)
                    update("相机/深度暂未就绪：继续等待")
                } catch (e: Exception) {
                    inferenceBusy.set(false)
                    skipped++
                    update("图像采集出错：${e.javaClass.simpleName}: ${e.message}")
                }
            } catch (e: CameraNotAvailableException) {
                update("ARCore相机不可用：${e.message}")
            } catch (e: Exception) {
                update("ARCore渲染错误：${e.javaClass.simpleName}: ${e.message}")
            }
        }

        private fun update(value: String) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastReportMs >= 500L) {
                lastReportMs = now
                report("🌌 Dreamscape Hand｜Phase 2.2 指尖深度探针\n" + value)
            }
        }
    }
}
