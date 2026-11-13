package com.toska.keystack

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** images — data URI вида "data:image/jpeg;base64,..." */
data class ChatMessage(val role: String, val content: String, val images: List<String> = emptyList())

sealed interface StreamEvent {
    data class Delta(val text: String) : StreamEvent
    data class Failure(val message: String) : StreamEvent
}

private data class ModelEntry(val id: String, val free: Boolean?)
data class ProviderQuota(val used: Double, val limit: Double, val remaining: Double, val currency: String = "$")

class LlmClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** Реальная квота OpenRouter из /auth/key: лимит ключа, не контекстное окно модели. */
    suspend fun fetchQuota(provider: Provider, apiKey: String): ProviderQuota? = withContext(Dispatchers.IO) {
        if (provider.id != "openrouter" || apiKey.isBlank()) return@withContext null
        try {
            val request = Request.Builder().url("https://openrouter.ai/api/v1/auth/key")
                .header("Authorization", "Bearer $apiKey").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val data = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject["data"]?.jsonObject ?: return@withContext null
                val limit = data["limit"]?.jsonPrimitive?.doubleOrNull
                val remaining = data["limit_remaining"]?.jsonPrimitive?.doubleOrNull
                val usage = data["usage"]?.jsonPrimitive?.doubleOrNull
                if (limit == null || remaining == null) return@withContext null
                ProviderQuota(usage ?: (limit - remaining).coerceAtLeast(0.0), limit, remaining.coerceAtLeast(0.0))
            }
        } catch (_: Exception) { null }
    }
    /** Точная длина контекстного окна модели из публичного каталога OpenRouter. */
    suspend fun fetchModelContextLimit(provider: Provider, model: String): Int? = withContext(Dispatchers.IO) {
        if (provider.id != "openrouter" || model.isBlank()) return@withContext null
        try {
            val request = Request.Builder().url("https://openrouter.ai/api/v1/models").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val data = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject["data"]?.jsonArray ?: return@withContext null
                data.firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull == model }
                    ?.jsonObject?.get("context_length")?.jsonPrimitive?.intOrNull
            }
        } catch (_: Exception) { null }
    }
    /**
     * Загружает список моделей, доступных с этим ключом.
     * Для OpenRouter: если ключ бесплатного тарифа — остаются только бесплатные модели.
     */
    suspend fun listModels(provider: Provider, apiKey: String, baseUrl: String): List<String> =
        withContext(Dispatchers.IO) {
            val effectiveBase = if (baseUrl.isNotBlank()) baseUrl.trimEnd('/') else provider.baseUrl
            if (effectiveBase.isBlank()) return@withContext emptyList()
            val request = when (provider.api) {
                ApiKind.OPENAI -> Request.Builder()
                    .url("$effectiveBase/models")
                    .header("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                ApiKind.ANTHROPIC -> Request.Builder()
                    .url("$effectiveBase/v1/models")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .get()
                    .build()
                ApiKind.GEMINI -> Request.Builder()
                    .url("$effectiveBase/v1beta/models?key=$apiKey&pageSize=200")
                    .get()
                    .build()
            }
            val entries: List<ModelEntry> = try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList()
                    val body = resp.body?.string() ?: return@use emptyList()
                    val obj = json.parseToJsonElement(body).jsonObject
                    when (provider.api) {
                        ApiKind.GEMINI -> obj["models"]?.jsonArray?.mapNotNull { m ->
                            val name = m.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                                ?.removePrefix("models/") ?: return@mapNotNull null
                            ModelEntry(name, null)
                        } ?: emptyList()

                        else -> obj["data"]?.jsonArray?.mapNotNull { m ->
                            val o = m.jsonObject
                            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                            val pricing = o["pricing"]?.jsonObject
                            val free = if (pricing != null) {
                                val p = pricing["prompt"]?.jsonPrimitive?.contentOrNull
                                val c = pricing["completion"]?.jsonPrimitive?.contentOrNull
                                if (p != null && c != null) p == "0" && c == "0" else null
                            } else null
                            ModelEntry(id, free)
                        } ?: emptyList()
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }

            var ids = entries.map { it.id }
            if (provider.id == "openrouter" && openRouterFreeTier(apiKey)) {
                val freeOnly = entries.filter { it.free == true }.map { it.id }
                if (freeOnly.isNotEmpty()) ids = freeOnly
            }
            ids.sorted()
        }

    /** Проверяет тариф ключа OpenRouter: true — бесплатный (is_free_tier). */
    private suspend fun openRouterFreeTier(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://openrouter.ai/api/v1/auth/key")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext false
                val body = r.body?.string() ?: return@withContext false
                val obj = json.parseToJsonElement(body).jsonObject
                obj["data"]?.jsonObject?.get("is_free_tier")?.jsonPrimitive?.booleanOrNull ?: false
            }
        } catch (e: Exception) {
            false
        }
    }

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

    /** "data:image/jpeg;base64,XXX" -> ("image/jpeg", "XXX") */
    private fun splitDataUri(uri: String): Pair<String, String> {
        val marker = ";base64,"
        val idx = uri.indexOf(marker)
        if (idx < 0) return "image/jpeg" to ""
        return uri.substring("data:".length, idx) to uri.substring(idx + marker.length)
    }

    private fun openAiRequest(base: String, apiKey: String, model: String, messages: List<ChatMessage>): Request {
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("messages", buildJsonArray {
                messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role)
                        if (m.images.isEmpty()) {
                            put("content", m.content)
                        } else {
                            put("content", buildJsonArray {
                                add(buildJsonObject {
                                    put("type", "text")
                                    put("text", m.content)
                                })
                                m.images.forEach { img ->
                                    add(buildJsonObject {
                                        put("type", "image_url")
                                        put("image_url", buildJsonObject { put("url", img) })
                                    })
                                }
                            })
                        }
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
                        if (m.images.isEmpty()) {
                            put("content", m.content)
                        } else {
                            put("content", buildJsonArray {
                                m.images.forEach { img ->
                                    val (mime, data) = splitDataUri(img)
                                    add(buildJsonObject {
                                        put("type", "image")
                                        put("source", buildJsonObject {
                                            put("type", "base64")
                                            put("media_type", mime)
                                            put("data", data)
                                        })
                                    })
                                }
                                add(buildJsonObject {
                                    put("type", "text")
                                    put("text", m.content)
                                })
                            })
                        }
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
                        put("parts", buildJsonArray {
                            m.images.forEach { img ->
                                val (mime, data) = splitDataUri(img)
                                add(buildJsonObject {
                                    put("inline_data", buildJsonObject {
                                        put("mime_type", mime)
                                        put("data", data)
                                    })
                                })
                            }
                            add(buildJsonObject { put("text", m.content) })
                        })
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
