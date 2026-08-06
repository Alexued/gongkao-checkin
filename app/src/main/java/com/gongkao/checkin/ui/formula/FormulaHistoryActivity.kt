package com.gongkao.checkin.ui.formula

import android.widget.LinearLayout
import android.widget.TextView
import com.gongkao.checkin.R
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.FormulaSession
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.ui.ListScreen
import com.gongkao.checkin.ui.dp

/** 公式背诵记录。点一组展开每条公式的自评。 */
class FormulaHistoryActivity : ListScreen() {

    private val expanded = mutableSetOf<String>()

    override fun title(): CharSequence = getString(R.string.formula_records)

    override fun build() {
        val sessions = Repo.formulaSessions()
        if (sessions.isEmpty()) {
            empty()
            return
        }
        sessions.groupBy { it.date }
            .toSortedMap(reverseOrder())
            .forEach { (date, list) ->
                section(DateUtil.prettyStr(date))
                list.sortedByDescending { it.startAt }.forEach { s -> group(s) }
            }
    }

    private fun group(s: FormulaSession) {
        val modeName = getString(
            if (s.mode == "RANDOM") R.string.mode_random_short else R.string.mode_full_short
        )
        row(
            title = getString(R.string.formula_session_sub, modeName, s.knownCount(), s.total()),
            sub = "${s.category} · ${DateUtil.human(s.durationMs())}",
            value = getString(R.string.stat_percent, (s.accuracy() * 100).toInt()),
            chevron = true
        ) {
            if (!expanded.remove(s.id)) expanded.add(s.id)
            rebuild()
        }
        if (s.id in expanded) details(s)
    }

    private fun details(s: FormulaSession) {
        val box = card(14)
        s.items.forEach { item ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 7.dp, 0, 7.dp)
                layoutParams = LinearLayout.LayoutParams(-1, -2)
            }
            line.addView(TextView(this).apply {
                text = item.title
                textSize = 13f
                setTextColor(getColor(R.color.ink))
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            line.addView(TextView(this).apply {
                text = DateUtil.human(item.ms)
                textSize = 12f
                setTextColor(getColor(R.color.ink_dim))
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = 8.dp }
            })
            line.addView(TextView(this).apply {
                text = if (item.known) "✓" else "✕"
                textSize = 15f
                setTextColor(getColor(if (item.known) R.color.teal else R.color.rose))
                layoutParams = LinearLayout.LayoutParams(-2, -2)
            })
            box.addView(line)
        }
        (box.layoutParams as LinearLayout.LayoutParams).apply {
            topMargin = (-2).dp
            bottomMargin = 10.dp
        }
    }
}
