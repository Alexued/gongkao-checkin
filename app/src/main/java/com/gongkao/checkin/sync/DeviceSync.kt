package com.gongkao.checkin.sync

import com.gongkao.checkin.data.Repo
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 设备直连同步的客户端一侧：把本机数据发给对方，或者拉对方数据整份覆盖本机。
 * 和 DeviceScan 一样是阻塞调用，调用方自己负责放到后台线程。
 */
object DeviceSync {

    private const val CONNECT_MS = 3000
    private const val READ_MS = 8000

    private val gson = Gson()

    /** 把本机全部数据 POST 给对方（带 PIN）。 */
    fun sendFull(device: FoundDevice, pin: String): Boolean {
        val data = Repo.exportFull()
        val body = "{\"pin\":" + gson.toJson(pin) + ",\"data\":" + gson.toJson(data) + "}"
        val url = "http://" + device.ip + ":" + device.port + "/api/full"
        val resp = postJson(url, body) ?: return false
        return runCatching {
            JsonParser.parseString(resp).asJsonObject.get("ok")?.asBoolean == true
        }.getOrDefault(false)
    }

    /** 从对方 GET 全部数据（带 PIN），拉回来后整份覆盖本机。 */
    fun receiveFull(device: FoundDevice, pin: String): Boolean {
        val pinEnc = URLEncoder.encode(pin, "UTF-8")
        val url = "http://" + device.ip + ":" + device.port + "/api/full?pin=" + pinEnc
        val text = getText(url) ?: return false
        return Repo.importFull(text)
    }

    private fun getText(url: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_MS
        conn.readTimeout = READ_MS
        conn.setRequestProperty("Accept", "application/json")
        try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun postJson(url: String, body: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = CONNECT_MS
        conn.readTimeout = READ_MS
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
