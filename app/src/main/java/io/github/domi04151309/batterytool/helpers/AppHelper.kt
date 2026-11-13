package io.github.domi04151309.batterytool.helpers

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.PowerManager
import android.util.Log
import io.github.domi04151309.batterytool.R
import io.github.domi04151309.batterytool.data.RunningAppItem
import io.github.domi04151309.batterytool.data.SimpleListItem
import io.github.domi04151309.batterytool.services.NotificationService

object AppHelper {
    fun generateListItem(
        context: Context,
        packageName: String,
    ): SimpleListItem =
        SimpleListItem(
            title = appTitle(context, packageName),
            summary = packageName,
            icon = context.packageManager.getApplicationIcon(packageName),
        )

    fun generateListItem(
        context: Context,
        applicationInfo: ApplicationInfo,
    ): SimpleListItem =
        SimpleListItem(
            applicationInfo.loadLabel(context.packageManager).toString(),
            applicationInfo.packageName,
            applicationInfo.loadIcon(context.packageManager),
        )

    /**
     * User apps that currently have a process but are not in the foreground, sorted by their label.
     * Foreground apps are left out on purpose: they are not "running in the background", and
     * force stopping the app the user is looking at would be surprising.
     */
    internal fun loadRunningApps(context: Context): List<RunningAppItem> {
        val candidates =
            context.packageManager
                .getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { isUserApp(context, it) }
                .map { it.packageName }
                .toSet()
        val running = if (candidates.isEmpty()) HashSet() else Root.getRunning(candidates)
        val focused = if (running.isEmpty()) HashSet() else Root.getFocusedApps()
        if (focused.isNotEmpty()) {
            Log.d(
                Global.LOG_TAG,
                "running apps: skipping ${context.resources.getString(R.string.running_foreground)} " +
                    focused.joinToString(),
            )
        }
        return candidates
            .filter { running.contains(it) && !focused.contains(it) }
            .mapNotNull { packageName -> runningItem(context, packageName) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    private fun isUserApp(
        context: Context,
        applicationInfo: ApplicationInfo,
    ): Boolean =
        applicationInfo.packageName != context.packageName &&
            applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0 &&
            applicationInfo.flags and ApplicationInfo.FLAG_INSTALLED != 0

    private fun runningItem(
        context: Context,
        packageName: String,
    ): RunningAppItem? =
        try {
            RunningAppItem(
                title = appTitle(context, packageName),
                summary = packageName,
                icon = context.packageManager.getApplicationIcon(packageName),
                packageName = packageName,
            )
        } catch (exception: PackageManager.NameNotFoundException) {
            // The app was uninstalled between the query and the lookup.
            Log.w(Global.LOG_TAG, exception)
            null
        }

    private fun appTitle(
        context: Context,
        packageName: String,
    ): String {
        val info = context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val label = context.packageManager.getApplicationLabel(info).toString()
        return if (P.getForced(context).contains(packageName)) {
            label + " " + context.resources.getString(R.string.main_forced)
        } else {
            label
        }
    }

    private fun hibernateApps(
        context: Context,
        playingMusicPackage: String?,
    ) {
        if (!Root.isAvailable()) {
            Log.w(Global.LOG_TAG, "hibernate: root is not available, nothing was stopped")
            return
        }
        val apps = P.getBlacklist(context)
        val services = Root.getServices()
        val commands: ArrayList<String> = ArrayList(apps.size / 2)
        val focused =
            if (
                P.getPreferences(context).getBoolean(
                    P.IGNORE_FOCUSED_APPS,
                    P.IGNORE_FOCUSED_APPS_DEFAULT,
                )
            ) {
                Root.getFocusedApps()
            } else {
                PseudoHashSet()
            }
        for (packageName in apps) {
            try {
                @Suppress("ComplexCondition")
                if (
                    packageName != playingMusicPackage &&
                    !focused.contains(packageName) &&
                    context.packageManager.getApplicationInfo(
                        packageName,
                        PackageManager.GET_META_DATA,
                    ).flags and ApplicationInfo.FLAG_STOPPED == 0 &&
                    (services.contains(packageName) || P.getForced(context).contains(packageName))
                ) {
                    commands.add("am force-stop $packageName")
                }
            } catch (exception: PackageManager.NameNotFoundException) {
                Log.w(Global.LOG_TAG, exception)
                continue
            }
        }
        if (commands.isNotEmpty()) Root.shell(commands.toTypedArray())
    }

    internal fun hibernate(context: Context) {
        if (P.getPreferences(context).getBoolean(P.ALLOW_MUSIC, P.ALLOW_MUSIC_DEFAULT)) {
            NotificationService.getInstance()?.getPlayingPackageName { packageName ->
                hibernateApps(
                    context,
                    packageName,
                )
            }
        } else {
            hibernateApps(context, null)
        }
    }

    internal fun isInteractive(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isInteractive
    }
}
