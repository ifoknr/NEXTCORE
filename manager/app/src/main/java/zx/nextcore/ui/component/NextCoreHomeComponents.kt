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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package zx.nextcore.ui.component


import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.outlined.EnergySavingsLeaf
import androidx.compose.material.icons.outlined.OfflineBolt
import androidx.compose.material.icons.outlined.Water
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zx.nextcore.R
import zx.nextcore.ui.theme.BrandFontFamily
import zx.nextcore.ui.util.LiveStats
import zx.nextcore.ui.util.SupportLevel
import java.util.Locale


/* ---------- Colors that are not part of the M3 scheme ---------- */

/** Green "OK" tone that reads on both light and dark surfaces. */
@Composable
fun ncOkContainer(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF1F3A26) else Color(0xFFCDEFD4)

@Composable
fun ncOnOkContainer(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF9BE3A8) else Color(0xFF0E5223)

@Composable
fun ncWarnContainer(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF3D3410) else Color(0xFFF6E7A6)

@Composable
fun ncOnWarnContainer(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFF0D46A) else Color(0xFF5A4700)


/* ---------- Basic building blocks ---------- */

@Composable
fun NcSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 2.dp)
    )
}

/** Icon inside an expressive shape (cookie, flower, …). */
@Composable
fun NcShapeIcon(
    icon: ImageVector,
    shape: Shape,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    size: Int = 36,
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
fun NcChip(
    text: String,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) Icon(icon, null, tint = content, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
    }
}

/** Number with a smaller unit, e.g. "780 MHz". Numbers always use the brand font. */
@Composable
fun NcValueText(value: String, unit: String = "", big: Boolean = true, color: Color = MaterialTheme.colorScheme.onSurface) {
    val numberSize = if (big) 26.sp else 18.sp
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontFamily = BrandFontFamily, fontWeight = FontWeight.SemiBold, fontSize = numberSize)) {
                append(value)
            }
            if (unit.isNotEmpty()) {
                withStyle(SpanStyle(fontFamily = BrandFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp)) {
                    append(" $unit")
                }
            }
        },
        color = color,
        maxLines = 1,
        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr)
    )
}


/* ---------- Hero status ---------- */

