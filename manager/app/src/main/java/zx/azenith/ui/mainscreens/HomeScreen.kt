/*
 * Copyright (C) 2026-2027 Zexshia
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


import android.os.Build
import android.system.Os
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.EnergySavingsLeaf
import androidx.compose.material.icons.outlined.OfflineBolt
import androidx.compose.material.icons.outlined.Water
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zx.azenith.R
import zx.azenith.overlay.OverlayPrefs
import zx.azenith.ui.component.*
import zx.azenith.ui.theme.BrandFontFamily
import zx.azenith.ui.util.SupportLevel
import zx.azenith.ui.util.clearHeaderImage
import zx.azenith.ui.util.getHeaderImage
import zx.azenith.ui.util.saveHeaderImage
import zx.azenith.ui.util.saveMediaDirectly
import zx.azenith.ui.util.getRealDeviceName
import zx.azenith.ui.util.getSELinuxStatus
import zx.azenith.ui.viewmodel.HomeUiState
import zx.azenith.ui.viewmodel.HomeViewModel
import java.util.Locale


/**
 * Polls live stats while [isVisible] and the app is in the foreground. Shared by Home and Monitor.
 * The interval is set under Settings > Floating monitor.
 */
@Composable
fun LiveStatsPoller(viewModel: HomeViewModel, isVisible: Boolean, profileLoaded: Boolean) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    LaunchedEffect(isVisible, profileLoaded) {
        if (!isVisible || !profileLoaded) return@LaunchedEffect
        val prefs = OverlayPrefs.prefs(context)
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.pollLiveStats()
                delay(
                    prefs.getLong(OverlayPrefs.MONITOR_INTERVAL_MS, OverlayPrefs.DEFAULT_MONITOR_INTERVAL_MS)
                        .coerceIn(500L, 10_000L)
                )
            }
        }
    }
}

/** Small pencil on the Home banner: pick a new image, go back to the default, or open the full banner settings. */
@Composable
private fun BannerEditButton(
    hasCustom: Boolean,
    modifier: Modifier = Modifier,
    onPick: () -> Unit,
    onReset: () -> Unit,
    onMore: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FilledTonalIconButton(
            onClick = { open = true },
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
            ),
            modifier = Modifier.size(40.dp)
        ) {
            Icon(Icons.Rounded.Edit, stringResource(R.string.nc_banner_edit), Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp)) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nc_banner_pick)) },
                leadingIcon = { Icon(Icons.Rounded.Image, null) },
                onClick = { open = false; onPick() }
            )
            if (hasCustom) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nc_banner_reset)) },
                    leadingIcon = { Icon(Icons.Rounded.SettingsBackupRestore, null) },
                    onClick = { open = false; onReset() }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nc_banner_more)) },
                leadingIcon = { Icon(Icons.Rounded.Palette, null) },
                onClick = { open = false; onMore() }
            )
        }
    }
}

private data class ProfileChoice(val value: String, val title: Int, val desc: Int, val icon: ImageVector)

private val profileChoices = listOf(
    ProfileChoice("3", R.string.nc_profile_eco, R.string.nc_profile_eco_desc, Icons.Outlined.EnergySavingsLeaf),
    ProfileChoice("2", R.string.nc_profile_balanced, R.string.nc_profile_balanced_desc, Icons.Outlined.Water),
    ProfileChoice("1", R.string.nc_profile_perf, R.string.nc_profile_perf_desc, Icons.Outlined.OfflineBolt),
)

