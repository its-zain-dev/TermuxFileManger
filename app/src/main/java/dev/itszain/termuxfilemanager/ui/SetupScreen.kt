package dev.itszain.termuxfilemanager.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.itszain.termuxfilemanager.AppGraph
import dev.itszain.termuxfilemanager.R
import dev.itszain.termuxfilemanager.TermuxCommandRunner
import kotlinx.coroutines.launch

const val SETUP_COMMAND = "mkdir -p ~/.termux; touch ~/.termux/termux.properties; if grep -q '^allow-external-apps=' ~/.termux/termux.properties; then sed -i 's/^allow-external-apps=.*/allow-external-apps=true/' ~/.termux/termux.properties; else printf '\\nallow-external-apps=true\\n' >> ~/.termux/termux.properties; fi; pkg install -y python curl"
const val STORAGE_COMMAND = "termux-setup-storage"

@Composable
fun SetupScreen(graph: AppGraph, connected: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val tfm = LocalTfmColors.current
    val cs = MaterialTheme.colorScheme

    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableStateOf<String?>(null) }
    var permGranted by remember { mutableStateOf(TermuxCommandRunner.hasPermission(context)) }
    val termuxInstalled = remember {
        runCatching { context.packageManager.getPackageInfo("com.termux", 0) != null }.getOrDefault(false)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permGranted = granted || TermuxCommandRunner.hasPermission(context)
        if (!granted) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(20.dp), color = cs.primaryContainer) {
                Icon(Icons.Outlined.Link, null, Modifier.padding(14.dp).size(30.dp), tint = cs.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.connect_termux), style = MaterialTheme.typography.headlineSmall)
                TfmStatusBadge(
                    stringResource(if (version != null) R.string.connected else if (permGranted) R.string.pending else R.string.not_connected),
                    if (version != null) tfm.success else if (permGranted) tfm.warning else cs.error
                )
            }
        }
        Text(stringResource(R.string.setup_intro), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)

        SetupStep(R.string.install_termux, R.string.install_termux_body, done = termuxInstalled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.termux/"))) }) {
                    Text("F-Droid")
                }
                OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://termux.dev/"))) }) {
                    Text(stringResource(R.string.learn_more))
                }
            }
        }

        SetupStep(R.string.allow_commands, R.string.allow_commands_body, done = permGranted) {
            OutlinedButton(onClick = { permission.launch("com.termux.permission.RUN_COMMAND") }) {
                Text(stringResource(R.string.grant_permission))
            }
        }

        SetupStep(R.string.prepare_termux, R.string.prepare_termux_body, done = version != null) {
            TfmCodeBlock(SETUP_COMMAND, language = "bash", copy = true)
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(SETUP_COMMAND))
                    context.packageManager.getLaunchIntentForPackage("com.termux")?.let { context.startActivity(it) }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.copy_setup))
            }
        }

        SetupStep(R.string.setup_storage_title, R.string.setup_storage_body, done = false) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(STORAGE_COMMAND))
                    context.packageManager.getLaunchIntentForPackage("com.termux")?.let { context.startActivity(it) }
                }) {
                    Text(stringResource(R.string.copy_storage_setup))
                }
            }
        }

        error?.let { ErrorNotice(it) }
        version?.let {
            Surface(color = tfm.successContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = tfm.success)
                    Text(stringResource(R.string.connection_ok, it), color = tfm.success, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        if (checking) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(
            enabled = !checking,
            onClick = {
                scope.launch {
                    checking = true; error = null
                    permGranted = TermuxCommandRunner.hasPermission(context)
                    try {
                        version = graph.files.probe()
                        connected()
                    } catch (failure: Exception) {
                        error = failure.message
                    } finally {
                        checking = false
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.check_connection)) }
    }
}

@Composable
private fun SetupStep(title: Int, body: Int, done: Boolean, action: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tfm = LocalTfmColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cs.surfaceContainer,
        border = BorderStroke(1.dp, if (done) tfm.success.copy(alpha = 0.45f) else cs.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    null,
                    tint = if (done) tfm.success else cs.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            action()
        }
    }
}
