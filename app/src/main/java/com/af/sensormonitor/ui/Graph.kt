package com.af.sensormonitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.af.sensormonitor.sensor.HistorySnapshot
import com.af.sensormonitor.sensor.ValueFormat
import kotlin.math.abs
import kotlin.math.max

enum class GraphStyle {
    /** Continuous signal: straight lines between samples. */
    LINE,
    /** On-change signal: the value holds until the next event. */
    STEP,
    /** Events: a vertical mark per event. */
    MARKS,
}

/** Y range that expands at once and shrinks slowly, so the axis does not jump around. */
class AutoRange {
    var lo = Float.NaN
    var hi = Float.NaN

    fun update(dataLo: Float, dataHi: Float) {
        var l = dataLo
        var h = dataHi
        if (!(l <= h)) return
        val span = h - l
        val pad = if (span > 0f) span * 0.08f else max(abs(h) * 0.05f, 0.5f)
        l -= pad; h += pad
        if (lo.isNaN() || l < lo) lo = l else lo += (l - lo) * SHRINK
        if (hi.isNaN() || h > hi) hi = h else hi += (h - hi) * SHRINK
    }

    companion object {
        const val SHRINK = 0.03f
    }
}

@Composable
fun SensorGraph(
    data: HistorySnapshot,
    endNanos: Long,
    windowNanos: Long,
    channels: Int,
    style: GraphStyle,
    decimals: Int,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val range = remember { AutoRange() }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = p.dim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val start = endNanos - windowNanos
        fun x(t: Long) = w * ((t - start).toFloat() / windowNanos)

        // Time grid every 5 s.
        for (s in 0..(windowNanos / 1_000_000_000L).toInt() step 5) {
            val gx = w - w * (s * 1e9f / windowNanos)
            drawLine(p.faint.copy(alpha = 0.5f), Offset(gx, 0f), Offset(gx, h), 1f)
            // No label at the left edge, where the minimum value is printed.
            if (s > 0 && gx > w * 0.15f) drawText(measurer, "-${s}s", Offset(gx + 3f, h - 14.sp.toPx()), labelStyle)
        }

        val n = data.size
        val ch = minOf(channels, data.channels)
        if (style == GraphStyle.MARKS) {
            for (i in 0 until n) {
                if (data.times[i] < start) continue
                val gx = x(data.times[i])
                drawLine(p.channels[0], Offset(gx, h * 0.15f), Offset(gx, h * 0.85f), 3f)
            }
            return@Canvas
        }
        if (n == 0 || ch == 0) return@Canvas

        var lo = Float.POSITIVE_INFINITY
        var hi = Float.NEGATIVE_INFINITY
        for (i in 0 until n) for (c in 0 until ch) {
            val v = data.value(i, c)
            if (v.isFinite()) { if (v < lo) lo = v; if (v > hi) hi = v }
        }
        range.update(lo, hi)
        val rLo = range.lo
        val rHi = range.hi
        if (rLo.isNaN() || rHi.isNaN() || rHi <= rLo) return@Canvas
        fun y(v: Float) = h - h * ((v - rLo) / (rHi - rLo))

        if (rLo < 0f && rHi > 0f) drawLine(p.faint, Offset(0f, y(0f)), Offset(w, y(0f)), 1.5f)
        drawText(measurer, ValueFormat.number(rHi, decimals, 9, plus = false), Offset(4f, 2f), labelStyle)
        drawText(measurer, ValueFormat.number(rLo, decimals, 9, plus = false), Offset(4f, h - 14.sp.toPx()), labelStyle)

        val stroke = Stroke(width = 2.2f)
        for (c in 0 until ch) {
            val path = Path()
            var started = false
            var lastY = 0f
            for (i in 0 until n) {
                val v = data.value(i, c)
                if (!v.isFinite()) continue
                val px = x(data.times[i])
                val py = y(v)
                if (!started) { path.moveTo(px, py); started = true }
                else {
                    if (style == GraphStyle.STEP) path.lineTo(px, lastY)
                    path.lineTo(px, py)
                }
                lastY = py
            }
            if (!started) continue
            // Hold the last value up to "now" (sensors that report only on change are silent while steady).
            path.lineTo(w, lastY)
            drawPath(path, p.channels[c % p.channels.size], style = stroke)
        }
    }
}
