package io.github.domi04151309.batterytool.receivers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.domi04151309.batterytool.helpers.Global
import io.github.domi04151309.batterytool.helpers.P
import io.github.domi04151309.batterytool.helpers.Root

/**
 * Enforces autostart blocking: every blacklisted app gets force stopped on boot, after the
 * user unlocks the device and when packages are installed. There is no per app permission,
 * autostart is always blocked for every added app.
 */
class AutostartReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                forceStopBlockedApps(appContext)
            } catch (exception: IllegalStateException) {
                Log.w(Global.LOG_TAG, exception)
            } catch (exception: InterruptedException) {
                Log.w(Global.LOG_TAG, exception)
                Thread.currentThread().interrupt()
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun forceStopBlockedApps(context: Context) {
        // Preferences live in credential encrypted storage: before the first unlock the
        // list is not available and the next boot or unlock broadcast enforces it again.
        val blocked = P.getBlacklist(context)
        if (blocked.isEmpty()) return
        for (attempt in 1..MAX_ATTEMPTS) {
            if (Root.isAvailable()) {
                Root.shell(blocked.map { "am force-stop $it" }.toTypedArray())
                return
            }
            if (attempt < MAX_ATTEMPTS) Thread.sleep(RETRY_DELAY_MILLIS)
        }
        Log.w(Global.LOG_TAG, "Root is not available, blocked apps could not be force stopped")
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MILLIS = 3_000L

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
