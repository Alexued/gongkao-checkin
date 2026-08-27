package com.gongkao.checkin.ui

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import com.gongkao.checkin.data.AppTheme
import com.gongkao.checkin.data.Repo

/**
 * 主题的应用入口。每个 Activity 在 setContentView **之前** 调 [apply]。
 *
 * 分两步：先按 [AppTheme.night] 定夜间模式（决定用 values 还是 values-night 的调色板），
 * 再套上该主题的 style（决定页面底色和卡片质感那几个 ?attr）。
 *
 * 页面底色不在这里管：根布局引用 `?attr/pageFill`，套上 style 就跟着变了，
 * 不需要运行时插背景层。
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
}
