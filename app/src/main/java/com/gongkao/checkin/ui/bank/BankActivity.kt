package com.gongkao.checkin.ui.bank

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.gongkao.checkin.R
import com.gongkao.checkin.anim.Motion
import com.gongkao.checkin.data.BankData
import com.gongkao.checkin.data.BankItemRecord
import com.gongkao.checkin.data.BankProgress
import com.gongkao.checkin.data.BankQuestion
import com.gongkao.checkin.data.BankSession
import com.gongkao.checkin.data.BankSource
import com.gongkao.checkin.data.BankSources
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.ui.AppDialog
import com.gongkao.checkin.ui.AppListDialog
import com.gongkao.checkin.ui.DialogRow
import com.gongkao.checkin.ui.Popup
import com.gongkao.checkin.ui.dp
import com.gongkao.checkin.ui.edgeToEdge
import com.gongkao.checkin.ui.open
import com.gongkao.checkin.ui.padBottomInset
import com.gongkao.checkin.ui.padTopInset
import com.gongkao.checkin.ui.show
import com.gongkao.checkin.ui.tap
import com.gongkao.checkin.ui.toast
import com.gongkao.checkin.ui.transparentBlack
import com.gongkao.checkin.view.CelebrationView

/**
 * 资料分析复盘的做题页。一题一页可左右滑，答题卡看进度，退出时可存档续做。
 *
 * 三种进入方式：新开一轮（chapter+source+size）、续做存档（resume=1）、
 * 从搜索结果单题复盘（questionId）。
 */
class BankActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var celebration: CelebrationView
    private lateinit var pager: ViewPager
    private lateinit var modeText: TextView
    private lateinit var progressText: TextView
    private lateinit var progressBar: View
    private lateinit var btnAction: TextView
    private lateinit var btnSheet: TextView

    private lateinit var source: BankSource
    private var chapter = BankData.ALL
    private var singleId: String? = null
    private var resuming = false

    private var queue: List<BankQuestion> = emptyList()
    /** 题 id → 选的选项。答题卡和存档都看它。 */
    private val answers = linkedMapOf<String, String>()
    private val pages = mutableMapOf<Int, BankQuestionView>()
    private var sessionStart = 0L
    private var saved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edgeToEdge()
        Repo.init(this)
        singleId = intent.getStringExtra("questionId")
        resuming = intent.getStringExtra("resume") == "1"

        root = FrameLayout(this)
        layoutInflater.inflate(R.layout.activity_bank, root, true)
        celebration = CelebrationView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            isClickable = false
        }
        root.addView(celebration)
        setContentView(root)

        bind()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = askQuit()
        })

        val stored = if (resuming) Repo.bankProgress() else null
        source = when {
            stored != null -> BankSources.byId(stored.sourceId)
            else -> BankSources.byId(intent.getStringExtra("source"))
        }
        chapter = stored?.chapter ?: intent.getStringExtra("chapter") ?: BankData.ALL

        modeText.text = getString(R.string.bank_loading)
        btnAction.isEnabled = false
        btnAction.alpha = 0.4f
        BankData.loadAsync(this, source) { all ->
            if (isFinishing || isDestroyed) return@loadAsync
            start(all, stored)
        }
    }

    private fun bind() {
        val page = root.getChildAt(0) as ViewGroup
        page.getChildAt(0).padTopInset()
        pager = findViewById(R.id.questionPager)
        modeText = findViewById(R.id.modeText)
        progressText = findViewById(R.id.progressText)
        progressBar = findViewById(R.id.progressBar)
        btnAction = findViewById(R.id.btnAction)
        btnSheet = findViewById(R.id.btnSheet)
        findViewById<LinearLayout>(R.id.actionRow).padBottomInset(18.dp)

        findViewById<TextView>(R.id.btnExit).tap { askQuit() }
        btnSheet.tap { showSheet() }
        btnAction.tap { onAction() }
        progressBar.pivotX = 0f
    }

    // ------------------------------------------------------------ 组卷

    private fun start(all: List<BankQuestion>, stored: BankProgress?) {
        val id = singleId
        queue = when {
            id != null -> all.filter { it.id == id }
            stored != null -> {
                // 按存档里的顺序还原，题库里已经没有的 id 直接跳过
                val byId = all.associateBy { it.id }
                stored.questionIds.mapNotNull { byId[it] }
            }
            else -> {
                val pool = BankData.byChapter(all, chapter).shuffled()
                val size = intent.getStringExtra("size")?.toIntOrNull() ?: Repo.bankBatchSize()
                if (size <= 0) pool else pool.take(size)
            }
        }
        if (queue.isEmpty()) {
            toast(getString(R.string.bank_search_none))
            finish()
            return
        }

        answers.clear()
        if (stored != null) {
            // 只回填仍在题里的作答
            val live = queue.map { it.id }.toSet()
            stored.answers.forEach { (qid, picked) -> if (qid in live) answers[qid] = picked }
        }
        sessionStart = stored?.startAt?.takeIf { it > 0 } ?: System.currentTimeMillis()

        btnAction.isEnabled = true
        btnAction.alpha = 1f
        modeText.text = when {
            singleId != null -> getString(R.string.bank_single)
            stored != null -> getString(R.string.bank_resume)
            else -> getString(R.string.bank_start)
        }
        btnSheet.show(singleId == null)

        pager.adapter = QuestionAdapter()
        pager.addOnPageChangeListener(pageListener)
        val startAt = (stored?.cursor ?: 0).coerceIn(0, queue.size - 1)
        pager.setCurrentItem(startAt, false)
        paintHeader(startAt)
    }

    private inner class QuestionAdapter : PagerAdapter() {

        override fun getCount() = queue.size

        override fun isViewFromObject(view: View, obj: Any) = view === obj

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val q = queue[position]
            val holder = BankQuestionView(this@BankActivity, q, answers[q.id]) { qid, picked, correct ->
                answers[qid] = picked
                onAnswer(correct)
            }
            pages[position] = holder
            container.addView(holder.root, ViewGroup.LayoutParams(-1, -1))
            return holder.root
        }

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
            container.removeView(obj as View)
            pages.remove(position)
        }
    }

    private val pageListener = object : ViewPager.SimpleOnPageChangeListener() {
        override fun onPageSelected(position: Int) = paintHeader(position)
    }

    // ------------------------------------------------------------ 头部与按钮

    private fun paintHeader(position: Int) {
        progressText.text = getString(R.string.progress_of, position + 1, queue.size)
        Motion.springTo(
            progressBar, DynamicAnimation.SCALE_X,
            (answers.size.toFloat() / queue.size).coerceIn(0f, 1f)
        )
        paintAction(position)
    }

    private fun paintAction(position: Int) {
        val holder = pages[position]
        val last = position == queue.size - 1
        when {
            holder == null -> btnAction.text = getString(R.string.bank_swipe_hint)
            holder.answered == null -> {
                btnAction.text = getString(R.string.bank_swipe_hint)
                btnAction.setBackgroundResource(R.drawable.bg_btn_ghost)
                btnAction.setTextColor(getColor(R.color.ink_sub))
            }
            holder.hasMoreExplain() -> {
                btnAction.text = holder.explainLabel()
                btnAction.setBackgroundResource(R.drawable.bg_btn_primary)
                btnAction.setTextColor(getColor(R.color.surface))
            }
            last -> {
                btnAction.text = getString(R.string.bank_submit)
                btnAction.setBackgroundResource(R.drawable.bg_btn_primary)
                btnAction.setTextColor(getColor(R.color.surface))
            }
            else -> {
                btnAction.text = getString(R.string.bank_next_question)
                btnAction.setBackgroundResource(R.drawable.bg_btn_primary)
                btnAction.setTextColor(getColor(R.color.surface))
            }
        }
    }

    private fun onAction() {
        val pos = pager.currentItem
        val holder = pages[pos] ?: return
        when {
            holder.answered == null -> Unit // 还没选，按钮只是提示滑动
            holder.hasMoreExplain() -> {
                holder.explainNext()
                paintAction(pos)
            }
            pos < queue.size - 1 -> pager.setCurrentItem(pos + 1, true)
            else -> submit()
        }
    }

    private fun onAnswer(correct: Boolean) {
        val pos = pager.currentItem
        paintHeader(pos)
        if (correct) {
            val holder = pages[pos] ?: return
            val loc = IntArray(2)
            val base = IntArray(2)
            holder.root.getLocationInWindow(loc)
            root.getLocationInWindow(base)
            celebration.burstAt(
                (loc[0] - base[0] + holder.root.width / 2f),
                (loc[1] - base[1] + holder.root.height * 0.4f),
                10
            )
        }
    }

    // ------------------------------------------------------------ 答题卡

    private fun showSheet() {
        val v = layoutInflater.inflate(R.layout.dialog_bank_sheet, null)
        val scrim = v.findViewById<View>(R.id.sheetScrim)
        val card = v.findViewById<View>(R.id.sheetCard)
        val grid = v.findViewById<GridLayout>(R.id.sheetGrid)
        val d = Popup.dialog(this, v)
        Popup.wireDismiss(d, scrim, card)

        v.findViewById<TextView>(R.id.sheetSub).text =
            getString(R.string.bank_sheet_sub, answers.size, queue.size)
        v.findViewById<TextView>(R.id.legendRight).setText(R.string.bank_legend_right)
        v.findViewById<TextView>(R.id.legendWrong).setText(R.string.bank_legend_wrong)
        v.findViewById<TextView>(R.id.legendBlank).setText(R.string.bank_legend_blank)

        queue.forEachIndexed { i, q ->
            val picked = answers[q.id]
            val cell = TextView(this).apply {
                text = (i + 1).toString()
                textSize = 13.5f
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setBackgroundResource(
                    when {
                        picked == null -> R.drawable.bg_option
                        picked == q.answer -> R.drawable.bg_option_correct
                        else -> R.drawable.bg_option_wrong
                    }
                )
                setTextColor(
                    getColor(
                        when {
                            picked == null -> R.color.ink_sub
                            picked == q.answer -> R.color.teal
                            else -> R.color.rose
                        }
                    )
                )
                // 当前题加一圈提示，知道自己在哪
                if (i == pager.currentItem) {
                    setTypeface(typeface, Typeface.BOLD)
                }
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 44.dp
                    height = 44.dp
                    setMargins(5.dp, 5.dp, 5.dp, 5.dp)
                }
                tap {
                    Popup.close(d)
                    pager.setCurrentItem(i, true)
                }
            }
            grid.addView(cell)
        }

        v.findViewById<TextView>(R.id.btnSheetSubmit).tap {
            Popup.close(d)
            submit()
        }
        d.show()
        Popup.enter(scrim, card)
    }

    // ------------------------------------------------------------ 交卷 / 存档

    private fun submit() {
        val blank = queue.size - answers.size
        if (blank > 0) {
            AppDialog.show(
                ctx = this,
                title = getString(R.string.bank_submit),
                message = getString(R.string.bank_submit_ask, blank),
                positive = getString(R.string.bank_submit),
                negative = getString(R.string.cancel)
            ) { finishRound() }
        } else {
            finishRound()
        }
    }

    private fun finishRound() {
        val now = System.currentTimeMillis()
        val items = queue.map { q ->
            val picked = answers[q.id].orEmpty()
            BankItemRecord(
                bankId = q.id,
                title = q.stem,
                picked = picked,
                answer = q.answer,
                correct = picked == q.answer,
                ms = 0
            )
        }.toMutableList()

        val sessionId = Repo.newId()
        Repo.addBankSession(
            BankSession(
                id = sessionId,
                mode = if (resuming) "RESUME" else "RANDOM",
                chapter = chapter,
                sourceId = source.id,
                date = DateUtil.todayStr(),
                startAt = sessionStart,
                endAt = now,
                items = items
            )
        )
        // 交完卷这份存档就没用了
        Repo.clearBankProgress()
        saved = true
        showResult(sessionId, items.count { it.correct }, items.size, now)
    }

    private fun showResult(sessionId: String, correct: Int, total: Int, endAt: Long) {
        val scrim = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            setBackgroundColor(transparentBlack(0.42f))
            alpha = 0f
            isClickable = true
        }
        val cardBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(22.dp, 24.dp, 22.dp, 20.dp)
            layoutParams = FrameLayout.LayoutParams(-1, -2).apply {
                gravity = Gravity.CENTER
                marginStart = 28.dp
                marginEnd = 28.dp
            }
            scaleX = 0.86f
            scaleY = 0.86f
        }
        cardBox.addView(bigText(getString(R.string.round_done)))
        cardBox.addView(
            subText(
                getString(
                    R.string.round_result, correct, total,
                    DateUtil.human(endAt - sessionStart)
                )
            )
        )
        // 交完卷主要就是想看解析，所以「查看逐题解析」当主按钮
        cardBox.addView(actionBtn(getString(R.string.bank_review_open), primary = true) {
            open<BankReviewActivity>("sessionId" to sessionId)
            finish()
        })
        cardBox.addView(actionBtn(getString(R.string.back_home), primary = false) { finish() })
        scrim.addView(cardBox)
        root.addView(scrim)

        scrim.animate().alpha(1f).setDuration(240).setInterpolator(Motion.EMPHASIZED).start()
        Motion.springTo(cardBox, DynamicAnimation.SCALE_X, 1f, stiffness = 520f, damping = 0.62f)
        Motion.springTo(cardBox, DynamicAnimation.SCALE_Y, 1f, stiffness = 520f, damping = 0.62f)
        if (correct == total && total > 0) {
            celebration.celebrate(root.width / 2f, root.height * 0.4f)
        }
    }

    /**
     * 退出前问要不要存档。单题复盘和一题没做的直接走，没什么可存的。
     */
    private fun askQuit() {
        if (saved || singleId != null || answers.isEmpty()) {
            finish()
            return
        }
        // 三条出路（存档走 / 丢弃走 / 不走），AppDialog 只有一个回调，所以用列表弹层
        AppListDialog.show(
            ctx = this,
            title = getString(R.string.bank_save_ask_title),
            rows = listOf(
                DialogRow(
                    title = getString(R.string.bank_save_keep),
                    sub = getString(R.string.bank_save_ask, answers.size)
                ),
                DialogRow(title = getString(R.string.bank_save_drop))
            ),
            negative = getString(R.string.cancel),
            onPick = { index ->
                if (index == 0) saveProgress() else finish()
            }
        )
    }

    private fun saveProgress() {
        Repo.saveBankProgress(
            BankProgress(
                sourceId = source.id,
                chapter = chapter,
                questionIds = queue.map { it.id }.toMutableList(),
                answers = LinkedHashMap(answers),
                cursor = pager.currentItem,
                startAt = sessionStart
            )
        )
        toast(getString(R.string.bank_saved))
        finish()
    }

    // ------------------------------------------------------------ 结果卡的小组件

    private fun bigText(t: CharSequence) = TextView(this).apply {
        text = t
        textSize = 21f
        setTextColor(getColor(R.color.ink))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun subText(t: CharSequence) = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(getColor(R.color.ink_sub))
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8.dp }
    }

    private fun actionBtn(t: CharSequence, primary: Boolean, onTap: () -> Unit) =
        TextView(this).apply {
            text = t
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(getColor(if (primary) R.color.surface else R.color.ink_sub))
            setBackgroundResource(if (primary) R.drawable.bg_btn_primary else R.drawable.bg_btn_ghost)
            layoutParams = LinearLayout.LayoutParams(-1, 48.dp).apply {
                topMargin = if (primary) 18.dp else 8.dp
            }
            tap { onTap() }
        }
}
