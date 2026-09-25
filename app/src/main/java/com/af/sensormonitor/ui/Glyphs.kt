package com.af.sensormonitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.af.sensormonitor.R
import com.af.sensormonitor.sensor.Status
import kotlin.math.min

/** The activity indicator: filled and bright while reacting, fading back to an outline. */
fun DrawScope.drawIndicator(status: Status, level: Float, p: Palette, center: Offset, radius: Float) {
    val stroke = Stroke(width = radius * 0.28f)
    when (status) {
        Status.LIVE -> {
            drawCircle(p.faint, radius, center, style = stroke)
            if (level > 0f) {
                drawCircle(p.active.copy(alpha = 0.25f + 0.75f * level), radius * (0.75f + 0.35f * level), center)
            }
        }
        Status.ARMED -> {
            val path = Path().apply {
                moveTo(center.x, center.y - radius); lineTo(center.x + radius, center.y)
                lineTo(center.x, center.y + radius); lineTo(center.x - radius, center.y); close()
            }
            if (level > 0f) drawPath(path, p.active.copy(alpha = 0.25f + 0.75f * level))
            drawPath(path, p.dim, style = stroke)
        }
        Status.WAITING -> drawLine(p.dim, center - Offset(radius * 0.7f, 0f), center + Offset(radius * 0.7f, 0f),
            strokeWidth = radius * 0.3f, cap = StrokeCap.Round)
        Status.LOCKED -> {
            val w = radius * 1.5f
            val h = radius * 1.1f
            drawRoundRect(p.warn, Offset(center.x - w / 2, center.y - h * 0.2f), Size(w, h), CornerRadius(radius * 0.2f))
            drawArc(p.warn, 180f, 180f, false, Offset(center.x - w * 0.33f, center.y - radius * 0.95f),
                Size(w * 0.66f, radius * 1.4f), style = Stroke(radius * 0.25f))
        }
        Status.FAILED -> {
            val r = radius * 0.7f
            drawLine(p.dim, center + Offset(-r, -r), center + Offset(r, r), radius * 0.28f, StrokeCap.Round)
            drawLine(p.dim, center + Offset(-r, r), center + Offset(r, -r), radius * 0.28f, StrokeCap.Round)
        }
        Status.OFF -> drawCircle(p.faint, radius * 0.35f, center)
    }
}

@Composable
fun GearIcon(size: Dp, color: Color, holeColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val c = center
        val r = min(this.size.width, this.size.height) / 2f
        for (i in 0 until 8) {
            rotate(i * 45f, c) {
                drawRoundRect(color, Offset(c.x - r * 0.16f, c.y - r * 0.92f), Size(r * 0.32f, r * 0.4f), CornerRadius(r * 0.06f))
            }
        }
        drawCircle(color, r * 0.62f, c)
        drawCircle(holeColor, r * 0.26f, c)
    }
}

@Composable
fun BackIcon(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.62f, h * 0.2f); lineTo(w * 0.32f, h * 0.5f); lineTo(w * 0.62f, h * 0.8f)
        }
        drawPath(path, color, style = Stroke(width = w * 0.11f, cap = StrokeCap.Round))
    }
}

/**
 * The mascot (Kuro-san, user-provided artwork): dimmed while the sensors are quiet, surprised when many sensors
 * react at once. It stays small and outside the gauge rows so it never covers a value.
 */
@Composable
fun MascotBadge(size: Dp, excited: Boolean, modifier: Modifier = Modifier, quiet: Boolean = !excited) {
    val id = when {
        excited -> R.drawable.kuro_reacting
        quiet -> R.drawable.kuro_quiet
        else -> R.drawable.kuro_normal
    }
    Image(painterResource(id), contentDescription = null, modifier = modifier.size(size))
}
