package com.gongkao.checkin.ui.bank

import android.widget.LinearLayout
import android.widget.TextView
import com.gongkao.checkin.R
import com.gongkao.checkin.data.BankData
import com.gongkao.checkin.data.BankQuestion
import com.gongkao.checkin.ui.ListScreen
import com.gongkao.checkin.ui.dp
import com.gongkao.checkin.view.DataTableView

/** 题库浏览：65 题按章节分组，点一题展开材料/表格 + 选项 + 答案 + 完整解析。 */
class BankBrowseActivity : ListScreen() {

    private val expanded = mutableSetOf<String>()

    override fun title(): CharSequence = getString(R.string.bank_browse)

    override fun build() {
        val all = BankData.list(this)
        if (all.isEmpty()) {
            empty()
            return
        }
        all.groupBy { it.chapter }.forEach { (chapter, list) ->
            section("$chapter · ${list.size}")
            list.forEach { q -> group(q) }
        }
    }

    private fun group(q: BankQuestion) {
        row(
            title = q.stem,
            sub = q.skill,
            value = q.answer,
            chevron = true
        ) {
            if (!expanded.remove(q.id)) expanded.add(q.id)
            rebuild()
        }
        if (q.id in expanded) details(q)
    }

    private fun details(q: BankQuestion) {
        val box = card(14)

        if (q.source.isNotBlank()) box.addView(dim(getString(R.string.bank_source, q.source)))

        val table = q.table
        if (table != null) {
            box.addView(
                DataTableView(this).apply {
                    bind(table)
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10.dp }
                }
            )
        }
        if (q.material.isNotBlank()) box.addView(body(q.material, R.color.ink_sub))

        box.addView(label(getString(R.string.bank_options)))
        q.optionList().forEach { (key, text) ->
            val hit = key == q.answer
            box.addView(
                TextView(this).apply {
                    this.text = "$key. $text"
                    textSize = 13.5f
                    setTextColor(getColor(if (hit) R.color.teal else R.color.ink))
                    if (hit) {
                        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                    }
                    setLineSpacing(3f.dp, 1f)
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 6.dp }
                }
            )
        }

        box.addView(label(getString(R.string.bank_solution)))
        box.addView(body(q.solution, R.color.ink))

        (box.layoutParams as LinearLayout.LayoutParams).apply {
            topMargin = (-2).dp
            bottomMargin = 10.dp
        }
    }

    private fun label(t: CharSequence) = TextView(this).apply {
        text = t
        textSize = 12f
        setTextColor(getColor(R.color.ink_dim))
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 14.dp }
    }

    private fun body(t: CharSequence, colorRes: Int) = TextView(this).apply {
        text = t
        textSize = 13.5f
        setTextColor(getColor(colorRes))
        setLineSpacing(5f.dp, 1f)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8.dp }
    }

    private fun dim(t: CharSequence) = TextView(this).apply {
        text = t
        textSize = 12f
        setTextColor(getColor(R.color.ink_dim))
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }
}
