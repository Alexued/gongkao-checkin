package com.gongkao.checkin.sync

import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.KIND_CARRY
import com.gongkao.checkin.data.REPEAT_UNTIL
import com.gongkao.checkin.data.Repo
import com.google.gson.Gson
import com.google.gson.GsonBuilder

/** 网页端需要的数据快照，字段扁平化，避免前端处理复杂结构。 */
object StateJson {

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    fun build(): String {
        val map = LinkedHashMap<String, Any?>()
        Repo.ensureDays()
        Repo.read { st ->
            val today = Repo.today()
            map["revision"] = Repo.revision
            map["date"] = today.date
            map["dateText"] = DateUtil.prettyStr(today.date)
            map["nickname"] = st.settings.nickname
            map["endDate"] = st.settings.endDate
            map["daysLeft"] = Repo.daysLeft()
            map["streak"] = Repo.streak()
            map["total"] = today.total()
            map["finished"] = today.finished()
            map["ratio"] = today.ratio()
            map["allDone"] = today.allDone()

            map["items"] = Repo.sortedItems(today).map { it ->
                mapOf(
                    "key" to it.key,
                    "title" to it.title,
                    "carry" to (it.kind == KIND_CARRY),
                    "target" to it.target,
                    "progress" to it.progress,
                    "done" to it.done,
                    "unit" to it.unit,
                    "colorIndex" to it.colorIndex,
                    "debtDays" to debtDays(it.oldestDebtDate),
                    "oldestDebtDate" to it.oldestDebtDate,
                    "doneAt" to (if (it.doneAt > 0) DateUtil.clock(it.doneAt) else null)
                )
            }

            map["tasks"] = st.tasks.filter { !it.archived }.sortedBy { it.order }.map {
                mapOf(
                    "id" to it.id,
                    "title" to it.title,
                    "repeat" to it.repeat,
                    "untilDate" to it.untilDate,
                    "target" to it.target,
                    "deadline" to it.deadlineText()
                )
            }

            map["timer"] = st.timerSessions.take(12).map {
                mapOf(
                    "id" to it.id,
                    "label" to it.label,
                    "date" to it.date,
                    "duration" to DateUtil.stopwatch(it.durationMs),
                    "durationMs" to it.durationMs,
                    "laps" to it.lapCount(),
                    "avg" to DateUtil.stopwatch(it.avgLapMs()),
                    "startAt" to DateUtil.clock(it.startAt)
                )
            }

            map["percent"] = st.percentSessions.take(12).map {
                mapOf(
                    "id" to it.id,
                    "date" to it.date,
                    "mode" to if (it.mode == "FULL") "完整背诵" else "随机抽取",
                    "total" to it.total(),
                    "correct" to it.correctCount(),
                    "accuracy" to Math.round(it.accuracy() * 100),
                    "avg" to DateUtil.human(it.avgMs())
                )
            }

            map["formula"] = st.formulaSessions.take(12).map {
                mapOf(
                    "id" to it.id,
                    "date" to it.date,
                    "mode" to if (it.mode == "FULL") "完整背诵" else "随机抽取",
                    "category" to it.category,
                    "total" to it.total(),
                    "known" to it.knownCount(),
                    "accuracy" to Math.round(it.accuracy() * 100)
                )
            }

            // 近 21 天完成率，网页端画迷你条形图
            map["recent"] = (0 until 21).map { i ->
                val d = DateUtil.today().minusDays((20 - i).toLong()).toString()
                val rec = st.days[d]
                mapOf(
                    "date" to d,
                    "ratio" to (rec?.ratio() ?: 0f),
                    "total" to (rec?.total() ?: 0),
                    "finished" to (rec?.finished() ?: 0)
                )
            }

            map["hasUntilTask"] = st.tasks.any { it.repeat == REPEAT_UNTIL && !it.archived }
        }
        return gson.toJson(map)
    }

    private fun debtDays(oldest: String?): Int {
        val d = DateUtil.parse(oldest) ?: return 0
        return DateUtil.daysBetween(d, DateUtil.today()).toInt().coerceAtLeast(0)
    }

    fun dayDetail(date: String): String {
        val map = LinkedHashMap<String, Any?>()
        Repo.read { st ->
            val rec = st.days[date]
            map["date"] = date
            map["dateText"] = DateUtil.prettyStr(date)
            map["items"] = rec?.items?.map {
                mapOf(
                    "title" to it.title,
                    "carry" to (it.kind == KIND_CARRY),
                    "progress" to it.progress,
                    "target" to it.target,
                    "done" to it.done,
                    "doneAt" to (if (it.doneAt > 0) DateUtil.clock(it.doneAt) else null)
                )
            } ?: emptyList<Any>()
            map["timer"] = st.timerSessions.filter { it.date == date }.map {
                mapOf(
                    "label" to it.label,
                    "duration" to DateUtil.stopwatch(it.durationMs),
                    "laps" to it.lapCount(),
                    "avg" to DateUtil.stopwatch(it.avgLapMs()),
                    "startAt" to DateUtil.clock(it.startAt)
                )
            }
            map["percent"] = st.percentSessions.filter { it.date == date }.map {
                mapOf(
                    "mode" to if (it.mode == "FULL") "完整背诵" else "随机抽取",
                    "total" to it.total(),
                    "correct" to it.correctCount(),
                    "accuracy" to Math.round(it.accuracy() * 100),
                    "avg" to DateUtil.human(it.avgMs())
                )
            }
            map["formula"] = st.formulaSessions.filter { it.date == date }.map {
                mapOf(
                    "mode" to if (it.mode == "FULL") "完整背诵" else "随机抽取",
                    "category" to it.category,
                    "total" to it.total(),
                    "known" to it.knownCount(),
                    "accuracy" to Math.round(it.accuracy() * 100)
                )
            }
        }
        return gson.toJson(map)
    }
}
