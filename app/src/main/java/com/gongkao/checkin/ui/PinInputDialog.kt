package com.gongkao.checkin.ui

import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.gongkao.checkin.R

/**
 * 带一个数字输入框的确认弹层，用于输入对方设备的访问码。
 * 布局复用 [AppDialog] 同一套动效骨架，只是多了一个 EditText。
 */
object PinInputDialog {

    fun show(
        ctx: Context,
        title: CharSequence,
        message: CharSequence? = null,
        positive: CharSequence,
        negative: CharSequence? = null,
        onPositive: (String) -> Unit
    ) {
        val v = ctx.inflate(R.layout.dialog_pin_input, null)
        val scrim = v.findViewById<View>(R.id.dialogScrim)
        val card = v.findViewById<View>(R.id.dialogCard)
        val titleView = v.findViewById<TextView>(R.id.dialogTitle)
        val messageView = v.findViewById<TextView>(R.id.dialogMessage)
        val input = v.findViewById<EditText>(R.id.dialogInput)
        val btnPositive = v.findViewById<TextView>(R.id.dialogPositive)
        val btnNegative = v.findViewById<TextView>(R.id.dialogNegative)

        titleView.text = title
        messageView.show(!message.isNullOrBlank())
        messageView.text = message ?: ""

        btnPositive.text = positive
        btnNegative.show(!negative.isNullOrBlank())
        btnNegative.text = negative ?: ""

        val d = Popup.dialog(ctx, v)
        Popup.wireDismiss(d, scrim, card)

        btnPositive.tap {
            val pin = input.text?.toString().orEmpty()
            Popup.close(d)
            onPositive(pin)
        }
        btnNegative.tap { Popup.close(d) }

        d.show()
        Popup.enter(scrim, card)
        input.requestFocus()
    }
}
