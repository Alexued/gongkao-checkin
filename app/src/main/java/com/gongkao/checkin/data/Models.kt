package com.gongkao.checkin.data

import java.time.LocalDate

const val KIND_TODAY = "TODAY"
const val KIND_CARRY = "CARRY"

const val REPEAT_DAILY = "DAILY"
const val REPEAT_UNTIL = "UNTIL"

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
    var colorIndex: Int = 0
) {
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
    var orphan: Boolean = false
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

data class Settings(
    /** 总结束日（考试日），用于倒计时，也停止生成之后的任务 */
    var endDate: String? = null,
    var syncEnabled: Boolean = false,
    /** 允许被局域网内其它设备发现（设备直连同步用，独立于网页同步开关） */
    var syncDiscoverable: Boolean = false,
    var syncPort: Int = 8765,
    var syncPin: String = "",
    var nickname: String = "考公人"
)

class AppState {
    var schema: Int = 1
    var settings: Settings = Settings()
    var tasks: MutableList<TaskDef> = mutableListOf()
    var days: LinkedHashMap<String, DayRecord> = LinkedHashMap()
    var timerSessions: MutableList<TimerSession> = mutableListOf()
    var percentSessions: MutableList<PercentSession> = mutableListOf()
    var formulaSessions: MutableList<FormulaSession> = mutableListOf()
    var lastRolledDate: String? = null
}
