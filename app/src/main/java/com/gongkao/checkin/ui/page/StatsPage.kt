package com.gongkao.checkin.ui.page

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.gongkao.checkin.R
import com.gongkao.checkin.anim.Motion
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.ui.MainActivity
import com.gongkao.checkin.ui.Page
import com.gongkao.checkin.ui.inflateChild
import com.gongkao.checkin.ui.open
import com.gongkao.checkin.ui.padTopInset
import com.gongkao.checkin.ui.show
import com.gongkao.checkin.ui.stats.DayDetailActivity
import com.gongkao.checkin.ui.tap
import com.gongkao.checkin.view.BarChartView
import com.gongkao.checkin.view.CalendarView

/** 统计页：四个数字 + 热力图 + 完成率柱图 + 用时对比，下面按天进入当日全部记录。 */
class StatsPage(host: MainActivity) : Page(host) {

    override val layoutRes = R.layout.fragment_stats

    private lateinit var statStreak: TextView
    private lateinit var statDone: TextView
    private lateinit var statRate: TextView
    private lateinit var statLeft: TextView
    private lateinit var heatmap: CalendarView
    private lateinit var chartRate: BarChartView
    private lateinit var chartTimer: BarChartView
    private lateinit var timerCompareSub: TextView
    private lateinit var timerEmpty: TextView
    private lateinit var dayList: LinearLayout

    /** 只在第一次绑定时播入场动效，数据刷新时直接落位。 */
    private var firstBind = true

    override fun onCreate(v: View) {
        statStreak = v.findViewById(R.id.statStreak)
        statDone = v.findViewById(R.id.statDone)
        statRate = v.findViewById(R.id.statRate)
        statLeft = v.findViewById(R.id.statLeft)
        heatmap = v.findViewById(R.id.heatmap)
        chartRate = v.findViewById(R.id.chartRate)
        chartTimer = v.findViewById(R.id.chartTimer)
        timerCompareSub = v.findViewById(R.id.timerCompareSub)
        timerEmpty = v.findViewById(R.id.timerEmpty)
        dayList = v.findViewById(R.id.dayList)

        (v as ViewGroup).getChildAt(0).padTopInset()

        chartTimer.lowerIsBetter = true
        // 完成率是百分比，纵轴固定 0~100%，不能按当期最大值缩放
        chartRate.fixedMax = 100f
        heatmap.onPick = { date -> openDay(date) }
    }

    override fun refresh() {
        val dates = Repo.recordedDates()

        statStreak.text = Repo.streak().toString()
        val fullDays = dates.count { Repo.day(it)?.allDone() == true }
        statDone.text = fullDays.toString()

        val rates = dates.mapNotNull { Repo.day(it) }.filter { it.total() > 0 }
        val avg = if (rates.isEmpty()) 0 else (rates.sumOf { it.ratio().toDouble() } / rates.size * 100).toInt()
        statRate.text = ctx.getString(R.string.stat_percent, avg)
        statLeft.text = Repo.daysLeft()?.coerceAtLeast(0L)?.toString() ?: "—"

        heatmap.setData(
            dates.associateWith { (Repo.day(it)?.ratio() ?: 0f) },
            animated = firstBind
        )

        bindRateChart()
        bindTimerChart()
        bindDays(dates.take(14))

        firstBind = false
    }

    /** 近 14 天完成率，缺席的日子也占一格（值为 0），才看得出断档。 */
    private fun bindRateChart() {
        val today = DateUtil.today()
        val bars = (13 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val rec = Repo.day(d.toString())
            BarChartView.Bar(
                label = "${d.dayOfMonth}",
                value = (rec?.ratio() ?: 0f) * 100f,
                highlight = back == 0
            )
        }
        chartRate.setBars(bars, animated = firstBind)
    }

    /** 用时对比：最近 12 组计时，越短越好，画一条平均线做参照。 */
    private fun bindTimerChart() {
        val sessions = Repo.timerSessions().take(12).reversed()
        timerEmpty.show(sessions.isEmpty())
        chartTimer.show(sessions.isNotEmpty())
        timerCompareSub.text = if (sessions.isEmpty()) {
            ""
        } else {
            ctx.getString(R.string.timer_compare_sub, sessions.size)
        }
        if (sessions.isEmpty()) {
            chartTimer.setBars(emptyList(), animated = false)
            return
        }
        val fastest = sessions.minByOrNull { it.durationMs }
        val bars = sessions.map { s ->
            BarChartView.Bar(
                label = DateUtil.clock(s.startAt),
                value = s.durationMs / 1000f,
                highlight = s === fastest
            )
        }
        chartTimer.average = sessions.sumOf { it.durationMs } / 1000f / sessions.size
        chartTimer.accentHi = ctx.getColor(R.color.teal)
        chartTimer.setBars(bars, animated = firstBind)
    }

    private fun bindDays(dates: List<String>) {
        if (dayList.childCount != dates.size) dayList.removeAllViews()
        dates.forEachIndexed { index, date ->
            val row = dayList.getChildAt(index)
                ?: dayList.inflateChild(R.layout.item_day_row).also { dayList.addView(it) }
            bindDayRow(row, date, index)
        }
    }

    private fun bindDayRow(row: View, date: String, index: Int) {
        val rec = Repo.day(date)
        row.findViewById<TextView>(R.id.dayTitle).text = DateUtil.prettyStr(date)

        val parts = mutableListOf<String>()
        if (rec != null && rec.total() > 0) {
            parts += ctx.getString(R.string.day_sub_tasks, rec.finished(), rec.total())
        }
        Repo.timerSessions().count { it.date == date }
            .takeIf { it > 0 }?.let { parts += ctx.getString(R.string.day_sub_timer, it) }
        Repo.percentSessions().count { it.date == date }
            .takeIf { it > 0 }?.let { parts += ctx.getString(R.string.day_sub_percent, it) }
        Repo.formulaSessions().count { it.date == date }
            .takeIf { it > 0 }?.let { parts += ctx.getString(R.string.day_sub_formula, it) }
        row.findViewById<TextView>(R.id.daySub).text =
            if (parts.isEmpty()) ctx.getString(R.string.day_sub_none) else parts.joinToString("・")

        val badge = row.findViewById<TextView>(R.id.dayBadge)
        val full = rec?.allDone() == true
        badge.show(full || (rec != null && rec.total() > 0))
        if (full) {
            badge.setText(R.string.badge_full)
            badge.setTextColor(ctx.getColor(R.color.teal))
        } else if (rec != null && rec.total() > 0) {
            badge.text = ctx.getString(R.string.stat_percent, (rec.ratio() * 100).toInt())
            badge.setTextColor(ctx.getColor(R.color.ink_sub))
        }

        row.tap { openDay(date) }
        if (firstBind) Motion.stagger(row, index)
    }

    private fun openDay(date: String) {
        ctx.open<DayDetailActivity>("date" to date)
    }
}
