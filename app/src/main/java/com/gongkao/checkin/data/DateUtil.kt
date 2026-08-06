package com.gongkao.checkin.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object DateUtil {
    private val WEEK = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun today(): LocalDate = LocalDate.now()
    fun todayStr(): String = today().toString()

    fun parse(s: String?): LocalDate? =
        s?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun weekOf(d: LocalDate): String = WEEK[d.dayOfWeek.value - 1]

    fun pretty(d: LocalDate): String = "${d.monthValue}月${d.dayOfMonth}日 ${weekOf(d)}"

    fun prettyStr(s: String): String = parse(s)?.let { pretty(it) } ?: s

    fun daysBetween(from: LocalDate, to: LocalDate): Long =
        java.time.temporal.ChronoUnit.DAYS.between(from, to)

    fun clock(ts: Long): String {
        if (ts <= 0) return "--:--"
        val t = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ts), ZoneId.systemDefault())
        return String.format("%02d:%02d", t.hour, t.minute)
    }

    fun clockSec(ts: Long): String {
        if (ts <= 0) return "--:--:--"
        val t = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ts), ZoneId.systemDefault())
        return String.format("%02d:%02d:%02d", t.hour, t.minute, t.second)
    }

    fun dateOf(ts: Long): String =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ts), ZoneId.systemDefault())
            .toLocalDate().toString()

    /** 12:34.56 风格（分:秒.厘秒） */
    fun stopwatch(ms: Long): String {
        val neg = ms < 0
        val v = kotlin.math.abs(ms)
        val h = TimeUnit.MILLISECONDS.toHours(v)
        val m = TimeUnit.MILLISECONDS.toMinutes(v) % 60
        val s = TimeUnit.MILLISECONDS.toSeconds(v) % 60
        val cs = (v % 1000) / 10
        val core = if (h > 0) String.format("%d:%02d:%02d.%02d", h, m, s, cs)
        else String.format("%02d:%02d.%02d", m, s, cs)
        return if (neg) "-$core" else core
    }

    /** 1小时23分 / 3分05秒 风格 */
    fun human(ms: Long): String {
        val s = ms / 1000
        return when {
            s >= 3600 -> "${s / 3600}小时${(s % 3600) / 60}分"
            s >= 60 -> "${s / 60}分${String.format("%02d", s % 60)}秒"
            else -> "${String.format("%.1f", ms / 1000f)}秒"
        }
    }
}
