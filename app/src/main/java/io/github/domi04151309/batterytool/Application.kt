package io.github.domi04151309.batterytool

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.material.color.DynamicColors
import io.github.domi04151309.batterytool.helpers.Global
import io.github.domi04151309.batterytool.helpers.P

class Application : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        removeSystemApps()
    }

    private fun removeSystemApps() {
        val blacklist = P.getBlacklist(this)
        if (blacklist.isEmpty()) return
        val systemApps = blacklist.filter { isSystemApp(it) }.toSet()
        if (systemApps.isEmpty()) return
        P.setBlacklist(this, blacklist - systemApps)
        Log.i(Global.LOG_TAG, "Removed ${systemApps.size} system apps from the blacklist")
    }

    private fun isSystemApp(packageName: String): Boolean =
        try {
            packageManager
                .getApplicationInfo(packageName, 0)
                .flags and ApplicationInfo.FLAG_SYSTEM != 0
        } catch (exception: PackageManager.NameNotFoundException) {
            Log.w(Global.LOG_TAG, exception)
            false
        }
}
