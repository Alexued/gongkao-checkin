package com.gongkao.checkin.ui

import android.content.Context
import android.view.View
import android.widget.TextView
import com.gongkao.checkin.R
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.view.CalendarView
import java.time.LocalDate
import java.time.YearMonth

/**
 * 应用内日期选择器，替换系统 DatePickerDialog。
 * 复用 [CalendarView] 的 PICKER 模式，所以和统计页的热力图长得是一家人。
 */
object DatePicker {

    /**
     * @param current   当前值（yyyy-MM-dd），null 表示未设置
     * @param title     弹层标题
     * @param allowClear 是否给「清除」按钮
     * @param onPick    选中并确认后回调
     * @param onClear   点清除时回调
     */
    fun show(
        ctx: Context,
        current: String?,
        title: CharSequence = ctx.getString(R.string.pick_date),
        allowClear: Boolean = false,
        onPick: (String) -> Unit,
        onClear: (() -> Unit)? = null
    ) {
        val v = ctx.inflate(R.layout.dialog_date, null)
        val scrim = v.findViewById<View>(R.id.dialogScrim)
        val card = v.findViewById<View>(R.id.dialogCard)
        val calendar = v.findViewById<CalendarView>(R.id.datePicker)
        val pickedLabel = v.findViewById<TextView>(R.id.dialogPicked)
        val btnOk = v.findViewById<TextView>(R.id.dialogPositive)
        val btnClear = v.findViewById<TextView>(R.id.dialogClear)
        val btnCancel = v.findViewById<TextView>(R.id.dialogNegative)

        v.findViewById<TextView>(R.id.dialogTitle).text = title

        val start = DateUtil.parse(current) ?: LocalDate.now().plusMonths(3)
        var chosen: String = current ?: start.toString()

        calendar.mode = CalendarView.Mode.PICKER
        calendar.emptyColor = ctx.getColor(R.color.surface_alt)
        calendar.accent = ctx.getColor(R.color.accent)
        calendar.inkColor = ctx.getColor(R.color.ink)
        calendar.dimColor = ctx.getColor(R.color.ink_dim)
        calendar.showMonth(YearMonth.from(start))
        calendar.setData(emptyMap(), animated = false)
        calendar.select(chosen)

        fun paintPicked() {
            pickedLabel.setTextAnimated(DateUtil.prettyStr(chosen))
        }
        pickedLabel.text = DateUtil.prettyStr(chosen)

        calendar.onPick = { date ->
            chosen = date
            paintPicked()
        }

        btnOk.text = ctx.getString(R.string.key_confirm)

        val d = Popup.dialog(ctx, v)
        Popup.wireDismiss(d, scrim, card)

        btnOk.tap {
            Popup.close(d)
            onPick(chosen)
        }
        btnClear.show(allowClear)
        btnClear.tap {
            Popup.close(d)
            onClear?.invoke()
        }
        btnCancel.tap { Popup.close(d) }

        d.show()
        Popup.enter(scrim, card)
    }
}
