package zx.nextcore.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BorderOuter
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zx.nextcore.R
import zx.nextcore.overlay.OverlayMetric
import zx.nextcore.overlay.OverlayMode
import zx.nextcore.overlay.OverlayPanelView
import zx.nextcore.overlay.OverlayPrefs
import zx.nextcore.overlay.OverlayService
import zx.nextcore.overlay.OverlayState
import zx.nextcore.ui.navigation.safePopBackStack
import java.util.Locale
import kotlin.math.roundToInt

/** Floating monitor settings: what it shows, in which order, and how it looks, with a live preview. */
@Composable
fun HudMonitorScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { OverlayPrefs.prefs(context) }
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(prefs.getBoolean(OverlayPrefs.ENABLED, false) && OverlayService.canDraw(context)) }
    var awaitingPermission by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val order = remember { mutableStateListOf<OverlayMetric>().apply { addAll(OverlayPrefs.order(prefs)) } }
    var shown by remember { mutableStateOf(OverlayPrefs.shown(prefs)) }
    var mode by remember { mutableStateOf(OverlayPrefs.mode(prefs)) }
    var scale by remember { mutableFloatStateOf(OverlayPrefs.scale(prefs)) }
    var opacity by remember { mutableFloatStateOf(prefs.getInt(OverlayPrefs.OPACITY, 60).toFloat()) }
    var interval by remember { mutableLongStateOf(OverlayPrefs.interval(prefs)) }
    var accentIdx by remember { mutableIntStateOf(prefs.getInt(OverlayPrefs.ACCENT, 0)) }
    var colorCode by remember { mutableStateOf(prefs.getBoolean(OverlayPrefs.COLOR_CODE, true)) }
    var border by remember { mutableStateOf(prefs.getBoolean(OverlayPrefs.BORDER, false)) }
    var graph by remember { mutableStateOf(prefs.getBoolean(OverlayPrefs.GRAPH, false)) }
    var gamesOnly by remember { mutableStateOf(prefs.getBoolean(OverlayPrefs.GAMES_ONLY, false)) }

    fun setEnabled(on: Boolean) {
        enabled = on
        prefs.edit().putBoolean(OverlayPrefs.ENABLED, on).apply()
        if (on) OverlayService.start(context) else OverlayService.stop(context)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (awaitingPermission) {
            awaitingPermission = false
            if (OverlayService.canDraw(context)) setEnabled(true)
        }
    }

    fun move(m: OverlayMetric, by: Int) {
        val i = order.indexOf(m)
        val j = i + by
        if (i < 0 || j !in order.indices) return
        order.removeAt(i)
        order.add(j, m)
        OverlayPrefs.saveOrder(prefs, order)
    }

    val permissionMsg = stringResource(R.string.nc_overlay_permission_needed)
    val visible = order.filter { it in shown }
    val accent = Color(OverlayPrefs.accents.getOrElse(accentIdx) { OverlayPrefs.accents[0] })

    HudPage(title = stringResource(R.string.nc_overlay_title), onBack = { navController.safePopBackStack() }) {
        item(key = "preview") {
            Box(
                Modifier.fillMaxWidth().height(170.dp).clip(Hud.cardShape).background(Color(0xFF1B2430)),
                contentAlignment = Alignment.Center,
            ) {
                // A stand-in "game" behind the panel so opacity reads correctly.
                Canvas(Modifier.fillMaxSize()) {
                    var x = -size.height
                    while (x < size.width) {
                        drawLine(Color(0x2234D399), Offset(x, size.height), Offset(x + size.height, 0f), 28f)
                        x += 70f
                    }
                }
                AndroidView(
                    factory = { OverlayPanelView(it) },
                    update = { v ->
                        v.configure(visible, mode, scale, opacity.roundToInt(), OverlayPrefs.accents.getOrElse(accentIdx) { OverlayPrefs.accents[0] }, colorCode, border, graph)
                        v.state = OverlayState.preview
                    },
                )
            }
        }
        item(key = "hint") { HudNote(stringResource(R.string.ov_hint)) }
        message?.let { m -> item(key = "msg") { HudNote(m) } }

        item(key = "main") {
            HudGroup(rows = listOf<@Composable () -> Unit>(
                {
                    HudRow(stringResource(R.string.nc_overlay_enable), icon = Icons.Rounded.PictureInPicture, subtitle = stringResource(R.string.nc_overlay_enable_desc), trailing = {
                        HudSwitch(enabled, { on ->
                            if (!on) setEnabled(false) else scope.launch {
                                val granted = withContext(Dispatchers.IO) { OverlayService.grantWithRoot(context) }
                                if (granted) setEnabled(true) else {
                                    awaitingPermission = true
                                    message = permissionMsg
                                    runCatching { context.startActivity(OverlayService.permissionIntent(context)) }
                                }
                            }
                        })
                    })
                },
                {
                    HudRow(stringResource(R.string.nc_overlay_games_only), icon = Icons.Rounded.SportsEsports, subtitle = stringResource(R.string.nc_overlay_games_only_desc), trailing = {
                        HudSwitch(gamesOnly, { gamesOnly = it; prefs.edit().putBoolean(OverlayPrefs.GAMES_ONLY, it).apply() })
                    })
                },
            ))
        }

        item(key = "h_mode") { HudSectionTitle(stringResource(R.string.ov_layout)) }
        item(key = "mode") {
            HudSegmented(
                options = OverlayMode.entries.map { stringResource(it.title) },
                selected = mode.ordinal,
                onSelect = { mode = OverlayMode.entries[it]; prefs.edit().putString(OverlayPrefs.MODE, mode.key).apply() },
                height = 40.dp,
                fontSize = 13.sp,
            )
        }

        item(key = "h_refresh") { HudSectionTitle(stringResource(R.string.nc_overlay_refresh)) }
        item(key = "refresh") {
            HudSegmented(
                options = OverlayPrefs.intervalsMs.map { if (it < 1000) "${it}ms" else String.format(Locale.US, "%.0fs", it / 1000f) },
                selected = OverlayPrefs.intervalsMs.indexOf(interval).coerceAtLeast(0),
                onSelect = { interval = OverlayPrefs.intervalsMs[it]; prefs.edit().putLong(OverlayPrefs.INTERVAL_MS, interval).apply() },
                height = 40.dp,
                fontSize = 13.sp,
            )
        }

        item(key = "h_metrics") { HudSectionTitle(stringResource(R.string.ov_metrics, visible.size)) }
        item(key = "metrics") {
            HudCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                order.forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider(color = Hud.line)
                    val on = m in shown
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            m.label,
                            color = if (on) accent else Hud.muted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.size(width = 44.dp, height = 18.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(m.title), color = if (on) Hud.text else Hud.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(m.desc), color = Hud.muted, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                        SmallArrow(Icons.Rounded.KeyboardArrowUp, i > 0) { move(m, -1) }
                        SmallArrow(Icons.Rounded.KeyboardArrowDown, i < order.lastIndex) { move(m, 1) }
                        Box(Modifier.padding(start = 6.dp)) {
                            HudSwitch(on, {
                                shown = if (it) shown + m else shown - m
                                OverlayPrefs.saveShown(prefs, shown)
                            })
                        }
                    }
                }
            }
        }

        item(key = "h_look") { HudSectionTitle(stringResource(R.string.nc_overlay_look)) }
        item(key = "look") {
            HudCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ov_size, (scale * 100).roundToInt()), color = Hud.muted, fontSize = 12.sp)
                HudSlider(scale, { scale = it }, steps = 10, range = 0.7f..1.8f, onFinished = { prefs.edit().putFloat(OverlayPrefs.SCALE, scale).apply() })
                Text(stringResource(R.string.ov_opacity, opacity.roundToInt()), color = Hud.muted, fontSize = 12.sp)
                HudSlider(opacity, { opacity = it }, steps = 9, range = 0f..100f, onFinished = { prefs.edit().putInt(OverlayPrefs.OPACITY, opacity.roundToInt()).apply() })
                Text(stringResource(R.string.ov_accent), color = Hud.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OverlayPrefs.accents.forEachIndexed { i, c ->
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .then(if (i == accentIdx) Modifier.border(2.dp, Hud.text, CircleShape) else Modifier)
                                .clickable { accentIdx = i; prefs.edit().putInt(OverlayPrefs.ACCENT, i).apply() },
                        )
                    }
                }
            }
        }
        item(key = "look_switches") {
            HudGroup(rows = listOf<@Composable () -> Unit>(
                { HudRow(stringResource(R.string.ov_graph), icon = Icons.AutoMirrored.Rounded.ShowChart, subtitle = stringResource(R.string.ov_graph_sub), trailing = { HudSwitch(graph, { graph = it; prefs.edit().putBoolean(OverlayPrefs.GRAPH, it).apply() }) }) },
                { HudRow(stringResource(R.string.ov_color_code), icon = Icons.Rounded.Palette, subtitle = stringResource(R.string.ov_color_code_sub), trailing = { HudSwitch(colorCode, { colorCode = it; prefs.edit().putBoolean(OverlayPrefs.COLOR_CODE, it).apply() }) }) },
                { HudRow(stringResource(R.string.ov_border), icon = Icons.Rounded.BorderOuter, trailing = { HudSwitch(border, { border = it; prefs.edit().putBoolean(OverlayPrefs.BORDER, it).apply() }) }) },
            ))
        }
        item(key = "fps_note") { HudNote(stringResource(R.string.ov_fps_note)) }
    }
}

@Composable
private fun SmallArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(Hud.smallCut).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) Hud.text else Hud.line, modifier = Modifier.size(20.dp))
    }
}
