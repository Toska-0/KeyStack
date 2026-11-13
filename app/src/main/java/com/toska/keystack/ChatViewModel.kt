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

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _settings = MutableStateFlow(Settings("", "", "", ""))
    val settings: StateFlow<Settings> = _settings

    private var job: Job? = null

    fun load(store: SettingsStore) {
        viewModelScope.launch {
            store.settings.collect { _settings.value = it }
        }
    }

    fun send(text: String) {
        val s = _settings.value
        if (text.isBlank() || _busy.value || s.apiKey.isBlank() || s.model.isBlank()) return
        val user = UiMessage("user", text.trim())
        val history = _messages.value + user
        _messages.value = history + UiMessage("assistant", "", isStreaming = true)
        _busy.value = true
        _error.value = null

        val apiMessages = history.map { ChatMessage(it.role, it.content) }
        job = viewModelScope.launch {
            val sb = StringBuilder()
            client.stream(Providers.byId(s.providerId), s.apiKey, s.baseUrl, s.model, apiMessages)
                .collect { ev ->
                    when (ev) {
                        is StreamEvent.Delta -> {
                            sb.append(ev.text)
                            _messages.value =
                                _messages.value.dropLast(1) + UiMessage("assistant", sb.toString(), isStreaming = true)
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
    }

    fun clearChat() {
        job?.cancel()
        _messages.value = emptyList()
        _error.value = null
        _busy.value = false
    }
}
