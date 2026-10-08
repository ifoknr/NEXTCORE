/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the \"License\");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an \"AS IS\" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package zx.nextcore.ui.mainscreens


import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import zx.nextcore.R
import zx.nextcore.ui.component.*
import zx.nextcore.ui.component.AppIconImage
import zx.nextcore.ui.viewmodel.ApplistViewmodel


@Composable
fun ApplistScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: ApplistViewmodel = viewModel()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyGridState()
    val wide = ncIsWide()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val topAppBarState = rememberSaveable(saver = TopAppBarState.Saver) {
        TopAppBarState(
            initialHeightOffsetLimit = -Float.MAX_VALUE,
            initialHeightOffset = 0f,
            initialContentOffset = 0f
        )
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    
    val pullToRefreshState = rememberPullToRefreshState()
    
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    LaunchedEffect(Unit) {
        viewModel.showSystemApps = prefs.getBoolean("show_system_apps", false)
        if (ApplistViewmodel.apps.isEmpty()) {
            viewModel.loadApps(context)
        }
    }

    var isSearchMode by remember { mutableStateOf(false) }
    
    BackHandler(enabled = isSearchMode) {
        viewModel.clearSearch()
        isSearchMode = false
        focusManager.clearFocus()
    }

    LaunchedEffect(isSearchMode) {
        if (isSearchMode) {
            focusRequester.requestFocus()
        } else {
            viewModel.clearSearch()
            focusManager.clearFocus()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (ApplistViewmodel.apps.isNotEmpty()) {
                    viewModel.refreshAppConfigStatus()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    val homeViewModel: zx.nextcore.ui.viewmodel.HomeViewModel = viewModel()
    val homeState by homeViewModel.uiState.collectAsState()
    var filter by rememberSaveable { mutableIntStateOf(0) } // 0 all, 1 enabled, 2 games
    var menuExpanded by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            NcPageHeader(subtitle = stringResource(R.string.nav_applist)) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.cd_menu), Modifier.size(28.dp))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_refresh)) },
                        onClick = {
                            AppIconCache.clear()
                            viewModel.loadApps(context, forceRefresh = true)
                            menuExpanded = false
                        },
                        leadingIcon = { Icon(Icons.Default.Refresh, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_show_system_apps)) },
                        trailingIcon = { if (viewModel.showSystemApps) Icon(Icons.Default.Check, null) },
                        onClick = {
                            val newValue = !viewModel.showSystemApps
                            viewModel.showSystemApps = newValue
                            prefs.edit().putBoolean("show_system_apps", newValue).apply()
                            menuExpanded = false
                        }
                    )
                }
            }
        },
        containerColor = cs.surface
    ) { innerPadding ->
        NcSheet(topPadding = innerPadding.calculateTopPadding()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullToRefresh(
                        state = pullToRefreshState,
                        isRefreshing = viewModel.isRefreshing,
                        onRefresh = {
                            AppIconCache.clear()
                            viewModel.loadApps(context, forceRefresh = true)
                        }
                    )
            ) {
                val allApps = viewModel.filteredApps
                val gamesCount = allApps.count { it.isRecommended }
                val appsToDisplay = when (filter) {
                    1 -> allApps.filter { it.isEnabledInConfig }
                    2 -> allApps.filter { it.isRecommended }
                    else -> allApps
                }

                // Phones: one grouped list. Tablets: app cards in as many
                // columns as fit, with search and filters across the top.
                val fullLine: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan) = { GridItemSpan(maxLineSpan) }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(NcColumnMinWidth),
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = ncSheetListPadding(),
                    horizontalArrangement = Arrangement.spacedBy(if (wide) 8.dp else 0.dp)
                ) {
                    item(key = "search", span = fullLine) {
                        Surface(shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Search, null, tint = cs.onSurfaceVariant)
                                TextField(
                                    value = viewModel.searchTextFieldValue,
                                    onValueChange = { viewModel.updateSearch(it) },
                                    placeholder = { Text(stringResource(R.string.nc_search_game)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent
                                    )
                                )
                                if (viewModel.searchTextFieldValue.text.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.clearSearch(); focusManager.clearFocus() }) {
                                        Icon(Icons.Default.Clear, stringResource(R.string.cd_clear))
                                    }
                                }
                            }
                        }
                    }
                    item(key = "filters", span = fullLine) {
                        Row(
                            Modifier.padding(top = 12.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterPill(stringResource(R.string.nc_filter_all), filter == 0) { filter = 0 }
                            FilterPill(stringResource(R.string.nc_filter_games, gamesCount), filter == 2) { filter = 2 }
                            FilterPill(stringResource(R.string.nc_filter_enabled), filter == 1) { filter = 1 }
                        }
                    }
                    val runningPkg = homeState.runningGamePkg
                    if (!runningPkg.isNullOrEmpty()) {
                        item(key = "running", span = fullLine) {
                            val runningApp = allApps.firstOrNull { it.packageName == runningPkg }
                            NcWatermarkCard(
                                watermark = Icons.Default.SportsEsports,
                                modifier = Modifier.padding(top = 10.dp),
                                padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                onClick = { navController.navigate("app_settings/$runningPkg") }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (runningApp != null) AppIconImage(app = runningApp, size = 48.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text(stringResource(R.string.nc_running_now), style = MaterialTheme.typography.labelMedium, color = cs.onSecondaryContainer.copy(alpha = 0.7f))
                                        Text(
                                            runningApp?.label ?: runningPkg,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    ElapsedTimeText(homeState.runningGameStartTime ?: "00:00:00")
                                }
                            }
                        }
                    }
                    item(key = "appsHeader", span = fullLine) { NcSheetSection(stringResource(R.string.nc_apps_section)) }
                    if (appsToDisplay.isEmpty() && !viewModel.isRefreshing) {
                        item(key = "empty", span = fullLine) {
                            Text(
                                text = stringResource(R.string.no_apps_found),
                                style = MaterialTheme.typography.bodyLarge,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(32.dp)
                            )
                        }
                    }
                    itemsIndexed(appsToDisplay, key = { _, app -> app.packageName }) { index, app ->
                        val big = 22.dp
                        val small = 6.dp
                        val shape = when {
                            wide || appsToDisplay.size == 1 -> RoundedCornerShape(big)
                            index == 0 -> RoundedCornerShape(topStart = big, topEnd = big, bottomStart = small, bottomEnd = small)
                            index == appsToDisplay.lastIndex -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = big, bottomEnd = big)
                            else -> RoundedCornerShape(small)
                        }
                        Surface(
                            onClick = { navController.navigate("app_settings/${app.packageName}") },
                            shape = shape,
                            color = cs.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = if (wide) 8.dp else 4.dp)
                                .animateItem(fadeInSpec = null, fadeOutSpec = null)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AppIconImage(app = app, size = 44.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        app.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                                        color = cs.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (app.isEnabledInConfig) {
                                    AppTag(stringResource(R.string.label_enabled), cs.tertiary, cs.onTertiary)
                                } else if (app.isRecommended) {
                                    AppTag(stringResource(R.string.label_recommended), cs.inverseSurface.copy(alpha = 0.8f), cs.inverseOnSurface)
                                } else if (app.isSystem) {
                                    AppTag(stringResource(R.string.label_system), cs.surfaceContainerHighest, cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = viewModel.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) cs.primary else cs.surfaceContainerHigh,
        contentColor = if (selected) cs.onPrimary else cs.onSurface
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

@Composable
private fun AppTag(text: String, container: Color, content: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
    }
}

@Composable
fun ApplistTopAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    isSearchMode: Boolean,
    onSearchModeChange: (Boolean) -> Unit,
    searchQuery: TextFieldValue,
    onSearchChange: (TextFieldValue) -> Unit,
    showSystemApps: Boolean,
    onToggleSystem: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    focusRequester: FocusRequester
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme

    val smoothGradient = Brush.verticalGradient(
        0.0f to colorScheme.surface,
        0.4f to colorScheme.surface.copy(alpha = 0.9f),
        0.5f to colorScheme.surface.copy(alpha = 0.8f),
        0.6f to colorScheme.surface.copy(alpha = 0.7f),
        0.7f to colorScheme.surface.copy(alpha = 0.5f),
        0.8f to colorScheme.surface.copy(alpha = 0.4f),
        0.9f to colorScheme.surface.copy(alpha = 0.3f),
        1.0f to Color.Transparent 
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(smoothGradient)
            .statusBarsPadding()
    ) {
        AnimatedContent(
            targetState = isSearchMode,
            transitionSpec = { 
                fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200)) 
            },
            label = "search_bar_transition"
        ) { searching ->
            if (searching) {
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            placeholder = { Text(stringResource(R.string.search_apps)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { onSearchModeChange(false) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                        }
                    },
                    actions = {
                        if (searchQuery.text.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange(TextFieldValue("")) }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cd_clear))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    windowInsets = WindowInsets(0, 0, 0, 0)
                )
            } else {
                LargeFlexibleTopAppBar(
                    scrollBehavior = scrollBehavior,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    title = {
                        Text(
                            text = stringResource(R.string.applist_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        Box(
                            modifier = Modifier
                                .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(colorScheme.surfaceVariant)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.avatar),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { onSearchModeChange(true) }) {
                            Icon(Icons.Default.Search, stringResource(R.string.cd_search))
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.cd_menu))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_refresh)) },
                                onClick = {
                                    AppIconCache.clear()
                                    onRefresh()
                                    menuExpanded = false
                                },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_show_system_apps)) },
                                trailingIcon = {
                                    if (showSystemApps) {
                                        Icon(Icons.Default.Check, null)
                                    }
                                },
                                onClick = { onToggleSystem(!showSystemApps); menuExpanded = false }
                            )
                        }
                    }
                )
            }
        }
    }
}


@Composable
fun LabelText(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.padding(end = 6.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
    }
}
