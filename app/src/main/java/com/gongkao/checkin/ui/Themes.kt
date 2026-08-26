package com.gongkao.checkin.ui

import android.app.Activity
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatDelegate
import com.gongkao.checkin.data.AppTheme
import com.gongkao.checkin.data.Repo

/**
 * 主题的应用入口。每个 Activity 在 setContentView **之前** 调 [apply]。
 *
 * 分两步：先按 [AppTheme.night] 定夜间模式（决定用 values 还是 values-night 的调色板），
 * 再套上该主题的 style（决定卡片质感那几个 ?attr）。
 */
object Themes {

    /** 模糊半径。太小看不出玻璃感，太大彩色光团会糊成一片灰。 */
    private const val BLUR_RADIUS = 46f

    fun apply(activity: Activity) {
        val theme = Repo.appTheme()
        // 夜间模式是进程级的，重复设同一个值不会触发重建
        AppCompatDelegate.setDefaultNightMode(
            if (theme.night) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        activity.setTheme(theme.style)
    }

    /**
     * 给页面铺底层彩色背景（只有模糊/液态玻璃主题需要）。
     * 在 setContentView **之后** 调，会把背景插到内容下面。
     *
     * API 31+ 用 RenderEffect 做真高斯模糊；低版本就用 drawable 本身的柔和渐变，
     * 观感弱一些但不会露出硬边。
     */
    fun installBackdrop(activity: Activity) {
        val theme = Repo.appTheme()
        if (!theme.hasBackdrop) return

        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val res = themeAttrDrawable(activity) ?: return

        val backdrop = ImageView(activity).apply {
            setImageDrawable(res)
            scaleType = ImageView.ScaleType.CENTER_CROP
            isClickable = false
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            if (theme == AppTheme.BLUR && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRenderEffect(
                    RenderEffect.createBlurEffect(BLUR_RADIUS, BLUR_RADIUS, Shader.TileMode.CLAMP)
                )
            }
        }
        root.addView(backdrop, 0)
    }

    /** 取当前主题的 backdropDrawable；没配（白/黑主题）就返回 null。 */
    private fun themeAttrDrawable(activity: Activity): android.graphics.drawable.Drawable? {
        val attrs = intArrayOf(com.gongkao.checkin.R.attr.backdropDrawable)
        val ta = activity.theme.obtainStyledAttributes(attrs)
        return try {
            ta.getDrawable(0)
        } finally {
            ta.recycle()
        }
    }

    /**
     * 给某个 view 加实时背景模糊（Android 12+）。用于长按菜单这类浮层。
     * 低版本静默跳过——调用方的半透明底色本身就是降级方案。
     */
    fun blurBehind(view: View, radius: Float = 26f) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        view.setRenderEffect(
            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        )
    }
}
