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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package zx.azenith.ui.mainscreens


import android.hardware.display.DisplayManager
import android.os.Build
import android.os.SystemClock
import android.view.Display
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import zx.azenith.R
import zx.azenith.ui.component.*
import zx.azenith.ui.theme.BrandFontFamily
import zx.azenith.ui.util.ClusterStat
import zx.azenith.ui.util.LiveStats
import zx.azenith.ui.viewmodel.HomeViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt


private fun freq(khz: Long): String = when {
    khz <= 0L -> "--"
    khz >= 1_000_000L -> String.format(Locale.US, "%.2f GHz", khz / 1_000_000f)
    else -> "${khz / 1000} MHz"
}

private fun gb(kb: Long): String = String.format(Locale.US, "%.1f", kb / 1048576f)

@Composable
fun MonitorScreen(
    navController: NavController? = null,
    isVisible: Boolean = true,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    LiveStatsPoller(viewModel, isVisible, uiState.profileLoaded)
    val live = uiState.live
    val profile = uiState.deviceProfile
    val cs = MaterialTheme.colorScheme

    Scaffold(
        topBar = { NcPageHeader(subtitle = stringResource(R.string.nav_monitor)) },
        containerColor = cs.surface
    ) { innerPadding ->
        NcSheet(topPadding = innerPadding.calculateTopPadding()) {
            // One column on phones; tablets fit more phone-sized cards per row
            // instead of stretching each card across the screen.
            LazyVerticalStaggeredGrid(
                columns = ncGridCells,
                modifier = Modifier.fillMaxSize(),
                contentPadding = ncSheetListPadding(),
                horizontalArrangement = Arrangement.spacedBy(NcGridGap),
                verticalItemSpacing = NcGridGap
            ) {
                // SoC hero
                item(key = "soc", span = StaggeredGridItemSpan.FullLine) {
                    NcWatermarkCard(
                        watermark = Icons.Rounded.Memory,
                        onClick = { navController?.navigate("devicecard") { launchSingleTop = true } }
                    ) {
                        Text(
                            profile.socModel.uppercase().ifEmpty { Build.HARDWARE.uppercase() },
                            fontFamily = BrandFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 34.sp
                        )
                        Text(
                            stringResource(R.string.nc_by, profile.socVendor.ifEmpty { socManufacturerCompat() }),
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.onSecondaryContainer.copy(alpha = 0.75f)
                        )
                        FlowRow(
                            Modifier.padding(top = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val cores = profile.clusters.sumOf { it.cores }
                            if (cores > 0) BigChip(stringResource(R.string.nc_cores, cores), cs.tertiary, cs.onTertiary)
                            if (live.peakKhz > 0) BigChip(stringResource(R.string.nc_peak, freq(live.peakKhz)), cs.primary, cs.onPrimary)
                            live.cpuTempC?.let { BigChip(String.format(Locale.US, "%.1f °C", it), cs.primary, cs.onPrimary) }
                        }
                        // In landscape on a tablet the four facts sit in one row; narrower
                        // screens keep two per row so the core list has room to wrap.
                        val facts = listOf(
                            stringResource(R.string.nc_architecture) to (Build.SUPPORTED_ABIS.firstOrNull() ?: "—"),
                            stringResource(R.string.nc_governor) to live.governor.ifEmpty { "—" },
                            stringResource(R.string.nc_cores_label) to profile.clusterSummary.ifEmpty { "—" },
                            stringResource(R.string.nc_sensor) to profile.cpuTempLabel.substringAfter("· ", "").ifEmpty { "—" },
                        )
                        facts.chunked(if (LocalConfiguration.current.screenWidthDp >= 1000) 4 else 2).forEachIndexed { i, row ->
                            Row(Modifier.padding(top = if (i == 0) 16.dp else 12.dp)) {
                                row.forEach { (label, value) -> Fact(label, value, Modifier.weight(1f)) }
                            }
                        }
                    }
                }

                item(key = "coresHeader", span = StaggeredGridItemSpan.FullLine) {
                    NcHelpSection(stringResource(R.string.nc_cores_section), stringResource(R.string.nc_help_cpu))
                }
                // Biggest cores first, like the mockup
                val clusters = live.clusters.reversed()
                items(clusters.size, key = { "cluster$it" }) { i -> ClusterCard(clusters[i], live.cpuTempC) }

                item(key = "gpu") {
                    Column {
                        NcHelpSection(stringResource(R.string.nc_gpu), stringResource(R.string.nc_help_gpu))
                        NcWatermarkCard(watermark = Icons.Rounded.DeveloperBoard) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                WavyRing((live.gpuLoad ?: 0) / 100f, live.gpuLoad?.let { "$it%" } ?: "--", cs.primary)
                                Column {
                                    Text(profile.gpuName.ifEmpty { "GPU" }, style = MaterialTheme.typography.bodyMedium, color = cs.onSecondaryContainer.copy(alpha = 0.75f))
                                    BigNumber(live.gpuMhz?.toString() ?: "--", "MHz")
                                    live.gpuMaxMhz?.let {
                                        BigChip(stringResource(R.string.nc_max_freq, "$it MHz"), cs.tertiary, cs.onTertiary, Modifier.padding(top = 8.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "mem") {
                    Column {
                        NcHelpSection(stringResource(R.string.nc_memory), stringResource(R.string.nc_help_memory))
                        NcWatermarkCard(watermark = Icons.Rounded.SdStorage) {
                            val total = live.memTotalKb
                            val avail = live.memAvailKb
                            if (total != null && avail != null && total > 0) {
                                val used = total - avail
                                MemRow(
                                    label = stringResource(R.string.nc_ram),
                                    fraction = used.toFloat() / total,
                                    value = "${gb(used)} / ${gb(total)} GB",
                                    free = stringResource(R.string.nc_free, "${gb(avail)} GB"),
                                    color = cs.primary
                                )
                            }
                            val st = live.swapTotalKb
                            val sf = live.swapFreeKb
                            if (st != null && sf != null && st > 0) {
                                Spacer(Modifier.height(16.dp))
                                MemRow(
                                    label = stringResource(R.string.nc_swap),
                                    fraction = (st - sf).toFloat() / st,
                                    value = "${gb(st - sf)} / ${gb(st)} GB",
                                    free = stringResource(R.string.nc_free, "${gb(sf)} GB"),
                                    color = cs.tertiary
                                )
                            }
                            if (total == null) Text(stringResource(R.string.nc_not_available))
                        }
                    }
                }

                if (live.memTotalKb != null) {
                    item(key = "memDetail") {
                        Column {
                            NcHelpSection(stringResource(R.string.nc_mem_breakdown), stringResource(R.string.nc_help_mem_breakdown))
                            MemBreakdownCard(live)
                        }
                    }
                }

                item(key = "batt") {
                    Column(verticalArrangement = Arrangement.spacedBy(NcGridGap)) {
                        NcHelpSection(stringResource(R.string.nc_battery), stringResource(R.string.nc_help_battery))
                        NcWatermarkCard(watermark = Icons.Rounded.BatteryFull) {
                            Text(
                                live.batteryPct?.let { "$it%" } ?: "--",
                                fontFamily = BrandFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 46.sp,
                                style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)
                            )
                            Text(batteryStatusText(live.batteryStatus), style = MaterialTheme.typography.bodyLarge, color = cs.onSecondaryContainer.copy(alpha = 0.75f))
                            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (live.batteryHealth.isNotBlank()) BigChip(live.batteryHealth, cs.primary, cs.onPrimary)
                                live.batteryTempC?.let { BigChip(String.format(Locale.US, "%.1f °C", it), cs.tertiary, cs.onTertiary) }
                            }
                        }
                        CurrentCard(live, uiState.currentHistory)
                    }
                }

                item(key = "disp") {
                    Column {
                        NcHelpSection(stringResource(R.string.nc_display), stringResource(R.string.nc_help_display))
                        DisplayCard(live.refreshHz, live.maxRefreshHz)
                    }
                }
            }
        }
    }
}

/** Charging / discharging speed with the live current chart. */
@Composable
private fun CurrentCard(live: LiveStats, history: List<Int>) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(30.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            val ma = live.batteryCurrentMa
            val charging = live.batteryStatus.equals("Charging", true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (charging) R.string.nc_charge_speed else R.string.nc_discharge_speed),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                    )
                    Text(
                        ma?.let { "$it mA" } ?: "--",
                        fontFamily = BrandFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp,
                        color = cs.primary,
                        style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)
                    )
                }
                val mv = live.batteryVoltageMv
                if (ma != null && mv != null) {
                    BigChip(String.format(Locale.US, "%.2f W", abs(ma) * mv / 1_000_000f), cs.primary, cs.onPrimary)
                }
            }
            CurrentChart(history, cs.primary, Modifier.fillMaxWidth().height(96.dp).padding(top = 12.dp))
            if (history.size >= 2) {
                Row(Modifier.padding(top = 8.dp)) {
                    Fact(stringResource(R.string.nc_lowest), "${history.min()} mA", Modifier.weight(1f), onCard = false)
                    Fact(stringResource(R.string.nc_highest), "${history.max()} mA", Modifier.weight(1f), onCard = false)
                }
            }
            Row(Modifier.padding(top = 12.dp)) {
                Fact(stringResource(R.string.nc_voltage), live.batteryVoltageMv?.let { "$it mV" } ?: "—", Modifier.weight(1f), onCard = false)
                Fact(stringResource(R.string.nc_cycles), live.batteryCycles?.toString() ?: "—", Modifier.weight(1f), onCard = false)
            }
            Row(Modifier.padding(top = 12.dp)) {
                Fact(stringResource(R.string.nc_design_capacity), live.batteryDesignMah?.let { "$it mAh" } ?: "—", Modifier.weight(1f), onCard = false)
                val up = SystemClock.elapsedRealtime() / 60000
                Fact(stringResource(R.string.nc_uptime), stringResource(R.string.nc_hours_minutes, (up / 60).toInt(), (up % 60).toInt()), Modifier.weight(1f), onCard = false)
            }
        }
    }
}

