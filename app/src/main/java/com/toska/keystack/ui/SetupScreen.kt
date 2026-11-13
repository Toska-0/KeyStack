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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import com.toska.keystack.LlmClient
import com.toska.keystack.Providers
import com.toska.keystack.Settings
import com.toska.keystack.ui.theme.MistralGradient
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    initial: Settings,
    onDone: (providerId: String, apiKey: String, model: String, baseUrl: String) -> Unit,
    onSaveTheme: (themeMode: String, dynamicColor: Boolean) -> Unit,
    onBack: (() -> Unit)? = null
) {
    var selectedId by remember { mutableStateOf(initial.providerId.ifBlank { Providers.all.first().id }) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var showKey by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }

    var themeMode by remember { mutableStateOf(initial.themeMode) }
    var dynamicColor by remember { mutableStateOf(initial.dynamicColor) }

    // Черновики полей по провайдерам — чтобы ввод не терялся при переключении
    val draftKeys = remember { mutableStateMapOf<String, String>() }
    val draftModels = remember { mutableStateMapOf<String, String>() }
    val draftBaseUrls = remember { mutableStateMapOf<String, String>() }
    var prevId by remember { mutableStateOf(selectedId) }

    var fetchedModels by remember { mutableStateOf<List<String>?>(null) }
    var loadingModels by remember { mutableStateOf(false) }
    var fetchFailed by remember { mutableStateOf(false) }

    val provider = Providers.byId(selectedId)
    val uriHandler = LocalUriHandler.current
    val client = remember { LlmClient() }
    val effectiveUrl = if (provider.needsBaseUrl) baseUrl.trim() else provider.baseUrl

    // При смене провайдера — подставляем его сохранённые ключ/модель/baseURL
    LaunchedEffect(selectedId) {
        if (selectedId != prevId) {
            draftKeys[prevId] = apiKey
            draftModels[prevId] = model
            draftBaseUrls[prevId] = baseUrl
            apiKey = draftKeys[selectedId] ?: initial.savedKeys[selectedId] ?: ""
            model = draftModels[selectedId] ?: initial.savedModels[selectedId] ?: ""
            baseUrl = draftBaseUrls[selectedId] ?: initial.savedBaseUrls[selectedId] ?: ""
            prevId = selectedId
        }
    }

    // Автозагрузка моделей по ключу (с debounce на ввод)
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
    val valid = apiKey.isNotBlank() && model.isNotBlank() &&
        (!provider.needsBaseUrl || baseUrl.isNotBlank())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KeyStack — настройка") },
                navigationIcon = {
                    Box(
                        Modifier
                            .padding(start = 12.dp)
                            .size(30.dp)
                            .background(Brush.linearGradient(MistralGradient), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("K", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    }
                },
                actions = {
                    if (onBack != null) {
                        TextButton(onClick = onBack) { Text("В чат") }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Провайдеры и ключи", style = MaterialTheme.typography.titleMedium)
            Text(
                "Для каждого провайдера сохраняется свой ключ — переключайся в один тап.",
                style = MaterialTheme.typography.bodySmall
            )

            Providers.all.forEach { p ->
                val hasKey = initial.savedKeys[p.id]?.isNotBlank() == true
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedId = p.id },
                    shape = RoundedCornerShape(14.dp),
                    colors = if (p.id == selectedId)
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    else
                        CardDefaults.cardColors()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = p.id == selectedId,
                            onClick = { selectedId = p.id }
                        )
                        Column {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge)
                            if (hasKey) {
                                Text(
                                    "Ключ сохранён",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (p.keyUrl.isNotBlank()) {
                                Text(
                                    "Получить API-ключ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { uriHandler.openUri(p.keyUrl) }
                                )
                            }
                        }
                    }
                }
            }

            if (provider.needsBaseUrl) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL (например https://api.example.com/v1)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API-ключ для " + provider.name) },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (options.isNotEmpty()) {
                // Выбор модели только из списка — вручную вписать нельзя
                Card(
                    onClick = {
                        search = ""
                        showSheet = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Модель",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                model.ifBlank { "Нажми, чтобы выбрать" },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Выбрать модель")
                    }
                }
            } else {
                // Кастомный провайдер: список моделей получить неоткуда — manual input
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("ID модели") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            when {
                loadingModels -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Загружаю модели, доступные с этим ключом…", style = MaterialTheme.typography.bodySmall)
                }
                fetchedModels != null && fetchedModels!!.isNotEmpty() -> Text(
                    "Доступно моделей: ${fetchedModels!!.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                fetchFailed && options.isEmpty() -> Text(
                    "Не удалось получить список моделей — впиши ID модели вручную",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val url = if (provider.needsBaseUrl) baseUrl.trim() else provider.baseUrl
                    onDone(provider.id, apiKey.trim(), model.trim(), url)
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50)
            ) {
                Text("Сохранить и открыть чат")
            }

            Text("Оформление", style = MaterialTheme.typography.titleMedium)

            val themes = listOf(
                "system" to "Как в системе",
                "light" to "Светлая",
                "dark" to "Тёмная",
                "amoled" to "AMOLED (чёрная)"
            )
            themes.forEach { (id, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            themeMode = id
                            onSaveTheme(id, dynamicColor)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = themeMode == id,
                        onClick = {
                            themeMode = id
                            onSaveTheme(id, dynamicColor)
                        }
                    )
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Динамические цвета (Material You)",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = dynamicColor,
                    onCheckedChange = {
                        dynamicColor = it
                        onSaveTheme(themeMode, it)
                    }
                )
            }

            Text(
                "Ключи хранятся только на устройстве и отправляются напрямую провайдерам.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState()
        val filtered = options.filter { it.contains(search.trim(), ignoreCase = true) }
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("Выбор модели", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Поиск") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(filtered) { m ->
                        val trailing: @Composable (() -> Unit)? = if (m == model) {
                            { Icon(Icons.Default.Check, contentDescription = "Выбрана") }
                        } else null
                        ListItem(
                            headlineContent = { Text(m) },
                            trailingContent = trailing,
                            modifier = Modifier.clickable {
                                model = m
                                showSheet = false
                            }
                        )
                    }
                }
                if (filtered.isEmpty()) {
                    Text(
                        "Ничего не найдено",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
