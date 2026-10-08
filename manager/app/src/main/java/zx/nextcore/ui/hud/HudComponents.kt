package zx.nextcore.ui.hud

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zx.nextcore.ui.theme.BrandFontFamily

/* ---------- Page scaffold ---------- */

/**
 * A HUD page: black background with faint diagonal stripes behind the header,
 * a title row, and a scrolling column of content. Every page scrolls.
 */
@Composable
fun HudPage(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    state: LazyListState = rememberLazyListState(),
    bottomPadding: Dp = 24.dp,
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(modifier.fillMaxSize().background(Hud.bg)) {
        HudStripes(Modifier.fillMaxWidth().height(top + 260.dp))
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = bottomPadding + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "hud_header") {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (onBack != null) {
                        HudIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "back", onBack)
                    }
                    Column(Modifier.weight(1f)) {
                        if (titleContent != null) titleContent() else Text(
                            title,
                            color = Hud.text,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle != null) Text(subtitle, color = Hud.muted, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
                }
            }
            content()
        }
    }
}

/** Diagonal accent stripes fading out downwards, as on the HUD header. */
@Composable
fun HudStripes(modifier: Modifier = Modifier) {
    val accent = hudAccent
    Canvas(modifier) {
        val gap = 22.dp.toPx()
        val w = 7.dp.toPx()
        var x = -size.height
        while (x < size.width) {
            drawLine(
                color = accent.copy(alpha = 0.07f),
                start = Offset(x, size.height),
                end = Offset(x + size.height, 0f),
                strokeWidth = w,
            )
            x += gap
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Hud.bg), startY = size.height * 0.2f, endY = size.height))
    }
}

/* ---------- Cards and rows ---------- */

/**
 * Card with one cut corner. [accent] draws the accent bar on the start edge
 * (right side in Arabic), used for the item that matters on a page.
 */
@Composable
fun HudCard(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val accentColor = hudAccent
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        modifier
            .clip(Hud.cardShape)
            .background(Hud.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .drawBehind {
                if (accent) {
                    val bar = 3.dp.toPx()
                    drawRect(accentColor, topLeft = Offset(if (rtl) size.width - bar else 0f, 0f), size = Size(bar, size.height))
                }
            }
            .padding(padding),
        content = content,
    )
}

/** Section title with an optional accent action on the far side. */
@Composable
fun HudSectionTitle(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Hud.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                color = hudAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(Hud.tileShape).clickable(enabled = onAction != null) { onAction?.invoke() }.padding(4.dp),
            )
        }
    }
}

/** Square icon button with a thin outline (header actions). */
@Composable
fun HudIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(Hud.tileShape)
            .background(Hud.card)
            .border(1.dp, Hud.line, Hud.tileShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Hud.text, modifier = Modifier.size(20.dp))
    }
}

/** Small square tile holding an accent icon, used at the start of rows. */
@Composable
fun HudIconTile(icon: ImageVector, tint: Color = hudAccent, size: Dp = 34.dp) {
    Box(
        Modifier.size(size).clip(Hud.smallCut).background(Hud.cardHi),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

/**
 * A settings row: icon tile, title and optional subtitle, then [trailing]
 * (a switch, a value chip) or a chevron when the row opens something.
 */
@Composable
fun HudRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(Hud.tileShape)
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) HudIconTile(icon, tint = if (enabled) hudAccent else Hud.muted)
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) Hud.text else Hud.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = Hud.muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Text("›", color = Hud.muted, fontSize = 18.sp)
        }
    }
}

/** Rows inside one card, separated by hairlines. */
@Composable
fun HudGroup(modifier: Modifier = Modifier, rows: List<@Composable () -> Unit>) {
    HudCard(modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
        rows.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(color = Hud.line, thickness = 1.dp)
            row()
        }
    }
}

/** Label/value table, as on the device info card. */
@Composable
fun HudKeyValues(rows: List<Pair<String, String>>, modifier: Modifier = Modifier, highlight: Set<String> = emptySet()) {
    HudCard(modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
        rows.forEachIndexed { i, (k, v) ->
            if (i > 0) HorizontalDivider(color = Hud.line, thickness = 1.dp)
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(k, color = Hud.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(
                    v,
                    color = if (k in highlight) Hud.green else Hud.text,
                    fontSize = 13.sp,
                    fontFamily = BrandFontFamily,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Small stat tile: label above a big value. */
@Composable
fun HudStat(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    HudCard(modifier) {
        Text(label, color = Hud.muted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            color = if (highlight) hudAccent else Hud.text,
            fontFamily = BrandFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            maxLines = 1,
        )
    }
}

/* ---------- Controls ---------- */

/** Compact switch (40×22) in the accent color. */
@Composable
fun HudSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val accent = hudAccent
    val track by animateColorAsState(if (checked) accent else Color(0xFF2B2B34), label = "hudSwitchTrack")
    val thumbX by animateDpAsState(if (checked) 21.dp else 3.dp, label = "hudSwitchThumb")
    Box(
        Modifier
            .size(width = 42.dp, height = 22.dp)
            .clip(CircleShape)
            .background(if (enabled) track else track.copy(alpha = 0.4f))
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .offset(x = thumbX, y = 3.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(if (checked) Color.White else Hud.muted),
        )
    }
}

/** Parallelogram used for the segmented control and the FPS picker. */
private val SkewShape = GenericShape { size, _ ->
    val skew = size.height * 0.28f
    moveTo(skew, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - skew, size.height)
    lineTo(0f, size.height)
    close()
}

/** Row of angled buttons; the selected one is filled with the accent gradient. */
@Composable
fun HudSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    fontSize: TextUnit = 14.sp,
) {
    val brush = hudAccentBrush
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .clip(SkewShape)
                    .then(if (on) Modifier.background(brush) else Modifier.background(Hud.cardHi))
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) Color.White else Hud.muted,
                    fontSize = fontSize,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Value chip on the far side of a row ("افتراضي", "تشغيل", "SkiaVK").
 * [icon] marks what a tap does: a menu arrow or a page arrow. With [fill]
 * the chip takes the width it is given and the icon sits at the far end.
 */
@Composable
fun HudValueChip(
    text: String,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    fill: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .heightIn(min = 38.dp)
            .clip(Hud.smallCut)
            .background(Hud.cardHi)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 14.dp, end = if (icon != null) 8.dp else 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text,
            color = if (active) hudAccent else Hud.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = BrandFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (fill) Modifier.weight(1f) else Modifier,
        )
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Hud.muted, modifier = Modifier.size(20.dp))
        }
    }
}

