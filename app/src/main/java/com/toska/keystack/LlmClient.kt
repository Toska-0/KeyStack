package com.toska.keystack

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String)

sealed interface StreamEvent {
    data class Delta(val text: String) : StreamEvent
    data class Failure(val message: String) : StreamEvent
}

class LlmClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun stream(
        provider: Provider,
        apiKey: String,
        baseUrl: String,
        model: String,
        messages: List<ChatMessage>
    ): Flow<StreamEvent> = callbackFlow {
        val effectiveBase = if (baseUrl.isNotBlank()) baseUrl.trimEnd('/') else provider.baseUrl
        val request = when (provider.api) {
            ApiKind.OPENAI -> openAiRequest(effectiveBase, apiKey, model, messages)
            ApiKind.ANTHROPIC -> anthropicRequest(effectiveBase, apiKey, model, messages)
            ApiKind.GEMINI -> geminiRequest(effectiveBase, apiKey, model, messages)
        }
        val call = client.newCall(request)
        val worker = Thread {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val err = response.body?.string()?.take(500) ?: ""
                        trySend(StreamEvent.Failure("HTTP ${response.code}: $err"))
                        return@use
                    }
                    val source = response.body?.source()
                    if (source == null) {
                        trySend(StreamEvent.Failure("Пустой ответ"))
                        return@use
                    }
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        val text = parseChunk(provider.api, data)
                        if (text.isNotEmpty()) trySend(StreamEvent.Delta(text))
                    }
                }
            } catch (e: Exception) {
                trySend(StreamEvent.Failure(e.message ?: "Ошибка сети"))
            } finally {
                close()
            }
        }
        worker.start()
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    private fun parseChunk(api: ApiKind, data: String): String {
        if (data.isBlank()) return ""
        return try {
            val obj = json.parseToJsonElement(data).jsonObject
            when (api) {
                ApiKind.OPENAI -> obj["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("delta")?.jsonObject?.get("content")
                    ?.jsonPrimitive?.contentOrNull ?: ""

                ApiKind.ANTHROPIC -> if (obj["type"]?.jsonPrimitive?.contentOrNull == "content_block_delta")
                    obj["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
                else ""

                ApiKind.GEMINI -> obj["candidates"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun jsonBody(body: String) = body.toRequestBody("application/json".toMediaType())

    private fun openAiRequest(base: String, apiKey: String, model: String, messages: List<ChatMessage>): Request {
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("messages", buildJsonArray {
                messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                    })
                }
            })
        }.toString()
        return Request.Builder()
            .url("$base/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(jsonBody(body))
            .build()
    }

    private fun anthropicRequest(base: String, apiKey: String, model: String, messages: List<ChatMessage>): Request {
        val system = messages.filter { it.role == "system" }.joinToString("\n") { it.content }
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 4096)
            put("stream", true)
            if (system.isNotBlank()) put("system", system)
            put("messages", buildJsonArray {
                messages.filter { it.role != "system" }.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                    })
                }
            })
        }.toString()
        return Request.Builder()
            .url("$base/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(jsonBody(body))
            .build()
    }

    private fun geminiRequest(base: String, apiKey: String, model: String, messages: List<ChatMessage>): Request {
        val system = messages.filter { it.role == "system" }.joinToString("\n") { it.content }
        val body = buildJsonObject {
            if (system.isNotBlank()) {
                put("system_instruction", buildJsonObject {
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) })
                })
            }
            put("contents", buildJsonArray {
                messages.filter { it.role != "system" }.forEach { m ->
                    add(buildJsonObject {
                        put("role", if (m.role == "assistant") "model" else "user")
                        put("parts", buildJsonArray { add(buildJsonObject { put("text", m.content) }) })
                    })
                }
            })
        }.toString()
        return Request.Builder()
            .url("$base/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey")
            .post(jsonBody(body))
            .build()
    }
}
