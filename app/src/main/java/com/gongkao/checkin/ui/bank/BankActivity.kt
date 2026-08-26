package com.gongkao.checkin.ui.bank

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.dynamicanimation.animation.DynamicAnimation
import com.gongkao.checkin.R
import com.gongkao.checkin.anim.Motion
import com.gongkao.checkin.data.BankData
import com.gongkao.checkin.data.BankItemRecord
import com.gongkao.checkin.data.BankQuestion
import com.gongkao.checkin.data.BankSession
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.ui.AppDialog
import com.gongkao.checkin.ui.dp
import com.gongkao.checkin.ui.edgeToEdge
import com.gongkao.checkin.ui.inflateChild
import com.gongkao.checkin.ui.padBottomInset
import com.gongkao.checkin.ui.padTopInset
import com.gongkao.checkin.ui.show
import com.gongkao.checkin.ui.tap
import com.gongkao.checkin.ui.transparentBlack
import com.gongkao.checkin.view.CelebrationView
import com.gongkao.checkin.view.DataTableView

/**
 * 资料分析技巧复盘：材料/表格 + 题干 → 选 ABCD 立即判对错 → 逐步放出讲解步骤
 * （每步划掉被排除的选项、高亮关键数字，pick 那步落到正确答案）。
 * mode = FULL 按顺序，RANDOM 打乱；chapter 限定章节。记录只进本功能自己的历史，不进每日统计。
 */
class BankActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var celebration: CelebrationView
    private lateinit var scroller: ScrollView
    private lateinit var modeText: TextView
    private lateinit var progressText: TextView
    private lateinit var progressBar: View
    private lateinit var chapterText: TextView
    private lateinit var sourceText: TextView
    private lateinit var materialText: TextView
    private lateinit var tableView: DataTableView
    private lateinit var stemText: TextView
    private lateinit var optionBox: LinearLayout
    private lateinit var verdictText: TextView
    private lateinit var skillText: TextView
    private lateinit var stepBox: LinearLayout
    private lateinit var btnAction: TextView

    private var mode = "FULL"
    private var chapter = BankData.ALL
    private lateinit var queue: List<BankQuestion>
    private var cursor = 0

    private var sessionStart = 0L
    private var itemStart = 0L
    private var answered = false
    private var stepShown = 0
    private val items = mutableListOf<BankItemRecord>()

    /** 选项字母 → 那一行的视图，讲解时要回头改它们的样式。 */
    private val optionRows = mutableMapOf<String, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edgeToEdge()
        Repo.init(this)
        mode = intent.getStringExtra("mode") ?: "FULL"
        chapter = intent.getStringExtra("chapter") ?: BankData.ALL

        root = FrameLayout(this)
        layoutInflater.inflate(R.layout.activity_bank, root, true)
        celebration = CelebrationView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            isClickable = false
        }
        root.addView(celebration)
        setContentView(root)

        bind()
        val pool = BankData.byChapter(this, chapter)
        queue = if (mode == "RANDOM") pool.shuffled() else pool
        if (queue.isEmpty()) {
            finish()
            return
        }
        sessionStart = System.currentTimeMillis()
        modeText.text = getString(if (mode == "RANDOM") R.string.mode_random else R.string.mode_full)
        showItem()
        // targetSdk 36 走预测式返回，onBackPressed 不再回调，必须注册 callback
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = askQuit()
        })
    }

    private fun bind() {
        val page = root.getChildAt(0) as ViewGroup
        page.getChildAt(0).padTopInset()
        scroller = findViewById(R.id.scroller)
        modeText = findViewById(R.id.modeText)
        progressText = findViewById(R.id.progressText)
        progressBar = findViewById(R.id.progressBar)
        chapterText = findViewById(R.id.chapterText)
        sourceText = findViewById(R.id.sourceText)
        materialText = findViewById(R.id.materialText)
        tableView = findViewById(R.id.tableView)
        stemText = findViewById(R.id.stemText)
        optionBox = findViewById(R.id.optionBox)
        verdictText = findViewById(R.id.verdictText)
        skillText = findViewById(R.id.skillText)
        stepBox = findViewById(R.id.stepBox)
        btnAction = findViewById(R.id.btnAction)
        findViewById<LinearLayout>(R.id.actionRow).padBottomInset(18.dp)

        findViewById<TextView>(R.id.btnExit).tap { askQuit() }
        btnAction.tap { onAction() }
        progressBar.pivotX = 0f
    }

    // ------------------------------------------------------------ 出题

    private fun showItem() {
        answered = false
        stepShown = 0
        itemStart = System.currentTimeMillis()
        val q = queue[cursor]

        chapterText.text = q.chapter
        sourceText.text = q.source
        sourceText.show(q.source.isNotBlank())
        stemText.text = q.stem
        progressText.text = getString(R.string.progress_of, cursor + 1, queue.size)

        val hasMaterial = q.material.isNotBlank()
        materialText.show(hasMaterial)
        if (hasMaterial) materialText.text = q.material
        val t = q.table
        tableView.show(t != null)
        if (t != null) tableView.bind(t)

        verdictText.show(false)
        skillText.show(false)
        stepBox.removeAllViews()
        buildOptions(q)

        btnAction.text = getString(R.string.bank_skip)
        btnAction.setBackgroundResource(R.drawable.bg_btn_ghost)
        btnAction.setTextColor(getColor(R.color.ink_sub))

        scroller.scrollTo(0, 0)
        stemText.alpha = 0f
        stemText.translationY = 12f.dp
        stemText.animate().alpha(1f).translationY(0f)
            .setDuration(340).setInterpolator(Motion.SOFT).start()
        Motion.springTo(progressBar, DynamicAnimation.SCALE_X, cursor.toFloat() / queue.size)
    }

    private fun buildOptions(q: BankQuestion) {
        optionBox.removeAllViews()
        optionRows.clear()
        q.optionList().forEachIndexed { i, (key, text) ->
            val row = optionBox.inflateChild(R.layout.item_bank_option)
            row.findViewById<TextView>(R.id.optKey).text = key
            row.findViewById<TextView>(R.id.optText).text = text
            row.findViewById<TextView>(R.id.optMark).show(false)
            row.tap { pick(key) }
            optionBox.addView(row)
            optionRows[key] = row
            Motion.stagger(row, i)
        }
    }

    // ------------------------------------------------------------ 作答

    private fun pick(key: String) {
        if (answered) return
        answered = true
        val q = queue[cursor]
        val correct = key == q.answer

        items.add(
            BankItemRecord(
                bankId = q.id,
                title = q.stem,
                picked = key,
                answer = q.answer,
                correct = correct,
                ms = System.currentTimeMillis() - itemStart
            )
        )

        optionRows.forEach { (k, row) ->
            row.isClickable = false
            when {
                k == q.answer -> markOption(row, R.drawable.bg_option_correct, R.color.teal, getString(R.string.bank_mark_answer))
                k == key -> markOption(row, R.drawable.bg_option_wrong, R.color.rose, getString(R.string.bank_mark_picked))
            }
        }

        verdictText.show(true)
        verdictText.text = if (correct) {
            getString(R.string.bank_verdict_right)
        } else {
            getString(R.string.bank_verdict_wrong, q.answer)
        }
        verdictText.setTextColor(getColor(if (correct) R.color.teal else R.color.rose))
        fadeIn(verdictText)

        skillText.show(true)
        skillText.text = getString(R.string.bank_skill, q.skill)
        fadeIn(skillText, delay = 80)

        val row = optionRows[key]
        if (row != null) {
            if (correct) Motion.confirm(row) else Motion.reject(row)
        }
        if (correct) {
            val me = IntArray(2)
            val base = IntArray(2)
            row?.getLocationInWindow(me)
            root.getLocationInWindow(base)
            if (row != null) {
                celebration.burstAt(
                    me[0] - base[0] + row.width / 2f,
                    me[1] - base[1] + row.height / 2f,
                    10
                )
            }
        }

        advanceAction()
    }

    private fun markOption(row: View, bg: Int, colorRes: Int, mark: String) {
        row.setBackgroundResource(bg)
        row.findViewById<TextView>(R.id.optMark).apply {
            show(true)
            text = mark
            setTextColor(getColor(colorRes))
        }
        row.findViewById<TextView>(R.id.optKey).setTextColor(getColor(colorRes))
    }

    // ------------------------------------------------------------ 分步讲解

    /** 底部按钮：还有讲解步骤就放下一步，放完了就进下一题。 */
    private fun onAction() {
        if (!answered) {
            skip()
            return
        }
        val q = queue[cursor]
        if (stepShown < q.anim.size) revealStep() else next()
    }

    private fun revealStep() {
        val q = queue[cursor]
        val step = q.anim[stepShown]
        stepShown++

        val card = stepBox.inflateChild(R.layout.item_bank_step)
        card.findViewById<TextView>(R.id.stepIndex).text = stepShown.toString()
        card.findViewById<TextView>(R.id.stepTitle).text = step.t
        card.findViewById<TextView>(R.id.stepBody).text = highlightFacts(step.b, step.facts)
        stepBox.addView(card)
        card.alpha = 0f
        card.translationY = 16f.dp
        card.animate().alpha(1f).translationY(0f)
            .setDuration(340).setInterpolator(Motion.SOFT).start()

        step.kill.forEach { killOption(it) }
        if (step.pick) optionRows[q.answer]?.let { Motion.confirm(it) }

        card.post { scroller.smoothScrollTo(0, scroller.getChildAt(0).height) }
        advanceAction()
    }

    /** 讲解里被排除的选项：划掉并压暗。正确答案不会被划。 */
    private fun killOption(key: String) {
        val row = optionRows[key] ?: return
        if (key == queue[cursor].answer) return
        val text = row.findViewById<TextView>(R.id.optText)
        val raw = text.text.toString()
        text.text = SpannableString(raw).apply {
            setSpan(StrikethroughSpan(), 0, raw.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        row.animate().alpha(0.45f).setDuration(260).setInterpolator(Motion.SOFT).start()
    }

    /** 讲解正文里的关键数字标成强调色，方便对着材料找。 */
    private fun highlightFacts(body: String, facts: List<String>): CharSequence {
        if (facts.isEmpty()) return body
        val sp = SpannableString(body)
        val accent = getColor(R.color.accent_deep)
        facts.filter { it.isNotBlank() }.forEach { fact ->
            var from = 0
            while (true) {
                val at = body.indexOf(fact, from)
                if (at < 0) break
                sp.setSpan(ForegroundColorSpan(accent), at, at + fact.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sp.setSpan(StyleSpan(Typeface.BOLD), at, at + fact.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                from = at + fact.length
            }
        }
        return sp
    }

    /** 按钮文案随剩余步骤变化。 */
    private fun advanceAction() {
        val q = queue[cursor]
        val last = cursor == queue.size - 1
        if (stepShown < q.anim.size) {
            btnAction.text = getString(R.string.bank_next_step, stepShown + 1, q.anim.size)
            btnAction.setBackgroundResource(R.drawable.bg_btn_primary)
            btnAction.setTextColor(getColor(R.color.surface))
        } else {
            btnAction.text = getString(if (last) R.string.bank_finish else R.string.bank_next_question)
            btnAction.setBackgroundResource(R.drawable.bg_btn_primary)
            btnAction.setTextColor(getColor(R.color.surface))
        }
    }

    /** 跳过：不记录作答，直接进下一题。 */
    private fun skip() {
        items.add(
            BankItemRecord(
                bankId = queue[cursor].id,
                title = queue[cursor].stem,
                picked = "",
                answer = queue[cursor].answer,
                correct = false,
                ms = System.currentTimeMillis() - itemStart
            )
        )
        next()
    }

    private fun next() {
        cursor++
        if (cursor >= queue.size) finishRound() else showItem()
    }

    private fun fadeIn(v: View, delay: Long = 0) {
        v.alpha = 0f
        v.translationY = 10f.dp
        v.animate().alpha(1f).translationY(0f)
            .setStartDelay(delay).setDuration(320).setInterpolator(Motion.SOFT).start()
    }

    // ------------------------------------------------------------ 收尾

    private fun finishRound() {
        val now = System.currentTimeMillis()
        val session = BankSession(
            id = Repo.newId(),
            mode = mode,
            chapter = chapter,
            date = DateUtil.todayStr(),
            startAt = sessionStart,
            endAt = now,
            items = items.toMutableList()
        )
        Repo.addBankSession(session)
        Motion.springTo(progressBar, DynamicAnimation.SCALE_X, 1f)
        showResult(session, now)
    }

    private fun showResult(s: BankSession, endAt: Long) {
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
                gravity = android.view.Gravity.CENTER
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
                    R.string.round_result,
                    s.correctCount(), s.total(), DateUtil.human(endAt - s.startAt)
                )
            )
        )
        cardBox.addView(actionBtn(getString(R.string.again), primary = true) { restart(scrim) })
        cardBox.addView(actionBtn(getString(R.string.back_home), primary = false) { finish() })
        scrim.addView(cardBox)
        root.addView(scrim)

        scrim.animate().alpha(1f).setDuration(240).setInterpolator(Motion.EMPHASIZED).start()
        Motion.springTo(cardBox, DynamicAnimation.SCALE_X, 1f, stiffness = 520f, damping = 0.62f)
        Motion.springTo(cardBox, DynamicAnimation.SCALE_Y, 1f, stiffness = 520f, damping = 0.62f)
        if (s.correctCount() == s.total() && s.total() > 0) {
            celebration.celebrate(root.width / 2f, root.height * 0.4f)
        }
    }

    private fun restart(scrim: View) {
        root.removeView(scrim)
        items.clear()
        cursor = 0
        sessionStart = System.currentTimeMillis()
        val pool = BankData.byChapter(this, chapter)
        queue = if (mode == "RANDOM") pool.shuffled() else pool
        showItem()
    }

    private fun bigText(t: CharSequence) = TextView(this).apply {
        text = t
        textSize = 21f
        setTextColor(getColor(R.color.ink))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = android.view.Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun subText(t: CharSequence) = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(getColor(R.color.ink_sub))
        gravity = android.view.Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8.dp }
    }

    private fun actionBtn(t: CharSequence, primary: Boolean, onTap: () -> Unit) =
        TextView(this).apply {
            text = t
            textSize = 15f
            gravity = android.view.Gravity.CENTER
            setTextColor(getColor(if (primary) R.color.surface else R.color.ink_sub))
            setBackgroundResource(if (primary) R.drawable.bg_btn_primary else R.drawable.bg_btn_ghost)
            layoutParams = LinearLayout.LayoutParams(-1, 48.dp).apply {
                topMargin = if (primary) 18.dp else 8.dp
            }
            tap { onTap() }
        }

    private fun askQuit() {
        if (items.isEmpty()) {
            finish()
            return
        }
        AppDialog.show(
            ctx = this,
            title = getString(R.string.quit),
            message = getString(R.string.quit_confirm),
            positive = getString(R.string.quit),
            negative = getString(R.string.keep_going),
            destructive = true
        ) { finish() }
    }
}
