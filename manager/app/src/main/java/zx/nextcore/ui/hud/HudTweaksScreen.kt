package zx.nextcore.ui.hud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WebStories
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zx.nextcore.R
import zx.nextcore.ui.theme.BrandFontFamily
import zx.nextcore.ui.util.PropertyUtils
import zx.nextcore.ui.util.getSupportedRefreshRatesPicker
import zx.nextcore.ui.viewmodel.TweakViewModel

private val RENDERERS = listOf("skiavk", "skiavkthreaded", "skiagl", "skiaglthreaded", "opengl", "openglthreaded", "vulkan")
private val RENDERER_NAMES = listOf("SkiaVK", "SkiaVK (Threaded)", "SkiaGL", "SkiaGL (Threaded)", "OpenGL ES", "OpenGL ES (Threaded)", "Vulkan")

/** Global tweaks: what applies to every enabled game, plus the engine pages. */
@Composable
fun HudTweaksScreen(navController: NavController, isVisible: Boolean, vm: TweakViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(isVisible) { if (isVisible) vm.loadAllConfiguration(context) }
    var isMediaTek by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isMediaTek = withContext(Dispatchers.IO) { PropertyUtils.get("persist.sys.nextcore.soctype") == "1" } }
    val refreshRates = remember { getSupportedRefreshRatesPicker(context) }

    HudPage(title = stringResource(R.string.hud_nav_tweaks)) {
        item(key = "note") { HudNote(stringResource(R.string.hud_tweaks_note)) }

        item(key = "h_perf") { HudSectionTitle(stringResource(R.string.hud_section_perf)) }
        item(key = "perf") {
            HudGroup(rows = listOf<@Composable () -> Unit>(
                {
                    HudRow(stringResource(R.string.hud_lite), icon = Icons.Rounded.Bolt, subtitle = stringResource(R.string.hud_lite_sub), trailing = {
                        vm.liteState?.let { HudSwitch(it, vm::updateLiteMode) }
                    })
                },
                {
                    HudRow(stringResource(R.string.nc_perf_max), icon = Icons.Rounded.Whatshot, subtitle = stringResource(R.string.nc_perf_max_desc), trailing = {
                        vm.perfMaxState?.let { HudSwitch(it, vm::updatePerfMax) }
                    })
                },
                {
                    HudRow(
                        stringResource(R.string.hud_fpsgo), icon = Icons.Rounded.Speed, subtitle = stringResource(R.string.hud_fpsgo_sub),
                        enabled = isMediaTek, onClick = { navController.navigate("fpsgoscreen") },
                    )
                },
                {
                    HudRow(
                        stringResource(R.string.hud_gov), icon = Icons.Rounded.Settings, subtitle = stringResource(R.string.hud_gov_sub),
                        onClick = { navController.navigate("governorsettings") },
                        trailing = {
                            HudValueChip(
                                vm.availableGovernors?.getOrNull(vm.defaultGovIndex ?: -1) ?: "—",
                                active = true,
                                icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            ) { navController.navigate("governorsettings") }
                        },
                    )
                },
            ))
        }

        item(key = "h_display") { HudSectionTitle(stringResource(R.string.hud_section_display)) }
        item(key = "display") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HudCard(Modifier.weight(1f)) {
                    Text(stringResource(R.string.hud_refresh), color = Hud.muted, fontSize = 12.sp)
                    val cur = vm.currentRefreshRate?.toString()
                    val idx = refreshRates.indexOf(cur).coerceAtLeast(0)
                    HudDropdownChip(refreshRates.map { "$it Hz" }, idx, { vm.executeSetRefreshRates(refreshRates[it], context) }, active = true, fill = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                HudCard(Modifier.weight(1f)) {
                    Text(stringResource(R.string.hud_renderer), color = Hud.muted, fontSize = 12.sp)
                    val idx = RENDERERS.indexOfFirst { it.equals(vm.currentRenderer, ignoreCase = true) }
                    HudDropdownChip(
                        RENDERER_NAMES, idx.coerceAtLeast(0),
                        { vm.executeSetRenderer(RENDERERS[it], context) },
                        active = idx >= 0,
                        fill = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }

        item(key = "h_extra") { HudSectionTitle(stringResource(R.string.hud_section_extra)) }
        item(key = "extra") {
            HudGroup(rows = listOf<@Composable () -> Unit>(
                { HudRow(stringResource(R.string.hud_preload), icon = Icons.Rounded.Download, subtitle = stringResource(R.string.hud_preload_sub), trailing = { vm.preloadState?.let { HudSwitch(it, vm::updatePreloadMode) } }) },
                { HudRow(stringResource(R.string.hud_dnd), icon = Icons.Rounded.DoNotDisturbOn, trailing = { vm.dndState?.let { HudSwitch(it, vm::updateDndMode) } }) },
                { HudRow(stringResource(R.string.hud_cleaner), icon = Icons.Rounded.CleaningServices, subtitle = stringResource(R.string.hud_cleaner_sub), trailing = { vm.memKillerState?.let { HudSwitch(it, vm::updateMemoryKiller) } }) },
                { HudRow(stringResource(R.string.hud_priority), icon = Icons.Rounded.SwapVert, subtitle = stringResource(R.string.hud_priority_sub), trailing = { vm.appPriorState?.let { HudSwitch(it, vm::updateAppPriority) } }) },
            ))
        }

        item(key = "h_power") { HudSectionTitle(stringResource(R.string.hud_section_power)) }
        item(key = "power") {
            HudGroup(rows = listOf<@Composable () -> Unit>(
                { HudRow(stringResource(R.string.hud_bypass_game), icon = Icons.Rounded.BatteryChargingFull, subtitle = stringResource(R.string.hud_bypass_sub), onClick = { navController.navigate("bypasschg") }) },
                { HudRow(stringResource(R.string.hud_thermalcore), icon = Icons.Rounded.AcUnit, subtitle = stringResource(R.string.hud_thermal_sub), trailing = { vm.thermalState?.let { HudSwitch(it, vm::updateThermalCore) } }) },
            ))
        }

        item(key = "h_engines") { HudSectionTitle(stringResource(R.string.hud_engines)) }
        item(key = "engines") {
            HudGroup(rows = listOf<@Composable () -> Unit>(
                { HudRow(stringResource(R.string.hud_cpu_engine), icon = Icons.Rounded.DeveloperBoard, onClick = { navController.navigate("engine_cpu") }) },
                { HudRow(stringResource(R.string.hud_mem_engine), icon = Icons.Rounded.Memory, onClick = { navController.navigate("engine_mem") }) },
                { HudRow(stringResource(R.string.hud_fps_engine), icon = Icons.Rounded.Insights, onClick = { navController.navigate("engine_fps") }) },
            ))
        }

        item(key = "h_more") { HudSectionTitle(stringResource(R.string.hud_section_more)) }
        item(key = "more") {
            HudGroup(rows = listOf<@Composable () -> Unit>(
                { HudRow(stringResource(R.string.hud_preferenced), icon = Icons.Rounded.Tune, subtitle = stringResource(R.string.hud_preferenced_sub), onClick = { navController.navigate("preferenced") }) },
                { HudRow(stringResource(R.string.hud_color_scheme), icon = Icons.Rounded.Palette, onClick = { navController.navigate("colorscheme") }) },
                { HudRow(stringResource(R.string.hud_fas), icon = Icons.Rounded.Layers, onClick = { navController.navigate("FasScreen") }) },
                { HudRow(stringResource(R.string.hud_monitor_overlay), icon = Icons.Rounded.WebStories, subtitle = stringResource(R.string.hud_monitor_overlay_sub), onClick = { navController.navigate("monitoring") }) },
            ))
        }
    }
}
