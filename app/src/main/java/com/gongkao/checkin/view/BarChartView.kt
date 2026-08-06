package com.gongkao.checkin.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.gongkao.checkin.anim.Motion

/** 柱状图：入场时每根柱子按索引错开生长，用于用时对比 / 每日完成量。 */
class BarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Bar(val label: String, val value: Float, val highlight: Boolean = false)

    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#8C97AF")
    }
    private val avgLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 8f), 0f)
        color = Color.parseColor("#FFB24D")
    }
    private val rect = RectF()

    private var bars: List<Bar> = emptyList()
    private var t = 0f
    /** 平均值参考线，<=0 不画 */
    var average: Float = 0f

    var accent = Color.parseColor("#6C8CFF")
    var accentHi = Color.parseColor("#FF6B8B")
    /** 值越小越好（用时对比场景），影响高亮颜色语义 */
    var lowerIsBetter = false
    /**
     * 固定纵轴上限，<=0 表示按数据最大值自适应。
     * 百分比这类有绝对刻度的数据要设成 100f，否则只有一天 20% 时那根柱子会顶到顶。
     */
    var fixedMax: Float = 0f

    fun setBars(list: List<Bar>, animated: Boolean = true) {
        bars = list
        if (!animated) { t = 1f; invalidate(); return }
        t = 0f
        Motion.animate(760L, Motion.SOFT) { t = it; invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        if (bars.isEmpty()) return
        text.textSize = dp(10f)
        val labelH = dp(15f)
        val h = height - paddingTop - paddingBottom - labelH
        val w = width - paddingLeft - paddingRight
        val slot = w.toFloat() / bars.size
        val bw = (slot * 0.56f).coerceAtMost(dp(22f))
        val max = if (fixedMax > 0f) fixedMax
        else (bars.maxOfOrNull { it.value } ?: 1f).coerceAtLeast(0.0001f)
        val baseY = (paddingTop + h).toFloat()

        bars.forEachIndexed { i, b ->
            val local = ((t - i * 0.045f) / 0.55f).coerceIn(0f, 1f)
            val eased = Motion.SOFT.getInterpolation(local)
            val bh = h * (b.value / max).coerceIn(0f, 1f) * eased
            val cx = paddingLeft + slot * i + slot / 2f
            rect.set(cx - bw / 2, baseY - bh, cx + bw / 2, baseY)
            val top = if (b.highlight) accentHi else accent
            bar.shader = LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                top, Color.argb(70, Color.red(top), Color.green(top), Color.blue(top)),
                Shader.TileMode.CLAMP
            )
            val r = bw / 2.4f
            canvas.drawRoundRect(rect, r, r, bar)
            canvas.drawText(b.label, cx, baseY + labelH * 0.86f, text)
        }

        if (average > 0f) {
            avgLine.strokeWidth = dp(1.2f)
            val y = baseY - h * (average / max) * t
            canvas.drawLine(paddingLeft.toFloat(), y, (width - paddingRight).toFloat(), y, avgLine)
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
