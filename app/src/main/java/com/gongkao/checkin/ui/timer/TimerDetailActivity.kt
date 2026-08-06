package com.gongkao.checkin.ui.timer

import android.app.AlertDialog
import android.widget.TextView
import com.gongkao.checkin.ui.AppDialog
import com.gongkao.checkin.R
import com.gongkao.checkin.anim.Motion
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.data.TimerSession
import com.gongkao.checkin.ui.ListScreen
import com.gongkao.checkin.ui.inflateChild
import com.gongkao.checkin.ui.show
import com.gongkao.checkin.ui.toast

/** 单组计时详情：汇总 + 每一段用时。 */
class TimerDetailActivity : ListScreen() {

    private val sessionId: String by lazy { intent.getStringExtra("id") ?: "" }

    /** 删除后 Repo 会回调，这里已经 finish 了，不需要再重建。 */
    override val liveUpdate = false

    override fun title(): CharSequence = getString(R.string.timer_detail_title)

    override fun build() {
        val s = Repo.timerSession(sessionId)
        if (s == null) {
            empty()
            return
        }
        section(s.label)
        summary(s)
        laps(s)
        action(getString(R.string.delete)) { confirmDelete() }
    }

    private fun summary(s: TimerSession) {
        val box = card()
        kv(getString(R.string.summary_total), DateUtil.stopwatch(s.durationMs), box)
        kv(getString(R.string.summary_laps), s.lapCount().toString(), box)
        if (s.laps.isNotEmpty()) {
            kv(getString(R.string.summary_avg), DateUtil.stopwatch(s.avgLapMs()), box)
            s.fastestLap()?.let {
                kv(getString(R.string.summary_fastest), DateUtil.stopwatch(it.splitMs), box)
            }
            s.slowestLap()?.let {
                kv(getString(R.string.summary_slowest), DateUtil.stopwatch(it.splitMs), box)
            }
        }
        kv(getString(R.string.summary_start), DateUtil.clockSec(s.startAt), box)
    }

    private fun laps(s: TimerSession) {
        if (s.laps.isEmpty()) return
        section(getString(R.string.summary_laps))
        val fastest = s.fastestLap()?.index
        val slowest = s.slowestLap()?.index
        s.laps.forEach { lap ->
            val v = content.inflateChild(R.layout.item_lap)
            v.findViewById<TextView>(R.id.idx).text = getString(R.string.lap_index, lap.index)
            v.findViewById<TextView>(R.id.split).text = DateUtil.stopwatch(lap.splitMs)
            v.findViewById<TextView>(R.id.at).text = DateUtil.stopwatch(lap.atMs)
            v.findViewById<TextView>(R.id.badge).apply {
                // 只有两段以上才谈快慢，一段的时候标最快没意义
                val tag = when {
                    s.laps.size < 2 -> null
                    lap.index == fastest -> getString(R.string.badge_fast)
                    lap.index == slowest -> getString(R.string.badge_slow)
                    else -> null
                }
                show(tag != null)
                text = tag ?: ""
                if (tag != null) {
                    setTextColor(
                        getColor(if (lap.index == fastest) R.color.teal else R.color.rose)
                    )
                }
            }
            content.addView(v)
            Motion.stagger(v, content.childCount - 1)
        }
    }

    private fun confirmDelete() {
        AppDialog.show(
            ctx = this,
            title = getString(R.string.delete),
            message = getString(R.string.confirm_delete),
            positive = getString(R.string.delete),
            negative = getString(R.string.cancel),
            destructive = true
        ) {
            Repo.deleteTimerSession(sessionId)
            toast(getString(R.string.deleted))
            finish()
        }
    }
}
