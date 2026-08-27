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
        Themes.apply(this)
        setContentView(R.layout.activity_list)
        Themes.installBackdrop(this)

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
        groupBox = null
        build()
    }

    // ------------------------------------------------------------ 内容积木

    /** 当前正在收集 row 的分组卡；为 null 时 row 各自独立成卡（原有行为）。 */
    private var groupBox: LinearLayout? = null

    /** row/kv 往哪儿塞：分组进行中就塞进分组卡，否则直接进 content。 */
    private val sink: LinearLayout get() = groupBox ?: content

    /**
     * 把 [block] 里建的若干 row 收进**同一张卡**，行间用细线隔开。
     *
     * 给设置页这种一屏十几行的页面用：一行一张卡会碎成一堆浮块，
     * 段落感只能靠色标硬撑。分组之后一个小节就是一块，扫一眼就知道边界在哪。
     * 不改 [row] 的默认行为，因为另外那些历史记录页就该是一行一卡。
     */
    protected fun group(block: () -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            // 行的按压效果和分隔线都不能溢出圆角
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8.dp }
        }
        content.addView(box)
        Motion.stagger(box, content.childCount - 1)
        groupBox = box
        try {
            block()
        } finally {
            // 异常也要还原，否则后续 row 会继续往这张卡里塞
            groupBox = null
        }
    }

    /**
     * 分组卡内部的行分隔线。左边缩进，跟标题文字对齐，不顶到卡片边。
     *
     * 用半透明的 `divider_glass`（12% 黑）而不是实色 `divider`：四套主题的卡片底色各不相同
     * （白/黑/两套玻璃），叠一层黑比固定的浅灰更稳 —— 浅灰线压在深色卡上会反过来变成亮线。
     */
    private fun groupDivider(box: LinearLayout) {
        box.addView(View(this).apply {
            setBackgroundColor(getColor(R.color.divider_glass))
            layoutParams = LinearLayout.LayoutParams(-1, 1).apply { marginStart = 16.dp }
        })
    }

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

    /**
     * 带色标的小节标题：左侧一根该分区的颜色竖条 + 标题 +（可选）说明。
     * 设置页项目多，纯文字标题分不开区块，加个色标一眼就知道换段了。
     */
    protected fun sectionColored(
        text: CharSequence,
        colorRes: Int,
        note: CharSequence? = null
    ): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = if (content.childCount == 0) 0 else 22.dp
                bottomMargin = 10.dp
                marginStart = 4.dp
            }
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        head.addView(View(this).apply {
            setBackgroundColor(getColor(colorRes))
            layoutParams = LinearLayout.LayoutParams(3.dp, 15.dp).apply { marginEnd = 9.dp }
        })
        head.addView(TextView(this).apply {
            this.text = text
            setTextColor(getColor(R.color.ink))
            textSize = 15f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        })
        box.addView(head)
        if (note != null) {
            box.addView(TextView(this).apply {
                this.text = note
                setTextColor(getColor(R.color.ink_dim))
                textSize = 11.5f
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = 5.dp
                    marginStart = 12.dp
                }
            })
        }
        content.addView(box)
        return box
    }

    /** 一行「标题 / 副标题 / 右值」的卡片，可点。 */
    protected fun row(
        title: CharSequence,
        sub: CharSequence? = null,
        value: CharSequence? = null,
        chevron: Boolean = false,
        onClick: (() -> Unit)? = null
    ): View {
        val box = sink
        val row = box.inflateChild(R.layout.item_session)
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

        if (box === content) {
            content.addView(row)
            Motion.stagger(row, content.childCount - 1)
        } else {
            // 分组内：卡背景和行间距交给分组卡，行自己只留内边距
            row.background = null
            (row.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = 0
            if (box.childCount > 0) groupDivider(box)
            box.addView(row)
            // 整张分组卡已经一起入场，行再各自淡入会打架
        }
        return row
    }

    /** 键值对，用于详情页的汇总数字。不传 parent 时跟随当前分组。 */
    protected fun kv(key: CharSequence, value: CharSequence, parent: LinearLayout = sink): View {
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
