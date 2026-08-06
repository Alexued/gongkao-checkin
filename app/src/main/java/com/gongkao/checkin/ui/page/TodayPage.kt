package com.gongkao.checkin.ui.page

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gongkao.checkin.R
import com.gongkao.checkin.anim.Motion
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.DayItem
import com.gongkao.checkin.data.KIND_CARRY
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.ui.AnchoredCard
import com.gongkao.checkin.ui.AppListDialog
import com.gongkao.checkin.ui.DialogRow
import com.gongkao.checkin.ui.MainActivity
import com.gongkao.checkin.ui.toast
import com.gongkao.checkin.ui.Page
import com.gongkao.checkin.ui.SettingsActivity
import com.gongkao.checkin.ui.TaskSheet
import com.gongkao.checkin.ui.Ui
import com.gongkao.checkin.ui.dp
import com.gongkao.checkin.ui.inflateChild
import com.gongkao.checkin.ui.padTopInset
import com.gongkao.checkin.ui.show
import com.gongkao.checkin.ui.tap
import com.gongkao.checkin.view.CheckCircleView
import com.gongkao.checkin.view.RingProgressView

/** 今日打卡。欠账条目排在前面并带橙色标签，最后一项完成时触发全屏庆祝。 */
class TodayPage(host: MainActivity) : Page(host) {

    override val layoutRes = R.layout.fragment_today

    private lateinit var dateText: TextView
    private lateinit var countdownText: TextView
    private lateinit var ring: RingProgressView
    private lateinit var ringText: TextView
    private lateinit var progressText: TextView
    private lateinit var carryText: TextView
    private lateinit var streakText: TextView
    private lateinit var taskList: LinearLayout
    private lateinit var emptyText: TextView

    /** 上一次渲染时每条的完成状态：只有真正翻转过的条目才播打卡动效。 */
    private val doneState = HashMap<String, Boolean>()
    private var firstBind = true

    override fun onCreate(v: View) {
        dateText = v.findViewById(R.id.dateText)
        countdownText = v.findViewById(R.id.countdownText)
        ring = v.findViewById(R.id.ring)
        ringText = v.findViewById(R.id.ringText)
        progressText = v.findViewById(R.id.progressText)
        carryText = v.findViewById(R.id.carryText)
        streakText = v.findViewById(R.id.streakText)
        taskList = v.findViewById(R.id.taskList)
        emptyText = v.findViewById(R.id.emptyText)

        // 顶部渐变头是 taskList 的兄弟，直接给它补状态栏高度
        (taskList.parent as ViewGroup).getChildAt(0).padTopInset()

        ring.trackColor = 0x33FFFFFF
        ring.startColor = 0xFFFFFFFF.toInt()
        ring.endColor = ctx.getColor(R.color.teal)

        v.findViewById<ImageView>(R.id.btnSettings).tap {
            ctx.startActivity(android.content.Intent(ctx, SettingsActivity::class.java))
        }
        v.findViewById<TextView>(R.id.btnAddTask).tap { TaskSheet.show(host, null) }
        v.findViewById<TextView>(R.id.btnManageTasks).tap { showManage() }
    }

    override fun refresh() {
        if (!created) return
        val rec = Repo.today()
        val items = Repo.sortedItems(rec)

        dateText.text = DateUtil.pretty(DateUtil.today())
        val left = Repo.daysLeft()
        countdownText.text = when {
            left == null -> ctx.getString(R.string.countdown_none)
            left > 0 -> ctx.getString(R.string.countdown_left, left)
            left == 0L -> ctx.getString(R.string.countdown_today)
            else -> ctx.getString(R.string.countdown_past)
        }

        val ratio = rec.ratio()
        ring.setProgress(ratio, animated = !firstBind)
        ringText.text = "${(ratio * 100).toInt()}%"
        progressText.text = ctx.getString(R.string.today_progress, rec.finished(), rec.total())

        val debt = items.filter { it.kind == KIND_CARRY }.sumOf { it.remaining }
        carryText.show(debt > 0)
        if (debt > 0) carryText.text = ctx.getString(R.string.today_debt, debt)

        val s = Repo.streak()
        streakText.show(s > 0)
        if (s > 0) streakText.text = ctx.getString(R.string.today_streak, s)

        emptyText.show(items.isEmpty())
        bindList(items)
        firstBind = false
    }

    private fun bindList(items: List<DayItem>) {
        // 条目数量不变时原地更新，保留打卡动效的视图状态
        if (taskList.childCount != items.size) taskList.removeAllViews()
        items.forEachIndexed { index, item ->
            val row = taskList.getChildAt(index) ?: taskList.inflateChild(R.layout.item_task)
                .also { taskList.addView(it) }
            bindRow(row, item, index)
        }
    }

