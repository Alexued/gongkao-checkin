package com.gongkao.checkin.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.LinearLayout
import android.widget.TextView
import com.gongkao.checkin.BuildConfig
import com.gongkao.checkin.R
import com.gongkao.checkin.data.DateUtil
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.sync.LanInfo
import com.gongkao.checkin.sync.SyncService
import java.time.LocalDate

/** 设置：总结束日 + 局域网网页同步 + 关于。 */
class SettingsActivity : ListScreen() {

    override fun title(): CharSequence = getString(R.string.settings)

    override fun build() {
        endDateSection()
        syncSection()
        updateSection()
        aboutSection()
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
        if (on) SyncService.start(this) else SyncService.stop(this)
    }

    private fun copy(text: String) {
        val cm = getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("url", text))
        toast(getString(R.string.sync_copied))
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
