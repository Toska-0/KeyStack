package com.toska.keystack.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toska.keystack.AttachedFile
import com.toska.keystack.ChatSession
import com.toska.keystack.ChatViewModel
import com.toska.keystack.LlmClient
import com.toska.keystack.Providers
import com.toska.keystack.ReleaseInfo
import com.toska.keystack.ReleaseChecker
import com.toska.keystack.ProviderQuota
import com.toska.keystack.UiMessage
import com.toska.keystack.ui.theme.MistralGradient
import com.toska.keystack.ui.theme.KeyStackMark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.util.Base64
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.awt.image.BufferedImage
import java.io.File
import java.util.zip.ZipFile
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** Файл-вложение: имя + прочитанный текст. */
data class PendingFile(val name: String, val text: String, val path: String = "")

private fun extractFileText(file: File): String = runCatching {
    when (file.extension.lowercase()) {
        "pdf" -> Loader.loadPDF(file).use { PDFTextStripper().getText(it).take(200_000) }
        "docx" -> ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: return@use ""
            val xml = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            Regex("<w:t[^>]*>([^<]*)</w:t>").findAll(xml).joinToString(" ") { it.groupValues[1] }.take(200_000)
        }
        else -> {
            val extensions = setOf("txt", "md", "markdown", "csv", "tsv", "json", "xml", "html", "htm", "css", "js", "ts", "jsx", "tsx", "kt", "kts", "java", "py", "c", "cpp", "h", "cs", "go", "rs", "php", "sql", "yaml", "yml", "toml", "ini", "cfg", "conf", "log", "sh", "bat", "ps1", "properties", "gradle", "env")
            if (file.extension.lowercase() in extensions && file.length() <= 2_000_000L) file.readText(Charsets.UTF_8).take(200_000) else ""
        }
    }
}.getOrDefault("")

/** Диалог выбора картинок: возвращает data URI вида "data:image/jpeg;base64,...". */
suspend fun pickImages(): List<String> = withContext(Dispatchers.Swing) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Выберите картинки"
        setMultiSelectionEnabled(true)
        fileFilter = FileNameExtensionFilter("Изображения (jpg, png, webp, gif)", "jpg", "jpeg", "png", "webp", "gif")
        isAcceptAllFileFilterUsed = false
    }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@withContext emptyList()
    withContext(Dispatchers.IO) {
        (chooser.selectedFiles.toList().ifEmpty { listOfNotNull(chooser.selectedFile) }).mapNotNull { f ->
            runCatching {
                if (f.length() > 8_000_000L) return@mapNotNull null
                val bytes = f.readBytes()
                val mime = when (f.extension.lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    else -> "image/jpeg"
                }
                "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)
            }.getOrNull()
        }
    }
}

/** Выбор документов и исходных файлов, содержимое которых можно передать модели как текст. */
suspend fun pickDocuments(): List<PendingFile> = withContext(Dispatchers.Swing) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Прикрепить файл"
        setMultiSelectionEnabled(true)
        isAcceptAllFileFilterUsed = true
    }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@withContext emptyList()
    withContext(Dispatchers.IO) {
        (chooser.selectedFiles.toList().ifEmpty { listOfNotNull(chooser.selectedFile) }).mapNotNull { file ->
            runCatching {
                require(file.isFile && file.length() <= 20_000_000L)
                val content = extractFileText(file)
                PendingFile(file.name, content, file.absolutePath)
            }.getOrNull()
        }
    }
}

private fun clipboardImageDataUri(): String? = runCatching {
    val image = Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.imageFlavor) as? Image ?: return null
    val w = image.getWidth(null).coerceAtLeast(1); val h = image.getHeight(null).coerceAtLeast(1)
    val buffered = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val graphics = buffered.createGraphics(); graphics.drawImage(image, 0, 0, null); graphics.dispose()
    val output = java.io.ByteArrayOutputStream(); ImageIO.write(buffered, "png", output)
    "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray())
}.getOrNull()

