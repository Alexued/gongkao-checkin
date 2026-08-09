package com.gongkao.checkin.ui.page

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.gongkao.checkin.R
import com.gongkao.checkin.data.FormulaData
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.ui.MainActivity
import com.gongkao.checkin.ui.Page
import com.gongkao.checkin.ui.dp
import com.gongkao.checkin.ui.formula.FormulaActivity
import com.gongkao.checkin.ui.formula.FormulaHistoryActivity
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
    private lateinit var categoryRow: LinearLayout

    /** 当前选中的公式分类，随 chip 变化并传给 FormulaActivity。 */
    private var category = FormulaData.categories.first()

    override fun onCreate(v: View) {
        percentStat = v.findViewById(R.id.percentStat)
        formulaStat = v.findViewById(R.id.formulaStat)
        categoryRow = v.findViewById(R.id.categoryRow)

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

        buildChips()
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
    }
}
