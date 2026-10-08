package zx.nextcore.ui.hud

import androidx.compose.material3.lightColorScheme

import androidx.compose.ui.graphics.luminance

import androidx.compose.runtime.setValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.getValue

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp

/** One set of HUD surface and text colors. */
data class HudPalette(
    val bg: Color,
    val card: Color,
    val cardHi: Color,
    val line: Color,
    val track: Color,
    val nav: Color,
    val text: Color,
    val muted: Color,
    val green: Color,
    val yellow: Color,
    val red: Color,
    val isLight: Boolean = false,
) {
    companion object {
        val Dark = HudPalette(
            bg = Color(0xFF0A0A0D), card = Color(0xFF15151A), cardHi = Color(0xFF1D1D24),
            line = Color(0xFF26262E), track = Color(0xFF2B2B34), nav = Color(0xFF0E0E12),
            text = Color(0xFFF2F2F4), muted = Color(0xFF8B8B96),
            green = Color(0xFF34D17A), yellow = Color(0xFFFFC23D), red = Color(0xFFFF3B5C),
        )
        /** Dark with true black behind everything, for OLED screens. */
        val Amoled = Dark.copy(bg = Color.Black, card = Color(0xFF0F0F12), cardHi = Color(0xFF17171C), nav = Color.Black)
        val Light = HudPalette(
            bg = Color(0xFFF3F3F6), card = Color(0xFFFFFFFF), cardHi = Color(0xFFECECF1),
            line = Color(0xFFDCDCE3), track = Color(0xFFD3D3DB), nav = Color(0xFFFFFFFF),
            text = Color(0xFF16161B), muted = Color(0xFF6A6A76),
            green = Color(0xFF1E9E57), yellow = Color(0xFFC08400), red = Color(0xFFE0284A),
            isLight = true,
        )
    }
}

/**
 * HUD colors. The palette follows the theme mode (dark, light, AMOLED); it is
 * set by NextCoreTheme and read everywhere through these getters, so a mode
 * switch recomposes every HUD page.
 */
object Hud {
    var palette by mutableStateOf(HudPalette.Dark)

    val bg get() = palette.bg
    val card get() = palette.card
    val cardHi get() = palette.cardHi
    val line get() = palette.line
    val track get() = palette.track
    val nav get() = palette.nav
    val text get() = palette.text
    val muted get() = palette.muted
    val green get() = palette.green
    val yellow get() = palette.yellow
    val red get() = palette.red

    /** Cards are square except one cut corner at the bottom on the end side. */
    val cardShape = CutCornerShape(bottomEnd = 14.dp)
    val smallCut = CutCornerShape(bottomEnd = 8.dp)
    val tileShape = RoundedCornerShape(10.dp)
}

/** The theme accent (user key color, orange by default). */
val hudAccent: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

/** Accent to a warmer red, for the selected segment, sliders and the logo. */
val hudAccentBrush: Brush
    @Composable @ReadOnlyComposable get() = Brush.horizontalGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
    )

/** Color scheme for the whole app from the HUD [palette], so older screens match the HUD pages. */
fun hudColorScheme(accent: Color, palette: HudPalette): ColorScheme {
    val p = palette
    fun tint(alpha: Float) = accent.copy(alpha = alpha).compositeOver(p.card)
    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF140A05) else Color.White
    val base = if (p.isLight) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = tint(0.22f),
        onPrimaryContainer = p.text,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = tint(0.16f),
        onSecondaryContainer = p.text,
        tertiary = p.red,
        onTertiary = Color.White,
        tertiaryContainer = p.red.copy(alpha = 0.2f).compositeOver(p.card),
        onTertiaryContainer = p.text,
        background = p.bg,
        onBackground = p.text,
        surface = p.bg,
        onSurface = p.text,
        surfaceVariant = p.cardHi,
        onSurfaceVariant = p.muted,
        surfaceTint = accent,
        inverseSurface = p.text,
        inverseOnSurface = p.bg,
        error = p.red,
        onError = Color.White,
        errorContainer = p.red.copy(alpha = 0.18f).compositeOver(p.card),
        onErrorContainer = p.text,
        outline = p.track,
        outlineVariant = p.line,
        scrim = Color.Black,
        surfaceBright = p.cardHi,
        surfaceDim = p.bg,
        surfaceContainerLowest = p.bg,
        surfaceContainerLow = p.card.copy(alpha = 0.6f).compositeOver(p.bg),
        surfaceContainer = p.card,
        surfaceContainerHigh = p.cardHi,
        surfaceContainerHighest = p.track,
    )
}
