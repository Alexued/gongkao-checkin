package com.gongkao.checkin.anim

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.PathInterpolator
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 非线性动效工具集。曲线取自 Apple 的运动设计习惯：
 * 进场用轻微欠阻尼弹簧带一点回弹，出场用快出慢入，位移一律走 spring 而非线性。
 */
object Motion {

    /** iOS 系统默认的 ease-in-ease-out。 */
    val STANDARD: TimeInterpolator = PathInterpolator(0.42f, 0f, 0.58f, 1f)

    /** SwiftUI 过渡常用曲线，起步快、尾巴长，最有「顺滑」感。 */
    val EMPHASIZED: TimeInterpolator = PathInterpolator(0.32f, 0.72f, 0f, 1f)

    /** 出场：立刻离开，尾部收得干净。 */
    val EXIT: TimeInterpolator = PathInterpolator(0.4f, 0f, 1f, 1f)

    /** 进场：慢起快到位。 */
    val ENTER: TimeInterpolator = PathInterpolator(0f, 0f, 0.2f, 1f)

    /** 明显回弹，用于打卡这类需要「手感」的反馈。 */
    val BOUNCY: TimeInterpolator = spring(dampingRatio = 0.55f, cycles = 1.6f)

    /** 轻微回弹，用于卡片进场。 */
    val SOFT: TimeInterpolator = spring(dampingRatio = 0.78f, cycles = 1.1f)

    /**
     * 欠阻尼弹簧的归一化位移曲线。
     * x(t) = 1 − e^(−ζωt)[cos(ω_d t) + (ζω/ω_d)·sin(ω_d t)]
     */
    fun spring(dampingRatio: Float, cycles: Float): TimeInterpolator {
        val z = dampingRatio.coerceIn(0.05f, 0.999f)
        val w = cycles * 2f * Math.PI.toFloat()
        val wd = w * sqrt(1f - z * z)
        return TimeInterpolator { t ->
            if (t >= 1f) 1f
            else 1f - exp(-z * w * t) * (cos(wd * t) + (z * w / wd) * sin(wd * t))
        }
    }

    /** 按下时缩到 0.96，松手弹回。所有可点区域统一手感。 */
    fun press(view: View, down: Boolean, scale: Float = 0.96f) {
        val target = if (down) scale else 1f
        springTo(view, DynamicAnimation.SCALE_X, target)
        springTo(view, DynamicAnimation.SCALE_Y, target)
    }

    private val springs = HashMap<String, SpringAnimation>()

    fun springTo(
        view: View,
        property: DynamicAnimation.ViewProperty,
        value: Float,
        stiffness: Float = SpringForce.STIFFNESS_MEDIUM,
        damping: Float = 0.72f
    ) {
        val key = "${System.identityHashCode(view)}#$property"
        val anim = springs.getOrPut(key) {
            SpringAnimation(view, property).apply {
                spring = SpringForce().setStiffness(stiffness).setDampingRatio(damping)
            }
        }
        anim.spring.stiffness = stiffness
        anim.spring.dampingRatio = damping
        anim.animateToFinalPosition(value)
    }

    /** 让 View 具备统一的按压反馈，同时保留原本的点击回调。 */
    fun touchable(view: View, scale: Float = 0.96f) {
        view.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> press(v, true, scale)
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> press(v, false, scale)
            }
            false
        }
    }

    fun animate(
        durationMs: Long,
        interpolator: TimeInterpolator = EMPHASIZED,
        onEnd: (() -> Unit)? = null,
        block: (Float) -> Unit
    ): ValueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = durationMs
        this.interpolator = interpolator
        addUpdateListener { block(it.animatedFraction) }
        if (onEnd != null) addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = onEnd()
        })
        start()
    }

    /** 卡片列表逐个进场，每项延迟 [step] ms。 */
    fun stagger(view: View, index: Int, step: Long = 42L, rise: Float = 28f) {
        view.alpha = 0f
        view.translationY = rise
        view.animate()
            .alpha(1f).translationY(0f)
            .setStartDelay(index * step)
            .setDuration(460)
            .setInterpolator(SOFT)
            .start()
    }

    fun tick(view: View) = haptic(view, HapticFeedbackConstants.CLOCK_TICK)

    fun confirm(view: View) {
        val c = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.VIRTUAL_KEY
        haptic(view, c)
    }

    fun reject(view: View) {
        val c = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.LONG_PRESS
        haptic(view, c)
    }

    private fun haptic(view: View, constant: Int) {
        runCatching {
            view.performHapticFeedback(
                constant,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        }
    }

    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
