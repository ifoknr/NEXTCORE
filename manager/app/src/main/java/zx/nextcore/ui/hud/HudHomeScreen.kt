package zx.nextcore.ui.hud

import android.os.Build
import android.system.Os
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zx.nextcore.R
import zx.nextcore.ui.theme.BrandFontFamily
import zx.nextcore.ui.util.PerfData
import zx.nextcore.ui.util.PropertyUtils
import zx.nextcore.ui.util.SupportLevel
import zx.nextcore.ui.util.getRealDeviceName
import zx.nextcore.ui.util.getSELinuxStatus
import zx.nextcore.ui.viewmodel.ApplistViewmodel
import zx.nextcore.ui.viewmodel.HomeViewModel
import java.util.Locale

/**
 * Home dashboard. On phones the sections stack; from 840 dp they spread over
 * three columns with the 30-minute chart and today's stats below.
 */
@Composable
fun HudHomeScreen(
    navController: NavController,
    isVisible: Boolean,
    onOpenGames: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val ui by viewModel.uiState.collectAsState()
    LiveStatsPoller(viewModel, isVisible, ui.profileLoaded)
    LaunchedEffect(Unit) { viewModel.refreshAiMode() }
    val perf = rememberPerfSnapshot(isVisible)

    var deviceName by remember { mutableStateOf("${Build.MANUFACTURER} ${Build.MODEL}") }
    var selinux by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            deviceName = getRealDeviceName(context)
            selinux = getSELinuxStatus(context)
        }
    }
    val appVm: ApplistViewmodel = viewModel()
    LaunchedEffect(Unit) { appVm.loadApps(context) }
    val enabledGames = ApplistViewmodel.apps.filter { it.isEnabledInConfig }

    val restarting = stringResource(R.string.hud_restarting)
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) { if (toast != null) { delay(2500); toast = null } }

    val sections = HomeSections(
        status = { StatusBlock(ui.serviceStatusRes == R.string.status_alive, ui.servicePid, ui.moduleVersion, ui.rootStatus, ui.currentProfileValue, ui.autoMode != "0") },
        live = {
            LiveBlock(
                tempC = ui.live.cpuTempC,
                peakKhz = ui.live.clusters.maxOfOrNull { it.curKhz } ?: 0L,
                maxKhz = ui.live.peakKhz,
                hz = ui.live.refreshHz,
                maxHz = ui.live.maxRefreshHz,
                onCpu = { navController.navigate("engine_cpu") },
                onFps = { navController.navigate("engine_fps") },
            )
        },
        mode = {
            ModeBlock(ui.autoMode != "0", ui.currentProfileValue) { choice ->
                if (choice == "auto") viewModel.setAutoMode(true)
                else {
                    if (ui.autoMode != "0") viewModel.setAutoMode(false)
                    viewModel.applyProfile(choice) {}
                }
            }
        },
        game = { NowPlayingBlock(ui.runningGamePkg, ui.runningGameStartTime) },
        games = {
            GamesRowBlock(
                pkgs = enabledGames.map { it.packageName },
                onGame = { navController.navigate("app_settings/$it") },
                onAll = onOpenGames,
            )
        },
        shortcuts = { ShortcutsBlock() },
        engines = { EnginesBlock(navController) },
        sessions = { RecentSessionsBlock(perf, onAll = { navController.navigate("sessions") }) },
        device = {
            DeviceBlock(
                rows = listOf(
                    stringResource(R.string.hud_device_name) to deviceName,
                    stringResource(R.string.hud_soc) to listOf(ui.deviceProfile.socVendor, ui.deviceProfile.socModel).filter { it.isNotBlank() }.joinToString(" ").ifBlank { Build.HARDWARE },
                    stringResource(R.string.hud_support) to supportLabel(ui.deviceProfile.support),
                    stringResource(R.string.hud_kernel) to Os.uname().release,
                    stringResource(R.string.hud_android) to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    stringResource(R.string.hud_selinux) to selinux,
                    stringResource(R.string.hud_abi) to (Build.SUPPORTED_ABIS.firstOrNull() ?: ""),
                ),
                selinuxKey = stringResource(R.string.hud_selinux),
            )
        },
        links = { LinksBlock() },
        chart = { ChartBlock(perf) },
        stats = { TodayBlock(perf) },
    )

    HudPage(
        title = "NEXTCORE",
        titleContent = { Wordmark() },
        actions = {
            HudIconButton(Icons.Rounded.RestartAlt, stringResource(R.string.hud_restart), {
                toast = restarting
                viewModel.restartService {}
            })
            HudIconButton(Icons.Rounded.PowerSettingsNew, stringResource(R.string.hud_reboot), { viewModel.rebootDevice("") })
        },
    ) {
        if (toast != null) item(key = "toast") { HudNote(toast!!) }
        item(key = "body") {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 840.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                sections.status(); sections.live(); sections.mode(); sections.shortcuts()
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                sections.game(); sections.games(); sections.engines(); sections.sessions()
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                sections.device(); sections.links()
                            }
                        }
                        sections.chart(); sections.stats()
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        sections.status(); sections.live(); sections.mode(); sections.game()
                        sections.games(); sections.engines(); sections.shortcuts(); sections.chart()
                        sections.stats(); sections.sessions(); sections.device(); sections.links()
                    }
                }
            }
        }
    }
}

