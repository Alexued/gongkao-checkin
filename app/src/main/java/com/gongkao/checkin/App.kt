package com.gongkao.checkin

import android.app.Application
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.sync.SyncService

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Repo.init(this)
        val st = Repo.read { it.settings }
        if (st.syncEnabled || st.syncDiscoverable) {
            runCatching { SyncService.start(this) }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // 进入后台时同步等写完，防止被系统杀进程丢数据
        if (level >= TRIM_MEMORY_UI_HIDDEN) Repo.flush()
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
