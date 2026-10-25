package com.dreamscape.hand.phase21

import android.graphics.Bitmap
import android.media.Image
import android.os.SystemClock
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.nio.ByteOrder

/**
 * Asynchronous ARCore -> MediaPipe CPU pipeline.
 *
 * capture() is called only on the GL/render thread; it copies a complete,
 * ARCore-associated CPU image and corresponding camera intrinsics/pose,
 * 2D projection transforms, and optionally one depth map, then CLOSES Images.
 *
 * analyze() runs on ONE serial worker thread, so detectForVideo() can block
 * without blocking camera rendering. No Frame/Session/Image crosses threads.
 *
 * Queueing policy is managed by the caller: only one in-flight frame at a time.
 * This favors live camera responsiveness and bounded latency over analyzing
 * every frame. A delayed result remains attached to the CAPTURED frame pose.
 */
internal class AsyncHandFrames(private val detector: HandLandmarker) {
    data class PackedPlane(val data: ByteArray, val rowStride: Int, val pixelStride: Int)
    data class DepthMap(
        val width: Int, val height: Int,
        val plane: PackedPlane,
        val normalizedImageToTexture: FloatArray
    )
    data class Packet(
        val frameNs: Long,
        val cameraNs: Long,
        val imageNs: Long,
        val width: Int,
        val height: Int,
        val focal: FloatArray,
        val center: FloatArray,
        val worldCamera: FloatArray,
        val worldQuaternion: FloatArray,
        val normalizedImageToView: FloatArray,
        val y: PackedPlane,
        val u: PackedPlane,
        val v: PackedPlane,
        val depth: DepthMap?,
        val capturedAtMs: Long
    )
    data class Result(
        val packet: Packet,
        val handCount: Int,
        val landmarkPointsInView: FloatArray?,
        val tip: FloatArray?,
        val depthText: String,
        val inferenceMs: Long,
        val resultAgeMs: Long
    )

    private var previousDetectorMs = -1L

    /** Capture one coherent ARCore Frame. Throws ARCore NotYetAvailableException when not ready. */
    fun capture(frame: Frame, tryDepth: Boolean): Packet {
        check(frame.camera.trackingState == TrackingState.TRACKING) { "ARCore not tracking" }
        val frameNs = frame.timestamp
        check(frameNs > 0L) { "ARCore frame timestamp unavailable" }
        val camera = frame.camera
        val intrinsics = camera.imageIntrinsics
        val focal = intrinsics.focalLength.copyOf()
        val center = intrinsics.principalPoint.copyOf()
        val size = intrinsics.imageDimensions
        val pose = camera.pose
        val basisInput = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f)
        val viewBasis = FloatArray(6)
        val textureBasis = FloatArray(6)
        frame.transformCoordinates2d(Coordinates2d.IMAGE_NORMALIZED, basisInput,
            Coordinates2d.VIEW, viewBasis)
        frame.transformCoordinates2d(Coordinates2d.IMAGE_NORMALIZED, basisInput,
            Coordinates2d.TEXTURE_NORMALIZED, textureBasis)

        // The depth image may not be ready, even when 6DoF tracking is good.
        // Take a compact byte snapshot while the ARCore Frame is CURRENT.
        val depth: DepthMap? = if (!tryDepth) null else try {
            frame.acquireDepthImage16Bits().use { image ->
                DepthMap(image.width, image.height, pack(image.planes[0]), textureBasis)
            }
        } catch (_: NotYetAvailableException) {
            null
        }

