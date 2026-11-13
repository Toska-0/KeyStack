package com.toska.keystack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.toska.keystack.LlmClient
import com.toska.keystack.Providers
import com.toska.keystack.Settings
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    initial: Settings,
    onDone: (providerId: String, apiKey: String, model: String, baseUrl: String) -> Unit,
    onBack: (() -> Unit)? = null
) {
    var selectedId by remember { mutableStateOf(initial.providerId.ifBlank { Providers.all.first().id }) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var showKey by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }

    var fetchedModels by remember { mutableStateOf<List<String>?>(null) }
    var loadingModels by remember { mutableStateOf(false) }
    var fetchFailed by remember { mutableStateOf(false) }

    val provider = Providers.byId(selectedId)
    val uriHandler = LocalUriHandler.current
    val client = remember { LlmClient() }
    val effectiveUrl = if (provider.needsBaseUrl) baseUrl.trim() else provider.baseUrl

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
            Text("Выбери провайдера", style = MaterialTheme.typography.titleMedium)

            Providers.all.forEach { p ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedId = p.id
                            if (model !in p.models) model = p.models.firstOrNull() ?: ""
                        },
                    colors = if (p.id == selectedId)
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    else
                        CardDefaults.cardColors()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = p.id == selectedId,
                            onClick = {
                                selectedId = p.id
                                if (model !in p.models) model = p.models.firstOrNull() ?: ""
                            }
                        )
                        Column {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge)
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
                label = { Text("API-ключ") },
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
                    modifier = Modifier.fillMaxWidth()
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сохранить и открыть чат")
            }

            Text(
                "Ключ хранится только на устройстве и отправляется напрямую провайдеру.",
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
