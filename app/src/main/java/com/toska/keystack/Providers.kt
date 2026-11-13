package com.toska.keystack

enum class ApiKind { OPENAI, ANTHROPIC, GEMINI }

data class Provider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val api: ApiKind,
    val models: List<String>,
    val keyUrl: String,
    val needsBaseUrl: Boolean = false
)

object Providers {
    val all = listOf(
        Provider(
            "openai", "OpenAI", "https://api.openai.com/v1", ApiKind.OPENAI,
            listOf("gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini", "o4-mini"),
            "https://platform.openai.com/api-keys"
        ),
        Provider(
            "anthropic", "Anthropic (Claude)", "https://api.anthropic.com", ApiKind.ANTHROPIC,
            listOf("claude-sonnet-4-5", "claude-haiku-4-5", "claude-opus-4-1", "claude-3-5-haiku-latest"),
            "https://console.anthropic.com/settings/keys"
        ),
        Provider(
            "google", "Google Gemini", "https://generativelanguage.googleapis.com", ApiKind.GEMINI,
            listOf("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash", "gemini-2.0-flash-lite"),
            "https://aistudio.google.com/apikey"
        ),
        Provider(
            "mistral", "Mistral AI", "https://api.mistral.ai/v1", ApiKind.OPENAI,
            listOf("mistral-large-latest", "mistral-small-latest", "magistral-medium-latest", "codestral-latest"),
            "https://console.mistral.ai/api-keys"
        ),
        Provider(
            "groq", "Groq", "https://api.groq.com/openai/v1", ApiKind.OPENAI,
            listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "openai/gpt-oss-120b", "deepseek-r1-distill-llama-70b"),
            "https://console.groq.com/keys"
        ),
        Provider(
            "deepseek", "DeepSeek", "https://api.deepseek.com/v1", ApiKind.OPENAI,
            listOf("deepseek-chat", "deepseek-reasoner"),
            "https://platform.deepseek.com/api_keys"
        ),
        Provider(
            "openrouter", "OpenRouter", "https://openrouter.ai/api/v1", ApiKind.OPENAI,
            listOf(
                "openai/gpt-4o-mini",
                "anthropic/claude-sonnet-4.5",
                "google/gemini-2.5-flash",
                "deepseek/deepseek-chat-v3.1",
                "meta-llama/llama-3.3-70b-instruct"
            ),
            "https://openrouter.ai/keys"
        ),
        Provider(
            "custom", "Custom (OpenAI-compatible)", "", ApiKind.OPENAI,
            emptyList(), "", needsBaseUrl = true
        )
    )

    fun byId(id: String): Provider = all.firstOrNull { it.id == id } ?: all.first()
}
