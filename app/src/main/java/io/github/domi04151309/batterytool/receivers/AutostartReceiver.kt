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
 * Enforces autostart blocking right after the device became usable: every blacklisted app gets
 * force stopped once on boot and once after the user unlocks the device. There is no per app
 * permission, autostart is always blocked for every added app.
 *
 * Package events do not force stop anything anymore — updating an app in the background used to
 * kill the app the user was about to use. They only start the service, so the next screen off
 * picks the new app up.
 *
 * Repeated passes never run here: the receiver starts [ForegroundService], whose watchdog only
 * acts while the screen is off, and does one quick force stop itself so the receiver stays inside
 * the `goAsync` budget. That one pass is the only place besides the user triggered actions where
 * an app is stopped while the device is interactive.
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
                forceStopOnce(appContext, action)
            } catch (exception: IllegalStateException) {
                Log.w(Global.LOG_TAG, exception)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun forceStopOnce(
        context: Context,
        action: String,
    ) {
        if (action !in FORCE_STOP_ACTIONS) {
            Log.i(Global.LOG_TAG, "package event, force stop deferred to screen off")
            return
        }
        // Preferences live in credential encrypted storage: before the first unlock the list is
        // not available at all, the service enforces it after the unlock broadcast instead.
        val blocked =
            try {
                P.getBlacklist(context)
            } catch (ignoredException: IllegalStateException) {
                return
            }
        if (blocked.isNotEmpty() && Root.isAvailable()) {
            Root.shell(blocked.map { "am force-stop $it" }.toTypedArray())
        }
    }

    private fun startEnforcementService(context: Context) {
        try {
            ContextCompat.startForegroundService(context, Intent(context, ForegroundService::class.java))
        } catch (exception: IllegalStateException) {
            // Starting a foreground service from the background is restricted on API 31+, the
            // watchdog then runs the next time the service is started from the foreground.
            Log.w(Global.LOG_TAG, exception)
        }
    }

    companion object {
        // The constants are inlined strings, comparing them is safe on every API level.
        @SuppressLint("InlinedApi")
        private val FORCE_STOP_ACTIONS: Set<String> =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                Intent.ACTION_USER_UNLOCKED,
            )

        @SuppressLint("InlinedApi")
        private val HANDLED_ACTIONS: Set<String> =
            FORCE_STOP_ACTIONS +
                setOf(
                    Intent.ACTION_PACKAGE_ADDED,
                    Intent.ACTION_PACKAGE_REPLACED,
                )
    }
}