/** Where the RAM goes, from /proc/meminfo, each as a thin bar against total RAM. */
@Composable
private fun MemBreakdownCard(live: LiveStats) {
    val cs = MaterialTheme.colorScheme
    val total = live.memTotalKb ?: return
    val rows = listOf(
        stringResource(R.string.nc_mem_cached) to live.memCachedKb,
        stringResource(R.string.nc_mem_buffers) to live.memBuffersKb,
        stringResource(R.string.nc_mem_active) to live.memActiveKb,
        stringResource(R.string.nc_mem_inactive) to live.memInactiveKb,
        stringResource(R.string.nc_mem_slab) to live.memSlabKb,
    ).filter { it.second != null }
    Surface(shape = RoundedCornerShape(30.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { (label, kb) ->
                val fraction = (kb!!.toFloat() / total).coerceIn(0f, 1f)
                Column {
                    Row {
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(
                            if (kb >= 1048576L) "${gb(kb)} GB" else "${kb / 1024} MB",
                            style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Ltr),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp),
                        color = cs.primary,
                        trackColor = cs.surfaceContainerHighest,
                        drawStopIndicator = {}
                    )
                }
            }
        }
    }
}

private fun socManufacturerCompat(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER else Build.MANUFACTURER

@Composable
private fun batteryStatusText(status: String): String = when (status.lowercase()) {
    "charging" -> stringResource(R.string.nc_status_charging)
    "discharging" -> stringResource(R.string.nc_status_discharging)
    "full" -> stringResource(R.string.nc_status_full)
    "not charging" -> stringResource(R.string.nc_status_not_charging)
    else -> status
}

@Composable
private fun BigChip(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.ContentOrLtr),
            color = content,
            maxLines = 1
        )
    }
}

