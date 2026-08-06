package com.gongkao.checkin.ui

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.gongkao.checkin.R
import com.gongkao.checkin.anim.Motion

/**
 * 应用内吐司：从顶部滑入的胶囊，替换系统 Toast。
 *
 * 系统 Toast 在不同 ROM 上样式完全不受控（MIUI 上尤其明显），
 * 自己画一个才能和 app 风格统一。
 */
object AppToast {

    private const val HOLD = 1900L

    fun show(activity: Activity, msg: CharSequence) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        // 同时只留一个，新的顶掉旧的
        root.findViewWithTag<View>(TAG)?.let { root.removeView(it) }

        val pill = TextView(activity).apply {
            tag = TAG
            text = msg
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setBackgroundResource(R.drawable.bg_glass)
            setPadding(22.dp, 13.dp, 22.dp, 13.dp)
            gravity = Gravity.CENTER
            elevation = 30f.dp
            isClickable = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 74.dp
            }
        }
        root.addView(pill)

        pill.alpha = 0f
        pill.translationY = (-22f).dp
        pill.animate().alpha(1f).translationY(0f)
            .setDuration(280).setInterpolator(Motion.EMPHASIZED)
            .withEndAction {
                pill.animate().alpha(0f).translationY((-16f).dp)
                    .setStartDelay(HOLD).setDuration(220).setInterpolator(Motion.EXIT)
                    .withEndAction { (pill.parent as? ViewGroup)?.removeView(pill) }
                    .start()
            }
            .start()
    }

    private const val TAG = "app_toast"
}
