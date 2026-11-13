package com.toska.keystack

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiMessage(
    val role: String,
    val content: String,
    val isStreaming: Boolean = false,
    val images: List<String> = emptyList()
)

/** Десктоп-версия ViewModel: обычный класс со своей coroutine-областью (без androidx.lifecycle). */
class ChatViewModel {

    private val client = LlmClient()
    private lateinit var store: SettingsStore
    private lateinit var chatStore: ChatStore
    private var initialized = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _settings = MutableStateFlow(Settings("", "", "", ""))
    val settings: StateFlow<Settings> = _settings

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions

    private val _currentId = MutableStateFlow<String?>(null)
    val currentId: StateFlow<String?> = _currentId

    private var job: Job? = null

    fun init(settingsStore: SettingsStore, chatStore: ChatStore) {
        if (initialized) return
        initialized = true
        store = settingsStore
        this.chatStore = chatStore

        scope.launch {
            settingsStore.settings.collect { _settings.value = it }
        }
        scope.launch {
            val loaded = chatStore.load()
            _sessions.value = loaded
            val first = loaded.firstOrNull { it.pinned } ?: loaded.firstOrNull()
            _currentId.value = first?.id
            _messages.value = first?.messages?.map { UiMessage(it.role, it.content, images = it.images) } ?: emptyList()
        }
    }

    private fun persist() {
        if (!::chatStore.isInitialized) return
        scope.launch { chatStore.save(_sessions.value) }
    }

    private fun persistCurrentMessages() {
        val id = _currentId.value ?: return
        val stored = _messages.value.filter { !it.isStreaming }.map { StoredMessage(it.role, it.content, it.images) }
        _sessions.value = _sessions.value.map {
            if (it.id == id) it.copy(updatedAt = System.currentTimeMillis(), messages = stored) else it
        }
        persist()
    }

    private fun openSession(providerId: String, model: String): ChatSession {
        val now = System.currentTimeMillis()
        val session = ChatSession(
            id = "chat_$now",
            title = "Новый чат",
            createdAt = now,
            updatedAt = now,
            providerId = providerId,
            model = model
        )
        _sessions.value = listOf(session) + _sessions.value
        _currentId.value = session.id
        return session
    }

    fun newChat() {
        stop()
        val s = _settings.value
        openSession(s.providerId, s.model)
        _messages.value = emptyList()
        _error.value = null
        persist()
    }

    fun selectChat(id: String) {
        stop()
        _currentId.value = id
        _messages.value = _sessions.value.firstOrNull { it.id == id }
            ?.messages?.map { UiMessage(it.role, it.content, images = it.images) } ?: emptyList()
        _error.value = null
    }

    fun renameChat(id: String, title: String) {
        if (title.isBlank()) return
        _sessions.value = _sessions.value.map {
            if (it.id == id) it.copy(title = title.trim()) else it
        }
        persist()
    }

    fun togglePin(id: String) {
        _sessions.value = _sessions.value.map {
            if (it.id == id) it.copy(pinned = !it.pinned) else it
        }
        persist()
    }

    fun deleteChat(id: String) {
        stop()
        val wasCurrent = _currentId.value == id
        _sessions.value = _sessions.value.filter { it.id != id }
        if (wasCurrent) {
            val next = _sessions.value.firstOrNull()
            _currentId.value = next?.id
            _messages.value = next?.messages?.map { UiMessage(it.role, it.content, images = it.images) } ?: emptyList()
        }
        persist()
    }

    fun send(text: String, images: List<String> = emptyList()) {
        val s = _settings.value
        if ((text.isBlank() && images.isEmpty()) || _busy.value || s.apiKey.isBlank() || s.model.isBlank()) return

        var session = _sessions.value.firstOrNull { it.id == _currentId.value }
        if (session == null) session = openSession(s.providerId, s.model)
        val sessionId = session.id

        val user = UiMessage("user", text.trim(), images = images)
        val history = _messages.value + user

        val stored = history.map { StoredMessage(it.role, it.content, it.images) }
        _sessions.value = _sessions.value.map {
            if (it.id == sessionId) {
                val newTitle = if (it.title == "Новый чат") {
                    val t = text.trim()
                    t.take(40) + if (t.length > 40) "…" else ""
                } else it.title
                it.copy(title = newTitle, updatedAt = System.currentTimeMillis(), messages = stored)
            } else it
        }
        persist()

        startStream(history)
    }

    /** Перегенерация последнего ответа ассистента. */
    fun regenerate() {
        if (_busy.value) return
        var history = _messages.value
        if (history.lastOrNull()?.role == "assistant") history = history.dropLast(1)
        if (history.isEmpty() || history.last().role != "user") return
        _error.value = null
        startStream(history)
    }

    private fun startStream(history: List<UiMessage>) {
        val s = _settings.value
        _messages.value = history + UiMessage("assistant", "", isStreaming = true)
        _busy.value = true
        _error.value = null

        job = scope.launch {
            val workspacePrompt = withContext(Dispatchers.IO) { buildWorkspacePrompt(s.workspacePath) }
            val apiMessages = buildList {
                if (workspacePrompt.isNotBlank()) add(ChatMessage("system", workspacePrompt))
                addAll(history.map { ChatMessage(it.role, it.content, it.images) })
            }
            val sb = StringBuilder()
            client.stream(Providers.byId(s.providerId), s.apiKey, s.baseUrl, s.model, apiMessages)
                .collect { ev ->
                    when (ev) {
                        is StreamEvent.Delta -> {
                            sb.append(ev.text)
                            _messages.value = _messages.value.dropLast(1) +
                                UiMessage("assistant", sb.toString(), isStreaming = true)
                        }
                        is StreamEvent.Failure -> _error.value = ev.message
                    }
                }
            val final = sb.toString()
            _messages.value = if (final.isBlank() && _error.value != null) {
                _messages.value.dropLast(1)
            } else {
                _messages.value.dropLast(1) + UiMessage("assistant", final)
            }
            _busy.value = false
            persistCurrentMessages()
        }
    }

    /** Переключить активного провайдера (ключ должен быть сохранён в настройках). */
    fun switchProvider(providerId: String) {
        if (!::store.isInitialized) return
        scope.launch { store.setActiveProvider(providerId) }
    }

    /** Сменить модель активного провайдера прямо из чата. */
    fun setModel(model: String) {
        if (!::store.isInitialized) return
        val pid = _settings.value.providerId
        scope.launch { store.saveModel(pid, model) }
    }

    fun stop() {
        job?.cancel()
        _busy.value = false
        val list = _messages.value
        val last = list.lastOrNull()
        if (last?.isStreaming == true) {
            _messages.value = list.dropLast(1) + last.copy(isStreaming = false)
        }
        if (::chatStore.isInitialized) persistCurrentMessages()
    }

    fun clearChat() {
        job?.cancel()
        _messages.value = emptyList()
        _error.value = null
        _busy.value = false
        if (::chatStore.isInitialized) persistCurrentMessages()
    }

    /** Остановка фоновой области при закрытии окна. */
    fun dispose() {
        scope.cancel()
    }
}
