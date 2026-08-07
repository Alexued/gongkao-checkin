package com.gongkao.checkin.sync

import android.content.Context
import com.gongkao.checkin.BuildConfig
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.security.MessageDigest

/**
 * 把本机安装的 APK 提供给同网段的其它设备，配合 [com.gongkao.checkin.update.Updater.checkLan]。
 *
 * 自己的 base.apk 是可读的（own uid），直接原样发出去；对端拿到后签名与本机一致，
 * 因此能覆盖安装。sha256 现算并缓存，避免每次扫描都重算一遍 5MB。
 */
object ApkServe {

    private var cachedSha: String? = null
    private var cachedSize: Long = -1L

    private fun selfApk(ctx: Context): File? =
        ctx.applicationInfo.sourceDir?.let(::File)?.takeIf { it.isFile }

    fun versionJson(ctx: Context): String {
        val apk = selfApk(ctx)
        val size = apk?.length() ?: 0L
        val sha = apk?.let { sha256(it) } ?: ""
        val name = "gongkao-checkin-v${BuildConfig.VERSION_NAME}.apk"
        return """
            {"versionCode":${BuildConfig.VERSION_CODE},
             "versionName":"${BuildConfig.VERSION_NAME}",
             "apkName":"$name",
             "size":$size,
             "sha256":"$sha",
             "notes":"来自局域网设备"}
        """.trimIndent().replace("\n", "")
    }

    fun serve(ctx: Context): NanoHTTPD.Response {
        val apk = selfApk(ctx)
            ?: return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "找不到安装包"
            )
        val res = NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/vnd.android.package-archive",
            apk.inputStream(),
            apk.length()
        )
        res.addHeader("Content-Disposition", "attachment; filename=\"gongkao-checkin.apk\"")
        return res
    }

    private fun sha256(f: File): String {
        cachedSha?.let { if (cachedSize == f.length()) return it }
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        val hex = md.digest().joinToString("") { "%02x".format(it) }
        cachedSha = hex
        cachedSize = f.length()
        return hex
    }
}
