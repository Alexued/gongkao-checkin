package com.gongkao.checkin.ui

import android.view.View
import android.widget.LinearLayout
import com.gongkao.checkin.R
import com.gongkao.checkin.data.TaskDef

/**
 * 从某个视图位置展开、并缩回同一位置的编辑卡（长按任务用）。
 *
 * 和底部面板 [TaskSheet] 的区别只在容器：表单内容和逻辑完全共用，
 * 所以长按改任务和「管理任务」进去改，看到的是同一个编辑器。
 */
object AnchoredCard {

    /** 长按任务行时弹出的任务编辑卡。[anchor] 是被长按的那一行。 */
    fun showTaskEditor(host: MainActivity, existing: TaskDef?, anchor: View) {
        val v = host.inflate(R.layout.card_anchored, null)
        val scrim = v.findViewById<View>(R.id.dialogScrim)
        val card = v.findViewById<View>(R.id.dialogCard)
        val body = v.findViewById<LinearLayout>(R.id.cardBody)

        // 复用底部面板的表单布局，去掉它自带的背景和内边距（外层卡片已经有了）
        val form = host.inflate(R.layout.sheet_task, body)
        form.background = null
        form.setPadding(0, 0, 0, 0)
        body.addView(form)

        val d = Popup.dialog(host, v)
        Popup.wireDismiss(d, scrim, card, anchor)

        TaskSheet.bindForm(host, form, existing) { Popup.close(d) }

        d.show()
        Popup.enter(scrim, card, anchor)
    }
}
