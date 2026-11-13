package com.toska.keystack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class UiMessage(val role: String, val content: String, val isStreaming: Boolean = false)

class ChatViewModel : ViewModel() {

    private val client = LlmClient()
    private lateinit var store: SettingsStore
    private lateinit var chatStore: ChatStore
    private var initialized = false

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

        viewModelScope.launch {
            settingsStore.settings.collect { _settings.value = it }
        }
        viewModelScope.launch {
            val loaded = chatStore.load()
            _sessions.value = loaded
            val first = loaded.firstOrNull { it.pinned } ?: loaded.firstOrNull()
            _currentId.value = first?.id
            _messages.value = first?.messages?.map { UiMessage(it.role, it.content) } ?: emptyList()
        }
    }

    private fun persist() {
        if (!::chatStore.isInitialized) return
        viewModelScope.launch { chatStore.save(_sessions.value) }
    }

    private fun persistCurrentMessages() {
        val id = _currentId.value ?: return
        val stored = _messages.value.filter { !it.isStreaming }.map { StoredMessage(it.role, it.content) }
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
            ?.messages?.map { UiMessage(it.role, it.content) } ?: emptyList()
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
            _messages.value = next?.messages?.map { UiMessage(it.role, it.content) } ?: emptyList()
        }
        persist()
    }

    fun send(text: String) {
        val s = _settings.value
        if (text.isBlank() || _busy.value || s.apiKey.isBlank() || s.model.isBlank()) return

        var session = _sessions.value.firstOrNull { it.id == _currentId.value }
        if (session == null) session = openSession(s.providerId, s.model)
        val sessionId = session.id

        val user = UiMessage("user", text.trim())
        val history = _messages.value + user
        _messages.value = history + UiMessage("assistant", "", isStreaming = true)
        _busy.value = true
        _error.value = null

        val stored = history.map { StoredMessage(it.role, it.content) }
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

        val apiMessages = history.map { ChatMessage(it.role, it.content) }
        job = viewModelScope.launch {
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
}
