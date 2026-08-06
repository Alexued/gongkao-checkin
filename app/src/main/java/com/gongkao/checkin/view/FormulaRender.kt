package com.gongkao.checkin.view

import android.graphics.Canvas
import android.graphics.Paint

/**
 * 极简数学排版引擎，支持：
 *   /f{分子}{分母}   真正上下叠放的分数
 *   ^{..} / ^x       上标
 *   _{..} / _x       下标
 *   /r{n}{被开方数}  n 次根式
 * 其余字符按普通文本渲染，中英文混排都可以。
 */
internal object FormulaRender {

    class Metrics(var w: Float = 0f, var asc: Float = 0f, var desc: Float = 0f) {
        val height: Float get() = asc + desc
    }

    abstract class Node {
        val m = Metrics()
        abstract fun measure(paint: Paint, size: Float)
        abstract fun draw(canvas: Canvas, x: Float, baseline: Float, paint: Paint, size: Float)
    }

    class TextNode(val text: String) : Node() {
        override fun measure(paint: Paint, size: Float) {
            paint.textSize = size
            m.w = paint.measureText(text)
            val fm = paint.fontMetrics
            m.asc = -fm.ascent
            m.desc = fm.descent
        }

        override fun draw(canvas: Canvas, x: Float, baseline: Float, paint: Paint, size: Float) {
            paint.textSize = size
            canvas.drawText(text, x, baseline, paint)
        }
    }

    class Row(val children: List<Node>) : Node() {
        override fun measure(paint: Paint, size: Float) {
            var w = 0f; var a = 0f; var d = 0f
            children.forEach {
                it.measure(paint, size)
                w += it.m.w; a = maxOf(a, it.m.asc); d = maxOf(d, it.m.desc)
            }
            if (children.isEmpty()) {
                paint.textSize = size
                val fm = paint.fontMetrics
                a = -fm.ascent; d = fm.descent
            }
            m.w = w; m.asc = a; m.desc = d
        }

        override fun draw(canvas: Canvas, x: Float, baseline: Float, paint: Paint, size: Float) {
            var cx = x
            children.forEach {
                it.measure(paint, size)
                it.draw(canvas, cx, baseline, paint, size)
                cx += it.m.w
            }
        }
    }

    class Frac(val num: Node, val den: Node) : Node() {
        private var inner = 0f
        private var pad = 0f
        private var gap = 0f
        private var barUp = 0f

        override fun measure(paint: Paint, size: Float) {
            inner = maxOf(size * 0.94f, size * 0.6f)
            pad = size * 0.16f
            gap = size * 0.14f
            barUp = size * 0.30f
            num.measure(paint, inner)
            den.measure(paint, inner)
            m.w = maxOf(num.m.w, den.m.w) + pad * 2
            m.asc = barUp + gap + num.m.desc + num.m.asc
            m.desc = -barUp + gap + den.m.asc + den.m.desc
        }

        override fun draw(canvas: Canvas, x: Float, baseline: Float, paint: Paint, size: Float) {
            measure(paint, size)
            val barY = baseline - barUp
            val cx = x + m.w / 2f
            num.draw(canvas, cx - num.m.w / 2f, barY - gap - num.m.desc, paint, inner)
            den.draw(canvas, cx - den.m.w / 2f, barY + gap + den.m.asc, paint, inner)
            val stroke = paint.strokeWidth
            val style = paint.style
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = maxOf(1.4f, size * 0.055f)
            canvas.drawLine(x + pad * 0.35f, barY, x + m.w - pad * 0.35f, barY, paint)
            paint.strokeWidth = stroke
            paint.style = style
        }
    }

    class Script(val body: Node, val sup: Boolean) : Node() {
        private var inner = 0f
        private var shift = 0f

        override fun measure(paint: Paint, size: Float) {
            inner = size * 0.68f
            shift = if (sup) size * 0.42f else -size * 0.20f
            body.measure(paint, inner)
            m.w = body.m.w + size * 0.04f
            m.asc = maxOf(0f, body.m.asc + shift)
            m.desc = maxOf(0f, body.m.desc - shift)
        }

