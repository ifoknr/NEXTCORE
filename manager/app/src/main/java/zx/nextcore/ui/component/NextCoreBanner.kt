package zx.nextcore.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zx.nextcore.R
import zx.nextcore.ui.theme.BrandFontFamily
import zx.nextcore.ui.util.SupportLevel

/**
 * Default Home banner. It is drawn rather than shown as a picture, so the
 * text keeps its margins at any width instead of being cropped on tablets,
 * and its colors follow the app theme. A picture the user picks replaces it.
 */
@Composable
fun NextCoreBanner(
    socModel: String,
    support: SupportLevel,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.background(cs.secondaryContainer)) {
        val wide = maxWidth >= 600.dp
        val small = maxWidth < 360.dp
        val titleSize = when {
            wide -> 52.sp
            small -> 28.sp
            else -> 34.sp
        }

        // Large faint silhouette on the far side, as on the Monitor cards.
        Icon(
            Icons.Rounded.RocketLaunch,
            contentDescription = null,
            tint = cs.onSecondaryContainer.copy(alpha = 0.16f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = if (wide) 0.dp else 20.dp)
                .padding(end = if (wide) 32.dp else 0.dp)
                .size(if (wide) 180.dp else 118.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(if (wide) 0.72f else 0.8f)
                .padding(horizontal = if (wide) 32.dp else 20.dp, vertical = if (wide) 28.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (wide) 6.dp else 3.dp)
        ) {
            Text(
                "PERFORMANCE ENGINE",
                color = cs.primary,
                fontFamily = BrandFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (wide) 13.sp else 10.sp,
                letterSpacing = 2.sp,
                maxLines = 1
            )
            Text(
                "NextCore",
                color = cs.onSecondaryContainer,
                fontFamily = BrandFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = titleSize,
                lineHeight = titleSize * 1.08f,
                maxLines = 1
            )
            Text(
                "Live monitoring · Auto profiles",
                color = cs.onSecondaryContainer.copy(alpha = 0.8f),
                fontFamily = BrandFontFamily,
                fontSize = if (wide) 18.sp else 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.padding(top = if (wide) 8.dp else 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (socModel.isNotBlank()) {
                    BannerChip(socModel, cs.primary, cs.onPrimary, wide)
                }
                when (support) {
                    SupportLevel.FULL ->
                        BannerChip(stringResource(R.string.nc_support_full), cs.tertiary, cs.onTertiary, wide)
                    SupportLevel.PARTIAL ->
                        BannerChip(stringResource(R.string.nc_support_partial), cs.tertiary, cs.onTertiary, wide)
                    SupportLevel.UNKNOWN -> Unit
                }
            }
        }
    }
}

@Composable
private fun BannerChip(text: String, container: Color, content: Color, wide: Boolean) {
    Surface(color = container, contentColor = content, shape = CircleShape) {
        Text(
            text,
            fontFamily = BrandFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (wide) 13.sp else 11.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = if (wide) 14.dp else 10.dp, vertical = 4.dp)
        )
    }
}
