package zx.nextcore.ui.hud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zx.nextcore.R
import zx.nextcore.ui.navigation.safePopBackStack
import zx.nextcore.ui.theme.BrandFontFamily
import zx.nextcore.ui.util.PropertyUtils
import zx.nextcore.ui.util.RootUtils
import zx.nextcore.ui.viewmodel.AppSettingsViewModel
import zx.nextcore.ui.viewmodel.HomeViewModel
import java.util.Locale
import kotlin.math.sqrt

/* ---------------- CPU engine ---------------- */

@Composable
fun HudCpuEngineScreen(navController: NavController, vm: HomeViewModel = viewModel()) {
    val ui by vm.uiState.collectAsState()
    LiveStatsPoller(vm, true, ui.profileLoaded)
    var perfMax by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { perfMax = withContext(Dispatchers.IO) { PropertyUtils.get("persist.sys.nextcoreconf.perfmax") == "1" } }
    val live = ui.live

    HudPage(title = stringResource(R.string.hud_cpu_engine), onBack = { navController.safePopBackStack() }) {
        item(key = "note") { HudNote(stringResource(R.string.hud_cpu_engine_note)) }
        item(key = "profile") {
            HudCard(Modifier.fillMaxWidth(), accent = true) {
                Text(stringResource(R.string.hud_current_profile), color = Hud.muted, fontSize = 12.sp)
                Text(profileName(ui.currentProfileValue), color = Hud.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(
                        when {
                            ui.currentProfileValue == "1" && perfMax -> R.string.hud_profile_max_explain
                            ui.currentProfileValue == "1" -> R.string.hud_profile_perf_explain
                            ui.currentProfileValue == "3" -> R.string.hud_profile_eco_explain
                            else -> R.string.hud_profile_bal_explain
                        }
                    ),
                    color = Hud.muted, fontSize = 13.sp, lineHeight = 19.sp,
                )
            }
        }
        item(key = "gauges") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HudGauge(((live.cpuTempC ?: 0f) - 20f) / 70f, live.cpuTempC?.let { one(it) + "°" } ?: "--", stringResource(R.string.hud_cpu_temp), Modifier.weight(1f))
                HudGauge(
                    if ((live.gpuMaxMhz ?: 0) > 0) (live.gpuMhz ?: 0).toFloat() / live.gpuMaxMhz!! else 0f,
                    live.gpuMhz?.toString() ?: "--", stringResource(R.string.hud_gpu) + " MHz", Modifier.weight(1f),
                    colors = listOf(Hud.yellow, hudAccent),
                )
                HudGauge((live.gpuLoad ?: 0) / 100f, live.gpuLoad?.let { "$it%" } ?: "--", stringResource(R.string.hud_gpu_load), Modifier.weight(1f), colors = listOf(Hud.red, hudAccent))
            }
        }
        item(key = "h_clusters") { HudSectionTitle(live.governor.ifBlank { "CPU" }) }
        live.clusters.forEachIndexed { i, c ->
            item(key = "cluster_$i") {
                HudCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.label, color = Hud.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        if (c.governor.isNotBlank()) HudTag(c.governor, true)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("${ghz(c.curKhz)} GHz", color = Hud.text, fontFamily = BrandFontFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                    Spacer(Modifier.height(6.dp))
                    HudBar(
                        progress = if (c.maxKhz > 0) c.curKhz.toFloat() / c.maxKhz else 0f,
                        marker = if (c.maxKhz > 0 && c.minLimitKhz > 0) c.minLimitKhz.toFloat() / c.maxKhz else null,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.hud_floor), color = Hud.muted, fontSize = 12.sp)
                            Text("${ghz(c.minLimitKhz)} GHz", color = Hud.text, fontFamily = BrandFontFamily, fontSize = 15.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.hud_ceiling), color = Hud.muted, fontSize = 12.sp)
                            Text("${ghz(c.maxLimitKhz)} GHz", color = Hud.text, fontFamily = BrandFontFamily, fontSize = 15.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Max", color = Hud.muted, fontSize = 12.sp)
                            Text("${ghz(c.maxKhz)} GHz", color = Hud.muted, fontFamily = BrandFontFamily, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- Memory engine ---------------- */

private data class MemTunables(
    val swappiness: String = "--",
    val vfs: String = "--",
    val readAhead: String = "--",
    val io: String = "--",
    val zramAlgo: String = "",
)

private fun readTunables(): MemTunables {
    fun r(p: String) = RootUtils.readRootFile(p)?.trim().orEmpty()
    val block = listOf("sda", "mmcblk0", "sdc").firstOrNull { RootUtils.rootFileExists("/sys/block/$it/queue/read_ahead_kb") } ?: "sda"
    val sched = r("/sys/block/$block/queue/scheduler")
    val active = Regex("\\[([^]]+)]").find(sched)?.groupValues?.get(1) ?: sched.substringBefore(' ')
    val algo = r("/sys/block/zram0/comp_algorithm")
    return MemTunables(
        swappiness = r("/proc/sys/vm/swappiness").ifBlank { "--" },
        vfs = r("/proc/sys/vm/vfs_cache_pressure").ifBlank { "--" },
        readAhead = r("/sys/block/$block/queue/read_ahead_kb").ifBlank { "--" }.let { if (it == "--") it else "$it KB ($block)" },
        io = active.ifBlank { "--" },
        zramAlgo = Regex("\\[([^]]+)]").find(algo)?.groupValues?.get(1).orEmpty(),
    )
}

@Composable
fun HudMemoryEngineScreen(navController: NavController, vm: HomeViewModel = viewModel()) {
    val ui by vm.uiState.collectAsState()
    LiveStatsPoller(vm, true, ui.profileLoaded)
    val scope = rememberCoroutineScope()
    var tunables by remember { mutableStateOf(MemTunables()) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { tunables = withContext(Dispatchers.IO) { readTunables() } }
    LaunchedEffect(done) { if (done) { delay(3000); done = false } }
    val live = ui.live
    val total = live.memTotalKb ?: 0L
    val avail = live.memAvailKb ?: 0L
    val used = (total - avail).coerceAtLeast(0)
    val swapTotal = live.swapTotalKb ?: 0L
    val swapUsed = (swapTotal - (live.swapFreeKb ?: 0L)).coerceAtLeast(0)

    HudPage(title = stringResource(R.string.hud_mem_engine), onBack = { navController.safePopBackStack() }) {
        item(key = "note") { HudNote(stringResource(R.string.hud_mem_note)) }
        if (done) item(key = "done") { HudNote(stringResource(R.string.hud_trim_done)) }
        item(key = "gauges") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HudGauge(if (total > 0) used.toFloat() / total else 0f, if (total > 0) "${used * 100 / total}%" else "--", stringResource(R.string.hud_ram), Modifier.weight(1f))
                HudGauge(
                    if (swapTotal > 0) swapUsed.toFloat() / swapTotal else 0f,
                    if (swapTotal > 0) "${swapUsed * 100 / swapTotal}%" else "--",
                    stringResource(R.string.hud_zram), Modifier.weight(1f), colors = listOf(Hud.yellow, hudAccent),
                )
            }
        }
        item(key = "ram") {
            HudKeyValues(listOf(
                stringResource(R.string.hud_ram) to if (total > 0) gb(total) else "--",
                stringResource(R.string.hud_used) to if (total > 0) gb(used) else "--",
                stringResource(R.string.hud_available) to if (total > 0) gb(avail) else "--",
                stringResource(R.string.hud_cached) to (live.memCachedKb?.let { gb(it) } ?: "--"),
                stringResource(R.string.hud_zram) to if (swapTotal > 0) "${gb(swapUsed)} / ${gb(swapTotal)}" + (tunables.zramAlgo.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") else "--",
            ))
        }
        item(key = "h_tun") { HudSectionTitle(stringResource(R.string.hud_kernel_tunables)) }
        item(key = "tun") {
            HudKeyValues(listOf(
                stringResource(R.string.hud_swappiness) to tunables.swappiness,
                stringResource(R.string.hud_vfs) to tunables.vfs,
                stringResource(R.string.hud_readahead) to tunables.readAhead,
                stringResource(R.string.hud_io) to tunables.io,
            ))
        }
        item(key = "trim") {
            HudGroup(rows = listOf<@Composable () -> Unit>({
                HudRow(
                    stringResource(R.string.hud_trim_now), icon = Icons.Rounded.CleaningServices,
                    subtitle = stringResource(R.string.hud_trim_now_sub),
                    onClick = {
                        scope.launch {
                            // Only cached (already stopped) processes; running apps are not touched.
                            withContext(Dispatchers.IO) { Shell.cmd("am kill-all").exec() }
                            done = true
                        }
                    },
                )
            }))
        }
    }
}

/* ---------------- Frames engine ---------------- */

@Composable
fun HudFramesEngineScreen(navController: NavController, vm: HomeViewModel = viewModel(), appVm: AppSettingsViewModel = viewModel()) {
    val ui by vm.uiState.collectAsState()
    LiveStatsPoller(vm, true, ui.profileLoaded)
    LaunchedEffect(Unit) { appVm.loadConfig() }
    val samples = remember { mutableStateListOf<Float>() }
    var unavailable by remember { mutableStateOf(false) }
    var fpsgo by remember { mutableStateOf<String?>(null) }
    var frameTime by remember { mutableStateOf<Float?>(null) }
    var measuredPkg by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(ui.profileLoaded) {
        if (!ui.profileLoaded) return@LaunchedEffect
        fpsgo = withContext(Dispatchers.IO) { RootUtils.readRootFile("/sys/kernel/fpsgo/common/force_onoff")?.trim() }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Its own root shell; closed when the page stops so time stats do not stay on.
            val meter = zx.nextcore.overlay.FrameMeter()
            try {
                var misses = 0
                while (true) {
                    val stats = withContext(Dispatchers.IO) { meter.sample(ui.runningGamePkg) }
                    stats.fps?.let {
                        samples.add(it.coerceIn(0f, 240f))
                        while (samples.size > 120) samples.removeAt(0)
                    }
                    frameTime = stats.frameTimeMs
                    measuredPkg = stats.pkg
                    if (stats.fps == null) misses++ else misses = 0
                    unavailable = misses >= 3
                    delay(1000)
                }
            } finally {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { meter.close() }
            }
        }
    }

    val now = samples.lastOrNull()
    val avg = samples.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    val low = samples.takeIf { it.size >= 5 }?.sorted()?.let { it[(it.size / 100).coerceAtLeast(0)] }
    val stability = if (avg != null && avg > 1f && samples.size >= 5) {
        val sd = sqrt(samples.map { (it - avg) * (it - avg) }.average()).toFloat()
        (100f - sd / avg * 100f).coerceIn(0f, 100f)
    } else null
    val targets = appVm.fullConfig.filter { (_, c) -> c.resolution_downscale != "default" }

    HudPage(title = stringResource(R.string.hud_fps_engine), onBack = { navController.safePopBackStack() }) {
        item(key = "note") { HudNote(stringResource(R.string.hud_fps_note)) }
        if (unavailable) item(key = "na") { HudEmpty(stringResource(R.string.hud_fps_unavailable)) }
        item(key = "big") {
            HudCard(Modifier.fillMaxWidth(), accent = true) {
                Text(stringResource(R.string.hud_fps_now), color = Hud.muted, fontSize = 12.sp)
                Text(now?.let { one(it) } ?: "--", color = hudAccent, fontFamily = BrandFontFamily, fontWeight = FontWeight.Black, fontSize = 52.sp)
                Text(
                    listOfNotNull(frameTime?.let { one(it) + " ms" }, measuredPkg).joinToString(" · "),
                    color = Hud.muted, fontSize = 12.sp, maxLines = 1,
                )
            }
        }
        item(key = "chart") {
            HudLineChart(
                listOf(ChartSeries("FPS", samples.toList(), hudAccent, filled = true)),
                height = 140.dp,
                xLabels = listOf("-2m", "-1m", "0"),
            )
        }
        item(key = "stats") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HudStat(stringResource(R.string.hud_fps_avg), avg?.let { one(it) } ?: "--", Modifier.weight(1f))
                    HudStat(stringResource(R.string.hud_fps_low), low?.let { one(it) } ?: "--", Modifier.weight(1f))
                    HudStat(stringResource(R.string.hud_fps_stability), stability?.let { one(it) + "%" } ?: "--", Modifier.weight(1f), highlight = true)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HudStat(stringResource(R.string.hud_refresh_now), ui.live.refreshHz?.let { "$it Hz" } ?: "--", Modifier.weight(1f))
                    HudStat(stringResource(R.string.hud_refresh_max), ui.live.maxRefreshHz?.let { "$it Hz" } ?: "--", Modifier.weight(1f))
                    if (fpsgo != null) HudStat("FPSGO", if (fpsgo == "0") stringResource(R.string.hud_off) else stringResource(R.string.hud_on), Modifier.weight(1f))
                }
            }
        }
        item(key = "h_targets") { HudSectionTitle(stringResource(R.string.hud_fps_targets)) }
        item(key = "targets") {
            if (targets.isEmpty()) HudEmpty(stringResource(R.string.hud_fps_targets_empty))
            else HudKeyValues(targets.map { (pkg, c) -> pkg.substringAfterLast('.') to "${c.resolution_fps} FPS · ${c.resolution_downscale}×" })
        }
    }
}