private class HomeSections(
    val status: @Composable () -> Unit,
    val live: @Composable () -> Unit,
    val mode: @Composable () -> Unit,
    val game: @Composable () -> Unit,
    val games: @Composable () -> Unit,
    val shortcuts: @Composable () -> Unit,
    val engines: @Composable () -> Unit,
    val sessions: @Composable () -> Unit,
    val device: @Composable () -> Unit,
    val links: @Composable () -> Unit,
    val chart: @Composable () -> Unit,
    val stats: @Composable () -> Unit,
)

/** "NEXT" in white and "CORE" in the accent, as on the HUD header. */
@Composable
fun Wordmark() {
    val accent = hudAccent
    Text(
        buildAnnotatedString {
            append("NEXT")
            withStyle(SpanStyle(color = accent)) { append("CORE") }
        },
        color = Hud.text,
        fontFamily = BrandFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 26.sp,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun supportLabel(level: SupportLevel): String = when (level) {
    SupportLevel.FULL -> stringResource(R.string.nc_support_full)
    SupportLevel.PARTIAL -> stringResource(R.string.nc_support_partial)
    SupportLevel.UNKNOWN -> stringResource(R.string.nc_support_unknown)
}

@Composable
private fun StatusBlock(alive: Boolean, pid: String, version: String, root: Boolean, profile: String, auto: Boolean) {
    HudCard(Modifier.fillMaxWidth(), accent = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.hud_service_state), color = Hud.muted, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (alive) Hud.green else Hud.red))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(if (alive) R.string.hud_running else R.string.hud_stopped),
                        color = if (alive) Hud.green else Hud.red,
                        fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    )
                }
            }
            Text(
                listOfNotNull(pid.takeIf { it.isNotBlank() }?.let { "PID $it" }, version.takeIf { it.isNotBlank() }).joinToString("  ·  "),
                color = Hud.muted, fontSize = 12.sp, fontFamily = BrandFontFamily,
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HudCard(Modifier.weight(1f)) {
            Text(stringResource(R.string.hud_current_profile), color = Hud.muted, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(profileName(profile), color = Hud.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (auto) {
                    Spacer(Modifier.width(6.dp))
                    Text("AUTO", color = Hud.muted, fontSize = 11.sp, fontFamily = BrandFontFamily, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
        HudCard(Modifier.weight(1f)) {
            Text(stringResource(R.string.hud_root), color = Hud.muted, fontSize = 12.sp)
            Text(
                (if (root) "✓ " else "✕ ") + stringResource(if (root) R.string.hud_root_granted else R.string.hud_root_denied),
                color = if (root) Hud.green else Hud.red, fontWeight = FontWeight.Bold, fontSize = 18.sp,
            )
        }
    }
}

@Composable
fun profileName(value: String): String = stringResource(
    when (value) {
        "1" -> R.string.hud_mode_perf
        "3" -> R.string.hud_mode_eco
        else -> R.string.hud_mode_balanced
    }
)

@Composable
private fun LiveBlock(
    tempC: Float?, peakKhz: Long, maxKhz: Long, hz: Int?, maxHz: Int?,
    onCpu: () -> Unit, onFps: () -> Unit,
) {
    HudSectionTitle(stringResource(R.string.hud_live))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HudGauge(
            progress = ((tempC ?: 0f) - 20f) / 70f,
            value = tempC?.let { one(it) + "°" } ?: "--",
            label = stringResource(R.string.hud_temp),
            modifier = Modifier.weight(1f),
            colors = listOf(hudAccent, Hud.red),
            onClick = onCpu,
        )
        HudGauge(
            progress = if (maxKhz > 0) peakKhz.toFloat() / maxKhz else 0f,
            value = if (peakKhz > 0) ghzShort(peakKhz) else "--",
            label = stringResource(R.string.hud_cpu_ghz),
            modifier = Modifier.weight(1f),
            colors = listOf(Hud.red, hudAccent),
            onClick = onCpu,
        )
        HudGauge(
            progress = if ((maxHz ?: 0) > 0) (hz ?: 0).toFloat() / maxHz!! else 1f,
            value = hz?.toString() ?: "--",
            label = stringResource(R.string.hud_screen_hz),
            modifier = Modifier.weight(1f),
            colors = listOf(Hud.yellow, hudAccent),
            onClick = onFps,
        )
    }
}

@Composable
private fun ModeBlock(auto: Boolean, profile: String, onSelect: (String) -> Unit) {
    HudSectionTitle(stringResource(R.string.hud_mode))
    val values = listOf("auto", "3", "2", "1")
    val selected = if (auto) 0 else values.indexOf(profile).takeIf { it >= 0 } ?: 2
    HudSegmented(
        options = listOf(
            stringResource(R.string.hud_mode_auto),
            stringResource(R.string.hud_mode_eco),
            stringResource(R.string.hud_mode_balanced),
            stringResource(R.string.hud_mode_perf),
        ),
        selected = selected,
        onSelect = { onSelect(values[it]) },
    )
}

@Composable
private fun NowPlayingBlock(pkg: String?, startTime: String?) {
    val context = LocalContext.current
    HudSectionTitle(stringResource(R.string.hud_now_playing))
    if (pkg == null) {
        HudEmpty(stringResource(R.string.hud_no_game))
        return
    }
    // The daemon writes the start as "HH:MM:SS"; count up from it locally.
    var elapsed by remember(pkg, startTime) { mutableLongStateOf(0L) }
    LaunchedEffect(pkg, startTime) {
        val start = parseClock(startTime)
        while (true) {
            elapsed = if (start != null) {
                val now = java.util.Calendar.getInstance()
                val secs = now.get(java.util.Calendar.HOUR_OF_DAY) * 3600L + now.get(java.util.Calendar.MINUTE) * 60L + now.get(java.util.Calendar.SECOND)
                ((secs - start) + 86_400L) % 86_400L
            } else elapsed + 1
            delay(1000)
        }
    }
    HudCard(Modifier.fillMaxWidth(), accent = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HudAppIcon(pkg, 44.dp)
            Column(Modifier.weight(1f)) {
                Text(appLabel(context, pkg), color = Hud.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(pkg, color = Hud.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                String.format(Locale.US, "%02d:%02d:%02d", elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60),
                color = hudAccent, fontFamily = BrandFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp,
            )
        }
    }
}

private fun parseClock(text: String?): Long? {
    val parts = text?.trim()?.split(' ')?.lastOrNull()?.split(':') ?: return null
    if (parts.size < 2) return null
    val h = parts[0].toLongOrNull() ?: return null
    val m = parts[1].toLongOrNull() ?: return null
    val s = parts.getOrNull(2)?.toLongOrNull() ?: 0L
    return h * 3600 + m * 60 + s
}

@Composable
private fun GamesRowBlock(pkgs: List<String>, onGame: (String) -> Unit, onAll: () -> Unit) {
    val context = LocalContext.current
    HudSectionTitle(stringResource(R.string.hud_your_games), stringResource(R.string.hud_view_all), onAll)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(pkgs, key = { it }) { pkg ->
            Column(
                Modifier.width(76.dp).clip(Hud.tileShape).clickable { onGame(pkg) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HudAppIcon(pkg, 64.dp)
                Spacer(Modifier.height(6.dp))
                Text(appLabel(context, pkg), color = Hud.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item(key = "add") {
            Column(Modifier.width(76.dp).clip(Hud.tileShape).clickable(onClick = onAll), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(64.dp).clip(Hud.tileShape).background(Hud.card).border(1.dp, Hud.line, Hud.tileShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, null, tint = hudAccent) }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.hud_add), color = Hud.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ShortcutsBlock() {
    val scope = rememberCoroutineScope()
    var bypass by remember { mutableStateOf<Boolean?>(null) }
    var dnd by remember { mutableStateOf<Boolean?>(null) }
    var thermal by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            bypass = PropertyUtils.get("persist.sys.nextcoreconf.bypasschg") == "1"
            dnd = PropertyUtils.get("persist.sys.nextcoreconf.dnd") == "1"
            thermal = PropertyUtils.get("persist.sys.nextcoreconf.thermalcore") == "1"
        }
    }
    fun set(prop: String, on: Boolean) = scope.launch(Dispatchers.IO) { PropertyUtils.set(prop, if (on) "1" else "0") }
    HudSectionTitle(stringResource(R.string.hud_shortcuts))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ShortcutTile(stringResource(R.string.hud_bypass), bypass, Modifier.weight(1f)) { bypass = it; set("persist.sys.nextcoreconf.bypasschg", it) }
        ShortcutTile(stringResource(R.string.hud_dnd), dnd, Modifier.weight(1f)) { dnd = it; set("persist.sys.nextcoreconf.dnd", it) }
        ShortcutTile(stringResource(R.string.hud_thermalcore), thermal, Modifier.weight(1f)) { thermal = it; set("persist.sys.nextcoreconf.thermalcore", it) }
    }
}

@Composable
private fun ShortcutTile(label: String, checked: Boolean?, modifier: Modifier, onChange: (Boolean) -> Unit) {
    HudCard(modifier, padding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Hud.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2)
            if (checked != null) HudSwitch(checked, onChange)
        }
    }
}

@Composable
private fun EnginesBlock(navController: NavController) {
    HudSectionTitle(stringResource(R.string.hud_engines))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EngineTile(Icons.Rounded.DeveloperBoard, stringResource(R.string.hud_engine_cpu), Modifier.weight(1f)) { navController.navigate("engine_cpu") }
        EngineTile(Icons.Rounded.Memory, stringResource(R.string.hud_engine_mem), Modifier.weight(1f)) { navController.navigate("engine_mem") }
        EngineTile(Icons.Rounded.Speed, stringResource(R.string.hud_engine_fps), Modifier.weight(1f)) { navController.navigate("engine_fps") }
        EngineTile(Icons.Rounded.History, stringResource(R.string.hud_engine_sessions), Modifier.weight(1f)) { navController.navigate("sessions") }
    }
}

@Composable
private fun EngineTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    HudCard(modifier, onClick = onClick, padding = PaddingValues(vertical = 12.dp, horizontal = 6.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            HudIconTile(icon)
            Spacer(Modifier.height(6.dp))
            Text(label, color = Hud.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun RecentSessionsBlock(perf: PerfSnapshot, onAll: () -> Unit) {
    val context = LocalContext.current
    HudSectionTitle(stringResource(R.string.hud_recent_sessions), stringResource(R.string.hud_view_all), onAll)
    val recent = perf.sessions.take(3)
    if (recent.isEmpty()) {
        HudEmpty(stringResource(R.string.hud_sessions_empty))
        return
    }
    HudCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
        recent.forEachIndexed { i, s ->
            if (i > 0) androidx.compose.material3.HorizontalDivider(color = Hud.line)
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(appLabel(context, s.pkg), color = Hud.muted, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(duration(s.seconds), s.avgFps?.let { one(it) + " FPS" }, s.maxTempC?.let { one(it) + "°" }).joinToString(" · "),
                    color = Hud.text, fontSize = 13.sp, fontFamily = BrandFontFamily, fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun DeviceBlock(rows: List<Pair<String, String>>, selinuxKey: String) {
    var expanded by remember { mutableStateOf(false) }
    HudSectionTitle(
        stringResource(R.string.hud_device),
        stringResource(if (expanded) R.string.hud_show_less else R.string.hud_show_more),
    ) { expanded = !expanded }
    HudKeyValues(if (expanded) rows else rows.take(4), highlight = if ("Enforcing" in rows.toMap().values) setOf(selinuxKey) else emptySet())
}

@Composable
private fun LinksBlock() {
    val context = LocalContext.current
    HudSectionTitle(stringResource(R.string.hud_links))
    HudGroup(rows = listOf(
        { HudRow(stringResource(R.string.hud_channel), icon = Icons.AutoMirrored.Rounded.Send, subtitle = stringResource(R.string.hud_channel_sub), onClick = { openUrl(context, CHANNEL_URL) }) },
        { HudRow(stringResource(R.string.hud_source), icon = Icons.Rounded.Code, subtitle = stringResource(R.string.hud_source_sub), onClick = { openUrl(context, SOURCE_URL) }) },
    ))
}

@Composable
private fun ChartBlock(perf: PerfSnapshot) {
    val temp = ChartSeries(stringResource(R.string.hud_chart_temp), perf.history.map { it.tempC }, hudAccent, filled = true)
    val freq = ChartSeries(stringResource(R.string.hud_chart_freq), perf.history.map { it.cpuMhz.takeIf { m -> m > 0 }?.toFloat() }, Hud.yellow, dashed = true)
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.hud_chart_title), color = Hud.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        HudChartLegend(listOf(temp, freq))
    }
    if (perf.history.size < 2) {
        HudEmpty(stringResource(R.string.hud_chart_empty))
    } else {
        HudLineChart(listOf(temp, freq), xLabels = listOf("-30m", "-25m", "-20m", "-15m", "-10m", "-5m", "0m"))
    }
}

@Composable
private fun TodayBlock(perf: PerfSnapshot) {
    val t = PerfData.today(perf.sessions, perf.history)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HudStat(stringResource(R.string.hud_today_play), duration(t.playSeconds), Modifier.weight(1f))
        HudStat(stringResource(R.string.hud_avg_freq), t.avgMhz?.let { String.format(Locale.US, "%.1f GHz", it / 1000f) } ?: "--", Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HudStat(stringResource(R.string.hud_avg_temp), t.avgTempC?.let { one(it) + "°C" } ?: "--", Modifier.weight(1f))
        HudStat(stringResource(R.string.hud_max_temp), t.maxTempC?.let { one(it) + "°C" } ?: "--", Modifier.weight(1f), highlight = true)
    }
}
