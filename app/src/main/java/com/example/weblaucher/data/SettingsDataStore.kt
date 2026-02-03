package com.example.weblaucher.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("launcher_settings")

object SettingsKeys {
    val windowsJson = stringPreferencesKey("windows_json")
    val maxAlive = intPreferencesKey("max_alive")
    val refreshPolicy = stringPreferencesKey("refresh_policy")
    val thirdPartyCookies = booleanPreferencesKey("third_party_cookies")
    val userAgent = stringPreferencesKey("user_agent")
    val foregroundService = booleanPreferencesKey("foreground_service")
    val backBehavior = stringPreferencesKey("back_behavior")
    val whitelistDomains = stringPreferencesKey("whitelist_domains")
    val debugWebView = booleanPreferencesKey("debug_webview")
}

data class LauncherSettings(
    val windowsJson: String,
    val maxAlive: Int,
    val refreshPolicy: String,
    val thirdPartyCookies: Boolean,
    val userAgent: String,
    val foregroundService: Boolean,
    val backBehavior: String,
    val whitelistDomains: String,
    val debugWebView: Boolean
)

class SettingsDataStore(private val context: Context) {

    val settingsFlow: Flow<LauncherSettings> = context.dataStore.data.map { prefs ->
        LauncherSettings(
            windowsJson = prefs[SettingsKeys.windowsJson] ?: "",
            maxAlive = prefs[SettingsKeys.maxAlive] ?: 5,
            refreshPolicy = prefs[SettingsKeys.refreshPolicy] ?: RefreshPolicy.CURRENT.name,
            thirdPartyCookies = prefs[SettingsKeys.thirdPartyCookies] ?: false,
            userAgent = prefs[SettingsKeys.userAgent] ?: "",
            foregroundService = prefs[SettingsKeys.foregroundService] ?: true,
            backBehavior = prefs[SettingsKeys.backBehavior] ?: BackBehavior.WEB_BACK.name,
            whitelistDomains = prefs[SettingsKeys.whitelistDomains] ?: "",
            debugWebView = prefs[SettingsKeys.debugWebView] ?: false
        )
    }

    suspend fun updateMaxAlive(value: Int) {
        context.dataStore.edit { it[SettingsKeys.maxAlive] = value }
    }

    suspend fun updateRefreshPolicy(value: RefreshPolicy) {
        context.dataStore.edit { it[SettingsKeys.refreshPolicy] = value.name }
    }

    suspend fun updateThirdPartyCookies(value: Boolean) {
        context.dataStore.edit { it[SettingsKeys.thirdPartyCookies] = value }
    }

    suspend fun updateUserAgent(value: String) {
        context.dataStore.edit { it[SettingsKeys.userAgent] = value }
    }

    suspend fun updateForegroundService(value: Boolean) {
        context.dataStore.edit { it[SettingsKeys.foregroundService] = value }
    }

    suspend fun updateBackBehavior(value: BackBehavior) {
        context.dataStore.edit { it[SettingsKeys.backBehavior] = value.name }
    }

    suspend fun updateWhitelistDomains(value: String) {
        context.dataStore.edit { it[SettingsKeys.whitelistDomains] = value }
    }

    suspend fun updateDebugWebView(value: Boolean) {
        context.dataStore.edit { it[SettingsKeys.debugWebView] = value }
    }

    suspend fun updateWindowsJson(value: String) {
        context.dataStore.edit { it[SettingsKeys.windowsJson] = value }
    }
}

enum class RefreshPolicy {
    OFF,
    CURRENT,
    ALL
}

enum class BackBehavior {
    WEB_BACK,
    DISABLED
}
