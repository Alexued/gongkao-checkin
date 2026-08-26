package com.gongkao.checkin.ui.pomodoro

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.gongkao.checkin.R
import com.gongkao.checkin.anim.Motion
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.POMODORO_LONG_BREAK
import com.gongkao.checkin.data.POMODORO_SHORT_BREAK
import com.gongkao.checkin.data.POMODORO_WORK
import com.gongkao.checkin.data.PomodoroSession
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.ui.AppDialog
import com.gongkao.checkin.ui.edgeToEdge
import com.gongkao.checkin.ui.open
import com.gongkao.checkin.ui.padTopInset
import com.gongkao.checkin.ui.tap
import com.gongkao.checkin.ui.toast

/**
 * 番茄钟：固定 25 分钟专注 / 5 分钟短休息 / 每 4 个专注后 15 分钟长休息。
 * 纯前台计时，不依赖系统通知栏，阶段结束时应用内提示音 + 震动。
 */
class PomodoroActivity : AppCompatActivity() {

    private lateinit var phaseText: TextView
    private lateinit var cycleText: TextView
    private lateinit var ringLabel: TextView
    private lateinit var timeText: TextView
    private lateinit var hintText: TextView
    private lateinit var btnStart: TextView
    private lateinit var btnSkip: TextView
    private lateinit var btnReset: TextView

    private val ui = Handler(Looper.getMainLooper())

    /** 当前阶段类型：WORK / SHORT_BREAK / LONG_BREAK。 */
    private var phaseKind = POMODORO_WORK
    /** 本大轮里已完成的专注次数，满 4 次触发长休息，然后清零。 */
    private var workCount = 0

    /** 当前阶段总时长（毫秒），随 phaseKind 变化。 */
    private var totalMs = WORK_MS

    /** 净运行毫秒 = accumulated + (now - runSince)，暂停时 runSince 为 0。 */
    private var accumulated = 0L
    private var runSince = 0L
    /** 本阶段真正开始计时的墙钟时间，0 表示还没开始，用于落记录。 */
    private var phaseStartAt = 0L

    private val running: Boolean get() = runSince > 0L
    private val started: Boolean get() = phaseStartAt > 0L

    private val elapsed: Long
        get() = accumulated + if (running) System.currentTimeMillis() - runSince else 0L

    private var toneGen: ToneGenerator? = null

