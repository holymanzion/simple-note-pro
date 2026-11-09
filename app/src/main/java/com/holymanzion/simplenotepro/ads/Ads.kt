package com.holymanzion.simplenotepro.ads

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.holymanzion.simplenotepro.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Banner ads through AdMob. Google's consent form (UMP) runs first: where the law requires
 * it (EEA, UK, Switzerland, some US states) people choose before any ad is requested,
 * and can change their choice later from Settings › Ad privacy choices.
 */
class AdsManager(context: Context) {
    private val appContext = context.applicationContext

    /** Device tests run without ads or the consent form, so they stay offline and repeatable. */
    val enabled: Boolean = !(BuildConfig.DEBUG && runningTests())

    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(appContext)
    private val requested = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val initialized = MutableStateFlow(false)

    private val _canShowAds = MutableStateFlow(false)
    val canShowAds: StateFlow<Boolean> = _canShowAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    /** Whether Settings must offer a way to change the consent choice. */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    /**
     * Call from the main activity's onCreate. Runs once per app start (not again on rotation)
     * and shows the consent form only where it's needed.
     */
    fun gatherConsent(activity: Activity) {
        if (!enabled || !requested.compareAndSet(false, true)) return
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { refresh() } },
            { refresh() }, // Offline or similar: decide from the choice saved last time.
        )
        // A choice saved in an earlier session lets ads start while the update runs.
        refresh()
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { refresh() }
    }

    private fun refresh() {
        _privacyOptionsRequired.value =
            consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        val allowed = consent.canRequestAds()
        if (allowed && started.compareAndSet(false, true)) {
            // Google recommends initializing off the main thread; it can take a moment.
            thread(name = "ads-init") {
                MobileAds.initialize(appContext) {
                    initialized.value = true
                    _canShowAds.value = consent.canRequestAds()
                }
            }
        }
        _canShowAds.value = allowed && initialized.value
    }

    private companion object {
        fun runningTests() = runCatching { Class.forName("androidx.test.platform.app.InstrumentationRegistry") }.isSuccess
    }
}

/**
 * An anchored banner as wide as the screen. Takes no space until an ad has actually loaded,
 * so a failed or blocked request leaves no empty gap.
 */
@Composable
fun BannerAd(ads: AdsManager, modifier: Modifier = Modifier) {
    val canShow by ads.canShowAds.collectAsStateWithLifecycle()
    if (!canShow) return
    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    var loaded by remember(widthDp) { mutableStateOf(false) }
    val adView = remember(widthDp) {
        AdView(context).apply {
            adUnitId = BuildConfig.BANNER_AD_UNIT_ID
            setAdSize(AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp))
            adListener = object : AdListener() {
                override fun onAdLoaded() { loaded = true }
            }
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) { onDispose { adView.destroy() } }
    LifecycleResumeEffect(adView) {
        adView.resume()
        onPauseOrDispose { adView.pause() }
    }
    if (loaded) {
        AndroidView(factory = { adView }, modifier = modifier.fillMaxWidth().navigationBarsPadding())
    }
}
