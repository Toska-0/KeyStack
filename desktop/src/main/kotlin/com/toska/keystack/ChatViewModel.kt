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
    val images: List<String> = emptyList(),
    val id: String = System.nanoTime().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val generationMs: Long = 0L,
    val tokenEstimate: Int = 0,
    val files: List<AttachedFile> = emptyList()
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
    private val _apiStatus = MutableStateFlow("Не проверено")
    val apiStatus: StateFlow<String> = _apiStatus

    private val _settings = MutableStateFlow(Settings("", "", "", ""))
    val settings: StateFlow<Settings> = _settings

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions

    private val _currentId = MutableStateFlow<String?>(null)
    val currentId: StateFlow<String?> = _currentId

    private var job: Job? = null
    private val _generationMs = MutableStateFlow(0L)
    val generationMs: StateFlow<Long> = _generationMs
    private val _tokenEstimate = MutableStateFlow(0)
    val tokenEstimate: StateFlow<Int> = _tokenEstimate
    private val _recoverablePrompt = MutableStateFlow<String?>(null)
    val recoverablePrompt: StateFlow<String?> = _recoverablePrompt
    private val _pendingWorkspaceWrites = MutableStateFlow<List<ProposedWorkspaceWrite>>(emptyList())
    val pendingWorkspaceWrites: StateFlow<List<ProposedWorkspaceWrite>> = _pendingWorkspaceWrites

    fun approveWorkspaceWrite(index: Int) {
        val proposal = _pendingWorkspaceWrites.value.getOrNull(index) ?: return
        val path = _settings.value.workspacePath
        scope.launch(Dispatchers.IO) {
            val result = applyWorkspaceWrite(path, proposal)
            _pendingWorkspaceWrites.value = _pendingWorkspaceWrites.value.filterNot { it == proposal }
            _error.value = result
        }
    }

    fun rejectWorkspaceWrites() { _pendingWorkspaceWrites.value = emptyList() }

    fun init(settingsStore: SettingsStore, chatStore: ChatStore) {
        if (initialized) return
        initialized = true
        store = settingsStore
        this.chatStore = chatStore

        scope.launch {
            settingsStore.settings.collect { _settings.value = it }
        }
        scope.launch {
            val loaded = runCatching { chatStore.load() }.getOrElse {
                _error.value = "Не удалось загрузить историю: ${it.message ?: "ошибка файла"}"
                emptyList()
            }
            _sessions.value = loaded
            val first = loaded.firstOrNull { it.pinned } ?: loaded.firstOrNull()
            _currentId.value = first?.id
            _messages.value = first?.messages?.map { UiMessage(it.role, it.content, images = it.images, id = it.id.ifBlank { System.nanoTime().toString() }, createdAt = it.createdAt, generationMs = it.generationMs, tokenEstimate = it.tokenEstimate, files = it.files) } ?: emptyList()
            _recoverablePrompt.value = first?.pendingPrompt
        }
    }

    private fun persist() {
        if (!::chatStore.isInitialized) return
        scope.launch { chatStore.save(_sessions.value) }
    }

    private fun persistCurrentMessages() {
        val id = _currentId.value ?: return
        val stored = _messages.value.filter { !it.isStreaming }.map { StoredMessage(it.role, it.content, it.images, it.id, it.createdAt, it.generationMs, it.tokenEstimate, it.files) }
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
            ?.messages?.map { UiMessage(it.role, it.content, images = it.images, files = it.files) } ?: emptyList()
        _error.value = null
        _recoverablePrompt.value = _sessions.value.firstOrNull { it.id == id }?.pendingPrompt
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
        val wasCurrent = _currentId.value == id
        if (wasCurrent) stop()
        _sessions.value = _sessions.value.filter { it.id != id }
        if (wasCurrent) {
            val next = _sessions.value.firstOrNull()
            _currentId.value = next?.id
            _messages.value = next?.messages?.map { UiMessage(it.role, it.content, images = it.images, files = it.files) } ?: emptyList()
            _recoverablePrompt.value = next?.pendingPrompt
        }
        persist()
    }

    fun send(text: String, images: List<String> = emptyList(), files: List<AttachedFile> = emptyList()) {
        val s = _settings.value
        if ((text.isBlank() && images.isEmpty() && files.isEmpty()) || _busy.value || s.apiKey.isBlank() || s.model.isBlank()) return

        var session = _sessions.value.firstOrNull { it.id == _currentId.value }
        if (session == null) session = openSession(s.providerId, s.model)
        val sessionId = session.id

        val user = UiMessage("user", text.trim(), images = images, files = files, tokenEstimate = estimateTokens(text))
        val history = _messages.value + user

        val stored = history.map { StoredMessage(it.role, it.content, it.images, it.id, it.createdAt, it.generationMs, it.tokenEstimate, it.files) }
        _sessions.value = _sessions.value.map {
            if (it.id == sessionId) {
                val newTitle = if (it.title == "Новый чат") {
                    val t = text.trim().ifBlank { files.firstOrNull()?.name ?: "Вложение" }
                    t.take(40) + if (t.length > 40) "…" else ""
                } else it.title
                it.copy(title = newTitle, updatedAt = System.currentTimeMillis(), messages = stored, pendingPrompt = text.trim(), pendingImages = images, pendingFiles = files)
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
        val startedAt = System.currentTimeMillis()
        _messages.value = history + UiMessage("assistant", "", isStreaming = true)
        _generationMs.value = 0L
        _tokenEstimate.value = history.sumOf { estimateTokens(it.content) }
        _busy.value = true
        _error.value = null
        val requestUser = history.lastOrNull { it.role == "user" }
        if (requestUser != null) {
            _sessions.value = _sessions.value.map { if (it.id == _currentId.value) it.copy(pendingPrompt = requestUser.content, pendingImages = requestUser.images, pendingFiles = requestUser.files) else it }
            persist()
        }

        job = scope.launch {
            val workspacePrompt = withContext(Dispatchers.IO) { buildWorkspacePrompt(s.workspacePath) }
            val webContext = WebSearch.searchForPrompt(history.lastOrNull()?.takeIf { it.role == "user" }?.content.orEmpty())
            val apiMessages = buildList {
                if (workspacePrompt.isNotBlank()) add(ChatMessage("system", workspacePrompt))
                if (webContext.isNotBlank()) add(ChatMessage("system", webContext))
                addAll(history.takeLast(24).map { message ->
                    val fileContext = message.files.joinToString("\n\n") { file ->
                        if (file.content.isBlank()) "Attached file: ${file.name} (content preview unavailable)"
                        else "Attached file: ${file.name}\n```text\n${file.content.take(12_000)}\n```"
                    }
                    ChatMessage(message.role, if (fileContext.isBlank()) message.content.takeLast(12_000) else listOf(message.content.takeLast(80_000), fileContext).filter { it.isNotBlank() }.joinToString("\n\n"), message.images)
                })
            }
            val sb = StringBuilder()
            val candidates = buildList {
                add(Triple(Providers.byId(s.providerId), s.apiKey, s.model to s.baseUrl))
                s.savedKeys.forEach { (providerId, key) ->
                    if (providerId != s.providerId && key.isNotBlank()) {
                        val fallbackModel = s.savedModels[providerId].orEmpty()
                        if (fallbackModel.isNotBlank()) add(Triple(Providers.byId(providerId), key, fallbackModel to s.savedBaseUrls[providerId].orEmpty()))
                    }
                }
            }
            var succeeded = false
            var lastFailure: String? = null
            for ((index, candidate) in candidates.withIndex()) {
                val p = candidate.first
                val key = candidate.second
                val modelInfo = candidate.third
                val modelId = modelInfo.first
                val base = modelInfo.second
                _apiStatus.value = "Подключение к ${p.name}…"
                val attemptText = StringBuilder()
                var failure: String? = null
                client.stream(p, key, base, modelId, apiMessages).collect { ev ->
                    when (ev) {
                        is StreamEvent.Delta -> {
                            attemptText.append(ev.text)
                            _messages.value = _messages.value.dropLast(1) +
                                UiMessage("assistant", attemptText.toString(), isStreaming = true, generationMs = System.currentTimeMillis() - startedAt, tokenEstimate = estimateTokens(attemptText.toString()))
                            _generationMs.value = System.currentTimeMillis() - startedAt
                            _tokenEstimate.value = history.sumOf { estimateTokens(it.content) } + estimateTokens(attemptText.toString())
                        }
                        is StreamEvent.Failure -> failure = friendlyProviderError(ev.message)
                    }
                }
                if (failure == null && attemptText.isNotEmpty()) {
                    sb.append(attemptText)
                    succeeded = true
                    _error.value = null
                    _apiStatus.value = "Подключено · ${p.name}"
                    break
                }
                lastFailure = failure ?: "Провайдер завершил запрос без ответа"
                if (index < candidates.lastIndex) {
                    _messages.value = history + UiMessage("assistant", "", isStreaming = true)
                }
            }
            if (!succeeded) {
                _error.value = lastFailure ?: "Не удалось подключиться к провайдеру"
                _apiStatus.value = "Нет подключения к API"
            }
            val final = sb.toString()
            val (visibleAnswer, proposedWrites) = extractWorkspaceWrites(final)
            _pendingWorkspaceWrites.value = proposedWrites
            val completedAnswer = visibleAnswer
            val elapsed = System.currentTimeMillis() - startedAt
            _generationMs.value = elapsed
            _messages.value = if (final.isBlank() && _error.value != null) {
                _messages.value.dropLast(1)
            } else {
                _messages.value.dropLast(1) + UiMessage("assistant", completedAnswer, generationMs = elapsed, tokenEstimate = estimateTokens(completedAnswer))
            }
            _busy.value = false
            _sessions.value = _sessions.value.map { if (it.id == _currentId.value) it.copy(pendingPrompt = null, pendingImages = emptyList(), pendingFiles = emptyList()) else it }
            _recoverablePrompt.value = null
            persistCurrentMessages()
        }
    }

    private fun friendlyProviderError(raw: String): String {
        val lower = raw.lowercase()
        return when {
            "401" in lower || "unauthorized" in lower || "invalid api key" in lower -> "Неверный API-ключ. Проверь ключ в настройках провайдера."
            "403" in lower || "forbidden" in lower -> "Доступ запрещён. Проверь права ключа и доступность модели."
            "404" in lower -> "Модель или API-адрес не найдены. Проверь выбранную модель и Base URL."
            "429" in lower || "rate limit" in lower || "quota" in lower -> "Превышен лимит запросов или исчерпана квота провайдера."
            "timeout" in lower -> "Провайдер не ответил вовремя. Попробуй ещё раз."
            "unable to resolve" in lower || "failed to connect" in lower || "network" in lower -> "Нет соединения с сервером провайдера. Проверь интернет."
            else -> raw.take(320).ifBlank { "Неизвестная ошибка провайдера." }
        }
    }

    fun editMessage(index: Int, replacement: String) {
        if (_busy.value || replacement.isBlank() || index !in _messages.value.indices) return
        val target = _messages.value[index]
        if (target.role != "user") return
        val history = _messages.value.take(index) + target.copy(content = replacement.trim(), createdAt = System.currentTimeMillis(), tokenEstimate = estimateTokens(replacement))
        _messages.value = history
        persistCurrentMessages()
        startStream(history)
    }

    fun branchFrom(messageIndex: Int) {
        if (_busy.value || messageIndex !in _messages.value.indices) return
        val selected = _messages.value[messageIndex]
        if (selected.role != "assistant" || selected.isStreaming || selected.content.isBlank()) return
        val source = _sessions.value.firstOrNull { it.id == _currentId.value } ?: return
        val prefix = _messages.value.take(messageIndex + 1)
        val now = System.currentTimeMillis()
        val branch = ChatSession(
            id = "branch_${now}_${messageIndex}",
            title = "Ветка: ${source.title}".take(40),
            createdAt = now,
            updatedAt = now,
            providerId = source.providerId,
            model = source.model,
            messages = prefix.map { StoredMessage(it.role, it.content, it.images, it.id, it.createdAt, it.generationMs, it.tokenEstimate, it.files) },
            parentSessionId = source.id,
            branchMessageId = prefix.lastOrNull()?.id
        )
        _sessions.value = listOf(branch) + _sessions.value
        _currentId.value = branch.id
        _messages.value = prefix
        persist()
    }

    fun recoverInterruptedRequest() {
        val session = _sessions.value.firstOrNull { it.id == _currentId.value } ?: return
        val prompt = _recoverablePrompt.value ?: return
        _recoverablePrompt.value = null
        val retryHistory = _messages.value.let { current -> if (current.lastOrNull()?.role == "assistant") current.dropLast(1) else current }
        _sessions.value = _sessions.value.map { if (it.id == session.id) it.copy(pendingPrompt = null, pendingImages = emptyList(), pendingFiles = emptyList()) else it }
        persist()
        // Retry the existing user turn; do not append a duplicate user message.
        startStream(retryHistory)
    }

    private fun estimateTokens(text: String): Int = if (text.isBlank()) 0 else ((text.length + 3) / 4).coerceAtLeast(1)

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
            val lastUser = list.dropLast(1).lastOrNull { it.role == "user" }
            _sessions.value = _sessions.value.map { if (it.id == _currentId.value) it.copy(pendingPrompt = lastUser?.content, pendingImages = lastUser?.images.orEmpty(), pendingFiles = lastUser?.files.orEmpty()) else it }
            _recoverablePrompt.value = _sessions.value.firstOrNull { it.id == _currentId.value }?.pendingPrompt
        }
        if (::chatStore.isInitialized) persistCurrentMessages()
    }

    fun clearChat() {
        job?.cancel()
        _messages.value = emptyList()
        _error.value = null
        _busy.value = false
        _recoverablePrompt.value = null
        _pendingWorkspaceWrites.value = emptyList()
        _sessions.value = _sessions.value.map { if (it.id == _currentId.value) it.copy(pendingPrompt = null, pendingImages = emptyList(), pendingFiles = emptyList()) else it }
        if (::chatStore.isInitialized) persistCurrentMessages()
    }

    /** Остановка фоновой области при закрытии окна. */
    fun dispose() {
        scope.cancel()
    }
}
