package com.gongkao.checkin.ui

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.gongkao.checkin.R
import com.gongkao.checkin.anim.Motion

/**
 * 应用内弹层的公共骨架：全屏透明窗口 + 自绘遮罩 + 卡片缩放进出场。
 *
 * 抽出来是因为确认框（[AppDialog]）、列表框（[AppListDialog]）和长按编辑卡
 * （[AnchoredCard]）要共用同一套动效，不然「风格割裂」会换个地方再出现一次。
 */
object Popup {

    /** 遮罩淡入时长。卡片比遮罩稍慢，视觉上是卡片"追"上来。 */
    private const val SCRIM_IN = 180L
    private const val CARD_IN = 340L
    private const val OUT = 200L

    /** 默认遮罩浓度。背后有真实模糊时用 [SCRIM_GLASS]，否则模糊会被压死看不出来。 */
    const val SCRIM_SOLID = 0.42f
    const val SCRIM_GLASS = 0.2f

    /**
     * 窗口背后模糊半径，**单位是像素**（不要再乘 density —— 按 dp 折算成 88px
     * 会把整页糊成一片色块，连卡片轮廓都没了，反而看不出是"背后有东西"）。
     * 取值跟页面 backdrop 的 46px 一个量级。
     */
    private const val WINDOW_BLUR = 40f

    /**
     * 建一个铺满屏幕、背景全透明、不带系统动画的 Dialog。
     *
     * 不要在这里调 requestFeature —— 它必须早于 setContentView，
     * 无标题已经由 Theme.Checkin.Dialog 的 windowNoTitle 保证。
     */
    fun dialog(ctx: Context, content: View): Dialog {
        val d = Dialog(ctx, R.style.Theme_Checkin_Dialog)
        d.setContentView(content)
        d.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            setDimAmount(0f)
            // 让遮罩画到系统栏下面，不然状态栏那条会是黑的
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(this, false)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }
        return d
    }

    /**
     * 给弹层窗口开启「背后模糊」，返回是否真的生效。
     *
     * 用窗口级的 [WindowManager.LayoutParams.setBlurBehindRadius] 而不是给 view 设
     * `RenderEffect` —— 后者模糊的是 view 自己（连里面的文字一起糊掉），
     * 而我们要糊的是浮层背后的页面。
     *
     * 返回 false 的情况（低版本、省电模式、开发者选项关掉了窗口模糊）由调用方
     * 用更浓的遮罩兜底，不然浮层会飘在完全清晰的页面上。
     */
    fun blurBehind(d: Dialog, radius: Float = WINDOW_BLUR): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val w = d.window ?: return false
        val wm = w.windowManager ?: return false
        if (!wm.isCrossWindowBlurEnabled) return false
        w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        w.attributes = w.attributes.apply { blurBehindRadius = radius.toInt() }
        return true
    }

    /**
     * 进场。[anchor] 给了就从它的屏幕位置展开（"从哪来"），
     * 没给就从卡片自身中心展开。
     */
    fun enter(scrim: View, card: View, anchor: View? = null, scrimAlpha: Float = SCRIM_SOLID) {
        scrim.setBackgroundColor(transparentBlack(0f))
        Motion.animate(SCRIM_IN, Motion.EXIT) { f ->
            scrim.setBackgroundColor(transparentBlack(scrimAlpha * f))
        }

        card.alpha = 0f
        card.scaleX = 0.86f
        card.scaleY = 0.86f
        // 等卡片量完才知道自己的位置，才能把 pivot 换算到锚点上
        card.post {
            applyPivot(card, anchor)
            card.animate().cancel()
            card.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(CARD_IN).setInterpolator(Motion.EMPHASIZED).start()
        }
    }

    /** 退场：缩回锚点（"就从哪回"），动画结束才真正 dismiss。 */
    fun exit(scrim: View, card: View, anchor: View? = null, onEnd: () -> Unit) {
        applyPivot(card, anchor)
        // 从遮罩当前浓度淡出，而不是写死的峰值：各弹层浓度不同（玻璃浮层更淡），
        // 而且中途打断进场时也能接着当前状态往回走，不会先跳一下
        val from = ((scrim.background as? ColorDrawable)?.color?.ushr(24) ?: 0) / 255f
        Motion.animate(OUT, Motion.EXIT) { f ->
            scrim.setBackgroundColor(transparentBlack(from * (1f - f)))
        }
        card.animate().cancel()
        card.animate().alpha(0f).scaleX(0.86f).scaleY(0.86f)
            .setDuration(OUT).setInterpolator(Motion.EXIT)
            .withEndAction(onEnd)
            .start()
    }

    /**
     * 把缩放原点挪到锚点中心。锚点和卡片都取窗口坐标，
     * 差值就是 pivot 在卡片内的偏移（允许落在卡片外，缩放依然朝那个方向）。
     */
    private fun applyPivot(card: View, anchor: View?) {
        if (anchor == null) {
            card.pivotX = card.width / 2f
            card.pivotY = card.height / 2f
            return
        }
        val a = IntArray(2)
        val c = IntArray(2)
        anchor.getLocationInWindow(a)
        card.getLocationInWindow(c)
        card.pivotX = (a[0] + anchor.width / 2f) - c[0]
        card.pivotY = (a[1] + anchor.height / 2f) - c[1]
    }

    /**
     * 遮罩点击 + 返回键都走同一条退场动画。
     *
     * [onCancel] 只在「用户主动放弃」时回调（点遮罩 / 按返回），
     * 通过 [close] 程序化关闭（比如保存成功后）不会触发。
     * 二级弹层靠这个区分：返回要回到上一级弹窗，保存则整条链一起收掉。
     */
    fun wireDismiss(
        d: Dialog,
        scrim: View,
        card: View,
        anchor: View? = null,
        onCancel: (() -> Unit)? = null
    ) {
        var closing = false
        fun close(cancelled: Boolean) {
            if (closing) return
            closing = true
            exit(scrim, card, anchor) {
                runCatching { d.dismiss() }
                if (cancelled) onCancel?.invoke()
            }
        }
        scrim.setOnClickListener { close(true) }
        // 卡片自身吃掉点击，避免点在卡片上被当成点遮罩
        card.isClickable = true
        d.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK &&
                event.action == android.view.KeyEvent.ACTION_UP
            ) {
                close(true); true
            } else false
        }
        // 自己接管返回键和遮罩点击，交给系统会跳过退场动画
        d.setCancelable(false)
        closeRefs[d] = { close(false) }
        d.setOnDismissListener { closeRefs.remove(d) }
    }

    /** 供调用方在自己的按钮里主动关闭（不算「放弃」，不触发 onCancel）。 */
    fun close(d: Dialog) {
        closeRefs[d]?.invoke() ?: runCatching { d.dismiss() }
    }

    private val closeRefs = HashMap<Dialog, () -> Unit>()
}
