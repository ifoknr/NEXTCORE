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

package zx.nextcore.ui.subscreens


import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zx.nextcore.R
import zx.nextcore.ui.component.*
import zx.nextcore.ui.navigation.safePopBackStack
import zx.nextcore.ui.util.DeviceMonitor
import zx.nextcore.ui.util.DeviceProfile
import zx.nextcore.ui.util.PropertyUtils
import zx.nextcore.ui.util.SupportLevel


/** What each vendor's chipset module tunes, for the "applied tweaks" list. */
private fun socTweakSummary(vendor: String): String = when (vendor) {
    "MediaTek" -> "FPSGO · GED · Mali · DVFSRC"
    "Snapdragon" -> "Adreno · KGSL · Bus DCVS"
    "Exynos" -> "Mali · DVFS"
    "Unisoc" -> "GPU · DVFS"
    "Tensor" -> "Mali · DVFS"
    else -> ""
}

@Composable
fun DeviceCardScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var profile by remember { mutableStateOf(DeviceProfile()) }
    var loaded by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var bypassPath by remember { mutableStateOf("") }
    val scannedMsg = stringResource(R.string.nc_rescan_done)

    suspend fun load() {
        val (p, b) = withContext(Dispatchers.IO) {
            DeviceMonitor.loadProfile() to PropertyUtils.get("persist.sys.nextcoreconf.bypasspath")
        }
        profile = p
        bypassPath = b
        loaded = true
    }

    LaunchedEffect(Unit) { load() }

    val rescan: () -> Unit = {
        if (!scanning) {
            scanning = true
            scope.launch {
                withContext(Dispatchers.IO) {
                    Shell.cmd("sh -c '. /data/adb/modules/nextcore/devprobe.sh && devprobe_run quiet'").exec()
                }
                load()
                scanning = false
                snackbar.showSnackbar(scannedMsg)
            }
        }
    }

    Scaffold(
        topBar = { DeviceCardTopBar(onBack = { navController.safePopBackStack() }, onRescan = rescan, scanning = scanning) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { inner ->
        NcSheet(topPadding = inner.calculateTopPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = ncSheetListPadding(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SupportBanner(profile, loaded) }

            item { NcSectionHeader(stringResource(R.string.nc_device_soc_header)) }
            item {
                val rows = buildList {
                    add(stringResource(R.string.nc_device) to listOf(
                        profile.raw["device_model"].orEmpty().ifEmpty { Build.MODEL },
                        profile.raw["device_codename"].orEmpty().ifEmpty { Build.DEVICE }
                    ).let { (m, c) -> "$m ($c)" })
                    add(stringResource(R.string.nc_soc) to profile.socModel.uppercase().ifEmpty { "—" })
                    add(stringResource(R.string.nc_vendor) to profile.socVendor.ifEmpty { stringResource(R.string.nc_unknown_soc) })
                    add(stringResource(R.string.nc_cores_label) to profile.clusterSummary.ifEmpty { "—" })
                    add(stringResource(R.string.nc_android) to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    rows.forEachIndexed { i, (k, v) ->
                        NcListRow(
                            title = k,
                            position = i, count = rows.size,
                            trailing = {
                                Text(
                                    v,
                                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.ContentOrLtr),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }
            }

            item { NcSectionHeader(stringResource(R.string.nc_sensors_header)) }
            item {
                val notFound = stringResource(R.string.nc_not_found)
                val bypassOk = bypassPath.isNotBlank() && bypassPath != "NEED_SETUP" && bypassPath != "UNSUPPORTED"
                val sensors = listOf(
                    Triple(Icons.Rounded.Thermostat, stringResource(R.string.nc_cpu_temp), profile.cpuTempLabel.ifEmpty { notFound }) to profile.cpuTempPath.isNotEmpty(),
                    Triple(Icons.Rounded.DeveloperBoard, stringResource(R.string.nc_gpu), profile.gpuFreqPath.removePrefix("/sys/class/").ifEmpty { notFound }) to profile.gpuFreqPath.isNotEmpty(),
                    Triple(Icons.Rounded.ElectricBolt, stringResource(R.string.nc_batt_current), profile.batteryCurrentPath.removePrefix("/sys/class/").ifEmpty { notFound }) to profile.batteryCurrentPath.isNotEmpty(),
                    Triple(
                        Icons.Rounded.Cable, stringResource(R.string.nc_bypass),
                        when {
                            bypassOk -> bypassPath
                            bypassPath == "UNSUPPORTED" -> stringResource(R.string.status_not_supported)
                            else -> stringResource(R.string.nc_bypass_need_setup)
                        }
                    ) to bypassOk,
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    sensors.forEachIndexed { i, (t, ok) ->
                        NcListRow(
                            title = t.second,
                            subtitle = t.third,
                            icon = t.first,
                            position = i, count = sensors.size,
                            ltrSubtitle = true,
                            trailing = { NcStatusMark(ok) }
                        )
                    }
                }
            }

            item { NcSectionHeader(stringResource(R.string.nc_tweaks_header)) }
            item {
                val socOn = profile.support == SupportLevel.FULL
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    NcListRow(
                        title = stringResource(R.string.nc_general_tweaks),
                        subtitle = stringResource(R.string.nc_general_tweaks_desc),
                        position = 0, count = 2,
                        trailing = { TweakState(true) }
                    )
                    NcListRow(
                        title = if (socOn) stringResource(R.string.nc_soc_tweaks, profile.socVendor)
                            else stringResource(R.string.nc_soc_specific_tweaks),
                        subtitle = if (socOn) socTweakSummary(profile.socVendor) else stringResource(R.string.nc_partial_desc),
                        position = 1, count = 2,
                        ltrSubtitle = socOn,
                        trailing = { TweakState(socOn) }
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun TweakState(on: Boolean) {
    NcChip(
        text = stringResource(if (on) R.string.nc_enabled else R.string.nc_skipped),
        container = if (on) ncOkContainer() else ncWarnContainer(),
        content = if (on) ncOnOkContainer() else ncOnWarnContainer()
    )
}

@Composable
private fun SupportBanner(profile: DeviceProfile, loaded: Boolean) {
    val cs = MaterialTheme.colorScheme
    val (container, onContainer) = when (profile.support) {
        SupportLevel.FULL -> ncOkContainer() to ncOnOkContainer()
        SupportLevel.PARTIAL -> ncWarnContainer() to ncOnWarnContainer()
        SupportLevel.UNKNOWN -> cs.surfaceContainerHigh to cs.onSurface
    }
    val title = when (profile.support) {
        SupportLevel.FULL -> stringResource(R.string.nc_support_full)
        SupportLevel.PARTIAL -> stringResource(R.string.nc_support_partial)
        SupportLevel.UNKNOWN -> stringResource(R.string.nc_support_unknown)
    }
    val desc = when (profile.support) {
        SupportLevel.FULL -> stringResource(R.string.nc_full_desc, profile.socVendor)
        SupportLevel.PARTIAL -> stringResource(R.string.nc_partial_desc)
        SupportLevel.UNKNOWN -> if (loaded) stringResource(R.string.nc_unknown_desc) else ""
    }
    Surface(shape = RoundedCornerShape(28.dp), color = container, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(MaterialShapes.Cookie12Sided.toShape())
                    .background(onContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (profile.support == SupportLevel.FULL) Icons.Rounded.Check else Icons.Rounded.ErrorOutline,
                    null, tint = container, modifier = Modifier.size(26.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = onContainer)
                if (desc.isNotEmpty()) {
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = onContainer.copy(alpha = 0.9f))
                }
            }
        }
    }
}

@Composable
private fun DeviceCardTopBar(onBack: () -> Unit, onRescan: () -> Unit, scanning: Boolean) {
    NcPageHeader(subtitle = stringResource(R.string.nc_device_card), onBack = onBack) {
        if (scanning) {
            LoadingIndicator(modifier = Modifier.size(40.dp).padding(end = 8.dp))
        } else {
            IconButton(onClick = onRescan) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.nc_rescan), modifier = Modifier.size(28.dp))
            }
        }
    }
}
