package com.gongkao.checkin.ui.pomodoro

import com.gongkao.checkin.R
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.POMODORO_LONG_BREAK
import com.gongkao.checkin.data.POMODORO_SHORT_BREAK
import com.gongkao.checkin.data.POMODORO_WORK
import com.gongkao.checkin.data.PomodoroSession
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.ui.ListScreen

/** 番茄钟记录，按天分组，只列专注段（休息段不单独展示，避免噪音）。 */
class PomodoroHistoryActivity : ListScreen() {

    override fun title(): CharSequence = getString(R.string.pomodoro_records)

    override fun build() {
        val sessions = Repo.pomodoroSessions().filter { it.kind == POMODORO_WORK }
        if (sessions.isEmpty()) {
            empty()
            return
        }
        sessions.groupBy { it.date }
            .toSortedMap(reverseOrder())
            .forEach { (date, list) ->
                section(DateUtil.prettyStr(date))
                list.sortedByDescending { it.startAt }.forEach { s -> row(s) }
            }
    }

    private fun row(s: PomodoroSession) {
        row(
            title = kindName(s.kind),
            sub = getString(
                R.string.pomodoro_session_sub,
                DateUtil.clock(s.startAt),
                if (s.completed) DateUtil.human(s.durationMs) else getString(R.string.pomodoro_incomplete)
            ),
            value = DateUtil.human(s.durationMs)
        )
    }

    private fun kindName(kind: String) = getString(
        when (kind) {
            POMODORO_LONG_BREAK -> R.string.pomodoro_long_break
            POMODORO_SHORT_BREAK -> R.string.pomodoro_short_break
            else -> R.string.pomodoro_work
        }
    )
}
