package zx.nextcore.ui.hud

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

/** Fixed HUD palette: black surfaces, light text, one accent from the theme. */
object Hud {
    val bg = Color(0xFF0A0A0D)
    val bgAmoled = Color(0xFF000000)
    val card = Color(0xFF15151A)
    val cardHi = Color(0xFF1D1D24)
    val line = Color(0xFF26262E)
    val text = Color(0xFFF2F2F4)
    val muted = Color(0xFF8B8B96)
    val green = Color(0xFF34D17A)
    val yellow = Color(0xFFFFC23D)
    val red = Color(0xFFFF3B5C)

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

/** Dark color scheme for the whole app, so older screens match the HUD pages. */
fun hudColorScheme(accent: Color, amoled: Boolean): ColorScheme {
    val bg = if (amoled) Hud.bgAmoled else Hud.bg
    fun tint(alpha: Float) = accent.copy(alpha = alpha).compositeOver(Hud.card)
    return darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF140A05),
        primaryContainer = tint(0.22f),
        onPrimaryContainer = Color.White,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = Color(0xFF140A05),
        secondaryContainer = tint(0.16f),
        onSecondaryContainer = Hud.text,
        tertiary = Hud.red,
        onTertiary = Color.White,
        tertiaryContainer = Hud.red.copy(alpha = 0.2f).compositeOver(Hud.card),
        onTertiaryContainer = Hud.text,
        background = bg,
        onBackground = Hud.text,
        surface = bg,
        onSurface = Hud.text,
        surfaceVariant = Hud.cardHi,
        onSurfaceVariant = Hud.muted,
        surfaceTint = accent,
        inverseSurface = Hud.text,
        inverseOnSurface = bg,
        error = Color(0xFFFF5A5F),
        onError = Color.White,
        errorContainer = Color(0xFF3A1416),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF34343E),
        outlineVariant = Hud.line,
        scrim = Color.Black,
        surfaceBright = Hud.cardHi,
        surfaceDim = bg,
        surfaceContainerLowest = bg,
        surfaceContainerLow = Color(0xFF111115),
        surfaceContainer = Hud.card,
        surfaceContainerHigh = Hud.cardHi,
        surfaceContainerHighest = Color(0xFF24242C),
    )
}
