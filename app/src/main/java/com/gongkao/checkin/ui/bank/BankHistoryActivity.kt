package com.gongkao.checkin.ui.bank

import android.widget.LinearLayout
import android.widget.TextView
import com.gongkao.checkin.R
import com.gongkao.checkin.data.BankSession
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.ui.ListScreen
import com.gongkao.checkin.ui.open
import com.gongkao.checkin.ui.dp

/** 技巧复盘记录。点一组展开每题的作答，跳过的题标「—」。结构照抄 MentalMathHistoryActivity。 */
class BankHistoryActivity : ListScreen() {

    private val expanded = mutableSetOf<String>()

    override fun title(): CharSequence = getString(R.string.bank_records)

    override fun build() {
        val sessions = Repo.bankSessions()
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

    private fun group(s: BankSession) {
        val modeName = getString(
            if (s.mode == "RANDOM") R.string.mode_random_short else R.string.mode_full_short
        )
        row(
            title = getString(R.string.bank_session_sub, modeName, s.correctCount(), s.total()),
            // 用整轮的墙钟时间：改成一题一页后不再记每题耗时，durationMs() 会是 0
            sub = DateUtil.human((s.endAt - s.startAt).coerceAtLeast(0)),
            value = getString(R.string.stat_percent, (s.accuracy() * 100).toInt()),
            chevron = true
        ) {
            if (!expanded.remove(s.id)) expanded.add(s.id)
            rebuild()
        }
        if (s.id in expanded) {
            details(s)
            // 展开后再给一条入口，回头能重看整轮的逐题解析
            row(
                title = getString(R.string.bank_review_open),
                chevron = true
            ) { open<BankReviewActivity>("sessionId" to s.id) }
        }
    }

    private fun details(s: BankSession) {
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
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(getColor(R.color.ink))
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            line.addView(TextView(this).apply {
                // 跳过的题没有选项，显示正确答案本身
                text = if (item.picked.isBlank()) item.answer else "${item.picked}→${item.answer}"
                textSize = 12f
                setTextColor(getColor(R.color.ink_dim))
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = 8.dp }
            })
            line.addView(TextView(this).apply {
                text = when {
                    item.picked.isBlank() -> "—"
                    item.correct -> "✓"
                    else -> "✕"
                }
                textSize = 15f
                setTextColor(
                    getColor(
                        when {
                            item.picked.isBlank() -> R.color.ink_dim
                            item.correct -> R.color.teal
                            else -> R.color.rose
                        }
                    )
                )
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
