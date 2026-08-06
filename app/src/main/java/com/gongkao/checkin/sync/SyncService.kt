package com.gongkao.checkin.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gongkao.checkin.R
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.ui.MainActivity

/** 常驻前台服务，保证锁屏/切后台后局域网同步不断。 */
class SyncService : android.app.Service() {

    private var server: WebServer? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification(LanInfo.url(this)))
        if (server == null) {
            val port = Repo.read { it.settings.syncPort }
            server = runCatching { WebServer(applicationContext, port).also { it.startUp() } }
                .getOrNull()
            // 端口被占用时自动向后找一个可用端口
            if (server == null) {
                for (p in (port + 1)..(port + 12)) {
                    val s = runCatching { WebServer(applicationContext, p).also { it.startUp() } }.getOrNull()
                    if (s != null) {
                        server = s
                        Repo.edit { st -> st.settings.syncPort = p }
                        break
                    }
                }
            }
            updateNotification()
        }
        return START_STICKY
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(LanInfo.url(this)))
    }

    private fun buildNotification(url: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("电脑端同步已开启")
            .setContentText(url)
            .setSmallIcon(R.drawable.ic_today)
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "局域网同步", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
    }

    override fun onDestroy() {
        runCatching { server?.stop() }
        server = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "sync"
        private const val NOTIF_ID = 1001

        fun start(ctx: Context) {
            val i = Intent(ctx, SyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, SyncService::class.java))
        }
    }
}
