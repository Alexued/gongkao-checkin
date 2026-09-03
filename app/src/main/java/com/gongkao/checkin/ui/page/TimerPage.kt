package com.gongkao.checkin.ui.page

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.gongkao.checkin.R
import com.gongkao.checkin.anim.Motion
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.Lap
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.data.TimerSession
import com.gongkao.checkin.ui.MainActivity
import com.gongkao.checkin.ui.Page
import com.gongkao.checkin.ui.dp
import com.gongkao.checkin.ui.inflateChild
import com.gongkao.checkin.ui.open
import com.gongkao.checkin.ui.padTopInset
import com.gongkao.checkin.ui.pomodoro.PomodoroActivity
import com.gongkao.checkin.ui.show
import com.gongkao.checkin.ui.tap
import com.gongkao.checkin.ui.timer.TimerHistoryActivity
import com.gongkao.checkin.ui.toast

/**
 * 做题计时。大号计时区 + 中下方大圆按钮，打点即时入列，
 * 结束时整段存成一个 TimerSession（含每个打点的间隔）。
 */
class TimerPage(host: MainActivity) : Page(host) {

    override val layoutRes = R.layout.fragment_timer

    private lateinit var labelText: TextView
    private lateinit var timeText: TextView
    private lateinit var splitText: TextView
    private lateinit var lapHint: TextView
    private lateinit var lapScroll: ScrollView
    private lateinit var lapList: LinearLayout
    private lateinit var btnLap: TextView
    private lateinit var btnStart: TextView
    private lateinit var btnFinish: TextView

    private val ui = Handler(Looper.getMainLooper())

    /** 净运行毫秒 = accumulated + (now - runSince)，暂停时 runSince 为 0。 */
    private var accumulated = 0L
    private var runSince = 0L
    private var startAt = 0L
    private val laps = mutableListOf<Lap>()

    private val running: Boolean get() = runSince > 0L
    private val started: Boolean get() = startAt > 0L

    private val elapsed: Long
        get() = accumulated + if (running) System.currentTimeMillis() - runSince else 0L

    private val ticker = object : Runnable {
        override fun run() {
            paintClock()
            if (running) ui.postDelayed(this, 33)
        }
    }

    override fun onCreate(v: View) {
        labelText = v.findViewById(R.id.labelText)
        timeText = v.findViewById(R.id.timeText)
        splitText = v.findViewById(R.id.splitText)
        lapHint = v.findViewById(R.id.lapHint)
        lapScroll = v.findViewById(R.id.lapScroll)
        lapList = v.findViewById(R.id.lapList)
        btnLap = v.findViewById(R.id.btnLap)
        btnStart = v.findViewById(R.id.btnStart)
        btnFinish = v.findViewById(R.id.btnFinish)

        (v as LinearLayout).getChildAt(0).padTopInset()

        v.findViewById<LinearLayout>(R.id.btnHistory).tap {
            ctx.startActivity(android.content.Intent(ctx, TimerHistoryActivity::class.java))
        }
        v.findViewById<LinearLayout>(R.id.btnPet).tap {
            ctx.startActivity(android.content.Intent(ctx, com.gongkao.checkin.ui.pet.PetActivity::class.java))
        }
        v.findViewById<LinearLayout>(R.id.btnPomodoro).tap {
            ctx.open<PomodoroActivity>()
        }
        btnStart.tap(haptic = false) { toggle() }
        btnLap.tap(haptic = false) { lap() }
        btnFinish.tap(haptic = false) { finish() }

        paintClock()
        paintButtons()
    }

    // ------------------------------------------------------------ 控制

    private fun toggle() {
        if (running) {
            accumulated = elapsed
            runSince = 0L
            Motion.tick(btnStart)
        } else {
            if (!started) startAt = System.currentTimeMillis()
            runSince = System.currentTimeMillis()
            Motion.confirm(btnStart)
            ui.post(ticker)
        }
        view.keepScreenOn = running
        paintClock()
        paintButtons()
    }

