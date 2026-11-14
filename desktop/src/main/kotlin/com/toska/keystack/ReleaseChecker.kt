package com.toska.keystack

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String
)

object ReleaseChecker {
    const val CURRENT_VERSION = "0.1.0"
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/Toska-0/KeyStack/releases/latest"

    suspend fun checkForUpdate(): ReleaseInfo? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "KeyStack-Update-Checker")
            }
            if (connection.responseCode !in 200..299) return@withContext null
            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = Json.parseToJsonElement(payload).jsonObject
            val tag = json["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@withContext null
            val url = json["html_url"]?.jsonPrimitive?.contentOrNull ?: return@withContext null
            val title = json["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "KeyStack $tag"
            val body = json["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (!isNewer(tag, CURRENT_VERSION)) return@withContext null
            ReleaseInfo(tag, title, body.take(1800), url)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun isNewer(remote: String, current: String): Boolean {
        fun parts(value: String): List<Int> =
            Regex("\\d+").findAll(value).mapNotNull { it.value.toIntOrNull() }.toList()
        val remoteParts = parts(remote)
        val currentParts = parts(current)
        if (remoteParts.isEmpty() || currentParts.isEmpty()) return false
        for (index in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val a = remoteParts.getOrElse(index) { 0 }
            val b = currentParts.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
