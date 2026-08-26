package com.gongkao.checkin.ui

import android.widget.LinearLayout
import android.widget.TextView
import com.gongkao.checkin.BuildConfig
import com.gongkao.checkin.R
import com.gongkao.checkin.data.ChangeEntry
import com.gongkao.checkin.data.ChangelogData
import com.gongkao.checkin.data.DateUtil

/** 更新日志。入口藏在设置-关于里连点 5 下版本号。 */
class ChangelogActivity : ListScreen() {

    override fun title(): CharSequence = getString(R.string.changelog_title)

    override fun build() {
        ChangelogData.entries.forEach { e -> entry(e) }
    }

    private fun entry(e: ChangeEntry) {
        val current = e.version == BuildConfig.VERSION_NAME
        sectionColored(
            "v${e.version}",
            if (current) R.color.accent else R.color.ink_dim,
            buildString {
                append(DateUtil.prettyStr(e.date))
                if (current) append(" · ").append(getString(R.string.changelog_current))
            }
        )
        val box = card(14)
        e.items.forEach { text ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 4.dp }
                setPadding(0, 5.dp, 0, 5.dp)
            }
            line.addView(TextView(this).apply {
                this.text = "·"
                textSize = 14f
                setTextColor(getColor(R.color.accent))
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = 8.dp }
            })
            line.addView(TextView(this).apply {
                this.text = text
                textSize = 13.5f
                setTextColor(getColor(R.color.ink))
                setLineSpacing(4f.dp, 1f)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            box.addView(line)
        }
    }
}
