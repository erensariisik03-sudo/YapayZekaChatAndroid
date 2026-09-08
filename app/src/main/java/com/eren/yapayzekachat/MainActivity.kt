package com.eren.yapayzekachat

import android.content.ClipData
import android.content.Context
import android.content.ClipboardManager
import android.net.Uri
import androidx.core.content.FileProvider
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.eren.yapayzekachat.data.ChatRepository
import com.eren.yapayzekachat.data.ConversationEntity
import com.eren.yapayzekachat.data.MessageEntity
import com.eren.yapayzekachat.network.GeminiApi

private const val PREFS = "app_settings"
private const val KEY_API = "api_key"
private const val KEY_MODEL = "model"
private const val KEY_TEMPERATURE = "temperature"
private const val KEY_MAX_OUTPUT_TOKENS = "max_output_tokens"
private const val DEFAULT_MODEL = ""
private const val CUSTOM_SYSTEM_INSTRUCTION = "Sen yardımcı, doğru ve doğrudan cevaplar veren bir yapay zeka asistanısın. Kullanıcının isteğine uygun olarak gerektiğinde kod, açıklama, örnek ve çözüm üret. Gereksiz tekrar yapma."
private const val DEFAULT_TEMPERATURE = 0.2f
private const val DEFAULT_MAX_OUTPUT_TOKENS = 16384

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels {
        ChatViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                YapayZekaChatApp(vm)
            }
        }
    }
}

class ChatViewModel(private val context: Context) : ViewModel() {
    private val repo = ChatRepository(context)
    private val api = GeminiApi()
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val conversations: StateFlow<List<ConversationEntity>> = repo.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val activeId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = activeId

