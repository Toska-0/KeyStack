package com.toska.keystack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.toska.keystack.LlmClient
import com.toska.keystack.Providers
import com.toska.keystack.Settings
import com.toska.keystack.ui.theme.KeyStackMark
import com.toska.keystack.ui.theme.MistralGradient
import kotlinx.coroutines.delay
import javax.swing.JFileChooser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    initial: Settings,
    onDone: (providerId: String, apiKey: String, model: String, baseUrl: String) -> Unit,
    onSaveTheme: (themeMode: String, dynamicColor: Boolean) -> Unit,
    onSaveWorkspace: (path: String) -> Unit,
    onSaveUiPreferences: (fontScale: Float, messageWidth: Int, animationsEnabled: Boolean) -> Unit,
    onSaveAccentColor: (String) -> Unit,
    onBack: (() -> Unit)? = null
) {
    var selectedId by remember { mutableStateOf(initial.providerId.ifBlank { Providers.all.first().id }) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var showKey by remember { mutableStateOf(false) }
    var providerMenu by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    var modelSearch by remember { mutableStateOf("") }

    var themeMode by remember { mutableStateOf(initial.themeMode) }
    var workspacePath by remember { mutableStateOf(initial.workspacePath) }
    var fontScale by remember { mutableStateOf(initial.fontScale) }
    var messageWidth by remember { mutableStateOf(initial.messageWidth.toFloat()) }
    var animationsEnabled by remember { mutableStateOf(initial.animationsEnabled) }
    var accentColor by remember { mutableStateOf(initial.accentColor) }

    val draftKeys = remember { mutableStateMapOf<String, String>() }
    val draftModels = remember { mutableStateMapOf<String, String>() }
    val draftBaseUrls = remember { mutableStateMapOf<String, String>() }
    var previousId by remember { mutableStateOf(selectedId) }

    var fetchedModels by remember { mutableStateOf<List<String>?>(null) }
    var loadingModels by remember { mutableStateOf(false) }
    var fetchFailed by remember { mutableStateOf(false) }

    val provider = Providers.byId(selectedId)
    val uriHandler = LocalUriHandler.current
    val client = remember { LlmClient() }
    val effectiveUrl = if (provider.needsBaseUrl) baseUrl.trim() else provider.baseUrl

    LaunchedEffect(selectedId) {
        if (selectedId != previousId) {
            draftKeys[previousId] = apiKey
            draftModels[previousId] = model
            draftBaseUrls[previousId] = baseUrl
            apiKey = draftKeys[selectedId] ?: initial.savedKeys[selectedId] ?: ""
            model = draftModels[selectedId] ?: initial.savedModels[selectedId] ?: ""
            baseUrl = draftBaseUrls[selectedId] ?: initial.savedBaseUrls[selectedId] ?: ""
            previousId = selectedId
        }
    }

    LaunchedEffect(selectedId, apiKey, effectiveUrl) {
        if (apiKey.trim().length >= 8 && effectiveUrl.isNotBlank()) {
            delay(700)
            loadingModels = true
            fetchFailed = false
            fetchedModels = null
            val result = client.listModels(provider, apiKey.trim(), effectiveUrl)
            fetchedModels = result
            fetchFailed = result.isEmpty()
            loadingModels = false
        } else {
            fetchedModels = null
            loadingModels = false
            fetchFailed = false
        }
    }

    val options = fetchedModels?.takeIf { it.isNotEmpty() } ?: provider.models
    val valid = apiKey.isNotBlank() && model.isNotBlank() && (!provider.needsBaseUrl || baseUrl.isNotBlank())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    Box(
                        Modifier.padding(start = 16.dp).size(32.dp)
                            .background(Brush.linearGradient(MistralGradient), RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) { KeyStackMark(Modifier.size(20.dp)) }
                },
                actions = {
                    if (onBack != null) TextButton(onClick = onBack) { Text("Вернуться в чат") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier.widthIn(max = 880.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Персонализируйте KeyStack", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Подключите провайдера, настройте внешний вид и выберите папку для работы с файлами.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SettingsSection(
                    title = "Подключение",
                    description = "API-ключ хранится на этом компьютере и используется для прямых запросов к провайдеру."
                ) {
                    Box {
                        Card(
                            onClick = { providerMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(13.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Провайдер", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(provider.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (initial.savedKeys[selectedId].isNullOrBlank()) "Ключ ещё не сохранён" else "Для этого провайдера есть сохранённый ключ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Выбрать провайдера", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                            Providers.all.forEach { item ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(item.name)
                                            if (!initial.savedKeys[item.id].isNullOrBlank()) {
                                                Text("Ключ сохранён", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    },
                                    leadingIcon = if (item.id == selectedId) {{ Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }} else null,
                                    onClick = { providerMenu = false; selectedId = item.id }
                                )
                            }
                        }
                    }

                    if (provider.needsBaseUrl) {
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            label = { Text("Base URL") },
                            placeholder = { Text("https://api.example.com/v1") },
                            supportingText = if (provider.id == "kiro") {
                                { Text("Укажите адрес запущенного Kiro API gateway.") }
                            } else null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API-ключ") },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showKey) "Скрыть ключ" else "Показать ключ"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (provider.keyUrl.isNotBlank()) {
                        TextButton(onClick = { uriHandler.openUri(provider.keyUrl) }) {
                            Text(if (provider.id == "kiro") "Открыть инструкцию" else "Где получить API-ключ?")
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            label = { Text("ID модели") },
                            placeholder = { Text("Например, модель из каталога провайдера") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { modelSearch = ""; showModelSheet = true }, enabled = options.isNotEmpty()) {
                            Text("Выбрать")
                        }
                    }
                    when {
                        loadingModels -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Загружаю модели…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        fetchedModels != null && fetchedModels!!.isNotEmpty() -> Text(
                            "Найдено моделей: ${fetchedModels!!.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        fetchFailed -> Text(
                            "Список моделей недоступен. Можно указать ID вручную.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            val url = if (provider.needsBaseUrl) baseUrl.trim() else provider.baseUrl
                            onDone(provider.id, apiKey.trim(), model.trim(), url)
                        },
                        enabled = valid,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(13.dp)
                    ) { Text("Сохранить настройки") }
                }

                SettingsSection(
                    title = "Внешний вид",
                    description = "Подстройте тему и акцент под себя."
                ) {
                    Text("Тема", style = MaterialTheme.typography.titleSmall)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf("system" to "Как в системе", "light" to "Светлая", "dark" to "Тёмная", "amoled" to "AMOLED — чёрная").forEach { (id, label) ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    themeMode = id
                                    onSaveTheme(id, false)
                                }.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = themeMode == id,
                                    onClick = { themeMode = id; onSaveTheme(id, false) }
                                )
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Акцентный цвет", style = MaterialTheme.typography.titleSmall)
                        Text("Цвет кнопок и активных элементов", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf(
                                "orange" to Color(0xFFFF5A1F), "blue" to Color(0xFF4D8DFF),
                                "purple" to Color(0xFF9B72E8), "green" to Color(0xFF35A879),
                                "pink" to Color(0xFFE65B91), "cyan" to Color(0xFF20AFC2)
                            ).forEach { (id, color) ->
                                Box(
                                    Modifier.size(32.dp).background(color, CircleShape)
                                        .clickable { accentColor = id; onSaveAccentColor(id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (accentColor == id) Icon(Icons.Default.Check, contentDescription = "Выбрано", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                SettingsSection(
                    title = "Чат",
                    description = "Настройки чтения и поведения интерфейса."
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Размер текста", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("${(fontScale * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(value = fontScale, onValueChange = {
                            fontScale = it
                            onSaveUiPreferences(it, messageWidth.toInt(), animationsEnabled)
                        }, valueRange = 0.85f..1.4f)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Ширина сообщений", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("${messageWidth.toInt()} dp", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(value = messageWidth, onValueChange = {
                            messageWidth = it
                            onSaveUiPreferences(fontScale, it.toInt(), animationsEnabled)
                        }, valueRange = 560f..1100f)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Анимации", style = MaterialTheme.typography.bodyMedium)
                            Text("Плавные переходы и появление сообщений", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = animationsEnabled, onCheckedChange = {
                            animationsEnabled = it
                            onSaveUiPreferences(fontScale, messageWidth.toInt(), it)
                        })
                    }
                }

                SettingsSection(
                    title = "Рабочая папка",
                    description = "KeyStack сможет читать поддерживаемые текстовые файлы и предлагать изменения. Запись выполняется только после вашего подтверждения."
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Text(
                            workspacePath.ifBlank { "Папка не выбрана" },
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (workspacePath.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = {
                            val chooser = JFileChooser().apply {
                                dialogTitle = "Выберите рабочую папку KeyStack"
                                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                isAcceptAllFileFilterUsed = false
                                if (workspacePath.isNotBlank()) currentDirectory = java.io.File(workspacePath)
                            }
                            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                workspacePath = chooser.selectedFile.absolutePath
                                onSaveWorkspace(workspacePath)
                            }
                        }) { Text(if (workspacePath.isBlank()) "Выбрать папку" else "Изменить папку") }
                        if (workspacePath.isNotBlank()) {
                            TextButton(onClick = { workspacePath = ""; onSaveWorkspace("") }) { Text("Отключить") }
                        }
                    }
                    Text(
                        "Не выбирайте папку с паролями, ключами или другими файлами, которые не хотите отправлять AI-провайдеру.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showModelSheet) {
        val sheetState = rememberModalBottomSheetState()
        val filteredModels = options.filter { it.contains(modelSearch.trim(), ignoreCase = true) }
        ModalBottomSheet(onDismissRequest = { showModelSheet = false }, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text("Модели · ${provider.name}", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = modelSearch,
                    onValueChange = { modelSearch = it },
                    label = { Text("Поиск по названию") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    filteredModels.forEach { item ->
                        ListItem(
                            headlineContent = { Text(item) },
                            trailingContent = if (item == model) {{ Icon(Icons.Default.Check, contentDescription = "Выбрана", tint = MaterialTheme.colorScheme.primary) }} else null,
                            modifier = Modifier.clickable { model = item; showModelSheet = false }
                        )
                    }
                    if (filteredModels.isEmpty()) Text(
                        "Нет совпадений. Можно ввести ID модели вручную.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String,
    content: @Composable Column.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}
