package com.gongkao.checkin.ui.timer

import com.gongkao.checkin.R
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.ui.ListScreen
import com.gongkao.checkin.ui.open

/** 计时历史，按天分组，点进去看每段用时。 */
class TimerHistoryActivity : ListScreen() {

    override fun title(): CharSequence = getString(R.string.timer_history)

    override fun build() {
        val sessions = Repo.timerSessions()
        if (sessions.isEmpty()) {
            empty()
            return
        }
        sessions.groupBy { it.date }
            .toSortedMap(reverseOrder())
            .forEach { (date, list) ->
                section(DateUtil.prettyStr(date))
                list.sortedByDescending { it.startAt }.forEach { s ->
                    row(
                        title = s.label,
                        sub = getString(
                            R.string.timer_session_sub,
                            s.lapCount(),
                            DateUtil.clock(s.startAt)
                        ),
                        value = DateUtil.human(s.durationMs),
                        chevron = true
                    ) {
                        open<TimerDetailActivity>("id" to s.id)
                    }
                }
            }
    }
}