        val cameraNs = frame.androidCameraTimestamp
        val im = frame.acquireCameraImage()
        return im.use {
            require(it.format == android.graphics.ImageFormat.YUV_420_888) { "Unexpected CPU YUV image format" }
            require(it.width == size[0] && it.height == size[1]) { "CPU image and intrinsics size mismatch" }
            require(it.timestamp > 0 && cameraNs > 0) { "Missing camera timestamp" }
            Packet(
                frameNs, cameraNs, it.timestamp, it.width, it.height,
                focal, center, pose.translation.copyOf(), pose.rotationQuaternion.copyOf(),
                viewBasis, pack(it.planes[0]), pack(it.planes[1]), pack(it.planes[2]),
                depth, SystemClock.elapsedRealtime()
            )
        }
    }

    private fun pack(p: Image.Plane): PackedPlane {
        val buffer = p.buffer.duplicate()
        buffer.position(0)
        val contents = ByteArray(buffer.remaining())
        buffer.get(contents)
        return PackedPlane(contents, p.rowStride, p.pixelStride)
    }

    /** CPU worker only; process sequentially for MediaPipe VIDEO timestamp monotonicity. */
    fun analyze(p: Packet): Result {
        val ms = p.frameNs / 1_000_000L
        check(ms > previousDetectorMs) { "Non-increasing inference timestamp" }
        val start = SystemClock.elapsedRealtime()
        val bmp = convert(p)
        val result = try {
            detector.detectForVideo(BitmapImageBuilder(bmp).build(), ms)
        } finally {
            bmp.recycle()
        }
        previousDetectorMs = ms
        val hand = result.landmarks().firstOrNull()
        val views = hand?.takeIf { it.size >= 21 }?.let { landmarks ->
            FloatArray(42) { i ->
                val dot = landmarks[i / 2]
                val xy = transform(p.normalizedImageToView, dot.x(), dot.y())
                xy[i % 2]
            }
        }
        val tip = hand?.getOrNull(8)?.let { floatArrayOf(it.x(), it.y()) }
        val depthText = if (p.depth == null) "当前帧未取得深度图 / 设备可能不支持"
            else if (tip == null) "有场景深度，未检测到手指"
            else sampleDepth(p.depth, tip[0], tip[1])
        return Result(p, result.landmarks().size, views, tip, depthText,
            SystemClock.elapsedRealtime() - start,
            SystemClock.elapsedRealtime() - p.capturedAtMs)
    }

    private fun transform(basis: FloatArray, x: Float, y: Float): FloatArray =
        floatArrayOf(
            basis[0] + x * (basis[2] - basis[0]) + y * (basis[4] - basis[0]),
            basis[1] + x * (basis[3] - basis[1]) + y * (basis[5] - basis[1])
        )

    private fun sampleDepth(d: DepthMap, ix: Float, iy: Float): String {
        val tex = transform(d.normalizedImageToTexture, ix, iy)
        if (tex[0] !in 0f..1f || tex[1] !in 0f..1f)
            return "指尖映射超出深度图范围"
        val px = (tex[0] * d.width).toInt().coerceIn(0, d.width - 1)
        val py = (tex[1] * d.height).toInt().coerceIn(0, d.height - 1)
        val plane = d.plane
        val arr = plane.data
        fun value(x: Int, y: Int): Int? {
            val start = y * plane.rowStride + x * plane.pixelStride
            if (start < 0 || start + 1 >= arr.size) return null
            val mm = (arr[start].toInt() and 0xFF) or
                ((arr[start + 1].toInt() and 0xFF) shl 8)
            return mm.takeIf { it in 1..20000 }
        }
        val values = ArrayList<Int>(9)
        for (y in (py - 1).coerceAtLeast(0)..(py + 1).coerceAtMost(d.height - 1))
            for (x in (px - 1).coerceAtLeast(0)..(px + 1).coerceAtMost(d.width - 1))
                value(x, y)?.let(values::add)
        values.sort()
        return if (values.isEmpty()) "指尖附近场景深度无效"
        else "候选场景深度：${values[values.size / 2]}mm（有效${values.size}/9；尚未验证是否为手指）"
    }

    private fun convert(p: Packet): Bitmap {
        val width = p.width
        val height = p.height
        val pixels = IntArray(width * height)
        val y = p.y; val u = p.u; val v = p.v
        for (row in 0 until height) {
            for (col in 0 until width) {
                val yp = row * y.rowStride + col * y.pixelStride
                val up = (row / 2) * u.rowStride + (col / 2) * u.pixelStride
                val vp = (row / 2) * v.rowStride + (col / 2) * v.pixelStride
                val yy = y.data[yp].toInt() and 255
                val uu = (u.data[up].toInt() and 255) - 128
                val vv = (v.data[vp].toInt() and 255) - 128
                val c = (yy - 16).coerceAtLeast(0)
                val red = ((298 * c + 409 * vv + 128) shr 8).coerceIn(0, 255)
                val green = ((298 * c - 100 * uu - 208 * vv + 128) shr 8).coerceIn(0, 255)
                val blue = ((298 * c + 516 * uu + 128) shr 8).coerceIn(0, 255)
                pixels[row * width + col] = (255 shl 24) or
                    (red shl 16) or (green shl 8) or blue
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
