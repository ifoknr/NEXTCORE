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

package zx.nextcore

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.tracing.Trace
import com.topjohnwu.superuser.Shell
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.material3.Material3
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zx.nextcore.R
import zx.nextcore.ui.component.*
import zx.nextcore.ui.hud.*
import zx.nextcore.ui.mainscreens.*
import zx.nextcore.ui.subscreens.*
import zx.nextcore.ui.theme.NextCoreTheme
import zx.nextcore.ui.util.*


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        
        val fromTileType = if (intent.action == "android.service.quicksettings.action.QS_TILE_PREFERENCES") {
            val component = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, android.content.ComponentName::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME)
            }
            
            when (component?.className) {
                "zx.nextcore.TileService.BypassChgTileService" -> "bypass"
                "zx.nextcore.TileService.ProfileTileService" -> "profile"
                else -> null
            }
        } else null

        // Bring the floating monitor back after a reboot or after the system stopped it.
        val overlayPrefs = zx.nextcore.overlay.OverlayPrefs.prefs(this)
        if (overlayPrefs.getBoolean(zx.nextcore.overlay.OverlayPrefs.ENABLED, false) &&
            !zx.nextcore.overlay.OverlayService.isRunning
        ) {
            zx.nextcore.overlay.OverlayService.start(this)
        }

        setContent {
            NextCoreTheme {
                MainScreen(fromTileType)
            }
        }
    }
}

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** MD3 emphasized easing, used for tab switches. */
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

