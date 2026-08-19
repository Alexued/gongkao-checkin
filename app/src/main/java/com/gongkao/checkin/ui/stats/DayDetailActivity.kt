package com.gongkao.checkin.ui.stats

import com.gongkao.checkin.R
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.DayItem
import com.gongkao.checkin.data.DayRecord
import com.gongkao.checkin.data.KIND_CARRY
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.ui.ListScreen
import com.gongkao.checkin.ui.open
import com.gongkao.checkin.ui.timer.TimerDetailActivity

/** 某一天的全部记录：任务打卡 + 做题计时 + 百化分 + 公式背诵。 */
class DayDetailActivity : ListScreen() {

    private val date: String by lazy {
        intent.getStringExtra("date") ?: DateUtil.todayStr()
    }

    override fun title(): CharSequence = DateUtil.prettyStr(date)

    override fun build() {
        val rec = Repo.day(date)
        val timers = Repo.timerSessions().filter { it.date == date }
        val percents = Repo.percentSessions().filter { it.date == date }
        val formulas = Repo.formulaSessions().filter { it.date == date }
        val mentalMaths = Repo.mentalMathSessions().filter { it.date == date }

        val nothing = (rec == null || rec.items.isEmpty()) &&
            timers.isEmpty() && percents.isEmpty() && formulas.isEmpty() && mentalMaths.isEmpty()
        if (nothing) {
            empty(getString(R.string.day_sub_none))
            return
        }

        if (rec != null && rec.items.isNotEmpty()) tasks(rec)

        if (timers.isNotEmpty()) {
            section(getString(R.string.detail_timer))
            timers.sortedBy { it.startAt }.forEach { s ->
                row(
                    title = s.label,
                    sub = getString(R.string.timer_session_sub, s.lapCount(), DateUtil.clock(s.startAt)),
                    value = DateUtil.human(s.durationMs),
                    chevron = true
                ) { open<TimerDetailActivity>("id" to s.id) }
            }
        }

        if (percents.isNotEmpty()) {
            section(getString(R.string.detail_percent))
            percents.sortedBy { it.startAt }.forEach { s ->
                val modeName = getString(
                    if (s.mode == "RANDOM") R.string.mode_random_short else R.string.mode_full_short
                )
                row(
                    title = getString(
                        R.string.percent_session_sub, modeName, s.correctCount(), s.total()
                    ),
                    sub = getString(R.string.percent_avg_sub, DateUtil.human(s.avgMs())),
                    value = getString(R.string.stat_percent, (s.accuracy() * 100).toInt())
                )
            }
        }

        if (formulas.isNotEmpty()) {
            section(getString(R.string.detail_formula))
            formulas.sortedBy { it.startAt }.forEach { s ->
                val modeName = getString(
                    if (s.mode == "RANDOM") R.string.mode_random_short else R.string.mode_full_short
                )
                row(
                    title = getString(
                        R.string.formula_session_sub, modeName, s.knownCount(), s.total()
                    ),
                    sub = "${s.category} · ${DateUtil.human(s.durationMs())}",
                    value = getString(R.string.stat_percent, (s.accuracy() * 100).toInt())
                )
            }
        }

        if (mentalMaths.isNotEmpty()) {
            section(getString(R.string.detail_mental_math))
            mentalMaths.sortedBy { it.startAt }.forEach { s ->
                val modeName = getString(
                    if (s.mode == "RANDOM") R.string.mode_random_short else R.string.mode_full_short
                )
                row(
                    title = getString(
                        R.string.formula_session_sub, modeName, s.knownCount(), s.total()
                    ),
                    sub = "${s.category} · ${DateUtil.human(s.durationMs())}",
                    value = getString(R.string.stat_percent, (s.accuracy() * 100).toInt())
                )
            }
        }
    }

    private fun tasks(rec: DayRecord) {
        section(getString(R.string.detail_tasks))
        val box = card()
        kv(
            getString(R.string.today_progress, rec.finished(), rec.total()),
            getString(R.string.stat_percent, (rec.ratio() * 100).toInt()),
            box
        )
        Repo.sortedItems(rec).forEach { item ->
            row(
                title = item.title,
                sub = itemSub(item),
                value = if (item.target > 1) "${item.progress}/${item.target}" else null,
                chevron = false
            )
        }
    }

    private fun itemSub(item: DayItem): String = buildString {
        if (item.kind == KIND_CARRY) {
            // 历史里看欠账要按「当天」算天数，不能按今天
            val days = item.oldestDebtDate?.let { from ->
                val a = DateUtil.parse(from)
                val b = DateUtil.parse(date)
                if (a != null && b != null) DateUtil.daysBetween(a, b) else null
            } ?: 1L
            append(getString(R.string.tag_carry, days.coerceAtLeast(1)))
        }
        if (item.unit.isNotBlank()) {
            if (isNotEmpty()) append(" · ")
            append(item.unit)
        }
        if (item.orphan) {
            if (isNotEmpty()) append(" · ")
            append(getString(R.string.tag_orphan))
        }
        if (item.done && item.doneAt > 0) {
            if (isNotEmpty()) append(" · ")
            append(getString(R.string.tag_done_at, DateUtil.clock(item.doneAt)))
        } else if (!item.done) {
            if (isNotEmpty()) append(" · ")
            append(getString(R.string.detail_undone))
        }
    }
}