    val messages: StateFlow<List<MessageEntity>> = activeId
        .flatMapLatest { id ->
            if (id == null) kotlinx.coroutines.flow.flowOf(emptyList()) else repo.observeMessages(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var selectedModel by mutableStateOf(prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL)
        private set
    var apiKey by mutableStateOf(prefs.getString(KEY_API, "") ?: "")
        private set
    var temperature by mutableStateOf(prefs.getFloat(KEY_TEMPERATURE, DEFAULT_TEMPERATURE))
        private set
    var maxOutputTokens by mutableStateOf(prefs.getInt(KEY_MAX_OUTPUT_TOKENS, DEFAULT_MAX_OUTPUT_TOKENS))
        private set
    var availableModels by mutableStateOf(emptyList<String>())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var retrying by mutableStateOf(false)
        private set
    var settingsOpen by mutableStateOf(false)
    var modelsLoading by mutableStateOf(false)
        private set
    var modelsError by mutableStateOf<String?>(null)
        private set

    init {
        if (apiKey.isNotBlank()) loadModels()
        viewModelScope.launch {
            conversations.collect { list ->
                if (activeId.value == null && list.isNotEmpty()) activeId.value = list.first().id
                if (list.isEmpty()) {
                    val created = repo.createConversation()
                    activeId.value = created.id
                }
            }
        }
    }

    fun selectConversation(id: String) {
        error = null
        activeId.value = id
    }

    fun newConversation() {
        viewModelScope.launch {
            val created = repo.createConversation()
            activeId.value = created.id
            error = null
        }
    }

    fun exportCurrentConversation(): String {
        val id = activeId.value ?: return ""
        val title = conversations.value.firstOrNull { it.id == id }?.title ?: "Sohbet"
        val currentMessages = messages.value
        return buildString {
            appendLine(title)
            appendLine("=".repeat(title.length.coerceAtLeast(6)))
            appendLine()
            currentMessages.forEach { message ->
                appendLine(if (message.role == "user") "Kullanıcı:" else "Yapay Zeka:")
                if (message.attachmentsJson != "[]") appendLine("[Ekli dosya]")
                appendLine(message.text)
                appendLine()
            }
        }.trimEnd()
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            val replacement = conversations.value.firstOrNull { it.id != id }?.id
            repo.deleteConversation(id)
            if (activeId.value == id) {
                activeId.value = replacement
                if (replacement == null) {
                    val created = repo.createConversation()
                    activeId.value = created.id
                }
            }
        }
    }

    fun saveSettings(key: String, model: String, newTemperature: Float, newMaxOutputTokens: Int) {
        apiKey = key.trim()
        selectedModel = model.trim()
        temperature = newTemperature.coerceIn(0f, 2f)
        maxOutputTokens = newMaxOutputTokens.coerceIn(256, 16384)
        prefs.edit()
            .putString(KEY_API, apiKey)
            .putString(KEY_MODEL, selectedModel)
            .putFloat(KEY_TEMPERATURE, temperature)
            .putInt(KEY_MAX_OUTPUT_TOKENS, maxOutputTokens)
            .apply()
        settingsOpen = false
        loadModels()
    }

    fun loadModels() {
        if (apiKey.isBlank()) {
            modelsError = "Önce API anahtarını kaydetmelisin."
            return
        }
        modelsLoading = true
        modelsError = null
        viewModelScope.launch(Dispatchers.IO) {
            val result = api.listModels(apiKey)
            withContext(Dispatchers.Main) {
                modelsLoading = false
                if (result.models.isNotEmpty()) {
                    availableModels = result.models
                    val savedModel = selectedModel
                    val shouldReplaceDeprecated = savedModel.equals("gemini-2.5-flash", ignoreCase = true)
                    if (savedModel !in result.models || shouldReplaceDeprecated) {
                        val fallback = choosePreferredModel(result.models, savedModel)
                        if (!fallback.isNullOrBlank()) {
                            selectedModel = fallback
                            prefs.edit().putString(KEY_MODEL, selectedModel).apply()
                        }
                    }
                } else {
                    modelsError = result.error ?: "Model listesi alınamadı."
                }
            }
        }
    }

    private fun choosePreferredModel(models: List<String>, exclude: String? = null): String? {
        return models.asSequence()
            .filter { !it.equals(exclude, ignoreCase = true) }
            .maxByOrNull { model ->
                val lower = model.lowercase()
                val version = Regex("""gemini-(\d+)(?:\.(\d+))?""")
                    .find(lower)
                    ?.let { (it.groupValues[1].toIntOrNull() ?: 0) * 100 + (it.groupValues.getOrNull(2)?.toIntOrNull() ?: 0) }
                    ?: 0
                val familyScore = when {
                    "flash" in lower -> 10000
                    "pro" in lower -> 9000
                    else -> 1000
                }
                familyScore + version
            }
    }

    fun setModel(model: String) {
        selectedModel = model
        prefs.edit().putString(KEY_MODEL, model).apply()
    }

    fun send(text: String, uris: List<Uri>) {
        val clean = text.trim()
        if (clean.isBlank() && uris.isEmpty()) return
        if (apiKey.isBlank()) {
            settingsOpen = true
            error = "API anahtarı gerekli. Ayarlardan Gemini API anahtarını gir."
            return
        }
        val conversationId = activeId.value ?: return
        var requestModel = selectedModel.ifBlank { availableModels.firstOrNull().orEmpty() }
        if (requestModel.isBlank()) {
            error = "Kullanılabilir model bulunamadı. API anahtarını kaydet ve model listesinin yüklenmesini bekle."
            loadModels()
            return
        }

        viewModelScope.launch {
            loading = true
            error = null
            val saved = withContext(Dispatchers.IO) { repo.insertUserMessage(conversationId, clean, uris) }
            var finalResult: GeminiApi.Result? = null
            retrying = false
            for (attempt in 1..3) {
                if (attempt > 1) {
                    retrying = true
                    delay((attempt - 1) * 1_500L)
                }
                val requestMessages = repo.getRequestMessages(conversationId)
                val result = withContext(Dispatchers.IO) {
                    api.generateComplete(
                        apiKey = apiKey,
                        model = requestModel,
                        messages = requestMessages,
                        systemInstruction = modeSystemInstruction(),
                        temperature = modeTemperature(),
                        maxOutputTokens = modeMaxOutputTokens()
                    )
                }
                finalResult = result
                if (result.isSuccess) break
                if (result.statusCode == 404 && result.rawError.orEmpty().contains("no longer available", ignoreCase = true)) {
                    val refreshed = withContext(Dispatchers.IO) { api.listModels(apiKey) }
                    if (refreshed.models.isNotEmpty()) {
                        availableModels = refreshed.models
                        val fallback = choosePreferredModel(refreshed.models, requestModel)
                        if (!fallback.isNullOrBlank()) {
                            selectedModel = fallback
                            requestModel = fallback
                            prefs.edit().putString(KEY_MODEL, selectedModel).apply()
                            continue
                        }
                    }
                    break
                }
            }
            retrying = false
            loading = false
            val result = finalResult ?: GeminiApi.Result(rawError = "Bilinmeyen hata")
            if (result.isSuccess) {
                repo.insertAssistantMessage(conversationId, result.text)
            } else {
                error = buildError(result)
            }
        }
    }

    fun retryLast() {
        val id = activeId.value ?: return
        if (loading || apiKey.isBlank()) return
        var requestModel = selectedModel.ifBlank { availableModels.firstOrNull().orEmpty() }
        if (requestModel.isBlank()) {
            error = "Kullanılabilir model bulunamadı. API anahtarını kaydet ve model listesinin yüklenmesini bekle."
            loadModels()
            return
        }
        viewModelScope.launch {
            loading = true
            error = null
            retrying = true
            var finalResult: GeminiApi.Result? = null
            for (attempt in 1..3) {
                if (attempt > 1) delay((attempt - 1) * 1_500L)
                val requestMessages = repo.getRequestMessages(id)
                val result = withContext(Dispatchers.IO) {
                    api.generateComplete(
                        apiKey = apiKey,
                        model = requestModel,
                        messages = requestMessages,
                        systemInstruction = modeSystemInstruction(),
                        temperature = modeTemperature(),
                        maxOutputTokens = modeMaxOutputTokens()
                    )
                }
                finalResult = result
                if (result.isSuccess) break
                if (result.statusCode == 404 && result.rawError.orEmpty().contains("no longer available", ignoreCase = true)) {
                    val refreshed = withContext(Dispatchers.IO) { api.listModels(apiKey) }
                    if (refreshed.models.isNotEmpty()) {
                        availableModels = refreshed.models
                        val fallback = choosePreferredModel(refreshed.models, requestModel)
                        if (!fallback.isNullOrBlank()) {
                            selectedModel = fallback
                            requestModel = fallback
                            prefs.edit().putString(KEY_MODEL, selectedModel).apply()
                            continue
                        }
                    }
                    break
                }
            }
            retrying = false
            loading = false
            val result = finalResult ?: GeminiApi.Result(rawError = "Bilinmeyen hata")
            if (result.isSuccess) repo.insertAssistantMessage(id, result.text) else error = buildError(result)
        }
    }

    private fun modeSystemInstruction(): String = CUSTOM_SYSTEM_INSTRUCTION

    private fun modeTemperature(): Double = temperature.toDouble()

    private fun modeMaxOutputTokens(): Int = maxOutputTokens

    private fun buildError(result: GeminiApi.Result): String {
        val detail = result.rawError ?: "İstek başarısız."
        return when (result.statusCode) {
            401 -> "HTTP 401 — API anahtarı kabul edilmedi. Anahtarın Gemini API için geçerli olduğunu ve kopyalanırken boşluk/karakter hatası olmadığını kontrol et.\n\nDetay: $detail"
            403 -> "HTTP 403 — API anahtarının bu isteğe veya seçili modele erişim izni yok. Cloud Console kısıtlamalarını kontrol et.\n\nDetay: $detail"
            429 -> "HTTP 429 — Kota veya hız limiti aşıldı. Biraz bekleyip tekrar dene ya da başka bir model kullan.\n\nDetay: $detail"
            else -> {
                val code = if (result.statusCode > 0) "HTTP ${result.statusCode}: " else ""
                "${code}$detail"
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(context) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YapayZekaChatApp(vm: ChatViewModel) {
    val conversations by vm.conversations.collectAsState()
    val activeId by vm.activeConversationId.collectAsState()
    val messages by vm.messages.collectAsState()
    val drawerState = rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    var draft by remember { mutableStateOf("") }
    var pendingUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(vm.exportCurrentConversation().toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        pendingUris = (pendingUris + uris).distinct()
    }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        pendingUris = (pendingUris + uris).distinct()
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraUri
        if (success && uri != null) pendingUris = (pendingUris + uri).distinct()
        else if (uri != null) runCatching { context.contentResolver.delete(uri, null, null) }
        cameraUri = null
    }

    fun launchCamera() {
        val dir = java.io.File(context.cacheDir, "camera").apply { mkdirs() }
        val file = java.io.File.createTempFile("camera_", ".jpg", dir)
        val uri = FileProvider.getUriForFile(context, "${'$'}{BuildConfig.APPLICATION_ID}.fileprovider", file)
        cameraUri = uri
        cameraLauncher.launch(uri)
    }

    LaunchedEffect(messages.size) {
        // The lazy list below naturally stays near the newest message after sending.
    }

    val scope = androidx.compose.runtime.rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.fillMaxHeight().width(320.dp)) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                        Text("Yapay Zeka Chat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Geçmiş", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(conversations, key = { it.id }) { chat ->
                            val selected = chat.id == activeId
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                                    .clickable { vm.selectConversation(chat.id); scope.launch { drawerState.close() } }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                    Text(java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(chat.updatedAt)), style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = { vm.deleteConversation(chat.id) }) { Icon(Icons.Default.DeleteOutline, "Sil") }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    Divider()
                    TextButton(
                        onClick = {
                            val safeTitle = conversations.firstOrNull { it.id == activeId }?.title?.ifBlank { "sohbet" } ?: "sohbet"
                            exportLauncher.launch("${safeTitle.replace(Regex("[^A-Za-z0-9ğüşöçıİĞÜŞÖÇ _-]"), "_")}.txt")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sohbeti dışa aktar")
                    }
                    TextButton(onClick = { vm.settingsOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Settings, null); Spacer(Modifier.width(8.dp)); Text("Ayarlar")
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val title = conversations.firstOrNull { it.id == activeId }?.title ?: "Yeni sohbet"
                        Column {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${vm.selectedModel} • Custom • T=${"%.2f".format(java.util.Locale.US, vm.temperature)} • ${vm.maxOutputTokens} token",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Menü") } },
                    actions = {
                        IconButton(onClick = vm::newConversation) { Icon(Icons.Default.Add, "Yeni sohbet") }
                        IconButton(onClick = { vm.settingsOpen = true }) { Icon(Icons.Default.Settings, "Ayarlar") }
                    }
                )
            }
        ) { padding ->
            ChatScreen(
                modifier = Modifier.padding(padding),
                messages = messages,
                draft = draft,
                onDraftChange = { draft = it },
                selectedUris = pendingUris,
                onPickFiles = { picker.launch(arrayOf("*/*")) },
                onPickGallery = { galleryPicker.launch("image/*") },
                onTakePhoto = { runCatching { launchCamera() } },
                onRemoveFile = { pendingUris = pendingUris.filterIndexed { index, _ -> index != it } },
                onSend = {
                    vm.send(draft, pendingUris)
                    draft = ""
                    pendingUris = emptyList()
                },
                loading = vm.loading,
                retrying = vm.retrying,
                error = vm.error,
                onRetry = vm::retryLast,
                onModel = { vm.settingsOpen = true }
            )
        }
    }

