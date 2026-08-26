package com.gongkao.checkin.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gongkao.checkin.R
import com.gongkao.checkin.anim.Motion
import com.gongkao.checkin.data.Repo

/**
 * 二级页面通用骨架：顶栏（返回 + 标题 + 可选动作）+ 竖排内容。
 * 子类在 [build] 里往 [content] 里塞卡片，数据变化时会自动重建。
 */
abstract class ListScreen : AppCompatActivity() {

    protected lateinit var content: LinearLayout
        private set

    private lateinit var barTitle: TextView
    private lateinit var barAction: TextView

    /** 顶栏标题 */
    protected abstract fun title(): CharSequence

    /** 往 content 里填内容；每次数据变更都会重新调用。 */
    protected abstract fun build()

    /** 是否随 Repo 变更自动重建，默认开。 */
    protected open val liveUpdate = true

    private val onData: () -> Unit = { rebuild() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edgeToEdge()
        Repo.init(this)
        setContentView(R.layout.activity_list)

        val topBar = findViewById<View>(R.id.topBar)
        topBar.padTopInset()
        barTitle = topBar.findViewById(R.id.barTitle)
        barAction = topBar.findViewById(R.id.barAction)
        topBar.findViewById<ImageView>(R.id.btnBack).tap { finish() }

        content = findViewById(R.id.content)
        (content.parent as View).padBottomInset()

        barTitle.text = title()
        rebuild()
        if (liveUpdate) Repo.addListener(onData)
    }

    override fun onDestroy() {
        if (liveUpdate) Repo.removeListener(onData)
        super.onDestroy()
    }

    protected fun action(text: CharSequence, block: () -> Unit) {
        barAction.show(true)
        barAction.text = text
        barAction.tap { block() }
    }

    protected fun rebuild() {
        content.removeAllViews()
        build()
    }

    // ------------------------------------------------------------ 内容积木

    /** 小节标题，section 之间自动留白。 */
    protected fun section(text: CharSequence): TextView {
        val tv = TextView(this).apply {
            this.text = text
            setTextColor(getColor(R.color.ink))
            textSize = 15f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = if (content.childCount == 0) 0 else 18.dp
                bottomMargin = 10.dp
                marginStart = 4.dp
            }
        }
        content.addView(tv)
        return tv
    }

    /** 一行「标题 / 副标题 / 右值」的卡片，可点。 */
    protected fun row(
        title: CharSequence,
        sub: CharSequence? = null,
        value: CharSequence? = null,
        chevron: Boolean = false,
        onClick: (() -> Unit)? = null
    ): View {
        val row = content.inflateChild(R.layout.item_session)
        row.findViewById<TextView>(R.id.sTitle).text = title
        row.findViewById<TextView>(R.id.sSub).apply {
            show(sub != null)
            text = sub ?: ""
        }
        row.findViewById<TextView>(R.id.sValue).apply {
            show(value != null)
            text = value ?: ""
        }
        row.findViewById<ImageView>(R.id.sChevron).show(chevron)
        if (onClick != null) row.tap { onClick() } else row.isClickable = false
        content.addView(row)
        Motion.stagger(row, content.childCount - 1)
        return row
    }

    /** 键值对，用于详情页的汇总数字。 */
    protected fun kv(key: CharSequence, value: CharSequence, parent: LinearLayout = content): View {
        val v = parent.inflateChild(R.layout.item_kv)
        v.findViewById<TextView>(R.id.k).text = key
        v.findViewById<TextView>(R.id.v).text = value
        parent.addView(v)
        return v
    }

    /** 一张空白卡片容器，调用方往里塞任意内容。 */
    protected fun card(padding: Int = 16): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(padding.dp, padding.dp, padding.dp, padding.dp)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8.dp }
        }
        content.addView(box)
        Motion.stagger(box, content.childCount - 1)
        return box
    }

    /** 小节下面的说明文字，解释这组设置的影响。 */
    protected fun hint(text: CharSequence): TextView {
        val tv = TextView(this).apply {
            this.text = text
            setTextColor(getColor(R.color.ink_dim))
            textSize = 12f
            setLineSpacing(4f.dp, 1f)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = 8.dp
                marginStart = 4.dp
                marginEnd = 4.dp
            }
        }
        content.addView(tv)
        return tv
    }

    protected fun empty(text: CharSequence = getString(R.string.no_record)) {
        val tv = TextView(this).apply {
            this.text = text
            setTextColor(getColor(R.color.ink_dim))
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = 24.dp
                bottomMargin = 24.dp
            }
        }
        content.addView(tv)
    }
}
