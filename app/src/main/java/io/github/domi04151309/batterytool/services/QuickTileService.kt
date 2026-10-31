package io.github.domi04151309.batterytool.services

import android.os.Build
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import io.github.domi04151309.batterytool.R
import io.github.domi04151309.batterytool.helpers.AppHelper

@RequiresApi(Build.VERSION_CODES.N)
class QuickTileService : TileService() {
    override fun onClick() {
        AppHelper.hibernate(this)
        Toast.makeText(this, R.string.toast_stopped_all, Toast.LENGTH_SHORT).show()
    }
}
