package com.gongkao.checkin.sync

import android.content.Context
import com.gongkao.checkin.data.Repo
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Method

/**
 * 局域网服务端。只剩两件事：**局域网更新**（报版本、供安装包）和
 * **设备直连同步**（被发现、整份收发）。电脑端网页同步已移除。
 *
 * 安全说明：明文 HTTP，仅限局域网访问，整份收发用 4 位 PIN 做最低限度校验。
 * 同一 WiFi 下拿到 PIN 的人可以覆盖打卡数据，不要在公共 WiFi 下开「允许被发现」。
 */
class WebServer(private val ctx: Context, port: Int) : NanoHTTPD("0.0.0.0", port) {

    private val gson = Gson()

    fun startUp() {
        start(SOCKET_READ_TIMEOUT, true)
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        return runCatching {
            when {
                // 局域网更新用：报版本 + 供包。不加 PIN —— 只是自己的安装包，
                // 而且对端要靠这两个接口发现「谁的版本更新」。
                uri == "/api/version" -> json(ApkServe.versionJson(ctx))
                uri == "/api/apk" -> ApkServe.serve(ctx)
                // 设备直连发现：不加 PIN，只有「允许被发现」开着才应答，
                // 否则扫描方连一个「关闭」的应答都拿不到，等同于探测不到这台设备。
                uri == "/api/discover" -> discover()
                // 设备直连整份覆盖同步：GET 导出本机数据，POST 用请求体整份覆盖本机数据。
                uri == "/api/full" && session.method == Method.GET -> guarded(session) {
                    json(Repo.exportFull())
                }
                uri == "/api/full" && session.method == Method.POST -> guarded(session) { importFull(session) }
                else -> notFound()
            }
        }.getOrElse { e ->
            json("""{"ok":false,"error":${gson.toJson(e.message ?: "error")}}""")
        }
    }

    /** PIN 校验：query ?pin= 或 header X-Pin。 */
    private inline fun guarded(session: IHTTPSession, block: () -> Response): Response {
        val expect = Repo.read { it.settings.syncPin }
        if (expect.isBlank()) return block()
        val got = session.parameters["pin"]?.firstOrNull()
            ?: session.headers["x-pin"]
            ?: bodyPin(session)
        if (got != expect) {
            return newFixedLengthResponse(
                Response.Status.UNAUTHORIZED, "application/json; charset=utf-8",
                """{"ok":false,"error":"PIN 不正确","needPin":true}"""
            ).also { it.addHeader("Access-Control-Allow-Origin", "*") }
        }
        return block()
    }

    private fun bodyPin(session: IHTTPSession): String? = runCatching {
        readBody(session)?.get("pin")?.asString
    }.getOrNull()

    private val bodyCache = HashMap<IHTTPSession, JsonObject?>()

    private fun readBody(session: IHTTPSession): JsonObject? {
        if (bodyCache.containsKey(session)) return bodyCache[session]
        val parsed = runCatching {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val raw = files["postData"] ?: return@runCatching null
            JsonParser.parseString(raw).asJsonObject
        }.getOrNull()
        bodyCache[session] = parsed
        return parsed
    }

    /** 设备直连发现的应答：昵称 + 版本，供扫描方在列表里认出这台设备。 */
    private fun discover(): Response {
        val discoverable = Repo.read { it.settings.syncDiscoverable }
        if (!discoverable) return notFound()
        val nickname = Repo.read { it.settings.nickname }
        return json(
            """{"ok":true,"nickname":${gson.toJson(nickname)},"versionName":"${com.gongkao.checkin.BuildConfig.VERSION_NAME}"}"""
        )
    }

    private fun importFull(session: IHTTPSession): Response {
        val body = readBody(session) ?: return json("""{"ok":false,"error":"缺少请求体"}""")
        val data = body.get("data")?.asString
        if (data.isNullOrBlank()) return json("""{"ok":false,"error":"缺少 data"}""")
        val done = Repo.importFull(data)
        if (!done) return json("""{"ok":false,"error":"数据格式不对"}""")
        return json("""{"ok":true,"revision":${Repo.revision}}""")
    }

    private fun json(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body)
            .also {
                it.addHeader("Access-Control-Allow-Origin", "*")
                it.addHeader("Cache-Control", "no-store")
            }

    private fun notFound(): Response =
        newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "404")
}
