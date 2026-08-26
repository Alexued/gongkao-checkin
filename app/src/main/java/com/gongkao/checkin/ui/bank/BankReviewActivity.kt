package com.gongkao.checkin.ui.bank

import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.TextView
import com.gongkao.checkin.R
import com.gongkao.checkin.data.BankData
import com.gongkao.checkin.data.BankItemRecord
import com.gongkao.checkin.data.BankQuestion
import com.gongkao.checkin.data.BankSession
import com.gongkao.checkin.data.BankSources
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.ui.ListScreen
import com.gongkao.checkin.ui.dp
import com.gongkao.checkin.view.DataTableView

/**
 * 交卷后的复盘页：逐题给出你选的、正确答案、以及**完整解析**。
 *
 * 解析不再分步放——这里的目的是回头一次看完，所以分步题把各步拼成全文。
 * 题量可能很大（抽「全部」时上千题），所以分批铺，避免一次性 inflate 卡死主线程。
 */
class BankReviewActivity : ListScreen() {

    private val sessionId: String by lazy { intent.getStringExtra("sessionId").orEmpty() }
    private var session: BankSession? = null
    private var questions: Map<String, BankQuestion> = emptyMap()
    private var loading = true
    private var shown = FIRST_BATCH

    override fun title(): CharSequence = getString(R.string.bank_review_title)

    override fun onResume() {
        super.onResume()
        if (loading) load()
    }

    private fun load() {
        val s = Repo.bankSessions().firstOrNull { it.id == sessionId }
        if (s == null) {
            loading = false
            rebuild()
            return
        }
        session = s
        BankData.loadAsync(this, BankSources.byId(s.sourceId)) { all ->
            if (isFinishing || isDestroyed) return@loadAsync
            questions = all.associateBy { it.id }
            loading = false
            rebuild()
        }
    }

    override fun build() {
        if (loading) {
            empty(getString(R.string.bank_loading))
            return
        }
        val s = session
        if (s == null) {
            empty()
            return
        }

        summary(s)
        val items = s.items
        items.take(shown).forEachIndexed { i, item -> question(i, item) }
        if (items.size > shown) {
            row(
                title = getString(R.string.bank_review_more, items.size - shown),
                chevron = true
            ) {
                shown += NEXT_BATCH
                rebuild()
            }
        }
    }

    private fun summary(s: BankSession) {
        val blank = s.items.count { it.picked.isBlank() }
        sectionColored(
            getString(R.string.bank_review_summary),
            R.color.accent,
            getString(
                R.string.bank_review_summary_sub,
                s.correctCount(), s.total(), DateUtil.human(s.endAt - s.startAt)
            )
        )
        val box = card()
        kv(getString(R.string.bank_review_right), s.correctCount().toString(), box)
        kv(getString(R.string.bank_review_wrong), (s.total() - s.correctCount() - blank).toString(), box)
        if (blank > 0) kv(getString(R.string.bank_review_blank), blank.toString(), box)
        kv(getString(R.string.stat_percent_label), getString(R.string.stat_percent, (s.accuracy() * 100).toInt()), box)
    }

    /** 一道题的复盘块：题号+对错 → 材料/表格 → 题干 → 选项 → 完整解析。 */
    private fun question(index: Int, item: BankItemRecord) {
        val q = questions[item.bankId]
        val mark = when {
            item.picked.isBlank() -> getString(R.string.bank_review_mark_blank)
            item.correct -> getString(R.string.bank_review_mark_right)
            else -> getString(R.string.bank_review_mark_wrong)
        }
        val colorRes = when {
            item.picked.isBlank() -> R.color.ink_dim
            item.correct -> R.color.teal
            else -> R.color.rose
        }
        sectionColored(getString(R.string.bank_review_no, index + 1, mark), colorRes)

        val box = card()

        // 题目可能已经不在题库里（换了题库或题库更新），那就只显示记录里存下的信息
        if (q == null) {
            box.addView(body(item.title, R.color.ink))
            box.addView(dim(getString(R.string.bank_review_missing, item.answer)))
            return
        }

        if (q.source.isNotBlank()) box.addView(dim(getString(R.string.bank_source, q.source)))

        q.table?.let { t ->
            box.addView(
                DataTableView(this).apply {
                    bind(t)
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10.dp }
                }
            )
        }
        if (q.material.isNotBlank()) box.addView(body(q.material, R.color.ink_sub))

        box.addView(label(getString(R.string.bank_review_stem)))
        box.addView(body(q.stem, R.color.ink, medium = true))

        // 选项：正确答案标绿，选错的那个标红
        q.optionList().forEach { (key, text) ->
            val isAnswer = key == q.answer
            val isPicked = key == item.picked
            box.addView(
                TextView(this).apply {
                    this.text = buildString {
                        append(key).append(". ").append(text)
                        if (isAnswer) append("   ").append(getString(R.string.bank_mark_answer))
                        else if (isPicked) append("   ").append(getString(R.string.bank_mark_picked))
                    }
                    textSize = 13.5f
                    setTextColor(
                        getColor(
                            when {
                                isAnswer -> R.color.teal
                                isPicked -> R.color.rose
                                else -> R.color.ink
                            }
                        )
                    )
                    if (isAnswer || isPicked) {
                        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    }
                    setLineSpacing(3f.dp, 1f)
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 6.dp }
                }
            )
        }

        box.addView(label(getString(R.string.bank_solution)))
        box.addView(body(fullSolution(q), R.color.ink))
    }

    /**
     * 完整解析。分步题（精选那批）把各步标题+正文拼成全文，
     * 免得复盘时还要一步步点；没有分步数据的就是原版解析。
     */
    private fun fullSolution(q: BankQuestion): String {
        if (q.anim.isEmpty()) return q.solution
        return q.anim.joinToString("\n\n") { step ->
            if (step.t.isBlank()) step.b else "【${step.t}】${step.b}"
        }
    }

    // ------------------------------------------------------------ 小组件

    private fun label(t: CharSequence) = TextView(this).apply {
        text = t
        textSize = 12f
        setTextColor(getColor(R.color.ink_dim))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 14.dp }
    }

    private fun body(t: CharSequence, colorRes: Int, medium: Boolean = false) = TextView(this).apply {
        text = t
        textSize = 13.5f
        setTextColor(getColor(colorRes))
        if (medium) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setLineSpacing(5f.dp, 1f)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8.dp }
    }

    private fun dim(t: CharSequence) = TextView(this).apply {
        text = t
        textSize = 12f
        setTextColor(getColor(R.color.ink_dim))
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private companion object {
        const val FIRST_BATCH = 15
        const val NEXT_BATCH = 25
    }
}
