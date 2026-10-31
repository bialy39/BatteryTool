package io.github.domi04151309.batterytool.activities

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import io.github.domi04151309.batterytool.R

abstract class BaseActivity : AppCompatActivity() {
    private val isNightMode: Boolean
        get() =
            resources.configuration.uiMode.and(
                Configuration.UI_MODE_NIGHT_MASK,
            ) == Configuration.UI_MODE_NIGHT_YES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isNightMode) {
            setTheme(R.style.LightStatusBarOverlay)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    /**
     * Draws the content edge to edge and adds the system bar insets as padding to [view],
     * so that no content ends up below the status or navigation bar.
     */
    protected fun applyEdgeToEdgeInsets(view: View) {
        val initialLeft = view.paddingLeft
        val initialTop = view.paddingTop
        val initialRight = view.paddingRight
        val initialBottom = view.paddingBottom
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isNightMode
        insetsController.isAppearanceLightNavigationBars = !isNightMode
        ViewCompat.setOnApplyWindowInsetsListener(view) { insetsView, windowInsets ->
            val systemBars =
                windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
            insetsView.setPadding(
                initialLeft + systemBars.left,
                initialTop + systemBars.top,
                initialRight + systemBars.right,
                initialBottom + systemBars.bottom,
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(view)
    }
}