        override fun draw(canvas: Canvas, x: Float, baseline: Float, paint: Paint, size: Float) {
            measure(paint, size)
            body.draw(canvas, x, baseline - shift, paint, inner)
        }
    }

    class Root(val index: Node, val body: Node) : Node() {
        private var glyph = 0f
        private var idxSize = 0f
        private var top = 0f

        override fun measure(paint: Paint, size: Float) {
            idxSize = size * 0.56f
            index.measure(paint, idxSize)
            body.measure(paint, size)
            glyph = size * 0.52f
            top = size * 0.20f
            m.w = index.m.w * 0.75f + glyph + body.m.w + size * 0.12f
            m.asc = body.m.asc + top
            m.desc = body.m.desc
        }

        override fun draw(canvas: Canvas, x: Float, baseline: Float, paint: Paint, size: Float) {
            measure(paint, size)
            val idxW = index.m.w * 0.75f
            index.draw(canvas, x, baseline - m.asc * 0.62f, paint, idxSize)
            val gx = x + idxW
            val topY = baseline - m.asc
            val botY = baseline + m.desc * 0.85f
            val stroke = paint.strokeWidth
            val style = paint.style
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = maxOf(1.4f, size * 0.055f)
            val p = android.graphics.Path().apply {
                moveTo(gx, baseline - m.asc * 0.35f)
                lineTo(gx + glyph * 0.34f, botY)
                lineTo(gx + glyph * 0.72f, topY)
                lineTo(gx + glyph + body.m.w + size * 0.10f, topY)
            }
            canvas.drawPath(p, paint)
            paint.strokeWidth = stroke
            paint.style = style
            body.draw(canvas, gx + glyph, baseline, paint, size)
        }
    }

    // ------------------------------------------------------------------ 解析

    fun parse(src: String): Row = Row(parseNodes(src, intArrayOf(0), false))

    private fun parseNodes(s: String, p: IntArray, stopOnBrace: Boolean): MutableList<Node> {
        val out = mutableListOf<Node>()
        val buf = StringBuilder()
        fun flush() {
            if (buf.isNotEmpty()) { out.add(TextNode(buf.toString())); buf.clear() }
        }
        while (p[0] < s.length) {
            val c = s[p[0]]
            when {
                stopOnBrace && c == '}' -> { flush(); return out }
                s.startsWith("/f{", p[0]) -> {
                    flush(); p[0] += 2
                    val a = brace(s, p); val b = brace(s, p)
                    out.add(Frac(a, b))
                }
                s.startsWith("/r{", p[0]) -> {
                    flush(); p[0] += 2
                    val n = brace(s, p); val body = brace(s, p)
                    out.add(Root(n, body))
                }
                (c == '^' || c == '_') && p[0] + 1 < s.length -> {
                    flush()
                    val sup = c == '^'
                    p[0]++
                    val node = if (s[p[0]] == '{') brace(s, p) else {
                        val ch = s[p[0]].toString(); p[0]++; Row(listOf(TextNode(ch)))
                    }
                    out.add(Script(node, sup))
                }
                else -> { buf.append(c); p[0]++ }
            }
        }
        flush()
        return out
    }

    private fun brace(s: String, p: IntArray): Row {
        if (p[0] < s.length && s[p[0]] == '{') p[0]++
        val nodes = parseNodes(s, p, true)
        if (p[0] < s.length && s[p[0]] == '}') p[0]++
        return Row(nodes)
    }

    /** 顶层拆成可换行的原子：普通文本按空格/中文字拆，结构体不可拆。 */
    fun atoms(row: Row): List<Node> {
        val out = mutableListOf<Node>()
        row.children.forEach { n ->
            if (n is TextNode) {
                val sb = StringBuilder()
                n.text.forEach { ch ->
                    if (ch == ' ') {
                        if (sb.isNotEmpty()) { out.add(TextNode(sb.toString())); sb.clear() }
                        out.add(TextNode(" "))
                    } else if (ch.code > 0x2E80) {
                        if (sb.isNotEmpty()) { out.add(TextNode(sb.toString())); sb.clear() }
                        out.add(TextNode(ch.toString()))
                    } else sb.append(ch)
                }
                if (sb.isNotEmpty()) out.add(TextNode(sb.toString()))
            } else out.add(n)
        }
        return out
    }
}
