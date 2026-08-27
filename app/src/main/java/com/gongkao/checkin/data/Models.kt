package com.gongkao.checkin.data

import java.time.LocalDate

const val KIND_TODAY = "TODAY"
const val KIND_CARRY = "CARRY"

const val REPEAT_DAILY = "DAILY"
const val REPEAT_UNTIL = "UNTIL"

/** 任务下的一步。只有标题，完成状态是按天记的（见 [DayItem.doneSubtasks]）。 */
data class Subtask(
    var id: String = "",
    var title: String = ""
)

/** 任务定义。repeat=DAILY 每天都要；repeat=UNTIL 每天都要但到 untilDate（含）截止。 */
data class TaskDef(
    var id: String = "",
    var title: String = "",
    var repeat: String = REPEAT_DAILY,
    var untilDate: String? = null,
    var startDate: String = "",
    var target: Int = 1,
    var unit: String = "",
    var order: Int = 0,
    var archived: Boolean = false,
    var colorIndex: Int = 0,
    /** 拆出来的步骤。非空时 [target] 由步骤数决定、[unit] 不用（编辑器里也会隐藏这两项）。 */
    var subtasks: MutableList<Subtask> = mutableListOf()
) {
    val hasSubtasks: Boolean get() = subtasks.isNotEmpty()

    /** 有步骤时目标数就是步骤数，否则用手填的 target。 */
    fun effectiveTarget(): Int =
        if (hasSubtasks) subtasks.size else target.coerceAtLeast(1)

    fun appliesTo(date: LocalDate, globalEnd: LocalDate?): Boolean {
        if (archived) return false
        val s = runCatching { LocalDate.parse(startDate) }.getOrNull() ?: return false
        if (date.isBefore(s)) return false
        if (repeat == REPEAT_UNTIL) {
            val u = untilDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
            if (date.isAfter(u)) return false
        }
        if (globalEnd != null && date.isAfter(globalEnd)) return false
        return true
    }

    fun deadlineText(): String = when (repeat) {
        REPEAT_UNTIL -> "截止 " + (untilDate ?: "-")
        else -> "每天"
    }
}

/** 某一天里的一条待办。kind=CARRY 表示昨天（及更早）没做完累加过来的。 */
data class DayItem(
    var taskId: String = "",
    var title: String = "",
    var kind: String = KIND_TODAY,
    var target: Int = 1,
    var progress: Int = 0,
    var unit: String = "",
    var colorIndex: Int = 0,
    var doneAt: Long = 0L,
    /** 最早欠账的那一天，用于显示「欠了几天」 */
    var oldestDebtDate: String? = null,
    /** 任务定义已删除，只保留补做条目 */
    var orphan: Boolean = false,
    /**
     * 当天勾掉的步骤 id。只存 id 不存标题，改名后显示自动跟着变；
     * 步骤被删掉留下的陈旧 id 由 buildDay 清掉。欠账条目不带步骤（只结转数量）。
     */
    var doneSubtasks: MutableList<String> = mutableListOf()
) {
    val done: Boolean get() = progress >= target
    val remaining: Int get() = (target - progress).coerceAtLeast(0)
    val key: String get() = "$kind#$taskId"
}

data class DayRecord(
    var date: String = "",
    var items: MutableList<DayItem> = mutableListOf(),
    var celebrated: Boolean = false
) {
    fun total() = items.sumOf { it.target }
    fun finished() = items.sumOf { it.progress.coerceAtMost(it.target) }
    fun allDone() = items.isNotEmpty() && items.all { it.done }
    fun ratio(): Float {
        val t = total()
        return if (t <= 0) 0f else finished().toFloat() / t
    }
}

data class Lap(
    var index: Int = 0,
    /** 从开始计时起的累计毫秒 */
    var atMs: Long = 0,
    /** 与上一个点的间隔毫秒 */
    var splitMs: Long = 0,
    var note: String = ""
)

data class TimerSession(
    var id: String = "",
    var label: String = "",
    var date: String = "",
    var startAt: Long = 0,
    var endAt: Long = 0,
    /** 净运行时长，不含暂停 */
    var durationMs: Long = 0,
    var laps: MutableList<Lap> = mutableListOf()
) {
    fun lapCount() = laps.size
    fun avgLapMs(): Long = if (laps.isEmpty()) 0 else laps.sumOf { it.splitMs } / laps.size
    fun fastestLap(): Lap? = laps.minByOrNull { it.splitMs }
    fun slowestLap(): Lap? = laps.maxByOrNull { it.splitMs }
}

data class PercentItem(
    var display: String = "",
    var expectNum: Int = 0,
    var expectDen: Int = 1,
    var answerNum: Int = 0,
    var answerDen: Int = 0,
    var correct: Boolean = false,
    var ms: Long = 0
)

data class PercentSession(
    var id: String = "",
    /** FULL 完整背诵 / RANDOM 随机抽取 */
    var mode: String = "FULL",
    var date: String = "",
    var startAt: Long = 0,
    var endAt: Long = 0,
    var items: MutableList<PercentItem> = mutableListOf()
) {
    fun total() = items.size
    fun correctCount() = items.count { it.correct }
    fun accuracy(): Float = if (items.isEmpty()) 0f else correctCount().toFloat() / items.size
    fun durationMs(): Long = items.sumOf { it.ms }
    fun avgMs(): Long = if (items.isEmpty()) 0 else durationMs() / items.size
}

data class FormulaItemRecord(
    var formulaId: String = "",
    var title: String = "",
    /** true=记住了 false=模糊 */
    var known: Boolean = false,
    var ms: Long = 0
)

