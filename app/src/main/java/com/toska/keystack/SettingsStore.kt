package com.toska.keystack

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "keystack")

data class Settings(
    val providerId: String,
    val apiKey: String,
    val model: String,
    val baseUrl: String,
    val themeMode: String = "system",
    val dynamicColor: Boolean = true,
    val savedKeys: Map<String, String> = emptyMap(),
    val savedModels: Map<String, String> = emptyMap(),
    val savedBaseUrls: Map<String, String> = emptyMap(),
    val fontScale: Float = 1f,
    val messageWidth: Int = 760,
    val animationsEnabled: Boolean = true,
    val accentColor: String = "orange"
)

/** Ключи/модели/baseURL хранятся отдельно для каждого провайдера. */
@Serializable
private data class ProviderCreds(
    val keys: Map<String, String> = emptyMap(),
    val models: Map<String, String> = emptyMap(),
    val baseUrls: Map<String, String> = emptyMap()
)

class SettingsStore(private val context: Context) {

    private val keyProvider = stringPreferencesKey("provider")
    private val keyApi = stringPreferencesKey("api_key") // legacy
    private val keyModel = stringPreferencesKey("model") // legacy
    private val keyBase = stringPreferencesKey("base_url") // legacy
    private val keyCreds = stringPreferencesKey("creds_json")
    private val keyTheme = stringPreferencesKey("theme_mode")
    private val keyDynamic = booleanPreferencesKey("dynamic_color")
    private val keyFontScale = floatPreferencesKey("font_scale")
    private val keyMessageWidth = intPreferencesKey("message_width")
    private val keyAnimations = booleanPreferencesKey("animations_enabled")
    private val keyAccent = stringPreferencesKey("accent_color")

    private val json = Json { ignoreUnknownKeys = true }

    private fun parseCreds(raw: String?): ProviderCreds =
        raw?.let { runCatching { json.decodeFromString<ProviderCreds>(it) }.getOrNull() } ?: ProviderCreds()

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        var creds = parseCreds(p[keyCreds])
        val providerId = p[keyProvider] ?: ""
        // Разовая миграция старых настроек (один ключ на всё приложение)
        if (creds.keys.isEmpty() && !p[keyApi].isNullOrBlank()) {
            creds = ProviderCreds(
                keys = mapOf(providerId to (p[keyApi] ?: "")),
                models = mapOf(providerId to (p[keyModel] ?: "")),
                baseUrls = mapOf(providerId to (p[keyBase] ?: ""))
            )
        }
        Settings(
            providerId = providerId,
            apiKey = creds.keys[providerId] ?: "",
            model = creds.models[providerId] ?: "",
            baseUrl = creds.baseUrls[providerId] ?: "",
            themeMode = p[keyTheme] ?: "system",
            dynamicColor = p[keyDynamic] ?: true,
            savedKeys = creds.keys,
            savedModels = creds.models,
            savedBaseUrls = creds.baseUrls,
            fontScale = p[keyFontScale] ?: 1f,
            messageWidth = p[keyMessageWidth] ?: 760,
            animationsEnabled = p[keyAnimations] ?: true,
            accentColor = p[keyAccent] ?: "orange"
        )
    }

    private suspend fun updateCreds(transform: (ProviderCreds) -> ProviderCreds) {
        context.dataStore.edit { p ->
            val updated = transform(parseCreds(p[keyCreds]))
            p[keyCreds] = json.encodeToString(updated)
        }
    }

    /** Сохраняет ключ/модель/baseURL для конкретного провайдера и делает его активным. */
    suspend fun saveProviderCreds(providerId: String, apiKey: String, model: String, baseUrl: String) {
        updateCreds { c ->
            c.copy(
                keys = c.keys + (providerId to apiKey),
                models = c.models + (providerId to model),
                baseUrls = if (baseUrl.isNotBlank()) c.baseUrls + (providerId to baseUrl) else c.baseUrls
            )
        }
        context.dataStore.edit { p -> p[keyProvider] = providerId }
    }

    suspend fun setActiveProvider(providerId: String) {
        context.dataStore.edit { p -> p[keyProvider] = providerId }
    }

    suspend fun saveModel(providerId: String, model: String) {
        updateCreds { c -> c.copy(models = c.models + (providerId to model)) }
    }

    suspend fun saveUiPreferences(fontScale: Float, messageWidth: Int, animationsEnabled: Boolean) {
        context.dataStore.edit { p ->
            p[keyFontScale] = fontScale.coerceIn(0.85f, 1.4f)
            p[keyMessageWidth] = messageWidth.coerceIn(560, 1100)
            p[keyAnimations] = animationsEnabled
        }
    }

    suspend fun saveAccentColor(accentColor: String) { context.dataStore.edit { it[keyAccent] = accentColor } }

    suspend fun saveTheme(themeMode: String, dynamicColor: Boolean) {
        context.dataStore.edit { p ->
            p[keyTheme] = themeMode
            p[keyDynamic] = dynamicColor
        }
    }
}
