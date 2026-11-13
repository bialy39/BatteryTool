package io.github.domi04151309.batterytool.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.domi04151309.batterytool.R
import io.github.domi04151309.batterytool.helpers.AppHelper
import io.github.domi04151309.batterytool.helpers.Global
import io.github.domi04151309.batterytool.receivers.ScreenStateReceiver
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Keeps running in the foreground and enforces autostart blocking. The watchdog repeats a pass
 * every 60 s for as long as the service lives and catches apps that come up from their own boot
 * receivers later on.
 *
 * A pass does nothing while the screen is on, so there is no series of passes right after boot
 * anymore: the app that starts right after the unlock is left alone, and the watchdog picks the
 * list up once the screen goes off. The single force stop after a boot or unlock broadcast is
 * done by [io.github.domi04151309.batterytool.receivers.AutostartReceiver], not by this service.
 */
class ForegroundService : Service() {
    private val screenStateReceiver = ScreenStateReceiver()
    private val handler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val watchdog =
        object : Runnable {
            override fun run() {
                runEnforcementPass(WATCHDOG_REASON)
                handler.postDelayed(this, WATCHDOG_INTERVAL_MILLIS)
            }
        }
    private var isWatchdogRunning = false

    override fun onCreate() {
        super.onCreate()
        registerReceiver(screenStateReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        isWatchdogRunning = false
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
        scheduleWatchdog()
        return START_STICKY
    }

    private fun scheduleWatchdog() {
        // onStartCommand runs on every restart, including the one after START_STICKY, so the flag
        // has to keep a single watchdog in the queue.
        if (isWatchdogRunning) return
        isWatchdogRunning = true
        handler.post(watchdog)
    }

    private fun runEnforcementPass(reason: String) {
        executor.execute {
            try {
                AppHelper.enforceAutostart(this, reason)
            } catch (exception: IllegalStateException) {
                Log.w(Global.LOG_TAG, exception)
            }
        }
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
        private const val WATCHDOG_REASON: String = "watchdog"
        private const val WATCHDOG_INTERVAL_MILLIS: Long = 60_000L
    }
}
