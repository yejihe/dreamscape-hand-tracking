package com.dreamscape.hand.phase21

import android.graphics.Bitmap
import android.media.Image
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

/**
 * Phase 2.1 integration component, NOT a complete Android application.
 *
 * Preconditions:
 * - Caller owns an ARCore Session, calls session.update() on the GL thread,
 *   and invokes process() on that same thread (or serializes access to session/landmarker).
 * - HandLandmarker is created in RunningMode.VIDEO, using the Android .task asset.
 * - Caller initializes ARCore's camera texture and renders a preview separately.
 *
 * This deliberately uses the ARCore CPU image for BOTH landmarks and metadata.
 * No attempt is made to infer fingertip depth.
 */
class SyncedHandFrameProcessor(private val landmarker: HandLandmarker) {
    data class Sample(
        val frameTimestampNs: Long,
        val imageTimestampNs: Long,
        val detectorTimestampMs: Long,
        val imageWidth: Int,
        val imageHeight: Int,
        val fx: Float,
        val fy: Float,
        val cx: Float,
        val cy: Float,
        val translation: FloatArray,
        val quaternion: FloatArray,
        val landmarks: HandLandmarkerResult,
        val processingMs: Long
    )

    sealed class Outcome {
        data class Captured(val sample: Sample) : Outcome()
        data class Skipped(val reason: String) : Outcome()
    }

    private var previousTimestampNs = -1L
    private var previousDetectorMs = -1L

    fun process(frame: Frame): Outcome {
        if (frame.camera.trackingState != TrackingState.TRACKING)
            return Outcome.Skipped("ARCore camera not TRACKING")
        val frameNs = frame.timestamp
        if (frameNs <= 0 || frameNs <= previousTimestampNs)
            return Outcome.Skipped("stale ARCore frame")
        previousTimestampNs = frameNs

        // acquireCameraImage() may legitimately fail with NotYetAvailableException.
        // The outer caller should catch/log recoverable ARCore exceptions.
        val image = frame.acquireCameraImage()
        image.use {
            if (it.format != android.graphics.ImageFormat.YUV_420_888)
                return Outcome.Skipped("unexpected image format")
            if (it.timestamp != frameNs)
                return Outcome.Skipped("image/frame timestamp mismatch")
            val timestampMs = frameNs / 1_000_000L
            if (timestampMs <= previousDetectorMs)
                return Outcome.Skipped("non-increasing MediaPipe milliseconds")

            // Raw CPU image coordinates, NOT preview/portrait screen coordinates.
            // Rotation, device orientation, mirroring and projection MUST be
            // calibrated explicitly before sending pixels to Geometry Spike.
            val intrinsic = frame.camera.imageIntrinsics
            val focal = intrinsic.focalLength
            val center = intrinsic.principalPoint
            val dimensions = intrinsic.imageDimensions
            if (dimensions[0] != it.width || dimensions[1] != it.height)
                return Outcome.Skipped("intrinsics and CPU image size disagree")

            val pose = frame.camera.pose
            val started = android.os.SystemClock.elapsedRealtime()
            val bitmap = yuv420ToBitmap(it)
            val result = landmarker.detectForVideo(BitmapImageBuilder(bitmap).build(), timestampMs)
            val duration = android.os.SystemClock.elapsedRealtime() - started
            bitmap.recycle()
            previousDetectorMs = timestampMs

            return Outcome.Captured(
                Sample(frameNs, it.timestamp, timestampMs, it.width, it.height,
                    focal[0], focal[1], center[0], center[1],
                    pose.translation.copyOf(), pose.rotationQuaternion.copyOf(),
                    result, duration)
            )
        }
    }

    /**
     * Slow, simple stride-correct YUV_420_888 -> RGB conversion for proof of concept.
     * Avoids assumptions about UV interleaving (NV21) and preserves exact raw pixels.
     * Replace with a profiled GPU/libyuv path only AFTER same-frame validation.
     */
    private fun yuv420ToBitmap(image: Image): Bitmap {
        val width = image.width
        val height = image.height
        val planes = image.planes
        val y = planes[0]; val u = planes[1]; val v = planes[2]
        val yb = y.buffer; val ub = u.buffer; val vb = v.buffer
        val pixels = IntArray(width * height)
        for (row in 0 until height) {
            for (col in 0 until width) {
                val yy = yb.get(row * y.rowStride + col * y.pixelStride).toInt() and 255
                val uvRow = row / 2
                val uvCol = col / 2
                val uu = (ub.get(uvRow * u.rowStride + uvCol * u.pixelStride).toInt() and 255) - 128
                val vv = (vb.get(uvRow * v.rowStride + uvCol * v.pixelStride).toInt() and 255) - 128
                val c = (yy - 16).coerceAtLeast(0)
                val r = ((298 * c + 409 * vv + 128) shr 8).coerceIn(0, 255)
                val g = ((298 * c - 100 * uu - 208 * vv + 128) shr 8).coerceIn(0, 255)
                val b = ((298 * c + 516 * uu + 128) shr 8).coerceIn(0, 255)
                pixels[row * width + col] = (255 shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
