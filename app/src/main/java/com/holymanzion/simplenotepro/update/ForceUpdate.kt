package com.holymanzion.simplenotepro.update

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Makes people update as soon as a newer version is on Google Play: Play's full-screen
 * "Update" page covers the app, downloads the update and restarts the app.
 *
 * Backing out of it closes the app, so the old version can't be used. If Play itself
 * fails (no space, no connection), the app carries on rather than locking people out
 * of their notes; it asks again the next time it is opened.
 *
 * Only works for copies installed from Google Play; for anything else Play reports
 * an error, which is ignored.
 */
class ForceUpdate(private val activity: ComponentActivity) {
    private val playManager: AppUpdateManager? by lazy {
        if (runningTests()) null else AppUpdateManagerFactory.create(activity)
    }

    // Device tests swap in Play's FakeAppUpdateManager; otherwise they skip the check.
    private val manager: AppUpdateManager? get() = testManager ?: playManager

    /** True while Play's update page is showing, so a resume doesn't open a second one. */
    private var showing = false

    // Registered when the activity is created, as the result API requires.
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        onClosed(result.resultCode)
    }

    /** Call from onResume: also picks an interrupted update back up. */
    fun check() {
        val manager = manager ?: return
        if (showing || activity.isFinishing) return
        manager.appUpdateInfo
            .addOnSuccessListener { info -> if (mustUpdate(info)) start(info) }
            .addOnFailureListener { /* Not installed from Play, or Play unavailable. */ }
    }

    private fun mustUpdate(info: AppUpdateInfo): Boolean = when (info.updateAvailability()) {
        UpdateAvailability.UPDATE_AVAILABLE -> info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
        UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> true
        else -> false
    }

    private fun start(info: AppUpdateInfo) {
        val manager = manager ?: return
        if (showing || activity.isFinishing) return
        showing = runCatching {
            manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build())
        }.getOrDefault(false)
    }

    /** Back from Play's page. */
    internal fun onClosed(resultCode: Int) {
        showing = false
        if (resultCode == Activity.RESULT_CANCELED) activity.finish()
    }

    companion object {
        @androidx.annotation.VisibleForTesting
        var testManager: AppUpdateManager? = null

        private fun runningTests() =
            runCatching { Class.forName("androidx.test.platform.app.InstrumentationRegistry") }.isSuccess
    }
}
