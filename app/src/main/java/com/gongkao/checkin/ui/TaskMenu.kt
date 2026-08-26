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
 * 长按任务卡弹出的操作菜单。菜单从手指位置展开，靠边时朝反方向翻转，
 * 免得贴着屏幕边缘只露出一半。
 */
object TaskMenu {

    private const val EDGE = 12
    private const val GAP = 6
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

        val d = Popup.dialog(host, v)
        Popup.wireDismiss(d, scrim, card, anchor)

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

        // 量完才知道卡片多大，才能决定往上还是往下开
        card.post { place(card, anchor) }
        Popup.enter(scrim, card, anchor)
    }

    /**
     * 把卡片贴在被长按那一行的下方；下面放不下就翻到行的上方，
     * 最后夹进屏幕安全区。水平方向跟行左对齐。
     */
    private fun place(card: View, anchor: View) {
        val parent = card.parent as? FrameLayout ?: return
        val a = IntArray(2)
        anchor.getLocationInWindow(a)

        val maxX = (parent.width - card.width - EDGE.dp).coerceAtLeast(EDGE.dp)
        val maxY = (parent.height - card.height - BOTTOM_CLEARANCE.dp).coerceAtLeast(EDGE.dp)

        val belowY = a[1] + anchor.height + GAP.dp
        val y = if (belowY > maxY) a[1] - card.height - GAP.dp else belowY

        card.x = a[0].toFloat().coerceIn(EDGE.dp.toFloat(), maxX.toFloat())
        card.y = y.toFloat().coerceIn(EDGE.dp.toFloat(), maxY.toFloat())
    }
}
