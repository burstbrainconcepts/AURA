package com.burstbrainconcepts.aura

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * User-added Quick Settings tile. Tapping it explicitly opens AURA.
 * It does not start the microphone or run a hidden listening service.
 */
class AuraQuickSettingsTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = "Open AURA"
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivityAndCollapse(launch)
    }
}
