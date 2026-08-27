package com.gongkao.checkin.ui

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatDelegate
import com.gongkao.checkin.data.AppTheme
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.view.BackdropView

/**
 * 主题的应用入口。每个 Activity 在 setContentView **之前** 调 [apply]。
 *
 * 分两步：先按 [AppTheme.night] 定夜间模式（决定用 values 还是 values-night 的调色板），
 * 再套上该主题的 style（决定卡片质感那几个 ?attr）。
 */
object Themes {

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
     * 用自绘的 [BackdropView] 而不是静态 drawable：光团要慢慢流动。
     * 它自己会在窗口不可见时停掉重绘循环。
     */
    fun installBackdrop(activity: Activity) {
        val theme = Repo.appTheme()
        if (!theme.hasBackdrop) return

        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        // 页面根布局大多写死了 android:background="@color/bg"，不透明，会把底层背景整块盖住。
        // 玻璃主题下把它清掉，彩色底才透得上来。
        for (i in 0 until root.childCount) {
            root.getChildAt(i).background = null
        }

        val backdrop = BackdropView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            if (theme == AppTheme.BLUR) useBlurPreset() else useLiquidPreset()
        }
        root.addView(backdrop, 0)
    }
}
