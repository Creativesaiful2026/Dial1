package com.example.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dialMeDataStore: DataStore<Preferences> by preferencesDataStore(name = "dialme_prefs")

class PreferencesRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val keyConfigJson = stringPreferencesKey("dialme_config_json")

    val configFlow: Flow<DialMeConfig> = context.dialMeDataStore.data.map { prefs ->
        val raw = prefs[keyConfigJson]
        if (raw.isNullOrBlank()) {
            DialMeConfig()
        } else {
            runCatching { json.decodeFromString<DialMeConfig>(raw) }.getOrDefault(DialMeConfig())
        }
    }

    suspend fun getConfigOnce(): DialMeConfig = configFlow.first()

    suspend fun updateConfig(transform: (DialMeConfig) -> DialMeConfig) {
        context.dialMeDataStore.edit { prefs ->
            val current = prefs[keyConfigJson]?.let {
                runCatching { json.decodeFromString<DialMeConfig>(it) }.getOrNull()
            } ?: DialMeConfig()
            val updated = transform(current)
            prefs[keyConfigJson] = json.encodeToString(updated)
        }
    }

    suspend fun replaceConfig(newConfig: DialMeConfig) {
        context.dialMeDataStore.edit { prefs ->
            prefs[keyConfigJson] = json.encodeToString(newConfig)
        }
    }

    suspend fun resetDefaults() {
        replaceConfig(DialMeConfig())
    }

    fun encodeConfig(config: DialMeConfig): String = json.encodeToString(config)

    fun decodeConfig(rawJson: String): DialMeConfig? =
        runCatching { json.decodeFromString<DialMeConfig>(rawJson) }.getOrNull()
}
