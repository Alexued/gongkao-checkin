package com.gongkao.checkin.ui

import android.content.Context
import android.view.View
import android.widget.TextView
import com.gongkao.checkin.R

/**
 * 应用内确认弹层，替换所有 android.app.AlertDialog。
 *
 * 样式沿用 app 的卡片与按钮语汇，动效走 [Popup]（缩放展开 / 缩回），
 * 不再出现系统原生弹窗那种风格断层。
 */
object AppDialog {

    /**
     * @param title    标题，必填
     * @param message  说明，留空则不占位
     * @param positive 主按钮文案
     * @param negative 次按钮文案，留空则只有一个按钮
     * @param destructive 主按钮是否用玫红（删除 / 放弃这类不可撤销操作）
     * @param anchor   给了就从该视图位置展开并缩回
     * @param onPositive 主按钮回调；点完自动关闭
     */
    fun show(
        ctx: Context,
        title: CharSequence,
        message: CharSequence? = null,
        positive: CharSequence,
        negative: CharSequence? = null,
        destructive: Boolean = false,
        anchor: View? = null,
        onPositive: () -> Unit
    ) {
        val v = ctx.inflate(R.layout.dialog_app, null)
        val scrim = v.findViewById<View>(R.id.dialogScrim)
        val card = v.findViewById<View>(R.id.dialogCard)
        val titleView = v.findViewById<TextView>(R.id.dialogTitle)
        val messageView = v.findViewById<TextView>(R.id.dialogMessage)
        val btnPositive = v.findViewById<TextView>(R.id.dialogPositive)
        val btnNegative = v.findViewById<TextView>(R.id.dialogNegative)

        titleView.text = title
        messageView.show(!message.isNullOrBlank())
        messageView.text = message ?: ""

        btnPositive.text = positive
        if (destructive) btnPositive.setBackgroundResource(R.drawable.bg_btn_danger)

        btnNegative.show(!negative.isNullOrBlank())
        btnNegative.text = negative ?: ""

        val d = Popup.dialog(ctx, v)
        Popup.wireDismiss(d, scrim, card, anchor)

        btnPositive.tap {
            Popup.close(d)
            onPositive()
        }
        btnNegative.tap { Popup.close(d) }

        d.show()
        Popup.enter(scrim, card, anchor)
    }

    /** 只有一个「知道了」按钮的告知型弹层。 */
    fun notice(
        ctx: Context,
        title: CharSequence,
        message: CharSequence? = null,
        onClose: () -> Unit = {}
    ) = show(
        ctx = ctx,
        title = title,
        message = message,
        positive = ctx.getString(R.string.got_it),
        onPositive = onClose
    )
}
