package com.gongkao.checkin.ui.page

import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gongkao.checkin.R
import com.gongkao.checkin.anim.Motion
import com.gongkao.checkin.data.AppTheme
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.DayItem
import com.gongkao.checkin.data.KIND_CARRY
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.ui.AnchoredCard
import com.gongkao.checkin.ui.AppListDialog
import com.gongkao.checkin.ui.DialogRow
import com.gongkao.checkin.data.Subtask
import com.gongkao.checkin.ui.MainActivity
import com.gongkao.checkin.ui.TaskMenu
import com.gongkao.checkin.ui.open
import com.gongkao.checkin.ui.stats.OverviewActivity
import com.gongkao.checkin.ui.toast
import com.gongkao.checkin.ui.Page
import com.gongkao.checkin.ui.SettingsActivity
import com.gongkao.checkin.ui.TaskSheet
import com.gongkao.checkin.ui.Ui
import com.gongkao.checkin.ui.DatePicker
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
        v.findViewById<TextView>(R.id.btnOverview).tap { ctx.open<OverviewActivity>() }
        v.findViewById<ImageView>(R.id.btnTheme).tap { pickTheme() }
        v.findViewById<ImageView>(R.id.btnReorder).tap { toggleReorder() }
    }

    /** 四主题任选。切换要重建 Activity（调色板和 style 都在 onCreate 时定）。 */
    private fun pickTheme() {
        val current = Repo.appTheme()
        AppListDialog.show(
            ctx = ctx,
            title = ctx.getString(R.string.theme_switch),
            rows = AppTheme.entries.map { t ->
                DialogRow(
                    title = ctx.getString(t.nameRes) + if (t == current) "  ✓" else ""
                )
            },
            negative = ctx.getString(R.string.cancel),
            onPick = { i ->
                val picked = AppTheme.entries[i]
                if (picked != current) {
                    Repo.setAppTheme(picked)
                    host.recreate()
                }
            }
        )
    }

    /** 进/出排序模式。排序模式下任务卡可按住上下拖。 */
    private fun toggleReorder() {
        reordering = !reordering
        ctx.toast(
            ctx.getString(if (reordering) R.string.reorder_on else R.string.reorder_off)
        )
        firstBind = false
        refresh()
    }

    override fun refresh() {
        if (!created) return
        val rec = Repo.today()
        val items = Repo.sortedItems(rec)

        dateText.text = DateUtil.pretty(DateUtil.today())
        bindCountdown()

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
        // 拖动中不重建列表：每次换位都会写库并回调到这里，重建会把手指底下那一行
        // 换绑到别的任务上（bindList 按下标绑），后面的拖动就全动错对象了。
        // 松手时会补一次 refresh 把真实顺序刷出来。
        if (!dragActive) bindList(items)
        firstBind = false
    }

    /**
     * 顶部那一行：没设结束日时是个「设置结束日」按钮，设了之后原位变成剩余天数
     * （按钮底去掉），点它都能进日期选择改期。
     *
     * 通用模式没有结束日概念，那边播标记的重要日。
     */
    private fun bindCountdown() {
        val general = Repo.appMode().isGeneral
        if (general) {
            countdownText.setBackgroundResource(0)
            countdownText.setPadding(0, 0, 0, 0)
            val markName = ctx.getString(R.string.mark_name_general)
            val toMark = Repo.daysToMark()
            countdownText.text = when {
                toMark == null -> ctx.getString(R.string.countdown_none_general)
                toMark > 0 -> ctx.getString(R.string.mark_countdown, markName, toMark)
                toMark == 0L -> ctx.getString(R.string.mark_today, markName)
                else -> ctx.getString(R.string.countdown_none_general)
            }
            countdownText.tap { host.select(3) }
            return
        }

        val left = Repo.daysLeft()
        if (left == null) {
            // 还没设：做成按钮样子，点了直接选日期
            countdownText.setBackgroundResource(R.drawable.bg_pill_glass)
            countdownText.setPadding(12.dp, 6.dp, 12.dp, 6.dp)
            countdownText.setText(R.string.countdown_set)
        } else {
            countdownText.setBackgroundResource(0)
            countdownText.setPadding(0, 0, 0, 0)
            countdownText.text = when {
                left > 0 -> ctx.getString(R.string.countdown_left, left)
                left == 0L -> ctx.getString(R.string.countdown_today)
                else -> ctx.getString(R.string.countdown_past)
            }
        }
        countdownText.tap { pickEndDate() }
    }

    /** 选结束日。已设置时把当前值当默认选中，方便改期。 */
    private fun pickEndDate() {
        val current = Repo.read { it.settings.endDate }
        DatePicker.show(
            ctx = host,
            current = current,
            title = ctx.getString(R.string.countdown_set),
            // 已设置时给个清除入口，否则设错了只能一直改不能取消
            allowClear = current != null,
            onPick = { picked -> Repo.setEndDate(picked) },
            onClear = { Repo.setEndDate(null) }
        )
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

    /** 展开了步骤清单的条目 key。 */
    private val expanded = mutableSetOf<String>()

    /** 是否处于排序模式（顶栏那个按钮切换）。 */
    private var reordering = false

    /** 有一行正在被拖。期间 [refresh] 不重建列表，见那边的注释。 */
    private var dragActive = false

    /**
     * 排序模式下的拖动。**按住**某一行才开始拖，之后上下移动，越过相邻行就换位并
     * 立刻写回顺序；松手结束。只对当天条目生效——欠账是聚合出来的，没有独立顺序。
     *
     * 装在**外层 row** 上而不是内层 taskRow：taskRow 只占卡片上半部分，装它的话
     * 卡片下缘按不动。前提是先把行内所有可点的后代静音（见 [muteDescendants]）——
     * 它们是 clickable，不静音的话触摸被子 view 先吃掉，外层这个监听根本收不到。
     *
     * 换位判定拿「一格 = 行高」算，成立的前提是排序模式下所有行等高——
     * 靠 [bindSubtasks] 在排序模式收起步骤清单来保证。
     */
    private fun wireDrag(row: View, item: DayItem) {
        if (!reordering || item.kind == KIND_CARRY) {
            row.setOnTouchListener(null)
            return
        }
        // 长按才起拖：按下就抢手势的话排序模式下整页都滚不动了，
        // 而且跟提示文案「按住任务上下拖动」不符。
        val timeout = android.view.ViewConfiguration.getLongPressTimeout().toLong()
        val slop = android.view.ViewConfiguration.get(row.context).scaledTouchSlop
        var downY = 0f
        var baseY = 0f
        var lastY = 0f
        var dragging = false
        var pending: Runnable? = null

        fun stop(v: View) {
            pending?.let { v.removeCallbacks(it) }
            pending = null
        }

        // 已越过的邻居数（带符号，下正上负）。拖动中列表不重建，所以
        // 「容器下标 ↔ 拖动开始时的那一行」是固定对应关系，靠这个数推该让位的是谁。
        var crossed = 0
        var myIndex = -1

        row.setOnTouchListener { v, ev ->
            lastY = ev.rawY
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = ev.rawY
                    dragging = false
                    crossed = 0
                    val r = Runnable {
                        dragging = true
                        dragActive = true
                        baseY = lastY
                        // 拖起来了才不让外面的滚动容器抢手势
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        // 只抬 elevation，不要 bringToFront()：taskList 是 LinearLayout，
                        // 子 view 顺序就是排版顺序，提到最前会让这行直接跳到列表末尾
                        row.elevation = 12f.dp
                        myIndex = taskList.indexOfChild(row)
                        Motion.tick(v)
                    }
                    pending = r
                    v.postDelayed(r, timeout)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) {
                        // 长按还没到就先滑动 → 这是在滚列表，撤掉长按、把手势让回去
                        if (kotlin.math.abs(ev.rawY - downY) > slop) stop(v)
                        return@setOnTouchListener true
                    }
                    // 拖着的这行一直跟手；每让过一个邻居，它的「归位点」就下移一格
                    val dy = ev.rawY - baseY
                    row.translationY = dy
                    val h = row.height.toFloat().coerceAtLeast(1f)
                    val rest = h * crossed
                    val down = when {
                        dy - rest > h * 0.6f -> true
                        dy - rest < -h * 0.6f -> false
                        else -> return@setOnTouchListener true
                    }
                    if (!swapWithNeighbor(item, down)) return@setOnTouchListener true
                    // 让位的那一行：往回走时是把之前挪开的那个放回去，所以下标要分情况
                    val idx = myIndex + when {
                        down -> if (crossed < 0) crossed else crossed + 1
                        else -> if (crossed > 0) crossed else crossed - 1
                    }
                    taskList.getChildAt(idx)?.let { n ->
                        n.animate().translationY(n.translationY + if (down) -h else h)
                            .setDuration(120).start()
                    }
                    crossed += if (down) 1 else -1
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stop(v)
                    if (dragging) {
                        dragging = false
                        dragActive = false
                        row.elevation = 0f
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        // 顺序早就一步步写进库了，这里只要把拖动期间的位移全清掉，
                        // 再重建一次列表，位置就跟真实顺序对上了
                        for (i in 0 until taskList.childCount) {
                            taskList.getChildAt(i).let { it.animate().cancel(); it.translationY = 0f }
                        }
                        refresh()
                    }
                    true
                }

                else -> false
            }
        }
    }

    /**
     * 把行内所有可点的后代静音，让触摸能落到外层的拖动监听上。
     *
     * 排序模式下不这么做就会出现「按住任务直接弹菜单」：条目数不变时 [bindList] 会
     * 复用行 view，`check`/`btnMinus` 上次绑的 `tap(onLongPress = …)` 还在，它们是
     * clickable，触摸落在圆圈上时先被子 view 消费，长按计时器照跑，弹出的是任务菜单。
     *
     * 只清监听和 clickable，不动 enabled/visibility——退出排序模式时 [bindRow] 会把
     * 该装的 tap 全部重新装回去（步骤行是 [bindSubtasks] 每次重建的）。
     */
    private fun muteDescendants(view: View) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) muteDescendants(view.getChildAt(i))
        }
        view.setOnTouchListener(null)
        view.setOnClickListener(null)
        view.isClickable = false
        view.isLongClickable = false
    }

    /** 跟上/下一个当天条目换位，返回是否真的换了。 */
    private fun swapWithNeighbor(item: DayItem, down: Boolean): Boolean {
        val todayItems = Repo.sortedItems(Repo.today()).filter { it.kind != KIND_CARRY }
        val i = todayItems.indexOfFirst { it.key == item.key }
        if (i < 0) return false
        val j = if (down) i + 1 else i - 1
        if (j < 0 || j >= todayItems.size) return false
        Repo.moveTask(item.taskId, todayItems[j].taskId)
        return true
    }

    /** 步骤清单：勾一条就联动主进度，勾满整条自动完成。 */
    private fun bindSubtasks(row: View, item: DayItem, subs: List<Subtask>) {
        val box = row.findViewById<LinearLayout>(R.id.subtaskBox)
        // 排序模式下一律收起：行高一致，拖动的换位判定才准（拿行高当一格）。
        // expanded 本身不动，退出排序模式后原来展开的还是展开的。
        val open = subs.isNotEmpty() && item.key in expanded && !reordering
        box.show(open)
        if (!open) {
            box.removeAllViews()
            return
        }
        box.removeAllViews()
        subs.forEachIndexed { i, s ->
            val line = box.inflateChild(R.layout.item_subtask)
            val done = s.id in item.doneSubtasks
            line.findViewById<TextView>(R.id.subCheck).apply {
                text = if (done) "✓" else ""
                setBackgroundResource(
                    if (done) R.drawable.bg_circle_accent else R.drawable.bg_circle_soft
                )
            }
            line.findViewById<TextView>(R.id.subTitle).apply {
                text = s.title
                alpha = if (done) 0.45f else 1f
                paintFlags = if (done) {
                    paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }
            }
            line.tap(haptic = false) { v ->
                Motion.tick(v)
                val justDone = Repo.toggleSubtask(Repo.today().date, item.key, s.id)
                if (justDone) celebrateAt(v)
            }
            box.addView(line)
            Motion.stagger(line, i)
        }
    }

    /** 步骤勾满导致整条完成时，在那一步的位置放动效。 */
    private fun celebrateAt(v: View) {
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        val cx = loc[0] + v.width / 2f
        val cy = loc[1] + v.height / 2f
        val rec = Repo.today()
        if (rec.allDone() && !rec.celebrated) {
            Repo.markCelebrated(rec.date)
            host.celebrateDay(cx, cy, encourage())
        } else {
            host.burst(cx, cy)
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

        // 步骤：只有当天条目才有粒度，欠账只结转数量
        val def = Repo.taskById(item.taskId)
        val subs = if (item.kind == KIND_CARRY) emptyList() else def?.subtasks.orEmpty()

        row.findViewById<TextView>(R.id.sub).text = buildString {
            if (subs.isNotEmpty()) {
                append(ctx.getString(R.string.subtask_progress, item.progress, item.target))
            } else if (item.target > 1) {
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

        bindSubtasks(row, item, subs)

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

        // 圆圈永远是「整条打卡/取消」；有步骤时点卡片改成展开清单，
        // 否则点一下就把所有步骤刷成完成，等于绕过了拆步骤的意义
        // 长按菜单跟点击共用一个 OnTouchListener（见 tap 的注释）。
        // 装在 rowBody 和 check 上而不是外层 row：这两个是 clickable，
        // 触摸被它们自己消费掉，外层根本收不到。
        val rowBody = row.findViewById<View>(R.id.taskRow)
        val longPress: (View) -> Unit = { openTaskMenu(rowBody, item) }

        // 排序模式下这一行只管拖：先静音行内所有可点的后代（否则触摸被 check/btnMinus
        // 先吃掉，长按弹出的是任务菜单而不是起拖），再把拖动装到外层 row 上。
        // 必须排在 bindSubtasks 之后：步骤行是那边每次重建的，得连它们一起静音。
        if (reordering && item.kind != KIND_CARRY) {
            muteDescendants(row)
            wireDrag(row, item)
        } else {
            wireDrag(row, item) // 非排序模式：清掉可能残留的拖动监听
            minus.tap { Repo.bump(Repo.today().date, item.key, -1) }
            check.tap(haptic = false, onLongPress = longPress) { onCheck(check, item) }
            if (subs.isEmpty()) {
                rowBody.tap(haptic = false, onLongPress = longPress) { onCheck(check, item) }
            } else {
                rowBody.tap(haptic = false, onLongPress = longPress) {
                    if (!expanded.remove(item.key)) expanded.add(item.key)
                    bindSubtasks(row, item, subs)
                }
            }
        }

        if (firstBind) Motion.stagger(row, index)
    }

    /**
     * 弹任务操作菜单。[anchor] 是菜单要盖住的那一行——用 rowBody 而不是外层 row：
     * 展开步骤后外层会变高，锚外层会盖住整块。
     */
    private fun openTaskMenu(anchor: View, item: DayItem) {
        val def = Repo.taskById(item.taskId)
        if (def == null) {
            // 任务定义已删、只剩补做条目，没有可操作的对象
            ctx.toast(ctx.getString(R.string.tag_orphan))
            return
        }
        Motion.tick(anchor)
        TaskMenu.show(host, def, anchor) { showManage() }
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
            // 编辑器里按返回是「放弃」，退回管理列表这一级；保存则整条链一起收掉
            onPick = { i -> TaskSheet.show(host, tasks[i], onCancel = { showManage() }) },
            onPositive = { TaskSheet.show(host, null, onCancel = { showManage() }) }
        )
    }

    override fun onShow() {
        refresh()
    }
}
