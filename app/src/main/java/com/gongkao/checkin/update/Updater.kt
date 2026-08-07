package com.gongkao.checkin.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.gongkao.checkin.BuildConfig
import com.google.gson.JsonParser
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 应用内更新。两条通道拿到的都是同一个 [UpdateInfo]，下载与安装共用一套流程。
 *
 * 安装用系统安装器（需要「安装未知应用」授权），不做静默安装。
 * 下载完先核对 sha256，不一致就丢弃 —— 明文 HTTP 下这是唯一的完整性保证。
 */
object Updater {

    private const val REPO = "Alexued/gongkao-checkin"
    private const val TIMEOUT = 12_000

    /** 扫描参数，改动前先看 [checkLan] 里的并发预算说明。 */
    private const val SCAN_THREADS = 64
    private const val PROBE_MS = 300
    private const val READ_MS = 1_500
    private const val SWEEP_S = 8L

    val currentCode: Int get() = BuildConfig.VERSION_CODE
    val currentName: String get() = BuildConfig.VERSION_NAME

    // ------------------------------------------------------------ 通道一：GitHub

    /** 读 latest release 的 manifest.json。阻塞调用，放后台线程。 */
    fun checkGitHub(): CheckResult = runCatching {
        val api = "https://api.github.com/repos/$REPO/releases/latest"
        val body = fetchText(api) ?: return CheckResult.Failed("连不上 GitHub")
        val root = JsonParser.parseString(body).asJsonObject
        val assets = root.getAsJsonArray("assets") ?: return CheckResult.Failed("这个版本没有附件")

        /*
         * 用 assets[].url（api.github.com 域）而不是 browser_download_url
         * （github.com 域）—— 后者在部分网络环境下连不上，而 API 域是通的。
         * 取原文件要带 Accept: application/octet-stream，否则返回的是 asset 元数据 JSON。
         */
        var manifestUrl: String? = null
        var apkUrl: String? = null
        for (e in assets) {
            val o = e.asJsonObject
            val name = o.get("name")?.asString ?: continue
            val url = o.get("url")?.asString ?: continue
            if (name == "manifest.json") manifestUrl = url
            if (name.endsWith(".apk")) apkUrl = url
        }
        if (manifestUrl == null || apkUrl == null) {
            return CheckResult.Failed("发布里缺 manifest 或 APK")
        }

        val mf = fetchText(manifestUrl, accept = OCTET)
            ?: return CheckResult.Failed("读不到版本信息")
        val info = parseManifest(mf, apkUrl) ?: return CheckResult.Failed("版本信息格式不对")
        if (info.versionCode <= currentCode) CheckResult.UpToDate else CheckResult.Found(info)
    }.getOrElse { CheckResult.Failed(it.message ?: "检查失败") }

    // ------------------------------------------------------------ 通道二：局域网

    /**
     * 扫同网段找版本更高的设备。
     *
     * 只扫自己所在的 /24，端口固定用本机同步端口 —— 同一套 app 默认端口一致。
     * 并发 24 条，单个 600ms 超时，整体控制在两秒出头。
     */
    fun checkLan(selfIp: String, port: Int): CheckResult {
        val prefix = selfIp.substringBeforeLast('.', "")
        if (prefix.isEmpty()) return CheckResult.Failed("拿不到本机 IP")
        val selfLast = selfIp.substringAfterLast('.').toIntOrNull() ?: -1

        val hosts = (1..254).filter { it != selfLast }
        val found = java.util.concurrent.ConcurrentHashMap<Int, UpdateInfo>()

        /*
         * 线程数和超时要一起算，不然会把已经连上的那台也一起掐掉：
         *   253 台 × PROBE_MS ÷ 线程数 必须显著小于 SWEEP_S，
         * 否则 invokeAll 超时后 shutdownNow 会打断正在读响应的任务，
         * 结果就是「明明扫到了却报已是最新」。
         * 64 线程 × 300ms ≈ 1.2s，留足余量。
         *
         * 连接超时和读超时分开：死 IP 要快速失败，活着的对端要有时间回数据。
         */
        val pool = java.util.concurrent.Executors.newFixedThreadPool(SCAN_THREADS)
        try {
            val tasks = hosts.map { last ->
                java.util.concurrent.Callable {
                    val ip = "$prefix.$last"
                    val txt = fetchText(
                        "http://$ip:$port/api/version",
                        connectMs = PROBE_MS,
                        readMs = READ_MS
                    ) ?: return@Callable
                    val info = parseManifest(txt, "http://$ip:$port/api/apk", peer = ip)
                        ?: return@Callable
                    if (info.versionCode > currentCode) found[info.versionCode] = info
                }
            }
            pool.invokeAll(tasks, SWEEP_S, java.util.concurrent.TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }

        val best = found.entries.maxByOrNull { it.key }?.value
        return if (best != null) CheckResult.Found(best) else CheckResult.UpToDate
    }

    // ------------------------------------------------------------ 下载 + 校验 + 安装

    /**
     * 下载到应用私有 cache。[onProgress] 在调用线程回调，界面侧自己切主线程。
     * 返回校验通过的文件；失败返回 null 并清掉残包。
     */
    fun download(
        ctx: Context,
        info: UpdateInfo,
        cancelled: () -> Boolean,
        onProgress: (Progress) -> Unit
    ): File? {
        val dir = File(ctx.cacheDir, "update").apply { mkdirs() }
        // 只留当次的包，避免旧残包堆着占空间
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, info.apkName)

        val conn = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            conn.connect()
            if (conn.responseCode !in 200..299) return null
            val total = conn.contentLengthLong.let { if (it > 0) it else info.size }

            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var lastReport = 0L
            conn.inputStream.use { ins ->
                out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelled()) { out.delete(); return null }
                        val n = ins.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        // 节流：每 80ms 或读完时才回调，别让界面被刷爆
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 80) {
                            lastReport = now
                            onProgress(Progress(done, total))
                        }
                    }
                }
            }
            onProgress(Progress(done, total))

            val got = digest.digest().joinToString("") { "%02x".format(it) }
            if (info.sha256.isNotBlank() && !got.equals(info.sha256, ignoreCase = true)) {
                out.delete()
                return null
            }
            return out
        } catch (e: Exception) {
            out.delete()
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** 交给系统安装器。用户没给「安装未知应用」权限时系统会自己引导。 */
    fun install(ctx: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    // ------------------------------------------------------------ 内部

    private fun parseManifest(text: String, apkUrl: String, peer: String? = null): UpdateInfo? =
        runCatching {
            val o = JsonParser.parseString(text).asJsonObject
            UpdateInfo(
                versionCode = o.get("versionCode").asInt,
                versionName = o.get("versionName")?.asString ?: "",
                apkName = o.get("apkName")?.asString ?: "update.apk",
                size = o.get("size")?.asLong ?: 0L,
                sha256 = o.get("sha256")?.asString ?: "",
                notes = o.get("notes")?.asString ?: "",
                downloadUrl = apkUrl,
                fromPeer = peer
            )
        }.getOrNull()

    /** 取 Release 附件原文件要用这个 Accept，不然拿到的是 asset 元数据。 */
    private const val OCTET = "application/octet-stream"

    private fun fetchText(
        url: String,
        connectMs: Int = TIMEOUT,
        readMs: Int = TIMEOUT,
        accept: String = "application/json"
    ): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectMs
            readTimeout = readMs
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
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
