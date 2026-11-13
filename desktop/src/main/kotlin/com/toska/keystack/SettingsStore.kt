package com.toska.keystack

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

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
    val workspacePath: String = ""
)

/** Состояние на диске: ключи/модели/baseURL отдельно для каждого провайдера. */
@Serializable
private data class StoredSettings(
    val providerId: String = "",
    val themeMode: String = "system",
    val dynamicColor: Boolean = true,
    val keys: Map<String, String> = emptyMap(),
    val models: Map<String, String> = emptyMap(),
    val baseUrls: Map<String, String> = emptyMap(),
    val workspacePath: String = ""
)

/** Десктоп-версия: настройки хранятся в settings.json в каталоге данных приложения. */
class SettingsStore(baseDir: File) {

    private val file = File(baseDir, "settings.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private fun loadStored(): StoredSettings = try {
        if (file.exists()) json.decodeFromString<StoredSettings>(file.readText()) else StoredSettings()
    } catch (e: Exception) {
        StoredSettings()
    }

    private val _state = MutableStateFlow(loadStored())

    val settings: Flow<Settings> = _state.map { s ->
        Settings(
            providerId = s.providerId,
            apiKey = s.keys[s.providerId] ?: "",
            model = s.models[s.providerId] ?: "",
            baseUrl = s.baseUrls[s.providerId] ?: "",
            themeMode = s.themeMode,
            dynamicColor = s.dynamicColor,
            savedKeys = s.keys,
            savedModels = s.models,
            savedBaseUrls = s.baseUrls,
            workspacePath = s.workspacePath
        )
    }

    private suspend fun update(transform: (StoredSettings) -> StoredSettings) {
        val next = mutex.withLock {
            val updated = transform(_state.value)
            withContext(Dispatchers.IO) {
                try {
                    file.parentFile?.mkdirs()
                    file.writeText(json.encodeToString(updated))
                } catch (e: Exception) {
                    // ignore write errors
                }
            }
            updated
        }
        _state.value = next
    }

    /** Сохраняет ключ/модель/baseURL для конкретного провайдера и делает его активным. */
    suspend fun saveProviderCreds(providerId: String, apiKey: String, model: String, baseUrl: String) {
        update { c ->
            c.copy(
                providerId = providerId,
                keys = c.keys + (providerId to apiKey),
                models = c.models + (providerId to model),
                baseUrls = if (baseUrl.isNotBlank()) c.baseUrls + (providerId to baseUrl) else c.baseUrls
            )
        }
    }

    suspend fun setActiveProvider(providerId: String) {
        update { it.copy(providerId = providerId) }
    }

    suspend fun saveModel(providerId: String, model: String) {
        update { it.copy(models = it.models + (providerId to model)) }
    }

    suspend fun saveTheme(themeMode: String, dynamicColor: Boolean) {
        update { it.copy(themeMode = themeMode, dynamicColor = dynamicColor) }
    }

    suspend fun saveWorkspace(path: String) {
        update { it.copy(workspacePath = path) }
    }
}
