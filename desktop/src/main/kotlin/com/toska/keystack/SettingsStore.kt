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
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.util.Base64
import com.sun.jna.platform.win32.Crypt32Util

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
    val workspacePath: String = "",
    val fontScale: Float = 1f,
    val messageWidth: Int = 760,
    val animationsEnabled: Boolean = true,
    val accentColor: String = "orange"
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
    val workspacePath: String = "",
    val fontScale: Float = 1f,
    val messageWidth: Int = 760,
    val animationsEnabled: Boolean = true,
    val accentColor: String = "orange"
)

/** Десктоп-версия: настройки хранятся в settings.json в каталоге данных приложения. */
class SettingsStore(baseDir: File) {

    private val file = File(baseDir, "settings.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private fun isWindows() = System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)

    private fun protectSecret(value: String): String {
        if (value.isEmpty() || !isWindows()) return value
        return "dpapi:" + Base64.getEncoder().encodeToString(
            Crypt32Util.cryptProtectData(value.toByteArray(Charsets.UTF_8))
        )
    }

    private fun revealSecret(value: String): String {
        if (!value.startsWith("dpapi:")) return value
        if (!isWindows()) return ""
        return runCatching {
            String(Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(value.removePrefix("dpapi:"))), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun loadStored(): StoredSettings {
        if (!file.exists()) return StoredSettings()
        val backup = File(file.parentFile, "settings.json.bak")
        val raw = try {
            json.decodeFromString<StoredSettings>(file.readText(Charsets.UTF_8))
        } catch (primary: Exception) {
            if (!backup.exists()) throw IllegalStateException("Не удалось прочитать settings.json. Файл не был изменён.", primary)
            val recovered = try { json.decodeFromString<StoredSettings>(backup.readText(Charsets.UTF_8)) }
            catch (backupError: Exception) { throw IllegalStateException("settings.json и резервная копия повреждены.", backupError) }
            runCatching { Files.copy(file.toPath(), File(file.parentFile, "settings.json.corrupt").toPath(), StandardCopyOption.REPLACE_EXISTING) }
            runCatching { Files.copy(backup.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            recovered
        }
        // Migrate old plaintext API keys immediately so the legacy file/backup does not retain secrets.
        if (isWindows() && raw.keys.values.any { it.isNotEmpty() && !it.startsWith("dpapi:") }) {
            val encrypted = raw.copy(keys = raw.keys.mapValues { (_, value) -> protectSecret(value) })
            val temp = File(file.parentFile, "settings.json.migrate")
            temp.writeText(json.encodeToString(encrypted), Charsets.UTF_8)
            try { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            backup.delete()
        }
        return raw.copy(keys = raw.keys.mapValues { (_, value) -> revealSecret(value) })
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
            workspacePath = s.workspacePath,
            fontScale = s.fontScale,
            messageWidth = s.messageWidth,
            animationsEnabled = s.animationsEnabled,
            accentColor = s.accentColor
        )
    }

    private suspend fun update(transform: (StoredSettings) -> StoredSettings) {
        val next = mutex.withLock {
            val updated = transform(_state.value)
            withContext(Dispatchers.IO) {
                file.parentFile?.mkdirs()
                val persisted = updated.copy(keys = updated.keys.mapValues { (_, value) -> protectSecret(value) })
                val temp = File(file.parentFile, "settings.json.tmp")
                val backup = File(file.parentFile, "settings.json.bak")
                temp.writeText(json.encodeToString(persisted), Charsets.UTF_8)
                if (file.exists()) Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
                try {
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } finally { if (temp.exists()) temp.delete() }
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

    suspend fun saveAccentColor(accentColor: String) { update { it.copy(accentColor = accentColor) } }

    suspend fun saveTheme(themeMode: String, dynamicColor: Boolean) {
        update { it.copy(themeMode = themeMode, dynamicColor = dynamicColor) }
    }

    suspend fun saveWorkspace(path: String) {
        update { it.copy(workspacePath = path) }
    }

    suspend fun saveUiPreferences(fontScale: Float, messageWidth: Int, animationsEnabled: Boolean) {
        update {
            it.copy(
                fontScale = fontScale.coerceIn(0.85f, 1.4f),
                messageWidth = messageWidth.coerceIn(560, 1100),
                animationsEnabled = animationsEnabled
            )
        }
    }
}