    private fun lap() {
        if (!started) return
        val at = elapsed
        val prev = laps.lastOrNull()?.atMs ?: 0L
        val lap = Lap(index = laps.size + 1, atMs = at, splitMs = at - prev)
        laps.add(lap)
        Motion.confirm(btnLap)
        addLapRow(lap, animated = true)
        splitText.text = ctx.getString(R.string.timer_last_split, DateUtil.stopwatch(lap.splitMs))
        lapHint.show(false)
        lapScroll.post { lapScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun finish() {
        if (!started) return
        val total = elapsed
        if (total < 1000) {
            reset()
            return
        }
        val now = System.currentTimeMillis()
        // 收尾：最后一个打点之后的那一段也算一次
        val tail = total - (laps.lastOrNull()?.atMs ?: 0L)
        if (tail > 400) laps.add(Lap(index = laps.size + 1, atMs = total, splitMs = tail))

        Repo.addTimerSession(
            TimerSession(
                id = Repo.newId(),
                label = ctx.getString(R.string.timer_session_label, DateUtil.clock(startAt)),
                date = DateUtil.dateOf(startAt),
                startAt = startAt,
                endAt = now,
                durationMs = total,
                laps = laps.toMutableList()
            )
        )
        Motion.confirm(btnFinish)
        ctx.toast(ctx.getString(R.string.timer_saved, DateUtil.human(total), laps.size))
        reset()
    }

    private fun reset() {
        ui.removeCallbacks(ticker)
        accumulated = 0L
        runSince = 0L
        startAt = 0L
        laps.clear()
        lapList.removeAllViews()
        lapHint.show(true)
        splitText.text = ""
        view.keepScreenOn = false
        paintClock()
        paintButtons()
    }

    // ------------------------------------------------------------ 渲染

    private fun paintClock() {
        timeText.text = DateUtil.stopwatch(elapsed)
    }

    private fun paintButtons() {
        btnStart.text = ctx.getString(
            when {
                running -> R.string.timer_pause
                started -> R.string.timer_resume
                else -> R.string.timer_start
            }
        )
        btnStart.setBackgroundResource(
            if (running) R.drawable.bg_btn_pause else R.drawable.bg_btn_start
        )
        // 没开始之前打点/结束都是灰的，避免误触
        listOf(btnLap, btnFinish).forEach {
            it.isEnabled = started
            it.alpha = if (started) 1f else 0.4f
        }
        labelText.text = if (started) {
            ctx.getString(R.string.timer_started_at, DateUtil.clockSec(startAt))
        } else {
            ctx.getString(R.string.timer_idle)
        }
    }

    private fun addLapRow(lap: Lap, animated: Boolean) {
        val row = lapList.inflateChild(R.layout.item_lap)
        row.findViewById<TextView>(R.id.idx).text = ctx.getString(R.string.lap_index, lap.index)
        row.findViewById<TextView>(R.id.split).text = DateUtil.stopwatch(lap.splitMs)
        row.findViewById<TextView>(R.id.at).text = DateUtil.stopwatch(lap.atMs)

        val badge = row.findViewById<TextView>(R.id.badge)
        // 打点数 >= 2 时标出目前最快/最慢的一段
        if (laps.size >= 2) {
            val fastest = laps.minByOrNull { it.splitMs }
            val slowest = laps.maxByOrNull { it.splitMs }
            when {
                lap === fastest -> tagBadge(badge, R.string.badge_fast, R.color.teal)
                lap === slowest -> tagBadge(badge, R.string.badge_slow, R.color.rose)
                else -> badge.show(false)
            }
        } else {
            badge.show(false)
        }

        lapList.addView(row)
        if (animated) {
            row.alpha = 0f
            row.translationY = 10f.dp
            row.animate().alpha(1f).translationY(0f)
                .setDuration(320).setInterpolator(Motion.SOFT).start()
        }
        repaintBadges()
    }

    private fun tagBadge(badge: TextView, textRes: Int, colorRes: Int) {
        badge.show(true)
        badge.setText(textRes)
        badge.setTextColor(ctx.getColor(colorRes))
    }

    /** 新打点可能改变最快/最慢，重刷所有行的标记。 */
    private fun repaintBadges() {
        if (laps.size < 2) return
        val fastest = laps.minByOrNull { it.splitMs }
        val slowest = laps.maxByOrNull { it.splitMs }
        for (i in 0 until lapList.childCount) {
            val badge = lapList.getChildAt(i).findViewById<TextView>(R.id.badge)
            val lap = laps.getOrNull(i) ?: continue
            when {
                lap === fastest -> tagBadge(badge, R.string.badge_fast, R.color.teal)
                lap === slowest -> tagBadge(badge, R.string.badge_slow, R.color.rose)
                else -> badge.show(false)
            }
        }
    }

    override fun refresh() {}

    override fun onShow() {
        if (running) ui.post(ticker)
    }

    override fun onDestroy() {
        ui.removeCallbacks(ticker)
    }
}
