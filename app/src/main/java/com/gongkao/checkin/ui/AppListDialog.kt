package com.gongkao.checkin.ui

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gongkao.checkin.R
import com.gongkao.checkin.anim.Motion

/** 列表弹层的一项。[color] 为 null 时不显示左侧色条。 */
data class DialogRow(
    val title: CharSequence,
    val sub: CharSequence? = null,
    val color: Int? = null
)

/**
 * 「选一项」型弹层，替换 AlertDialog.setItems。
 * 行样式跟 app 内列表一致，逐行错峰入场。
 */
object AppListDialog {

    fun show(
        ctx: Context,
        title: CharSequence,
        rows: List<DialogRow>,
        positive: CharSequence? = null,
        negative: CharSequence? = null,
        onPick: (Int) -> Unit,
        onPositive: (() -> Unit)? = null
    ) {
        val v = ctx.inflate(R.layout.dialog_list, null)
        val scrim = v.findViewById<View>(R.id.dialogScrim)
        val card = v.findViewById<View>(R.id.dialogCard)
        val rowBox = v.findViewById<LinearLayout>(R.id.dialogRows)
        val btnPositive = v.findViewById<TextView>(R.id.dialogPositive)
        val btnNegative = v.findViewById<TextView>(R.id.dialogNegative)

        v.findViewById<TextView>(R.id.dialogTitle).text = title

        val d = Popup.dialog(ctx, v)
        Popup.wireDismiss(d, scrim, card)

        rows.forEachIndexed { index, row ->
            val item = rowBox.inflateChild(R.layout.item_dialog_row)
            item.findViewById<TextView>(R.id.rowTitle).text = row.title
            item.findViewById<TextView>(R.id.rowSub).apply {
                show(!row.sub.isNullOrBlank())
                text = row.sub ?: ""
            }
            item.findViewById<View>(R.id.rowColor).apply {
                if (row.color == null) {
                    show(false)
                } else {
                    setBackgroundColor(row.color)
                }
            }
            item.tap {
                Popup.close(d)
                onPick(index)
            }
            rowBox.addView(item)
            Motion.stagger(item, index)
        }

        btnPositive.show(!positive.isNullOrBlank())
        btnPositive.text = positive ?: ""
        btnPositive.tap {
            Popup.close(d)
            onPositive?.invoke()
        }

        btnNegative.show(!negative.isNullOrBlank())
        btnNegative.text = negative ?: ""
        btnNegative.tap { Popup.close(d) }

        d.show()
        Popup.enter(scrim, card)
    }
}
