package com.gongkao.checkin.ui.bank

import android.widget.LinearLayout
import android.widget.TextView
import com.gongkao.checkin.R
import com.gongkao.checkin.data.BankData
import com.gongkao.checkin.data.BankQuestion
import com.gongkao.checkin.data.BankSource
import com.gongkao.checkin.data.BankSources
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.ui.AppListDialog
import com.gongkao.checkin.ui.DialogRow
import com.gongkao.checkin.ui.ListScreen
import com.gongkao.checkin.ui.dp
import com.gongkao.checkin.ui.open
import com.gongkao.checkin.view.DataTableView

/** 题库浏览：按章节分组，点一题展开材料/表格 + 选项 + 答案 + 完整解析。右上角切题库。 */
class BankBrowseActivity : ListScreen() {

    private val expanded = mutableSetOf<String>()
    private val openChapters = mutableSetOf<String>()
    private var source: BankSource = BankSources.CURATED
    private var questions: List<BankQuestion> = emptyList()
    private var loading = true

    override fun title(): CharSequence = getString(R.string.bank_browse)

    override fun onResume() {
        super.onResume()
        if (loading) load()
    }

    /**
     * 章节先折叠。真题题库 1175 题，一次性铺进 ScrollView 会卡死主线程
     * （实测 Skipped 1148 frames），所以点开章节才铺，且每章最多铺 [CHAPTER_LIMIT] 条，
     * 剩下的引导去搜索。
     */
    override fun build() {
        action(source.name) { switchSource() }

        if (loading) {
            empty(getString(R.string.bank_loading))
            return
        }
        if (questions.isEmpty()) {
            empty()
            return
        }
        questions.groupBy { it.chapter }.forEach { (chapter, list) ->
            val open = chapter in openChapters
            row(
                title = chapter,
                sub = getString(R.string.bank_chapter_count, list.size),
                value = if (open) "−" else "+",
                chevron = false
            ) {
                if (!openChapters.remove(chapter)) openChapters.add(chapter)
                rebuild()
            }
            if (!open) return@forEach

            list.take(CHAPTER_LIMIT).forEach { q -> group(q) }
            if (list.size > CHAPTER_LIMIT) {
                row(
                    title = getString(R.string.bank_more_in_search, list.size - CHAPTER_LIMIT),
                    chevron = true
                ) { open<BankSearchActivity>() }
            }
        }
    }

    private fun load() {
        loading = true
        source = BankSources.byId(Repo.state.settings.bankSourceId)
        BankData.loadAsync(this, source) { all ->
            if (isFinishing || isDestroyed) return@loadAsync
            questions = all
            loading = false
            rebuild()
        }
    }

    private fun switchSource() {
        AppListDialog.show(
            ctx = this,
            title = getString(R.string.bank_switch_source),
            rows = BankSources.all.map { DialogRow(it.name) },
            onPick = { i ->
                val picked = BankSources.all[i]
                if (picked.id != source.id) {
                    Repo.setBankSource(picked.id)
                    expanded.clear()
                    openChapters.clear()
                    load()
                    rebuild()
                }
            }
        )
    }

    private fun group(q: BankQuestion) {
        row(
            title = q.stem,
            sub = q.skill,
            value = q.answer,
            chevron = true
        ) {
            if (!expanded.remove(q.id)) expanded.add(q.id)
            rebuild()
        }
        if (q.id in expanded) details(q)
    }

    private fun details(q: BankQuestion) {
        val box = card(14)

        if (q.source.isNotBlank()) box.addView(dim(getString(R.string.bank_source, q.source)))

        val table = q.table
        if (table != null) {
            box.addView(
                DataTableView(this).apply {
                    bind(table)
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10.dp }
                }
            )
        }
        if (q.material.isNotBlank()) box.addView(body(q.material, R.color.ink_sub))

        box.addView(label(getString(R.string.bank_options)))
        q.optionList().forEach { (key, text) ->
            val hit = key == q.answer
            box.addView(
                TextView(this).apply {
                    this.text = "$key. $text"
                    textSize = 13.5f
                    setTextColor(getColor(if (hit) R.color.teal else R.color.ink))
                    if (hit) {
                        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                    }
                    setLineSpacing(3f.dp, 1f)
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 6.dp }
                }
            )
        }

        box.addView(label(getString(R.string.bank_solution)))
        box.addView(body(q.solution, R.color.ink))

        (box.layoutParams as LinearLayout.LayoutParams).apply {
            topMargin = (-2).dp
            bottomMargin = 10.dp
        }
    }

    private fun label(t: CharSequence) = TextView(this).apply {
        text = t
        textSize = 12f
        setTextColor(getColor(R.color.ink_dim))
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 14.dp }
    }

    private fun body(t: CharSequence, colorRes: Int) = TextView(this).apply {
        text = t
        textSize = 13.5f
        setTextColor(getColor(colorRes))
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
        const val CHAPTER_LIMIT = 40
    }
}
