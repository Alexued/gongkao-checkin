package com.gongkao.checkin.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.gongkao.checkin.anim.Motion
import kotlin.math.cos
import kotlin.math.sin

/**
 * 打卡圆圈。完成时依次发生：圆环填充 → 勾线沿路径描出 → 冲击环扩散 → 小粒子四散。
 * 全部由一条 0..1 的弹簧进度驱动，各段用不同区间映射，保证节奏连贯。
 */
class CheckCircleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val particle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pixelRect = RectF()
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }

    private val tickPath = Path()
    private val drawPath = Path()
    private val measure = PathMeasure()

    private var progress = 0f          // 0 未完成 1 完成
    private var burstT = -1f           // <0 表示不画
    private var animator: ValueAnimator? = null
    private var burstAnimator: ValueAnimator? = null

    var accent = Color.parseColor("#6C8CFF")
        set(v) { field = v; invalidate() }
    var idleColor = Color.parseColor("#3A4560")

    /** 多次打卡的任务显示 "2/5"，单次任务只画勾。 */
    var countText: String? = null
        set(v) { field = v; invalidate() }

    fun setDone(done: Boolean, animated: Boolean) {
        val target = if (done) 1f else 0f
        animator?.cancel()
        if (!animated) {
            progress = target
            burstT = -1f
            invalidate()
            return
        }
        val from = progress
        animator = Motion.animate(if (done) 620L else 320L, if (done) Motion.BOUNCY else Motion.EXIT) {
            progress = (from + (target - from) * it).coerceIn(0f, 1.02f)
            invalidate()
        }
        if (done) fireBurst()
    }

    private fun fireBurst() {
        burstAnimator?.cancel()
        burstAnimator = Motion.animate(720L, Motion.EXIT, onEnd = { burstT = -1f; invalidate() }) {
            burstT = it
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) / 2f - dp(3f)
        ring.strokeWidth = dp(2.2f)
        tickPaint.strokeWidth = r * 0.22f
        label.textSize = r * 0.78f
        tickPath.reset()
        tickPath.moveTo(cx - r * 0.42f, cy + r * 0.04f)
        tickPath.lineTo(cx - r * 0.10f, cy + r * 0.34f)
        tickPath.lineTo(cx + r * 0.46f, cy - r * 0.32f)
        measure.setPath(tickPath, false)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - dp(3f)

        // 冲击环：像素方块沿环排列，一起向外扩散淡出，比描边圆更有"打卡"的颗粒感
        if (burstT >= 0f) {
            val t = burstT
            val ringCount = 10
            val ringR = r * (1f + t * 0.9f)
            val pixelSize = dp(2.6f) * (1f - t * 0.35f)
            particle.color = accent
            particle.alpha = ((1f - t) * 150).toInt().coerceIn(0, 255)
            for (i in 0 until ringCount) {
                val a = Math.toRadians((360.0 / ringCount) * i)
                val px = cx + ringR * cos(a).toFloat()
                val py = cy + ringR * sin(a).toFloat()
                pixelRect.set(px - pixelSize, py - pixelSize, px + pixelSize, py + pixelSize)
                canvas.drawRect(pixelRect, particle)
            }
            // 飞散的像素粒子：方块而非圆点，带轻微旋转，扩散更远
            val count = 8
            for (i in 0 until count) {
                val a = Math.toRadians((360.0 / count) * i - 90)
                val dist = r * (1.15f + t * 1.25f)
                val px = cx + dist * cos(a).toFloat()
                val py = cy + dist * sin(a).toFloat()
                val half = dp(2.4f) * (1f - t * 0.55f)
                particle.alpha = ((1f - t) * 210).toInt().coerceIn(0, 255)
                canvas.save()
                canvas.rotate((t * 140f), px, py)
                pixelRect.set(px - half, py - half, px + half, py + half)
                canvas.drawRect(pixelRect, particle)
                canvas.restore()
            }
        }

        // 底环
        ring.color = if (progress > 0.02f) accent else idleColor
        ring.alpha = if (progress > 0.02f) 255 else 200
        canvas.drawCircle(cx, cy, r, ring)

        if (progress > 0.01f) {
            fill.color = accent
            fill.alpha = (255 * progress.coerceAtMost(1f)).toInt()
            canvas.drawCircle(cx, cy, r * progress.coerceAtMost(1f), fill)
        }

        val text = countText
        if (text != null) {
            label.color = if (progress > 0.5f) Color.WHITE else Color.parseColor("#8C97AF")
            val fm = label.fontMetrics
            canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, label)
            return
        }

        // 勾线：progress 的后 65% 用来描线
        val tickT = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)
        if (tickT > 0f) {
            drawPath.reset()
            measure.getSegment(0f, measure.length * tickT, drawPath, true)
            tickPaint.color = Color.WHITE
            canvas.drawPath(drawPath, tickPaint)
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
