package com.gongkao.checkin

import android.app.Application
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.sync.SyncService

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Repo.init(this)
        if (Repo.read { it.settings.syncEnabled }) {
            runCatching { SyncService.start(this) }
        }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
