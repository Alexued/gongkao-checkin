package com.gongkao.checkin.ui.percent

import android.widget.LinearLayout
import android.widget.TextView
import com.gongkao.checkin.R
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.PercentSession
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.ui.ListScreen
import com.gongkao.checkin.ui.dp
import com.gongkao.checkin.ui.inflateChild

/** 百化分记录。点一组展开逐题明细。 */
class PercentHistoryActivity : ListScreen() {

    /** 展开的组 id，重建时保持展开状态。 */
    private val expanded = mutableSetOf<String>()

    override fun title(): CharSequence = getString(R.string.percent_records)

    override fun build() {
        val sessions = Repo.percentSessions()
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

    private fun group(s: PercentSession) {
        val modeName = getString(
            if (s.mode == "RANDOM") R.string.mode_random_short else R.string.mode_full_short
        )
        row(
            title = getString(R.string.percent_session_sub, modeName, s.correctCount(), s.total()),
            sub = getString(R.string.percent_avg_sub, DateUtil.human(s.avgMs())),
            value = getString(R.string.stat_percent, (s.accuracy() * 100).toInt()),
            chevron = true
        ) {
            if (!expanded.remove(s.id)) expanded.add(s.id)
            rebuild()
        }
        if (s.id in expanded) details(s)
    }

    private fun details(s: PercentSession) {
        val box = card(14)
        s.items.forEach { item ->
            val v = box.inflateChild(R.layout.item_percent_result)
            v.findViewById<TextView>(R.id.pDisplay).text = item.display
            v.findViewById<TextView>(R.id.pAnswer).text = if (item.correct) {
                "${item.answerNum}/${item.answerDen}"
            } else {
                // 错的把「你写的 → 正确」都摊开，回看才有用
                "${item.answerNum}/${item.answerDen} → ${item.expectNum}/${item.expectDen}"
            }
            v.findViewById<TextView>(R.id.pMs).text = DateUtil.human(item.ms)
            v.findViewById<TextView>(R.id.pMark).apply {
                text = if (item.correct) "✓" else "✕"
                setTextColor(getColor(if (item.correct) R.color.teal else R.color.rose))
            }
            box.addView(v)
        }
        (box.layoutParams as LinearLayout.LayoutParams).apply {
            topMargin = (-2).dp
            bottomMargin = 10.dp
        }
    }
}
