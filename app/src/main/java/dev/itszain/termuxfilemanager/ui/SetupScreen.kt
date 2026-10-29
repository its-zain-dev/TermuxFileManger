package dev.itszain.termuxfilemanager.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.itszain.termuxfilemanager.AppGraph
import dev.itszain.termuxfilemanager.R
import kotlinx.coroutines.launch

const val SETUP_COMMAND = "mkdir -p ~/.termux; touch ~/.termux/termux.properties; if grep -q '^allow-external-apps=' ~/.termux/termux.properties; then sed -i 's/^allow-external-apps=.*/allow-external-apps=true/' ~/.termux/termux.properties; else printf '\\nallow-external-apps=true\\n' >> ~/.termux/termux.properties; fi; pkg install -y python curl"
@Composable fun SetupScreen(graph: AppGraph, connected: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(!granted) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Outlined.Link, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.connect_termux), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.setup_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SetupStep(R.string.install_termux, R.string.install_termux_body) {
            OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://termux.dev/"))) }) { Text(stringResource(R.string.learn_more)) }
        }
        SetupStep(R.string.allow_commands, R.string.allow_commands_body) {
            OutlinedButton(onClick = { permission.launch("com.termux.permission.RUN_COMMAND") }) { Text(stringResource(R.string.grant_permission)) }
        }
        SetupStep(R.string.prepare_termux, R.string.prepare_termux_body) {
            Button(onClick = { clipboard.setText(AnnotatedString(SETUP_COMMAND)); context.packageManager.getLaunchIntentForPackage("com.termux")?.let { context.startActivity(it) } }) { Text(stringResource(R.string.copy_setup)) }
        }
        error?.let { ErrorNotice(it) }
        version?.let { Text(stringResource(R.string.connection_ok, it), color = MaterialTheme.colorScheme.primary) }
        if(checking) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(enabled = !checking, onClick = { scope.launch { checking = true; error = null; try { version = graph.files.probe(); connected() } catch(failure: Exception) { error = failure.message } finally { checking = false } } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.check_connection)) }
    }
}
@Composable private fun SetupStep(title: Int, body: Int, action: @Composable () -> Unit) {
    ElevatedCard { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium); Text(stringResource(body), style = MaterialTheme.typography.bodyMedium); action()
    } }
}
