package com.gongkao.checkin.data

import androidx.annotation.StyleRes
import com.gongkao.checkin.R

/**
 * 界面主题。四套：白 / 黑 / 高斯模糊 / 液态玻璃。
 *
 * 实现上分两层：
 * - **调色板**靠 `values` 与 `values-night`（[night] 决定用哪套），
 *   这样全项目 300 多处 `@color/...` 引用一个都不用改。
 * - **卡片质感**靠主题属性（`?attr/cardFill` 等）+ 各主题的 style 覆盖，
 *   shape drawable 里能直接引用 `?attr/`，所以只改几个 drawable 就够。
 */
enum class AppTheme(
    val id: String,
    /** 用暗色调色板（values-night） */
    val night: Boolean,
    @StyleRes val style: Int
) {
    LIGHT("light", false, R.style.Theme_Checkin),
    DARK("dark", true, R.style.Theme_Checkin_Dark),

    /** 白面板 + 背后一层极淡的冷色呼吸光团（团大而糊） */
    BLUR("blur", false, R.style.Theme_Checkin_Blur),

    /** 白面板 + 背后一层极淡的暖色呼吸光团（团聚拢、隐约有边） */
    LIQUID("liquid", false, R.style.Theme_Checkin_Liquid);

    /** 这套主题要不要在页面底层铺一张彩色背景（模糊/液态玻璃靠它出效果） */
    val hasBackdrop: Boolean get() = this == BLUR || this == LIQUID

    /** 给用户看的名字 */
    val nameRes: Int
        get() = when (this) {
            LIGHT -> R.string.theme_light
            DARK -> R.string.theme_dark
            BLUR -> R.string.theme_blur
            LIQUID -> R.string.theme_liquid
        }

    companion object {
        fun of(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: LIGHT

        /** 按钮循环切换的顺序 */
        fun next(current: AppTheme): AppTheme =
            entries[(entries.indexOf(current) + 1) % entries.size]
    }
}
