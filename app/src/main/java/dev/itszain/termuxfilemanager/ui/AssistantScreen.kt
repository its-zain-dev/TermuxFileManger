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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
import java.io.File
import java.util.UUID
import org.json.JSONObject

@OptIn(ExperimentalLayoutApi::class)
@Composable fun AssistantScreen(model: WorkspaceModel, settings: () -> Unit, terminal: (String, String) -> Unit) {
    val chats by model.chats.collectAsStateWithLifecycle()
    val sessions by model.sessions.collectAsStateWithLifecycle()
    val chatId by model.chatId.collectAsStateWithLifecycle()
    val messages by model.messages.collectAsStateWithLifecycle()
    val active by model.graph.agent.active.collectAsStateWithLifecycle()
    val pending by model.graph.agent.pending.collectAsStateWithLifecycle()
    val suggested by model.suggestedPrompt.collectAsStateWithLifecycle()
    val chat = chats.firstOrNull { it.id == chatId }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prompt by rememberSaveable { mutableStateOf("") }
    var image by rememberSaveable { mutableStateOf("") }
    var newChat by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var attachmentMenu by remember { mutableStateOf(false) }
    var broadcast by remember { mutableStateOf(false) }
    var autoConfirm by remember { mutableStateOf(false) }
    var preferredProvider by remember { mutableStateOf(AiSecureStore.getActiveProvider(context)) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri != null) scope.launch {
            try {
                image = withContext(Dispatchers.IO) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
                    check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image" }
                    val options = BitmapFactory.Options().apply { inSampleSize = generateSequence(1) { it * 2 }.first { bounds.outWidth / it <= 1600 && bounds.outHeight / it <= 1600 } }
                    val bitmap = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) } ?: error("Invalid image")
                    val file = File(context.filesDir, "attachments/${UUID.randomUUID()}.jpg"); file.parentFile?.mkdirs()
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }; bitmap.recycle(); file.path
                }
            } catch(error: Exception) { model.error.value = error.message }
        }
    }
    LaunchedEffect(suggested) { suggested?.let { prompt = it; model.suggestedPrompt.value = null } }
    val list = rememberLazyListState()
    LaunchedEffect(chatId) { if(messages.isNotEmpty()) list.scrollToItem(messages.lastIndex) }
    LaunchedEffect(messages.lastOrNull()?.text, messages.size) {
        val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if(messages.isNotEmpty() && (last >= messages.size - 3 || messages.size < 3)) list.scrollToItem(messages.lastIndex)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChoiceMenu(chat?.name ?: stringResource(R.string.conversation), chats, { it.name }, { model.selectChat(it.id) }, Modifier.weight(1f))
            IconButton(onClick = { newChat = true }) { Icon(Icons.Outlined.Add, stringResource(R.string.new_chat)) }
            Box { IconButton(onClick = { more = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(more, { more = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, enabled = chat != null, onClick = { more = false; rename = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, enabled = chat != null, onClick = { more = false; delete = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.auto_execute)) }, enabled = chat != null, onClick = { more = false; autoConfirm = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.chat_broadcast)) }, enabled = prompt.isNotBlank() && chats.isNotEmpty(), onClick = { more = false; broadcast = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, onClick = { more = false; settings() })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceMenu(AiProvider.byId(chat?.provider ?: preferredProvider).name, AiProvider.all(), { it.name }, { provider ->
                if(chat != null) model.saveChat(chat.copy(provider = provider.id)) else { preferredProvider = provider.id; scope.launch { model.graph.settings.set("provider", provider.id) } }
            }, Modifier.weight(1f))
            ChoiceMenu(sessions.firstOrNull { it.id == chat?.sessionId }?.name ?: stringResource(R.string.session), sessions, { it.name }, { if(chat != null) model.saveChat(chat.copy(sessionId = it.id)) else model.selectSession(it.id) }, Modifier.weight(1f))
        }
        Text(AiSecureStore.getModel(context, chat?.provider ?: preferredProvider).ifBlank { stringResource(R.string.at_model_hint) },
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            if(messages.isEmpty()) item {
                EmptyPane(stringResource(R.string.chat_empty), stringResource(R.string.chat_empty_body), Icons.Outlined.AutoAwesome) {
                    OutlinedButton(onClick = settings) { Text(stringResource(R.string.settings)) }
                }
            }
            items(messages.filterNot { it.role == "assistant" && it.status == "complete" && (it.text.isBlank() && it.thinking.isBlank() || runCatching { JSONObject(it.metadata).optBoolean("tool_request") }.getOrDefault(false)) }, key = { it.id }) { row ->
                MessageBubble(row, { code -> terminal(sessions.firstOrNull { it.id == chat?.sessionId }?.workdir ?: "~/", code) },
                    if(chatId in active && pending[chatId]?.id == row.id) { allowed -> model.graph.agent.approve(chatId, allowed, row.id) } else null)
            }
        }
        if(image.isNotBlank()) InputChip(selected = true, onClick = { image = "" }, label = { Text(stringResource(R.string.attachment_ready)) }, trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.remove_attachment)) })
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.padding(bottom = 10.dp)) {
            Row(Modifier.padding(6.dp), verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = { attachmentMenu = true }) { Icon(Icons.Outlined.AttachFile, stringResource(R.string.attach_image)) }
                TextField(prompt, { prompt = it }, placeholder = { Text(stringResource(R.string.prompt)) }, maxLines = 5, modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent))
                if(chatId in active) FilledIconButton(onClick = { model.graph.agent.stop(chatId) }) { Icon(Icons.Outlined.Stop, stringResource(R.string.stop)) }
                else FilledIconButton(enabled = prompt.isNotBlank(), onClick = { model.send(prompt, image); prompt = ""; image = "" }) { Icon(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.send)) }
            }
        }
    }
    if(newChat) TextEntryDialog(stringResource(R.string.new_chat), label = stringResource(R.string.chat_name), dismiss = { newChat = false }) { newChat = false; model.newChat(it) }
    if(rename && chat != null) TextEntryDialog(stringResource(R.string.rename), chat.name, dismiss = { rename = false }) { rename = false; model.saveChat(chat.copy(name = it)) }
    if(delete && chat != null) ConfirmDialog(stringResource(R.string.delete), stringResource(R.string.chat_delete_confirm), { delete = false }) { delete = false; model.deleteChat(chat.id) }
    if(autoConfirm && chat != null) ConfirmDialog(stringResource(R.string.auto_execute), stringResource(R.string.auto_execute_body), { autoConfirm = false }) {
        val enabled = chat.autoExecute ?: AiSecureStore.settings(context).getBoolean("agent_auto_execute", false)
        model.saveChat(chat.copy(autoExecute = !enabled)); autoConfirm = false
    }
    if(broadcast) ConfirmDialog(stringResource(R.string.chat_broadcast), stringResource(R.string.chat_broadcast_notice), { broadcast = false }) { model.broadcastPrompt(prompt); prompt = ""; broadcast = false }
    if(attachmentMenu) AlertDialog(onDismissRequest = { attachmentMenu = false }, title = { Text(stringResource(R.string.attach_image)) }, text = {
        Column {
            TextButton(onClick = { attachmentMenu = false; imagePicker.launch("image/*") }) { Text(stringResource(R.string.attach_image)) }
            Text(stringResource(R.string.screen_capture_help), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { attachmentMenu = false; context.startActivity(Intent(context, ScreenCaptureActivity::class.java)) }) { Text(stringResource(R.string.attach_screenshot)) }
            TextButton(onClick = { val path = AiSecureStore.lastScreenshot(context); if(File(path).isFile) { image = path; attachmentMenu = false } }, enabled = AiSecureStore.lastScreenshot(context).isNotBlank()) { Text(stringResource(R.string.attach_last_capture)) }
            TextButton(onClick = { context.startService(Intent(context, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP)); attachmentMenu = false }) { Text(stringResource(R.string.stop_capture)) }
        }
    }, confirmButton = { TextButton(onClick = { attachmentMenu = false }) { Text(stringResource(R.string.close)) } })
}

