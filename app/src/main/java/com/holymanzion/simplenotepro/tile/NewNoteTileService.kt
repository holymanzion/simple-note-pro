package com.holymanzion.simplenotepro.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.holymanzion.simplenotepro.MainActivity

/** "New note" in the Quick Settings panel: one tap from anywhere to start writing. */
class NewNoteTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE // an action, not an on/off switch
            updateTile()
        }
    }

    override fun onClick() {
        // On a locked phone, ask to unlock first rather than opening notes over the lock screen.
        if (isLocked) unlockAndRun(::openNewNote) else openNewNote()
    }

    private fun openNewNote() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_NEW_NOTE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ only accepts a PendingIntent here.
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
