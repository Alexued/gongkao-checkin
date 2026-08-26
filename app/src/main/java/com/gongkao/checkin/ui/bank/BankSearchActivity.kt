package com.gongkao.checkin.ui.bank

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gongkao.checkin.R
import com.gongkao.checkin.anim.Motion
import com.gongkao.checkin.data.BankData
import com.gongkao.checkin.data.BankQuestion
import com.gongkao.checkin.data.BankSource
import com.gongkao.checkin.data.BankSources
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.ui.AppListDialog
import com.gongkao.checkin.ui.DialogRow
import com.gongkao.checkin.ui.dp
import com.gongkao.checkin.ui.Themes
import com.gongkao.checkin.ui.edgeToEdge
import com.gongkao.checkin.ui.inflateChild
import com.gongkao.checkin.ui.open
import com.gongkao.checkin.ui.padTopInset
import com.gongkao.checkin.ui.show
import com.gongkao.checkin.ui.tap

/** 题库搜索：按关键词搜当前题库，点结果直接进单题复盘。 */
class BankSearchActivity : AppCompatActivity() {

    private lateinit var input: EditText
    private lateinit var btnSourceFilter: TextView
    private lateinit var countText: TextView
    private lateinit var resultBox: LinearLayout
    private lateinit var scroller: ScrollView

    private var source: BankSource = BankSources.CURATED
    private var questions: List<BankQuestion> = emptyList()
    private var loading = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edgeToEdge()
        Repo.init(this)
        Themes.apply(this)
        setContentView(R.layout.activity_bank_search)
        Themes.installBackdrop(this)
        source = BankSources.byId(Repo.state.settings.bankSourceId)

        findViewById<View>(R.id.topBar).padTopInset()
        findViewById<TextView>(R.id.barTitle).text = getString(R.string.bank_search)
        findViewById<ImageView>(R.id.btnBack).tap { finish() }

        input = findViewById(R.id.searchInput)
        btnSourceFilter = findViewById(R.id.btnSourceFilter)
        countText = findViewById(R.id.countText)
        resultBox = findViewById(R.id.resultBox)
        scroller = findViewById(R.id.scroller)

        btnSourceFilter.text = source.name
        btnSourceFilter.tap { switchSource() }
        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = runSearch()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        })

        load()
    }

    override fun onResume() {
        super.onResume()
        // 从单题复盘返回时题库已在缓存里，不用重来
        if (loading) load()
    }

    private fun load() {
        loading = true
        showHint(getString(R.string.bank_loading))
        BankData.loadAsync(this, source) { all ->
            if (isFinishing || isDestroyed) return@loadAsync
            questions = all
            loading = false
            runSearch()
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
                    source = picked
                    btnSourceFilter.text = picked.name
                    Repo.setBankSource(picked.id)
                    load()
                }
            }
        )
    }

    private fun runSearch() {
        if (loading) return
        val q = input.text.toString().trim()
        if (q.isEmpty()) {
            showHint(getString(R.string.bank_search_empty))
            return
        }
        val hits = BankData.search(questions, q)
        if (hits.isEmpty()) {
            showHint(getString(R.string.bank_search_none))
            return
        }
        resultBox.removeAllViews()
        countText.show(true)
        countText.text = getString(R.string.bank_search_count, hits.size)
        hits.forEachIndexed { i, item ->
            val row = resultBox.inflateChild(R.layout.item_session)
            row.findViewById<TextView>(R.id.sTitle).apply {
                text = item.stem
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            row.findViewById<TextView>(R.id.sSub).apply {
                show(true)
                text = listOf(item.chapter, item.skill).filter { it.isNotBlank() }.joinToString(" · ")
            }
            row.findViewById<TextView>(R.id.sValue).apply {
                show(true)
                text = item.answer
            }
            row.findViewById<ImageView>(R.id.sChevron).show(true)
            row.tap {
                open<BankActivity>("source" to source.id, "questionId" to item.id)
            }
            resultBox.addView(row)
            Motion.stagger(row, i)
        }
        scroller.scrollTo(0, 0)
    }

    private fun showHint(text: CharSequence) {
        resultBox.removeAllViews()
        countText.show(false)
        resultBox.addView(
            TextView(this).apply {
                this.text = text
                textSize = 13f
                setTextColor(getColor(R.color.ink_dim))
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 40.dp }
            }
        )
    }
}
