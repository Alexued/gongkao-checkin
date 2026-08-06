package com.gongkao.checkin.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.gongkao.checkin.R
import com.gongkao.checkin.anim.Motion

/** 通用 UI 小工具：dp 换算、系统栏内边距、点按手感、任务配色。 */
object Ui {

    private val TASK_COLORS = intArrayOf(
        R.color.task_0, R.color.task_1, R.color.task_2,
        R.color.task_3, R.color.task_4, R.color.task_5
    )

    fun taskColor(ctx: Context, index: Int): Int {
        val i = ((index % TASK_COLORS.size) + TASK_COLORS.size) % TASK_COLORS.size
        return ctx.getColor(TASK_COLORS[i])
    }

    fun fade(c: Int, alpha: Float): Int =
        ColorUtils.setAlphaComponent(c, (alpha.coerceIn(0f, 1f) * 255).toInt())

    fun mix(a: Int, b: Int, t: Float): Int = ColorUtils.blendARGB(a, b, t.coerceIn(0f, 1f))
}

val Int.dp: Int get() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
val Float.dp: Float get() = this * android.content.res.Resources.getSystem().displayMetrics.density

fun Context.color(id: Int): Int = getColor(id)

fun Context.inflate(layout: Int, parent: ViewGroup?, attach: Boolean = false): View =
    LayoutInflater.from(this).inflate(layout, parent, attach)

fun ViewGroup.inflateChild(layout: Int): View =
    LayoutInflater.from(context).inflate(layout, this, false)

/**
 * targetSdk 36 强制 edge-to-edge，加上主题里状态栏透明，内容会画到系统栏下面。
 * 这里统一手动补内边距：顶部给状态栏，底部给导航栏。
 */
fun Activity.edgeToEdge() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
}

/** 顶部避开状态栏（在原有 padding 上叠加，只叠一次）。 */
fun View.padTopInset(extra: Int = 0) {
    val base = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
        v.updatePadding(top = base + top + extra)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/** 底部避开导航栏。 */
fun View.padBottomInset(extra: Int = 0) {
    val base = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        v.updatePadding(bottom = base + bottom + extra)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/** 上下都避开。 */
fun View.padVerticalInsets() {
    val top = paddingTop
    val bottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.updatePadding(top = top + bars.top, bottom = bottom + bars.bottom)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/** 统一的可点击：按压回弹 + 轻震动 + 点击回调。 */
fun View.tap(haptic: Boolean = true, block: (View) -> Unit) {
    Motion.touchable(this)
    isClickable = true
    setOnClickListener {
        if (haptic) Motion.tick(this)
        block(it)
    }
}

fun View.show(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

/** 文字内容变化时做一次轻微的换字动效。 */
fun TextView.setTextAnimated(value: CharSequence) {
    if (text == value) return
    animate().cancel()
    Motion.animate(120L, Motion.EXIT, onEnd = {
        text = value
        alpha = 0f
        translationY = 6f.dp
        animate().alpha(1f).translationY(0f)
            .setDuration(220).setInterpolator(Motion.SOFT).start()
    }) { f -> alpha = 1f - f }
}

/**
 * 应用内吐司。有 Activity 上下文时走自绘的 [AppToast]（样式可控），
 * 拿不到 Activity 才退回系统 Toast —— 系统 Toast 在 MIUI 上样式完全不受控。
 */
fun Context.toast(msg: CharSequence) {
    val activity = when (this) {
        is Activity -> this
        is android.content.ContextWrapper -> baseContext as? Activity
        else -> null
    }
    if (activity != null && !activity.isFinishing) {
        AppToast.show(activity, msg)
    } else {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

inline fun <reified T : Activity> Context.open(vararg extras: Pair<String, String>) {
    val i = Intent(this, T::class.java)
    extras.forEach { (k, v) -> i.putExtra(k, v) }
    startActivity(i)
}

/** 灰底上的浅色描边分隔线颜色。 */
fun Context.dividerColor(): Int = getColor(R.color.divider)

fun Context.inkColor(): Int = getColor(R.color.ink)

fun accentOf(ctx: Context): Int = ctx.getColor(R.color.accent)

fun transparentBlack(alpha: Float): Int =
    ColorUtils.setAlphaComponent(Color.BLACK, (alpha * 255).toInt())