@Composable
fun HomeScreen(
    navController: NavController? = null,
    isVisible: Boolean = true,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showRebootSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshAiMode() }
    LiveStatsPoller(viewModel, isVisible, uiState.profileLoaded)

    var deviceName by remember { mutableStateOf("${Build.MANUFACTURER} ${Build.MODEL}") }
    var selinux by remember { mutableStateOf("") }
    val kernel = remember { Os.uname().release }
    // Follows the saved banner, so a change here or in Colors & Theme shows up right away.
    var bannerUri by remember { mutableStateOf(context.getHeaderImage()) }
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "header_image_uri" || key == null) bannerUri = context.getHeaderImage()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val bannerSavedMsg = stringResource(R.string.str_banner_updated)
    val bannerFailMsg = stringResource(R.string.str_pick_media_fail)
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val ext = when (context.contentResolver.getType(uri)) {
                "image/png" -> "png"
                "image/gif" -> "gif"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val saved = withContext(Dispatchers.IO) {
                context.saveMediaDirectly(uri, ext)?.also { context.saveHeaderImage(it) }
            }
            snackbarHostState.showSnackbar(if (saved != null) bannerSavedMsg else bannerFailMsg)
        }
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            deviceName = getRealDeviceName(context)
            selinux = getSELinuxStatus(context)
        }
    }

    val autoMode = uiState.autoMode != "0"
    val restartingMsg = stringResource(R.string.nc_restarting)
    val applyingMsg = stringResource(R.string.toast_applying_profile)
    val openDeviceCard: () -> Unit = { navController?.navigate("devicecard") { launchSingleTop = true } }
    val wide = ncIsWide()

    Scaffold(
        topBar = {
            NcPageHeader(subtitle = stringResource(R.string.nav_home)) {
                IconButton(onClick = {
                    viewModel.restartService {
                        coroutineScope.launch { snackbarHostState.showSnackbar(restartingMsg) }
                    }
                }) {
                    Icon(Icons.Rounded.RestartAlt, stringResource(R.string.nc_restart_service), Modifier.size(28.dp))
                }
                IconButton(onClick = { showRebootSheet = true }) {
                    Icon(Icons.Rounded.PowerSettingsNew, stringResource(R.string.reboot), Modifier.size(28.dp))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = 100.dp)) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        NcSheet(topPadding = innerPadding.calculateTopPadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = ncSheetListPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.isBannerEnabled) {
                    item(key = "banner") {
                        // A picked picture keeps the picture's shape; the drawn banner
                        // grows to fit its text instead of being cropped.
                        val custom = bannerUri
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    when {
                                        custom != null && wide -> Modifier.height(240.dp)
                                        custom != null -> Modifier.aspectRatio(1280f / 560f)
                                        else -> Modifier
                                    }
                                )
                                .clip(RoundedCornerShape(30.dp))
                        ) {
                            if (custom != null) {
                                MediaBannerRenderer(uriString = custom, modifier = Modifier.fillMaxSize())
                            } else {
                                NextCoreBanner(
                                    socModel = uiState.deviceProfile.socModel,
                                    support = uiState.deviceProfile.support,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = if (wide) 200.dp else 150.dp)
                                )
                            }
                            BannerEditButton(
                                hasCustom = bannerUri != null,
                                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                                onPick = {
                                    bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                onReset = {
                                    coroutineScope.launch(Dispatchers.IO) { context.clearHeaderImage() }
                                },
                                onMore = { navController?.navigate("color_palette") { launchSingleTop = true } }
                            )
                        }
                    }
                }
                val statusCard: @Composable (Modifier) -> Unit = { m ->
                    Box(m) {
                        StatusDeviceCard(
                            uiState = uiState,
                            deviceName = deviceName,
                            kernel = kernel,
                            selinux = selinux,
                            autoMode = autoMode,
                            onClick = openDeviceCard,
                            onAutoClick = { viewModel.setAutoMode(!autoMode) }
                        )
                    }
                }
                if (!wide) {
                    item(key = "status") { statusCard(Modifier) }
                    item(key = "game") { RunningGameSection(uiState) }
                }
                item(key = "tiles") {
                    val live = uiState.live
                    val tiles: List<@Composable (Modifier) -> Unit> = listOf(
                        { m ->
                            ProfileTile(
                                current = uiState.currentProfileValue,
                                autoMode = autoMode,
                                modifier = m,
                                onSelect = { value ->
                                    if (autoMode) viewModel.setAutoMode(false)
                                    viewModel.applyProfile(value) {
                                        coroutineScope.launch { snackbarHostState.showSnackbar(applyingMsg) }
                                    }
                                }
                            )
                        },
                        { m ->
                            val t = live.cpuTempC
                            RingTile(
                                progress = t?.let { (it - 20f) / 80f } ?: 0f,
                                value = t?.let { String.format(Locale.US, "%.0f°", it) } ?: "--",
                                label = stringResource(R.string.nc_cpu_temp),
                                sub = uiState.deviceProfile.cpuTempLabel.substringAfter("· ", "").ifEmpty { "—" },
                                color = if ((t ?: 0f) >= 75f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = m
                            )
                        },
                        { m ->
                            RingTile(
                                progress = (live.gpuLoad ?: 0) / 100f,
                                value = live.gpuMhz?.toString() ?: "--",
                                label = stringResource(R.string.nc_gpu),
                                sub = listOfNotNull("MHz", live.gpuLoad?.let { "$it%" }).joinToString(" · "),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = m
                            )
                        },
                        { m ->
                            RingTile(
                                progress = (live.batteryPct ?: 0) / 100f,
                                value = live.batteryPct?.let { "$it%" } ?: "--",
                                label = stringResource(R.string.nc_battery),
                                sub = listOfNotNull(
                                    live.batteryCurrentMa?.let { "$it mA" },
                                    live.batteryTempC?.let { String.format(Locale.US, "%.0f°C", it) }
                                ).joinToString(" · ").ifEmpty { "—" },
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = m
                            )
                        },
                    )
                    val tileGrid: @Composable (Modifier) -> Unit = { m ->
                        Column(m, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            tiles.chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // Square on phones; a fixed height on tablets so
                                    // the tiles don't grow with the screen.
                                    row.forEach { tile ->
                                        tile(Modifier.weight(1f).then(if (wide) Modifier.height(168.dp) else Modifier.aspectRatio(1f)))
                                    }
                                }
                            }
                        }
                    }
                    if (wide) {
                        // Tablet: status card and the 2×2 tiles side by side.
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                statusCard(Modifier.weight(1f))
                                tileGrid(Modifier.weight(1f))
                            }
                            RunningGameSection(uiState)
                        }
                    } else {
                        tileGrid(Modifier)
                    }
                }
                item(key = "support") {
                    Surface(
                        onClick = { uriHandler.openUri("https://t.me/IFOKNR1") },
                        shape = RoundedCornerShape(30.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            NcIconTile(Icons.AutoMirrored.Rounded.Send, size = 44.dp)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.nc_support_channel), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "t.me/IFOKNR1",
                                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    RootAppDialog {
        RebootBottomSheet(
            show = showRebootSheet,
            onDismiss = { showRebootSheet = false },
            onReboot = { reason -> viewModel.rebootDevice(reason) }
        )
    }
}

@Composable
private fun SmallChip(text: String, container: Color, content: Color, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
    }
}