@Composable
private fun BigNumber(value: String, unit: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            value, fontFamily = BrandFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp,
            style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)
        )
        Text(" $unit", fontFamily = BrandFontFamily, fontSize = 17.sp, modifier = Modifier.padding(bottom = 5.dp))
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier, onCard: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.padding(end = 8.dp)) {
        Text(
            label, style = MaterialTheme.typography.bodySmall,
            color = if (onCard) cs.onSecondaryContainer.copy(alpha = 0.7f) else cs.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.ContentOrLtr),
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WavyRing(progress: Float, label: String, color: Color, size: Int = 104) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "wavyRing")
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            progress = { animated },
            modifier = Modifier.fillMaxSize(),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
        Text(
            label, fontFamily = BrandFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
            style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)
        )
    }
}

@Composable
private fun MemRow(label: String, fraction: Float, value: String, free: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        WavyRing(fraction, "${(fraction * 100).toInt()}%", color, size = 96)
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f))
            Text(
                value, fontFamily = BrandFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp,
                style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)
            )
            BigChip(free, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary, Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun ClusterCard(c: ClusterStat, tempC: Float?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val target = if (c.maxKhz > 0) (c.curKhz.toFloat() / c.maxKhz).coerceIn(0f, 1f) else 0f
    val progress by animateFloatAsState(target, label = "clusterCard")
    Surface(shape = RoundedCornerShape(30.dp), color = cs.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NcIconTile(Icons.Rounded.Memory, size = 46.dp)
                Text(
                    c.label,
                    style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr),
                    color = cs.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                tempC?.let { BigChip(String.format(Locale.US, "%.1f °C", it), cs.primary, cs.onPrimary) }
                if (c.governor.isNotBlank()) BigChip(c.governor, cs.tertiary, cs.onTertiary)
            }
            Text(
                freq(c.curKhz),
                fontFamily = BrandFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 36.sp,
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)
            )
            LinearWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                color = cs.primary,
                trackColor = cs.surfaceContainerHighest
            )
            Row {
                Fact(stringResource(R.string.nc_min), freq(c.minLimitKhz), Modifier.weight(1f), onCard = false)
                Fact(stringResource(R.string.nc_max), freq(if (c.maxLimitKhz > 0) c.maxLimitKhz else c.maxKhz), Modifier.weight(1f), onCard = false)
            }
        }
    }
}

