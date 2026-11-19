package dev.itszain.termuxfilemanager.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.itszain.termuxfilemanager.R
import dev.itszain.termuxfilemanager.data.CommandRecord
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun errorText(code: String): String {
    val id = when (code) {
        "not_found" -> R.string.file_not_found
        "permission" -> R.string.file_permission
        "exists" -> R.string.file_exists
        "changed" -> R.string.file_changed
        "binary" -> R.string.file_binary
        "encoding" -> R.string.file_encoding
        "too_large" -> R.string.file_too_large
        "recursive" -> R.string.file_recursive
        "invalid_name" -> R.string.file_invalid_name
        "no_space" -> R.string.file_no_space
        "read_only" -> R.string.file_read_only
        "protected" -> R.string.file_protected
        "invalid_mode" -> R.string.file_invalid_mode
        "not_directory" -> R.string.file_not_directory
        "unsafe_archive" -> R.string.file_unsafe_archive
        "unsupported_archive" -> R.string.file_unsupported_archive
        "unsupported_media" -> R.string.file_unsupported_media
        "invalid_regex" -> R.string.invalid_regex
        "patch_not_found" -> R.string.patch_not_found
        "patch_ambiguous" -> R.string.patch_ambiguous
        "invalid_encrypted_key" -> R.string.invalid_encrypted_key
        "web_search_no_results" -> R.string.web_search_no_results
        "command_pending" -> R.string.command_pending
        "termux_permission" -> R.string.termux_permission
        "bridge_unavailable" -> R.string.bridge_unavailable
        "missing_key" -> R.string.missing_key
        "missing_model" -> R.string.missing_model
        "rate_limit" -> R.string.rate_limit
        "https_required" -> R.string.https_required
        "empty_response" -> R.string.empty_response
        "agent_stopped" -> R.string.agent_stopped
        "agent_no_progress" -> R.string.agent_no_progress
        "agent_step_limit" -> R.string.agent_step_limit
        "tool_disabled" -> R.string.tool_disabled
        "manual_approval_required" -> R.string.manual_approval_required
        "action_declined" -> R.string.action_declined
        "unknown_skill" -> R.string.unknown_skill
        "interrupted" -> R.string.interrupted
        "complete" -> R.string.complete
        "skipped" -> R.string.skipped
        "pending" -> R.string.pending
        "failed" -> R.string.failed
        "chatgpt_title" -> R.string.chatgpt_title
        "chatgpt_continue" -> R.string.chatgpt_continue
        "chatgpt_account" -> R.string.chatgpt_account
        "chatgpt_add" -> R.string.chatgpt_add
        "chatgpt_sign_out" -> R.string.chatgpt_sign_out
        "chatgpt_usage" -> R.string.chatgpt_usage
        "chatgpt_info" -> R.string.chatgpt_info
        "chatgpt_sign_in_required" -> R.string.chatgpt_sign_in_required
        "chatgpt_login_timeout" -> R.string.chatgpt_login_timeout
        "chatgpt_login_declined" -> R.string.chatgpt_login_declined
        "chatgpt_invalid_identity" -> R.string.chatgpt_invalid_identity
        "chatgpt_registration_incomplete" -> R.string.chatgpt_registration_incomplete
        "chatgpt_plan_not_granted" -> R.string.chatgpt_plan_not_granted
        "chatgpt_reauthorize" -> R.string.chatgpt_reauthorize
        "chatgpt_login_failed" -> R.string.chatgpt_login_failed
        "chatgpt_revocation_unconfirmed" -> R.string.chatgpt_revocation_unconfirmed
        "chatgpt_incomplete" -> R.string.chatgpt_incomplete
        "subscription_sharing_usage_limit_exceeded" -> R.string.subscription_sharing_usage_limit_exceeded
        "subscription_sharing_usage_unavailable" -> R.string.subscription_sharing_usage_unavailable
        "opencode_not_installed" -> R.string.opencode_not_installed
        "opencode_unsupported_android" -> R.string.opencode_unsupported_android
        "opencode_unsupported_cpu" -> R.string.opencode_unsupported_cpu
        "opencode_unsupported_version" -> R.string.opencode_unsupported_version
        "opencode_already_installed" -> R.string.opencode_already_installed
        "opencode_installed" -> R.string.opencode_installed_message
        "opencode_updated" -> R.string.opencode_updated
        "opencode_install_failed" -> R.string.opencode_install_failed
        "opencode_start_failed" -> R.string.opencode_start_failed
        "opencode_no_password" -> R.string.opencode_no_password
        "opencode_pair_failed" -> R.string.opencode_pair_failed
        "opencode_auth_failed" -> R.string.opencode_auth_failed
        "opencode_session_failed" -> R.string.opencode_session_failed
        "opencode_stream_unavailable" -> R.string.opencode_stream_unavailable
        "opencode_run_failed" -> R.string.opencode_run_failed
        "opencode_run_interrupted" -> R.string.opencode_run_interrupted
        "opencode_permission_rejected" -> R.string.opencode_permission_rejected
        "opencode_no_text" -> R.string.opencode_no_text
        else -> 0
    }
    return if (id == 0) {
        if (code.contains("Timed out")) stringResource(R.string.command_pending) else code
    } else stringResource(id)
}