    private fun bindRow(row: View, item: DayItem, index: Int) {
        val carry = item.kind == KIND_CARRY
        val color = Ui.taskColor(ctx, item.colorIndex)

        row.findViewById<View>(R.id.colorBar).setBackgroundColor(
            if (carry) ctx.getColor(R.color.carry_ink) else color
        )
        row.findViewById<TextView>(R.id.title).apply {
            text = item.title
            alpha = if (item.done) 0.45f else 1f
        }

        val carryTag = row.findViewById<TextView>(R.id.carryTag)
        carryTag.show(carry)
        if (carry) {
            val days = item.oldestDebtDate?.let {
                DateUtil.parse(it)?.let { d -> DateUtil.daysBetween(d, DateUtil.today()) }
            } ?: 1L
            carryTag.text = ctx.getString(R.string.tag_carry, days.coerceAtLeast(1))
        }

        row.findViewById<TextView>(R.id.sub).text = buildString {
            if (item.target > 1) {
                append(item.progress).append('/').append(item.target)
                if (item.unit.isNotBlank()) append(' ').append(item.unit)
            } else if (item.unit.isNotBlank()) {
                append(item.unit)
            }
            if (item.orphan) {
                if (isNotEmpty()) append(" · ")
                append(ctx.getString(R.string.tag_orphan))
            }
            if (item.done && item.doneAt > 0) {
                if (isNotEmpty()) append(" · ")
                append(ctx.getString(R.string.tag_done_at, DateUtil.clock(item.doneAt)))
            }
        }

        val check = row.findViewById<CheckCircleView>(R.id.check)
        check.accent = if (carry) ctx.getColor(R.color.carry_ink) else color
        check.idleColor = ctx.getColor(R.color.divider)
        check.countText = if (item.target > 1 && !item.done) "${item.progress}/${item.target}" else null

        // 只有这一条的完成状态相对上次渲染发生了翻转，才播打卡动效
        val prev = doneState[item.key]
        val animated = prev != null && prev != item.done
        doneState[item.key] = item.done
        check.setDone(item.done, animated)

        val minus = row.findViewById<TextView>(R.id.btnMinus)
        minus.show(item.progress > 0 && !item.done)
        minus.tap { Repo.bump(Repo.today().date, item.key, -1) }

        check.tap(haptic = false) { onCheck(check, item) }
        row.tap(haptic = false) { onCheck(check, item) }

        // 长按直接改这条任务：卡片从这一行展开，关闭时缩回同一行
        row.setOnLongClickListener { v ->
            val def = Repo.taskById(item.taskId)
            if (def == null) {
                // 任务定义已删、只剩补做条目，没有可编辑的对象
                ctx.toast(ctx.getString(R.string.tag_orphan))
            } else {
                Motion.tick(v)
                AnchoredCard.showTaskEditor(host, def, v)
            }
            true // 吃掉事件，避免松手时又触发打卡
        }

        if (firstBind) Motion.stagger(row, index)
    }

    private fun onCheck(check: CheckCircleView, item: DayItem) {
        if (item.done) {
            // 已完成再点是取消，避免误触时无法回退
            Motion.tick(check)
            Repo.bump(Repo.today().date, item.key, -item.target)
            return
        }
        Motion.confirm(check)
        val justDone = Repo.bump(Repo.today().date, item.key, 1)
        if (!justDone) return

        val loc = IntArray(2)
        check.getLocationOnScreen(loc)
        val cx = loc[0] + check.width / 2f
        val cy = loc[1] + check.height / 2f

        val rec = Repo.today()
        if (rec.allDone() && !rec.celebrated) {
            Repo.markCelebrated(rec.date)
            host.celebrateDay(cx, cy, encourage())
        } else {
            host.burst(cx, cy)
        }
    }

    private fun encourage(): String {
        val msgs = ctx.resources.getStringArray(R.array.encourage)
        return msgs[(Repo.streak().coerceAtLeast(0)) % msgs.size]
    }

    /** 任务管理：列出全部任务定义，点进去编辑。 */
    private fun showManage() {
        val tasks = Repo.read { it.tasks.sortedBy { t -> t.order } }
        if (tasks.isEmpty()) {
            TaskSheet.show(host, null)
            return
        }
        AppListDialog.show(
            ctx = host,
            title = ctx.getString(R.string.manage_tasks),
            rows = tasks.map {
                DialogRow(
                    title = it.title,
                    sub = it.deadlineText(),
                    color = Ui.taskColor(ctx, it.colorIndex)
                )
            },
            positive = ctx.getString(R.string.add_task),
            negative = ctx.getString(R.string.cancel),
            onPick = { i -> TaskSheet.show(host, tasks[i]) },
            onPositive = { TaskSheet.show(host, null) }
        )
    }

    override fun onShow() {
        refresh()
    }
}
