package com.gongkao.checkin.ui.page

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.gongkao.checkin.R
import com.gongkao.checkin.data.BankData
import com.gongkao.checkin.data.BankSource
import com.gongkao.checkin.data.BankSources
import com.gongkao.checkin.data.FormulaData
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.data.SkillRegistry
import com.gongkao.checkin.ui.AppListDialog
import com.gongkao.checkin.ui.DialogRow
import com.gongkao.checkin.ui.MainActivity
import com.gongkao.checkin.ui.Page
import com.gongkao.checkin.ui.bank.BankActivity
import com.gongkao.checkin.ui.bank.BankBrowseActivity
import com.gongkao.checkin.ui.bank.BankHistoryActivity
import com.gongkao.checkin.ui.bank.BankSearchActivity
import com.gongkao.checkin.ui.dp
import com.gongkao.checkin.ui.toast
import com.gongkao.checkin.ui.formula.FormulaActivity
import com.gongkao.checkin.ui.formula.FormulaHistoryActivity
import com.gongkao.checkin.ui.mentalmath.MentalMathActivity
import com.gongkao.checkin.ui.mentalmath.MentalMathHistoryActivity
import com.gongkao.checkin.ui.padTopInset
import com.gongkao.checkin.ui.percent.PercentActivity
import com.gongkao.checkin.ui.percent.PercentHistoryActivity
import com.gongkao.checkin.ui.open
import com.gongkao.checkin.ui.show
import com.gongkao.checkin.ui.tap

/** 背诵入口页：百化分 + 资料分析公式，各自两种模式与记录入口。 */
class RecitePage(host: MainActivity) : Page(host) {

    override val layoutRes = R.layout.fragment_recite

    private lateinit var percentStat: TextView
    private lateinit var formulaStat: TextView
    private lateinit var mentalMathStat: TextView
    private lateinit var bankStat: TextView
    private lateinit var categoryRow: LinearLayout
    private lateinit var bankChapterRow: LinearLayout
    private lateinit var bankSourceRow: LinearLayout
    private lateinit var bankStyleRow: TextView
    private lateinit var bankSizeRow: LinearLayout
    private lateinit var btnBankResume: TextView

    /** 每次抽几题，0 表示全部。 */
    private var bankSize = 10

    /** 当前选中的公式分类，随 chip 变化并传给 FormulaActivity。 */
    private var category = FormulaData.categories.first()

    /** 当前选中的复盘章节，随 chip 变化并传给 BankActivity。 */
    private var bankChapter = BankData.ALL

    /** 当前选中的题库来源，持久化在 Settings 里。 */
    private var bankSource: BankSource = BankSources.CURATED

