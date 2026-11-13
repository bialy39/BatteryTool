package io.github.domi04151309.batterytool.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import io.github.domi04151309.batterytool.helpers.AppHelper
import io.github.domi04151309.batterytool.helpers.P

/**
 * Hibernates the blacklist once the screen has been off for the configured delay.
 *
 * The receiver keeps no screen state of its own: it is created in the service and a restored
 * process would start with a guess. The state is read from the power manager when the delayed
 * runnable fires instead, so a screen that came back on cancels the pass implicitly.
 */
class ScreenStateReceiver : BroadcastReceiver() {
    private fun onScreenOff(context: Context) {
        if (P.getPreferences(context).getBoolean(P.AUTO_STOP, P.AUTO_STOP_DEFAULT)) {
            Handler(Looper.getMainLooper()).postDelayed(
                { if (!AppHelper.isInteractive(context)) AppHelper.hibernate(context) },
                P.getPreferences(context).getInt(P.AUTO_STOP_DELAY, P.AUTO_STOP_DELAY_DEFAULT)
                    .toLong() * SECONDS_TO_MILLIS,
            )
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Intent.ACTION_SCREEN_OFF) onScreenOff(context)
    }

    companion object {
        private const val SECONDS_TO_MILLIS = 1000L
    }
}
