package com.toska.keystack

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

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
    val pendingPrompt: String? = null
)

class ChatStore(context: Context) {

    private val file = File(context.filesDir, "chats.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    suspend fun load(): List<ChatSession> = withContext(Dispatchers.IO) {
        try {
            if (file.exists()) json.decodeFromString<List<ChatSession>>(file.readText()) else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun save(sessions: List<ChatSession>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                file.writeText(json.encodeToString(sessions))
            } catch (e: Exception) {
                // ignore write errors
            }
        }
    }
}
