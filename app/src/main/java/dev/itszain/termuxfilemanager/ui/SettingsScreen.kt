package dev.itszain.termuxfilemanager.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.itszain.termuxfilemanager.*
import dev.itszain.termuxfilemanager.R
import dev.itszain.termuxfilemanager.core.*
import dev.itszain.termuxfilemanager.data.AppSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.json.JSONArray

@Composable fun SettingsScreen(graph: AppGraph, setup: () -> Unit) {
    val appearance by graph.settings.appearance.collectAsStateWithLifecycle(AppSettings())
    val context = LocalContext.current
    val chatAccounts by graph.chatGpt.accounts.collectAsStateWithLifecycle()
    val activeAccount by graph.chatGpt.active.collectAsStateWithLifecycle()
    val signingIn by graph.chatGpt.busy.collectAsStateWithLifecycle()
    val loginError by graph.chatGpt.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf(AiProvider.byId(AiSecureStore.getActiveProvider(context))) }
    var model by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var modelChoices by remember { mutableStateOf<List<String>?>(null) }
    var removeKey by remember { mutableStateOf(false) }
    var autoConfirm by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var skillDialog by remember { mutableStateOf(false) }
    var skillUrl by remember { mutableStateOf("") }
    var skillName by remember { mutableStateOf("") }
    var removeSkill by remember { mutableStateOf<String?>(null) }
    val preferences = AiSecureStore.settings(context)
    var daily by remember(revision) { mutableStateOf(preferences.getInt("rate_daily_max", 40).toString()) }
    var gap by remember(revision) { mutableStateOf(preferences.getInt("rate_gap_seconds", 3).toString()) }
    var output by remember(revision) { mutableStateOf(preferences.getInt("output_budget_tokens", 2048).toString()) }
    var thinking by remember(revision) { mutableStateOf(preferences.getInt("thinking_budget_tokens", 0).toString()) }
    var level by remember(revision) { mutableStateOf(preferences.getString("thinking_level", "medium").orEmpty()) }
    var instructions by remember(revision) { mutableStateOf(preferences.getString("agent_instructions", "").orEmpty()) }
    var systemPrompt by remember(revision) { mutableStateOf(preferences.getString("custom_system_prompt", "").orEmpty()) }
    val skills = remember(revision) { AgentSkillManager.loadAll(context) }
    fun task(action: suspend () -> String) {
        if(busy) return
        busy = true
        scope.launch {
            try { message = withContext(Dispatchers.IO) { action() }; revision++ }
            catch(error: CancellationException) { throw error }
            catch(error: Exception) { message = error.message }
            finally { busy = false }
        }
    }
    suspend fun saveProvider() {
        require(endpoint.startsWith("https://")) { "https_required" }
        require(model.isNotBlank()) { "missing_model" }
        graph.settings.set("provider", provider.id)
        graph.settings.set("model_${provider.id}", model.trim())
        graph.settings.set("endpoint_${provider.id}", endpoint.trim())
        if(key.isNotBlank()) AiSecureStore.saveKey(context, provider.id, key)
        AgentWorkspace.persistSettings(context)
    }
    LaunchedEffect(provider.id, revision) {
        model = AiSecureStore.getModel(context, provider.id); endpoint = AiSecureStore.getEndpoint(context, provider.id)
        hasKey = withContext(Dispatchers.IO) { runCatching { AiSecureStore.getKey(context, provider.id).isNotBlank() }.getOrDefault(false) }; key = ""
    }
    LaunchedEffect(activeAccount) {
        if(activeAccount.isNotBlank()) {
            provider = AiProvider.byId("chatgpt")
            try {
                val choices = withContext(Dispatchers.IO) { graph.ai.models("chatgpt") }
                if(choices.isNotEmpty()) {
                    val selected = AiSecureStore.getModel(context, "chatgpt").takeIf { it in choices } ?: choices.first()
                    graph.settings.set("model_chatgpt", selected); model = selected; graph.settings.set("provider", "chatgpt")
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(failure: Exception) { message = failure.message }
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle(stringResource(R.string.appearance)) }
        item { Text(stringResource(R.string.theme), style = MaterialTheme.typography.labelLarge) }
        item {
            val labels = mapOf("system" to stringResource(R.string.system), "light" to stringResource(R.string.light), "midnight" to stringResource(R.string.dark), "amoled" to stringResource(R.string.amoled))
            ChoiceMenu(labels[appearance.theme] ?: labels.getValue("midnight"), labels.keys.toList(), { labels.getValue(it) }, { scope.launch { graph.settings.set("theme", it) } })
        }
        item { Text(stringResource(R.string.language), style = MaterialTheme.typography.labelLarge) }
        item {
            val labels = mapOf("system" to stringResource(R.string.system), "ar" to stringResource(R.string.arabic), "en" to stringResource(R.string.english))
            ChoiceMenu(labels[appearance.language] ?: labels.getValue("system"), labels.keys.toList(), { labels.getValue(it) }, { scope.launch { graph.settings.set("language", it) } })
        }
        item { OutlinedButton(onClick = setup, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Link, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.connect_termux)) } }
        item { SectionTitle(stringResource(R.string.chatgpt_title)) }
        item { Text(stringResource(R.string.chatgpt_info), style = MaterialTheme.typography.bodyMedium) }
        if(chatAccounts.isNotEmpty()) item { ChoiceMenu(chatAccounts.firstOrNull { it.first == activeAccount }?.second ?: stringResource(R.string.chatgpt_account), chatAccounts, { it.second }, { graph.chatGpt.login(it.first) }) }
        item { Button(enabled = !signingIn, onClick = { graph.chatGpt.login(activeAccount.takeIf { it.isNotBlank() }) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.chatgpt_continue)) } }
        if(signingIn) item { TextButton(onClick = { graph.chatGpt.cancel() }) { Text(stringResource(R.string.cancel)) } }
        if(chatAccounts.isNotEmpty()) item { Row { TextButton(enabled = !signingIn, onClick = { graph.chatGpt.login() }) { Text(stringResource(R.string.chatgpt_add)) }; if(activeAccount.isNotBlank()) TextButton(onClick = { task { graph.chatGpt.logout(activeAccount); "saved" } }) { Text(stringResource(R.string.chatgpt_sign_out)) } } }
        loginError?.let { failure -> item { ErrorNotice(failure) } }
        item { TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/settings/usage"))) }) { Text(stringResource(R.string.chatgpt_usage)) } }
        item { SectionTitle(stringResource(R.string.provider)) }
        item { ChoiceMenu(provider.name, AiProvider.all(), { it.name }, { provider = it }) }
        item { OutlinedTextField(model, { model = it }, label = { Text(stringResource(R.string.model)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        if(provider.id != "chatgpt") item { OutlinedTextField(endpoint, { endpoint = it }, label = { Text(stringResource(R.string.endpoint)) }, maxLines = 3, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)) }
        if(provider.id != "chatgpt") item { OutlinedTextField(key, { key = it }, label = { Text(stringResource(R.string.api_key)) }, supportingText = { if(hasKey) Text(stringResource(R.string.key_saved)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { Button(enabled = !busy, onClick = { task { saveProvider(); "saved" } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_provider)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = { task { saveProvider(); val found = graph.ai.models(provider.id); withContext(Dispatchers.Main) { modelChoices = found }; "" } }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.discover_models)) }
            OutlinedButton(enabled = !busy, onClick = { task { saveProvider(); var result = ""; graph.ai.stream(AiRequest(provider.id, listOf("user" to "Reply with exactly: OK"))).collect { if(it is AiEvent.Complete) result = it.value }; result } }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.test_connection)) }
        } }
        item { Row {
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.docs))) }) { Text(stringResource(R.string.documentation)) }
            if(hasKey && provider.id != "chatgpt") TextButton(onClick = { removeKey = true }) { Text(stringResource(R.string.remove_key), color = MaterialTheme.colorScheme.error) }
        } }
        item { SectionTitle(stringResource(R.string.usage_limits)) }
        item { NumberField(daily, { daily = it }, R.string.daily_requests) }
        item { NumberField(gap, { gap = it }, R.string.request_gap) }
        item { NumberField(output, { output = it }, R.string.output_tokens) }
        item { NumberField(thinking, { thinking = it }, R.string.thinking_tokens) }
        item { Text(stringResource(R.string.thinking_level)); ChoiceMenu(level, listOf("minimal", "low", "medium", "high"), { it }, { level = it }) }
        item { Button(enabled = !busy, onClick = { task {
            fun number(value: String, range: IntRange): Int { val parsed = value.toIntOrNull(); require(parsed != null && parsed in range) { "Invalid number: ${range.first}–${range.last}" }; return parsed }
            graph.settings.set("rate_daily_max", number(daily, 1..10000)); graph.settings.set("rate_gap_seconds", number(gap, 0..3600))
            graph.settings.set("output_budget_tokens", number(output, 128..8192)); graph.settings.set("thinking_budget_tokens", number(thinking, 0..8192)); graph.settings.set("thinking_level", level)
            AgentWorkspace.persistSettings(context); "saved"
        } }) { Text(stringResource(R.string.save)) } }
        item { SectionTitle(stringResource(R.string.agent_options)) }
        item { SettingSwitch(stringResource(R.string.auto_execute), remember(revision) { preferences.getBoolean("agent_auto_execute", false) }) { autoConfirm = true } }
        items(listOf("agent_web_search_enabled" to R.string.web_search, "agent_skills_enabled" to R.string.skills_enabled)) { (name, label) ->
            SettingSwitch(stringResource(label), remember(revision) { preferences.getBoolean(name, name != "auto_analyze_results") }) { enabled -> task { graph.settings.set(name, enabled); AgentWorkspace.persistSettings(context); "saved" } }
        }
        item { OutlinedTextField(instructions, { instructions = it }, label = { Text(stringResource(R.string.instructions)) }, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(systemPrompt, { systemPrompt = it }, label = { Text(stringResource(R.string.system_prompt)) }, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth()) }
        item { Button(enabled = !busy, onClick = { task { graph.settings.set("agent_instructions", instructions); graph.settings.set("custom_system_prompt", systemPrompt); AgentWorkspace.persistPromptFiles(context); "saved" } }) { Text(stringResource(R.string.save_instructions)) } }
        item { OutlinedButton(enabled = !busy, onClick = { task {
            val result = graph.commands.execute(CommandSpec(AgentWorkspace.syncCommand(context), ownerId = "settings", label = "Workspace sync", kind = "workspace"))
            check(result.exitCode == 0) { result.output }; graph.applyWorkspace(result.stdout); "saved"
        } }) { Text(stringResource(R.string.sync_workspace)) } }
        item { SectionTitle(stringResource(R.string.skills)) }
        if(skills.length() == 0) item { Text(stringResource(R.string.no_skills), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(skills.length()) { index -> val skill = skills.getJSONObject(index)
            ListItem(headlineContent = { Text("/" + skill.optString("name")) }, supportingContent = { Text(skill.optString("description")) }, trailingContent = { IconButton(onClick = { removeSkill = skill.optString("name") }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete)) } })
        }
        item { OutlinedButton(enabled = !busy, onClick = { skillDialog = true }) { Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.install_skill)) } }
        item { SectionTitle(stringResource(R.string.about)); Text(stringResource(R.string.version), style = MaterialTheme.typography.bodyMedium) }
    }
    if(busy) AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.loading)) }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }, confirmButton = {})
    message?.takeIf { it.isNotBlank() }?.let { text -> AlertDialog(onDismissRequest = { message = null }, text = { Text(errorText(text).let { if(it == "saved") stringResource(R.string.saved) else it }) }, confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.close)) } }) }
    modelChoices?.let { choices -> AlertDialog(onDismissRequest = { modelChoices = null }, title = { Text(stringResource(R.string.model)) }, text = {
        LazyColumn(Modifier.heightIn(max = 400.dp)) { items(choices) { choice -> TextButton(onClick = { model = choice; modelChoices = null }) { Text(graph.ai.modelNames[choice] ?: choice) } } }
    }, confirmButton = { TextButton(onClick = { modelChoices = null }) { Text(stringResource(R.string.close)) } }) }
    if(removeKey) ConfirmDialog(stringResource(R.string.remove_key), stringResource(R.string.remove_key_confirm), { removeKey = false }) { removeKey = false; task { AiSecureStore.saveKey(context, provider.id, ""); "saved" } }
    if(autoConfirm) ConfirmDialog(stringResource(R.string.auto_execute), stringResource(R.string.auto_execute_body), { autoConfirm = false }) { autoConfirm = false; task { graph.settings.set("agent_auto_execute", !preferences.getBoolean("agent_auto_execute", false)); AgentWorkspace.persistSettings(context); "saved" } }
    if(skillDialog) AlertDialog(onDismissRequest = { skillDialog = false }, title = { Text(stringResource(R.string.install_skill)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(skillUrl, { skillUrl = it }, label = { Text(stringResource(R.string.skill_url)) }); OutlinedTextField(skillName, { skillName = it }, label = { Text(stringResource(R.string.name)) }) }
    }, confirmButton = { TextButton(enabled = skillUrl.startsWith("https://"), onClick = { skillDialog = false; task { AgentSkillManager.installFromUrl(context, skillUrl, skillName); graph.settings.refreshWorkspace(); "saved" } }) { Text(stringResource(R.string.install_skill)) } }, dismissButton = { TextButton(onClick = { skillDialog = false }) { Text(stringResource(R.string.cancel)) } })
    removeSkill?.let { name -> ConfirmDialog(stringResource(R.string.delete), name, { removeSkill = null }) { removeSkill = null; task { AgentSkillManager.delete(context, name); graph.settings.refreshWorkspace(); "saved" } } }
}
@Composable private fun NumberField(value: String, change: (String) -> Unit, label: Int) {
    OutlinedTextField(value, change, label = { Text(stringResource(label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
}
@Composable private fun SettingSwitch(label: String, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(enabled, change) }
}
