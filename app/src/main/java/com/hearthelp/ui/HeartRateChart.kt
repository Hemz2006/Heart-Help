package com.hearthelp.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import com.hearthelp.domain.HistorySeries
import com.hearthelp.domain.MinuteSample
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Draws the heart-rate graph onto an Android [Canvas]. The same code renders the on-screen graph
 * and the PNG that is shared, so they always look the same.
 */
object HeartRateChart {
    fun draw(
        canvas: Canvas, width: Float, height: Float, density: Float,
        points: List<MinuteSample>, from: Long, to: Long, lowLimit: Int, highLimit: Int,
        dark: Boolean = false,
    ) {
        // Dark matches the app; light is used for the shared image so it prints well.
        val bg = if (dark) Color.rgb(20, 27, 38) else Color.WHITE
        val ink = if (dark) Color.rgb(138, 151, 170) else Color.DKGRAY
        val gridColor = if (dark) Color.rgb(36, 46, 62) else Color.rgb(230, 230, 230)
        val accent = if (dark) Color.rgb(240, 104, 122) else Color.rgb(211, 47, 47)
        val limitColor = if (dark) Color.rgb(138, 151, 170) else Color.rgb(198, 40, 40)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 11 * density }
        val left = 34 * density
        val right = width - 8 * density
        val top = 8 * density
        val bottom = height - 20 * density
        // drawRect, not drawColor: on screen the canvas isn't clipped, and drawColor would paint the whole window.
        canvas.drawRect(0f, 0f, width, height, Paint().apply { color = bg })

        val lo = (minOf(lowLimit, points.minOfOrNull { it.min } ?: lowLimit) - 10).coerceAtLeast(0)
        val hi = maxOf(highLimit, points.maxOfOrNull { it.max } ?: highLimit) + 10
        fun x(t: Long) = left + (right - left) * ((t - from).toFloat() / (to - from).coerceAtLeast(1))
        fun y(bpm: Int) = bottom - (bottom - top) * ((bpm - lo).toFloat() / (hi - lo))

        // Grid and y labels
        val grid = Paint().apply { color = gridColor; strokeWidth = density }
        for (i in 0..4) {
            val bpm = lo + (hi - lo) * i / 4
            canvas.drawLine(left, y(bpm), right, y(bpm), grid)
            canvas.drawText("$bpm", 2 * density, y(bpm) + 4 * density, text)
        }
        // X labels
        val fmt = SimpleDateFormat(if (to - from <= 86_400_000L) "HH:mm" else "d MMM", Locale.getDefault())
        for (i in 0..3) {
            val t = from + (to - from) * i / 3
            val label = fmt.format(Date(t))
            val tx = (x(t) - text.measureText(label) / 2).coerceIn(left, right - text.measureText(label))
            canvas.drawText(label, tx, height - 4 * density, text)
        }
        // Limit lines
        val limit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = limitColor; strokeWidth = density
            pathEffect = DashPathEffect(floatArrayOf(6 * density, 4 * density), 0f)
        }
        canvas.drawLine(left, y(highLimit), right, y(highLimit), limit)
        canvas.drawLine(left, y(lowLimit), right, y(lowLimit), limit)

        // Min–max band and average line, broken at gaps in the data
        val step = if (points.size > 1) (points[1].minuteStart - points[0].minuteStart).coerceAtLeast(60_000) else 60_000
        val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; alpha = if (dark) 50 else 60; style = Paint.Style.FILL }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent; strokeWidth = 2 * density; style = Paint.Style.STROKE
        }
        for (seg in HistorySeries.segments(points, maxGapMs = step * 3)) {
            if (seg.size == 1) {
                val p = seg[0]
                canvas.drawCircle(x(p.minuteStart), y(p.avg), 2 * density, line)
                continue
            }
            val bandPath = Path().apply {
                moveTo(x(seg[0].minuteStart), y(seg[0].max))
                seg.drop(1).forEach { lineTo(x(it.minuteStart), y(it.max)) }
                seg.asReversed().forEach { lineTo(x(it.minuteStart), y(it.min)) }
                close()
            }
            canvas.drawPath(bandPath, band)
            val avgPath = Path().apply {
                moveTo(x(seg[0].minuteStart), y(seg[0].avg))
                seg.drop(1).forEach { lineTo(x(it.minuteStart), y(it.avg)) }
            }
            canvas.drawPath(avgPath, line)
        }
    }
}
