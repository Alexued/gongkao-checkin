package com.gongkao.checkin.sync

import android.content.Context
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.REPEAT_DAILY
import com.gongkao.checkin.data.REPEAT_UNTIL
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.data.TaskDef
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fi.iki.elonen.NanoHTTPD

/**
 * 局域网同步服务端。
 *
 * 安全说明：明文 HTTP，仅限局域网访问，用 4 位 PIN 做最低限度校验。
 * 同一 WiFi 下拿到 PIN 的人可以读写打卡数据，不要在公共 WiFi 下开启。
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
                uri == "/" || uri == "/index.html" -> html(WebAssets.page())
                uri == "/api/ping" -> json("""{"ok":true,"revision":${Repo.revision}}""")
                // 局域网更新用：报版本 + 供包。不加 PIN —— 只是自己的安装包，
                // 而且对端要靠这两个接口发现「谁的版本更新」。
                uri == "/api/version" -> json(ApkServe.versionJson(ctx))
                uri == "/api/apk" -> ApkServe.serve(ctx)
                uri == "/api/state" -> guarded(session) { json(StateJson.build()) }
                uri == "/api/day" -> guarded(session) {
                    val d = session.parameters["date"]?.firstOrNull() ?: DateUtil.todayStr()
                    json(StateJson.dayDetail(d))
                }
                uri == "/api/action" -> guarded(session) { action(session) }
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

    private fun action(session: IHTTPSession): Response {
        val body = readBody(session) ?: return json("""{"ok":false,"error":"缺少请求体"}""")
        val what = body.get("action")?.asString ?: return json("""{"ok":false,"error":"缺少 action"}""")
        Repo.ensureDays()
        when (what) {
            "bump" -> {
                val key = body.get("key")?.asString ?: return bad("key")
                val delta = body.get("delta")?.asInt ?: 1
                val date = body.get("date")?.asString ?: DateUtil.todayStr()
                Repo.bump(date, key, delta)
            }
            "addTask" -> {
                val title = body.get("title")?.asString?.trim().orEmpty()
                if (title.isEmpty()) return bad("title")
                val repeat = body.get("repeat")?.asString ?: REPEAT_DAILY
                val until = body.get("untilDate")?.takeIf { !it.isJsonNull }?.asString
                val target = body.get("target")?.asInt ?: 1
                if (repeat == REPEAT_UNTIL && until.isNullOrBlank()) return bad("untilDate")
                Repo.upsertTask(
                    TaskDef(
                        title = title, repeat = repeat, untilDate = until,
                        startDate = DateUtil.todayStr(), target = target.coerceAtLeast(1),
                        colorIndex = Repo.read { it.tasks.size } % 6
                    )
                )
            }
            "deleteTask" -> {
                val id = body.get("id")?.asString ?: return bad("id")
                Repo.deleteTask(id)
            }
            "setEndDate" -> {
                val d = body.get("date")?.takeIf { !it.isJsonNull }?.asString
                Repo.setEndDate(d?.takeIf { it.isNotBlank() })
            }
            else -> return json("""{"ok":false,"error":"未知 action"}""")
        }
        return json("""{"ok":true,"revision":${Repo.revision}}""")
    }

    private fun bad(field: String) = json("""{"ok":false,"error":"缺少字段 $field"}""")

    private fun json(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body)
            .also {
                it.addHeader("Access-Control-Allow-Origin", "*")
                it.addHeader("Cache-Control", "no-store")
            }

    private fun html(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)
            .also { it.addHeader("Cache-Control", "no-store") }

    private fun notFound(): Response =
        newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "404")
}
