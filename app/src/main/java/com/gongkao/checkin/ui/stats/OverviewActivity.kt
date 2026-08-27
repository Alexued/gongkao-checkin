package com.gongkao.checkin.ui.stats

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gongkao.checkin.R
import com.gongkao.checkin.anim.Motion
import com.gongkao.checkin.data.AppMode
import com.gongkao.checkin.data.Capability
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.Overview
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.data.SuggestGo
import com.gongkao.checkin.data.SuggestKind
import com.gongkao.checkin.data.Track
import com.gongkao.checkin.ui.MainActivity
import com.gongkao.checkin.ui.dp
import com.gongkao.checkin.ui.Themes
import com.gongkao.checkin.ui.edgeToEdge
import com.gongkao.checkin.ui.padTopInset
import com.gongkao.checkin.ui.show
import com.gongkao.checkin.ui.tap
import com.gongkao.checkin.view.RingProgressView

/**
 * 学习概览 / 节律概览。把「今天该干什么」压缩成一张建议卡，
 * 其余是当日指标、双轨进度和能力画像。今日页只管打卡，算账挪到这里。
 */
class OverviewActivity : AppCompatActivity() {

    private val date: String by lazy { intent.getStringExtra("date") ?: DateUtil.todayStr() }
    private val mode: AppMode by lazy { Repo.appMode() }

    private lateinit var ring: RingProgressView
    private lateinit var ringText: TextView
    private lateinit var dateText: TextView
    private lateinit var progressText: TextView
    private lateinit var metricRow: LinearLayout
    private lateinit var suggestTitle: TextView
    private lateinit var suggestSub: TextView
    private lateinit var btnSuggest: TextView
    private lateinit var trackCard: View
    private lateinit var trackBox: LinearLayout
    private lateinit var capBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edgeToEdge()
        Repo.init(this)
        Themes.apply(this)
        setContentView(R.layout.activity_overview)

        findViewById<View>(R.id.topBar).padTopInset()
        findViewById<TextView>(R.id.barTitle).text = getString(
            if (mode.isGeneral) R.string.overview_title_general else R.string.overview_title_exam
        )
        findViewById<ImageView>(R.id.btnBack).tap { finish() }

        ring = findViewById(R.id.ring)
        ringText = findViewById(R.id.ringText)
        dateText = findViewById(R.id.dateText)
        progressText = findViewById(R.id.progressText)
        metricRow = findViewById(R.id.metricRow)
        suggestTitle = findViewById(R.id.suggestTitle)
        suggestSub = findViewById(R.id.suggestSub)
        btnSuggest = findViewById(R.id.btnSuggest)
        trackCard = findViewById(R.id.trackCard)
        trackBox = findViewById(R.id.trackBox)
        capBox = findViewById(R.id.capBox)

