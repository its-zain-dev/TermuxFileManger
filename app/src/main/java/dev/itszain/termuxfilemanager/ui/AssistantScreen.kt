package dev.itszain.termuxfilemanager.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.itszain.termuxfilemanager.*
import dev.itszain.termuxfilemanager.R
import dev.itszain.termuxfilemanager.data.MessageRecord
import dev.itszain.termuxfilemanager.features.AgentAction
import dev.itszain.termuxfilemanager.features.WorkspaceModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    model: WorkspaceModel,
    settings: () -> Unit,
    terminal: (String, String) -> Unit,
    openOpenCode: () -> Unit
) {
    val chats by model.chats.collectAsStateWithLifecycle()
    val sessions by model.sessions.collectAsStateWithLifecycle()
    val chatId by model.chatId.collectAsStateWithLifecycle()
    val messages by model.messages.collectAsStateWithLifecycle()
    val active by model.graph.agent.active.collectAsStateWithLifecycle()
    val pending by model.graph.agent.pending.collectAsStateWithLifecycle()
    val suggested by model.suggestedPrompt.collectAsStateWithLifecycle()
    val chat = chats.firstOrNull { it.id == chatId }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val tfm = LocalTfmColors.current

    var prompt by rememberSaveable { mutableStateOf("") }
    var image by rememberSaveable { mutableStateOf("") }
    var newChat by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var attachmentSheet by remember { mutableStateOf(false) }
    var broadcast by remember { mutableStateOf(false) }
    var autoConfirm by remember { mutableStateOf(false) }
    var preferredProvider by remember { mutableStateOf(AiSecureStore.getActiveProvider(context)) }
    val providerId = chat?.provider ?: preferredProvider
    val openCodeStatus by model.graph.openCode.status.collectAsStateWithLifecycle()
    val installedSkills = remember(chatId) {
        val arr = AgentSkillManager.loadAll(context)
        List(arr.length()) { idx ->
            val obj = arr.getJSONObject(idx)
            obj.optString("name") to obj.optString("description")
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            try {
                image = withContext(Dispatchers.IO) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
                    check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image" }
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = generateSequence(1) { it * 2 }.first { bounds.outWidth / it <= 1600 && bounds.outHeight / it <= 1600 }
                    }
                    val bitmap = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) } ?: error("Invalid image")
                    val file = File(context.filesDir, "attachments/${UUID.randomUUID()}.jpg")
                    file.parentFile?.mkdirs()
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                    bitmap.recycle()
                    file.path
                }
            } catch (error: Exception) {
                model.error.value = error.message
            }
        }
    }

    LaunchedEffect(suggested) { suggested?.let { prompt = it; model.suggestedPrompt.value = null } }
    LaunchedEffect(providerId) { if (providerId == "opencode") runCatching { model.graph.openCode.refresh() } }

    val list = rememberLazyListState()
    LaunchedEffect(chatId) { if (messages.isNotEmpty()) list.scrollToItem(messages.lastIndex) }
    LaunchedEffect(messages.lastOrNull()?.text, messages.size) {
        val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (messages.isNotEmpty() && (last >= messages.size - 3 || messages.size < 3)) list.scrollToItem(messages.lastIndex)
    }

    val visibleMessages = remember(messages) {
        messages.filterNot {
            it.role == "assistant" && it.status == "complete" &&
                (it.text.isBlank() && it.thinking.isBlank() || runCatching { JSONObject(it.metadata).optBoolean("tool_request") }.getOrDefault(false))
        }
    }

    val slashMatches = remember(prompt, installedSkills) {
        if (prompt.startsWith("/") && !prompt.contains(' ') && !prompt.contains('\n')) {
            val needle = prompt.removePrefix("/").lowercase()
            installedSkills.filter { it.first.lowercase().contains(needle) }
        } else emptyList()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Conversation header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChoiceMenu(chat?.name ?: stringResource(R.string.conversation), chats, { it.name }, { model.selectChat(it.id) }, Modifier.weight(1f))
            IconButton(onClick = { newChat = true }) { Icon(Icons.Outlined.Add, stringResource(R.string.new_chat)) }
            Box {
                IconButton(onClick = { more = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(more, { more = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, enabled = chat != null, onClick = { more = false; rename = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.regenerate)) }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) }, enabled = chat != null && chatId !in active && messages.any { it.role == "user" }, onClick = { more = false; model.regenerateLast() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.export_chat)) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, enabled = messages.isNotEmpty(), onClick = { more = false; clipboard.setText(AnnotatedString(model.exportChatMarkdown())) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.auto_execute)) }, leadingIcon = { Icon(Icons.Outlined.Bolt, null) }, enabled = chat != null, onClick = { more = false; autoConfirm = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.chat_broadcast)) }, leadingIcon = { Icon(Icons.Outlined.Campaign, null) }, enabled = prompt.isNotBlank() && chats.isNotEmpty(), onClick = { more = false; broadcast = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.clear_chat)) }, leadingIcon = { Icon(Icons.Outlined.ClearAll, null) }, enabled = chat != null && messages.isNotEmpty(), onClick = { more = false; clearConfirm = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = cs.error) }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = cs.error) }, enabled = chat != null, onClick = { more = false; delete = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, leadingIcon = { Icon(Icons.Outlined.Settings, null) }, onClick = { more = false; settings() })
                }
            }
        }

        // Provider & Session selectors
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceMenu(
                AiProvider.byId(providerId).name,
                AiProvider.all(),
                { it.name },
                { provider ->
                    if (chat != null) model.saveChat(chat.copy(provider = provider.id))
                    else { preferredProvider = provider.id; scope.launch { model.graph.settings.set("provider", provider.id) } }
                },
                Modifier.weight(1f)
            )
            ChoiceMenu(
                sessions.firstOrNull { it.id == chat?.sessionId }?.name ?: stringResource(R.string.session),
                sessions,
                { it.name },
                { if (chat != null) model.saveChat(chat.copy(sessionId = it.id)) else model.selectSession(it.id) },
                Modifier.weight(1f)
            )
        }

        // Model & Auto-Execute status strip
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val activeModel = AiSecureStore.getModel(context, providerId).ifBlank { stringResource(R.string.at_model_hint) }
            Text(
                activeModel,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable { settings() }
            )
            val autoOn = chat?.autoExecute ?: AiSecureStore.settings(context).getBoolean("agent_auto_execute", false)
            if (autoOn) TfmStatusBadge(stringResource(R.string.auto_short), tfm.warning)
        }

        if (providerId == "opencode") {
            val ready = openCodeStatus.installed && openCodeStatus.compatible
            Surface(color = cs.secondaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Extension, null, tint = cs.onSecondaryContainer)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.opencode), style = MaterialTheme.typography.titleSmall)
                        Text(
                            when {
                                !openCodeStatus.supported -> stringResource(R.string.opencode_not_supported)
                                !ready -> stringResource(R.string.opencode_install_short)
                                else -> stringResource(R.string.opencode_ready)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSecondaryContainer
                        )
                    }
                    TextButton(onClick = openOpenCode) {
                        Text(stringResource(if (ready) R.string.opencode_open_ui else R.string.opencode_setup_action))
                    }
                }
            }
        }

        // Chat transcript
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = list,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (visibleMessages.isEmpty()) {
                item {
                    EmptyPane(stringResource(R.string.chat_empty), stringResource(R.string.chat_empty_body), Icons.Outlined.AutoAwesome) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val starters = listOf(
                                R.string.starter_inspect to "Inspect the current workspace directory, list key files, and summarize the project.",
                                R.string.starter_git to "Check Git status and recent commit history, then summarize what changed.",
                                R.string.starter_runtimes to "Inspect installed developer runtimes and tools in Termux.",
                                R.string.starter_todos to "Search the project for TODO or FIXME comments and summarize them."
                            )
                            starters.forEach { (labelId, text) ->
                                OutlinedCard(
                                    onClick = { prompt = text },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp), tint = cs.primary)
                                        Text(stringResource(labelId), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(onClick = settings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings)) }
                        }
                    }
                }
            }
            items(visibleMessages, key = { it.id }) { row ->
                MessageBubble(
                    message = row,
                    run = { code -> terminal(sessions.firstOrNull { it.id == chat?.sessionId }?.workdir ?: "~/", code) },
                    approve = if (chatId in active && pending[chatId]?.id == row.id) { allowed -> model.graph.agent.approve(chatId, allowed, row.id) } else null
                )
            }
        }

        // Slash skill autocomplete popup
        if (slashMatches.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = cs.surfaceContainerHigh,
                border = BorderStroke(1.dp, cs.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(6.dp)) {
                    slashMatches.take(4).forEach { (name, desc) ->
                        ListItem(
                            headlineContent = { Text("/$name", fontFamily = FontFamily.Monospace, color = cs.primary) },
                            supportingContent = { Text(desc, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.clickable { prompt = "/$name " }
                        )
                    }
                }
            }
        }

        if (image.isNotBlank()) {
            InputChip(
                selected = true,
                onClick = { image = "" },
                label = { Text(stringResource(R.string.attachment_ready)) },
                leadingIcon = { Icon(Icons.Outlined.Image, null, Modifier.size(16.dp)) },
                trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.remove_attachment), Modifier.size(16.dp)) }
            )
        }

        // Composer bar
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = cs.surfaceContainer,
            border = BorderStroke(1.dp, cs.outlineVariant),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Row(Modifier.padding(6.dp), verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = { attachmentSheet = true }) {
                    Icon(Icons.Outlined.AttachFile, stringResource(R.string.attach_image))
                }
                TextField(
                    prompt, { prompt = it },
                    placeholder = { Text(stringResource(R.string.prompt)) },
                    maxLines = 5,
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                if (chatId in active) {
                    FilledIconButton(
                        onClick = { model.graph.agent.stop(chatId) },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = cs.error, contentColor = cs.onError)
                    ) { Icon(Icons.Outlined.Stop, stringResource(R.string.stop)) }
                } else {
                    FilledIconButton(
                        enabled = prompt.isNotBlank(),
                        onClick = { model.send(prompt, image); prompt = ""; image = "" }
                    ) { Icon(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.send)) }
                }
            }
        }
    }

    if (newChat) TextEntryDialog(stringResource(R.string.new_chat), label = stringResource(R.string.chat_name), dismiss = { newChat = false }) { newChat = false; model.newChat(it) }
    if (rename && chat != null) TextEntryDialog(stringResource(R.string.rename), chat.name, dismiss = { rename = false }) { rename = false; model.saveChat(chat.copy(name = it)) }
    if (delete && chat != null) ConfirmDialog(stringResource(R.string.delete), stringResource(R.string.chat_delete_confirm), { delete = false }, destructive = true) { delete = false; model.deleteChat(chat.id) }
    if (clearConfirm && chat != null) ConfirmDialog(stringResource(R.string.clear_chat), stringResource(R.string.clear_chat_confirm), { clearConfirm = false }, destructive = true) { clearConfirm = false; model.clearChat(chat.id) }
    if (autoConfirm && chat != null) ConfirmDialog(stringResource(R.string.auto_execute), stringResource(R.string.auto_execute_body), { autoConfirm = false }) {
        val enabled = chat.autoExecute ?: AiSecureStore.settings(context).getBoolean("agent_auto_execute", false)
        model.saveChat(chat.copy(autoExecute = !enabled))
        autoConfirm = false
    }
    if (broadcast) ConfirmDialog(stringResource(R.string.chat_broadcast), stringResource(R.string.chat_broadcast_notice), { broadcast = false }) { model.broadcastPrompt(prompt); prompt = ""; broadcast = false }

    if (attachmentSheet) {
        ModalBottomSheet(onDismissRequest = { attachmentSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.attach_image), style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(onClick = { attachmentSheet = false; imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.attach_image))
                }
                Text(stringResource(R.string.screen_capture_help), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                OutlinedButton(onClick = { attachmentSheet = false; context.startActivity(Intent(context, ScreenCaptureActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Screenshot, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.attach_screenshot))
                }
                OutlinedButton(
                    onClick = { val path = AiSecureStore.lastScreenshot(context); if (File(path).isFile) { image = path; attachmentSheet = false } },
                    enabled = AiSecureStore.lastScreenshot(context).isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.attach_last_capture)) }
                TextButton(
                    onClick = { context.startService(Intent(context, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP)); attachmentSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.stop_capture)) }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageRecord, run: (String) -> Unit, approve: ((Boolean) -> Unit)? = null) {
    val user = message.role == "user"
    val clipboard = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme
    var expanded by remember(message.id) { mutableStateOf(false) }
    var thinking by remember(message.id) { mutableStateOf(false) }

    if (message.role == "agent_control") return
    if (message.role == "tool_call" || message.role == "tool_output") {
        AgentToolCard(message, approve)
        return
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (user) cs.primaryContainer else cs.surfaceContainer,
            shape = RoundedCornerShape(
                topStart = 20.dp, topEnd = 20.dp,
                bottomStart = if (user) 20.dp else 6.dp,
                bottomEnd = if (user) 6.dp else 20.dp
            ),
            border = BorderStroke(1.dp, if (user) cs.primary.copy(alpha = 0.25f) else cs.outlineVariant),
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(if (user) 0.88f else 1f)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (message.role) {
                    "status" -> Text(errorText(message.text), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    "error" -> ErrorNotice(message.text)
                    "terminal" -> {
                        TextButton(onClick = { expanded = !expanded }) {
                            Icon(Icons.Outlined.Terminal, null); Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.tool_output))
                            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                        }
                        if (expanded) CodeText(message.text, copy = true, ansi = true)
                    }
                    else -> {
                        if (message.thinking.isNotBlank()) {
                            Surface(
                                onClick = { thinking = !thinking },
                                shape = RoundedCornerShape(12.dp),
                                color = cs.surfaceContainerHigh
                            ) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Outlined.Psychology, null, Modifier.size(16.dp), tint = cs.primary)
                                    Text(stringResource(R.string.thinking), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                    Icon(if (thinking) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(16.dp))
                                }
                            }
                            if (thinking) {
                                Text(message.thinking, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        }
                        if (message.status == "invalid") {
                            Text(stringResource(R.string.at_invalid), color = cs.error)
                            return@Column
                        }
                        val action = if (user) null else AgentAction.parse(message.text)
                        if (action != null) {
                            Text(action.value("message").ifBlank { action.value("reason").ifBlank { action.tool } })
                            if (action.value("command").isNotBlank()) {
                                Box(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                                    TfmCodeBlock(action.value("command"), language = "bash", copy = true)
                                }
                            }
                        } else {
                            TfmMarkdownView(message.text, if (user) null else run)
                        }
                        if (message.status == "streaming") LinearProgressIndicator(Modifier.width(80.dp))
                        if (message.status == "interrupted") {
                            Text(stringResource(R.string.interrupted), style = MaterialTheme.typography.labelSmall, color = cs.error)
                        }
                        if (!user && message.status == "complete" && message.text.isNotBlank()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = { clipboard.setText(AnnotatedString(message.text)) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy), Modifier.size(15.dp), tint = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
