package zx.nextcore.ui.hud

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import zx.nextcore.R
import zx.nextcore.ui.navigation.safePopBackStack
import zx.nextcore.ui.theme.ColorMode
import zx.nextcore.ui.theme.NEXTCORE_SEED_COLOR

/** Accent presets; 0 means "take it from the wallpaper" (Android 12+). */
private val ACCENTS = listOf(
    NEXTCORE_SEED_COLOR, 0xFFFF3D5A.toInt(), 0xFFF472B6.toInt(), 0xFFA78BFA.toInt(), 0xFF3B82F6.toInt(),
    0xFF22D3EE.toInt(), 0xFF34D399.toInt(), 0xFFA3E635.toInt(), 0xFFFACC15.toInt(), 0xFFE5E7EB.toInt(),
)

private val MODES = listOf(ColorMode.SYSTEM, ColorMode.LIGHT, ColorMode.DARK, ColorMode.DARKAMOLED)

/**
 * Theme: light, dark, system or AMOLED, and the accent color. Writes the same
 * "settings" keys NextCoreTheme listens to, so the whole app switches at once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HudThemeScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var mode by remember { mutableIntStateOf(prefs.getInt("color_mode", ColorMode.DARK.value)) }
    var key by remember { mutableIntStateOf(prefs.getInt("key_color", NEXTCORE_SEED_COLOR)) }
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    HudPage(title = stringResource(R.string.hud_theme), onBack = { navController.safePopBackStack() }) {
        item(key = "preview") { ThemePreview() }

        item(key = "h_mode") { HudSectionTitle(stringResource(R.string.th_mode)) }
        item(key = "mode") {
            HudSegmented(
                options = listOf(
                    stringResource(R.string.th_system),
                    stringResource(R.string.th_light),
                    stringResource(R.string.th_dark),
                    stringResource(R.string.th_amoled),
                ),
                selected = MODES.indexOfFirst { it.value == mode }.coerceAtLeast(0),
                onSelect = {
                    mode = MODES[it].value
                    prefs.edit().putInt("color_mode", mode).apply()
                },
                height = 40.dp,
                fontSize = 13.sp,
            )
        }
        item(key = "mode_note") { HudNote(stringResource(R.string.th_mode_note)) }

        item(key = "h_accent") { HudSectionTitle(stringResource(R.string.th_accent)) }
        item(key = "accent") {
            HudCard(Modifier.fillMaxWidth()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (dynamicAvailable) {
                        Swatch(color = null, selected = key == 0) {
                            key = 0
                            prefs.edit().putInt("key_color", 0).apply()
                        }
                    }
                    ACCENTS.forEach { c ->
                        Swatch(color = Color(c), selected = key == c) {
                            key = c
                            prefs.edit().putInt("key_color", c).apply()
                        }
                    }
                }
                if (dynamicAvailable) {
                    Text(
                        stringResource(R.string.th_wallpaper),
                        color = Hud.muted, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

/** A round color choice; null is the wallpaper (dynamic) choice. */
@Composable
private fun Swatch(color: Color?, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color ?: Hud.cardHi)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) Hud.text else Hud.line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(
                Icons.Rounded.Check, null,
                tint = if (color != null && color.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(20.dp),
            )
            color == null -> Icon(Icons.Rounded.Wallpaper, null, tint = Hud.muted, modifier = Modifier.size(20.dp))
        }
    }
}

/** A small piece of the app in the current theme: a gauge, a switch and the mode selector. */
@Composable
private fun ThemePreview() {
    HudCard(Modifier.fillMaxWidth(), accent = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            HudGauge(0.72f, "2.7", "CPU GHz", Modifier.weight(1f))
            Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.hud_lite), color = Hud.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    HudSwitch(true, {})
                }
                HudBar(0.6f)
                Text(stringResource(R.string.th_preview), color = Hud.muted, fontSize = 11.sp)
            }
        }
    }
}
