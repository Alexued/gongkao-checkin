package com.gongkao.checkin.ui.page

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.gongkao.checkin.R
import com.gongkao.checkin.data.BankData
import com.gongkao.checkin.data.FormulaData
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.ui.MainActivity
import com.gongkao.checkin.ui.Page
import com.gongkao.checkin.ui.bank.BankActivity
import com.gongkao.checkin.ui.bank.BankBrowseActivity
import com.gongkao.checkin.ui.bank.BankHistoryActivity
import com.gongkao.checkin.ui.dp
import com.gongkao.checkin.ui.formula.FormulaActivity
import com.gongkao.checkin.ui.formula.FormulaHistoryActivity
import com.gongkao.checkin.ui.mentalmath.MentalMathActivity
import com.gongkao.checkin.ui.mentalmath.MentalMathHistoryActivity
import com.gongkao.checkin.ui.padTopInset
import com.gongkao.checkin.ui.percent.PercentActivity
import com.gongkao.checkin.ui.percent.PercentHistoryActivity
import com.gongkao.checkin.ui.open
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

    /** 当前选中的公式分类，随 chip 变化并传给 FormulaActivity。 */
    private var category = FormulaData.categories.first()

    /** 当前选中的复盘章节，随 chip 变化并传给 BankActivity。 */
    private var bankChapter = BankData.ALL

    override fun onCreate(v: View) {
        percentStat = v.findViewById(R.id.percentStat)
        formulaStat = v.findViewById(R.id.formulaStat)
        mentalMathStat = v.findViewById(R.id.mentalMathStat)
        bankStat = v.findViewById(R.id.bankStat)
        categoryRow = v.findViewById(R.id.categoryRow)
        bankChapterRow = v.findViewById(R.id.bankChapterRow)

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
        v.findViewById<TextView>(R.id.btnBankFull).tap {
            ctx.open<BankActivity>("mode" to "FULL", "chapter" to bankChapter)
        }
        v.findViewById<TextView>(R.id.btnBankRandom).tap {
            ctx.open<BankActivity>("mode" to "RANDOM", "chapter" to bankChapter)
        }
        v.findViewById<TextView>(R.id.btnBankBrowse).tap {
            ctx.open<BankBrowseActivity>()
        }
        v.findViewById<TextView>(R.id.btnBankRecords).tap {
            ctx.open<BankHistoryActivity>()
        }

        buildChips()
        buildBankChips()
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

    /** 章节 chip：文案取「第四章 增长量的比较与计算」这种全名，横向可滑。 */
    private fun buildBankChips() {
        bankChapterRow.removeAllViews()
        val chapters = BankData.chapters(ctx)
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
