package com.gongkao.checkin.ui

import android.content.Context
import android.view.View

/**
 * 一个底部标签对应的页面。不用 Fragment：四个页面视图常驻 container，
 * 切换只做可见性 + 动效，滚动位置和计时状态都能原样保留。
 */
abstract class Page(val host: MainActivity) {

    val ctx: Context get() = host

    abstract val layoutRes: Int

    /** view 是否已经被创建过（避免为了刷新而提前 inflate 未访问的页面）。 */
    var created = false
        private set

    val view: View by lazy {
        host.layoutInflater.inflate(layoutRes, null, false).also {
            created = true
            onCreate(it)
        }
    }

    /** 视图第一次创建时绑定控件。 */
    protected open fun onCreate(v: View) {}

    /** 数据变化或页面显示时刷新内容。 */
    open fun refresh() {}

    open fun onShow() { refresh() }

    open fun onHide() {}

    open fun onDestroy() {}
}