data class FormulaSession(
    var id: String = "",
    var mode: String = "FULL",
    var category: String = "全部",
    var date: String = "",
    var startAt: Long = 0,
    var endAt: Long = 0,
    var items: MutableList<FormulaItemRecord> = mutableListOf()
) {
    fun total() = items.size
    fun knownCount() = items.count { it.known }
    fun accuracy(): Float = if (items.isEmpty()) 0f else knownCount().toFloat() / items.size
    fun durationMs(): Long = items.sumOf { it.ms }
}

data class MentalMathItemRecord(
    var mentalMathId: String = "",
    var title: String = "",
    /** true=记住了 false=模糊 */
    var known: Boolean = false,
    var ms: Long = 0
)

data class MentalMathSession(
    var id: String = "",
    var mode: String = "FULL",
    var category: String = "全部",
    var date: String = "",
    var startAt: Long = 0,
    var endAt: Long = 0,
    var items: MutableList<MentalMathItemRecord> = mutableListOf()
) {
    fun total() = items.size
    fun knownCount() = items.count { it.known }
    fun accuracy(): Float = if (items.isEmpty()) 0f else knownCount().toFloat() / items.size
    fun durationMs(): Long = items.sumOf { it.ms }
}

data class BankItemRecord(
    var bankId: String = "",
    /** 题干摘要，记录页直接显示，免得回头查题库 */
    var title: String = "",
    /** 用户选的选项字母，跳过则为空串 */
    var picked: String = "",
    var answer: String = "",
    var correct: Boolean = false,
    var ms: Long = 0
)

/** 一轮资料分析技巧复盘。不进每日统计，只在背诵页与记录页汇总。 */
data class BankSession(
    var id: String = "",
    var mode: String = "FULL",
    var chapter: String = "全部",
    /** 题目出自哪个题库（BankSource.id），复盘页要靠它回查题面。老记录没有这个字段，按精选算 */
    var sourceId: String = "curated",
    var date: String = "",
    var startAt: Long = 0,
    var endAt: Long = 0,
    var items: MutableList<BankItemRecord> = mutableListOf()
) {
    fun total() = items.size
    fun correctCount() = items.count { it.correct }
    fun accuracy(): Float = if (items.isEmpty()) 0f else correctCount().toFloat() / items.size
    fun durationMs(): Long = items.sumOf { it.ms }
}

/**
 * 一份没做完的复盘存档。只留一份——「继续做题」是接着上次那次，
 * 存多份就得让用户选，反而更麻烦。
 */
data class BankProgress(
    var sourceId: String = "curated",
    var chapter: String = "全部",
    /** 抽出来的题 id，顺序就是做题顺序，续做时照原顺序 */
    var questionIds: MutableList<String> = mutableListOf(),
    /** 已作答：题 id → 选的选项字母 */
    var answers: MutableMap<String, String> = mutableMapOf(),
    /** 上次停在第几题（下标） */
    var cursor: Int = 0,
    var startAt: Long = 0,
    var savedAt: Long = 0
) {
    fun total() = questionIds.size
    fun answered() = answers.size
    fun isEmpty() = questionIds.isEmpty()
}

const val POMODORO_WORK = "WORK"
const val POMODORO_SHORT_BREAK = "SHORT_BREAK"
const val POMODORO_LONG_BREAK = "LONG_BREAK"

/** 一段番茄钟（专注或休息）。completed=false 表示中途放弃/跳过，未计满设定时长。 */
data class PomodoroSession(
    var id: String = "",
    /** WORK / SHORT_BREAK / LONG_BREAK */
    var kind: String = POMODORO_WORK,
    var date: String = "",
    var startAt: Long = 0,
    var endAt: Long = 0,
    /** 实际计时的毫秒数，可能小于该阶段设定时长（提前结束时） */
    var durationMs: Long = 0,
    var completed: Boolean = true
)

data class Settings(
    /** 总结束日（考试日），用于倒计时，也停止生成之后的任务 */
    var endDate: String? = null,
    /** 允许被局域网内其它设备发现（设备直连同步用） */
    var syncDiscoverable: Boolean = false,
    var syncPort: Int = 8765,
    var syncPin: String = "",
    var nickname: String = "考公人",
    /** 复盘用哪个题库（BankSource.id） */
    var bankSourceId: String = "curated",
    /** 复盘用哪种讲解风格（ReviewSkill.id） */
    var reviewSkillId: String = "builtin",
    /** 使用模式（AppMode.id）：exam=考公，general=通用 */
    var appMode: String = "exam",
    /** 复盘每次抽几题 */
    var bankBatchSize: Int = 10,
    /** 界面主题（AppTheme.id）：light / dark / blur / liquid */
    var themeId: String = "light",
    /** 日历上标记的重要日/考试日，yyyy-MM-dd */
    var markDate: String? = null
)

class AppState {
    var schema: Int = 1
    var settings: Settings = Settings()
    var tasks: MutableList<TaskDef> = mutableListOf()
    var days: LinkedHashMap<String, DayRecord> = LinkedHashMap()
    var timerSessions: MutableList<TimerSession> = mutableListOf()
    var percentSessions: MutableList<PercentSession> = mutableListOf()
    var formulaSessions: MutableList<FormulaSession> = mutableListOf()
    var mentalMathSessions: MutableList<MentalMathSession> = mutableListOf()
    var pomodoroSessions: MutableList<PomodoroSession> = mutableListOf()
    var bankSessions: MutableList<BankSession> = mutableListOf()
    /** 没做完的复盘存档，null 表示没有；「继续做题」按钮按它显隐 */
    var bankProgress: BankProgress? = null
    var lastRolledDate: String? = null
}
