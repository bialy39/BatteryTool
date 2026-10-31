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
 * Keeps running in the foreground and enforces autostart blocking. On request it schedules one
 * pass right away, then 20 s, 60 s and 150 s later and afterwards every 60 s for as long as the
 * service lives. The first passes catch apps that start immediately at boot, the later passes
 * and the watchdog catch apps that come up from their own boot receivers after our force stop.
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
        registerReceiver(
            screenStateReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON).apply {
                addAction(Intent.ACTION_SCREEN_OFF)
            },
        )
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
        if (intent?.getBooleanExtra(EXTRA_ENFORCE_AUTOSTART, false) == true) scheduleBootPasses()
        return START_REDELIVER_INTENT
    }

    private fun scheduleBootPasses() {
        // Every boot, unlock or package broadcast gets a fresh series of passes: a pass that ran
        // before the first unlock could not read the blacklist, this one can.
        for (delayMillis in ENFORCEMENT_DELAYS_MILLIS) {
            val reason = "boot+" + delayMillis / MILLIS_PER_SECOND + "s"
            handler.postDelayed({ runEnforcementPass(reason) }, delayMillis)
        }
        if (!isWatchdogRunning) {
            isWatchdogRunning = true
            handler.postDelayed(watchdog, WATCHDOG_INTERVAL_MILLIS)
        }
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
        const val EXTRA_ENFORCE_AUTOSTART: String = "enforce_autostart"
        private const val CHANNEL_ID: String = "service_channel"
        private const val MILLIS_PER_SECOND: Long = 1_000L
        private const val WATCHDOG_REASON: String = "watchdog"
        private const val WATCHDOG_INTERVAL_MILLIS: Long = 60_000L
        private val ENFORCEMENT_DELAYS_MILLIS: List<Long> =
            listOf(0L, 20_000L, 60_000L, 150_000L)
    }
}
