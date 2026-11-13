
package com.capitan.phonesync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SyncService : Service() {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private val channelId = "capitan_sync_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        startForeground(
            1001,
            createNotification("انتقال اطلاعات در حال آماده‌سازی است")
        )

        scope.launch {
            val prefs = getSharedPreferences(
                "capitan_sync",
                MODE_PRIVATE
            )

            val token = prefs.getString("token", "") ?: ""
            val chatId = prefs.getString("chat", "") ?: ""

            if (token.isBlank() || chatId.isBlank()) {
                updateNotification("توکن یا شناسه تلگرام تنظیم نشده است")
                stopSelf()
                return@launch
            }

            try {
                val result = SyncManager.syncAll(
                    this@SyncService,
                    token,
                    chatId
                ) { message ->
                    updateNotification(message)
                }

                updateNotification(
                    "پایان انتقال | موفق: ${result.sent} | ناموفق: ${result.failed} | ردشده: ${result.skipped}"
                )

            } catch (e: Exception) {
                updateNotification("خطا در انتقال اطلاعات")
            }

            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "CAPITAN PHONE SYNC",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun createNotification(message: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("CAPITAN PHONE SYNC")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(
            NotificationManager::class.java
        )

        manager.notify(1001, createNotification(message))
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
