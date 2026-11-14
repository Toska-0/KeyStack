package com.toska.keystack.ui

import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toska.keystack.AttachedFile
import com.toska.keystack.ChatSession
import com.toska.keystack.ChatViewModel
import com.toska.keystack.LlmClient
import com.toska.keystack.Providers
import com.toska.keystack.ReleaseInfo
import com.toska.keystack.ReleaseChecker
import com.toska.keystack.UiMessage
import com.toska.keystack.ui.theme.MistralGradient
import com.toska.keystack.ui.theme.KeyStackMark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Файл-вложение: имя + прочитанный текст. */
data class PendingFile(val name: String, val text: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onOpenSettings: () -> Unit,
    releaseInfo: ReleaseInfo? = null,
    onDismissRelease: () -> Unit = {},
    onOpenRelease: (String) -> Unit = {}
) {
    val messages by vm.messages.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val apiStatus by vm.apiStatus.collectAsState()
    val settings by vm.settings.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val currentId by vm.currentId.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val client = remember { LlmClient() }
    var input by remember { mutableStateOf("") }
    var editMessageTarget by remember { mutableStateOf<Int?>(null) }
    var editMessageText by remember { mutableStateOf("") }
    var chatSearch by remember { mutableStateOf("") }
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
    var quota by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var modelContextLimit by remember { mutableStateOf<Int?>(null) }
    val estimatedContextTokens by vm.tokenEstimate.collectAsState()
    var quotaStatus by remember { mutableStateOf("Проверка API…") }

    LaunchedEffect(settings.providerId, settings.apiKey) {
        quota = null
        quotaStatus = if (settings.apiKey.isBlank()) "API-ключ не настроен" else "Проверка API…"
        while (true) {
            if (settings.apiKey.isNotBlank()) {
                quota = client.fetchQuota(Providers.byId(settings.providerId), settings.apiKey)?.let { it.used to it.limit }
                quotaStatus = if (settings.providerId == "openrouter") {
                    if (quota != null) "API подключён" else "Квота OpenRouter недоступна"
                } else apiStatus
            }
            delay(60_000)
        }
    }

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
                            val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                                stream.readBytes()
                            } ?: return@mapNotNull null
                            if (bytes.size > 2_000_000) return@runCatching null
                            val textExtensions = setOf("txt", "md", "markdown", "csv", "tsv", "json", "xml", "html", "htm", "css", "js", "ts", "jsx", "tsx", "kt", "kts", "java", "py", "c", "cpp", "h", "cs", "go", "rs", "php", "sql", "yaml", "yml", "toml", "ini", "cfg", "conf", "log", "sh", "bat", "ps1", "properties", "gradle", "env")
                            val extension = name.substringAfterLast('.', "").lowercase()
                            val text = if (extension in textExtensions) bytes.toString(Charsets.UTF_8).take(100_000) else ""
                            PendingFile(name, text)
                        }.getOrNull()
                    }
                }
                pendingFiles = (pendingFiles + picked).distinctBy { it.name }
            }
        }
    }

    // Follow streamed output only while the user is actually at the bottom.
    // Checking the last item's bottom edge also works when one message is taller than the viewport.
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            val layout = listState.layoutInfo
            val lastItem = layout.visibleItemsInfo.lastOrNull { it.index == messages.lastIndex }
            val distanceToBottom = lastItem?.let {
                it.offset + it.size - layout.viewportEndOffset
            }
            val isAtBottom = layout.totalItemsCount == 0 ||
                (distanceToBottom != null && distanceToBottom <= 48)
            if (isAtBottom) {
                listState.scrollToItem(messages.lastIndex, Int.MAX_VALUE)
            }
        }
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
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color.White, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(com.toska.keystack.R.drawable.ic_launcher_foreground),
                                contentDescription = "KeyStack",
                                modifier = Modifier.size(38.dp).graphicsLayer { scaleX = 1.65f; scaleY = 1.65f }
                            )
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
                Text(
                    "ИСТОРИЯ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 18.dp, top = 20.dp, bottom = 8.dp)
                )
                OutlinedTextField(
                    value = chatSearch,
                    onValueChange = { chatSearch = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    singleLine = true,
                    placeholder = { Text("Найти диалог") },
                    shape = RoundedCornerShape(13.dp),
                    leadingIcon = { Text("⌕", style = MaterialTheme.typography.titleLarge) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
                val sorted = sessions.sortedWith(
                    compareByDescending<ChatSession> { it.pinned }.thenByDescending { it.updatedAt }
                ).filter { it.title.contains(chatSearch, ignoreCase = true) }
                if (sorted.isEmpty()) {
                    Text(
                        if (chatSearch.isBlank()) "Здесь появятся ваши диалоги" else "Ничего не найдено",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
                    )
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Сменить провайдера или модель",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
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
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(if (quota != null || (settings.providerId != "openrouter" && settings.apiKey.isNotBlank())) Color(0xFF45B879) else Color(0xFFB58A45), CircleShape))
                        Spacer(Modifier.width(7.dp))
                        Text(if (settings.providerId == "openrouter") quotaStatus else apiStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (modelContextLimit != null) {
                        val contextFraction = (estimatedContextTokens.toFloat() / modelContextLimit!!.coerceAtLeast(1)).coerceIn(0f, 1f)
                        Text("Контекст ≈ " + estimatedContextTokens + " / " + modelContextLimit + " токенов", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(progress = { contextFraction }, modifier = Modifier.fillMaxWidth())
                    } else Text("Лимит не раскрывается API · использовано ≈ $estimatedContextTokens токенов", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (settings.providerId == "openrouter" && quota != null) {
                        val q = quota!!
                        val fraction = if (q.second > 0) (q.first / q.second).toFloat().coerceIn(0f, 1f) else 0f
                        Text("Квота ключа: $" + "%.2f".format((q.second - q.first).coerceAtLeast(0.0)) + " осталось из $" + "%.2f".format(q.second), style = MaterialTheme.typography.labelSmall)
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    } else if (settings.providerId == "openrouter") Text("Квота ключа недоступна", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            if (messages.isEmpty()) {
                    VibeWelcomePanel(
                        providerName = provider.name,
                        modifier = Modifier.weight(1f).fillMaxWidth()
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
                            onRegenerate = { vm.regenerate() },
                            onEdit = {
                                editMessageText = msg.content
                                editMessageTarget = idx
                            },
                            onBranch = { vm.branchFrom(idx) },
                            fontScale = settings.fontScale,
                            maxWidth = settings.messageWidth,
                            animationsEnabled = settings.animationsEnabled
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
                                text = { Text("Файл") },
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
                        val canSend = input.isNotBlank() || pendingImages.isNotEmpty() || pendingFiles.isNotEmpty()
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (canSend) SolidColor(MaterialTheme.colorScheme.primary)
                                    else SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    CircleShape
                                )
                                .clickable(enabled = canSend) {
                                    vm.send(input, pendingImages, pendingFiles.map { AttachedFile(it.name, it.text) })
                                    input = ""
                                    pendingImages = emptyList()
                                    pendingFiles = emptyList()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Отправить",
                                tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    editMessageTarget?.let { index ->
        AlertDialog(
            onDismissRequest = { editMessageTarget = null },
            title = { Text("Изменить сообщение") },
            text = {
                OutlinedTextField(
                    value = editMessageText,
                    onValueChange = { editMessageText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.editMessage(index, editMessageText)
                    editMessageTarget = null
                }, enabled = editMessageText.isNotBlank()) { Text("Сохранить и повторить") }
            },
            dismissButton = { TextButton(onClick = { editMessageTarget = null }) { Text("Отмена") } }
        )
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

    releaseInfo?.let { release ->
        AlertDialog(
            onDismissRequest = onDismissRelease,
            title = { Text("Доступна новая версия") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(release.name, style = MaterialTheme.typography.titleMedium)
                    Text("Установлена: ${ReleaseChecker.CURRENT_VERSION}  ·  Доступна: ${release.tagName}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(release.body.ifBlank { "Вышло новое обновление KeyStack. Подробности доступны на странице релиза." },
                        style = MaterialTheme.typography.bodyMedium, maxLines = 12, overflow = TextOverflow.Ellipsis)
                }
            },
            confirmButton = {
                TextButton(onClick = { onOpenRelease(release.htmlUrl) }) { Text("Обновить") }
            },
            dismissButton = {
                TextButton(onClick = onDismissRelease) { Text("Позже") }
            }
        )
    }
}

@Composable
private fun VibeWelcomePanel(
    providerName: String,
    modifier: Modifier = Modifier
) {
    val greetings = remember {
        listOf(
            "Привет! Чем займёмся сегодня?" to "Расскажи, что у тебя на уме — начнём с этого.",
            "С чего начнём?" to "Можем разобраться с вопросом, идеей или задачей.",
            "Рад тебя видеть!" to "Опиши задачу своими словами — я помогу её решить.",
            "Есть интересная идея?" to "Давай превратим её в понятный план действий.",
            "Что будем создавать?" to "Можно начать новый проект или продолжить старый.",
            "Готов помочь." to "Задай вопрос или прикрепи файл для разбора.",
            "Давай приступим." to "Напиши, что нужно сделать, и разберёмся вместе.",
            "На связи!" to "От небольшого вопроса до большой задачи — начинай."
        ).random()
    }
    Column(
        modifier = modifier.padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(Color.White, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.toska.keystack.R.drawable.ic_launcher_foreground),
                contentDescription = "KeyStack",
                modifier = Modifier.size(72.dp).graphicsLayer { scaleX = 1.65f; scaleY = 1.65f }
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            greetings.first,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            greetings.second,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(50.dp)) {
            Text(
                "●  $providerName  ·  готов к работе",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
            )
        }

    }
}

/** Карточка-подсказка на экране приветствия в стиле Vibe. */
@Composable
private fun WelcomeSuggestion(symbol: String, title: String, prompt: String, onPrompt: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onPrompt(prompt) },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(13.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(symbol, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SidebarNavItem(symbol: String, label: String, tag: String? = null) {
    Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(symbol, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(12.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f))
        if (tag != null) Text(tag, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
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
    onRegenerate: () -> Unit = {},
    onEdit: () -> Unit = {},
    onBranch: () -> Unit = {},
    fontScale: Float = 1f,
    maxWidth: Int = 760,
    animationsEnabled: Boolean = true
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
            modifier = if (animationsEnabled) Modifier.animateContentSize() else Modifier,
            color = if (isUser) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            shape = if (isUser) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp)
        ) {
            if (msg.isStreaming && msg.content.isEmpty()) {
                TypingDots()
            } else {
                Column(Modifier.padding(if (isUser) 14.dp else 4.dp).widthIn(max = maxWidth.dp)) {
                    if (isUser && msg.images.isNotEmpty()) {
                        Text("📷 Фото: ${msg.images.size}", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                    }
                    if (isUser && msg.files.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            msg.files.forEach { file ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(7.dp))
                                    Text(file.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (file.content.isBlank()) "Файл" else "Текст · ${file.content.length} симв.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    MessageRichContent(
                        content = msg.content,
                        streaming = msg.isStreaming,
                        color = if (isUser) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                        fontScale = fontScale
                    )
                }
            }
        }
        if (isUser && !msg.isStreaming) {
            Row {
                TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Изменить", modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Изменить", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (!isUser && (msg.isStreaming || msg.content.isNotBlank())) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (msg.isStreaming && msg.tokenEstimate > 0) Text("≈ ${msg.tokenEstimate} токенов · генерируется", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                else {
                    if (msg.generationMs > 0L) Text("${"%.1f".format(msg.generationMs / 1000.0)} с", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (msg.tokenEstimate > 0) Text("≈ ${msg.tokenEstimate} токенов", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                TextButton(onClick = onBranch, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("↳", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(4.dp))
                    Text("Создать ветвь", style = MaterialTheme.typography.labelMedium)
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

@Composable
private fun MessageRichContent(content: String, streaming: Boolean, color: Color, fontScale: Float) {
    val clipboard = LocalClipboardManager.current
    val codePattern = Regex("(?s)```([\\w.+#-]*)\\s*\\n(.*?)(?:```|$)")
    val blocks = codePattern.findAll(content).toList()
    if (blocks.isEmpty()) {
        SelectionContainer {
            Text(content + if (streaming) " ▌" else "", color = color,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * fontScale,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * fontScale
                ))
        }
        return
    }
    var cursor = 0
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        blocks.forEach { block ->
            val before = content.substring(cursor, block.range.first)
            if (before.isNotEmpty()) SelectionContainer {
                Text(before, color = color, style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * fontScale,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * fontScale
                ))
            }
            val language = block.groupValues[1].ifBlank { "Код" }
            val code = block.groupValues[2].trimEnd()
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(language.uppercase(), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text("Скопировать код") }
                    }
                    SelectionContainer {
                        Text(code + if (streaming && block.range.last == content.lastIndex) " ▌" else "",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = (13f * fontScale).sp, lineHeight = (19f * fontScale).sp))
                    }
                }
            }
            cursor = block.range.last + 1
        }
        if (cursor < content.length) SelectionContainer {
            Text(content.substring(cursor) + if (streaming) " ▌" else "", color = color,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * fontScale,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * fontScale
                ))
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
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}
