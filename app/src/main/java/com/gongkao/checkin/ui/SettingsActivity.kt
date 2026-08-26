package com.gongkao.checkin.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.LinearLayout
import android.widget.TextView
import com.gongkao.checkin.BuildConfig
import com.gongkao.checkin.R
import com.gongkao.checkin.data.AppMode
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.sync.DeviceScan
import com.gongkao.checkin.sync.DeviceSync
import com.gongkao.checkin.sync.FoundDevice
import com.gongkao.checkin.sync.LanInfo
import com.gongkao.checkin.sync.SyncService
import java.time.LocalDate

/** 设置：总结束日 + 局域网网页同步 + 关于。 */
class SettingsActivity : ListScreen() {

    override fun title(): CharSequence = getString(R.string.settings)

    override fun build() {
        modeSection()
        endDateSection()
        syncSection()
        deviceSyncSection()
        updateSection()
        aboutSection()
    }

    // ------------------------------------------------------------ 使用模式

    /**
     * 考公 / 通用 二选一。切换只改界面和统计口径，一条业务数据都不动，
     * 所以不需要二次确认。切完要重建 MainActivity（tab 数量会变），故直接重启回首页。
     */
    private fun modeSection() {
        section(getString(R.string.mode_section))
        val mode = Repo.appMode()
        row(
            title = getString(R.string.mode_exam),
            sub = getString(R.string.mode_exam_sub),
            value = if (mode == AppMode.EXAM) "✓" else null
        ) { switchMode(AppMode.EXAM) }
        row(
            title = getString(R.string.mode_general),
            sub = getString(R.string.mode_general_sub),
            value = if (mode == AppMode.GENERAL) "✓" else null
        ) { switchMode(AppMode.GENERAL) }
        hint(getString(R.string.mode_hint))
    }

