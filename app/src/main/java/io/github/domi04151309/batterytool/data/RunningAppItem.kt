package io.github.domi04151309.batterytool.data

import android.graphics.drawable.Drawable

/**
 * One row of the "Running apps" tab.
 *
 * [packageName] is null for the placeholder row that is shown when no user app runs in the
 * background. A placeholder has nothing to force stop, so it must never offer a Kill button and
 * its summary may wrap over several lines.
 */
data class RunningAppItem(
    val title: String,
    val summary: String,
    val icon: Drawable?,
    val packageName: String?,
)
