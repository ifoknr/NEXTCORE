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

package zx.azenith.ui.component


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zx.azenith.R
import zx.azenith.ui.theme.BrandFontFamily


/** Corner radius of the content sheet under the page header. */
val NcSheetCorner = 36.dp

/** Bottom padding for lists on the main tabs, leaving room for the floating nav bar. */
@Composable
fun ncTabBottomPadding(): Dp =
    116.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** The darker sheet the page content sits on (pure black in AMOLED mode). */
@Composable
fun ncSheetColor(): Color = MaterialTheme.colorScheme.surfaceContainerLowest

/** Muted tonal color for hero cards. */
@Composable
fun ncHeroColor(): Color = MaterialTheme.colorScheme.secondaryContainer

/** Faded watermark icon color on hero cards. */
@Composable
fun ncWatermarkColor(): Color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.08f)

/**
 * Large page header: "NextCore" with the page name under it, plus optional
 * back button and actions. Used as the Scaffold topBar on every redesigned page.
 */
@Composable
fun NcPageHeader(
    subtitle: String,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.app_name),
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(cs.surface)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = if (onBack != null) 8.dp else 18.dp, bottom = 18.dp)
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(cs.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = BrandFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = if (onBack != null) 28.sp else 30.sp,
                    lineHeight = 36.sp,
                    color = cs.onSurface,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
            actions()
        }
    }
}

/** Rounded black sheet that holds the page content below [NcPageHeader]. */
@Composable
fun NcSheet(
    topPadding: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topPadding),
        shape = RoundedCornerShape(topStart = NcSheetCorner, topEnd = NcSheetCorner),
        color = ncSheetColor()
    ) {
        Box(Modifier.fillMaxSize(), content = content)
    }
}

/** True on tablets and unfolded foldables (600dp and wider). */
@Composable
fun ncIsWide(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

/**
 * Narrowest a column of cards may get. Wider screens fit more columns instead
 * of stretching cards, so a tablet fills its width with phone-sized cards:
 * one column on phones, two on a portrait tablet, three in landscape.
 */
val NcColumnMinWidth = 340.dp

/** Columns for the main tabs' card grids; see [NcColumnMinWidth]. */
val ncGridCells: StaggeredGridCells = StaggeredGridCells.Adaptive(NcColumnMinWidth)

/** Gap between cards in a grid, both across and down. */
val NcGridGap = 12.dp

/** Standard content padding for a list inside [NcSheet]. */
@Composable
fun ncSheetListPadding(bottom: Dp = ncTabBottomPadding()): PaddingValues {
    val side = if (ncIsWide()) 24.dp else 16.dp
    return PaddingValues(start = side, end = side, top = 18.dp, bottom = bottom)
}

/**
 * Hero card with a big faded icon in the bottom corner, in the style of
 * RvSystem Monitor. The content draws on top of the watermark.
 */
@Composable
fun NcWatermarkCard(
    watermark: ImageVector?,
    modifier: Modifier = Modifier,
    container: Color = ncHeroColor(),
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    padding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val body: @Composable () -> Unit = {
        Box {
            if (watermark != null) {
                Icon(
                    watermark,
                    contentDescription = null,
                    tint = ncWatermarkColor(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 18.dp, y = 26.dp)
                        .size(150.dp)
                )
            }
            Column(Modifier.padding(padding), content = content)
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), color = container, contentColor = contentColor) { body() }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), color = container, contentColor = contentColor) { body() }
    }
}

/** Section title inside a sheet, e.g. "الأنوية". */
@Composable
fun NcSheetSection(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 4.dp)
    )
}

/** Square-rounded icon tile (filled primary) used as card and list leading icons. */
@Composable
fun NcIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.33f))
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.54f))
    }
}
