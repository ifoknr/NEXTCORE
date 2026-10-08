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

package zx.nextcore.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import kotlin.math.ceil
import kotlin.math.max

/**
 * The floating monitor panel. Drawn straight onto a canvas so a refresh costs
 * one invalidate, not a layout pass over a dozen text views. Cells only ever
 * grow while the panel is up, so the panel does not twitch as digits change.
 * Used by [OverlayService] and by the live preview in the settings page.
 */
class OverlayPanelView(context: Context) : View(context) {

    var state: OverlayState = OverlayState()
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    private var metrics: List<OverlayMetric> = emptyList()
    private var mode = OverlayMode.COMPACT
    private var scale = 1f
    private var opacity = 60
    private var accent = OverlayPrefs.accents[0]
    private var colorCode = true
    private var border = false
    private var graph = false

    /** Applies the look settings. Resets the grow-only cell widths. */
    fun configure(
        metrics: List<OverlayMetric>,
        mode: OverlayMode,
        scale: Float,
        opacity: Int,
        accent: Int,
        colorCode: Boolean,
        border: Boolean,
        graph: Boolean,
    ) {
        this.metrics = metrics
        this.mode = mode
        this.scale = scale
        this.opacity = opacity.coerceIn(0, 100)
        this.accent = accent
        this.colorCode = colorCode
        this.border = border
        this.graph = graph
        widest.clear()
        applyPaints()
        requestLayout()
        invalidate()
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v * scale, resources.displayMetrics)
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v * scale, resources.displayMetrics)

    private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; fontFeatureSettings = "tnum" }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val graphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private fun applyPaints() {
        val shadow = if (opacity < 30) dp(1.5f) else 0f
        labelPaint.textSize = sp(if (mode == OverlayMode.EXPANDED) 11f else 9.5f)
        labelPaint.color = if (mode == OverlayMode.EXPANDED) Color.argb(200, 255, 255, 255) else accent
        valuePaint.textSize = sp(when (mode) { OverlayMode.COMPACT -> 16f; OverlayMode.MINIMAL -> 14f; OverlayMode.EXPANDED -> 13f })
        listOf(labelPaint, valuePaint).forEach { it.setShadowLayer(shadow, 0f, 0f, Color.BLACK) }
        bgPaint.color = Color.argb(opacity * 255 / 100, 10, 10, 13)
        borderPaint.color = accent
        borderPaint.strokeWidth = dp(1f)
        linePaint.color = Color.argb(60, 255, 255, 255)
        linePaint.strokeWidth = dp(1f)
        graphPaint.color = accent
        graphPaint.strokeWidth = dp(1.6f)
        fillPaint.color = Color.argb(60, Color.red(accent), Color.green(accent), Color.blue(accent))
        targetPaint.color = Color.argb(90, 255, 255, 255)
        targetPaint.strokeWidth = dp(0.8f)
        targetPaint.pathEffect = DashPathEffect(floatArrayOf(dp(3f), dp(3f)), 0f)
    }

    init {
        applyPaints()
        // Numbers and units read left to right in every language.
        layoutDirection = LAYOUT_DIRECTION_LTR
    }

    private fun valueColor(metric: OverlayMetric, level: Int): Int = when {
        !colorCode -> Color.WHITE
        level >= 2 -> 0xFFF87171.toInt()
        level == 1 -> 0xFFFACC15.toInt()
        metric == OverlayMetric.FPS -> 0xFF4ADE80.toInt()
        else -> Color.WHITE
    }

    /* ---------- Layout ---------- */

    private class Cell(val metric: OverlayMetric, val value: OverlayValue, var x: Float = 0f, var y: Float = 0f, var w: Float = 0f, var h: Float = 0f)

    private val cells = ArrayList<Cell>()
    private val widest = HashMap<OverlayMetric, Float>()
    private val graphRect = RectF()
    private val separators = ArrayList<RectF>()

    private fun grow(metric: OverlayMetric, needed: Float): Float {
        val w = max(widest[metric] ?: 0f, needed)
        widest[metric] = w
        return w
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        cells.clear()
        separators.clear()
        metrics.forEach { cells += Cell(it, it.format(state)) }
        val pad = dp(if (mode == OverlayMode.MINIMAL) 6f else 9f)
        var width: Float
        var height: Float
        val labelH = labelPaint.fontMetrics.let { it.descent - it.ascent }
        val valueH = valuePaint.fontMetrics.let { it.descent - it.ascent }

        when (mode) {
            OverlayMode.EXPANDED -> {
                val labelW = cells.maxOfOrNull { labelPaint.measureText(it.metric.label) } ?: 0f
                val valueW = cells.maxOfOrNull { grow(it.metric, valuePaint.measureText(it.value.text)) } ?: 0f
                width = max(dp(124f), pad * 2 + dp(6f) + labelW + dp(14f) + valueW)
                val rowH = max(labelH, valueH) + dp(4f)
                var y = pad
                cells.forEach { c ->
                    c.x = pad + dp(6f); c.y = y; c.w = width - c.x - pad; c.h = rowH
                    y += rowH
                }
                height = y + pad
            }
            else -> {
                val perRow = if (mode == OverlayMode.MINIMAL) 5 else 4
                val gap = dp(if (mode == OverlayMode.MINIMAL) 10f else 12f)
                val rows = cells.chunked(perRow)
                var y = pad
                var maxRowW = 0f
                rows.forEach { row ->
                    var x = pad
                    val rowH = if (mode == OverlayMode.COMPACT) labelH + valueH else max(labelH, valueH)
                    row.forEachIndexed { i, c ->
                        val label = labelPaint.measureText(c.metric.label)
                        val value = valuePaint.measureText(c.value.text)
                        c.w = if (mode == OverlayMode.COMPACT) grow(c.metric, max(label, value))
                        else label + dp(4f) + grow(c.metric, value)
                        c.x = x; c.y = y; c.h = rowH
                        x += c.w
                        if (i < row.lastIndex) {
                            separators += RectF(x + gap / 2, y + rowH * 0.2f, x + gap / 2 + dp(1f), y + rowH * 0.8f)
                            x += gap
                        }
                    }
                    maxRowW = max(maxRowW, x - pad)
                    y += rowH + dp(5f)
                }
                width = pad * 2 + max(maxRowW, if (cells.isEmpty()) dp(60f) else 0f)
                height = y - dp(5f) + pad
            }
        }

        if (graph && mode != OverlayMode.MINIMAL) {
            width = max(width, dp(130f))
            val gh = dp(if (mode == OverlayMode.EXPANDED) 34f else 26f)
            graphRect.set(pad, height - pad + dp(4f), width - pad, height - pad + dp(4f) + gh)
            height += gh + dp(4f)
        } else graphRect.setEmpty()

        setMeasuredDimension(ceil(width).toInt(), ceil(height).toInt())
    }

    /* ---------- Drawing ---------- */

    private val shape = Path()
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = dp(8f)
        val cut = dp(9f)
        // Rounded panel with the HUD's cut bottom-right corner.
        shape.reset()
        shape.moveTo(r, 0f)
        shape.lineTo(w - r, 0f)
        shape.quadTo(w, 0f, w, r)
        shape.lineTo(w, h - cut)
        shape.lineTo(w - cut, h)
        shape.lineTo(r, h)
        shape.quadTo(0f, h, 0f, h - r)
        shape.lineTo(0f, r)
        shape.quadTo(0f, 0f, r, 0f)
        shape.close()
        if (opacity > 0) canvas.drawPath(shape, bgPaint)
        if (border) canvas.drawPath(shape, borderPaint)

        val labelAscent = -labelPaint.fontMetrics.ascent
        val valueAscent = -valuePaint.fontMetrics.ascent
        when (mode) {
            OverlayMode.EXPANDED -> {
                // Accent bar on the left edge, as on the HUD cards.
                rect.set(0f, dp(10f), dp(2.5f), h - dp(10f))
                fillPaint.alpha = 255
                fillPaint.color = accent
                canvas.drawRect(rect, fillPaint)
                cells.forEach { c ->
                    val base = c.y + max(labelAscent, valueAscent)
                    canvas.drawText(c.metric.label, c.x, base, labelPaint)
                    valuePaint.color = valueColor(c.metric, c.value.level)
                    canvas.drawText(c.value.text, c.x + c.w - valuePaint.measureText(c.value.text), base, valuePaint)
                }
            }
            OverlayMode.COMPACT -> cells.forEach { c ->
                val lw = labelPaint.measureText(c.metric.label)
                canvas.drawText(c.metric.label, c.x + (c.w - lw) / 2, c.y + labelAscent, labelPaint)
                valuePaint.color = valueColor(c.metric, c.value.level)
                val vw = valuePaint.measureText(c.value.text)
                canvas.drawText(c.value.text, c.x + (c.w - vw) / 2, c.y + labelPaint.fontMetrics.let { it.descent - it.ascent } + valueAscent, valuePaint)
            }
            OverlayMode.MINIMAL -> cells.forEach { c ->
                val base = c.y + max(labelAscent, valueAscent)
                canvas.drawText(c.metric.label, c.x, base, labelPaint)
                valuePaint.color = valueColor(c.metric, c.value.level)
                canvas.drawText(c.value.text, c.x + labelPaint.measureText(c.metric.label) + dp(4f), base, valuePaint)
            }
        }
        separators.forEach { canvas.drawRect(it, linePaint) }
        if (!graphRect.isEmpty) drawGraph(canvas)
    }

    private val graphPath = Path()
    private val fillPath = Path()

    private fun drawGraph(canvas: Canvas) {
        val values = state.fpsHistory
        val g = graphRect
        val top = max(state.refreshHz.toFloat(), values.maxOrNull() ?: 0f).coerceAtLeast(30f) * 1.05f
        // Dashed line at the refresh rate: the most the game can show.
        val ty = g.bottom - g.height() * (state.refreshHz / top)
        canvas.drawLine(g.left, ty, g.right, ty, targetPaint)
        canvas.drawLine(g.left, g.bottom, g.right, g.bottom, linePaint)
        if (values.size < 2) return
        val step = g.width() / (OverlaySampler.HISTORY_SIZE - 1)
        val startX = g.right - step * (values.size - 1)
        graphPath.reset()
        fillPath.reset()
        values.forEachIndexed { i, v ->
            val x = startX + step * i
            val y = g.bottom - g.height() * (v.coerceAtLeast(0f) / top)
            if (i == 0) {
                graphPath.moveTo(x, y)
                fillPath.moveTo(x, g.bottom)
                fillPath.lineTo(x, y)
            } else {
                graphPath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(g.right, g.bottom)
        fillPath.close()
        fillPaint.color = Color.argb(60, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(graphPath, graphPaint)
    }
}