    override fun onCreate(v: View) {
        percentStat = v.findViewById(R.id.percentStat)
        formulaStat = v.findViewById(R.id.formulaStat)
        mentalMathStat = v.findViewById(R.id.mentalMathStat)
        bankStat = v.findViewById(R.id.bankStat)
        categoryRow = v.findViewById(R.id.categoryRow)
        bankChapterRow = v.findViewById(R.id.bankChapterRow)
        bankSourceRow = v.findViewById(R.id.bankSourceRow)
        bankStyleRow = v.findViewById(R.id.bankStyleRow)
        bankSource = BankSources.byId(Repo.state.settings.bankSourceId)

        // NestedScrollView 的唯一子节点承担状态栏留白
        (v as ViewGroup).getChildAt(0).padTopInset()

        v.findViewById<TextView>(R.id.btnPercentFull).tap {
            ctx.open<PercentActivity>("mode" to "FULL")
        }
        v.findViewById<TextView>(R.id.btnPercentRandom).tap {
            ctx.open<PercentActivity>("mode" to "RANDOM")
        }
        v.findViewById<TextView>(R.id.btnPercentRecords).tap {
            ctx.open<PercentHistoryActivity>()
        }
        v.findViewById<TextView>(R.id.btnFormulaFull).tap {
            ctx.open<FormulaActivity>("mode" to "FULL", "category" to category)
        }
        v.findViewById<TextView>(R.id.btnFormulaRandom).tap {
            ctx.open<FormulaActivity>("mode" to "RANDOM", "category" to category)
        }
        v.findViewById<TextView>(R.id.btnFormulaRecords).tap {
            ctx.open<FormulaHistoryActivity>()
        }
        v.findViewById<TextView>(R.id.btnMentalMathFull).tap {
            ctx.open<MentalMathActivity>("mode" to "FULL")
        }
        v.findViewById<TextView>(R.id.btnMentalMathRandom).tap {
            ctx.open<MentalMathActivity>("mode" to "RANDOM")
        }
        v.findViewById<TextView>(R.id.btnMentalMathRecords).tap {
            ctx.open<MentalMathHistoryActivity>()
        }
        bankSizeRow = v.findViewById(R.id.bankSizeRow)
        btnBankResume = v.findViewById(R.id.btnBankResume)

        v.findViewById<TextView>(R.id.btnBankStart).tap {
            ctx.open<BankActivity>(
                "chapter" to bankChapter,
                "source" to bankSource.id,
                "size" to bankSize.toString()
            )
        }
        btnBankResume.tap { ctx.open<BankActivity>("resume" to "1") }
        v.findViewById<TextView>(R.id.btnBankSearch).tap {
            ctx.open<BankSearchActivity>()
        }
        v.findViewById<TextView>(R.id.btnBankBrowse).tap {
            ctx.open<BankBrowseActivity>()
        }
        v.findViewById<TextView>(R.id.btnBankRecords).tap {
            ctx.open<BankHistoryActivity>()
        }
        bankStyleRow.tap { pickSkill() }

        bankSize = Repo.bankBatchSize()
        buildChips()
        buildSourceChips()
        buildSizeChips()
        buildBankChips()
        showSkill()
    }