@Composable
fun ErrorNotice(message: String, retry: (() -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 2.dp).size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(errorText(message), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                if (retry != null) {
                    TextButton(onClick = retry, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(stringResource(R.string.retry), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, icon: ImageVector? = null, trailing: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        if (!trailing.isNullOrBlank()) {
            Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TfmStatusBadge(label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun EmptyPane(
    title: String,
    body: String,
    icon: ImageVector = Icons.Outlined.FolderOpen,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        ) {
            Icon(icon, null, Modifier.padding(20.dp).size(34.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.let { Spacer(Modifier.height(4.dp)); it() }
    }
}

@Composable
fun TfmSkeletonList(rows: Int = 7) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "pulse"
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        repeat(rows) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = alpha),
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth(0.55f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                        Box(Modifier.fillMaxWidth(0.35f).height(9.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                    }
                }
            }
        }
    }
}

@Composable
fun TextEntryDialog(
    title: String,
    initial: String = "",
    label: String = stringResource(R.string.name),
    dismiss: () -> Unit,
    confirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value, { value = it },
                label = { Text(label) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(enabled = value.isNotBlank(), onClick = { confirm(value.trim()) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    dismiss: () -> Unit,
    confirmLabel: String = stringResource(R.string.approve),
    destructive: Boolean = false,
    confirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = confirm,
                colors = if (destructive) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) else ButtonDefaults.buttonColors()
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun <T> ChoiceMenu(
    value: String,
    choices: List<T>,
    label: (T) -> String,
    select: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(value, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded, { expanded = false }) {
            choices.forEach { item ->
                DropdownMenuItem(
                    text = { Text(label(item)) },
                    onClick = { expanded = false; select(item) }
                )
            }
        }
    }
}

@Composable
fun TfmCodeBlock(
    text: String,
    modifier: Modifier = Modifier,
    language: String = "plain",
    copy: Boolean = false,
    ansi: Boolean = false,
    onRun: (() -> Unit)? = null
) {
    val clipboard = LocalClipboardManager.current
    val tfm = LocalTfmColors.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    val showHeader = copy || onRun != null || (language.isNotBlank() && language != "plain")
    val detected = remember(language) { SyntaxHighlighter.detectLanguage(language) }
    val styled = remember(text, detected, ansi, tfm) {
        when {
            ansi -> AnsiParser.parse(text, tfm.isDark)
            detected != "plain" -> SyntaxHighlighter.highlight(text, detected, tfm)
            else -> AnnotatedString(text)
        }
    }

    Surface(
        color = tfm.codeBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, tfm.codeBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            if (showHeader) {
                Row(
                    Modifier.fillMaxWidth().background(tfm.codeHeaderBg).padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (language.isNotBlank() && language != "plain") {
                        Text(
                            language.lowercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (onRun != null) {
                        TextButton(onClick = onRun, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Icon(Icons.Outlined.PlayArrow, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.run), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (copy) {
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(if (ansi) AnsiParser.strip(text) else text))
                                copied = true
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                                stringResource(if (copied) R.string.copied else R.string.copy),
                                tint = if (copied) tfm.success else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                SelectionContainer {
                    Text(
                        text = styled,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                        style = TextStyle(textDirection = TextDirection.Ltr)
                    )
                }
            }
        }
    }
}

@Composable
fun CodeText(text: String, modifier: Modifier = Modifier, copy: Boolean = false, ansi: Boolean = false) {
    TfmCodeBlock(text = text, modifier = modifier, copy = copy, ansi = ansi)
}

@Composable
fun TfmDiffView(diff: String, modifier: Modifier = Modifier) {
    val tfm = LocalTfmColors.current
    Surface(
        color = tfm.codeBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, tfm.codeBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                diff.lines().forEach { line ->
                    val bg = when {
                        line.startsWith("+") && !line.startsWith("+++") -> tfm.diffAddBg
                        line.startsWith("-") && !line.startsWith("---") -> tfm.diffDelBg
                        else -> Color.Transparent
                    }
                    val fg = when {
                        line.startsWith("+") && !line.startsWith("+++") -> tfm.diffAddText
                        line.startsWith("-") && !line.startsWith("---") -> tfm.diffDelText
                        line.startsWith("@@") -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = fg,
                        modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 12.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TfmPermissionMatrix(
    initialOctal: String,
    onOctalChange: (String) -> Unit
) {
    val initialBits = remember(initialOctal) { initialOctal.takeLast(3).toIntOrNull(8) ?: 0b110100100 }
    var bits by remember(initialOctal) { mutableIntStateOf(initialBits) }
    val octal = remember(bits) { String.format("%03o", bits and 0b111111111) }
    val symbolic = remember(bits) {
        buildString {
            for (shift in listOf(6, 3, 0)) {
                val n = (bits shr shift) and 7
                append(if (n and 4 != 0) 'r' else '-')
                append(if (n and 2 != 0) 'w' else '-')
                append(if (n and 1 != 0) 'x' else '-')
            }
        }
    }
    LaunchedEffect(octal) { onOctalChange(octal) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(symbolic, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text("0$octal", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        val rows = listOf(
            stringResource(R.string.perm_owner) to 6,
            stringResource(R.string.perm_group) to 3,
            stringResource(R.string.perm_other) to 0
        )
        rows.forEach { (label, shift) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                listOf("R" to 4, "W" to 2, "X" to 1).forEach { (flag, mask) ->
                    val bit = mask shl shift
                    val checked = (bits and bit) != 0
                    FilterChip(
                        selected = checked,
                        onClick = { bits = if (checked) bits and bit.inv() else bits or bit },
                        label = { Text(flag, fontFamily = FontFamily.Monospace) },
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CommandCard(
    command: CommandRecord,
    onRerun: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onAskAi: (() -> Unit)? = null
) {
    var expanded by remember(command.id) { mutableStateOf(command.state == "pending" || (command.exitCode != null && command.exitCode != 0)) }
    val tfm = LocalTfmColors.current
    val cs = MaterialTheme.colorScheme
    val statusColor = when {
        command.state == "pending" -> tfm.info
        command.exitCode == 0 -> tfm.success
        else -> cs.error
    }
    val duration = command.completed?.let { end ->
        val ms = (end - command.created).coerceAtLeast(0L)
        if (ms < 1000) "${ms}ms" else String.format("%.1fs", ms / 1000.0)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cs.surfaceContainer,
        border = BorderStroke(1.dp, cs.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(shape = RoundedCornerShape(10.dp), color = statusColor.copy(alpha = 0.14f)) {
                    Icon(
                        if (command.state == "pending") Icons.Outlined.Schedule
                        else if (command.exitCode == 0) Icons.Outlined.CheckCircle
                        else Icons.Outlined.ErrorOutline,
                        null,
                        tint = statusColor,
                        modifier = Modifier.padding(8.dp).size(18.dp)
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        command.command.lineSequence().firstOrNull()?.take(80) ?: command.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(errorText(command.state), style = MaterialTheme.typography.labelSmall, color = statusColor)
                        command.exitCode?.let { Text("· " + stringResource(R.string.exit_code, it), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) }
                        duration?.let { Text("· $it", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) }
                    }
                }
                if (command.state == "pending") CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = cs.onSurfaceVariant)
            }
            if (expanded) {
                HorizontalDivider(color = cs.outlineVariant)
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TfmCodeBlock(command.command, language = "bash", copy = true)
                    Text(
                        "${command.workdir} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(command.created))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                    if (command.stdout.isNotEmpty()) TfmCodeBlock(command.stdout, copy = true, ansi = true)
                    if (command.stderr.isNotEmpty()) ErrorNotice(AnsiParser.strip(command.stderr))
                    if (onRerun != null || onEdit != null || onAskAi != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            onRerun?.let {
                                FilledTonalButton(onClick = it, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                                    Icon(Icons.Outlined.Replay, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.rerun), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            onEdit?.let {
                                OutlinedButton(onClick = it, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                                    Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.edit), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            onAskAi?.let {
                                TextButton(onClick = it, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.ask_ai), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1_048_576 -> "%.1f KiB".format(bytes / 1024.0)
    bytes < 1_073_741_824 -> "%.1f MiB".format(bytes / 1_048_576.0)
    else -> "%.2f GiB".format(bytes / 1_073_741_824.0)
}
