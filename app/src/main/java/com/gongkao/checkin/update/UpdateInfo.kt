package com.gongkao.checkin.update

/**
 * 一个可安装版本的描述。字段与 CI 产出的 manifest.json 一一对应
 * （见 .github/workflows/build.yml 的「打包产物与 manifest」步骤）。
 *
 * [downloadUrl] 不在 manifest 里，是按通道拼出来的：
 *   GitHub 通道 -> release 资产地址
 *   局域网通道 -> http://对端IP:端口/api/apk
 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkName: String,
    val size: Long,
    val sha256: String,
    val notes: String,
    val downloadUrl: String,
    /** 局域网通道才有：来源设备的 IP，用于在界面上说明「从谁那儿更新」 */
    val fromPeer: String? = null
) {
    val sizeText: String
        get() = if (size <= 0) "未知大小"
        else String.format("%.1f MB", size / 1024.0 / 1024.0)
}

/** 检查结果。分开表达「没更新」和「查不到」，界面提示不一样。 */
sealed interface CheckResult {
    data class Found(val info: UpdateInfo) : CheckResult
    data object UpToDate : CheckResult
    data class Failed(val reason: String) : CheckResult
}

/** 下载进度。[total] 为 0 表示服务端没给 Content-Length。 */
data class Progress(val done: Long, val total: Long) {
    val ratio: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    val percent: Int get() = (ratio * 100).toInt()
    val text: String
        get() {
            val d = done / 1024.0 / 1024.0
            return if (total > 0) String.format("%.1f / %.1f MB", d, total / 1024.0 / 1024.0)
            else String.format("%.1f MB", d)
        }
}