    private val ticker = object : Runnable {
        override fun run() {
            paintClock()
            if (running) {
                if (elapsed >= totalMs) {
                    completePhase()
                } else {
                    ui.postDelayed(this, 200)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edgeToEdge()
        Repo.init(this)
        setContentView(R.layout.activity_pomodoro)

        (findViewById<android.view.View>(android.R.id.content).let {
            (it as android.view.ViewGroup).getChildAt(0)
        }).padTopInset()

        phaseText = findViewById(R.id.phaseText)
        cycleText = findViewById(R.id.cycleText)
        ringLabel = findViewById(R.id.ringLabel)
        timeText = findViewById(R.id.timeText)
        hintText = findViewById(R.id.hintText)
        btnStart = findViewById(R.id.btnStart)
        btnSkip = findViewById(R.id.btnSkip)
        btnReset = findViewById(R.id.btnReset)

        findViewById<android.widget.LinearLayout>(R.id.btnHistory).tap {
            open<PomodoroHistoryActivity>()
        }
        findViewById<TextView>(R.id.btnExit).tap { askQuit() }
        // 返回走同一套退出确认，不直接 finish，否则跑着的这一段悄悄没了
        findViewById<android.widget.ImageView>(R.id.btnBack).tap { askQuit() }
        btnStart.tap(haptic = false) { toggle() }
        btnSkip.tap(haptic = false) { skip() }
        btnReset.tap(haptic = false) { reset() }

        paintPhase()
        paintClock()
        paintButtons()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = askQuit()
        })
    }

    // ------------------------------------------------------------ 控制

    private fun toggle() {
        if (running) {
            accumulated = elapsed
            runSince = 0L
            Motion.tick(btnStart)
        } else {
            if (!started) phaseStartAt = System.currentTimeMillis()
            runSince = System.currentTimeMillis()
            Motion.confirm(btnStart)
            ui.post(ticker)
        }
        window.decorView.keepScreenOn = running
        paintClock()
        paintButtons()
    }

    /** 提前结束当前阶段：已开始过的记成未完成，未开始的直接跳过不落记录。 */
    private fun skip() {
        if (started) {
            saveSession(completed = false)
            Motion.tick(btnSkip)
        }
        advance()
    }

    /** 把当前阶段清回起点，不落记录（还没正式跑完，不算数）。 */
    private fun reset() {
        ui.removeCallbacks(ticker)
        accumulated = 0L
        runSince = 0L
        phaseStartAt = 0L
        window.decorView.keepScreenOn = false
        Motion.tick(btnReset)
        paintClock()
        paintButtons()
    }

    /** 阶段自然跑满：落记录、提示音+震动、自动切到下一阶段并暂停等待用户开始。 */
    private fun completePhase() {
        ui.removeCallbacks(ticker)
        accumulated = totalMs
        runSince = 0L
        window.decorView.keepScreenOn = false
        saveSession(completed = true)
        alert()
        toast(if (phaseKind == POMODORO_WORK) R.string.pomodoro_done_work else R.string.pomodoro_done_break)
        advance()
    }

    private fun advance() {
        phaseKind = when (phaseKind) {
            POMODORO_WORK -> {
                workCount++
                if (workCount % 4 == 0) POMODORO_LONG_BREAK else POMODORO_SHORT_BREAK
            }
            else -> POMODORO_WORK
        }
        totalMs = durationOf(phaseKind)
        accumulated = 0L
        runSince = 0L
        phaseStartAt = 0L
        paintPhase()
        paintClock()
        paintButtons()
    }

    private fun saveSession(completed: Boolean) {
        val now = System.currentTimeMillis()
        Repo.addPomodoroSession(
            PomodoroSession(
                id = Repo.newId(),
                kind = phaseKind,
                date = DateUtil.dateOf(phaseStartAt),
                startAt = phaseStartAt,
                endAt = now,
                durationMs = elapsed,
                completed = completed
            )
        )
    }

    // ------------------------------------------------------------ 提示音 + 震动

    private fun alert() {
        runCatching {
            val gen = toneGen ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90).also { toneGen = it }
            gen.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
        }
        runCatching {
            val vibrator = vibratorService() ?: return@runCatching
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 120, 200, 120, 300), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 200, 120, 200, 120, 300), -1)
            }
        }
    }

    private fun vibratorService(): Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    // ------------------------------------------------------------ 渲染

    private fun paintPhase() {
        phaseText.text = getString(
            when (phaseKind) {
                POMODORO_WORK -> R.string.pomodoro_work
                POMODORO_LONG_BREAK -> R.string.pomodoro_long_break
                else -> R.string.pomodoro_short_break
            }
        )
        cycleText.text = getString(R.string.pomodoro_cycle, workCount + 1)
        ringLabel.text = getString(
            when (phaseKind) {
                POMODORO_WORK -> R.string.pomodoro_work
                POMODORO_LONG_BREAK -> R.string.pomodoro_long_break
                else -> R.string.pomodoro_short_break
            }
        )
    }

    private fun paintClock() {
        val remain = (totalMs - elapsed).coerceAtLeast(0)
        val m = remain / 60000
        val s = (remain % 60000) / 1000
        timeText.text = String.format("%02d:%02d", m, s)
    }

    private fun paintButtons() {
        btnStart.text = getString(
            when {
                running -> R.string.pomodoro_pause
                started -> R.string.pomodoro_resume
                else -> R.string.pomodoro_start
            }
        )
        btnStart.setBackgroundResource(
            if (running) R.drawable.bg_btn_pause else R.drawable.bg_btn_start
        )
        hintText.text = if (started) "" else getString(R.string.pomodoro_ready, workCount + 1)
    }

    private fun toast(res: Int) = toast(getString(res))

    // ------------------------------------------------------------ 退出

    private fun askQuit() {
        if (!started) {
            finish()
            return
        }
        AppDialog.show(
            ctx = this,
            title = getString(R.string.quit),
            message = getString(R.string.pomodoro_quit_confirm),
            positive = getString(R.string.quit),
            negative = getString(R.string.keep_going),
            destructive = true
        ) {
            saveSession(completed = false)
            finish()
        }
    }

    override fun onDestroy() {
        ui.removeCallbacks(ticker)
        toneGen?.release()
        toneGen = null
        super.onDestroy()
    }

    companion object {
        const val WORK_MS = 25 * 60 * 1000L
        const val SHORT_BREAK_MS = 5 * 60 * 1000L
        const val LONG_BREAK_MS = 15 * 60 * 1000L

        fun durationOf(kind: String): Long = when (kind) {
            POMODORO_WORK -> WORK_MS
            POMODORO_LONG_BREAK -> LONG_BREAK_MS
            else -> SHORT_BREAK_MS
        }
    }
}
