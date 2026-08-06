package com.gongkao.checkin.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.gson.GsonBuilder
import java.io.File
import java.time.LocalDate
import java.util.UUID

/**
 * 全局单例仓库。UI 线程与局域网同步线程都会访问，所有读写走 [lock]。
 * 变更后通过 [listeners] 通知（回调固定在主线程）。
 */
object Repo {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<() -> Unit>()

    private lateinit var file: File
    private lateinit var backup: File
    private var loaded = false

    var state: AppState = AppState()
        private set

    /** 每次数据变更自增，网页端用它判断是否需要刷新 */
    @Volatile
    var revision: Long = 0
        private set

    fun init(ctx: Context) {
        if (loaded) return
        file = File(ctx.filesDir, "checkin_state.json")
        backup = File(ctx.filesDir, "checkin_state.bak.json")
        state = readFrom(file) ?: readFrom(backup) ?: AppState()
        loaded = true
        if (state.settings.syncPin.isBlank()) {
            state.settings.syncPin = (1000..9999).random().toString()
        }
        if (state.tasks.isEmpty() && state.days.isEmpty()) seedDemo()
        ensureDays()
        persist()
    }

    private fun readFrom(f: File): AppState? = runCatching {
        if (!f.exists() || f.length() == 0L) return null
        gson.fromJson(f.readText(), AppState::class.java)?.also {
            if (it.days == null) it.days = LinkedHashMap()
        }
    }.getOrNull()

    private fun seedDemo() {
        val today = DateUtil.todayStr()
        listOf(
            Triple("行测·言语理解 40 题", REPEAT_DAILY, 0),
            Triple("行测·资料分析 20 题", REPEAT_DAILY, 1),
            Triple("百化分背诵一轮", REPEAT_DAILY, 2),
            Triple("资料分析公式背诵", REPEAT_DAILY, 3),
            Triple("申论·大作文 1 篇", REPEAT_DAILY, 4)
        ).forEachIndexed { i, (t, r, c) ->
            state.tasks.add(
                TaskDef(
                    id = UUID.randomUUID().toString(),
                    title = t, repeat = r, startDate = today, order = i, colorIndex = c
                )
            )
        }
    }

    fun addListener(l: () -> Unit) { synchronized(lock) { listeners.add(l) } }
    fun removeListener(l: () -> Unit) { synchronized(lock) { listeners.remove(l) } }

    /** 在锁内修改数据，随后落盘 + 通知。 */
    fun <T> edit(block: (AppState) -> T): T {
        val r = synchronized(lock) { val v = block(state); revision++; v }
        persist()
        notifyChanged()
        return r
    }

    fun <T> read(block: (AppState) -> T): T = synchronized(lock) { block(state) }

    fun notifyChanged() {
        val snapshot = synchronized(lock) { listeners.toList() }
        main.post { snapshot.forEach { runCatching { it() } } }
    }

    private fun persist() {
        val text = synchronized(lock) { gson.toJson(state) }
        runCatching {
            if (file.exists()) file.copyTo(backup, overwrite = true)
            file.writeText(text)
        }
    }

    // ---------------------------------------------------------------- 日计划

    val globalEnd: LocalDate?
        get() = DateUtil.parse(state.settings.endDate)

    /**
     * 保证「今天」的计划存在，并把历史未完成的量逐日累加滚到今天。
     * 逐日 roll（而不是一次性汇总）保证欠账数量准确：漏 3 天 = 欠 3 份。
     */
    fun ensureDays() {
        synchronized(lock) {
            val today = DateUtil.today()
            val last: LocalDate? = DateUtil.parse(state.lastRolledDate)
            // 首次启动，或手机时间被往前调过 → 只重建今天
            if (last == null || last.isAfter(today)) {
                buildDay(today, today.minusDays(1))
                state.lastRolledDate = today.toString()
                return
            }
            // 从上次滚动日逐日重建到今天，每天的欠账来源就是它的前一天
            var cursor: LocalDate = last
            var guard = 0
            while (!cursor.isAfter(today) && guard++ < 800) {
                buildDay(cursor, cursor.minusDays(1))
                cursor = cursor.plusDays(1)
            }
            state.lastRolledDate = today.toString()
        }
    }

    /** 重建 [date] 的条目：幂等，可重复调用（新增任务后也会补进当天）。 */
    private fun buildDay(date: LocalDate, prevDate: LocalDate?) {
        val key = date.toString()
        val rec = state.days.getOrPut(key) { DayRecord(key) }
        val end = globalEnd

        // 1) 当天常规条目
        state.tasks.filter { it.appliesTo(date, end) }.sortedBy { it.order }.forEach { t ->
            val exist = rec.items.firstOrNull { it.taskId == t.id && it.kind == KIND_TODAY }
            if (exist == null) {
                rec.items.add(
                    DayItem(
                        taskId = t.id, title = t.title, kind = KIND_TODAY,
                        target = t.target.coerceAtLeast(1), unit = t.unit, colorIndex = t.colorIndex
                    )
                )
            } else {
                exist.title = t.title
                exist.unit = t.unit
                exist.colorIndex = t.colorIndex
                if (exist.target < t.target) exist.target = t.target
            }
        }
        // 任务已删除/归档，且当天还没做过 → 移除当天条目（保留已有进度的）
        rec.items.removeAll { item ->
            item.kind == KIND_TODAY && item.progress == 0 &&
                state.tasks.none { it.id == item.taskId && it.appliesTo(date, end) }
        }

        // 2) 欠账：上一天所有条目的剩余量累加
        val prev = prevDate?.let { state.days[it.toString()] }
        val debt = HashMap<String, Pair<Int, String>>() // taskId -> (欠量, 最早欠账日)
        prev?.items?.forEach { item ->
            val remain = item.remaining
            if (remain > 0) {
                val oldest = item.oldestDebtDate ?: prev.date
                val cur = debt[item.taskId]
                debt[item.taskId] = Pair(
                    (cur?.first ?: 0) + remain,
                    if (cur == null || oldest < cur.second) oldest else cur.second
                )
            }
        }
        debt.forEach { (taskId, pair) ->
            val def = state.tasks.firstOrNull { it.id == taskId }
            val title = def?.title
                ?: prev?.items?.firstOrNull { it.taskId == taskId }?.title
                ?: "已删除的任务"
            val exist = rec.items.firstOrNull { it.taskId == taskId && it.kind == KIND_CARRY }
            if (exist == null) {
                rec.items.add(
                    DayItem(
                        taskId = taskId, title = title, kind = KIND_CARRY,
                        target = pair.first, unit = def?.unit ?: "",
                        colorIndex = def?.colorIndex ?: 0,
                        oldestDebtDate = pair.second, orphan = def == null
                    )
                )
            } else {
                exist.title = title
                exist.target = pair.first
                exist.oldestDebtDate = pair.second
                exist.orphan = def == null
                if (exist.progress > exist.target) exist.progress = exist.target
            }
        }
        // 上一天已补完 → 今天的欠账条目作废（无进度时移除）
        rec.items.removeAll { it.kind == KIND_CARRY && !debt.containsKey(it.taskId) && it.progress == 0 }
    }

