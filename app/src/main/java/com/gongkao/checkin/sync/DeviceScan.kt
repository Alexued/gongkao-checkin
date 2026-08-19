package com.gongkao.checkin.sync

import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URL

/** 局域网内一台开着「允许被发现」的设备。 */
data class FoundDevice(
    val ip: String,
    val port: Int,
    val nickname: String,
    val versionName: String
)

/**
 * 设备直连发现：扫同网段找应答了 /api/discover 的设备。
 *
 * 做法和 [com.gongkao.checkin.update.Updater.checkLan] 一致（同一套并发预算），
 * 只是探测的接口和解析的字段不同。
 */
object DeviceScan {

    private const val SCAN_THREADS = 64
    private const val PROBE_MS = 300
    private const val READ_MS = 1_500
    private const val SWEEP_S = 8L

    /** 扫本机所在的 /24 网段，返回所有应答了 discover 接口的设备。阻塞调用，放后台线程。 */
    fun scan(selfIp: String, port: Int): List<FoundDevice> {
        val prefix = selfIp.substringBeforeLast('.', "")
        if (prefix.isEmpty()) return emptyList()
        val selfLast = selfIp.substringAfterLast('.').toIntOrNull() ?: -1

        val hosts = (1..254).filter { it != selfLast }
        val found = java.util.concurrent.ConcurrentHashMap<String, FoundDevice>()

        val pool = java.util.concurrent.Executors.newFixedThreadPool(SCAN_THREADS)
        try {
            val tasks = hosts.map { last ->
                java.util.concurrent.Callable {
                    val ip = "$prefix.$last"
                    val txt = fetchText(
                        "http://$ip:$port/api/discover",
                        connectMs = PROBE_MS,
                        readMs = READ_MS
                    ) ?: return@Callable
                    val device = parse(txt, ip, port) ?: return@Callable
                    found[ip] = device
                }
            }
            pool.invokeAll(tasks, SWEEP_S, java.util.concurrent.TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }

        return found.values.sortedBy { it.ip }
    }

    private fun parse(text: String, ip: String, port: Int): FoundDevice? = runCatching {
        val o = JsonParser.parseString(text).asJsonObject
        if (o.get("ok")?.asBoolean != true) return null
        FoundDevice(
            ip = ip,
            port = port,
            nickname = o.get("nickname")?.asString ?: ip,
            versionName = o.get("versionName")?.asString ?: ""
        )
    }.getOrNull()

    private fun fetchText(url: String, connectMs: Int, readMs: Int): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectMs
            readTimeout = readMs
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "gongkao-checkin")
        }
        try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
