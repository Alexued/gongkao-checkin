package com.gongkao.checkin.update

import android.app.Activity
import android.app.Dialog
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.gongkao.checkin.R
import com.gongkao.checkin.anim.Motion
import com.gongkao.checkin.sync.LanInfo
import com.gongkao.checkin.ui.AppToast
import com.gongkao.checkin.ui.Popup
import com.gongkao.checkin.ui.inflate
import com.gongkao.checkin.ui.show
import com.gongkao.checkin.ui.tap
import com.gongkao.checkin.view.RingProgressView
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 更新流程界面。检查中 / 发现新版本 / 下载中三个阶段原地换内容，
 * 不连弹三次窗口；动效与其它弹层统一走 [Popup]。
 *
 * 取消只置标志位，下载线程在下一次读循环里自己退出并清残包。
 */
object UpdateDialog {

    /** [lan] 为 true 走局域网通道，否则走 GitHub。 */
    fun start(act: Activity, lan: Boolean) {
        val selfIp = if (lan) LanInfo.ip(act) else null
        if (lan && selfIp == null) {
            AppToast.show(act, act.getString(R.string.update_no_wifi))
            return
        }

        val v = act.inflate(R.layout.dialog_update, null)
        val scrim = v.findViewById<View>(R.id.dialogScrim)
        val card = v.findViewById<View>(R.id.dialogCard)
        val title = v.findViewById<TextView>(R.id.updTitle)
        val sub = v.findViewById<TextView>(R.id.updSub)
        val ringBox = v.findViewById<FrameLayout>(R.id.updRingBox)
        val ring = v.findViewById<RingProgressView>(R.id.updRing)
        val percent = v.findViewById<TextView>(R.id.updPercent)
        val bytes = v.findViewById<TextView>(R.id.updBytes)
        val btnMain = v.findViewById<TextView>(R.id.updPositive)
        val btnClose = v.findViewById<TextView>(R.id.updNegative)

        ring.trackColor = act.getColor(R.color.divider)
        ring.startColor = act.getColor(R.color.accent)
        ring.endColor = act.getColor(R.color.teal)

        title.text = act.getString(if (lan) R.string.update_lan else R.string.update_github)
        sub.text = act.getString(if (lan) R.string.update_scanning else R.string.update_connecting)
        btnClose.text = act.getString(R.string.close)

        val d: Dialog = Popup.dialog(act, v)
        Popup.wireDismiss(d, scrim, card)

        val cancelled = AtomicBoolean(false)
        d.setOnDismissListener { cancelled.set(true) }
        btnClose.tap { Popup.close(d) }

        d.show()
        Popup.enter(scrim, card)

        fun ui(block: () -> Unit) {
            act.runOnUiThread { if (!act.isFinishing && d.isShowing) block() }
        }

        // ---------------- 阶段一：检查 ----------------
        Thread {
            val result =
                if (lan) Updater.checkLan(selfIp!!, LanInfo.port()) else Updater.checkGitHub()
            ui {
                when (result) {
                    is CheckResult.UpToDate ->
                        sub.text = act.getString(R.string.update_latest, Updater.currentName)

                    is CheckResult.Failed -> sub.text = result.reason

                    is CheckResult.Found -> {
                        val info = result.info
                        title.text = act.getString(R.string.update_found, info.versionName)
                        sub.text = buildString {
                            append(act.getString(R.string.update_current, Updater.currentName))
                            append(" · ").append(info.sizeText)
                            info.fromPeer?.let {
                                append('\n').append(act.getString(R.string.update_from_peer, it))
                            }
                            if (info.notes.isNotBlank()) {
                                append("\n\n").append(info.notes.lines().first().take(60))
                            }
                        }
                        btnMain.text = act.getString(R.string.update_download)
                        btnMain.show(true)
                        Motion.touchable(btnMain, 0.96f)
                        btnMain.tap {
                            btnMain.show(false)
                            download(
                                act, info, cancelled, ::ui,
                                title, sub, ringBox, ring, percent, bytes, btnMain
                            )
                        }
                    }
                }
            }
        }.start()
    }

    // ---------------- 阶段二：下载 + 校验 + 安装 ----------------

    private fun download(
        act: Activity,
        info: UpdateInfo,
        cancelled: AtomicBoolean,
        ui: (() -> Unit) -> Unit,
        title: TextView,
        sub: TextView,
        ringBox: FrameLayout,
        ring: RingProgressView,
        percent: TextView,
        bytes: TextView,
        btnMain: TextView
    ) {
        title.text = act.getString(R.string.update_downloading, info.versionName)
        sub.text = info.fromPeer?.let { act.getString(R.string.update_from_peer, it) }
            ?: act.getString(R.string.update_from_github)
        ringBox.show(true)
        bytes.show(true)
        percent.text = "0%"
        bytes.text = act.getString(R.string.update_bytes_start, info.sizeText)

        Thread {
            val file: File? = Updater.download(act, info, { cancelled.get() }) { p ->
                ui {
                    ring.setProgress(p.ratio, animated = false)
                    percent.text = "${p.percent}%"
                    bytes.text = p.text
                }
            }
            ui {
                if (cancelled.get()) return@ui
                if (file == null) {
                    title.text = act.getString(R.string.update_failed)
                    sub.text = act.getString(R.string.update_failed_sub)
                    ringBox.show(false)
                    bytes.show(false)
                    return@ui
                }
                title.text = act.getString(R.string.update_done)
                sub.text = act.getString(R.string.update_done_sub)
                // 安装器可能被用户取消，留个按钮能再点一次
                btnMain.text = act.getString(R.string.update_install)
                btnMain.show(true)
                btnMain.tap { Updater.install(act, file) }
                Updater.install(act, file)
            }
        }.start()
    }
}
