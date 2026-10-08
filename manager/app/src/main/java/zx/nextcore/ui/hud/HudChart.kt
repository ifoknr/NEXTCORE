package zx.nextcore.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background

/** One line on a [HudLineChart]. Values are drawn on their own min..max scale. */
data class ChartSeries(
    val label: String,
    val values: List<Float?>,
    val color: Color,
    val dashed: Boolean = false,
    val filled: Boolean = false,
)

/** Legend row: a short colored bar and the series name for each line. */
@Composable
fun HudChartLegend(series: List<ChartSeries>) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        series.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 14.dp, height = 3.dp).background(s.color))
                Spacer(Modifier.width(6.dp))
                Text(s.label, color = s.color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Line chart for the dashboard. Each series is scaled to its own range so
 * temperature and clock can share one plot; gaps (null) break the line.
 * [xLabels] are spread evenly under the plot.
 */
@Composable
fun HudLineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    xLabels: List<String> = emptyList(),
) {
    HudCard(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            // Grid
            val rows = 4
            for (i in 0..rows) {
                val y = size.height * i / rows
                drawLine(Hud.line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            series.forEach { s ->
                val vals = s.values
                val present = vals.filterNotNull()
                if (present.size < 2) return@forEach
                val lo = present.min()
                val hi = present.max()
                val span = (hi - lo).takeIf { it > 0f } ?: 1f
                val pad = size.height * 0.12f
                fun x(i: Int) = if (vals.size <= 1) 0f else size.width * i / (vals.size - 1)
                fun y(v: Float) = size.height - pad - (v - lo) / span * (size.height - 2 * pad)

                val line = Path()
                var started = false
                var firstX = 0f
                var lastX = 0f
                vals.forEachIndexed { i, v ->
                    if (v == null) { started = false; return@forEachIndexed }
                    if (!started) { line.moveTo(x(i), y(v)); if (firstX == 0f) firstX = x(i); started = true }
                    else line.lineTo(x(i), y(v))
                    lastX = x(i)
                }
                if (s.filled) {
                    val area = Path().apply {
                        addPath(line)
                        lineTo(lastX, size.height)
                        lineTo(firstX, size.height)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.35f), Color.Transparent)))
                }
                drawPath(
                    line,
                    s.color,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null,
                    ),
                )
            }
        }
        if (xLabels.isNotEmpty()) {
            // The plot runs left to right (oldest first) in every language.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    xLabels.forEach { Text(it, color = Hud.muted, fontSize = 11.sp) }
                }
            }
        }
    }
}