@Composable private fun MessageBubble(message: MessageRecord, run: (String) -> Unit, approve: ((Boolean) -> Unit)? = null) {
    val user = message.role == "user"
    var expanded by remember(message.id) { mutableStateOf(false) }
    var thinking by remember(message.id) { mutableStateOf(false) }
    if(message.role == "agent_control") return
    if(message.role == "tool_call" || message.role == "tool_output") { AgentToolCard(message, approve); return }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if(user) Arrangement.End else Arrangement.Start) {
        Surface(color = if(user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(if(user) 22.dp else 16.dp), modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(if(user) 0.9f else 1f)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when(message.role) {
                    "status" -> Text(errorText(message.text), style = MaterialTheme.typography.bodySmall)
                    "error" -> ErrorNotice(message.text)
                    "tool_output", "terminal" -> {
                        TextButton(onClick = { expanded = !expanded }) { Icon(Icons.Outlined.Terminal, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.tool_output)); Icon(if(expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) }
                        if(expanded) CodeText(message.text, copy = true)
                    }
                    else -> {
                        if(message.thinking.isNotBlank()) {
                            TextButton(onClick = { thinking = !thinking }) { Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.thinking)) }
                            if(thinking) Text(message.thinking, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if(message.status == "invalid") { Text(stringResource(R.string.at_invalid)); return@Column }
                        val action = if(user) null else AgentAction.parse(message.text)
                        if(action != null) {
                            Text(action.value("message").ifBlank { action.value("reason").ifBlank { action.tool } })
                            if(action.value("command").isNotBlank()) Box(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) { CodeText(action.value("command"), copy = true) }
                        } else MarkdownContent(message.text, if(user) null else run)
                        if(message.status == "streaming") LinearProgressIndicator(Modifier.width(70.dp))
                        if(message.status == "interrupted") Text(stringResource(R.string.interrupted), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
@Composable fun MarkdownContent(text: String, run: ((String) -> Unit)? = null) {
    val blocks = Regex("```([^\\n]*)\\n([\\s\\S]*?)```").findAll(text).toList()
    var start = 0
    for(block in blocks) {
        MarkdownParagraphs(text.substring(start, block.range.first))
        CodeText(block.groupValues[2].trimEnd(), copy = true)
        if(run != null && block.groupValues[1].trim().lowercase() in setOf("sh", "bash", "shell", "zsh")) TextButton(onClick = { run(block.groupValues[2]) }) { Text(stringResource(R.string.open_terminal)) }
        start = block.range.last + 1
    }
    MarkdownParagraphs(text.substring(start))
}
@Composable private fun MarkdownParagraphs(text: String) {
    if(text.isBlank()) return
    androidx.compose.foundation.text.selection.SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            text.trim().split('\n').forEach { line ->
                val heading = line.startsWith("#")
                val raw = if(heading) line.trimStart('#', ' ') else line
                val annotated = buildAnnotatedString {
                    val bold = Regex("\\*\\*(.+?)\\*\\*").findAll(raw); var cursor = 0
                    for(match in bold) { append(raw.substring(cursor, match.range.first)); pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append(match.groupValues[1]); pop(); cursor = match.range.last + 1 }
                    append(raw.substring(cursor))
                }
                Text(annotated, style = if(heading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
