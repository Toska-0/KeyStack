package com.toska.keystack

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException

@Serializable
data class AttachedFile(
    val name: String,
    val content: String = "",
    val sizeBytes: Long = 0L
)

@Serializable
data class StoredMessage(
    val role: String,
    val content: String,
    val images: List<String> = emptyList(),
    val id: String = "",
    val createdAt: Long = 0L,
    val generationMs: Long = 0L,
    val tokenEstimate: Int = 0,
    val files: List<AttachedFile> = emptyList()
)

@Serializable
data class ChatSession(
    val id: String,
    val title: String = "Новый чат",
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val providerId: String = "",
    val model: String = "",
    val messages: List<StoredMessage> = emptyList(),
    val parentSessionId: String? = null,
    val branchMessageId: String? = null,
    val pendingPrompt: String? = null,
    val pendingImages: List<String> = emptyList(),
    val pendingFiles: List<AttachedFile> = emptyList()
)

/** Десктоп-версия: чаты хранятся в JSON-файле в каталоге данных приложения. */
class ChatStore(baseDir: File) {

    private val file = File(baseDir, "chats.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    suspend fun load(): List<ChatSession> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        val backup = File(file.parentFile, "chats.json.bak")
        fun decode(source: File): List<ChatSession> = json.decodeFromString<List<ChatSession>>(source.readText(Charsets.UTF_8))
        try { decode(file) } catch (primary: Exception) {
            if (backup.exists()) {
                try {
                    val recovered = decode(backup)
                    runCatching { Files.copy(file.toPath(), File(file.parentFile, "chats.json.corrupt").toPath(), StandardCopyOption.REPLACE_EXISTING) }
                    runCatching { Files.copy(backup.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
                    recovered
                } catch (backupError: Exception) {
                    throw IOException("Не удалось прочитать историю чатов. Файл и резервная копия повреждены.", backupError)
                }
            } else {
                runCatching { Files.copy(file.toPath(), File(file.parentFile, "chats.json.corrupt").toPath(), StandardCopyOption.REPLACE_EXISTING) }
                throw IOException("Файл истории повреждён. Копия сохранена как chats.json.corrupt.", primary)
            }
        }
    }

    suspend fun save(sessions: List<ChatSession>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "chats.json.tmp")
            val backup = File(file.parentFile, "chats.json.bak")
            temp.writeText(json.encodeToString(sessions), Charsets.UTF_8)
            if (file.exists()) Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally { if (temp.exists()) temp.delete() }
        }
    }
}