    private fun switchMode(target: AppMode) {
        if (Repo.appMode() == target) return
        Repo.setAppMode(target)
        val name = getString(if (target.isGeneral) R.string.mode_general else R.string.mode_exam)
        toast(getString(R.string.mode_switched, name))
        rebuild()
        // tab 数量随模式变，回到首页重建一次才对得上
        startActivity(
            android.content.Intent(this, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    // ------------------------------------------------------------ 更新

    private fun updateSection() {
        section(getString(R.string.update_title))
        row(
            title = getString(R.string.update_github),
            sub = getString(R.string.update_github_sub),
            chevron = true
        ) { com.gongkao.checkin.update.UpdateDialog.start(this, lan = false) }
        row(
            title = getString(R.string.update_lan),
            sub = getString(R.string.update_lan_sub),
            chevron = true
        ) { com.gongkao.checkin.update.UpdateDialog.start(this, lan = true) }
    }

    // ------------------------------------------------------------ 结束日

    private fun endDateSection() {
        section(getString(R.string.setting_end_date))
        val end = Repo.read { it.settings.endDate }
        row(
            title = end?.let { DateUtil.prettyStr(it) } ?: getString(R.string.setting_not_set),
            sub = getString(R.string.setting_end_date_sub),
            value = if (end != null) getString(R.string.setting_clear) else null,
            chevron = end == null
        ) {
            if (end != null) {
                Repo.setEndDate(null)
            } else {
                pickEndDate()
            }
        }
        // 已设置时再给一行改日期的入口，避免右边「清除」把改期挡住
        if (end != null) {
            row(title = getString(R.string.pick_until_date), chevron = true) { pickEndDate() }
        }
    }

    private fun pickEndDate() {
        val cur = Repo.read { it.settings.endDate }
        DatePicker.show(
            ctx = this,
            current = cur,
            title = getString(R.string.setting_end_date),
            allowClear = cur != null,
            onPick = { Repo.setEndDate(it) },
            onClear = { Repo.setEndDate(null) }
        )
    }

    // ------------------------------------------------------------ 局域网同步

    private fun syncSection() {
        section(getString(R.string.sync_title))
        val on = Repo.read { it.settings.syncEnabled }
        val pin = Repo.read { it.settings.syncPin }
        val ip = LanInfo.ip(this)

        row(
            title = getString(R.string.sync_title),
            sub = getString(R.string.sync_sub),
            value = getString(if (on) R.string.sync_on else R.string.sync_off),
            chevron = true
        ) { toggleSync(!on) }

        if (on) {
            val box = card()
            kv(getString(R.string.sync_url), ip?.let { "http://$it:${LanInfo.port()}" }
                ?: getString(R.string.sync_no_wifi), box)
            kv(getString(R.string.sync_port), LanInfo.port().toString(), box)
            kv(getString(R.string.sync_pin), pin, box)
            if (ip != null) {
                row(title = getString(R.string.sync_url), value = getString(R.string.copy)) {
                    copy("http://$ip:${LanInfo.port()}")
                }
            }
            row(title = getString(R.string.sync_pin), value = getString(R.string.sync_pin_new)) {
                Repo.edit { st -> st.settings.syncPin = (1000..9999).random().toString() }
            }
        }
        warn(getString(R.string.sync_warn))
    }

    private fun toggleSync(on: Boolean) {
        Repo.edit { st -> st.settings.syncEnabled = on }
        syncServiceRefresh()
    }

    /** 网页同步、设备直连发现共用同一个前台服务；任意一个开着就要常驻。 */
    private fun syncServiceRefresh() {
        val st = Repo.read { it.settings }
        if (st.syncEnabled || st.syncDiscoverable) SyncService.start(this) else SyncService.stop(this)
    }

    private fun copy(text: String) {
        val cm = getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("url", text))
        toast(getString(R.string.sync_copied))
    }

    // ------------------------------------------------------------ 设备直连同步

    private var scanning = false

    private fun deviceSyncSection() {
        section(getString(R.string.device_sync_title))
        val discoverable = Repo.read { it.settings.syncDiscoverable }

        row(
            title = getString(R.string.device_sync_discoverable),
            sub = getString(R.string.device_sync_discoverable_sub),
            value = getString(if (discoverable) R.string.sync_on else R.string.sync_off),
            chevron = true
        ) {
            Repo.edit { st -> st.settings.syncDiscoverable = !discoverable }
            syncServiceRefresh()
        }

        row(
            title = getString(R.string.device_sync_scan),
            sub = if (scanning) getString(R.string.device_sync_scanning) else null,
            chevron = true
        ) { startScan() }

        row(
            title = getString(R.string.device_sync_show_qr),
            chevron = true
        ) { showMyQr() }

        row(
            title = getString(R.string.device_sync_scan_qr),
            chevron = true
        ) { scanQrLauncher.launch(android.content.Intent(this, com.gongkao.checkin.sync.QrScanActivity::class.java)) }
    }

    private val scanQrLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@registerForActivityResult
        val ip = data.getStringExtra(com.gongkao.checkin.sync.QrScanActivity.EXTRA_IP) ?: return@registerForActivityResult
        val port = data.getIntExtra(com.gongkao.checkin.sync.QrScanActivity.EXTRA_PORT, 0)
        val pin = data.getStringExtra(com.gongkao.checkin.sync.QrScanActivity.EXTRA_PIN).orEmpty()
        if (port <= 0) return@registerForActivityResult
        val device = FoundDevice(ip = ip, port = port, nickname = ip, versionName = "")
        // 扫码已经拿到了 PIN，直接选发送/接收即可，不用再手输一遍。
        AppListDialog.show(
            ctx = this,
            title = getString(R.string.device_sync_pick_action, device.nickname),
            rows = listOf(
                DialogRow(title = getString(R.string.device_sync_send)),
                DialogRow(title = getString(R.string.device_sync_receive))
            ),
            negative = getString(R.string.cancel),
            onPick = { index ->
                if (index == 0) doSend(device, pin)
                else confirmReceiveWithPin(device, pin)
            }
        )
    }

    private fun confirmReceiveWithPin(device: FoundDevice, pin: String) {
        AppDialog.show(
            ctx = this,
            title = getString(R.string.device_sync_confirm_receive_title),
            message = getString(R.string.device_sync_confirm_receive_msg),
            positive = getString(R.string.key_confirm),
            negative = getString(R.string.cancel),
            destructive = true
        ) { doReceive(device, pin) }
    }

    private fun startScan() {
        if (scanning) return
        val ip = LanInfo.ip(this)
        if (ip == null) {
            toast(getString(R.string.device_sync_no_wifi))
            return
        }
        scanning = true
        rebuild()
        val port = LanInfo.port()
        Thread {
            val found = runCatching { DeviceScan.scan(ip, port) }.getOrDefault(emptyList())
            runOnUiThread {
                scanning = false
                if (found.isEmpty()) {
                    toast(getString(R.string.device_sync_none_found))
                    rebuild()
                } else {
                    showDeviceList(found)
                }
            }
        }.start()
    }

    private fun showDeviceList(devices: List<FoundDevice>) {
        val rows = devices.map { d ->
            DialogRow(title = d.nickname, sub = "${d.ip}:${d.port}")
        }
        AppListDialog.show(
            ctx = this,
            title = getString(R.string.device_sync_scan),
            rows = rows,
            negative = getString(R.string.cancel),
            onPick = { index -> pickAction(devices[index]) }
        )
        rebuild()
    }

    private fun pickAction(device: FoundDevice) {
        AppListDialog.show(
            ctx = this,
            title = getString(R.string.device_sync_pick_action, device.nickname),
            rows = listOf(
                DialogRow(title = getString(R.string.device_sync_send)),
                DialogRow(title = getString(R.string.device_sync_receive))
            ),
            negative = getString(R.string.cancel),
            onPick = { index ->
                if (index == 0) askPinThen(device) { pin -> doSend(device, pin) }
                else confirmReceive(device)
            }
        )
    }

    private fun confirmReceive(device: FoundDevice) {
        AppDialog.show(
            ctx = this,
            title = getString(R.string.device_sync_confirm_receive_title),
            message = getString(R.string.device_sync_confirm_receive_msg),
            positive = getString(R.string.key_confirm),
            negative = getString(R.string.cancel),
            destructive = true
        ) {
            askPinThen(device) { pin -> doReceive(device, pin) }
        }
    }

    private fun askPinThen(device: FoundDevice, block: (String) -> Unit) {
        PinInputDialog.show(
            ctx = this,
            title = getString(R.string.device_sync_pin_title),
            message = getString(R.string.device_sync_pin_sub),
            positive = getString(R.string.key_confirm),
            negative = getString(R.string.cancel)
        ) { pin -> block(pin) }
    }

    private fun doSend(device: FoundDevice, pin: String) {
        toast(getString(R.string.device_sync_sending))
        Thread {
            val ok = runCatching { DeviceSync.sendFull(device, pin) }.getOrDefault(false)
            runOnUiThread {
                toast(getString(if (ok) R.string.device_sync_send_ok else R.string.device_sync_fail))
            }
        }.start()
    }

    private fun doReceive(device: FoundDevice, pin: String) {
        toast(getString(R.string.device_sync_receiving))
        Thread {
            val ok = runCatching { DeviceSync.receiveFull(device, pin) }.getOrDefault(false)
            runOnUiThread {
                toast(getString(if (ok) R.string.device_sync_receive_ok else R.string.device_sync_fail))
            }
        }.start()
    }

    private fun showMyQr() {
        val ip = LanInfo.ip(this)
        if (ip == null) {
            toast(getString(R.string.device_sync_no_wifi))
            return
        }
        val pin = Repo.read { it.settings.syncPin }
        QrShowDialog.show(this, ip, LanInfo.port(), pin)
    }

    /** 橙底提示条，用于把「同一局域网都能访问」这件事说明白。 */
    private fun warn(text: CharSequence) {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(getColor(R.color.carry_ink))
            setBackgroundResource(R.drawable.bg_pill_carry)
            setPadding(14.dp, 11.dp, 14.dp, 11.dp)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = 4.dp
                bottomMargin = 8.dp
            }
        }
        content.addView(tv)
    }

    // ------------------------------------------------------------ 关于

    private fun aboutSection() {
        section(getString(R.string.setting_about))
        val box = card()
        kv(getString(R.string.setting_version), BuildConfig.VERSION_NAME, box)
        val taskCount = Repo.read { it.tasks.count { t -> !t.archived } }
        val dayCount = Repo.read { it.days.size }
        kv(
            getString(R.string.setting_data),
            getString(R.string.setting_counts, taskCount, dayCount),
            box
        )
    }
}