private suspend fun readDroppedFiles(files: List<File>): Pair<List<String>, List<PendingFile>> = withContext(Dispatchers.IO) {
    val images = mutableListOf<String>(); val docs = mutableListOf<PendingFile>()
    files.filter { it.isFile }.take(12).forEach { file -> runCatching {
        when (file.extension.lowercase()) {
            "png", "jpg", "jpeg", "webp", "gif", "bmp" -> {
                if (file.length() <= 8_000_000L) {
                    val bytes = file.readBytes()
                    val mime = when (file.extension.lowercase()) { "png" -> "image/png"; "webp" -> "image/webp"; "gif" -> "image/gif"; "bmp" -> "image/bmp"; else -> "image/jpeg" }
                    images += "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes)
                }
            }
            else -> {
                if (file.length() <= 20_000_000L) docs += PendingFile(file.name, extractFileText(file), file.absolutePath)
            }
        }
    } }
    images to docs
}
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
    val recoverablePrompt by vm.recoverablePrompt.collectAsState()
    val pendingWorkspaceWrites by vm.pendingWorkspaceWrites.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val client = remember { LlmClient() }
    var input by remember { mutableStateOf("") }
    var editMessageTarget by remember { mutableStateOf<Int?>(null) }
    var editMessageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    var renameTarget by remember { mutableStateOf<ChatSession?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatSession?>(null) }
    var renameText by remember { mutableStateOf("") }
    var sessionMenuId by remember { mutableStateOf<String?>(null) }

    // Вложения
    var pendingImages by remember { mutableStateOf(listOf<String>()) }
    var pendingFiles by remember { mutableStateOf(listOf<PendingFile>()) }
    var attachMenu by remember { mutableStateOf(false) }
    var loadingAttachments by remember { mutableStateOf(false) }
    var recentPickerOpen by remember { mutableStateOf(false) }
    var recentAttachmentPaths by remember { mutableStateOf(emptyList<String>()) }
    val awtWindow = remember { java.awt.Window.getWindows().filter { it.isShowing }.maxByOrNull { if (it.isActive) 1 else 0 } }
    fun saveRecentAttachmentPaths(paths: List<String>) {
        recentAttachmentPaths = (paths + recentAttachmentPaths).filter { it.isNotBlank() }.distinct().take(12)
        runCatching { val history = File(System.getProperty("user.home"), ".keystack/attachment-history.txt"); history.parentFile?.mkdirs(); history.writeText(recentAttachmentPaths.joinToString("\n")) }
    }
    LaunchedEffect(Unit) {
        val history = File(System.getProperty("user.home"), ".keystack/attachment-history.txt")
        recentAttachmentPaths = runCatching { if (history.exists()) history.readLines().filter { File(it).exists() }.take(12) else emptyList() }.getOrDefault(emptyList())
    }
    DisposableEffect(awtWindow) {
        if (awtWindow == null) {
            onDispose { }
        } else {
            val handler = object : DropTargetAdapter() {
                override fun dragEnter(event: java.awt.dnd.DropTargetDragEvent) {
                    if (event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) event.acceptDrag(DnDConstants.ACTION_COPY)
                    else event.rejectDrag()
                }
                override fun drop(event: DropTargetDropEvent) {
                    try {
                        if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                            event.rejectDrop()
                            return
                        }
                        event.acceptDrop(DnDConstants.ACTION_COPY)
                        @Suppress("UNCHECKED_CAST")
                        val files = event.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                        scope.launch {
                            loadingAttachments = true
                            val batch = readDroppedFiles(files)
                            pendingImages = (pendingImages + batch.first).distinct().take(4)
                            pendingFiles = (pendingFiles + batch.second).distinctBy { it.path.ifBlank { it.name } }
                            saveRecentAttachmentPaths(files.map { it.absolutePath })
                            loadingAttachments = false
                        }
                        event.dropComplete(true)
                    } catch (_: Exception) { event.dropComplete(false) }
                }
            }
            val targets = mutableMapOf<java.awt.Component, DropTarget?>()
            fun install(component: java.awt.Component) {
                targets[component] = component.dropTarget
                runCatching { component.dropTarget = DropTarget(component, DnDConstants.ACTION_COPY, handler, true) }
                if (component is java.awt.Container) component.components.forEach(::install)
            }
            install(awtWindow)
            onDispose {
                targets.forEach { (component, previous) -> runCatching { component.dropTarget = previous } }
            }
        }
    }

    // Переключатель провайдера/модели
    var showSwitcher by remember { mutableStateOf(false) }
    var quota by remember { mutableStateOf<ProviderQuota?>(null) }
    var modelContextLimit by remember { mutableStateOf<Int?>(null) }
    val estimatedContextTokens by vm.tokenEstimate.collectAsState()
    var quotaChecked by remember { mutableStateOf(false) }
    var quotaStatus by remember { mutableStateOf("Проверка API…") }

    LaunchedEffect(settings.providerId, settings.apiKey) {
        quota = null
        modelContextLimit = null
        quotaChecked = false
        quotaStatus = if (settings.apiKey.isBlank()) "API-ключ не настроен" else "Проверка API…"
        while (true) {
            if (settings.apiKey.isNotBlank()) {
                quota = client.fetchQuota(Providers.byId(settings.providerId), settings.apiKey)
                modelContextLimit = client.fetchModelContextLimit(Providers.byId(settings.providerId), settings.model, settings.apiKey, settings.baseUrl)
                quotaChecked = true
                quotaStatus = if (settings.providerId == "openrouter") {
                    if (quota != null) "API подключён" else "Квота OpenRouter недоступна"
                } else "API-статус определяется по последнему запросу"
            }
            delay(60_000)
        }
    }

    val currentSession = sessions.firstOrNull { it.id == currentId }
    val provider = Providers.byId(settings.providerId)
    val lastAssistantIdx = messages.indexOfLast { it.role == "assistant" && !it.isStreaming }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Постоянная навигационная панель — структура повторяет референс Vibe.
        Column(
            Modifier.width(202.dp).fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(horizontal = 12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(27.dp).background(Color.White, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("K", style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 21.sp, fontWeight = FontWeight.Black,
                        brush = Brush.linearGradient(MistralGradient)
                    ))
                }
                Spacer(Modifier.width(9.dp))
                Text("KeyStack", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(9.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().height(34.dp)
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.78f))), RoundedCornerShape(8.dp))
                    .clickable { vm.newChat() },
                color = Color.Transparent, shape = RoundedCornerShape(8.dp)
            ) {
                Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("+", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(9.dp))
                    Text("Новый чат", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(7.dp))
                        Spacer(Modifier.height(18.dp))
            Text("НЕДАВНИЕ ЧАТЫ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 8.dp, bottom = 9.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                // Сортировка по времени создания не перемещает чат после каждого нового сообщения.
                val stableSessions = sessions.sortedByDescending { it.createdAt }
                items(stableSessions, key = { it.id }) { session ->
                    Row(
                        Modifier.fillMaxWidth().height(38.dp)
                            .background(if (session.id == currentId) MaterialTheme.colorScheme.primary.copy(alpha = 0.17f) else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { vm.selectChat(session.id) }
                            .padding(start = 9.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(session.title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { sessionMenuId = session.id }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Действия с чатом",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                            }
                            DropdownMenu(expanded = sessionMenuId == session.id, onDismissRequest = { sessionMenuId = null }) {
                                DropdownMenuItem(text = { Text("Переименовать") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = { sessionMenuId = null; renameText = session.title; renameTarget = session })
                                DropdownMenuItem(text = { Text(if (session.pinned) "Открепить" else "Закрепить") },
                                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                                    onClick = { sessionMenuId = null; vm.togglePin(session.id) })
                                DropdownMenuItem(text = { Text("Удалить") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = { sessionMenuId = null; deleteTarget = session })
                            }
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onOpenSettings() },
                color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(9.dp)
            ) {
                Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.size(27.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("K", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)) }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("KeyStack", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
                        Text(provider.name, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    Text("⚙", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Основная рабочая область без старой верхней панели.
        Column(
            Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.background)
        ) {
            Row(
                Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(provider.name + "  ·  " + settings.model.ifBlank { "Модель не выбрана" } + "  ▾",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clickable { showSwitcher = true })
                Spacer(Modifier.width(16.dp))
                Text("⚙", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { onOpenSettings() })
                Spacer(Modifier.width(8.dp))
                Text("⋯", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { vm.clearChat() })
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(7.dp).background(
                    if (quota != null || (settings.providerId != "openrouter" && settings.apiKey.isNotBlank())) Color(0xFF45B879) else Color(0xFFB58A45),
                    CircleShape
                ))
                Spacer(Modifier.width(7.dp))
                Text(if (settings.providerId == "openrouter") quotaStatus else apiStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (modelContextLimit != null) {
                    val contextFraction = (estimatedContextTokens.toFloat() / modelContextLimit!!.coerceAtLeast(1)).coerceIn(0f, 1f)
                    Text("Контекст ≈ " + estimatedContextTokens + " / " + modelContextLimit + " токенов", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.width(76.dp)) { LinearProgressIndicator(progress = { contextFraction }, modifier = Modifier.fillMaxWidth()) }
                } else {
                    Text("Лимит не раскрывается API · использовано ≈ $estimatedContextTokens токенов", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (settings.providerId == "openrouter" && quota != null) {
                    val q = quota!!
                    val usedFraction = if (q.limit > 0) (q.used / q.limit).toFloat().coerceIn(0f, 1f) else 0f
                    Text("$" + "%.2f".format(q.remaining) + " осталось из $" + "%.2f".format(q.limit),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.width(86.dp)) {
                        LinearProgressIndicator(progress = { usedFraction }, modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    Text(if (settings.providerId == "openrouter") "Реальная квота не получена" else "Лимит не раскрывается API",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (recoverablePrompt != null) {
                Surface(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Запрос был прерван. Можно повторить его вместе с вложениями.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { vm.recoverInterruptedRequest() }) { Text("Повторить") }
                    }
                }
            }
            if (messages.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.78f))), RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        KeyStackMark(Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Добро пожаловать. С чего начнём?",
                        color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(18.dp))
                    ComposerBar(
                        input = input,
                        onInput = { input = it },
                        busy = busy,
                        onStop = { vm.stop() },
                        onSend = {
vm.send(input, pendingImages, pendingFiles.map { AttachedFile(it.name, it.text) }); input = ""; pendingImages = emptyList(); pendingFiles = emptyList()
                        },
                        onAttach = { attachMenu = true },
                        attachMenu = attachMenu,
                        onDismissAttach = { attachMenu = false },
                        onPickImages = { scope.launch { loadingAttachments = true; pendingImages = (pendingImages + pickImages()).distinct().take(4); val history = File(System.getProperty("user.home"), ".keystack/attachment-history.txt"); recentAttachmentPaths = runCatching { if (history.exists()) history.readLines().filter { File(it).exists() }.take(12) else emptyList() }.getOrDefault(emptyList()); loadingAttachments = false } },
                        onPickFiles = { scope.launch { loadingAttachments = true; val picked = pickDocuments(); pendingFiles = (pendingFiles + picked).distinctBy { it.path.ifBlank { it.name } }; saveRecentAttachmentPaths(picked.map { it.path }); loadingAttachments = false } },
                        onPasteImageValue = { pendingImages = (pendingImages + it).distinct().take(4) },
                        onPickRecent = { recentPickerOpen = true },
                        enabled = input.isNotBlank() || pendingImages.isNotEmpty() || pendingFiles.isNotEmpty()
                    )
                    if (pendingImages.isNotEmpty() || pendingFiles.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pendingImages.forEachIndexed { index, _ ->
                                AssistChip(onClick = { pendingImages = pendingImages.filterIndexed { i, _ -> i != index } },
                                    label = { Text("Фото ${index + 1}") },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Убрать", modifier = Modifier.size(14.dp)) })
                            }
                            pendingFiles.forEach { file ->
                                AssistChip(onClick = { pendingFiles = pendingFiles.filterNot { it.path == file.path && it.name == file.name } },
                                    label = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Убрать", modifier = Modifier.size(14.dp)) })
                            }
                        }
                    }

                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        itemsIndexed(messages, key = { index, _ -> index }) { idx, msg ->
                            MessageBubble(
                                msg = msg,
                                isLastAssistant = idx == lastAssistantIdx,
                                busy = busy,
                                onRegenerate = { vm.regenerate() },
                                onEdit = { editMessageText = msg.content; editMessageTarget = idx },
                                onBranch = { vm.branchFrom(idx) },
                                fontScale = settings.fontScale,
                                maxWidth = settings.messageWidth,
                                animationsEnabled = settings.animationsEnabled
                            )
                        }
                    }
                    error?.let { err ->
                        Text("Ошибка: $err", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    }
                    if (pendingImages.isNotEmpty() || pendingFiles.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pendingImages.forEachIndexed { index, _ ->
                                AssistChip(onClick = { pendingImages = pendingImages.filterIndexed { i, _ -> i != index } },
                                    label = { Text("Фото ${index + 1}") },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Убрать", modifier = Modifier.size(14.dp)) })
                            }
                            pendingFiles.forEach { file ->
                                AssistChip(onClick = { pendingFiles = pendingFiles.filterNot { it.path == file.path && it.name == file.name } },
                                    label = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Убрать", modifier = Modifier.size(14.dp)) })
                            }
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ComposerBar(
                            input = input, onInput = { input = it }, busy = busy, onStop = { vm.stop() },
                            onSend = {
vm.send(input, pendingImages, pendingFiles.map { AttachedFile(it.name, it.text) }); input = ""; pendingImages = emptyList(); pendingFiles = emptyList()
                            },
                            onAttach = { attachMenu = true }, attachMenu = attachMenu, onDismissAttach = { attachMenu = false },
                            onPickImages = { scope.launch { pendingImages = (pendingImages + pickImages()).distinct().take(4) } },
                            onPickFiles = { scope.launch { loadingAttachments = true; val picked = pickDocuments(); pendingFiles = (pendingFiles + picked).distinctBy { it.path.ifBlank { it.name } }; saveRecentAttachmentPaths(picked.map { it.path }); loadingAttachments = false } },
                            onPasteImageValue = { pendingImages = (pendingImages + it).distinct().take(4) },
                            onPickRecent = { recentPickerOpen = true },
                            enabled = input.isNotBlank() || pendingImages.isNotEmpty() || pendingFiles.isNotEmpty()
                        )
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

    if (loadingAttachments) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp))
    if (recentPickerOpen) {
        AlertDialog(
            onDismissRequest = { recentPickerOpen = false },
            title = { Text("Недавние вложения") },
            text = {
                if (recentAttachmentPaths.isEmpty()) Text("История вложений пока пуста.")
                else LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(recentAttachmentPaths) { path ->
                        ListItem(
                            headlineContent = { Text(File(path).name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    loadingAttachments = true
                                    val batch = readDroppedFiles(listOf(File(path)))
                                    pendingImages = (pendingImages + batch.first).distinct().take(4)
                                    pendingFiles = (pendingFiles + batch.second).distinctBy { it.path.ifBlank { it.name } }
                                    loadingAttachments = false
                                    recentPickerOpen = false
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { recentPickerOpen = false }) { Text("Закрыть") } }
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

    if (pendingWorkspaceWrites.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { vm.rejectWorkspaceWrites() },
            title = { Text("Разрешить изменение файлов?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ИИ подготовил изменения. Ничего не будет записано без подтверждения.")
                    pendingWorkspaceWrites.forEachIndexed { index, proposal ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(proposal.relativePath, style = MaterialTheme.typography.titleSmall)
                            Text("Размер нового содержимого: ${proposal.content.length} символов", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { vm.approveWorkspaceWrite(index) }) { Text("Сохранить") }
                                TextButton(onClick = { vm.rejectWorkspaceWrites() }) { Text("Отклонить все") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.rejectWorkspaceWrites() }) { Text("Закрыть") } }
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
private fun ComposerBar(
    input: String,
    onInput: (String) -> Unit,
    busy: Boolean,
    onStop: () -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    attachMenu: Boolean,
    onDismissAttach: () -> Unit,
    onPickImages: () -> Unit,
    onPickFiles: () -> Unit,
    onPickRecent: () -> Unit = {},
    enabled: Boolean,
    onPasteImageValue: (String) -> Unit = {}
) {
    Row(
        Modifier.widthIn(max = 760.dp).fillMaxWidth()
            .animateContentSize(tween(180))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(26.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            IconButton(onClick = onAttach, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Добавить вложение", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = attachMenu, onDismissRequest = onDismissAttach) {
                DropdownMenuItem(text = { Text("Фото / картинка") }, onClick = { onDismissAttach(); onPickImages() })
                DropdownMenuItem(text = { Text("Файл") }, onClick = { onDismissAttach(); onPickFiles() })
                DropdownMenuItem(text = { Text("Недавние вложения") }, onClick = { onDismissAttach(); onPickRecent() })
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.V) { clipboardImageDataUri()?.let { onPasteImageValue(it) }; false } else false
            },
            placeholder = { Text("Введите сообщение или / для быстрых команд", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
            maxLines = 5,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.primary, focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent
            )
        )
        if (busy) {
            FilledIconButton(onClick = onStop) { Icon(Icons.Default.Stop, contentDescription = "Остановить") }
        } else {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        if (enabled) SolidColor(MaterialTheme.colorScheme.primary) else SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest),
                        CircleShape
                    )
                    .clickable(enabled = enabled, onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Отправить",
                    tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun VibeWelcomePanel(
    providerName: String,
    onPrompt: (String) -> Unit
) {
    val prompts = listOf(
        listOf("01", "Исследовать идею", "Разложи сложную тему на понятные шаги", "Разложи сложную тему на понятные шаги"),
        listOf("02", "Работа с файлами", "Изучи документ и выдели главное", "Проанализируй прикреплённый файл и сделай краткое резюме"),
        listOf("03", "План проекта", "От идеи до готового результата", "Помоги составить подробный план моего проекта")
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(34.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        KeyStackMark(Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("KEYSTACK WORKSPACE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Пространство для идей и работы", style = MaterialTheme.typography.titleSmall)
                }
            }
            Spacer(Modifier.height(42.dp))
            Text(
                "С чего начнём?",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(9.dp))
            Text(
                "Задайте вопрос, исследуйте идею или начните новую задачу.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(50.dp)) {
                Text(
                    "●  $providerName  /  готов к работе",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(28.dp))
            Text("БЫСТРЫЙ СТАРТ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                prompts.forEach { (number, title, subtitle, prompt) ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onPrompt(prompt) },
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(15.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 17.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(number, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(3.dp))
                                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("↗", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                        }
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
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val enterProgress by animateFloatAsState(
        targetValue = if (appeared || !animationsEnabled) 1f else 0f,
        animationSpec = tween(durationMillis = if (animationsEnabled) 260 else 0),
        label = "message-enter"
    )

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Column(
        Modifier.fillMaxWidth().then(if (animationsEnabled) Modifier.graphicsLayer {
            alpha = enterProgress
            translationY = (1f - enterProgress) * 12f
        } else Modifier),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            modifier = if (animationsEnabled) Modifier.animateContentSize(tween(180)) else Modifier,
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            shape = if (isUser) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(18.dp),
            border = if (isUser) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
        ) {
            if (msg.isStreaming && msg.content.isEmpty()) {
                TypingDots()
            } else {
                Column(Modifier.padding(horizontal = 17.dp, vertical = 13.dp).widthIn(max = maxWidth.dp)) {
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
                    MessageRichContent(msg.content, msg.isStreaming,
                        if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, fontScale)
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
        SelectionContainer { Text(content + if (streaming) " ▌" else "", color = color,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = (15f * fontScale).sp, lineHeight = (23f * fontScale).sp)) }
        return
    }
    var cursor = 0
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        blocks.forEach { block ->
            val before = content.substring(cursor, block.range.first)
            if (before.isNotEmpty()) SelectionContainer {
                Text(before, color = color, style = MaterialTheme.typography.bodyLarge.copy(fontSize = (15f * fontScale).sp, lineHeight = (23f * fontScale).sp))
            }
            val language = block.groupValues[1].ifBlank { "Код" }
            val code = block.groupValues[2].trimEnd()
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(language.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text("Скопировать код") }
                    }
                    SelectionContainer {
                        Text(code + if (streaming && block.range.last == content.lastIndex) " ▌" else "",
                            color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = (13f * fontScale).sp, lineHeight = (19f * fontScale).sp))
                    }
                }
            }
            cursor = block.range.last + 1
        }
        if (cursor < content.length) SelectionContainer {
            Text(content.substring(cursor) + if (streaming) " ▌" else "", color = color,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = (15f * fontScale).sp, lineHeight = (23f * fontScale).sp))
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
