package io.github.domi04151309.batterytool.receivers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.domi04151309.batterytool.helpers.Global
import io.github.domi04151309.batterytool.helpers.P
import io.github.domi04151309.batterytool.helpers.Root
import io.github.domi04151309.batterytool.services.ForegroundService

/**
 * Enforces autostart blocking: every blacklisted app gets force stopped on boot, after the
 * user unlocks the device and when packages are installed. There is no per app permission,
 * autostart is always blocked for every added app.
 *
 * The receiver only does one quick force stop, the repeated passes and the watchdog run in
 * [ForegroundService] so the receiver stays inside the `goAsync` budget.
 */
class AutostartReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return
        val appContext = context.applicationContext
        startEnforcementService(appContext)
        val pendingResult = goAsync()
        Thread {
            try {
                forceStopOnce(appContext)
            } catch (exception: IllegalStateException) {
                Log.w(Global.LOG_TAG, exception)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun forceStopOnce(context: Context) {
        // Preferences live in credential encrypted storage: before the first unlock the list is
        // not available at all, the service enforces it after the unlock broadcast instead.
        val blocked =
            try {
                P.getBlacklist(context)
            } catch (ignoredException: IllegalStateException) {
                return
            }
        if (blocked.isEmpty() || !Root.isAvailable()) return
        Root.shell(blocked.map { "am force-stop $it" }.toTypedArray())
    }

    private fun startEnforcementService(context: Context) {
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ForegroundService::class.java)
                    .putExtra(ForegroundService.EXTRA_ENFORCE_AUTOSTART, true),
            )
        } catch (exception: IllegalStateException) {
            // Starting a foreground service from the background is restricted on API 31+, the
            // passes then run the next time the service is started from the foreground.
            Log.w(Global.LOG_TAG, exception)
        }
    }

    companion object {
        // The constants are inlined strings, comparing them is safe on every API level.
        @SuppressLint("InlinedApi")
        private val HANDLED_ACTIONS: Set<String> =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                Intent.ACTION_USER_UNLOCKED,
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REPLACED,
            )
    }
}
