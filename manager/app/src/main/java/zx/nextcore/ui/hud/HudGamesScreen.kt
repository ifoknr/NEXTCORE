package zx.nextcore.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import zx.nextcore.R
import zx.nextcore.ui.component.AppIconImage
import zx.nextcore.ui.viewmodel.ApplistViewmodel

private const val FILTER_ALL = 0
private const val FILTER_ENABLED = 1
private const val FILTER_SUGGESTED = 2
private const val FILTER_SYSTEM = 3

/** Games and apps: enabled ones first, then suggestions, then everything else. */
@Composable
fun HudGamesScreen(navController: NavController, isVisible: Boolean, vm: ApplistViewmodel = viewModel()) {
    val context = LocalContext.current
    var filter by rememberSaveable { mutableIntStateOf(FILTER_ALL) }
    LaunchedEffect(isVisible) {
        if (isVisible) {
            vm.loadApps(context)
            vm.refreshAppConfigStatus()
        }
    }
    LaunchedEffect(filter) { vm.showSystemApps = filter == FILTER_SYSTEM }
    val apps = vm.filteredApps
    val enabled = apps.filter { it.isEnabledInConfig }
    val suggested = apps.filter { it.isRecommended && !it.isEnabledInConfig }
    val others = apps.filter { !it.isRecommended && !it.isEnabledInConfig }
    val open: (String) -> Unit = { navController.navigate("app_settings/$it") }

    HudPage(
        title = stringResource(R.string.hud_games_title),
        actions = { HudIconButton(Icons.Rounded.Refresh, "refresh", { vm.loadApps(context, forceRefresh = true) }) },
    ) {
        item(key = "search") {
            Row(
                Modifier.fillMaxWidth().clip(Hud.cardShape).background(Hud.card).padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = Hud.muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (vm.searchQuery.isEmpty()) Text(stringResource(R.string.hud_search), color = Hud.muted, fontSize = 14.sp)
                    BasicTextField(
                        value = vm.searchTextFieldValue,
                        onValueChange = vm::updateSearch,
                        singleLine = true,
                        textStyle = TextStyle(color = Hud.text, fontSize = 14.sp),
                        cursorBrush = SolidColor(hudAccent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item(key = "filters") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { HudFilterChip(stringResource(R.string.hud_filter_all), filter == FILTER_ALL) { filter = FILTER_ALL } }
                item { HudFilterChip(stringResource(R.string.hud_filter_enabled, enabled.size), filter == FILTER_ENABLED) { filter = FILTER_ENABLED } }
                item { HudFilterChip(stringResource(R.string.hud_filter_suggested), filter == FILTER_SUGGESTED) { filter = FILTER_SUGGESTED } }
                item { HudFilterChip(stringResource(R.string.hud_filter_system), filter == FILTER_SYSTEM) { filter = FILTER_SYSTEM } }
            }
        }
        if (ApplistViewmodel.apps.isEmpty()) {
            item(key = "loading") { HudEmpty(stringResource(R.string.hud_loading)) }
            return@HudPage
        }
        if (apps.isEmpty()) {
            item(key = "none") { HudEmpty(stringResource(R.string.hud_no_apps)) }
        }
        if (filter != FILTER_SUGGESTED && enabled.isNotEmpty()) {
            item(key = "h_enabled") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { HudSectionTitle(stringResource(R.string.hud_enabled_section)) }
                    Text(enabled.size.toString(), color = hudAccent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                }
            }
            item(key = "enabled") { AppGroup(enabled, R.string.hud_tag_enabled, true, open) }
        }
        if ((filter == FILTER_ALL || filter == FILTER_SUGGESTED) && suggested.isNotEmpty()) {
            item(key = "h_suggested") { HudSectionTitle(stringResource(R.string.hud_suggested_section), stringResource(R.string.hud_suggestions)) }
            item(key = "suggested") { AppGroup(suggested, R.string.hud_tag_suggested, false, open) }
        }
        if ((filter == FILTER_ALL || filter == FILTER_SYSTEM) && others.isNotEmpty()) {
            item(key = "h_others") { HudSectionTitle(stringResource(R.string.hud_other_section)) }
            // Long lists are chunked so the page stays a lazy list.
            others.chunked(25).forEachIndexed { i, chunk ->
                item(key = "others_$i") { AppGroup(chunk, R.string.hud_tag_off, false, open) }
            }
        }
    }
}

@Composable
private fun AppGroup(apps: List<ApplistViewmodel.AppInfo>, tag: Int, active: Boolean, onOpen: (String) -> Unit) {
    HudCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
        apps.forEachIndexed { i, app ->
            if (i > 0) HorizontalDivider(color = Hud.line)
            Row(
                Modifier.fillMaxWidth().clip(Hud.tileShape).clickable { onOpen(app.packageName) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIconImage(app, 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(app.label, color = Hud.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, color = Hud.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HudTag(stringResource(tag), active)
            }
        }
    }
}
