package com.gongkao.checkin.ui

import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.gongkao.checkin.R
import com.gongkao.checkin.anim.Motion
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.data.TaskDef

/**
 * 长按任务卡弹出的操作菜单。**盖在被长按那一行上面**、宽度与该行一致、
 * 从行中心长出来——看上去是这张卡自己翻开成了菜单，而不是旁边又冒出一个东西。
 */
object TaskMenu {

    private const val EDGE = 12
    private const val BOTTOM_CLEARANCE = 86

    fun show(
        host: MainActivity,
        task: TaskDef,
        anchor: View,
        onReorder: () -> Unit
    ) {
        val v = host.inflate(R.layout.card_task_menu, null)
        val scrim = v.findViewById<View>(R.id.menuScrim)
        val card = v.findViewById<LinearLayout>(R.id.menuCard)
        v.findViewById<TextView>(R.id.menuTitle).text = task.title
        // 色条跟任务本身同色，接得上那一行
        v.findViewById<View>(R.id.menuColorBar)
            .setBackgroundColor(Ui.taskColor(host, task.colorIndex))

        val d = Popup.dialog(host, v)
        // anchor 传 null：菜单已经盖在行上，缩放原点取卡片中心正好等于行中心
        Popup.wireDismiss(d, scrim, card)

        card.findViewById<TextView>(R.id.menuEdit).tap {
            Popup.close(d)
            AnchoredCard.showTaskEditor(host, task, anchor)
        }
        card.findViewById<TextView>(R.id.menuFocus).tap {
            Popup.close(d)
            // 计时页是常驻页，切过去就行，不用另开 Activity
            host.select(1)
        }
        card.findViewById<TextView>(R.id.menuReorder).tap {
            Popup.close(d)
            onReorder()
        }
        card.findViewById<TextView>(R.id.menuDelete).tap {
            Popup.close(d)
            AppDialog.show(
                ctx = host,
                title = host.getString(R.string.menu_delete),
                message = host.getString(R.string.menu_delete_confirm, task.title),
                positive = host.getString(R.string.menu_delete),
                negative = host.getString(R.string.cancel),
                destructive = true
            ) { Repo.deleteTask(task.id) }
        }

        // 宽度要跟行一样，得等行量好；菜单高度也要等自己量完才知道能不能居中盖住
        card.post { place(card, anchor) }
        Popup.enter(scrim, card)
    }

    /**
     * 让菜单盖住被长按的那一行：宽度取行宽、横向与行对齐，
     * 纵向以行中心为中心展开（放不下时才上下夹回安全区）。
     */
    private fun place(card: View, anchor: View) {
        val parent = card.parent as? FrameLayout ?: return
        val a = IntArray(2)
        anchor.getLocationInWindow(a)

        // 宽度对齐行，菜单就不像"另一个控件"
        if (anchor.width > 0 && card.width != anchor.width) {
            card.layoutParams = card.layoutParams.apply { width = anchor.width }
            card.requestLayout()
            // 改了宽度要重新量一轮再定位，否则拿到的还是旧高度
            card.post { place(card, anchor) }
            return
        }

        val rowCenterY = a[1] + anchor.height / 2f
        val maxX = (parent.width - card.width - EDGE.dp).coerceAtLeast(EDGE.dp)
        val maxY = (parent.height - card.height - BOTTOM_CLEARANCE.dp).coerceAtLeast(EDGE.dp)

        card.x = a[0].toFloat().coerceIn(EDGE.dp.toFloat(), maxX.toFloat())
        card.y = (rowCenterY - card.height / 2f)
            .coerceIn(EDGE.dp.toFloat(), maxY.toFloat())
        // 卡片已经以行为中心，pivot 交给 Popup 取卡片中心即可（= 行中心）
    }
}
