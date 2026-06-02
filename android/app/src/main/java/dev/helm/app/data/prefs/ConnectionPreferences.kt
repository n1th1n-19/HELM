package dev.helm.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectionMode { USB, WIFI }

@Singleton
class ConnectionPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private val KEY_MODE = stringPreferencesKey("mode")
        private val KEY_WIFI_HOST = stringPreferencesKey("wifi_host")
        private val KEY_WIFI_PORT = intPreferencesKey("wifi_port")

        const val DEFAULT_PORT = 9090
    }

    val mode: Flow<ConnectionMode> = dataStore.data.map { prefs ->
        when (prefs[KEY_MODE]) {
            "wifi" -> ConnectionMode.WIFI
            else -> ConnectionMode.USB
        }
    }

    val wifiHost: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_WIFI_HOST] ?: ""
    }

    val wifiPort: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_WIFI_PORT] ?: DEFAULT_PORT
    }

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
}
