package com.af.sensormonitor.ui

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** How the main screen's rows are laid out, in dp. */
data class GaugeLayout(val columns: Int, val rows: Int, val rowHeight: Float, val fontSize: Float, val scroll: Boolean)

/**
 * Fits the rows into the available area. Rows grow up to [MAX_ROW] and shrink down to [MIN_ROW]; below that the
 * list scrolls instead of the text getting smaller. Wide screens (tablets, landscape) get more columns, decided
 * only by the width. Both limits scale with the system font size.
 */
object GaugeLayoutCalc {
    const val MIN_ROW = 15.5f
    const val PREFERRED_ROW = 22f
    const val MAX_ROW = 30f
    const val MIN_COLUMN_WIDTH = 330f
    /** Characters in one row: name 12, wake mark 2, gap, 3 fields of 7 with gaps, gap, unit 5. */
    const val ROW_CHARS = 44
    const val CHAR_WIDTH_EM = 0.6f
    const val FONT_PER_ROW = 0.72f
    const val HORIZONTAL_PADDING = 8f

    fun indicatorWidth(rowHeight: Float) = rowHeight * 0.9f

    fun compute(count: Int, widthDp: Float, heightDp: Float, fontScale: Float = 1f): GaugeLayout {
        val n = max(1, count)
        val minRow = MIN_ROW * fontScale
        val preferred = PREFERRED_ROW * fontScale
        val maxRow = MAX_ROW * fontScale
        val maxCols = max(1, floor(widthDp / MIN_COLUMN_WIDTH).toInt())
        var cols = maxCols
        for (c in 1..maxCols) {
            if (heightDp / ceil(n / c.toFloat()) >= preferred) { cols = c; break }
        }
        val rows = ceil(n / cols.toFloat()).toInt()
        val row = (heightDp / rows).coerceIn(minRow, maxRow)
        val scroll = row * rows > heightDp + 0.5f
        val colWidth = widthDp / cols
        val byWidth = (colWidth - HORIZONTAL_PADDING - indicatorWidth(row)) / (ROW_CHARS * CHAR_WIDTH_EM)
        val font = min(row * FONT_PER_ROW, byWidth)
        return GaugeLayout(cols, rows, row, font, scroll)
    }
}