/** Battery current history as a smooth area chart. */
@Composable
private fun CurrentChart(values: List<Int>, color: Color, modifier: Modifier) {
    if (values.size < 2) {
        Spacer(modifier)
        return
    }
    Canvas(modifier) {
        val min = values.min().toFloat()
        val max = values.max().toFloat()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        // Drawn left-to-right (oldest to newest) regardless of layout direction
        val pts = values.mapIndexed { i, v ->
            Offset(i * stepX, size.height * 0.1f + (1f - (v - min) / range) * size.height * 0.8f)
        }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val p0 = pts[i - 1]
                val p1 = pts[i]
                val mx = (p0.x + p1.x) / 2f
                cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(pts.last().x, size.height)
            lineTo(pts.first().x, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun DisplayCard(refresh: Int?, maxRefresh: Int?) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val info = remember {
        val dm = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        val mode = dm?.mode
        val metrics = context.resources.displayMetrics
        val w = mode?.physicalWidth ?: metrics.widthPixels
        val h = mode?.physicalHeight ?: metrics.heightPixels
        val inches = sqrt((w / metrics.xdpi).let { it * it } + (h / metrics.ydpi).let { it * it })
        val hdr = dm?.hdrCapabilities?.supportedHdrTypes?.toList()?.mapNotNull {
            when (it) {
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
                Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                else -> null
            }
        }.orEmpty()
        Pair<List<String>, List<String>>(
            listOf("${minOf(w, h)}×${maxOf(w, h)}", String.format(Locale.US, "%.1f", inches), "${metrics.densityDpi} dpi"),
            hdr
        )
    }
    NcWatermarkCard(watermark = Icons.Rounded.TabletAndroid) {
        Text(
            info.first[0], fontFamily = BrandFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp,
            style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)
        )
        Text(stringResource(R.string.nc_inches, info.first[1]), style = MaterialTheme.typography.bodyLarge, color = cs.onSecondaryContainer.copy(alpha = 0.75f))
        FlowRow(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            refresh?.let { BigChip("${it}Hz", cs.inverseSurface, cs.inverseOnSurface) }
            maxRefresh?.let { if (it != refresh) BigChip(stringResource(R.string.nc_max_hz, it), cs.inverseSurface, cs.inverseOnSurface) }
            BigChip(info.first[2], cs.inverseSurface, cs.inverseOnSurface)
            info.second.forEach { BigChip(it, cs.tertiary, cs.onTertiary) }
        }
    }
}
