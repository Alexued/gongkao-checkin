package com.gongkao.checkin.data

/** 建议卡的去向，界面按它决定按钮文案和跳转。 */
enum class SuggestGo { TIMER, REVIEW, TASKS, STATS }

/**
 * 建议卡的五种状态，按优先级判定：欠账 > 今日未完成 > 今天还没复盘 > 已完成 > 没任务。
 * 只挑一件事说，说三件等于没说。文案由界面按 kind 组装，这里只给语义。
 */
enum class SuggestKind { CARRY, NEXT, REVIEW, DONE, EMPTY }

data class Suggestion(
    val kind: SuggestKind,
    /** CARRY/NEXT 时是那条任务的标题，其余为空 */
    val taskTitle: String = "",
    /** CARRY 时是欠账条数，NEXT 时是今日未完成条数 */
    val count: Int = 0,
    val go: SuggestGo
)

/** 双轨进度里的一条。 */
data class Track(val name: String, val done: Int, val total: Int, val ratio: Float)

/** 能力画像里的一格。[detail] 已经是给人看的文案。 */
data class Capability(val name: String, val value: String, val detail: String)

/**
 * 概览页要的计算。全是纯函数，方便对着数据核对，也不用碰 UI。
 */
object Overview {

    /** 建议专注时长的上限，用来算「建议可用」和专注占比。 */
    const val FOCUS_CEILING_MIN = 120

    /** 申论类任务的判定：标题命中这些词就归申论轨。 */
    private val ESSAY = Regex("申论|作文|文章|贯彻|综合分析|提出对策")

    fun focusMinutes(date: String): Int {
        val timer = Repo.timerSessions().filter { it.date == date }.sumOf { it.durationMs }
        val pomo = Repo.pomodoroSessions()
            .filter { it.date == date && it.kind == POMODORO_WORK }
            .sumOf { it.durationMs }
        return ((timer + pomo) / 60000L).toInt()
    }

    /** 当天的复盘记录条数（资料分析技巧复盘）。 */
    fun reviewCount(date: String): Int =
        Repo.bankSessions().count { it.date == date }

    fun suggestion(date: String, mode: AppMode): Suggestion {
        val rec = Repo.day(date)
        val items = rec?.let { Repo.sortedItems(it) } ?: emptyList()

        val carried = items.filter { it.kind == KIND_CARRY && !it.done }
        if (carried.isNotEmpty()) {
            return Suggestion(SuggestKind.CARRY, carried.first().title, carried.size, SuggestGo.TIMER)
        }

        val unfinished = items.filter { !it.done }
        if (unfinished.isNotEmpty()) {
            return Suggestion(SuggestKind.NEXT, unfinished.first().title, unfinished.size, SuggestGo.TIMER)
        }

        // 考公模式才提复盘，通用模式没有这个训练概念
        if (!mode.isGeneral && reviewCount(date) == 0) {
            return Suggestion(SuggestKind.REVIEW, go = SuggestGo.REVIEW)
        }

        return if (items.isEmpty()) {
            Suggestion(SuggestKind.EMPTY, go = SuggestGo.TASKS)
        } else {
            Suggestion(SuggestKind.DONE, go = SuggestGo.STATS)
        }
    }

    /**
     * 双轨进度。考公按标题分行测/申论，通用按「每天重复」分习惯/行动——
     * 都是从已有字段推的，不额外让用户标分类。
     */
    fun tracks(date: String, mode: AppMode, nameA: String, nameB: String): List<Track> {
        val rec = Repo.day(date) ?: return emptyList()
        val items = Repo.sortedItems(rec)
        if (items.isEmpty()) return emptyList()

        val (groupB, groupA) = if (mode.isGeneral) {
            // 通用：每天重复的算习惯，其余算行动
            items.partition { Repo.taskById(it.taskId)?.repeat == REPEAT_DAILY }
        } else {
            items.partition { ESSAY.containsMatchIn(it.title) }
        }
        return listOf(track(nameA, groupA), track(nameB, groupB))
    }

    /** 完成率：全部有计划的天里，完成条目占总条目的比例。 */
    fun doneRatio(): Pair<Int, Int> {
        val days = Repo.recordedDates().mapNotNull { Repo.day(it) }.filter { it.total() > 0 }
        val total = days.sumOf { it.items.size }
        val done = days.sumOf { rec -> rec.items.count { it.done } }
        return done to total
    }

    /** 速算正确率（考公模式用）。 */
    fun mentalMathRatio(): Pair<Int, Int> {
        val s = Repo.mentalMathSessions()
        return s.sumOf { it.knownCount() } to s.sumOf { it.total() }
    }

    fun activeTaskCount(): Int = Repo.read { st -> st.tasks.count { !it.archived } }

    private fun track(name: String, items: List<DayItem>): Track {
        if (items.isEmpty()) return Track(name, 0, 0, 0f)
        val ratio = items.map {
            if (it.target <= 0) 0f else (it.progress.toFloat() / it.target).coerceAtMost(1f)
        }.average().toFloat()
        return Track(name, items.count { it.done }, items.size, ratio)
    }
}
