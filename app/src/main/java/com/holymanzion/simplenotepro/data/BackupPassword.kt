package com.holymanzion.simplenotepro.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The optional backup password. It has to be kept on the phone, because automatic backups
 * run unattended, but it is stored encrypted with an AES key that lives in Android's
 * keystore and never leaves it. So the password can't be read out of the app's files.
 */
class BackupPasswordStore(context: Context) {
    private val prefs = context.getSharedPreferences("backup_secret", Context.MODE_PRIVATE)
    private val setState = MutableStateFlow(prefs.contains(KEY_BLOB))

    /** Whether backups are currently password-protected. */
    val isSet: StateFlow<Boolean> = setState.asStateFlow()

    fun get(): CharArray? {
        val blob = prefs.getString(KEY_BLOB, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(blob, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8).toCharArray()
        }.getOrNull() // a wiped keystore (e.g. after a backup restore to a new phone) reads as "no password"
    }

    /** Sets the password, or removes it when [password] is null or blank. */
    fun set(password: CharArray?) {
        if (password == null || password.isEmpty()) {
            prefs.edit().remove(KEY_BLOB).apply()
            setState.value = false
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(String(password).toByteArray(Charsets.UTF_8))
        prefs.edit().putString(KEY_BLOB, Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)).apply()
        setState.value = true
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "backup-password"
        const val KEY_BLOB = "password"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}

/** Import needs a password: the backup is encrypted and none (or the wrong one) was given. */
class BackupPasswordException(val wrongPassword: Boolean) :
    Exception(if (wrongPassword) "Wrong backup password" else "This backup is password-protected")
