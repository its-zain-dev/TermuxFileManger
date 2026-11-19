package dev.itszain.termuxfilemanager.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.itszain.termuxfilemanager.*
import dev.itszain.termuxfilemanager.R
import dev.itszain.termuxfilemanager.core.*
import dev.itszain.termuxfilemanager.data.AppSettings
import dev.itszain.termuxfilemanager.data.OpenCodeManager
import kotlinx.coroutines.*

@Composable
fun SettingsScreen(graph: AppGraph, setup: () -> Unit, openOpenCode: () -> Unit = {}) {
    val appearance by graph.settings.appearance.collectAsStateWithLifecycle(AppSettings())
    val context = LocalContext.current
    val chatAccounts by graph.chatGpt.accounts.collectAsStateWithLifecycle()
    val activeAccount by graph.chatGpt.active.collectAsStateWithLifecycle()
    val signingIn by graph.chatGpt.busy.collectAsStateWithLifecycle()
    val loginError by graph.chatGpt.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

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
    var inspectSkill by remember { mutableStateOf<Pair<String, String>?>(null) }
    var privacyDialog by remember { mutableStateOf(false) }

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
        if (busy) return
        busy = true
        scope.launch {
            try {
                message = withContext(Dispatchers.IO) { action() }
                revision++
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                message = error.message
            } finally {
                busy = false
            }
        }
    }

    suspend fun saveProvider() {
        if (provider.id == "opencode") {
            graph.settings.set("provider", provider.id)
            graph.settings.set("model_opencode", model.trim())
            AgentWorkspace.persistSettings(context)
            return
        }
        require(AiProvider.isAllowedEndpoint(endpoint)) { "https_required" }
        require(model.isNotBlank()) { "missing_model" }
        graph.settings.set("provider", provider.id)
        graph.settings.set("model_${provider.id}", model.trim())
        graph.settings.set("endpoint_${provider.id}", endpoint.trim())
        if (key.isNotBlank()) AiSecureStore.saveKey(context, provider.id, key)
        AgentWorkspace.persistSettings(context)
    }

    LaunchedEffect(provider.id, revision) {
        model = AiSecureStore.getModel(context, provider.id)
        endpoint = AiSecureStore.getEndpoint(context, provider.id)
        hasKey = withContext(Dispatchers.IO) { runCatching { AiSecureStore.getKey(context, provider.id).isNotBlank() }.getOrDefault(false) }
        key = ""
        if (provider.id == "opencode" && model.isBlank()) {
            val found = withContext(Dispatchers.IO) { runCatching { graph.ai.models("opencode") }.getOrNull() }.orEmpty()
            if (found.isNotEmpty()) {
                model = found.first()
                graph.settings.set("model_opencode", model)
            }
        }
    }

    LaunchedEffect(activeAccount) {
        if (activeAccount.isNotBlank()) {
            provider = AiProvider.byId("chatgpt")
            try {
                val choices = withContext(Dispatchers.IO) { graph.ai.models("chatgpt") }
                if (choices.isNotEmpty()) {
                    val selected = AiSecureStore.getModel(context, "chatgpt").takeIf { it in choices } ?: choices.first()
                    graph.settings.set("model_chatgpt", selected)
                    model = selected
                    graph.settings.set("provider", "chatgpt")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                message = failure.message
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Appearance & Themes
        item { SectionTitle(stringResource(R.string.appearance), icon = Icons.Outlined.Palette) }
        item {
            SettingsCard {
                Text(stringResource(R.string.theme), style = MaterialTheme.typography.labelLarge)
                val themes = listOf(
                    Triple("system", R.string.system, Color(0xFF4CE0C2)),
                    Triple("midnight", R.string.dark, Color(0xFF0B1014)),
                    Triple("amoled", R.string.amoled, Color.Black),
                    Triple("nord", R.string.theme_nord, Color(0xFF1B2335)),
                    Triple("light", R.string.light, Color(0xFFF4F8F6))
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    themes.forEach { (id, labelRes, swatch) ->
                        FilterChip(
                            selected = appearance.theme == id,
                            onClick = { scope.launch { graph.settings.set("theme", id) } },
                            leadingIcon = { Box(Modifier.size(14.dp).clip(CircleShape).background(swatch)) },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingSwitch(stringResource(R.string.dynamic_color), appearance.dynamicColor) {
                        scope.launch { graph.settings.set("dynamic_color", it) }
                    }
                }
                Text(stringResource(R.string.language), style = MaterialTheme.typography.labelLarge)
                val langLabels = mapOf("system" to stringResource(R.string.system), "ar" to stringResource(R.string.arabic), "en" to stringResource(R.string.english))
                ChoiceMenu(
                    langLabels[appearance.language] ?: langLabels.getValue("system"),
                    langLabels.keys.toList(),
                    { langLabels.getValue(it) },
                    { scope.launch { graph.settings.set("language", it) } }
                )
            }
        }

        // Workspace, Editor & Background Behavior
        item { SectionTitle(stringResource(R.string.workspace_prefs), icon = Icons.Outlined.Tune) }
        item {
            SettingsCard {
                SettingSwitch(stringResource(R.string.terminal_confirm_run), appearance.terminalConfirmRun) {
                    scope.launch { graph.settings.set("terminal_confirm_run", it) }
                }
                SettingSwitch(stringResource(R.string.background_notifications), appearance.backgroundNotifications) {
                    scope.launch { graph.settings.set("background_notifications", it) }
                }
                SettingSwitch(stringResource(R.string.syntax_highlight), appearance.editorSyntaxHighlight) {
                    scope.launch { graph.settings.set("editor_syntax_highlight", it) }
                }
                SettingSwitch(stringResource(R.string.word_wrap), appearance.editorWordWrap) {
                    scope.launch { graph.settings.set("editor_word_wrap", it) }
                }
                OutlinedButton(onClick = setup, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Outlined.Link, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.connect_termux))
                }
            }
        }

        // ChatGPT Plan OAuth
        item { SectionTitle(stringResource(R.string.chatgpt_title), icon = Icons.Outlined.VerifiedUser) }
        item {
            SettingsCard {
                Text(stringResource(R.string.chatgpt_info), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                if (chatAccounts.isNotEmpty()) {
                    ChoiceMenu(
                        chatAccounts.firstOrNull { it.first == activeAccount }?.second ?: stringResource(R.string.chatgpt_account),
                        chatAccounts,
                        { it.second },
                        { graph.chatGpt.login(it.first) }
                    )
                }
                Button(enabled = !signingIn, onClick = { graph.chatGpt.login(activeAccount.takeIf { it.isNotBlank() }) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.chatgpt_continue))
                }
                if (signingIn) TextButton(onClick = { graph.chatGpt.cancel() }) { Text(stringResource(R.string.cancel)) }
                if (chatAccounts.isNotEmpty()) {
                    Row {
                        TextButton(enabled = !signingIn, onClick = { graph.chatGpt.login() }) { Text(stringResource(R.string.chatgpt_add)) }
                        if (activeAccount.isNotBlank()) {
                            TextButton(onClick = { task { graph.chatGpt.logout(activeAccount); "saved" } }) { Text(stringResource(R.string.chatgpt_sign_out), color = cs.error) }
                        }
                    }
                }
                loginError?.let { ErrorNotice(it) }
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/settings/usage"))) }) {
                    Text(stringResource(R.string.chatgpt_usage))
                }
            }
        }

        // AI Provider Configuration
        item { SectionTitle(stringResource(R.string.provider), icon = Icons.Outlined.AutoAwesome) }
        item {
            SettingsCard {
                ChoiceMenu(provider.name, AiProvider.all(), { it.name }, { provider = it })
                if (provider.id == "opencode") OpenCodeSettingsCard(graph, openOpenCode)
                if (provider.id == "ollama") {
                    Text(stringResource(R.string.ollama_hint), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                OutlinedTextField(model, { model = it }, label = { Text(stringResource(R.string.model)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (provider.id != "chatgpt" && provider.id != "opencode") {
                    OutlinedTextField(
                        endpoint, { endpoint = it },
                        label = { Text(stringResource(R.string.endpoint)) },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                    OutlinedTextField(
                        key, { key = it },
                        label = { Text(stringResource(if (provider.id == "ollama") R.string.api_key_optional else R.string.api_key)) },
                        supportingText = { if (hasKey) Text(stringResource(R.string.key_saved)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Button(enabled = !busy, onClick = { task { saveProvider(); "saved" } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.save_provider))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { task { saveProvider(); val found = graph.ai.models(provider.id); withContext(Dispatchers.Main) { modelChoices = found }; "" } },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.discover_models), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            task {
                                saveProvider()
                                val t0 = System.currentTimeMillis()
                                var result = ""
                                graph.ai.stream(AiRequest(provider.id, listOf("user" to "Reply with exactly: OK"))).collect {
                                    if (it is AiEvent.Complete) result = it.value
                                }
                                val ms = System.currentTimeMillis() - t0
                                "$result (${ms}ms)"
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.test_connection), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Row {
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.docs))) }) { Text(stringResource(R.string.documentation)) }
                    if (hasKey && provider.id != "chatgpt" && provider.id != "opencode") {
                        TextButton(onClick = { removeKey = true }) { Text(stringResource(R.string.remove_key), color = cs.error) }
                    }
                }
            }
        }

        // Usage Limits
        item { SectionTitle(stringResource(R.string.usage_limits), icon = Icons.Outlined.Speed) }
        item {
            SettingsCard {
                NumberField(daily, { daily = it }, R.string.daily_requests)
                NumberField(gap, { gap = it }, R.string.request_gap)
                NumberField(output, { output = it }, R.string.output_tokens)
                NumberField(thinking, { thinking = it }, R.string.thinking_tokens)
                Text(stringResource(R.string.thinking_level), style = MaterialTheme.typography.labelLarge)
                ChoiceMenu(level, listOf("minimal", "low", "medium", "high"), { it }, { level = it })
                Button(
                    enabled = !busy,
                    onClick = {
                        task {
                            fun number(value: String, range: IntRange): Int {
                                val parsed = value.toIntOrNull()
                                require(parsed != null && parsed in range) { "Invalid number: ${range.first}–${range.last}" }
                                return parsed
                            }
                            graph.settings.set("rate_daily_max", number(daily, 1..10000))
                            graph.settings.set("rate_gap_seconds", number(gap, 0..3600))
                            graph.settings.set("output_budget_tokens", number(output, 128..8192))
                            graph.settings.set("thinking_budget_tokens", number(thinking, 0..8192))
                            graph.settings.set("thinking_level", level)
                            AgentWorkspace.persistSettings(context)
                            "saved"
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.save)) }
            }
        }

        // Agent Behavior
        item { SectionTitle(stringResource(R.string.agent_options), icon = Icons.Outlined.Psychology) }
        item {
            SettingsCard {
                SettingSwitch(stringResource(R.string.auto_execute), remember(revision) { preferences.getBoolean("agent_auto_execute", false) }) { autoConfirm = true }
                listOf("agent_web_search_enabled" to R.string.web_search, "agent_skills_enabled" to R.string.skills_enabled).forEach { (name, label) ->
                    SettingSwitch(stringResource(label), remember(revision) { preferences.getBoolean(name, true) }) { enabled ->
                        task { graph.settings.set(name, enabled); AgentWorkspace.persistSettings(context); "saved" }
                    }
                }
                OutlinedTextField(instructions, { instructions = it }, label = { Text(stringResource(R.string.instructions)) }, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(systemPrompt, { systemPrompt = it }, label = { Text(stringResource(R.string.system_prompt)) }, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy,
                        onClick = { task { graph.settings.set("agent_instructions", instructions); graph.settings.set("custom_system_prompt", systemPrompt); AgentWorkspace.persistPromptFiles(context); "saved" } },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.save_instructions)) }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            task {
                                val result = graph.commands.execute(CommandSpec(AgentWorkspace.syncCommand(context), ownerId = "settings", label = "Workspace sync", kind = "workspace"))
                                check(result.exitCode == 0) { result.output }
                                graph.applyWorkspace(result.stdout)
                                "saved"
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.sync_workspace), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }

        // Agent Skills
        item { SectionTitle(stringResource(R.string.skills), icon = Icons.Outlined.Extension) }
        item {
            SettingsCard {
                if (skills.length() == 0) {
                    Text(stringResource(R.string.no_skills), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                } else {
                    for (index in 0 until skills.length()) {
                        val skill = skills.getJSONObject(index)
                        val name = skill.optString("name")
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { inspectSkill = name to skill.optString("content") }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("/$name", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleSmall, color = cs.primary)
                                Text(skill.optString("description"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { removeSkill = name }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete), tint = cs.error) }
                        }
                    }
                }
                OutlinedButton(enabled = !busy, onClick = { skillDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.install_skill))
                }
            }
        }

        // About, License & Privacy (F-Droid compliant!)
        item { SectionTitle(stringResource(R.string.about), icon = Icons.Outlined.Info) }
        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TfmStatusBadge("GPL-3.0", LocalTfmColors.current.success)
                }
                Text(stringResource(R.string.about_foss_summary), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { privacyDialog = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.privacy_info)) }
                    OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/its-zain-dev/TermuxFileManger"))) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.source_code))
                    }
                }
            }
        }
    }

    if (busy) AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.loading)) }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }, confirmButton = {})
    message?.takeIf { it.isNotBlank() }?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(errorText(text).let { if (it == "saved") stringResource(R.string.saved) else it }) },
            confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.close)) } }
        )
    }

    // Searchable Model Picker Dialog
    modelChoices?.let { choices ->
        var filter by remember { mutableStateOf("") }
        val filtered = remember(choices, filter) {
            choices.filter { filter.isBlank() || it.contains(filter, true) || (graph.ai.modelNames[it]?.contains(filter, true) == true) }
        }
        AlertDialog(
            onDismissRequest = { modelChoices = null },
            title = { Text(stringResource(R.string.model)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(filter, { filter = it }, placeholder = { Text(stringResource(R.string.search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(filtered) { choice ->
                            TextButton(onClick = { model = choice; modelChoices = null }, modifier = Modifier.fillMaxWidth()) {
                                Text(graph.ai.modelNames[choice] ?: choice, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { modelChoices = null }) { Text(stringResource(R.string.close)) } }
        )
    }

    inspectSkill?.let { (name, content) ->
        AlertDialog(
            onDismissRequest = { inspectSkill = null },
            title = { Text("/$name", fontFamily = FontFamily.Monospace) },
            text = { Box(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) { TfmMarkdownView(content) } },
            confirmButton = { TextButton(onClick = { inspectSkill = null }) { Text(stringResource(R.string.close)) } }
        )
    }

    if (privacyDialog) {
        AlertDialog(
            onDismissRequest = { privacyDialog = false },
            title = { Text(stringResource(R.string.privacy_info)) },
            text = { Text(stringResource(R.string.privacy_summary_body)) },
            confirmButton = { TextButton(onClick = { privacyDialog = false }) { Text(stringResource(R.string.close)) } }
        )
    }

    if (removeKey) ConfirmDialog(stringResource(R.string.remove_key), stringResource(R.string.remove_key_confirm), { removeKey = false }, destructive = true) {
        removeKey = false; task { AiSecureStore.saveKey(context, provider.id, ""); "saved" }
    }
    if (autoConfirm) ConfirmDialog(stringResource(R.string.auto_execute), stringResource(R.string.auto_execute_body), { autoConfirm = false }) {
        autoConfirm = false; task { graph.settings.set("agent_auto_execute", !preferences.getBoolean("agent_auto_execute", false)); AgentWorkspace.persistSettings(context); "saved" }
    }
    if (skillDialog) AlertDialog(
        onDismissRequest = { skillDialog = false },
        title = { Text(stringResource(R.string.install_skill)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(skillUrl, { skillUrl = it }, label = { Text(stringResource(R.string.skill_url)) }, singleLine = true)
                OutlinedTextField(skillName, { skillName = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
            }
        },
        confirmButton = {
            Button(enabled = skillUrl.startsWith("https://"), onClick = {
                skillDialog = false
                task { AgentSkillManager.installFromUrl(context, skillUrl, skillName); graph.settings.refreshWorkspace(); "saved" }
            }) { Text(stringResource(R.string.install_skill)) }
        },
        dismissButton = { TextButton(onClick = { skillDialog = false }) { Text(stringResource(R.string.cancel)) } }
    )
    removeSkill?.let { name ->
        ConfirmDialog(stringResource(R.string.delete), name, { removeSkill = null }, destructive = true) {
            removeSkill = null; task { AgentSkillManager.delete(context, name); graph.settings.refreshWorkspace(); "saved" }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun NumberField(value: String, change: (String) -> Unit, label: Int) {
    OutlinedTextField(value, change, label = { Text(stringResource(label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun SettingSwitch(label: String, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(enabled, change)
    }
}

@Composable
private fun OpenCodeSettingsCard(graph: AppGraph, openOpenCode: () -> Unit) {
    val status by graph.openCode.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var reviewAction by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { if (!status.checked) runCatching { graph.openCode.refresh() } }
    fun run(task: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            try { message = task() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message = error.message }
            finally { busy = false }
        }
    }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.opencode), style = MaterialTheme.typography.titleSmall)
            Text(
                when {
                    !status.supported -> errorText(status.supportReason)
                    !status.installed -> stringResource(R.string.opencode_not_installed)
                    !status.compatible -> stringResource(R.string.opencode_unsupported_version)
                    else -> status.version.ifBlank { stringResource(R.string.opencode_installed) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!status.installed) {
                    OutlinedButton(enabled = !busy && status.supported, onClick = { reviewAction = "install" }) {
                        Text(stringResource(R.string.opencode_install))
                    }
                }
                if (status.installed) {
                    OutlinedButton(enabled = !busy, onClick = { reviewAction = "upgrade" }) { Text(stringResource(R.string.opencode_update)) }
                    OutlinedButton(onClick = openOpenCode) { Text(stringResource(R.string.opencode_open_ui)) }
                }
            }
            if (status.installed) {
                TextButton(onClick = {
                    val url = status.url.ifBlank { graph.openCode.storedUrl() }
                    if (url.isNotBlank()) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }) { Text(stringResource(R.string.opencode_open_browser)) }
            }
        }
    }
    reviewAction?.let { action ->
        val script = if (action == "upgrade") graph.openCode.upgradeCommandPreview() else graph.openCode.installCommandPreview()
        AlertDialog(
            onDismissRequest = { reviewAction = null },
            title = { Text(stringResource(if (action == "upgrade") R.string.opencode_update else R.string.opencode_install)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.opencode_review_body, OpenCodeManager.REPO_URL), style = MaterialTheme.typography.bodySmall)
                    TfmCodeBlock(script, language = "bash", copy = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val chosen = action
                    reviewAction = null
                    run { if (chosen == "upgrade") graph.openCode.upgrade().message else graph.openCode.install().message }
                }) { Text(stringResource(R.string.approve)) }
            },
            dismissButton = { TextButton(onClick = { reviewAction = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(errorText(text)) },
            confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.close)) } }
        )
    }
}
