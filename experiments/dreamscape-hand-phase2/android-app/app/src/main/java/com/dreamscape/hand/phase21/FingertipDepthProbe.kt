package com.dreamscape.hand.phase21

import android.media.Image
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.exceptions.NotYetAvailableException
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Phase 2.2 measurement-only probe.
 * Samples ARCore automatic scene depth at the projected index fingertip pixel.
 *
 * IMPORTANT: scene depth is NOT verified finger-surface depth. At slender,
 * moving fingers it can be stale background depth or hole-filled estimates.
 * Do NOT interpret this value as validated fingertip world XYZ.
 */
internal object FingertipDepthProbe {
    data class Reading(
        val summary: String,
        val medianMillimetres: Int? = null,
        val centerMillimetres: Int? = null,
        val validPixelCount: Int = 0,
        val x: Int? = null,
        val y: Int? = null
    )

    fun sample(frame: Frame, nx: Float, ny: Float): Reading {
        if (!nx.isFinite() || !ny.isFinite() || nx !in 0f..1f || ny !in 0f..1f)
            return Reading("未取得有效指尖位置")
        val uv = FloatArray(2)
        frame.transformCoordinates2d(
            Coordinates2d.IMAGE_NORMALIZED, floatArrayOf(nx, ny),
            Coordinates2d.TEXTURE_NORMALIZED, uv)
        if (!uv[0].isFinite() || !uv[1].isFinite() ||
            uv[0] !in 0f..1f || uv[1] !in 0f..1f)
            return Reading("指尖落在深度图裁剪范围外")
        return try {
            frame.acquireDepthImage16Bits().use { img ->
                readPatch(img, uv[0], uv[1])
            }
        } catch (e: NotYetAvailableException) {
            Reading("深度图暂不可用（ARCore尚未准备好）")
        } catch (e: Exception) {
            Reading("深度查询失败：${e.javaClass.simpleName}")
        }
    }

    private fun readPatch(image: Image, u: Float, v: Float): Reading {
        if (image.width <= 0 || image.height <= 0 || image.planes.isEmpty())
            return Reading("深度图无有效尺寸")
        val px = (u * image.width).toInt().coerceIn(0, image.width - 1)
        val py = (v * image.height).toInt().coerceIn(0, image.height - 1)
        val plane = image.planes[0]
        val bytes = plane.buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        fun getDepth(x: Int, y: Int): Int? {
            val offset = y * plane.rowStride + x * plane.pixelStride
            if (offset < 0 || offset + 2 > bytes.limit()) return null
            val mm = bytes.getShort(offset).toInt() and 0xFFFF
            return mm.takeIf { it in 1..20000 }
        }
        val center = getDepth(px, py)
        val values = ArrayList<Int>(9)
        for (y in (py - 1).coerceAtLeast(0)..(py + 1).coerceAtMost(image.height - 1)) {
            for (x in (px - 1).coerceAtLeast(0)..(px + 1).coerceAtMost(image.width - 1)) {
                getDepth(x, y)?.let { values.add(it) }
            }
        }
        values.sort()
        if (values.isEmpty())
            return Reading("该点附近无有效场景深度（${px},${py}）", null, center, 0, px, py)
        val med = values[values.size / 2]
        val summary = "候选场景深度：${med}mm｜中心${center ?: 0}mm｜有效${values.size}/9｜深度图${image.width}×${image.height}"
        return Reading(summary, med, center, values.size, px, py)
    }
}