        ring.trackColor = getColor(R.color.surface_alt)
        ring.startColor = getColor(R.color.accent)
        ring.endColor = getColor(R.color.teal)
    }

    override fun onResume() {
        super.onResume()
        bind()
    }

    private fun bind() {
        val rec = Repo.day(date)
        val items = rec?.let { Repo.sortedItems(it) } ?: emptyList()
        val doneCount = items.count { it.done }

        dateText.text = DateUtil.prettyStr(date)
        findViewById<View>(R.id.readonlyText).show(date != DateUtil.todayStr())

        val ratio = rec?.ratio() ?: 0f
        ring.setProgress(ratio, animated = true)
        ringText.text = "${(ratio * 100).toInt()}%"
        progressText.text = if (items.isEmpty()) {
            getString(R.string.overview_progress_none)
        } else {
            getString(R.string.overview_progress, (ratio * 100).toInt())
        }

        bindMetrics(items.size, doneCount)
        bindSuggestion()
        bindTracks()
        bindCapabilities()
    }

    private fun bindMetrics(itemCount: Int, @Suppress("UNUSED_PARAMETER") doneCount: Int) {
        val focus = Overview.focusMinutes(date)
        val free = (Overview.FOCUS_CEILING_MIN - focus).coerceAtLeast(0)
        metricRow.removeAllViews()
        val cells = mutableListOf(
            getString(R.string.overview_metric_items) to itemCount.toString(),
            getString(R.string.overview_metric_focus) to getString(R.string.overview_minutes, focus)
        )
        // 复盘是考公模式的训练概念，通用模式不提
        if (!mode.isGeneral) {
            cells += getString(R.string.overview_metric_review) to Overview.reviewCount(date).toString()
        }
        cells += getString(R.string.overview_metric_free) to getString(R.string.overview_minutes, free)

        cells.forEachIndexed { i, (name, value) ->
            if (i > 0) metricRow.addView(divider())
            metricRow.addView(metricCell(name, value))
        }
    }

    private fun metricCell(name: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        addView(TextView(this@OverviewActivity).apply {
            text = value
            textSize = 17f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(getColor(R.color.ink))
            gravity = Gravity.CENTER
        })
        addView(TextView(this@OverviewActivity).apply {
            text = name
            textSize = 11.5f
            setTextColor(getColor(R.color.ink_dim))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = 4.dp }
        })
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(getColor(R.color.divider))
        layoutParams = LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT).apply {
            topMargin = 4.dp
            bottomMargin = 4.dp
        }
    }

    private fun bindSuggestion() {
        val s = Overview.suggestion(date, mode)
        when (s.kind) {
            SuggestKind.CARRY -> {
                suggestTitle.text = getString(R.string.overview_suggest_carry, s.taskTitle)
                suggestSub.text = getString(R.string.overview_suggest_carry_sub, s.count)
                suggestTitle.setTextColor(getColor(R.color.carry_ink))
            }
            SuggestKind.NEXT -> {
                suggestTitle.text = getString(R.string.overview_suggest_next, s.taskTitle)
                suggestSub.text = getString(R.string.overview_suggest_next_sub, s.count)
                suggestTitle.setTextColor(getColor(R.color.ink))
            }
            SuggestKind.REVIEW -> {
                suggestTitle.text = getString(R.string.overview_suggest_review)
                suggestSub.text = getString(R.string.overview_suggest_review_sub)
                suggestTitle.setTextColor(getColor(R.color.ink))
            }
            SuggestKind.DONE -> {
                suggestTitle.text = getString(R.string.overview_suggest_done)
                suggestSub.text = getString(R.string.overview_suggest_done_sub)
                suggestTitle.setTextColor(getColor(R.color.teal))
            }
            SuggestKind.EMPTY -> {
                suggestTitle.text = getString(R.string.overview_suggest_empty)
                suggestSub.text = getString(R.string.overview_suggest_empty_sub)
                suggestTitle.setTextColor(getColor(R.color.ink))
            }
        }
        btnSuggest.text = getString(
            when (s.go) {
                SuggestGo.TIMER -> R.string.overview_go_timer
                SuggestGo.REVIEW -> R.string.overview_go_review
                SuggestGo.TASKS -> R.string.overview_go_tasks
                SuggestGo.STATS -> R.string.overview_go_stats
            }
        )
        btnSuggest.tap { goTo(s.go) }
    }

    /** 建议卡的按钮：都回到主界面的对应 tab，概览页本身收起来。 */
    private fun goTo(go: SuggestGo) {
        val tab = when (go) {
            SuggestGo.TIMER -> MainActivity.TAB_TIMER
            SuggestGo.REVIEW -> MainActivity.TAB_RECITE
            SuggestGo.TASKS -> MainActivity.TAB_TODAY
            SuggestGo.STATS -> MainActivity.TAB_STATS
        }
        startActivity(
            android.content.Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAB, tab)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    private fun bindTracks() {
        val tracks = Overview.tracks(
            date, mode,
            getString(if (mode.isGeneral) R.string.overview_track_general_a else R.string.overview_track_exam_a),
            getString(if (mode.isGeneral) R.string.overview_track_general_b else R.string.overview_track_exam_b)
        )
        trackCard.show(tracks.any { it.total > 0 })
        trackBox.removeAllViews()
        tracks.filter { it.total > 0 }.forEachIndexed { i, t -> trackBox.addView(trackRow(t, i)) }
    }

    private fun trackRow(t: Track, index: Int): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = if (index == 0) 0 else 14.dp
            }
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        head.addView(TextView(this).apply {
            text = t.name
            textSize = 13.5f
            setTextColor(getColor(R.color.ink))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        head.addView(TextView(this).apply {
            text = getString(R.string.overview_track_done, t.done, t.total)
            textSize = 12.5f
            setTextColor(getColor(R.color.ink_dim))
        })
        box.addView(head)

        // 进度条：底槽当背景，前景按比例用 weight 撑开
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, 8.dp).apply { topMargin = 8.dp }
            setBackgroundColor(getColor(R.color.surface_alt))
        }
        val fill = View(this).apply {
            setBackgroundColor(getColor(R.color.accent))
            layoutParams = LinearLayout.LayoutParams(0, -1, t.ratio.coerceIn(0.001f, 1f))
        }
        val rest = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, -1, (1f - t.ratio).coerceIn(0f, 0.999f))
        }
        holder.addView(fill)
        holder.addView(rest)
        box.addView(holder)
        Motion.stagger(box, index)
        return box
    }

    private fun bindCapabilities() {
        val (done, total) = Overview.doneRatio()
        val focus = Overview.focusMinutes(date)
        val caps = mutableListOf(
            Capability(
                getString(if (mode.isGeneral) R.string.overview_cap_done_general else R.string.overview_cap_done_exam),
                percent(done, total),
                "$done / $total"
            ),
            Capability(
                getString(R.string.overview_cap_focus),
                getString(R.string.overview_minutes, focus),
                percent(focus, Overview.FOCUS_CEILING_MIN)
            )
        )
        caps += if (mode.isGeneral) {
            val n = Overview.activeTaskCount()
            Capability(getString(R.string.overview_cap_active), n.toString(), "")
        } else {
            val (k, t) = Overview.mentalMathRatio()
            Capability(getString(R.string.overview_cap_drill), percent(k, t), "$k / $t")
        }
        caps += Capability(
            getString(R.string.overview_metric_review),
            Overview.reviewCount(date).toString(),
            ""
        )

        capBox.removeAllViews()
        caps.chunked(2).forEachIndexed { rowIndex, pair ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = if (rowIndex == 0) 0 else 12.dp
                }
            }
            pair.forEach { row.addView(capCell(it)) }
            if (pair.size == 1) row.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            capBox.addView(row)
        }
    }

    private fun capCell(c: Capability) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        addView(TextView(this@OverviewActivity).apply {
            text = c.name
            textSize = 11.5f
            setTextColor(getColor(R.color.ink_dim))
        })
        addView(TextView(this@OverviewActivity).apply {
            text = c.value
            textSize = 16f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(getColor(R.color.ink))
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = 3.dp }
        })
        if (c.detail.isNotBlank()) {
            addView(TextView(this@OverviewActivity).apply {
                text = c.detail
                textSize = 11f
                setTextColor(getColor(R.color.ink_dim))
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = 2.dp }
            })
        }
    }

    private fun percent(part: Int, whole: Int): String =
        if (whole <= 0) "—" else getString(R.string.stat_percent, part * 100 / whole)
}
