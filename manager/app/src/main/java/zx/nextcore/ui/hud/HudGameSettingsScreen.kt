package zx.nextcore.ui.hud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.WebStories
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import zx.nextcore.R
import zx.nextcore.ui.navigation.safePopBackStack
import zx.nextcore.ui.theme.BrandFontFamily
import zx.nextcore.ui.util.AppConfig
import zx.nextcore.ui.util.PerfData
import zx.nextcore.ui.util.getSupportedDownscaleFactors
import zx.nextcore.ui.util.getSupportedFpsTargets
import zx.nextcore.ui.util.getSupportedRefreshRates
import zx.nextcore.ui.viewmodel.AppSettingsViewModel

private val RENDERER_VALUES = listOf("default", "skiavk", "skiavkthreaded", "skiagl", "skiaglthreaded", "opengl", "openglthreaded", "vulkan")
private val RENDERER_LABELS = listOf("", "SkiaVK", "SkiaVK (Threaded)", "SkiaGL", "SkiaGL (Threaded)", "OpenGL ES", "OpenGL ES (Threaded)", "Vulkan")
private val TRI_VALUES = listOf("default", "true", "false")

/** Per-game overrides: master switch, performance toggles, display, resolution and FPS target. */
@Composable
fun HudGameSettingsScreen(navController: NavController, pkg: String?, vm: AppSettingsViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.loadConfig() }
    if (pkg == null) return
    val config: AppConfig? = vm.fullConfig[pkg]
    val cfg = config ?: AppConfig()
    val enabled = config != null
    val perf = rememberPerfSnapshot()
    val mySessions = perf.sessions.filter { it.pkg == pkg }

    val defaultLabel = stringResource(R.string.hud_default)
    val triLabels = listOf(defaultLabel, stringResource(R.string.hud_on), stringResource(R.string.hud_off))
    val refreshValues = remember { getSupportedRefreshRates(context) }
    val downscaleSteps = remember { getSupportedDownscaleFactors() }
    val fpsSteps = remember { getSupportedFpsTargets(context) }

    @Composable
    fun tri(key: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, title: Int) {
        val idx = TRI_VALUES.indexOf(value).coerceAtLeast(0)
        HudRow(
            stringResource(title), icon = icon, enabled = enabled,
            trailing = {
                HudValueChip(triLabels[idx], active = idx == 1) {
                    if (enabled) vm.updateSetting(pkg, key, TRI_VALUES[(idx + 1) % TRI_VALUES.size])
                }
            },
        )
    }

    HudPage(
        title = stringResource(R.string.hud_game_settings),
        onBack = { navController.safePopBackStack() },
        actions = { HudIconButton(Icons.Rounded.PlayArrow, stringResource(R.string.hud_launch), { launchApp(context, pkg) }) },
    ) {
        item(key = "game") {
            HudCard(Modifier.fillMaxWidth(), accent = true) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HudAppIcon(pkg, 56.dp)
                    Column(Modifier.weight(1f)) {
                        Text(appLabel(context, pkg), color = Hud.text, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pkg, color = Hud.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item(key = "master") {
            HudGroup(rows = listOf<@Composable () -> Unit>({
                HudRow(
                    stringResource(R.string.hud_master), icon = Icons.Rounded.PowerSettingsNew,
                    subtitle = stringResource(R.string.hud_master_sub),
                    trailing = { HudSwitch(enabled, { vm.toggleMasterSwitch(pkg, it) }) },
                )
            }))
        }
        item(key = "note") { HudNote(stringResource(R.string.hud_game_note)) }

        item(key = "h_perf") { HudSectionTitle(stringResource(R.string.hud_section_perf)) }
        item(key = "perf") {
            HudGroup(rows = listOf<@Composable () -> Unit>(
                { tri("perf_lite_mode", cfg.perf_lite_mode, Icons.Rounded.Bolt, R.string.hud_lite) },
                { tri("bypass_charging", cfg.bypass_charging, Icons.Rounded.BatteryChargingFull, R.string.hud_bypass_game) },
                { tri("game_preload", cfg.game_preload, Icons.Rounded.Download, R.string.hud_preload) },
                { tri("dnd_on_gaming", cfg.dnd_on_gaming, Icons.Rounded.DoNotDisturbOn, R.string.hud_dnd) },
                { tri("app_priority", cfg.app_priority, Icons.Rounded.SwapVert, R.string.hud_priority) },
            ))
        }

        item(key = "h_display") { HudSectionTitle(stringResource(R.string.hud_section_display)) }
        item(key = "display") {
            val rendererLabels = RENDERER_LABELS.mapIndexed { i, l -> if (i == 0) defaultLabel else l }
            val rIdx = RENDERER_VALUES.indexOfFirst { it.equals(cfg.renderer, ignoreCase = true) }.coerceAtLeast(0)
            val refreshLabels = refreshValues.map { if (it == "default") defaultLabel else "$it Hz" }
            val hIdx = refreshValues.indexOfFirst { it.equals(cfg.refresh_rate, ignoreCase = true) }.coerceAtLeast(0)
            HudGroup(rows = listOf<@Composable () -> Unit>(
                {
                    HudRow(stringResource(R.string.hud_renderer), icon = Icons.Rounded.Layers, enabled = enabled, trailing = {
                        HudDropdownChip(rendererLabels, rIdx, { if (enabled) vm.updateSetting(pkg, "renderer", RENDERER_VALUES[it]) }, active = rIdx != 0)
                    })
                },
                {
                    HudRow(stringResource(R.string.hud_refresh), icon = Icons.Rounded.WebStories, enabled = enabled, trailing = {
                        HudDropdownChip(refreshLabels, hIdx, { if (enabled) vm.updateSetting(pkg, "refresh_rate", refreshValues[it]) }, active = hIdx != 0)
                    })
                },
            ))
        }

        item(key = "h_res") { HudSectionTitle(stringResource(R.string.hud_section_res)) }
        item(key = "res") {
            val current = downscaleSteps.indexOf(cfg.resolution_downscale).takeIf { it >= 0 } ?: (downscaleSteps.size - 1)
            var pos by remember(cfg.resolution_downscale) { mutableFloatStateOf(current.toFloat()) }
            val label = downscaleSteps.getOrElse(pos.toInt()) { "default" }
            val off = label == "default"
            HudCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.hud_downscale), color = Hud.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    HudValueChip(if (off) defaultLabel else "${label}×", active = !off)
                }
                HudSlider(
                    value = pos,
                    onValueChange = { pos = it },
                    steps = (downscaleSteps.size - 2).coerceAtLeast(0),
                    range = 0f..(downscaleSteps.size - 1).toFloat(),
                    onFinished = { vm.updateSetting(pkg, "resolution_downscale", downscaleSteps[pos.toInt()]) },
                    enabled = enabled,
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(downscaleSteps.first(), color = Hud.muted, fontSize = 11.sp, fontFamily = BrandFontFamily, modifier = Modifier.weight(1f))
                    Text(defaultLabel, color = Hud.muted, fontSize = 11.sp)
                }
                Text(stringResource(R.string.hud_target_fps), color = if (off) Hud.muted else Hud.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.fillMaxWidth())
                val fIdx = fpsSteps.indexOf(cfg.resolution_fps).coerceAtLeast(0)
                HudSegmented(
                    options = fpsSteps,
                    selected = if (off) -1 else fIdx,
                    onSelect = { if (enabled && !off) vm.updateSetting(pkg, "resolution_fps", fpsSteps[it]) },
                    height = 38.dp,
                    fontSize = 14.sp,
                )
                Text(stringResource(R.string.hud_res_note), color = Hud.muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }

        item(key = "h_stats") { HudSectionTitle(stringResource(R.string.hud_game_stats)) }
        item(key = "stats") {
            if (mySessions.isEmpty()) {
                HudEmpty(stringResource(R.string.hud_sessions_empty))
            } else {
                val t = PerfData.totals(mySessions).first()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HudStat(stringResource(R.string.hud_sessions_count), t.sessions.toString(), Modifier.weight(1f))
                        HudStat(stringResource(R.string.hud_total_time), duration(t.seconds), Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HudStat(stringResource(R.string.hud_avg_fps), t.avgFps?.let { one(it) } ?: "--", Modifier.weight(1f))
                        HudStat(stringResource(R.string.hud_max_temp), t.maxTempC?.let { one(it) + "°C" } ?: "--", Modifier.weight(1f), highlight = true)
                    }
                }
            }
        }
    }
}