    fun today(): DayRecord = synchronized(lock) {
        state.days.getOrPut(DateUtil.todayStr()) { DayRecord(DateUtil.todayStr()) }
    }

    fun day(date: String): DayRecord? = synchronized(lock) { state.days[date] }

    /**
     * 顺序只由「欠账优先 → 最早欠账日 → 标题」决定，**不含完成状态**：
     * 划掉的任务留在原地，不会跳到列表末尾。
     */
    fun sortedItems(rec: DayRecord): List<DayItem> =
        rec.items.sortedWith(
            compareBy({ it.kind != KIND_CARRY }, { it.oldestDebtDate ?: "" }, { it.title })
        )

    /** 打卡 / 反打卡。返回操作后该条目是否刚刚完成。 */
    fun bump(date: String, key: String, delta: Int): Boolean = edit { st ->
        val rec = st.days[date] ?: return@edit false
        val item = rec.items.firstOrNull { it.key == key } ?: return@edit false
        val before = item.done
        item.progress = (item.progress + delta).coerceIn(0, item.target)
        item.doneAt = if (item.done) System.currentTimeMillis() else 0L
        if (!rec.allDone()) rec.celebrated = false
        item.done && !before
    }

    fun markCelebrated(date: String) = edit { st -> st.days[date]?.celebrated = true }

    // ---------------------------------------------------------------- 任务

    fun upsertTask(t: TaskDef) = edit { st ->
        val i = st.tasks.indexOfFirst { it.id == t.id }
        if (i >= 0) st.tasks[i] = t else {
            if (t.id.isBlank()) t.id = UUID.randomUUID().toString()
            if (t.startDate.isBlank()) t.startDate = DateUtil.todayStr()
            t.order = (st.tasks.maxOfOrNull { it.order } ?: -1) + 1
            st.tasks.add(t)
        }
        buildDay(DateUtil.today(), DateUtil.today().minusDays(1))
    }

    /** 按 id 找任务定义。补做条目的任务可能已被删除，所以返回可空。 */
    fun taskById(id: String): TaskDef? = read { st -> st.tasks.firstOrNull { it.id == id } }

    fun deleteTask(id: String) = edit { st ->
        st.tasks.removeAll { it.id == id }
        st.days[DateUtil.todayStr()]?.items?.removeAll { it.taskId == id && it.kind == KIND_TODAY && it.progress == 0 }
    }

    fun setEndDate(d: String?) = edit { st -> st.settings.endDate = d }

    // ---------------------------------------------------------------- 记录

    fun addTimerSession(s: TimerSession) = edit { st -> st.timerSessions.add(0, s) }
    fun deleteTimerSession(id: String) = edit { st -> st.timerSessions.removeAll { it.id == id } }
    fun timerSessions(): List<TimerSession> = read { it.timerSessions.toList() }
    fun timerSession(id: String): TimerSession? = read { it.timerSessions.firstOrNull { s -> s.id == id } }

    fun addPercentSession(s: PercentSession) = edit { st -> st.percentSessions.add(0, s) }
    fun percentSessions(): List<PercentSession> = read { it.percentSessions.toList() }
    fun percentSession(id: String): PercentSession? = read { it.percentSessions.firstOrNull { s -> s.id == id } }

    fun addFormulaSession(s: FormulaSession) = edit { st -> st.formulaSessions.add(0, s) }
    fun formulaSessions(): List<FormulaSession> = read { it.formulaSessions.toList() }

    fun newId(): String = UUID.randomUUID().toString()

    // ---------------------------------------------------------------- 统计

    fun recordedDates(): List<String> = read { it.days.keys.sortedDescending() }

    /** 连续全勤天数（今天没做完不算断） */
    fun streak(): Int = read { st ->
        var n = 0
        var d = DateUtil.today()
        val todayRec = st.days[d.toString()]
        if (todayRec != null && todayRec.allDone()) n++
        d = d.minusDays(1)
        var guard = 0
        while (guard++ < 800) {
            val rec = st.days[d.toString()] ?: break
            if (rec.items.isEmpty()) { d = d.minusDays(1); continue }
            if (!rec.allDone()) break
            n++
            d = d.minusDays(1)
        }
        n
    }

    fun daysLeft(): Long? = globalEnd?.let { DateUtil.daysBetween(DateUtil.today(), it) }
}
