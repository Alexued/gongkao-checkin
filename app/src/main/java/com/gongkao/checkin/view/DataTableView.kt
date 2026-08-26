package com.gongkao.checkin.view

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.gongkao.checkin.R
import com.gongkao.checkin.data.BankTable
import com.gongkao.checkin.ui.dp

/**
 * 资料分析的表格材料。表头允许跨行/跨列（`{"t":"年份","rs":2}`），所以用 GridLayout 按占位表排布，
 * 而不是逐行摆 LinearLayout。列宽超出屏幕时整体横向滚动，不压缩单元格。
 */
class DataTableView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val titleView = TextView(context).apply {
        textSize = 12.5f
        setTextColor(context.getColor(R.color.ink))
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        setLineSpacing(3f.dp, 1f)
        layoutParams = LayoutParams(-1, -2).apply { bottomMargin = 8.dp }
    }

    private val grid = GridLayout(context).apply {
        // 底色当网格线用：每个单元格留 1dp 外边距，露出来的就是分隔线
        setBackgroundColor(context.getColor(R.color.divider))
        setPadding(1.dp, 1.dp, 0, 0)
    }

    init {
        orientation = VERTICAL
        addView(titleView)
        addView(
            HorizontalScrollView(context).apply {
                isFillViewport = true
                addView(grid)
                layoutParams = LayoutParams(-1, -2)
            }
        )
    }

    fun bind(table: BankTable) {
        titleView.text = table.title
        titleView.visibility = if (table.title.isBlank()) GONE else VISIBLE

        grid.removeAllViews()
        val cols = table.columnCount()
        if (cols == 0) return
        grid.columnCount = cols

        val heads = table.headCells()
        val occupied = Array(heads.size) { BooleanArray(cols) }

        heads.forEachIndexed { r, row ->
            var c = 0
            for (cell in row) {
                while (c < cols && occupied[r][c]) c++
                if (c >= cols) break
                for (rr in r until minOf(r + cell.rowSpan, heads.size)) {
                    for (cc in c until minOf(c + cell.colSpan, cols)) occupied[rr][cc] = true
                }
                addCell(cell.text, r, c, cell.rowSpan, cell.colSpan, head = true, label = c == 0)
                c += cell.colSpan
            }
        }

        table.rows.forEachIndexed { i, row ->
            row.forEachIndexed { c, text ->
                if (c < cols) addCell(text, heads.size + i, c, 1, 1, head = false, label = c == 0)
            }
        }
    }

    private fun addCell(
        text: String,
        row: Int,
        col: Int,
        rowSpan: Int,
        colSpan: Int,
        head: Boolean,
        label: Boolean = false
    ) {
        val tv = TextView(context).apply {
            this.text = text
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(
                context.getColor(
                    when {
                        head -> R.color.ink
                        label -> R.color.ink
                        else -> R.color.ink_sub
                    }
                )
            )
            if (head || label) {
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }
            setBackgroundColor(context.getColor(if (head) R.color.surface_alt else R.color.surface))
            setPadding(7.dp, 8.dp, 7.dp, 8.dp)
            // 首列是指标名，动辄十几个字；不封顶的话它会把年份列全挤出屏幕，
            // 而题目往往要横向比较两个年份，所以让它折行而不是撑宽。
            if (label) maxWidth = LABEL_MAX_WIDTH.dp else minWidth = DATA_MIN_WIDTH.dp
        }
        tv.layoutParams = GridLayout.LayoutParams().apply {
            rowSpec = GridLayout.spec(row, rowSpan, 1f)
            columnSpec = GridLayout.spec(col, colSpan, 1f)
            setMargins(0, 0, 1.dp, 1.dp)
        }
        grid.addView(tv)
    }

    private companion object {
        const val LABEL_MAX_WIDTH = 100
        const val DATA_MIN_WIDTH = 46
    }
}