/** Service status and device facts in one hero card. */
@Composable
private fun StatusDeviceCard(
    uiState: HomeUiState,
    deviceName: String,
    kernel: String,
    selinux: String,
    autoMode: Boolean,
    onClick: () -> Unit,
    onAutoClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val running = uiState.serviceStatusRes == R.string.status_alive
    val profile = uiState.deviceProfile
    NcWatermarkCard(watermark = Icons.Rounded.TabletAndroid, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NcIconTile(
                Icons.Rounded.Bolt, size = 44.dp,
                container = if (running) cs.primary else cs.error,
                content = if (running) cs.onPrimary else cs.onError
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        when {
                            !uiState.moduleInstalled -> R.string.module_not_installed
                            running -> R.string.nc_service_running
                            else -> R.string.nc_service_stopped
                        }
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val detail = listOfNotNull(
                    uiState.servicePid.takeIf { running && it.isNotBlank() }?.let { "PID $it" },
                    uiState.moduleVersion.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr),
                        color = cs.onSecondaryContainer.copy(alpha = 0.75f),
                        maxLines = 1
                    )
                }
            }
            SmallChip(
                text = "● " + stringResource(if (running) R.string.nc_service_active else R.string.nc_service_inactive),
                container = if (running) ncOkContainer() else cs.errorContainer,
                content = if (running) ncOnOkContainer() else cs.onErrorContainer
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = cs.onSecondaryContainer.copy(alpha = 0.15f))
        Text(
            deviceName,
            fontFamily = BrandFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL, Build.DEVICE)
                .filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr),
            color = cs.onSecondaryContainer.copy(alpha = 0.75f)
        )
        FlowRow(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (profile.support) {
                SupportLevel.FULL -> SmallChip(
                    "✓ " + stringResource(R.string.nc_support_full) +
                        if (profile.socModel.isNotBlank()) " · ${profile.socModel.uppercase()}" else "",
                    ncOkContainer(), ncOnOkContainer()
                )
                SupportLevel.PARTIAL -> SmallChip("⚠ " + stringResource(R.string.nc_support_partial), ncWarnContainer(), ncOnWarnContainer())
                SupportLevel.UNKNOWN -> SmallChip(stringResource(R.string.nc_support_unknown), cs.surfaceContainerHighest, cs.onSurfaceVariant)
            }
            SmallChip(
                stringResource(if (uiState.rootStatus) R.string.nc_root_ok else R.string.nc_root_no),
                if (uiState.rootStatus) cs.tertiary else cs.errorContainer,
                if (uiState.rootStatus) cs.onTertiary else cs.onErrorContainer
            )
            SmallChip(
                stringResource(if (autoMode) R.string.nc_auto_short else R.string.nc_manual_short),
                cs.inverseSurface.copy(alpha = 0.85f), cs.inverseOnSurface,
                onClick = onAutoClick
            )
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniFact(stringResource(R.string.nc_android), Build.VERSION.RELEASE, Modifier.weight(0.7f))
            MiniFact(stringResource(R.string.nc_kernel), kernel.substringBefore("-GKI").let { k ->
                // "6.1.177-GKID-r5-…-IFOKNR-Kernel" -> "6.1.177-IFOKNR"; keep short kernels as they are
                val tag = kernel.split('-').firstOrNull { it.contains("IFOKNR") }
                if (tag != null) "${kernel.substringBefore('-')}-$tag" else k
            }, Modifier.weight(1.4f))
            MiniFact(
                "SELinux", selinux.ifEmpty { "—" }, Modifier.weight(1f),
                valueColor = if (selinux.contains("Enforcing", true)) ncOnOkContainer() else cs.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun MiniFact(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.ContentOrLtr),
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Square tile showing the active profile; tapping it opens a menu with all profiles. */
@Composable
private fun ProfileTile(
    current: String,
    autoMode: Boolean,
    modifier: Modifier,
    onSelect: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val choice = profileChoices.firstOrNull { it.value == current }
    Box(modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(30.dp),
            color = cs.primary,
            contentColor = cs.onPrimary,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(cs.onPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(choice?.icon ?: Icons.Outlined.Water, null, tint = cs.primary, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Rounded.UnfoldMore, null)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.nc_profile_header),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onPrimary.copy(alpha = 0.75f)
                )
                Text(
                    choice?.let { stringResource(it.title) } ?: "—",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    stringResource(if (autoMode) R.string.nc_auto_short else R.string.nc_tap_to_switch),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onPrimary.copy(alpha = 0.75f)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = cs.surfaceContainerHigh,
            offset = DpOffset(0.dp, 8.dp),
            modifier = Modifier.widthIn(min = 260.dp)
        ) {
            profileChoices.forEach { p ->
                val selected = p.value == current
                Row(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) ncHeroColor() else Color.Transparent)
                        .clickable {
                            expanded = false
                            if (!selected) onSelect(p.value)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .widthIn(min = 240.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NcIconTile(
                        p.icon, size = 42.dp,
                        container = if (selected) cs.primary else cs.surfaceContainerHighest,
                        content = if (selected) cs.onPrimary else cs.onSurfaceVariant
                    )
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(p.title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(p.desc), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    if (selected) Icon(Icons.Rounded.Check, null, tint = cs.primary)
                }
            }
            if (autoMode) {
                Text(
                    stringResource(R.string.nc_profile_menu_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/** Square tile with a wavy progress ring and a value in its centre. */
@Composable
fun RingTile(
    progress: Float,
    value: String,
    label: String,
    sub: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "ringTile")
    Surface(modifier = modifier, shape = RoundedCornerShape(30.dp), color = cs.surfaceContainer) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(
                    progress = { animated },
                    modifier = Modifier.fillMaxHeight().aspectRatio(1f).padding(4.dp),
                    color = color,
                    trackColor = cs.surfaceContainerHighest
                )
                Text(
                    value,
                    fontFamily = BrandFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)
                )
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1)
            Text(
                sub,
                style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.ContentOrLtr),
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RunningGameSection(uiState: HomeUiState) {
    val isPerformanceMode = uiState.currentProfileRes == R.string.Profile_Performance ||
        uiState.currentProfileRes == R.string.profile_perflite
    val showGameCard = isPerformanceMode && !uiState.runningGamePkg.isNullOrEmpty()

    var retainedPkg by remember { mutableStateOf("") }
    var retainedStartTime by remember { mutableStateOf("00:00:00") }
    LaunchedEffect(uiState.runningGamePkg, uiState.runningGameStartTime) {
        if (!uiState.runningGamePkg.isNullOrEmpty()) {
            retainedPkg = uiState.runningGamePkg
            retainedStartTime = uiState.runningGameStartTime ?: "00:00:00"
        }
    }
    AnimatedVisibility(
        visible = showGameCard,
        enter = expandVertically(animationSpec = spring()) + fadeIn(),
        exit = shrinkVertically(animationSpec = spring()) + fadeOut()
    ) {
        if (retainedPkg.isNotEmpty()) {
            RunningGameCard(pkgName = retainedPkg, startTimeStr = retainedStartTime)
        }
    }
}
