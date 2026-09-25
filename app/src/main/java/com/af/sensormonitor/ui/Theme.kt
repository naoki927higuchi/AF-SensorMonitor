package com.af.sensormonitor.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colors of the gauge panel. Follows the system light / dark setting. */
@Immutable
data class Palette(
    val background: Color,
    val stripe: Color,
    val text: Color,
    val dim: Color,
    val faint: Color,
    val active: Color,
    val warn: Color,
    val channels: List<Color>,
)

private val DarkPalette = Palette(
    background = Color(0xFF0D1012),
    stripe = Color(0xFF151A1D),
    text = Color(0xFFE7EAEC),
    dim = Color(0xFF8C959C),
    faint = Color(0xFF3A4247),
    active = Color(0xFF3EE089),
    warn = Color(0xFFFFB454),
    channels = listOf(
        Color(0xFFFF6B6B), Color(0xFF5FD86F), Color(0xFF5AA9FF), Color(0xFFF5C542),
        Color(0xFFC77DFF), Color(0xFF4DD0E1), Color(0xFFFF9F43), Color(0xFFB0B8BF),
    ),
)

private val LightPalette = Palette(
    background = Color(0xFFFBFBF8),
    stripe = Color(0xFFEFF1EC),
    text = Color(0xFF14181A),
    dim = Color(0xFF5B636A),
    faint = Color(0xFFC3C9CD),
    active = Color(0xFF0B9A4E),
    warn = Color(0xFFB86300),
    channels = listOf(
        Color(0xFFD63A3A), Color(0xFF238B32), Color(0xFF1E6FD6), Color(0xFFB08400),
        Color(0xFF8B3FC4), Color(0xFF00838F), Color(0xFFD9640A), Color(0xFF5E666C),
    ),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.active, background = palette.background, surface = palette.background,
            onBackground = palette.text, onSurface = palette.text, onSurfaceVariant = palette.dim,
        )
    } else {
        lightColorScheme(
            primary = palette.active, background = palette.background, surface = palette.background,
            onBackground = palette.text, onSurface = palette.text, onSurfaceVariant = palette.dim,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
