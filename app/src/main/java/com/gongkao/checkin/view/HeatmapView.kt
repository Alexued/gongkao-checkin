package com.gongkao.checkin.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.gongkao.checkin.anim.Motion
import java.time.DayOfWeek
import java.time.LocalDate

/** 打卡热力图。列=周，行=周一~周日，点击某格回调日期。 */
class HeatmapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val cell = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#6C8CFF")
    }
    private val rect = RectF()

    private var weeks = 14
    private var start: LocalDate = LocalDate.now()
    private var ratios: Map<String, Float> = emptyMap()
    private var t = 1f
    private var picked: String? = null

    var emptyColor = Color.parseColor("#EDF0F7")
    var accent = Color.parseColor("#6C8CFF")
    var onPick: ((String) -> Unit)? = null

    fun setData(ratios: Map<String, Float>, weeks: Int = 14, animated: Boolean = true) {
        this.ratios = ratios
        this.weeks = weeks
        val today = LocalDate.now()
        val monday = today.with(DayOfWeek.MONDAY)
        start = monday.minusWeeks((weeks - 1).toLong())
        if (animated) {
            t = 0f
            Motion.animate(700L, Motion.EMPHASIZED) { t = it; invalidate() }
        } else t = 1f
        requestLayout()
        invalidate()
    }

    fun select(date: String?) { picked = date; invalidate() }

    private fun cellSize(): Float {
        val w = (width - paddingLeft - paddingRight).toFloat()
        val gap = dp(3f)
        return ((w - gap * (weeks - 1)) / weeks).coerceAtLeast(dp(6f))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val gap = dp(3f)
        val size = ((w - paddingLeft - paddingRight - gap * (weeks - 1)) / weeks).coerceAtLeast(dp(6f))
        val h = size * 7 + gap * 6 + paddingTop + paddingBottom
        setMeasuredDimension(w, h.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val size = cellSize()
        val gap = dp(3f)
        val today = LocalDate.now()
        for (c in 0 until weeks) {
            for (r in 0 until 7) {
                val date = start.plusWeeks(c.toLong()).plusDays(r.toLong())
                if (date.isAfter(today)) continue
                val key = date.toString()
                val ratio = ratios[key] ?: 0f
                val local = ((t - c * 0.035f) / 0.5f).coerceIn(0f, 1f)
                val x = paddingLeft + c * (size + gap)
                val y = paddingTop + r * (size + gap)
                rect.set(x, y, x + size, y + size)
                val inset = size * (1f - Motion.SOFT.getInterpolation(local)) / 2f
                rect.inset(inset, inset)
                cell.color = when {
                    ratio <= 0f -> emptyColor
                    else -> blend(emptyColor, accent, 0.25f + 0.75f * ratio.coerceAtMost(1f))
                }
                canvas.drawRoundRect(rect, size * 0.28f, size * 0.28f, cell)
                if (key == picked || (picked == null && date == today)) {
                    stroke.strokeWidth = dp(1.6f)
                    rect.set(x, y, x + size, y + size)
                    rect.inset(-dp(1.2f), -dp(1.2f))
                    canvas.drawRoundRect(rect, size * 0.32f, size * 0.32f, stroke)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        val size = cellSize()
        val gap = dp(3f)
        val c = ((event.x - paddingLeft) / (size + gap)).toInt()
        val r = ((event.y - paddingTop) / (size + gap)).toInt()
        if (c < 0 || c >= weeks || r < 0 || r > 6) return true
        val date = start.plusWeeks(c.toLong()).plusDays(r.toLong())
        if (date.isAfter(LocalDate.now())) return true
        picked = date.toString()
        invalidate()
        Motion.tick(this)
        onPick?.invoke(date.toString())
        return true
    }

    private fun blend(a: Int, b: Int, f: Float): Int {
        val k = f.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()
        )
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