@Composable
fun MainScreen(fromTileType: String? = null) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val pagerRoutes = remember { listOf("home", "applist", "tweaks", "settings") }
    val pagerState = rememberPagerState(initialPage = 0) { pagerRoutes.size }
    var showExitConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(fromTileType) {
        val rootNav = "main"
        when (fromTileType) {
            "bypass" -> navController.navigate("bypasschg") {
                popUpTo(rootNav) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            "profile" -> navController.navigate(rootNav) {
                popUpTo(rootNav) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val rawRoute = navBackStackEntry?.destination?.route
    val isOnMainPager = rawRoute == "main"

    val coroutineScope = rememberCoroutineScope()
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    val appPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val hasCompletedGetStarted = remember { appPrefs.getBoolean("has_completed_get_started", false) }

    LaunchedEffect(Unit) { WallpaperCache.init(context) }

    val hazeState = remember { HazeState() }
    var rootStatus by remember { mutableStateOf(false) }
    var moduleInstalled by remember { mutableStateOf(false) }

    val navItems = listOf(
        HudNavItem(stringResource(R.string.hud_nav_home), Icons.Rounded.Home),
        HudNavItem(stringResource(R.string.hud_nav_games), Icons.Rounded.SportsEsports),
        HudNavItem(stringResource(R.string.hud_nav_tweaks), Icons.Rounded.Tune),
        HudNavItem(stringResource(R.string.hud_nav_settings), Icons.Rounded.Settings),
    )
    // targetPage moves as soon as a swipe or tap commits, so the bar follows
    // the finger instead of waiting for the scroll to settle.
    val selectedPage = if (pagerState.isScrollInProgress) pagerState.targetPage else pagerState.currentPage

    val goToPage: (Int) -> Unit = { index ->
        if (isOnMainPager) {
            if (pagerState.currentPage != index) coroutineScope.launch {
                Trace.beginSection("NextCore:tabScrollTo")
                try {
                    pagerState.animateScrollToPage(index, animationSpec = tween(320, easing = Emphasized))
                } finally {
                    Trace.endSection()
                }
            }
        } else {
            navController.navigate("main") {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            coroutineScope.launch { pagerState.scrollToPage(index) }
        }
    }

    val pendingReboot by RebootManager.pendingReboot.collectAsState()

    // Root and module probes go through su, so they are polled off the main
    // thread instead of running in front of the first composition.
    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) {
                rootStatus = RootUtils.requestRootAccess()
                moduleInstalled = RootUtils.isModuleInstalled()
            }
            delay(2000)
        }
    }

    val installingDialog = rememberInstallingDialog()
    val updateDialog = rememberConfirmDialog(
        onConfirm = {
            coroutineScope.launch {
                installingDialog.withInstalling {
                    val result = withContext(Dispatchers.IO) {
                        Shell.cmd(
                            "cp /data/adb/modules/nextcore/NextCore.apk /data/local/tmp/NextCore_tmp.apk",
                            "sleep 5 && pm install -r /data/local/tmp/NextCore_tmp.apk",
                            "rm -f /data/local/tmp/NextCore_tmp.apk"
                        ).exec()
                    }
                    if (result.isSuccess) {
                        Toast.makeText(context, context.getString(R.string.toast_update_success), Toast.LENGTH_SHORT).show()
                    } else {
                        val errorLog = result.out.joinToString("\n").ifEmpty { context.getString(R.string.status_unknown) }
                        Toast.makeText(context, context.getString(R.string.toast_install_fail, errorLog), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    val rebootDialog = rememberConfirmDialog(
        onConfirm = { Shell.cmd("svc power reboot || reboot").submit() }
    )

    LaunchedEffect(rootStatus) {
        if (rootStatus) {
            val (moduleVC, updateApkAvailable, modulePendingReboot) = withContext(Dispatchers.IO) {
                Triple(
                    RootUtils.getModuleVersionCode(),
                    RootUtils.isUpdateApkAvailable(),
                    RootUtils.isModuleUpdatePendingReboot()
                )
            }
            val appVC = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            }

            if (appVC < moduleVC && updateApkAvailable) {
                updateDialog.showConfirm(
                    title = context.getString(R.string.dialog_update_available_title),
                    content = context.getString(R.string.dialog_update_available_content, appVC, moduleVC),
                    confirm = context.getString(R.string.dialog_update_available_confirm),
                    dismiss = context.getString(R.string.dialog_update_available_dismiss)
                )
            }

            if (modulePendingReboot) {
                rebootDialog.showConfirm(
                    title = context.getString(R.string.dialog_module_update_title),
                    content = context.getString(R.string.dialog_module_update_content),
                    confirm = context.getString(R.string.dialog_module_update_confirm),
                    dismiss = context.getString(R.string.dialog_module_update_dismiss)
                )
            }
        }
    }

    val activeDialogCount = remember { mutableStateOf(0) }
    CompositionLocalProvider(
        LocalAppHazeState provides hazeState,
        zx.nextcore.ui.component.LocalActiveDialogCount provides activeDialogCount
    ) {
        RootDialogsProvider {
            val isAnyDialogOpen = zx.nextcore.ui.component.LocalActiveDialogCount.current.value > 0 ||
                installingDialog.isShown || updateDialog.isShown || rebootDialog.isShown

            BackHandler(enabled = isOnMainPager && !isAnyDialogOpen) {
                if (showExitConfirm) {
                    showExitConfirm = false
                } else if (pagerState.currentPage != 0) {
                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                } else {
                    showExitConfirm = true
                }
            }

            Box(Modifier.fillMaxSize().background(Hud.bg)) {
                Row(Modifier.fillMaxSize()) {
                    // Tablets get a side rail, phones a bottom bar; both only on the main pages.
                    AnimatedVisibility(
                        visible = isWide && isOnMainPager,
                        enter = expandHorizontally() + fadeIn(),
                        exit = shrinkHorizontally() + fadeOut(),
                    ) {
                        HudRail(
                            items = navItems,
                            selected = selectedPage,
                            onSelect = goToPage,
                            bottomAction = HudNavItem(stringResource(R.string.hud_about), Icons.Rounded.Info),
                            onBottomAction = { navController.navigate("aboutscreen") },
                        )
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        NavHost(
                            navController = navController,
                            startDestination = if (hasCompletedGetStarted) "main" else "get_started",
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            enterTransition = { zx.nextcore.ui.navigation.enterTransition() },
                            exitTransition = { zx.nextcore.ui.navigation.exitTransition() },
                            popEnterTransition = { zx.nextcore.ui.navigation.popEnterTransition() },
                            popExitTransition = { zx.nextcore.ui.navigation.popExitTransition() }
                        ) {
                            composable("get_started") {
                                // get_started writes has_completed_get_started itself and then calls back.
                                ScreenWrapper(navController = navController, animatedVisibilityScope = this) {
                                    GetStartedScreen(navController) {
                                        navController.navigate("main") {
                                            popUpTo("get_started") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            }

                            composable("main") {
                                ScreenWrapper(navController = navController, animatedVisibilityScope = this) {
                                    // A plain pager: pages slide with the finger in both directions
                                    // and follow the layout direction, so Arabic swipes the other way.
                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier.fillMaxSize(),
                                        beyondViewportPageCount = 1,
                                        key = { pagerRoutes[it] },
                                    ) { page ->
                                        val visible = pagerState.currentPage == page
                                        when (pagerRoutes[page]) {
                                            "home" -> HudHomeScreen(navController, isVisible = visible, onOpenGames = { goToPage(1) })
                                            "applist" -> HudGamesScreen(navController, isVisible = visible)
                                            "tweaks" -> HudTweaksScreen(navController, isVisible = visible)
                                            "settings" -> HudSettingsScreen(navController)
                                        }
                                    }
                                }
                            }

                            composable("engine_cpu") {
                                ScreenWrapper(navController = navController, animatedVisibilityScope = this) { HudCpuEngineScreen(navController) }
                            }
                            composable("engine_mem") {
                                ScreenWrapper(navController = navController, animatedVisibilityScope = this) { HudMemoryEngineScreen(navController) }
                            }
                            composable("engine_fps") {
                                ScreenWrapper(navController = navController, animatedVisibilityScope = this) { HudFramesEngineScreen(navController) }
                            }
                            composable("sessions") {
                                ScreenWrapper(navController = navController, animatedVisibilityScope = this) { HudSessionsScreen(navController) }
                            }
                            composable("aboutscreen") {
                                ScreenWrapper(navController = navController, animatedVisibilityScope = this) { HudAboutScreen(navController) }
                            }
                            composable(
                                route = "app_settings/{pkg}",
                                arguments = listOf(navArgument("pkg") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val pkg = backStackEntry.arguments?.getString("pkg").orEmpty()
                                ScreenWrapper(navController = navController, animatedVisibilityScope = this) {
                                    HudGameSettingsScreen(navController, pkg)
                                }
                            }

                            composable("color_palette") {
                                ScreenWrapper(navController = navController, animatedVisibilityScope = this) { ColorPaletteScreen(navController) }
                            }
                            composable("colorscheme") {
                                ScreenWrapper(navController = navController, animatedVisibilityScope = this) { ColorSchemeSettings(navController) }
                            }
                            composable("FasScreen") {
                                ScreenWrapper(navController = navController, animatedVisibilityScope = this) { FasScreen(navController) }
                            }
                            composable("bypasschg") {
                                ScreenWrapper(navController = navController, animatedVisibilityScope = this) { BypassChargeScreen(navController) }
                            }
                            composable("bypasschg_check") {
                                ScreenWrapper(navController = navController, animatedVisibilityScope = this) { BypassChargeCheckScreen(navController) }
                            }
                            composable("preferenced") {
                                ScreenWrapper(navController = navController, animatedVisibilityScope = this) { PreferenceTweakScreen(navController) }
                            }
                            composable("fpsgoscreen") {
                                ScreenWrapper(navController = navController, animatedVisibilityScope = this) { FpsGoSettings(navController) }
                            }
                            composable("devicecard") {
                                ScreenWrapper(navController = navController, animatedVisibilityScope = this) { DeviceCardScreen(navController) }
                            }
                            composable("monitoring") {
                                ScreenWrapper(navController = navController, animatedVisibilityScope = this) { HudMonitorScreen(navController) }
                            }
                            composable("governorsettings") {
                                ScreenWrapper(navController = navController, animatedVisibilityScope = this) { GovSettings(navController) }
                            }
                        }

                        AnimatedVisibility(
                            visible = !isWide && isOnMainPager,
                            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                        ) {
                            HudBottomBar(items = navItems, selected = selectedPage, onSelect = goToPage)
                        }
                    }
                }

                AnimatedVisibility(
                    visible = rootStatus && moduleInstalled && pendingReboot && isOnMainPager,
                    enter = scaleIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(),
                    exit = scaleOut(animationSpec = tween(200, easing = FastOutLinearInEasing)) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = (if (isWide) 16.dp else 86.dp) + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                ) {
                    Row(
                        Modifier
                            .clip(Hud.cardShape)
                            .background(hudAccent)
                            .clickable {
                                rebootDialog.showConfirm(
                                    title = context.getString(R.string.dialog_reboot_required_title),
                                    content = context.getString(R.string.dialog_reboot_required_content),
                                    confirm = context.getString(R.string.reboot),
                                    dismiss = context.getString(R.string.dialog_update_available_dismiss)
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.reboot), color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
            ConfirmDialogHost(handle = updateDialog)
            ConfirmDialogHost(handle = rebootDialog)
            InstallingDialogHost(handle = installingDialog)

            ExitPopup(
                visible = showExitConfirm,
                onDismiss = { showExitConfirm = false },
                onConfirm = { (context as? android.app.Activity)?.finishAffinity() }
            )
        }
    }
}
