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

    /**
     * 让 View 具备统一的按压反馈，同时保留原本的点击回调。
     *
     * [onLongPress] 给了就自己做长按检测（DOWN 起定时器、移动超过 slop 或抬手就取消），
     * **不用框架的 setOnLongClickListener**：列表在滚动容器里，父级一旦抢走手势就会给子 view
     * 发 CANCEL，框架那套待触发的长按随之作废——手指稍微一动就长按不出来。
     * CalendarView 的标记日长按早就是这么做的，这里沿用同一套。
     */
    fun touchable(
        view: View,
        scale: Float = 0.96f,
        onLongPress: ((View) -> Unit)? = null
    ) {
        val slop = android.view.ViewConfiguration.get(view.context).scaledTouchSlop
        var fired = false
        var pending: Runnable? = null

        fun cancelPending(v: View) {
            pending?.let { v.removeCallbacks(it) }
            pending = null
        }

        view.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    press(v, true, scale)
                    if (onLongPress != null) {
                        fired = false
                        val r = Runnable {
                            fired = true
                            press(v, false, scale)
                            onLongPress(v)
                        }
                        pending = r
                        v.postDelayed(
                            r,
                            android.view.ViewConfiguration.getLongPressTimeout().toLong()
                        )
                    }
                }

                android.view.MotionEvent.ACTION_MOVE -> {
                    // 按框架的规则：手指移出这个 view（外扩一点容差）才算取消。
                    // 不要拿「相对按下点的位移」判——手指按住时的微抖就会误取消，
                    // 长按基本按不出来。
                    if (pending != null && !insideWithSlop(v, ev.x, ev.y, slop)) {
                        cancelPending(v)
                    }
                }

                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    cancelPending(v)
                    press(v, false, scale)
                }
            }
            // 长按已经处理过就吃掉这一轮，避免抬手时又触发点击
            fired && ev.actionMasked == android.view.MotionEvent.ACTION_UP
        }
    }

    /** 触点是否还在 view 里（四周留 [slop] 容差），跟框架判长按/点击的口径一致。 */
    private fun insideWithSlop(v: View, x: Float, y: Float, slop: Int): Boolean =
        x >= -slop && y >= -slop && x < v.width + slop && y < v.height + slop

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
