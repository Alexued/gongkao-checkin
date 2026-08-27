package com.gongkao.checkin.ui

import android.widget.LinearLayout
import android.widget.TextView
import com.gongkao.checkin.BuildConfig
import com.gongkao.checkin.R

/**
 * 软件介绍。**内容写在这里，不再跳 GitHub Pages** —— 断网也能看，
 * 也不会因为仓库改名或 Pages 没启用就变成 404（之前就是这么坏掉的）。
 *
 * **加功能时记得同步改这一页**，否则介绍会慢慢和实际功能脱节。
 * 上一次脱节的例子：「局域网网页同步」功能都删了，介绍里还写着。
 */
class IntroActivity : ListScreen() {

    override fun title(): CharSequence = getString(R.string.about_intro)

    /** 介绍页是静态内容，不需要跟着 Repo 重建。 */
    override val liveUpdate = false

    override fun build() {
        lead()
        daily()
        practice()
        usage()
        dataNote()
    }

    // ------------------------------------------------------------ 开头

    private fun lead() {
        val box = card(20)
        big(getString(R.string.intro_lead_title), box)
        body(getString(R.string.intro_lead_body), box)
        dim(getString(R.string.intro_lead_req, BuildConfig.VERSION_NAME), box)
    }

    // ------------------------------------------------------------ 每天

    private fun daily() {
        sectionColored(getString(R.string.intro_sec_daily), R.color.accent,
            getString(R.string.intro_sec_daily_sub))

        block(
            getString(R.string.intro_task_title),
            getString(R.string.intro_task_body),
            listOf(
                getString(R.string.intro_task_p1),
                getString(R.string.intro_task_p2),
                getString(R.string.intro_task_p3)
            )
        )
        block(
            getString(R.string.intro_overview_title),
            getString(R.string.intro_overview_body),
            listOf(
                getString(R.string.intro_overview_p1),
                getString(R.string.intro_overview_p2)
            )
        )
        block(
            getString(R.string.intro_stats_title),
            getString(R.string.intro_stats_body),
            listOf(
                getString(R.string.intro_stats_p1),
                getString(R.string.intro_stats_p2),
                getString(R.string.intro_stats_p3)
            )
        )
    }

    // ------------------------------------------------------------ 练习

    private fun practice() {
        sectionColored(getString(R.string.intro_sec_practice), R.color.teal,
            getString(R.string.intro_sec_practice_sub))

        block(
            getString(R.string.intro_step_title),
            getString(R.string.intro_step_body),
            emptyList()
        )
        block(
            getString(R.string.intro_set_title),
            getString(R.string.intro_set_body),
            listOf(
                getString(R.string.intro_set_p1),
                getString(R.string.intro_set_p2)
            )
        )
        block(
            getString(R.string.intro_recite_title),
            getString(R.string.intro_recite_body),
            emptyList()
        )
        block(
            getString(R.string.intro_timer_title),
            getString(R.string.intro_timer_body),
            emptyList()
        )
    }

    // ------------------------------------------------------------ 两种用法

    private fun usage() {
        sectionColored(getString(R.string.intro_sec_usage), R.color.violet,
            getString(R.string.intro_sec_usage_sub))

        block(
            getString(R.string.intro_mode_title),
            getString(R.string.intro_mode_body),
            emptyList()
        )
        block(
            getString(R.string.intro_sync_title),
            getString(R.string.intro_sync_body),
            listOf(
                getString(R.string.intro_sync_p1),
                getString(R.string.intro_sync_p2)
            )
        )
    }

    // ------------------------------------------------------------ 数据说明

    private fun dataNote() {
        sectionColored(getString(R.string.intro_sec_data), R.color.ink_dim)
        val box = card(18)
        body(getString(R.string.intro_data_body), box)
        dim(getString(R.string.intro_footer), box)
    }

    // ------------------------------------------------------------ 排版积木

    /** 一块：小标题 + 说明 +（可选）要点。 */
    private fun block(title: String, bodyText: String, points: List<String>) {
        val box = card(18)
        sub(title, box)
        body(bodyText, box)
        points.forEach { bullet(it, box) }
    }

    private fun big(text: String, box: LinearLayout) {
        box.addView(TextView(this).apply {
            this.text = text
            setTextColor(getColor(R.color.ink))
            textSize = 21f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setLineSpacing(6f.dp, 1f)
        })
    }

    private fun sub(text: String, box: LinearLayout) {
        box.addView(TextView(this).apply {
            this.text = text
            setTextColor(getColor(R.color.ink))
            textSize = 15.5f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        })
    }

    private fun body(text: String, box: LinearLayout) {
        box.addView(TextView(this).apply {
            this.text = text
            setTextColor(getColor(R.color.ink_sub))
            textSize = 13.5f
            setLineSpacing(5f.dp, 1f)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 7.dp }
        })
    }

    /** 要点行：左边一个小圆点，文字挂在右边并保持缩进。 */
    private fun bullet(text: String, box: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 7.dp }
        }
        row.addView(TextView(this).apply {
            this.text = "·"
            setTextColor(getColor(R.color.accent))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = 8.dp }
        })
        row.addView(TextView(this).apply {
            this.text = text
            setTextColor(getColor(R.color.ink_sub))
            textSize = 13f
            setLineSpacing(4f.dp, 1f)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        box.addView(row)
    }

    private fun dim(text: String, box: LinearLayout) {
        box.addView(TextView(this).apply {
            this.text = text
            setTextColor(getColor(R.color.ink_dim))
            textSize = 11.5f
            setLineSpacing(4f.dp, 1f)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10.dp }
        })
    }
}
