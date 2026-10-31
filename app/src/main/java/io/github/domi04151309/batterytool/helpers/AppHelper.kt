package io.github.domi04151309.batterytool.helpers

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import io.github.domi04151309.batterytool.R
import io.github.domi04151309.batterytool.data.SimpleListItem
import io.github.domi04151309.batterytool.services.NotificationService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object AppHelper {
    private const val DEFAULT_PASS_REASON = "manual"
    private const val MUSIC_LOOKUP_TIMEOUT_MILLIS = 2_000L

    fun generateListItem(
        context: Context,
        packageName: String,
    ): SimpleListItem =
        SimpleListItem(
            title =
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(
                        packageName,
                        PackageManager.GET_META_DATA,
                    ),
                ).toString() +
                    if (P.getForced(context).contains(packageName)) {
                        " " + context.resources.getString(R.string.main_forced)
                    } else {
                        ""
                    },
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
    internal fun loadRunningApps(context: Context): List<SimpleListItem> {
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
    ): SimpleListItem? =
        try {
            generateListItem(context, packageName)
        } catch (exception: PackageManager.NameNotFoundException) {
            // The app was uninstalled between the query and the lookup.
            Log.w(Global.LOG_TAG, exception)
            null
        }

    private fun hibernateApps(
        context: Context,
        playingMusicPackage: String?,
    ) {
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

    /**
     * One enforcement pass: force stops every app of the blacklist that is running right now.
     * Apps that are in the foreground and the app that is currently playing music (when allowing
     * music is enabled) are left alone — the foreground exclusion is unconditional, the watchdog
     * must never kill the app the user is using.
     */
    internal fun enforceAutostart(
        context: Context,
        reason: String = DEFAULT_PASS_REASON,
    ) {
        val blocked =
            try {
                P.getBlacklist(context)
            } catch (ignoredException: IllegalStateException) {
                // Storage is locked until the first unlock, the pass after USER_UNLOCKED runs.
                Log.i(Global.LOG_TAG, "autostart enforcement (pass=$reason): storage locked")
                return
            }
        val playingMusicPackage =
            if (P.getPreferences(context).getBoolean(P.ALLOW_MUSIC, P.ALLOW_MUSIC_DEFAULT)) {
                getPlayingPackage()
            } else {
                null
            }
        enforceAutostartApps(reason, blocked, playingMusicPackage)
    }

    private fun enforceAutostartApps(
        reason: String,
        blocked: Set<String>,
        playingMusicPackage: String?,
    ) {
        val candidates = getAutostartCandidates(blocked, playingMusicPackage)
        val running = if (candidates.isEmpty()) HashSet() else Root.getRunning(candidates)
        var stopped = 0
        if (running.isNotEmpty() && Root.isAvailable()) {
            Root.shell(running.map { "am force-stop $it" }.toTypedArray())
            stopped = running.size
        }
        Log.i(
            Global.LOG_TAG,
            "autostart enforcement (pass=$reason): candidates=${candidates.size} " +
                "running=${running.size} stopped=$stopped",
        )
    }

    private fun getAutostartCandidates(
        blocked: Set<String>,
        playingMusicPackage: String?,
    ): Set<String> {
        // The foreground app is always excluded from enforcement, IGNORE_FOCUSED_APPS only
        // controls hibernateApps().
        val focused = Root.getFocusedApps()
        return blocked
            .filter { it != playingMusicPackage && !focused.contains(it) }
            .toSet()
    }

    private fun getPlayingPackage(): String? {
        val service = NotificationService.getInstance() ?: return null
        val latch = CountDownLatch(1)
        val playingPackage = AtomicReference<String?>()
        service.getPlayingPackageName { packageName ->
            playingPackage.set(packageName)
            latch.countDown()
        }
        return try {
            if (latch.await(MUSIC_LOOKUP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                playingPackage.get()
            } else {
                Log.w(Global.LOG_TAG, "Timed out waiting for the playing music package")
                null
            }
        } catch (exception: InterruptedException) {
            Log.w(Global.LOG_TAG, exception)
            Thread.currentThread().interrupt()
            null
        }
    }
}