@Composable
fun NcHeroCard(
    installed: Boolean,
    running: Boolean,
    pid: String,
    version: String,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (running) cs.primaryContainer else cs.errorContainer, label = "heroContainer"
    )
    val onContainer = if (running) cs.onPrimaryContainer else cs.onErrorContainer
    val title = when {
        !installed -> stringResource(R.string.module_not_installed)
        running -> stringResource(R.string.nc_service_running)
        else -> stringResource(R.string.nc_service_stopped)
    }
    val detail = listOfNotNull(
        pid.takeIf { running && it.isNotBlank() }?.let { "PID $it" },
        version.takeIf { it.isNotBlank() }
    ).joinToString(" · ")

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = container
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(if (running) cs.primary else cs.error),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "N",
                    fontFamily = BrandFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = if (running) cs.onPrimary else cs.onError
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = onContainer, fontWeight = FontWeight.SemiBold)
                if (detail.isNotEmpty()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                        color = onContainer.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun NcStatusChips(
    support: SupportLevel,
    socModel: String,
    rootGranted: Boolean,
    autoMode: Boolean,
    onSupportClick: () -> Unit,
    onAutoClick: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (support) {
            SupportLevel.FULL -> NcChip(
                text = stringResource(R.string.nc_support_full) + if (socModel.isNotBlank()) " · $socModel" else "",
                container = ncOkContainer(), content = ncOnOkContainer(),
                icon = Icons.Rounded.Check, onClick = onSupportClick
            )
            SupportLevel.PARTIAL -> NcChip(
                text = stringResource(R.string.nc_support_partial),
                container = ncWarnContainer(), content = ncOnWarnContainer(),
                icon = Icons.Rounded.ErrorOutline, onClick = onSupportClick
            )
            SupportLevel.UNKNOWN -> NcChip(
                text = stringResource(R.string.nc_support_unknown),
                icon = Icons.Rounded.ErrorOutline, onClick = onSupportClick
            )
        }
        if (rootGranted) {
            NcChip(stringResource(R.string.nc_root_ok), ncOkContainer(), ncOnOkContainer(), Icons.Rounded.Check)
        } else {
            NcChip(
                stringResource(R.string.nc_root_no),
                MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer,
                Icons.Rounded.ErrorOutline
            )
        }
        NcChip(
            text = stringResource(if (autoMode) R.string.nc_auto_mode else R.string.nc_manual_mode),
            container = if (autoMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            content = if (autoMode) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onAutoClick
        )
    }
}


/* ---------- Profile selector: radio list with descriptions ---------- */

private data class NcProfile(val value: String, val title: Int, val desc: Int, val icon: ImageVector)

private val ncProfiles = listOf(
    NcProfile("3", R.string.nc_profile_eco, R.string.nc_profile_eco_desc, Icons.Outlined.EnergySavingsLeaf),
    NcProfile("2", R.string.nc_profile_balanced, R.string.nc_profile_balanced_desc, Icons.Outlined.Water),
    NcProfile("1", R.string.nc_profile_perf, R.string.nc_profile_perf_desc, Icons.Outlined.OfflineBolt),
)

@Composable
fun NcProfileList(
    current: String,
    autoMode: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        ncProfiles.forEachIndexed { index, p ->
            val selected = p.value == current
            val container by animateColorAsState(
                if (selected) cs.primaryContainer else cs.surfaceContainer, label = "profileContainer"
            )
            val onContainer = if (selected) cs.onPrimaryContainer else cs.onSurface
            val big = 22.dp
            val small = 6.dp
            val shape = when (index) {
                0 -> RoundedCornerShape(topStart = big, topEnd = big, bottomStart = small, bottomEnd = small)
                ncProfiles.lastIndex -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = big, bottomEnd = big)
                else -> RoundedCornerShape(small)
            }
            Surface(
                onClick = { onSelect(p.value) },
                shape = if (selected) RoundedCornerShape(big) else shape,
                color = container,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NcShapeIcon(
                        icon = p.icon,
                        shape = CircleShape,
                        container = if (selected) cs.primary.copy(alpha = 0.22f) else cs.surfaceContainerHighest,
                        content = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                        size = 34
                    )
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(p.title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = onContainer)
                        Text(
                            stringResource(p.desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) onContainer.copy(alpha = 0.85f) else cs.onSurfaceVariant
                        )
                    }
                    RadioButton(
                        selected = selected,
                        onClick = { onSelect(p.value) },
                        colors = RadioButtonDefaults.colors(selectedColor = cs.primary)
                    )
                }
            }
        }
        if (autoMode) {
            Text(
                stringResource(R.string.nc_profile_auto_hint),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}


/* ---------- Live monitoring ---------- */

private fun ghz(khz: Long): String = String.format(Locale.US, "%.2f", khz / 1_000_000f)

@Composable
fun NcCpuCard(live: LiveStats, cpuCount: Int, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = cs.surfaceContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NcShapeIcon(Icons.Rounded.Memory, MaterialShapes.Cookie7Sided.toShape(), cs.tertiaryContainer, cs.onTertiaryContainer)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.nc_cpu_freq), style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                    val sub = listOfNotNull(
                        live.governor.takeIf { it.isNotBlank() },
                        if (cpuCount > 0) stringResource(R.string.nc_cores, cpuCount) else null
                    ).joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            if (live.clusters.isEmpty()) {
                Text(stringResource(R.string.nc_not_available), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            live.clusters.forEach { c ->
                val target = if (c.maxKhz > 0) (c.curKhz.toFloat() / c.maxKhz).coerceIn(0f, 1f) else 0f
                val progress by animateFloatAsState(
                    target, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow), label = "clusterBar"
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        c.label,
                        style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr),
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.width(64.dp),
                        maxLines = 1
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.weight(1f).height(8.dp),
                        color = cs.primary,
                        trackColor = cs.surfaceContainerHighest,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {}
                    )
                    Text(
                        "${ghz(c.curKhz)} GHz",
                        fontFamily = BrandFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = cs.onSurface,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(72.dp),
                        style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr)
                    )
                }
            }
        }
    }
}

