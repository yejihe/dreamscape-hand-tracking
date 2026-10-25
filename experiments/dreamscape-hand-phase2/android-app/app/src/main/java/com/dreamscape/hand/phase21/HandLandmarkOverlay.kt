package com.dreamscape.hand.phase21

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * User-facing calibration overlay. Already transformed by ARCore from
 * IMAGE_NORMALIZED into VIEW pixel coordinates (NOT a world-space overlay).
 */
internal class HandLandmarkOverlay(context: Context) : View(context) {
    private val bonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN; strokeWidth = 5f; style = Paint.Style.STROKE
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN; style = Paint.Style.FILL
    }
    private val indexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.MAGENTA; style = Paint.Style.FILL
    }
    private val edges = intArrayOf(0,1, 1,2, 2,3, 3,4, 0,5, 5,6, 6,7, 7,8,
        5,9, 9,10, 10,11, 11,12, 9,13, 13,14, 14,15, 15,16,
        13,17, 17,18, 18,19, 19,20, 0,17)
    private var points: FloatArray? = null

    fun setPoints(viewPoints: FloatArray?) {
        // A UI-thread-only copy: ARCore GL thread must not race Canvas drawing.
        points = viewPoints?.copyOf()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = points ?: return
        if (p.size != 42) return
        for (i in edges.indices step 2) {
            val a = edges[i] * 2
            val b = edges[i + 1] * 2
            if (p[a].isFinite() && p[a+1].isFinite() &&
                p[b].isFinite() && p[b+1].isFinite())
                canvas.drawLine(p[a], p[a + 1], p[b], p[b + 1], bonePaint)
        }
        for (i in 0 until 21) {
            val x = p[2 * i]; val y = p[2 * i + 1]
            if (x.isFinite() && y.isFinite())
                canvas.drawCircle(x, y, if (i == 8) 17f else 7f,
                    if (i == 8) indexPaint else dotPaint)
        }
    }
}
