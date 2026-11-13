package io.github.domi04151309.batterytool.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.github.domi04151309.batterytool.R
import io.github.domi04151309.batterytool.receivers.ScreenStateReceiver

/**
 * Hosts [ScreenStateReceiver] and the ongoing foreground notification.
 *
 * The screen-off receiver is registered at runtime on purpose: the stop that happens
 * `auto_stop_delay` seconds after the display went dark needs a live process, and a manifest
 * receiver would be gone by then. The notification is what keeps that process alive. Starting the
 * service therefore starts the stop timer, nothing more — no app is stopped from here, neither on
 * start nor on a repeated `onStartCommand`.
 */
class ForegroundService : Service() {
    private val screenStateReceiver = ScreenStateReceiver()

    override fun onCreate() {
        super.onCreate()
        registerReceiver(screenStateReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenStateReceiver)
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        createNotificationChannel()
        startForeground(
            1,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentText(getString(R.string.service_text))
                .setSmallIcon(R.drawable.ic_spa)
                .setShowWhen(false)
                .build(),
        )
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.service_channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
            channel.setShowBadge(false)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID: String = "service_channel"
    }
}