    // Overlay menu button behavior is implemented through a lightweight dialog-free drawer trigger below.
    LaunchedEffect(Unit) {
        // Kept intentionally empty; Scaffold's menu button is replaced by this state-aware route in ChatScreen.
    }

    if (vm.settingsOpen) {
        SettingsDialog(vm)
    }
}

@Composable
private fun ChatScreen(
    modifier: Modifier,
    messages: List<MessageEntity>,
    draft: String,
    onDraftChange: (String) -> Unit,
    selectedUris: List<Uri>,
    onPickFiles: () -> Unit,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemoveFile: (Int) -> Unit,
    onSend: () -> Unit,
    loading: Boolean,
    retrying: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onModel: () -> Unit
) {
    val listState = rememberLazyListState()
    var attachmentMenu by remember { mutableStateOf(false) }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    Column(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 12.dp)
        ) {
            itemsIndexed(messages, key = { _, item -> item.id }) { _, message ->
                MessageBubble(message)
            }
            if (retrying) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("İstek başarısız oldu, tekrar deneniyor…")
                    }
                }
            }
        }
        if (error != null) {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Yanıt alınamadı", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Bu hata seçili modelden kaynaklanıyor olabilir; başka bir model denemek önerilir.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onRetry) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(4.dp)); Text("Tekrar gönder") }
                        TextButton(onClick = onModel) { Icon(Icons.Default.SwapHoriz, null); Spacer(Modifier.width(4.dp)); Text("Model değiştir") }
                    }
                }
            }
        }
        if (selectedUris.isNotEmpty()) {
            LazyRow(modifier = Modifier.fillMaxWidth().height(72.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(selectedUris) { index, uri ->
                    AssistChip(
                        onClick = { onRemoveFile(index) },
                        label = { Text(uri.lastPathSegment?.substringAfterLast('/') ?: "Dosya", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Default.AttachFile, null, Modifier.size(16.dp)) }
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                IconButton(onClick = { attachmentMenu = !attachmentMenu }) { Icon(Icons.Default.AttachFile, "Ek ekle") }
                DropdownMenu(expanded = attachmentMenu, onDismissRequest = { attachmentMenu = false }) {
                    DropdownMenuItem(text = { Text("Dosya seç") }, leadingIcon = { Icon(Icons.Default.AttachFile, null) }, onClick = { attachmentMenu = false; onPickFiles() })
                    DropdownMenuItem(text = { Text("Galeriden seç") }, leadingIcon = { Icon(Icons.Default.Image, null) }, onClick = { attachmentMenu = false; onPickGallery() })
                    DropdownMenuItem(text = { Text("Kamera") }, leadingIcon = { Icon(Icons.Default.CameraAlt, null) }, onClick = { attachmentMenu = false; onTakePhoto() })
                }
            }
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    minLines = 1,
                    maxLines = 6,
                    decorationBox = { inner ->
                        if (draft.isBlank()) Text("Mesajını yaz…", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f))
                        inner()
                    }
                )
            }
            Surface(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).clickable(enabled = !loading, onClick = onSend),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Icon(Icons.AutoMirrored.Filled.Send, "Gönder")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageEntity) {
    val mine = message.role == "user"
    val context = androidx.compose.ui.platform.LocalContext.current

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Yapay Zeka Chat", text))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(.88f)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (mine) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(14.dp)
        ) {
            if (message.attachmentsJson != "[]") {
                Text("📎 Ekli dosya", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
            }
            if (message.text.isNotBlank()) {
                SelectionContainer {
                    RenderMarkdownMessage(
                        text = message.text,
                        onCopyCode = ::copyToClipboard
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderMarkdownMessage(
    text: String,
    onCopyCode: (String) -> Unit
) {
    val fenceRegex = Regex("(?s)```([^\\n]*)\\n(.*?)```")
    var cursor = 0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fenceRegex.findAll(text).forEach { match ->
            if (match.range.first > cursor) {
                val before = text.substring(cursor, match.range.first)
                if (before.isNotBlank()) MarkdownText(before)
            }
            val language = match.groupValues[1].trim()
            val code = match.groupValues[2].trimEnd('\n')
            CodeBlock(code = code, language = language, onCopy = onCopyCode)
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            val tail = text.substring(cursor)
            if (tail.isNotBlank()) MarkdownText(tail)
        }
    }
}

@Composable
private fun MarkdownText(text: String) {
    val normalized = text
        // Bazı modeller yanlışlıkla *kelime** yazabiliyor; bunu kalın biçim olarak yorumla.
        .replace(Regex("(?<!\\*)\\*([^*\\n]+)\\*\\*"), "**$1**")
        .replace(Regex("(?m)^###\\s+"), "")

    val lines = normalized.split('\n')
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        lines.forEach { line ->
            val trimmed = line.trimStart()
            val isH1 = trimmed.startsWith("# ")
            val isH2 = trimmed.startsWith("## ")
            val content = when {
                isH1 || isH2 -> trimmed.dropWhile { it == '#' }.trimStart()
                else -> line
            }
            Text(
                text = markdownAnnotatedString(content, boldEverything = isH1 || isH2),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = when {
                        isH1 -> 22.sp
                        isH2 -> 19.sp
                        else -> MaterialTheme.typography.bodyLarge.fontSize
                    },
                    fontWeight = if (isH1 || isH2) FontWeight.Bold else FontWeight.Normal
                )
            )
        }
    }
}

private fun markdownAnnotatedString(text: String, boldEverything: Boolean = false): AnnotatedString {
    val tokenRegex = Regex("(\\*\\*[^*\\n]+\\*\\*|__[^_\\n]+__|(?<!\\*)\\*[^*\\n]+\\*(?!\\*)|(?<!_)_[^_\\n]+_(?!_)|(?<!\\*)\\*\\*[^*\\n]+)")
    return buildAnnotatedString {
        if (boldEverything) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text) }
            return@buildAnnotatedString
        }
        var cursor = 0
        tokenRegex.findAll(text).forEach { match ->
            if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
            val token = match.value
            when {
                token.startsWith("**") && token.endsWith("**") ->
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(token.drop(2).dropLast(2)) }
                token.startsWith("__") && token.endsWith("__") ->
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(token.drop(2).dropLast(2)) }
                token.startsWith("*") && token.endsWith("*") ->
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(token.drop(1).dropLast(1)) }
                token.startsWith("_") && token.endsWith("_") ->
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(token.drop(1).dropLast(1)) }
                token.startsWith("**") ->
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(token.drop(2).trimEnd('*')) }
                else -> append(token.trimEnd('*'))
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}

