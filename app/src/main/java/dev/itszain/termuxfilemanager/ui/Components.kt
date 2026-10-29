package dev.itszain.termuxfilemanager.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.itszain.termuxfilemanager.R
import dev.itszain.termuxfilemanager.data.CommandRecord
import java.text.DateFormat
import java.util.Date

@Composable fun errorText(code: String): String {
    val id = when(code) {
        "not_found" -> R.string.file_not_found; "permission" -> R.string.file_permission
        "exists" -> R.string.file_exists; "changed" -> R.string.file_changed; "binary" -> R.string.file_binary
        "encoding" -> R.string.file_encoding; "too_large" -> R.string.file_too_large
        "recursive" -> R.string.file_recursive; "invalid_name" -> R.string.file_invalid_name
        "no_space" -> R.string.file_no_space; "read_only" -> R.string.file_read_only
        "protected" -> R.string.file_protected; "invalid_mode" -> R.string.file_invalid_mode
        "not_directory" -> R.string.file_not_directory; "command_pending" -> R.string.command_pending
        "termux_permission" -> R.string.termux_permission; "bridge_unavailable" -> R.string.bridge_unavailable
        "missing_key" -> R.string.missing_key; "missing_model" -> R.string.missing_model; "rate_limit" -> R.string.rate_limit
        "https_required" -> R.string.https_required; "empty_response" -> R.string.empty_response
        "agent_stopped" -> R.string.agent_stopped; "agent_no_progress" -> R.string.agent_no_progress
        "agent_step_limit" -> R.string.agent_step_limit; "tool_disabled" -> R.string.tool_disabled
        "manual_approval_required" -> R.string.manual_approval_required; "action_declined" -> R.string.action_declined
        "unknown_skill" -> R.string.unknown_skill; "interrupted" -> R.string.interrupted
        "complete" -> R.string.complete; "skipped" -> R.string.skipped; "pending" -> R.string.pending; "failed" -> R.string.failed
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
        else -> 0
    }
    return if (id == 0) { if (code.contains("Timed out")) stringResource(R.string.command_pending) else code } else stringResource(id)
}
@Composable fun ErrorNotice(message: String, retry: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(errorText(message), color = MaterialTheme.colorScheme.onErrorContainer)
            if (retry != null) TextButton(onClick = retry) { Text(stringResource(R.string.retry)) }
        }
    }
}
@Composable fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(top = 20.dp, bottom = 12.dp))
}
@Composable fun EmptyPane(title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.FolderOpen, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(icon, null, Modifier.padding(22.dp).size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action?.invoke()
    }
}
@Composable fun TextEntryDialog(title: String, initial: String = "", label: String = stringResource(R.string.name), dismiss: () -> Unit, confirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }, confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { confirm(value.trim()) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}
@Composable fun ConfirmDialog(title: String, message: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = confirm) { Text(stringResource(R.string.approve)) } }, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}
@Composable fun <T> ChoiceMenu(value: String, choices: List<T>, label: (T) -> String, select: (T) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(value, Modifier.weight(1f), maxLines = 1); Icon(Icons.Outlined.ExpandMore, null) }
        DropdownMenu(expanded, { expanded = false }) { choices.forEach { item -> DropdownMenuItem(text = { Text(label(item)) }, onClick = { expanded = false; select(item) }) } }
    }
}
@Composable fun CodeText(text: String, modifier: Modifier = Modifier, copy: Boolean = false) {
    val clipboard = LocalClipboardManager.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth()) {
        Column {
            if (copy) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy)) }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                SelectionContainer { Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 19.sp,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(14.dp), style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr)) }
            }
        }
    }
}
@Composable fun CommandCard(command: CommandRecord) {
    var expanded by remember(command.id) { mutableStateOf(command.state == "pending") }
    ElevatedCard(Modifier.fillMaxWidth()) {
        ListItem(headlineContent = { Text(command.label, maxLines = 1) }, supportingContent = { Text(errorText(command.state)) },
            leadingContent = { Icon(Icons.Outlined.Terminal, null) }, trailingContent = { Icon(if(expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) },
            modifier = Modifier.clickable { expanded = !expanded }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer))
        if (expanded) Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CodeText(command.command)
            Text(command.workdir, style = MaterialTheme.typography.labelSmall)
            if (command.stdout.isNotEmpty()) CodeText(command.stdout, copy = true)
            if (command.stderr.isNotEmpty()) ErrorNotice(command.stderr)
            command.exitCode?.let { Text(stringResource(R.string.exit_code, it), style = MaterialTheme.typography.labelSmall) }
            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(command.created)), style = MaterialTheme.typography.labelSmall)
        }
    }
}
fun humanSize(bytes: Long): String = when { bytes < 1024 -> "$bytes B"; bytes < 1048576 -> "%.1f KiB".format(bytes / 1024.0); bytes < 1073741824 -> "%.1f MiB".format(bytes / 1048576.0); else -> "%.1f GiB".format(bytes / 1073741824.0) }
