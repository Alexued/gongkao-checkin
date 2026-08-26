package com.gongkao.checkin.ui.bank

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.gongkao.checkin.R
import com.gongkao.checkin.anim.Motion
import com.gongkao.checkin.data.BankQuestion
import com.gongkao.checkin.ui.dp
import com.gongkao.checkin.ui.inflateChild
import com.gongkao.checkin.ui.show
import com.gongkao.checkin.ui.tap
import com.gongkao.checkin.view.DataTableView

/**
 * 一道题的一整页。每页自己管「选了哪个、讲解放到第几步」，
 * 这样左右滑回来还是原样，不用把状态摊到 Activity 上。
 */
class BankQuestionView(
    private val ctx: Context,
    private val q: BankQuestion,
    /** 之前选过的选项（续做/滑回来时回填），null 表示还没作答 */
    picked: String?,
    /** 作答回调，Activity 拿去记账并刷新答题卡 */
    private val onAnswered: (questionId: String, picked: String, correct: Boolean) -> Unit
) {

    val root: View = LayoutInflater.from(ctx).inflate(R.layout.view_bank_question, null)

    private val scroller: ScrollView = root.findViewById(R.id.scroller)
    private val optionBox: LinearLayout = root.findViewById(R.id.optionBox)
    private val verdictText: TextView = root.findViewById(R.id.verdictText)
    private val skillText: TextView = root.findViewById(R.id.skillText)
    private val stepBox: LinearLayout = root.findViewById(R.id.stepBox)
    private val solutionCard: LinearLayout = root.findViewById(R.id.solutionCard)
    private val solutionText: TextView = root.findViewById(R.id.solutionText)

    private val optionRows = mutableMapOf<String, View>()

    var answered: String? = picked
        private set
    private var stepShown = 0
    private var solutionShown = false

    init {
        bindStem()
        buildOptions()
        // 续做时把已答的题直接还原成"答完"的样子
        answered?.let { revealAnswered(it, animate = false) }
    }

    private fun bindStem() {
        root.findViewById<TextView>(R.id.chapterText).text = q.chapter
        root.findViewById<TextView>(R.id.sourceText).apply {
            text = q.source
            show(q.source.isNotBlank())
        }
        root.findViewById<TextView>(R.id.stemText).text = q.stem

        root.findViewById<TextView>(R.id.materialText).apply {
            show(q.material.isNotBlank())
            if (q.material.isNotBlank()) text = q.material
        }
        val t = q.table
        root.findViewById<DataTableView>(R.id.tableView).apply {
            show(t != null)
            if (t != null) bind(t)
        }
    }

    private fun buildOptions() {
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

    private fun pick(key: String) {
        if (answered != null) return
        answered = key
        revealAnswered(key, animate = true)
        onAnswered(q.id, key, key == q.answer)
    }

    /** 把「已作答」的样子铺出来：标对错、给考点、准备讲解。 */
    private fun revealAnswered(key: String, animate: Boolean) {
        val correct = key == q.answer
        optionRows.forEach { (k, row) ->
            row.isClickable = false
            when {
                k == q.answer -> mark(row, R.drawable.bg_option_correct, R.color.teal, R.string.bank_mark_answer)
                k == key -> mark(row, R.drawable.bg_option_wrong, R.color.rose, R.string.bank_mark_picked)
            }
        }
        verdictText.show(true)
        verdictText.text = if (correct) {
            ctx.getString(R.string.bank_verdict_right)
        } else {
            ctx.getString(R.string.bank_verdict_wrong, q.answer)
        }
        verdictText.setTextColor(ctx.getColor(if (correct) R.color.teal else R.color.rose))

        skillText.show(true)
        skillText.text = ctx.getString(R.string.bank_skill, q.skill)

        if (animate) {
            fadeIn(verdictText)
            fadeIn(skillText, 80)
            optionRows[key]?.let { if (correct) Motion.confirm(it) else Motion.reject(it) }
        }
    }

    private fun mark(row: View, bg: Int, colorRes: Int, markRes: Int) {
        row.setBackgroundResource(bg)
        row.findViewById<TextView>(R.id.optMark).apply {
            show(true)
            setText(markRes)
            setTextColor(ctx.getColor(colorRes))
        }
        row.findViewById<TextView>(R.id.optKey).setTextColor(ctx.getColor(colorRes))
    }

    // ------------------------------------------------------------ 讲解

    /** 还有没有下一步可放（用来决定底部按钮文案）。 */
    fun hasMoreExplain(): Boolean = when {
        answered == null -> false
        q.anim.isNotEmpty() -> stepShown < q.anim.size
        else -> !solutionShown
    }

    fun explainLabel(): CharSequence = if (q.anim.isNotEmpty()) {
        ctx.getString(R.string.bank_next_step, stepShown + 1, q.anim.size)
    } else {
        ctx.getString(R.string.bank_show_solution)
    }

    /** 放下一步讲解；没有分步数据的题就一次摊开解析全文。 */
    fun explainNext() {
        if (answered == null) return
        if (q.anim.isNotEmpty()) revealStep() else revealSolution()
    }

    private fun revealStep() {
        if (stepShown >= q.anim.size) return
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
        scrollToBottom()
    }

    private fun revealSolution() {
        if (solutionShown) return
        solutionShown = true
        solutionText.text = q.solution
        solutionCard.show(true)
        fadeIn(solutionCard)
        scrollToBottom()
    }

    /** 讲解里被排除的选项：划掉并压暗。正确答案不会被划。 */
    private fun killOption(key: String) {
        if (key == q.answer) return
        val row = optionRows[key] ?: return
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
        val accent = ctx.getColor(R.color.accent_deep)
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

    private fun scrollToBottom() {
        scroller.post { scroller.smoothScrollTo(0, scroller.getChildAt(0).height) }
    }

    private fun fadeIn(v: View, delay: Long = 0) {
        v.alpha = 0f
        v.translationY = 10f.dp
        v.animate().alpha(1f).translationY(0f)
            .setStartDelay(delay).setDuration(320).setInterpolator(Motion.SOFT).start()
    }
}
