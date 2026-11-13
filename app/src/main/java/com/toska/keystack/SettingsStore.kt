package com.toska.keystack

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "keystack")

data class Settings(
    val providerId: String,
    val apiKey: String,
    val model: String,
    val baseUrl: String
)

class SettingsStore(private val context: Context) {

    private val keyProvider = stringPreferencesKey("provider")
    private val keyApi = stringPreferencesKey("api_key")
    private val keyModel = stringPreferencesKey("model")
    private val keyBase = stringPreferencesKey("base_url")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            providerId = p[keyProvider] ?: "",
            apiKey = p[keyApi] ?: "",
            model = p[keyModel] ?: "",
            baseUrl = p[keyBase] ?: ""
        )
    }

    suspend fun save(providerId: String, apiKey: String, model: String, baseUrl: String) {
        context.dataStore.edit { p ->
            p[keyProvider] = providerId
            p[keyApi] = apiKey
            p[keyModel] = model
            p[keyBase] = baseUrl
        }
    }
}
