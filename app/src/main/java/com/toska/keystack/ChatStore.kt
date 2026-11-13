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
data class StoredMessage(
    val role: String,
    val content: String,
    val images: List<String> = emptyList()
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
    val messages: List<StoredMessage> = emptyList()
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
