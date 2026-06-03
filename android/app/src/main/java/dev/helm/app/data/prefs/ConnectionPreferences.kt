package dev.helm.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectionMode { USB, WIFI }

@Singleton
class ConnectionPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        // DataStore keys — non-sensitive connection settings only.
        private val KEY_MODE      = stringPreferencesKey("mode")
        private val KEY_WIFI_HOST = stringPreferencesKey("wifi_host")
        private val KEY_WIFI_PORT = intPreferencesKey("wifi_port")

        // EncryptedSharedPreferences keys — sensitive values.
        private const val ENC_KEY_TOKEN       = "token"
        private const val ENC_KEY_FINGERPRINT = "cert_fingerprint"

        const val DEFAULT_PORT = 9090
    }

    /**
     * Encrypted storage for PSK token and certificate fingerprint.
     * Backed by AES256-GCM (values) + AES256-SIV (keys) via Jetpack security-crypto.
     * Lazy so that the KeyStore is only accessed when first needed.
     */
    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "helm_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // ── Non-sensitive prefs (DataStore) ──────────────────────────────────────

    val mode: Flow<ConnectionMode> = dataStore.data.map { prefs ->
        when (prefs[KEY_MODE]) {
            "wifi" -> ConnectionMode.WIFI
            else   -> ConnectionMode.USB
        }
    }

    val wifiHost: Flow<String> = dataStore.data.map { prefs -> prefs[KEY_WIFI_HOST] ?: "" }
    val wifiPort: Flow<Int>    = dataStore.data.map { prefs -> prefs[KEY_WIFI_PORT] ?: DEFAULT_PORT }

    suspend fun setMode(mode: ConnectionMode) {
        dataStore.edit { prefs ->
            prefs[KEY_MODE] = if (mode == ConnectionMode.WIFI) "wifi" else "usb"
        }
    }

    suspend fun setWifiHost(host: String) {
        dataStore.edit { prefs -> prefs[KEY_WIFI_HOST] = host }
    }

    suspend fun setWifiPort(port: Int) {
        dataStore.edit { prefs -> prefs[KEY_WIFI_PORT] = port }
    }

    // ── Sensitive prefs (EncryptedSharedPreferences) ─────────────────────────

    /** Null in USB mode or when not yet paired via QR. */
    val token: Flow<String?> = flow {
        emit(encryptedPrefs.getString(ENC_KEY_TOKEN, null))
    }

    /** SHA-256 hex fingerprint of the agent's TLS cert. Null in USB mode. */
    val certFingerprint: Flow<String?> = flow {
        emit(encryptedPrefs.getString(ENC_KEY_FINGERPRINT, null))
    }

    suspend fun setToken(token: String?) {
        encryptedPrefs.edit().apply {
            if (token != null) putString(ENC_KEY_TOKEN, token) else remove(ENC_KEY_TOKEN)
        }.apply()
    }

    suspend fun setCertFingerprint(fingerprint: String?) {
        encryptedPrefs.edit().apply {
            if (fingerprint != null) putString(ENC_KEY_FINGERPRINT, fingerprint)
            else remove(ENC_KEY_FINGERPRINT)
        }.apply()
    }
}
