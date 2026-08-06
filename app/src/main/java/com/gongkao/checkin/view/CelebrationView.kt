package com.gongkao.checkin.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.gongkao.checkin.anim.Motion
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 全天完成后的庆祝层：径向光线扩散 + 彩带纸片下落 + 中央文案弹入。
 * 纸片带旋转与横向摆动，落速各不相同，避免机械感。
 */
class CelebrationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private class Piece(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var rot: Float, var vr: Float, var w: Float, var h: Float,
        var color: Int, var swing: Float, var phase: Float, var round: Boolean
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val pieces = mutableListOf<Piece>()
    private val colors = intArrayOf(
        Color.parseColor("#6C8CFF"), Color.parseColor("#35D0BA"),
        Color.parseColor("#FFB24D"), Color.parseColor("#FF6B8B"),
        Color.parseColor("#C08BFF"), Color.parseColor("#FFFFFF")
    )

    private var t = 0f
    private var animator: ValueAnimator? = null
    private var rayOrigin = floatArrayOf(0.5f, 0.42f)
    private var onDone: (() -> Unit)? = null

    /** 局部小型爆发（单个任务完成），不带光线与文案。 */
    fun burstAt(cx: Float, cy: Float, count: Int = 22) {
        spawn(cx, cy, count, spread = 1f, upward = true)
        run(1100L, rays = false)
    }

    /** 全屏庆祝。 */
    fun celebrate(originX: Float, originY: Float, onEnd: (() -> Unit)? = null) {
        onDone = onEnd
        rayOrigin = floatArrayOf(originX / width.coerceAtLeast(1), originY / height.coerceAtLeast(1))
        pieces.clear()
        // 顶部两侧礼花
        spawn(width * 0.12f, height * 0.30f, 32, spread = 1.3f, upward = true)
        spawn(width * 0.88f, height * 0.30f, 32, spread = 1.3f, upward = true)
        // 上方飘落
        repeat(46) {
            pieces.add(newPiece(Random.nextFloat() * width, -Random.nextFloat() * height * 0.4f, 0f, 0f))
        }
        run(2600L, rays = true)
    }

    private fun spawn(cx: Float, cy: Float, count: Int, spread: Float, upward: Boolean) {
        repeat(count) {
            val angle = if (upward) Random.nextDouble(-Math.PI * 0.92, -Math.PI * 0.08)
            else Random.nextDouble(0.0, Math.PI * 2)
            val speed = (dp(4f) + Random.nextFloat() * dp(11f)) * spread
            pieces.add(
                newPiece(
                    cx, cy,
                    cos(angle).toFloat() * speed,
                    sin(angle).toFloat() * speed
                )
            )
        }
    }

    private fun newPiece(x: Float, y: Float, vx: Float, vy: Float): Piece {
        val size = dp(4f) + Random.nextFloat() * dp(5f)
        return Piece(
            x = x, y = y, vx = vx, vy = vy,
            rot = Random.nextFloat() * 360f,
            vr = -9f + Random.nextFloat() * 18f,
            w = size, h = size * (0.5f + Random.nextFloat() * 1.1f),
            color = colors[Random.nextInt(colors.size)],
            swing = dp(0.5f) + Random.nextFloat() * dp(1.6f),
            phase = Random.nextFloat() * 6.28f,
            round = Random.nextFloat() < 0.28f
        )
    }

    private var showRays = false

    private fun run(duration: Long, rays: Boolean) {
        showRays = rays
        visibility = VISIBLE
        alpha = 1f
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = android.animation.TimeInterpolator { it }
            addUpdateListener {
                t = it.animatedFraction
                step()
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    animate().alpha(0f).setDuration(320)
                        .setInterpolator(Motion.EXIT)
                        .withEndAction {
                            visibility = GONE
                            pieces.clear()
                            onDone?.invoke()
                            onDone = null
                        }.start()
                }
            })
            start()
        }
    }

    private fun step() {
        val g = dp(0.42f)
        pieces.forEach { p ->
            p.vy += g
            p.vx *= 0.994f
            p.x += p.vx + sin(p.phase + t * 12f) * p.swing
            p.y += p.vy
            p.rot += p.vr
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (showRays) drawRays(canvas)
        pieces.forEach { p ->
            if (p.y > height + dp(20f)) return@forEach
            paint.color = p.color
            paint.alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.rotate(p.rot, p.x, p.y)
            if (p.round) canvas.drawCircle(p.x, p.y, p.w / 2f, paint)
            else {
                rect.set(p.x - p.w / 2, p.y - p.h / 2, p.x + p.w / 2, p.y + p.h / 2)
                canvas.drawRoundRect(rect, dp(1f), dp(1f), paint)
            }
            canvas.restore()
        }
    }

    private fun drawRays(canvas: Canvas) {
        val rt = (t / 0.42f).coerceIn(0f, 1f)
        if (rt >= 1f) return
        val eased = Motion.EMPHASIZED.getInterpolation(rt)
        val cx = width * rayOrigin[0]
        val cy = height * rayOrigin[1]
        val maxR = maxOf(width, height) * 0.62f
        rayPaint.strokeWidth = dp(3f)
        val n = 14
        for (i in 0 until n) {
            val a = Math.toRadians((360.0 / n) * i + eased * 22)
            val inner = maxR * eased * 0.42f
            val outer = inner + maxR * 0.20f * (1f - eased * 0.4f)
            rayPaint.color = colors[i % colors.size]
            rayPaint.alpha = ((1f - eased) * 130).toInt().coerceIn(0, 255)
            canvas.drawLine(
                cx + inner * cos(a).toFloat(), cy + inner * sin(a).toFloat(),
                cx + outer * cos(a).toFloat(), cy + outer * sin(a).toFloat(),
                rayPaint
            )
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