/** Outlined status tag ("مفعّل", "موصى به"). */
@Composable
fun HudTag(text: String, active: Boolean = true) {
    val c = if (active) hudAccent else Hud.muted
    Box(
        Modifier.border(1.dp, c, Hud.smallCut).padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(text, color = c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Filter chip; selected chips take the accent fill. */
@Composable
fun HudFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(SkewShape)
            .background(if (selected) hudAccent else Hud.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(text, color = if (selected) Color.White else Hud.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/* ---------- Gauges ---------- */

/**
 * 240° arc gauge with the value in the middle. [progress] is 0..1; [colors]
 * run along the arc from start to end.
 */
@Composable
fun HudGauge(
    progress: Float,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(hudAccent, Hud.red),
    onClick: (() -> Unit)? = null,
) {
    HudCard(modifier, onClick = onClick, padding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)) {
        Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(64.dp)) {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = Hud.line,
                    startAngle = 150f, sweepAngle = 240f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                val sweep = 240f * progress.coerceIn(0f, 1f)
                if (sweep > 0f) drawArc(
                    brush = Brush.sweepGradient(colors + colors.first(), center = center),
                    startAngle = 150f, sweepAngle = sweep, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Text(value, color = Hud.text, fontFamily = BrandFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Text(label, color = Hud.muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** Thin horizontal bar (0..1) with an optional floor marker. */
@Composable
fun HudBar(progress: Float, modifier: Modifier = Modifier, marker: Float? = null, color: Color = hudAccent) {
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val r = size.height / 2
        drawRoundRect(Hud.line, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
        val w = size.width * progress.coerceIn(0f, 1f)
        if (w > 0f) drawRoundRect(color, size = Size(w, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
        if (marker != null) {
            val x = size.width * marker.coerceIn(0f, 1f)
            drawLine(Hud.text, Offset(x, -2f), Offset(x, size.height + 2f), strokeWidth = 2.dp.toPx())
        }
    }
}

/** Empty-state text inside a card. */
@Composable
fun HudEmpty(text: String) {
    HudCard(Modifier.fillMaxWidth()) {
        Text(text, color = Hud.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.fillMaxWidth())
    }
}

/** Note card with the accent bar, used for explanations at the top of a page. */
@Composable
fun HudNote(text: String) {
    HudCard(Modifier.fillMaxWidth(), accent = true, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Text(text, color = Hud.muted, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

/** Box filling a grid cell with fixed height, for tiles in a row. */
@Composable
fun RowScope.HudCell(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.weight(1f), content = content)
}

/** Equal-width spacer helper for rows of tiles. */
@Composable
fun HudSpacer(width: Dp) = Spacer(Modifier.width(width))

/** Value chip that opens a menu of [options]; [selected] indexes into it. */
@Composable
fun HudDropdownChip(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    active: Boolean = false,
    fill: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box(modifier) {
        HudValueChip(
            options.getOrElse(selected) { options.firstOrNull().orEmpty() },
            active = active,
            modifier = if (fill) Modifier.fillMaxWidth() else Modifier,
            icon = Icons.Rounded.ArrowDropDown,
            fill = fill,
        ) { open = true }
        androidx.compose.material3.DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Hud.cardHi,
        ) {
            options.forEachIndexed { i, label ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(label, color = if (i == selected) hudAccent else Hud.text, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { open = false; onSelect(i) },
                )
            }
        }
    }
}

/** Accent slider without Material's large thumb. */
@Composable
fun HudSlider(value: Float, onValueChange: (Float) -> Unit, steps: Int, range: ClosedFloatingPointRange<Float>, onFinished: () -> Unit, enabled: Boolean = true) {
    androidx.compose.material3.Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        steps = steps,
        enabled = enabled,
        onValueChangeFinished = onFinished,
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = hudAccent,
            inactiveTrackColor = Hud.line,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
    )
}
