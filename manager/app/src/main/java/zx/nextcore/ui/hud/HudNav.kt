package zx.nextcore.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class HudNavItem(val label: String, val icon: ImageVector)

/** Width the side rail takes on tablets; pages are padded by it. */
val HudRailWidth = 92.dp

/** Bottom bar for phones: four tabs, the selected one tinted with the accent. */
@Composable
fun HudBottomBar(items: List<HudNavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val accent = hudAccent
    Row(
        modifier
            .fillMaxWidth()
            .background(Hud.nav)
            .drawBehind { drawLine(Hud.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEachIndexed { i, item ->
            val on = i == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(Hud.tileShape)
                    .clickable { onSelect(i) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(width = 44.dp, height = 30.dp)
                        .clip(Hud.tileShape)
                        .then(if (on) Modifier.background(accent.copy(alpha = 0.16f)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(item.icon, item.label, tint = if (on) accent else Hud.muted, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(2.dp))
                Text(item.label, color = if (on) accent else Hud.muted, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

/** Side rail for tablets: logo on top, tabs, and an extra action at the bottom. */
@Composable
fun HudRail(
    items: List<HudNavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    bottomAction: HudNavItem? = null,
    onBottomAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val accent = hudAccent
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        modifier
            .width(HudRailWidth)
            .fillMaxHeight()
            .background(Hud.nav)
            .drawBehind {
                // Hairline on the side facing the content.
                val x = if (rtl) 0f else size.width - 1.dp.toPx()
                drawRect(Hud.line, Offset(x, 0f), Size(1.dp.toPx(), size.height))
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NcLogo(52.dp)
        Spacer(Modifier.height(12.dp))
        items.forEachIndexed { i, item ->
            val on = i == selected
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(i) }
                    .then(if (on) Modifier.background(accent.copy(alpha = 0.12f)) else Modifier)
                    .drawBehind {
                        if (on) {
                            val bar = 3.dp.toPx()
                            val x = if (rtl) 0f else size.width - bar
                            drawRect(accent, Offset(x, 0f), Size(bar, size.height))
                        }
                    }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(36.dp).border(1.5.dp, if (on) accent else Hud.muted, Hud.tileShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(item.icon, item.label, tint = if (on) accent else Hud.muted, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(item.label, color = if (on) accent else Hud.muted, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            }
        }
        Spacer(Modifier.weight(1f))
        if (bottomAction != null) {
            Column(
                Modifier.fillMaxWidth().clickable(onClick = onBottomAction).padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(bottomAction.icon, bottomAction.label, tint = Hud.muted, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(4.dp))
                Text(bottomAction.label, color = Hud.muted, fontSize = 11.sp)
            }
        }
    }
}
