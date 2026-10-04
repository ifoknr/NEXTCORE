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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package zx.azenith.ui.mainscreens


import android.os.Build
import android.system.Os
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Token
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import zx.azenith.ui.component.*
import zx.azenith.ui.theme.BrandFontFamily
import zx.azenith.ui.util.SupportLevel
import zx.azenith.ui.util.getChipsetName
import zx.azenith.ui.util.getRealDeviceName
import zx.azenith.ui.util.getSELinuxStatus
import zx.azenith.ui.viewmodel.HomeUiState
import zx.azenith.ui.viewmodel.HomeViewModel


private const val LIVE_POLL_MS = 1500L

@Composable
fun HomeScreen(
    navController: NavController? = null,
    isVisible: Boolean = true,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val uiState by viewModel.uiState.collectAsState()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showRebootSheet by remember { mutableStateOf(false) }

    val wide = LocalConfiguration.current.screenWidthDp >= 600

    LaunchedEffect(Unit) { viewModel.refreshAiMode() }

    // Live monitoring runs only while Home is the visible page and the app is in the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(isVisible, uiState.profileLoaded) {
        if (!isVisible || !uiState.profileLoaded) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.pollLiveStats()
                delay(LIVE_POLL_MS)
            }
        }
    }

    // Static device facts for the device list
    var deviceName by remember { mutableStateOf("${Build.MANUFACTURER} ${Build.MODEL}") }
    var chipsetName by remember { mutableStateOf("") }
    var selinux by remember { mutableStateOf("") }
    val kernel = remember { Os.uname().release }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            deviceName = getRealDeviceName(context)
            chipsetName = getChipsetName(context)
            selinux = getSELinuxStatus(context)
        }
    }

    val autoMode = uiState.autoMode != "0"
    val restartingMsg = stringResource(R.string.nc_restarting)
    val applyingMsg = stringResource(R.string.toast_applying_profile)

    val onSelectProfile: (String) -> Unit = { value ->
        if (autoMode) viewModel.setAutoMode(false)
        viewModel.applyProfile(value) {
            coroutineScope.launch { snackbarHostState.showSnackbar(applyingMsg) }
        }
    }
    val openDeviceCard: () -> Unit = { navController?.navigate("devicecard") { launchSingleTop = true } }

    val statusSection: LazyListScope.() -> Unit = {
        item(key = "hero") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NcHeroCard(
                    installed = uiState.moduleInstalled,
                    running = uiState.serviceStatusRes == R.string.status_alive,
                    pid = uiState.servicePid,
                    version = uiState.moduleVersion
                )
                NcStatusChips(
                    support = uiState.deviceProfile.support,
                    socModel = uiState.deviceProfile.socModel.uppercase(),
                    rootGranted = uiState.rootStatus,
                    autoMode = autoMode,
                    onSupportClick = openDeviceCard,
                    onAutoClick = { viewModel.setAutoMode(!autoMode) }
                )
            }
        }
        item(key = "game") { RunningGameSection(uiState) }
        item(key = "profileHeader") { NcSectionHeader(stringResource(R.string.nc_profile_header)) }
        item(key = "profiles") {
            NcProfileList(
                current = uiState.currentProfileValue,
                autoMode = autoMode,
                onSelect = onSelectProfile
            )
        }
    }

    val monitorSection: LazyListScope.() -> Unit = {
        item(key = "liveHeader") { NcSectionHeader(stringResource(R.string.nc_live_header)) }
        item(key = "cpu") {
            NcCpuCard(
                live = uiState.live,
                cpuCount = uiState.deviceProfile.clusters.sumOf { it.cores }
            )
        }
        item(key = "grid") {
            NcMonitorGrid(
                live = uiState.live,
                cpuTempLabel = uiState.deviceProfile.cpuTempLabel,
                gpuName = uiState.deviceProfile.gpuName
            )
        }
    }

    val deviceSection: LazyListScope.() -> Unit = {
        item(key = "deviceHeader") { NcSectionHeader(stringResource(R.string.nc_device_header)) }
        item(key = "deviceRows") {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                NcListRow(
                    title = deviceName,
                    subtitle = listOf(chipsetName, "Android ${Build.VERSION.RELEASE}")
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    icon = Icons.Rounded.Token,
                    position = 0, count = 3,
                    onClick = openDeviceCard
                )
                NcListRow(
                    title = stringResource(R.string.nc_kernel),
                    subtitle = kernel,
                    icon = Icons.Rounded.Layers,
                    position = 1, count = 3,
                    ltrSubtitle = true
                )
                NcListRow(
                    title = "SELinux",
                    subtitle = selinux,
                    icon = Icons.Rounded.Security,
                    position = 2, count = 3
                )
            }
        }
        item(key = "links") {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 8.dp)) {
                NcListRow(
                    title = stringResource(R.string.support_us),
                    subtitle = stringResource(R.string.support_us_desc),
                    icon = Icons.Rounded.Favorite,
                    position = 0, count = 2,
                    onClick = { uriHandler.openUri("https://t.me/IFOKNR1") }
                )
                NcListRow(
                    title = stringResource(R.string.learn_more),
                    subtitle = stringResource(R.string.learn_more_desc),
                    icon = Icons.Rounded.Info,
                    position = 1, count = 2,
                    onClick = { uriHandler.openUri("https://github.com/ifoknr/NEXTCORE#readme") }
                )
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            NcHomeTopBar(
                scrollBehavior = scrollBehavior,
                onRebootClick = { showRebootSheet = true },
                onRestartClick = {
                    viewModel.restartService {
                        coroutineScope.launch { snackbarHostState.showSnackbar(restartingMsg) }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = 100.dp)) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        val padding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            start = 16.dp, end = 16.dp,
            bottom = 110.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        )
        if (wide) {
            // Tablet / landscape: controls on one side, live monitoring on the other.
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = padding,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    statusSection()
                    deviceSection()
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = padding,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    monitorSection()
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                statusSection()
                monitorSection()
                deviceSection()
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

@Composable
private fun NcHomeTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onRebootClick: () -> Unit,
    onRestartClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0.0f to cs.surface,
                    0.5f to cs.surface.copy(alpha = 0.9f),
                    1.0f to Color.Transparent
                )
            )
            .statusBarsPadding()
    ) {
        LargeFlexibleTopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.app_name),
                    fontFamily = BrandFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 30.sp
                )
            },
            subtitle = {
                Text(
                    text = stringResource(R.string.nc_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            },
            actions = {
                FilledTonalIconButton(onClick = onRestartClick, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(R.string.nc_restart_service), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(6.dp))
                FilledTonalIconButton(onClick = onRebootClick, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.PowerSettingsNew, contentDescription = stringResource(R.string.reboot), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
            scrollBehavior = scrollBehavior,
            windowInsets = WindowInsets(0, 0, 0, 0)
        )
    }
}
