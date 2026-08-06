package com.gongkao.checkin.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/** 渲染 FormulaRender 语法的公式，支持自动换行。 */
class FormulaView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("serif", Typeface.NORMAL)
        color = Color.parseColor("#0E1526")
    }

    private var lines: List<List<FormulaRender.Node>> = emptyList()
    private var lineMetrics: List<FormulaRender.Metrics> = emptyList()
    private var atoms: List<FormulaRender.Node> = emptyList()

    var textSizePx = 20f * resources.displayMetrics.density
        set(v) { field = v; requestLayout(); invalidate() }

    var textColor: Int
        get() = paint.color
        set(v) { paint.color = v; invalidate() }

    var expression: String = ""
        set(v) {
            field = v
            atoms = FormulaRender.atoms(FormulaRender.parse(v))
            requestLayout()
            invalidate()
        }

    private fun layoutLines(maxWidth: Float) {
        val ls = mutableListOf<List<FormulaRender.Node>>()
        val ms = mutableListOf<FormulaRender.Metrics>()
        var cur = mutableListOf<FormulaRender.Node>()
        var w = 0f
        var asc = 0f
        var desc = 0f

        fun push() {
            if (cur.isEmpty()) return
            // 行尾空格不占位
            while (cur.isNotEmpty() && (cur.last() as? FormulaRender.TextNode)?.text == " ") {
                cur.removeAt(cur.size - 1)
            }
            ls.add(cur)
            ms.add(FormulaRender.Metrics(w, asc, desc))
            cur = mutableListOf(); w = 0f; asc = 0f; desc = 0f
        }

        atoms.forEach { n ->
            n.measure(paint, textSizePx)
            val isSpace = (n as? FormulaRender.TextNode)?.text == " "
            if (w + n.m.w > maxWidth && cur.isNotEmpty() && !isSpace) push()
            if (isSpace && cur.isEmpty()) return@forEach
            cur.add(n)
            w += n.m.w
            asc = maxOf(asc, n.m.asc)
            desc = maxOf(desc, n.m.desc)
        }
        push()
        lines = ls
        lineMetrics = ms
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val avail = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight)
            .coerceAtLeast(1).toFloat()
        layoutLines(avail)
        val gap = textSizePx * 0.34f
        val h = lineMetrics.sumOf { (it.height).toDouble() }.toFloat() +
            gap * (lineMetrics.size - 1).coerceAtLeast(0) + paddingTop + paddingBottom
        val w = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec)
            else -> (lineMetrics.maxOfOrNull { it.w } ?: 0f).toInt() + paddingLeft + paddingRight
        }
        setMeasuredDimension(w, h.toInt().coerceAtLeast(1))
    }

    override fun onDraw(canvas: Canvas) {
        var y = paddingTop.toFloat()
        val gap = textSizePx * 0.34f
        lines.forEachIndexed { i, line ->
            val met = lineMetrics[i]
            val baseline = y + met.asc
            var x = paddingLeft.toFloat()
            line.forEach { n ->
                n.measure(paint, textSizePx)
                n.draw(canvas, x, baseline, paint, textSizePx)
                x += n.m.w
            }
            y += met.height + gap
        }
    }
}
