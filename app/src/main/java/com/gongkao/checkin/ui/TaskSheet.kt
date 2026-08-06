package com.gongkao.checkin.ui

import android.app.DatePickerDialog
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.gongkao.checkin.R
import com.gongkao.checkin.anim.Motion
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.REPEAT_DAILY
import com.gongkao.checkin.data.REPEAT_UNTIL
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.data.TaskDef
import java.time.LocalDate

/** 新建 / 编辑任务的底部面板。existing 为 null 表示新建。 */
object TaskSheet {

    private const val COLOR_COUNT = 6

    fun show(host: MainActivity, existing: TaskDef?) {
        val dialog = BottomSheetDialog(host)
        val v = host.inflate(R.layout.sheet_task, null)
        dialog.setContentView(v)
        bindForm(host, v, existing) { dialog.dismiss() }
        dialog.show()
    }

    /**
     * 把表单绑定抽出来，底部面板（[show]）和长按弹出的锚定卡（[AnchoredCard]）共用同一套逻辑。
     * [onDone] 由调用方决定怎么关闭自己的容器。
     */
    fun bindForm(host: MainActivity, v: View, existing: TaskDef?, onDone: () -> Unit) {
        val inputTitle = v.findViewById<EditText>(R.id.inputTitle)
        val inputTarget = v.findViewById<EditText>(R.id.inputTarget)
        val inputUnit = v.findViewById<EditText>(R.id.inputUnit)
        val chipDaily = v.findViewById<TextView>(R.id.chipDaily)
        val chipUntil = v.findViewById<TextView>(R.id.chipUntil)
        val btnUntilDate = v.findViewById<TextView>(R.id.btnUntilDate)
        val colorRow = v.findViewById<LinearLayout>(R.id.colorRow)
        val btnDelete = v.findViewById<TextView>(R.id.btnDelete)
        val btnSave = v.findViewById<TextView>(R.id.btnSave)

        v.findViewById<TextView>(R.id.sheetTitle)
            .setText(if (existing == null) R.string.add_task else R.string.edit_task)

        // 编辑时回填原值，新建时给一份可以直接保存的默认值
        var mode = existing?.repeat ?: REPEAT_DAILY
        var until = existing?.untilDate
        var colorIndex = existing?.colorIndex ?: (Repo.read { it.tasks.size } % COLOR_COUNT)

        inputTitle.setText(existing?.title ?: "")
        inputTarget.setText((existing?.target ?: 1).toString())
        inputUnit.setText(existing?.unit ?: "")

        fun paintMode() {
            chipDaily.isSelected = mode == REPEAT_DAILY
            chipUntil.isSelected = mode == REPEAT_UNTIL
            btnUntilDate.show(mode == REPEAT_UNTIL)
            btnUntilDate.text = until?.let { DateUtil.prettyStr(it) }
                ?: host.getString(R.string.pick_until_date)
        }

        val swatches = ArrayList<View>(COLOR_COUNT)
        fun paintColors() {
            swatches.forEachIndexed { i, sw ->
                val on = i == colorIndex
                Motion.springTo(sw, DynamicAnimation.SCALE_X, if (on) 1f else 0.72f)
                Motion.springTo(sw, DynamicAnimation.SCALE_Y, if (on) 1f else 0.72f)
                sw.alpha = if (on) 1f else 0.45f
            }
        }
        for (i in 0 until COLOR_COUNT) {
            val sw = View(host).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Ui.taskColor(host, i))
                }
                layoutParams = LinearLayout.LayoutParams(34.dp, 34.dp).apply { marginEnd = 12.dp }
            }
            sw.tap { colorIndex = i; paintColors() }
            colorRow.addView(sw)
            swatches.add(sw)
        }
        paintColors()

        chipDaily.tap { mode = REPEAT_DAILY; paintMode() }
        chipUntil.tap {
            mode = REPEAT_UNTIL
            paintMode()
            if (until == null) pickDate(host, until) { until = it; paintMode() }
        }
        btnUntilDate.tap { pickDate(host, until) { until = it; paintMode() } }
        paintMode()

        btnDelete.show(existing != null)
        btnDelete.tap {
            AppDialog.show(
                ctx = host,
                title = host.getString(R.string.delete),
                message = existing!!.title,
                positive = host.getString(R.string.delete),
                negative = host.getString(R.string.cancel),
                destructive = true
            ) {
                Repo.deleteTask(existing.id)
                onDone()
            }
        }

        btnSave.tap {
            val title = inputTitle.text.toString().trim()
            if (title.isEmpty()) {
                Motion.reject(inputTitle)
                return@tap
            }
            if (mode == REPEAT_UNTIL && until == null) {
                Motion.reject(btnUntilDate)
                host.toast(host.getString(R.string.pick_until_date))
                return@tap
            }
            val target = inputTarget.text.toString().toIntOrNull()?.coerceIn(1, 999) ?: 1
            val base = existing ?: TaskDef(startDate = DateUtil.todayStr())
            Repo.upsertTask(
                base.copy(
                    title = title,
                    repeat = mode,
                    untilDate = if (mode == REPEAT_UNTIL) until else null,
                    target = target,
                    unit = inputUnit.text.toString().trim(),
                    colorIndex = colorIndex
                )
            )
            onDone()
        }
    }

    private fun pickDate(host: MainActivity, current: String?, onPick: (String) -> Unit) {
        DatePicker.show(
            ctx = host,
            current = current ?: DateUtil.today().plusDays(7).toString(),
            title = host.getString(R.string.pick_until_date),
            onPick = onPick
        )
    }
}
