package com.toska.keystack.ui

import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.toska.keystack.ChatSession
import com.toska.keystack.ChatViewModel
import com.toska.keystack.LlmClient
import com.toska.keystack.Providers
import com.toska.keystack.UiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Файл-вложение: имя + прочитанный текст. */
data class PendingFile(val name: String, val text: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel, onOpenSettings: () -> Unit) {
    val messages by vm.messages.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val settings by vm.settings.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val currentId by vm.currentId.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val client = remember { LlmClient() }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    var renameTarget by remember { mutableStateOf<ChatSession?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatSession?>(null) }
    var renameText by remember { mutableStateOf("") }

    // Вложения
    var pendingImages by remember { mutableStateOf(listOf<String>()) }
    var pendingFiles by remember { mutableStateOf(listOf<PendingFile>()) }
    var attachMenu by remember { mutableStateOf(false) }

    // Переключатель провайдера/модели
    var showSwitcher by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val imgs = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        runCatching {
                            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                ?: return@mapNotNull null
                            if (bytes.size > 8_000_000) return@mapNotNull null
                            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                            "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                        }.getOrNull()
                    }
                }
                pendingImages = (pendingImages + imgs).distinct().take(4)
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val picked = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        runCatching {
                            val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else "файл"
                            } ?: "файл"
                            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                                stream.readBytes().toString(Charsets.UTF_8).take(100_000)
                            } ?: return@mapNotNull null
                            PendingFile(name, text)
                        }.getOrNull()
                    }
                }
                pendingFiles = (pendingFiles + picked).distinctBy { it.name }
            }
        }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    val currentSession = sessions.firstOrNull { it.id == currentId }
    val provider = Providers.byId(settings.providerId)
    val lastAssistantIdx = messages.indexOfLast { it.role == "assistant" && !it.isStreaming }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(color = Color(0xFFFF5A1F), shape = RoundedCornerShape(7.dp), modifier = Modifier.size(28.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("K", color = Color.White, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("KeyStack", style = MaterialTheme.typography.titleMedium)
                            Text("AI workspace", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    FilledTonalIconButton(onClick = {
                        vm.newChat()
                        scope.launch { drawerState.close() }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Новый чат")
                    }
                }
                if (sessions.isEmpty()) {
                    Text(
                        "Пока нет чатов",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                val sorted = sessions.sortedWith(
                    compareByDescending<ChatSession> { it.pinned }.thenByDescending { it.updatedAt }
                )
                LazyColumn {
                    items(sorted, key = { it.id }) { s ->
                        ChatListItem(
                            session = s,
                            selected = s.id == currentId,
                            onClick = {
                                vm.selectChat(s.id)
                                scope.launch { drawerState.close() }
                            },
                            onRename = { renameText = s.title; renameTarget = s },
                            onPin = { vm.togglePin(s.id) },
                            onDelete = { deleteTarget = s }
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Список чатов")
                        }
                    },
                    title = {
                        // Тап по заголовку — смена провайдера/модели прямо в чате
                        Column(Modifier.clickable { showSwitcher = true }) {
                            Text(currentSession?.title ?: "Новая задача", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    provider.name + "  /  " + settings.model.ifBlank { "Модель не выбрана" },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Сменить провайдера или модель",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.clearChat() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Очистить чат")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Настройки")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
                    .imePadding()
            ) {
                if (messages.isEmpty()) {
                    VibeWelcomePanel(
                        providerName = provider.name,
                        onPrompt = { input = it }
                    )
                } else {
                    LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    itemsIndexed(messages) { idx, msg ->
                        MessageBubble(
                            msg = msg,
                            isLastAssistant = idx == lastAssistantIdx,
                            busy = busy,
                            onRegenerate = { vm.regenerate() }
                        )
                    }
                    }
                }

                error?.let { err ->
                    Text(
                        "Ошибка: $err",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                // Чипы выбранных вложений
                if (pendingImages.isNotEmpty() || pendingFiles.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pendingImages.forEachIndexed { i, _ ->
                            AssistChip(
                                onClick = { pendingImages = pendingImages.filterIndexed { idx, _ -> idx != i } },
                                label = { Text("📷 ${i + 1}") },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = "Убрать", modifier = Modifier.size(14.dp))
                                }
                            )
                        }
                        pendingFiles.forEach { f ->
                            AssistChip(
                                onClick = { pendingFiles = pendingFiles.filter { it.name != f.name } },
                                label = { Text("📄 " + f.name.take(16)) },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = "Убрать", modifier = Modifier.size(14.dp))
                                }
                            )
                        }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                         .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Плюс — прикрепить фото или файл
                    Box {
                        FilledTonalIconButton(onClick = { attachMenu = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Прикрепить фото или файл")
                        }
                        DropdownMenu(
                            expanded = attachMenu,
                            onDismissRequest = { attachMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Фото / картинка") },
                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                onClick = {
                                    attachMenu = false
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Файл (текст)") },
                                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                                onClick = {
                                    attachMenu = false
                                    filePicker.launch(arrayOf("*/*"))
                                }
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Спросите что-нибудь…") },
                        maxLines = 5,
                        shape = RoundedCornerShape(26.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    if (busy) {
                        FilledIconButton(onClick = { vm.stop() }) {
                            Icon(Icons.Default.Stop, contentDescription = "Стоп")
                        }
                    } else {
                        FilledIconButton(
                            onClick = {
                                val fileBlock = pendingFiles.joinToString("\n\n") { f ->
                                    "Файл: ${f.name}\n```\n${f.text}\n```"
                                }
                                val fullText = if (fileBlock.isBlank()) input else (input + "\n\n" + fileBlock).trim()
                                vm.send(fullText, pendingImages)
                                input = ""
                                pendingImages = emptyList()
                                pendingFiles = emptyList()
                            },
                            enabled = input.isNotBlank() || pendingImages.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Отправить", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    // Шторка переключения провайдера и модели
    if (showSwitcher) {
        val sheetState = rememberModalBottomSheetState()
        var modelSearch by remember { mutableStateOf("") }
        var fetchedModels by remember { mutableStateOf<List<String>?>(null) }

        LaunchedEffect(settings.providerId, settings.apiKey, settings.baseUrl) {
            fetchedModels = null
            val p = Providers.byId(settings.providerId)
            fetchedModels = if (settings.apiKey.isNotBlank() &&
                (p.baseUrl.isNotBlank() || settings.baseUrl.isNotBlank())
            ) {
                client.listModels(p, settings.apiKey, settings.baseUrl)
            } else emptyList()
        }

        ModalBottomSheet(
            onDismissRequest = { showSwitcher = false },
            sheetState = sheetState
        ) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                item {
                    Text(
                        "Провайдер",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                items(Providers.all, key = { "p_" + it.id }) { p ->
                    val hasKey = settings.savedKeys[p.id]?.isNotBlank() == true
                    ListItem(
                        headlineContent = { Text(p.name) },
                        supportingContent = {
                            Text(if (hasKey) "Ключ сохранён" else "Ключ не добавлен — добавь в настройках")
                        },
                        trailingContent = if (p.id == settings.providerId) {
                            { Icon(Icons.Default.Check, contentDescription = "Активен", tint = MaterialTheme.colorScheme.primary) }
                        } else null,
                        modifier = Modifier.clickable { vm.switchProvider(p.id) }
                    )
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Spacer(Modifier.height(8.dp))
                        Text("Модель — " + provider.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = modelSearch,
                            onValueChange = { modelSearch = it },
                            label = { Text("Поиск модели") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                when {
                    fetchedModels == null -> item {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Загружаю модели…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    fetchedModels!!.isEmpty() -> item {
                        Text(
                            "Список недоступен — выбери модель в настройках",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    else -> {
                        val filtered = fetchedModels!!.filter { it.contains(modelSearch.trim(), ignoreCase = true) }
                        items(filtered, key = { "m_" + it }) { m ->
                            ListItem(
                                headlineContent = { Text(m) },
                                trailingContent = if (m == settings.model) {
                                    { Icon(Icons.Default.Check, contentDescription = "Выбрана", tint = MaterialTheme.colorScheme.primary) }
                                } else null,
                                modifier = Modifier.clickable {
                                    vm.setModel(m)
                                    showSwitcher = false
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    renameTarget?.let { s ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Переименовать чат") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Название") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.renameChat(s.id, renameText); renameTarget = null },
                    enabled = renameText.isNotBlank()
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Отмена") }
            }
        )
    }

    deleteTarget?.let { s ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить чат?") },
            text = { Text("«${s.title}» будет удалён безвозвратно.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteChat(s.id); deleteTarget = null }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun VibeWelcomePanel(
    providerName: String,
    onPrompt: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = Color(0xFFFF5A1F),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(54.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("K", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            "Что будем создавать?",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "KeyStack • $providerName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        Column(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(
                "Помоги разобраться в сложной теме",
                "Проанализируй файл и сделай краткое резюме",
                "Придумай план для моего проекта"
            ).forEach { prompt ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onPrompt(prompt) },
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✦", color = Color(0xFFFF5A1F), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(12.dp))
                        Text(prompt, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("↗", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatListItem(
    session: ChatSession,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
             .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (session.pinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = "Закреплён",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    session.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (session.model.isNotBlank()) {
                Text(
                    session.model,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Меню чата")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Переименовать") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { menuOpen = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text(if (session.pinned) "Открепить" else "Закрепить") },
                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                    onClick = { menuOpen = false; onPin() }
                )
                DropdownMenuItem(
                    text = { Text("Удалить") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

@Composable
fun MessageBubble(
    msg: UiMessage,
    isLastAssistant: Boolean = false,
    busy: Boolean = false,
    onRegenerate: () -> Unit = {}
) {
    val isUser = msg.role == "user"
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
            shape = RoundedCornerShape(20.dp)
        ) {
            if (msg.isStreaming && msg.content.isEmpty()) {
                TypingDots()
            } else {
                Column(Modifier.padding(if (isUser) 14.dp else 4.dp).widthIn(max = 760.dp)) {
                    if (isUser && msg.images.isNotEmpty()) {
                        Text(
                            "📷 Фото: ${msg.images.size}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    SelectionContainer {
                        Text(
                            msg.content + if (msg.isStreaming) " ▌" else ""
                        )
                    }
                }
            }
        }
        // Кнопки под ответом ИИ: копировать и перегенерировать
        if (!isUser && !msg.isStreaming && msg.content.isNotBlank()) {
            Row {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(msg.content))
                        copied = true
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Копировать",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (copied) "Скопировано" else "Копировать",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                if (isLastAssistant && !busy) {
                    TextButton(
                        onClick = onRegenerate,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Перегенерировать",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Заново", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** Анимация «печатает…» — три пульсирующие точки. */
@Composable
fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    val dots = (0..2).map { i ->
        transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 500),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(i * 200)
            ),
            label = "dot$i"
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        dots.forEach { state ->
            val alpha by state
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer(alpha = alpha)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
            )
        }
    }
}