@Composable
fun NcMetricTile(
    icon: ImageVector,
    iconShape: Shape,
    label: String,
    value: String,
    unit: String,
    sub: String,
    modifier: Modifier = Modifier,
    iconContainer: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconContent: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    progress: Float? = null,
) {
    val cs = MaterialTheme.colorScheme
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), color = cs.surfaceContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(contentAlignment = Alignment.Center) {
                if (progress != null) {
                    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "tileRing")
                    CircularWavyProgressIndicator(
                        progress = { animated },
                        modifier = Modifier.size(46.dp),
                        color = iconContainer,
                        trackColor = cs.surfaceContainerHighest
                    )
                    Icon(icon, null, tint = cs.onSurface, modifier = Modifier.size(18.dp))
                } else {
                    NcShapeIcon(icon, iconShape, iconContainer, iconContent, size = 38)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1)
            NcValueText(value, unit)
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** CPU temperature, GPU, display and battery tiles in a 2×2 grid. */
@Composable
fun NcMonitorGrid(
    live: LiveStats,
    cpuTempLabel: String,
    gpuName: String,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val na = stringResource(R.string.nc_not_available)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val t = live.cpuTempC
            NcMetricTile(
                icon = Icons.Rounded.Thermostat,
                iconShape = MaterialShapes.Sunny.toShape(),
                label = stringResource(R.string.nc_cpu_temp),
                value = t?.let { "%.0f".format(Locale.US, it) } ?: "--",
                unit = if (t != null) "°C" else "",
                sub = if (t != null) cpuTempLabel.substringAfter("· ", cpuTempLabel) else na,
                progress = t?.let { (it - 20f) / 80f },
                iconContainer = if ((t ?: 0f) >= 75f) cs.error else cs.primary,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            val load = live.gpuLoad
            NcMetricTile(
                icon = Icons.Rounded.DeveloperBoard,
                iconShape = MaterialShapes.Cookie4Sided.toShape(),
                label = stringResource(R.string.nc_gpu),
                value = live.gpuMhz?.toString() ?: "--",
                unit = if (live.gpuMhz != null) "MHz" else "",
                sub = listOfNotNull(
                    gpuName.takeIf { it.isNotBlank() },
                    load?.let { stringResource(R.string.nc_load, it) }
                ).joinToString(" · ").ifEmpty { if (live.gpuMhz == null) na else "" },
                iconContainer = cs.tertiaryContainer,
                iconContent = cs.onTertiaryContainer,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val pct = live.batteryPct
            val battSub = listOfNotNull(
                live.batteryTempC?.let { "%.0f°C".format(Locale.US, it) },
                live.batteryCurrentMa?.let { "$it mA" }
            ).joinToString(" · ")
            NcMetricTile(
                icon = Icons.Filled.BatteryChargingFull,
                iconShape = MaterialShapes.Cookie6Sided.toShape(),
                label = stringResource(R.string.nc_battery),
                value = pct?.toString() ?: "--",
                unit = if (pct != null) "%" else "",
                sub = battSub.ifEmpty { na },
                iconContainer = cs.primaryContainer,
                iconContent = cs.onPrimaryContainer,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            NcMetricTile(
                icon = Icons.Rounded.AspectRatio,
                iconShape = MaterialShapes.Flower.toShape(),
                label = stringResource(R.string.nc_display),
                value = live.refreshHz?.toString() ?: "--",
                unit = if (live.refreshHz != null) "Hz" else "",
                sub = live.maxRefreshHz?.let { stringResource(R.string.nc_max_hz, it) } ?: na,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}


/* ---------- Lists ---------- */

/** One row of a grouped list; [position] picks the connected corner shape. */
@Composable
fun NcListRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    position: Int = 0,
    count: Int = 1,
    trailing: (@Composable () -> Unit)? = null,
    ltrSubtitle: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val big = 22.dp
    val small = 6.dp
    val shape = when {
        count == 1 -> RoundedCornerShape(big)
        position == 0 -> RoundedCornerShape(topStart = big, topEnd = big, bottomStart = small, bottomEnd = small)
        position == count - 1 -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = big, bottomEnd = big)
        else -> RoundedCornerShape(small)
    }
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                NcShapeIcon(icon, CircleShape, cs.secondaryContainer, cs.onSecondaryContainer, size = 34)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall.let {
                            if (ltrSubtitle) it.copy(textDirection = TextDirection.ContentOrLtr) else it
                        },
                        color = cs.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            when {
                trailing != null -> trailing()
                onClick != null -> Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                    tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp)
                )
            }
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) { content() }
    } else {
        Surface(shape = shape, color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) { content() }
    }
}

/** Small round status mark used as a list trailing element. */
@Composable
fun NcStatusMark(ok: Boolean) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (ok) ncOkContainer() else ncWarnContainer()),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (ok) Icons.Rounded.Check else Icons.Rounded.ErrorOutline, null,
            tint = if (ok) ncOnOkContainer() else ncOnWarnContainer(),
            modifier = Modifier.size(16.dp)
        )
    }
}
