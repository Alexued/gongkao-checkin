package com.gongkao.checkin.view

import android.animation.ValueAnimator
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

/** 进度环：扫过角度用弹簧曲线补间，末端带一个发光小圆点。 */
class RingProgressView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    private var shown = 0f
    private var animator: ValueAnimator? = null

    var strokeWidth = dp(9f)
        set(v) { field = v; track.strokeWidth = v; arc.strokeWidth = v; invalidate() }
    var startColor = Color.parseColor("#6C8CFF")
    var endColor = Color.parseColor("#35D0BA")
    var trackColor = Color.parseColor("#1F2A44")

    init {
        track.strokeWidth = strokeWidth
        arc.strokeWidth = strokeWidth
    }

    fun setProgress(p: Float, animated: Boolean = true) {
        val target = p.coerceIn(0f, 1f)
        animator?.cancel()
        if (!animated) { shown = target; invalidate(); return }
        val from = shown
        animator = Motion.animate(760L, Motion.SOFT) {
            shown = from + (target - from) * it
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        arc.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            startColor, endColor, Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val pad = strokeWidth / 2f + dp(1f)
        box.set(pad, pad, width - pad, height - pad)
        track.color = trackColor
        canvas.drawArc(box, 0f, 360f, false, track)
        if (shown <= 0f) return

        val sweep = 360f * shown
        canvas.drawArc(box, -90f, sweep, false, arc)

        // 末端光点
        val cx = box.centerX()
        val cy = box.centerY()
        val r = box.width() / 2f
        val rad = Math.toRadians((-90f + sweep).toDouble())
        val px = cx + r * Math.cos(rad).toFloat()
        val py = cy + r * Math.sin(rad).toFloat()
        glow.color = endColor
        glow.alpha = 60
        canvas.drawCircle(px, py, strokeWidth * 1.15f, glow)
        dot.color = Color.WHITE
        canvas.drawCircle(px, py, strokeWidth * 0.36f, dot)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