    /** 抽题数：常用几档 + 全部。选完记住，下次进来还是这个。 */
    private fun buildSizeChips() {
        bankSizeRow.removeAllViews()
        val options = listOf(5, 10, 20, 30, 0)
        options.forEach { n ->
            val chip = TextView(ctx).apply {
                text = if (n == 0) {
                    ctx.getString(R.string.bank_size_all)
                } else {
                    ctx.getString(R.string.bank_size, n)
                }
                textSize = 13f
                setBackgroundResource(R.drawable.bg_chip)
                setTextColor(ctx.getColorStateList(R.color.chip_text))
                setPadding(14.dp, 7.dp, 14.dp, 7.dp)
                isSelected = n == bankSize
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = 8.dp }
            }
            chip.tap {
                bankSize = n
                Repo.setBankBatchSize(n)
                for (i in 0 until bankSizeRow.childCount) {
                    bankSizeRow.getChildAt(i).isSelected = i == options.indexOf(n)
                }
            }
            bankSizeRow.addView(chip)
        }
    }

    /** 讲解风格：目前只有「题库内置讲解」能选，名师风格要接 AI，先灰着。 */
    private fun pickSkill() {
        AppListDialog.show(
            ctx = ctx,
            title = ctx.getString(R.string.bank_style_pick),
            rows = SkillRegistry.all.map {
                DialogRow(
                    title = if (it.available) it.name
                    else ctx.getString(R.string.bank_style_locked, it.name),
                    sub = it.tagline
                )
            },
            onPick = { i ->
                val picked = SkillRegistry.all[i]
                if (picked.available) {
                    Repo.setReviewSkill(picked.id)
                    showSkill()
                } else {
                    ctx.toast(ctx.getString(R.string.bank_style_locked, picked.name))
                }
            }
        )
    }

    private fun showSkill() {
        val skill = SkillRegistry.byId(Repo.state.settings.reviewSkillId)
        bankStyleRow.text = ctx.getString(R.string.bank_style, skill.name)
    }

    private fun buildSourceChips() {
        bankSourceRow.removeAllViews()
        BankSources.all.forEach { src ->
            val chip = TextView(ctx).apply {
                text = src.name
                textSize = 13f
                setBackgroundResource(R.drawable.bg_chip)
                setTextColor(ctx.getColorStateList(R.color.chip_text))
                setPadding(14.dp, 7.dp, 14.dp, 7.dp)
                isSelected = src.id == bankSource.id
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = 8.dp }
            }
            chip.tap {
                if (src.id == bankSource.id) return@tap
                bankSource = src
                bankChapter = BankData.ALL
                Repo.setBankSource(src.id)
                for (i in 0 until bankSourceRow.childCount) {
                    bankSourceRow.getChildAt(i).isSelected =
                        i == BankSources.all.indexOfFirst { it.id == src.id }
                }
                buildBankChips()
            }
            bankSourceRow.addView(chip)
        }
    }

    private fun buildChips() {
        categoryRow.removeAllViews()
        FormulaData.categories.forEach { name ->
            val chip = TextView(ctx).apply {
                text = name
                textSize = 13f
                setBackgroundResource(R.drawable.bg_chip)
                setTextColor(ctx.getColorStateList(R.color.chip_text))
                setPadding(14.dp, 7.dp, 14.dp, 7.dp)
                isSelected = name == category
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = 8.dp }
            }
            chip.tap {
                category = name
                for (i in 0 until categoryRow.childCount) {
                    categoryRow.getChildAt(i).isSelected = i == FormulaData.categories.indexOf(name)
                }
            }
            categoryRow.addView(chip)
        }
    }

    /** 章节 chip：题库要后台解析，加载完才能知道有哪些章节。 */
    private fun buildBankChips() {
        bankChapterRow.removeAllViews()
        BankData.loadAsync(ctx, bankSource) { all ->
            if (host.isFinishing || host.isDestroyed) return@loadAsync
            fillBankChips(BankData.chapters(all))
        }
    }

    private fun fillBankChips(chapters: List<String>) {
        bankChapterRow.removeAllViews()
        chapters.forEach { name ->
            val chip = TextView(ctx).apply {
                text = name
                textSize = 13f
                setBackgroundResource(R.drawable.bg_chip)
                setTextColor(ctx.getColorStateList(R.color.chip_text))
                setPadding(14.dp, 7.dp, 14.dp, 7.dp)
                isSelected = name == bankChapter
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = 8.dp }
            }
            chip.tap {
                bankChapter = name
                for (i in 0 until bankChapterRow.childCount) {
                    bankChapterRow.getChildAt(i).isSelected = i == chapters.indexOf(name)
                }
            }
            bankChapterRow.addView(chip)
        }
    }

    override fun refresh() {
        val ps = Repo.percentSessions()
        percentStat.text = if (ps.isEmpty()) {
            ctx.getString(R.string.recite_stat_none)
        } else {
            val acc = ps.sumOf { it.correctCount() } * 100 /
                ps.sumOf { it.total() }.coerceAtLeast(1)
            ctx.getString(R.string.recite_stat, ps.size, acc)
        }

        val fs = Repo.formulaSessions()
        formulaStat.text = if (fs.isEmpty()) {
            ctx.getString(R.string.recite_stat_none)
        } else {
            val acc = fs.sumOf { it.knownCount() } * 100 /
                fs.sumOf { it.total() }.coerceAtLeast(1)
            ctx.getString(R.string.recite_stat, fs.size, acc)
        }

        val ms = Repo.mentalMathSessions()
        mentalMathStat.text = if (ms.isEmpty()) {
            ctx.getString(R.string.recite_stat_none)
        } else {
            val acc = ms.sumOf { it.knownCount() } * 100 /
                ms.sumOf { it.total() }.coerceAtLeast(1)
            ctx.getString(R.string.recite_stat, ms.size, acc)
        }

        // 有存档才给「继续做题」，没存档时按钮直接不出现
        val progress = Repo.bankProgress()
        btnBankResume.show(progress != null && !progress.isEmpty())

        val bs = Repo.bankSessions()
        bankStat.text = if (bs.isEmpty()) {
            ctx.getString(R.string.recite_stat_none)
        } else {
            val acc = bs.sumOf { it.correctCount() } * 100 /
                bs.sumOf { it.total() }.coerceAtLeast(1)
            ctx.getString(R.string.recite_stat, bs.size, acc)
        }
    }
}