@Composable
private fun CodeBlock(
    code: String,
    language: String,
    onCopy: (String) -> Unit
) {
    var copied by remember(code) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_500L)
            copied = false
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    language.ifBlank { "Kod" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = {
                    onCopy(code)
                    copied = true
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (copied) "Kopyalandı" else "Kopyala")
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(14.dp)
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDialog(vm: ChatViewModel) {
    var key by remember(vm.apiKey) { mutableStateOf(vm.apiKey) }
    var model by remember(vm.selectedModel) { mutableStateOf(vm.selectedModel) }
    var tempText by remember(vm.temperature) { mutableStateOf("%.2f".format(java.util.Locale.US, vm.temperature)) }
    var tokenText by remember(vm.maxOutputTokens) { mutableStateOf(vm.maxOutputTokens.toString()) }
    var expanded by remember { mutableStateOf(false) }
    val models = vm.availableModels.distinct()

    AlertDialog(
        onDismissRequest = { vm.settingsOpen = false },
        title = { Text("Ayarlar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Gemini API anahtarı", style = MaterialTheme.typography.labelLarge)
                BasicTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    decorationBox = { inner ->
                        if (key.isBlank()) Text("Gemini API anahtarı", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f))
                        inner()
                    }
                )
                Text("Custom Modu", style = MaterialTheme.typography.labelLarge)
                Text("Sıcaklık ve maksimum çıktı token sayısını kendin belirle.", style = MaterialTheme.typography.bodySmall)

                Text("Sıcaklık (0.0 - 2.0)", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicTextField(
                        value = tempText,
                        onValueChange = { value ->
                            tempText = value.replace(',', '.')
                        },
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        singleLine = true
                    )
                    Text("${"%.2f".format(java.util.Locale.US, tempText.toFloatOrNull()?.coerceIn(0f, 2f) ?: vm.temperature)}")
                }
                androidx.compose.material3.Slider(
                    value = tempText.toFloatOrNull()?.coerceIn(0f, 2f) ?: vm.temperature,
                    onValueChange = { tempText = "%.2f".format(java.util.Locale.US, it) },
                    valueRange = 0f..2f
                )

                Text("Maksimum çıktı tokeni", style = MaterialTheme.typography.labelLarge)
                BasicTextField(
                    value = tokenText,
                    onValueChange = { tokenText = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    singleLine = true
                )
                Text("İzin verilen aralık: 256 - 16384", style = MaterialTheme.typography.bodySmall)
                Text("Model", style = MaterialTheme.typography.labelLarge)
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    BasicTextField(
                        value = model,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                        decorationBox = { inner ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) { inner() }
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                            }
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        models.forEach { item ->
                            DropdownMenuItem(text = { Text(item) }, onClick = { model = item; expanded = false })
                        }
                    }
                }
                if (vm.modelsLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Modeller getiriliyor…", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (vm.availableModels.isNotEmpty()) {
                    Text("${vm.availableModels.size} model bulundu", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
                vm.modelsError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text("Anahtar bu cihazdaki uygulama ayarlarında tutulur; kaynak koduna eklenmez.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedTemperature = tempText.toFloatOrNull()?.coerceIn(0f, 2f) ?: vm.temperature
                val parsedTokens = tokenText.toIntOrNull()?.coerceIn(256, 16384) ?: vm.maxOutputTokens
                vm.saveSettings(key, model, parsedTemperature, parsedTokens)
            }) {
                Text("Kaydet")
            }
        },
        dismissButton = { TextButton(onClick = { vm.settingsOpen = false }) { Text("İptal") } }
    )
}
