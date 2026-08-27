package com.gongkao.checkin.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.gson.GsonBuilder
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 全局单例仓库。UI 线程与局域网同步线程都会访问，所有读写走 [lock]。
 * 变更后通过 [listeners] 通知（回调固定在主线程）。
 *
 * 落盘是异步的：[edit] 只排一次写任务，真正的 json 序列化 + 写文件在 `repo-io`
 * 线程上做，避免每次打卡都在 UI 线程上写整份状态。进入后台时由 [flush] 兜底。
 */
object Repo {

    /** 保留的日计划天数上限，约 13 个月；连续全勤天数因此也以此为上限 */
    private const val MAX_DAYS = 400

    /** 三类练习记录各自保留的条数上限（列表是新的在前，超出从尾部丢） */
    private const val MAX_SESSIONS = 200

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<() -> Unit>()

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "repo-io") }
    private val writeQueued = AtomicBoolean(false)

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

    /**
     * 异步落盘，多次连续调用只提交一次写任务（最新状态由任务执行时快照，不丢数据）。
     * 正常使用时每次打卡触发一次 edit → 一次 persist → 一次 IO 任务；
     * 连续快速操作自动合并，IO 线程不会被堆积。
     */
    private fun persist() {
        if (!writeQueued.compareAndSet(false, true)) return
        io.execute {
            val text = synchronized(lock) {
                writeQueued.set(false)
                gson.toJson(state)
            }
            runCatching {
                if (file.exists()) file.copyTo(backup, overwrite = true)
                file.writeText(text)
            }
        }
    }

    /**
     * 进入后台时调用，同步等待写完（最多 5 秒）。
     * 保证 App.onTrimMemory / Activity.onStop 时数据不丢失。
     */
    fun flush() {
        val text = synchronized(lock) { writeQueued.set(false); gson.toJson(state) }
        runCatching {
            io.submit {
                runCatching {
                    if (file.exists()) file.copyTo(backup, overwrite = true)
                    file.writeText(text)
                }
            }.get(5, TimeUnit.SECONDS)
        }
    }

    // ---------------------------------------------------------------- 日计划

    /**
     * 总结束日只在考公模式下生效。通用模式不套用它（但值保留，切回考公照旧），
     * 否则当习惯打卡用的时候会莫名其妙在考试日之后就不再生成计划。
     */
    val globalEnd: LocalDate?
        get() = if (appMode().isGeneral) null else DateUtil.parse(state.settings.endDate)

    fun appMode(): AppMode = AppMode.of(state.settings.appMode)

    fun setAppMode(mode: AppMode) = edit { st -> st.settings.appMode = mode.id }

    fun markDate(): LocalDate? = DateUtil.parse(state.settings.markDate)

    fun setMarkDate(d: String?) = edit { st -> st.settings.markDate = d }

    /** 距标记日还有几天。已过去返回负数，未设置返回 null。 */
    fun daysToMark(): Long? = markDate()?.let { DateUtil.daysBetween(DateUtil.today(), it) }

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

            // 裁掉超出上限的旧天记录，保留最近 MAX_DAYS 条
            if (state.days.size > MAX_DAYS) {
                val toRemove = state.days.keys.sorted().take(state.days.size - MAX_DAYS)
                toRemove.forEach { state.days.remove(it) }
            }
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
                        target = t.effectiveTarget(),
                        unit = if (t.hasSubtasks) "" else t.unit,
                        colorIndex = t.colorIndex
                    )
                )
            } else {
                exist.title = t.title
                exist.unit = if (t.hasSubtasks) "" else t.unit
                exist.colorIndex = t.colorIndex
                if (t.hasSubtasks) {
                    // 步骤数就是目标数，增删步骤都要跟着变（不像手填 target 只增不减，
                    // 否则删掉一步之后目标永远到不了，这条就再也做不完）
                    exist.target = t.subtasks.size
                    val live = t.subtasks.map { s -> s.id }.toSet()
                    exist.doneSubtasks.retainAll(live)
                    exist.progress = exist.doneSubtasks.size
                } else {
                    exist.doneSubtasks.clear()
                    if (exist.target < t.target) exist.target = t.target
                }
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
    /**
     * 今日列表的顺序：欠账条目置顶（按最早欠账日），其余按任务自己的 [TaskDef.order]。
     *
     * 按 order 排是「调整顺序」能看出效果的前提——原先按标题排，
     * 拖完写回 order 界面上一点变化都没有。完成的条目不沉底，位置固定。
     */
    fun sortedItems(rec: DayRecord): List<DayItem> {
        val orderOf = state.tasks.associate { it.id to it.order }
        return rec.items.sortedWith(
            compareBy(
                { it.kind != KIND_CARRY },
                { if (it.kind == KIND_CARRY) it.oldestDebtDate ?: "" else "" },
                { orderOf[it.taskId] ?: Int.MAX_VALUE },
                { it.title }
            )
        )
    }

    /**
     * 把 [fromId] 挪到 [toId] 现在的位置，其余顺延。拖动排序松手时调。
     * 先把 order 规范化成 0..n-1，避免历史数据里有重复的 order 导致挪不动。
     */
    fun moveTask(fromId: String, toId: String) = edit { st ->
        if (fromId == toId) return@edit
        val ordered = st.tasks.sortedBy { it.order }.toMutableList()
        val from = ordered.indexOfFirst { it.id == fromId }
        val to = ordered.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0) return@edit
        ordered.add(to, ordered.removeAt(from))
        ordered.forEachIndexed { i, t -> t.order = i }
        buildDay(DateUtil.today(), DateUtil.today().minusDays(1))
    }

    /** 打卡 / 反打卡。返回操作后该条目是否刚刚完成。 */
    fun bump(date: String, key: String, delta: Int): Boolean = edit { st ->
        val rec = st.days[date] ?: return@edit false
        val item = rec.items.firstOrNull { it.key == key } ?: return@edit false
        val before = item.done
        item.progress = (item.progress + delta).coerceIn(0, item.target)
        item.doneAt = if (item.done) System.currentTimeMillis() else 0L
        // 直接点大圆圈的情况：把步骤勾选同步成一致，免得展开后进度和勾选对不上
        val subs = st.tasks.firstOrNull { it.id == item.taskId }?.subtasks
        if (item.kind == KIND_TODAY && !subs.isNullOrEmpty()) {
            val ids = subs.map { it.id }
            item.doneSubtasks = ids.take(item.progress).toMutableList()
        }
        if (!rec.allDone()) rec.celebrated = false
        item.done && !before
    }

    /**
     * 勾/取消某一步，进度跟着步骤数走。返回是否「刚好整条做完」，交给 UI 放庆祝动效。
     * 只对当天条目生效——欠账条目只结转数量，没有步骤粒度。
     */
    fun toggleSubtask(date: String, key: String, subtaskId: String): Boolean = edit { st ->
        val rec = st.days[date] ?: return@edit false
        val item = rec.items.firstOrNull { it.key == key } ?: return@edit false
        if (item.kind != KIND_TODAY) return@edit false
        val subs = st.tasks.firstOrNull { it.id == item.taskId }?.subtasks.orEmpty()
        if (subs.none { it.id == subtaskId }) return@edit false

        val before = item.done
        if (!item.doneSubtasks.remove(subtaskId)) item.doneSubtasks.add(subtaskId)
        item.target = subs.size
        item.progress = item.doneSubtasks.size
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

    fun addTimerSession(s: TimerSession) = edit { st ->
        st.timerSessions.add(0, s)
        if (st.timerSessions.size > MAX_SESSIONS)
            st.timerSessions.subList(MAX_SESSIONS, st.timerSessions.size).clear()
    }
    fun deleteTimerSession(id: String) = edit { st -> st.timerSessions.removeAll { it.id == id } }
    fun timerSessions(): List<TimerSession> = read { it.timerSessions.toList() }
    fun timerSession(id: String): TimerSession? = read { it.timerSessions.firstOrNull { s -> s.id == id } }

    fun addPercentSession(s: PercentSession) = edit { st ->
        st.percentSessions.add(0, s)
        if (st.percentSessions.size > MAX_SESSIONS)
            st.percentSessions.subList(MAX_SESSIONS, st.percentSessions.size).clear()
    }
    fun percentSessions(): List<PercentSession> = read { it.percentSessions.toList() }
    fun percentSession(id: String): PercentSession? = read { it.percentSessions.firstOrNull { s -> s.id == id } }

    fun addFormulaSession(s: FormulaSession) = edit { st ->
        st.formulaSessions.add(0, s)
        if (st.formulaSessions.size > MAX_SESSIONS)
            st.formulaSessions.subList(MAX_SESSIONS, st.formulaSessions.size).clear()
    }
    fun formulaSessions(): List<FormulaSession> = read { it.formulaSessions.toList() }

    fun addMentalMathSession(s: MentalMathSession) = edit { st ->
        st.mentalMathSessions.add(0, s)
        if (st.mentalMathSessions.size > MAX_SESSIONS)
            st.mentalMathSessions.subList(MAX_SESSIONS, st.mentalMathSessions.size).clear()
    }
    fun mentalMathSessions(): List<MentalMathSession> = read { it.mentalMathSessions.toList() }

    fun addPomodoroSession(s: PomodoroSession) = edit { st ->
        st.pomodoroSessions.add(0, s)
        if (st.pomodoroSessions.size > MAX_SESSIONS)
            st.pomodoroSessions.subList(MAX_SESSIONS, st.pomodoroSessions.size).clear()
    }
    fun pomodoroSessions(): List<PomodoroSession> = read { it.pomodoroSessions.toList() }

    fun appTheme(): AppTheme = AppTheme.of(state.settings.themeId)

    fun setAppTheme(t: AppTheme) = edit { st -> st.settings.themeId = t.id }

    fun setBankSource(id: String) = edit { st -> st.settings.bankSourceId = id }
    fun setReviewSkill(id: String) = edit { st -> st.settings.reviewSkillId = id }

    fun addBankSession(s: BankSession) = edit { st ->
        st.bankSessions.add(0, s)
        if (st.bankSessions.size > MAX_SESSIONS)
            st.bankSessions.subList(MAX_SESSIONS, st.bankSessions.size).clear()
    }
    fun bankSessions(): List<BankSession> = read { it.bankSessions.toList() }

    // ---------------------------------------------------------------- 复盘存档

    fun bankProgress(): BankProgress? = read { it.bankProgress }

    fun saveBankProgress(p: BankProgress) = edit { st ->
        p.savedAt = System.currentTimeMillis()
        st.bankProgress = p
    }

    fun clearBankProgress() = edit { st -> st.bankProgress = null }

    /** 每次抽题数量，记住上次选的。 */
    fun bankBatchSize(): Int = read { it.settings.bankBatchSize }

    fun setBankBatchSize(n: Int) = edit { st -> st.settings.bankBatchSize = n }

    fun newId(): String = UUID.randomUUID().toString()

    // ---------------------------------------------------------------- 设备直连同步：整份覆盖导出/导入

    /** 覆盖式同步的数据类：任务/每日记录/三类练习历史/结束日。不含 syncPin/syncPort 等设备本地设置。 */
    private class FullSync {
        var endDate: String? = null
        var tasks: MutableList<TaskDef> = mutableListOf()
        var days: LinkedHashMap<String, DayRecord> = LinkedHashMap()
        var timerSessions: MutableList<TimerSession> = mutableListOf()
        var percentSessions: MutableList<PercentSession> = mutableListOf()
        var formulaSessions: MutableList<FormulaSession> = mutableListOf()
        var mentalMathSessions: MutableList<MentalMathSession> = mutableListOf()
        var pomodoroSessions: MutableList<PomodoroSession> = mutableListOf()
        var bankSessions: MutableList<BankSession> = mutableListOf()
    }

    /** 供「发送我的记录」使用：把本机全部打卡数据序列化成 JSON。 */
    fun exportFull(): String = read { st ->
        val f = FullSync()
        f.endDate = st.settings.endDate
        f.tasks = st.tasks
        f.days = st.days
        f.timerSessions = st.timerSessions
        f.percentSessions = st.percentSessions
        f.formulaSessions = st.formulaSessions
        f.mentalMathSessions = st.mentalMathSessions
        f.pomodoroSessions = st.pomodoroSessions
        f.bankSessions = st.bankSessions
        gson.toJson(f)
    }

    /** 供「接收记录」使用：整份覆盖本机数据（不合并）。调用前应已由 UI 二次确认。 */
    fun importFull(json: String): Boolean {
        val parsed = runCatching { gson.fromJson(json, FullSync::class.java) }.getOrNull() ?: return false
        edit { st ->
            st.settings.endDate = parsed.endDate
            st.tasks = parsed.tasks
            st.days = parsed.days ?: LinkedHashMap()
            st.timerSessions = parsed.timerSessions
            st.percentSessions = parsed.percentSessions
            st.formulaSessions = parsed.formulaSessions
            st.mentalMathSessions = parsed.mentalMathSessions
            st.pomodoroSessions = parsed.pomodoroSessions ?: mutableListOf()
            st.bankSessions = parsed.bankSessions ?: mutableListOf()
            // 强制下次 ensureDays() 从头重建欠账链条，避免沿用本机旧的 lastRolledDate 导致漏滚
            st.lastRolledDate = null
        }
        ensureDays()
        return true
    }

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
