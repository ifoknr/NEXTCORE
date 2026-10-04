/*
 * Copyright (C) 2026-2027 NextCore
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package zx.azenith.ui.subscreens


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zx.azenith.R
import zx.azenith.overlay.OverlayPrefs
import zx.azenith.overlay.OverlayService
import zx.azenith.ui.component.*
import zx.azenith.ui.navigation.safePopBackStack
import java.util.Locale
import kotlin.math.roundToInt


/** Interval choices for the Home and Monitor tabs. */
private val monitorIntervalsMs = listOf(1000L, 1500L, 3000L, 5000L)

private fun seconds(ms: Long): String =
    if (ms % 1000L == 0L) "${ms / 1000}" else String.format(Locale.US, "%.1f", ms / 1000f)

/** Floating monitor (overlay) options and how often the live tabs refresh. */
@Composable
fun MonitoringSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { OverlayPrefs.prefs(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val cs = MaterialTheme.colorScheme

    var enabled by remember { mutableStateOf(prefs.getBoolean(OverlayPrefs.ENABLED, false) && OverlayService.canDraw(context)) }
    // Set while the user is in system settings granting the overlay permission.
    var awaitingPermission by remember { mutableStateOf(false) }

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

    val permissionMsg = stringResource(R.string.nc_overlay_permission_needed)

    fun prefSwitch(key: String, default: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, summary: String? = null): @Composable () -> Unit = {
        var on by remember { mutableStateOf(prefs.getBoolean(key, default)) }
        ExpressiveSwitchItem(
            icon = icon,
            title = title,
            summary = summary,
            checked = on,
            onCheckedChange = { on = it; prefs.edit().putBoolean(key, it).apply() }
        )
    }

    Scaffold(
        topBar = {
            NcPageHeader(
                subtitle = stringResource(R.string.nc_monitoring_settings),
                onBack = { navController.safePopBackStack() }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = cs.surface
    ) { inner ->
        NcSheet(topPadding = inner.calculateTopPadding()) {
            LazyVerticalStaggeredGrid(
                columns = ncGridCells,
                modifier = Modifier.fillMaxSize(),
                contentPadding = ncSheetListPadding(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                horizontalArrangement = Arrangement.spacedBy(NcGridGap)
            ) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    NcWatermarkCard(
                        watermark = Icons.Rounded.PictureInPicture,
                        container = cs.primaryContainer,
                        contentColor = cs.onPrimaryContainer
                    ) {
                        Text(stringResource(R.string.nc_overlay_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.nc_overlay_desc), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }

                item {
                    Column {
                        MonitoringSectionTitle(stringResource(R.string.nc_overlay_title))
                        ExpressiveList(
                            content = listOf(
                                {
                                    ExpressiveSwitchItem(
                                        icon = Icons.Rounded.PictureInPicture,
                                        title = stringResource(R.string.nc_overlay_enable),
                                        summary = stringResource(R.string.nc_overlay_enable_desc),
                                        checked = enabled,
                                        onCheckedChange = { on ->
                                            if (!on) {
                                                setEnabled(false)
                                            } else scope.launch {
                                                val granted = withContext(Dispatchers.IO) { OverlayService.grantWithRoot(context) }
                                                if (granted) {
                                                    setEnabled(true)
                                                } else {
                                                    awaitingPermission = true
                                                    snackbar.showSnackbar(permissionMsg)
                                                    runCatching { context.startActivity(OverlayService.permissionIntent(context)) }
                                                }
                                            }
                                        }
                                    )
                                },
                                prefSwitch(
                                    OverlayPrefs.GAMES_ONLY, false, Icons.Rounded.SportsEsports,
                                    stringResource(R.string.nc_overlay_games_only), stringResource(R.string.nc_overlay_games_only_desc)
                                ),
                            )
                        )
                    }
                }

                item {
                    Column {
                        MonitoringSectionTitle(stringResource(R.string.nc_overlay_show))
                        ExpressiveList(
                            content = listOf(
                                prefSwitch(OverlayPrefs.SHOW_FPS, true, Icons.Rounded.Speed, stringResource(R.string.nc_overlay_fps), stringResource(R.string.nc_overlay_fps_desc)),
                                prefSwitch(OverlayPrefs.SHOW_CPU_TEMP, true, Icons.Rounded.Thermostat, stringResource(R.string.nc_overlay_cpu_temp)),
                                prefSwitch(OverlayPrefs.SHOW_GPU, false, Icons.Rounded.DeveloperBoard, stringResource(R.string.nc_overlay_gpu)),
                                prefSwitch(OverlayPrefs.SHOW_RAM, true, Icons.Rounded.Memory, stringResource(R.string.nc_overlay_ram)),
                                prefSwitch(OverlayPrefs.SHOW_BATT_TEMP, true, Icons.Rounded.BatteryFull, stringResource(R.string.nc_overlay_batt_temp)),
                            )
                        )
                    }
                }

                item {
                    Column {
                        MonitoringSectionTitle(stringResource(R.string.nc_overlay_look))
                        var vertical by remember { mutableStateOf(prefs.getBoolean(OverlayPrefs.VERTICAL, false)) }
                        var size by remember { mutableIntStateOf(prefs.getInt(OverlayPrefs.TEXT_SIZE, 1).coerceIn(0, 2)) }
                        var opacity by remember { mutableFloatStateOf(prefs.getInt(OverlayPrefs.OPACITY, 55).toFloat()) }
                        var interval by remember { mutableLongStateOf(prefs.getLong(OverlayPrefs.INTERVAL_MS, 1000L)) }
                        val secondsFmt = stringResource(R.string.nc_seconds)
                        ExpressiveList(
                            content = listOf(
                                {
                                    ExpressiveDropdownItem(
                                        icon = Icons.Rounded.ViewDay,
                                        title = stringResource(R.string.nc_overlay_layout),
                                        items = listOf(stringResource(R.string.nc_overlay_horizontal), stringResource(R.string.nc_overlay_vertical)),
                                        selectedIndex = if (vertical) 1 else 0,
                                        onItemSelected = { vertical = it == 1; prefs.edit().putBoolean(OverlayPrefs.VERTICAL, vertical).apply() }
                                    )
                                },
                                {
                                    ExpressiveDropdownItem(
                                        icon = Icons.Rounded.FormatSize,
                                        title = stringResource(R.string.nc_overlay_text_size),
                                        items = listOf(
                                            stringResource(R.string.nc_size_small),
                                            stringResource(R.string.nc_size_medium),
                                            stringResource(R.string.nc_size_large)
                                        ),
                                        selectedIndex = size,
                                        onItemSelected = { size = it; prefs.edit().putInt(OverlayPrefs.TEXT_SIZE, it).apply() }
                                    )
                                },
                                {
                                    ExpressiveDropdownItem(
                                        icon = Icons.Rounded.Update,
                                        title = stringResource(R.string.nc_overlay_refresh),
                                        items = OverlayPrefs.intervalsMs.map { String.format(secondsFmt, seconds(it)) },
                                        selectedIndex = OverlayPrefs.intervalsMs.indexOf(interval).coerceAtLeast(0),
                                        onItemSelected = { interval = OverlayPrefs.intervalsMs[it]; prefs.edit().putLong(OverlayPrefs.INTERVAL_MS, interval).apply() }
                                    )
                                },
                                {
                                    ExpressiveSliderItem(
                                        icon = Icons.Rounded.Opacity,
                                        title = stringResource(R.string.nc_overlay_opacity),
                                        badgeText = "${opacity.roundToInt()}%",
                                        sliderPosition = opacity,
                                        valueRange = 0f..100f,
                                        steps = 9,
                                        onValueChange = { opacity = it },
                                        onValueChangeFinished = { prefs.edit().putInt(OverlayPrefs.OPACITY, opacity.roundToInt()).apply() }
                                    )
                                },
                            )
                        )
                        Text(
                            stringResource(R.string.nc_overlay_drag_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                item {
                    Column {
                        MonitoringSectionTitle(stringResource(R.string.nc_refresh_section))
                        var monitorInterval by remember {
                            mutableLongStateOf(prefs.getLong(OverlayPrefs.MONITOR_INTERVAL_MS, OverlayPrefs.DEFAULT_MONITOR_INTERVAL_MS))
                        }
                        val secondsFmt = stringResource(R.string.nc_seconds)
                        ExpressiveList(
                            content = listOf {
                                ExpressiveDropdownItem(
                                    icon = Icons.Rounded.Timer,
                                    title = stringResource(R.string.nc_monitor_refresh),
                                    summary = stringResource(R.string.nc_monitor_refresh_desc),
                                    items = monitorIntervalsMs.map { String.format(secondsFmt, seconds(it)) },
                                    selectedIndex = monitorIntervalsMs.indexOf(monitorInterval).coerceAtLeast(0),
                                    onItemSelected = {
                                        monitorInterval = monitorIntervalsMs[it]
                                        prefs.edit().putLong(OverlayPrefs.MONITOR_INTERVAL_MS, monitorInterval).apply()
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitoringSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)
    )
}
