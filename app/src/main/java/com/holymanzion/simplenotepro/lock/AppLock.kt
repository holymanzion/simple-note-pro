package com.holymanzion.simplenotepro.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Decides when the app is locked. The app locks at launch and after it has been in the
 * background for [gracePeriodMs], so quickly switching to another app and back, or
 * rotating the phone, doesn't ask again.
 */
class AppLock(
    private val isEnabled: () -> Boolean,
    private val gracePeriodMs: Long = 60_000,
) {
    private val lockedState = MutableStateFlow(isEnabled())
    val locked: StateFlow<Boolean> = lockedState.asStateFlow()

    /** Tests turn this off so the system prompt doesn't cover the screen. */
    var promptAutomatically = true

    private var backgroundedAt: Long? = null

    fun onBackground(now: Long) {
        backgroundedAt = now
    }

    fun onForeground(now: Long) {
        val since = backgroundedAt
        backgroundedAt = null
        if (isEnabled() && since != null && now - since >= gracePeriodMs) lockedState.value = true
    }

    fun unlock() {
        lockedState.value = false
    }

    fun lockNow() {
        if (isEnabled()) lockedState.value = true
    }
}

/** Fingerprint, face, or the phone's PIN/pattern/password — whatever the user has set up. */
object DeviceAuth {
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {},
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errString.toString())
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }
}
