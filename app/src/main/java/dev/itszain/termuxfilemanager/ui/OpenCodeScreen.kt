package dev.itszain.termuxfilemanager.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.itszain.termuxfilemanager.AppGraph
import dev.itszain.termuxfilemanager.R
import dev.itszain.termuxfilemanager.data.OpenCodeStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full OpenCode experience: setup and package checks, then the official server web UI
 * inside a WebView with cookies, JavaScript, file uploads and external-link handling.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable fun OpenCodeScreen(graph: AppGraph, openTerminal: (String, String) -> Unit) {
    val status by graph.openCode.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = pendingFile
        pendingFile = null
        callback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
    }
    fun external(url: String?): Boolean {
        val uri = url?.let { Uri.parse(it) } ?: return false
        val host = uri.host.orEmpty()
        if (host == "127.0.0.1" || host == "localhost" || host.isBlank()) return false
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        return true
    }
    val web = remember(status.installed) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadsImagesAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            isFocusable = true
            isFocusableInTouchMode = true
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
                    pendingFile?.onReceiveValue(null)
                    pendingFile = filePathCallback
                    return try {
                        val intent = fileChooserParams?.createIntent()
                            ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                        filePicker.launch(intent)
                        true
                    } catch (_: Exception) {
                        pendingFile = null
                        false
                    }
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                    external(request?.url?.toString())
                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = external(url)
                override fun onPageFinished(view: WebView?, url: String?) {
                    canGoBack = view?.canGoBack() == true
                    CookieManager.getInstance().flush()
                }
                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    canGoBack = view?.canGoBack() == true
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) message = "opencode_start_failed"
                }
            }
        }
    }
    DisposableEffect(web) {
        onDispose {
            runCatching { web.stopLoading(); (web.parent as? ViewGroup)?.removeView(web); web.destroy() }
        }
    }
    fun connect(forcePair: Boolean = false) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val connection = graph.openCode.connection()
                val target = if (forcePair || !graph.openCode.pairedRecently()) graph.openCode.pairUrl() else connection.url
                withContext(Dispatchers.Main) { web.loadUrl(target) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message = error.message ?: "opencode_start_failed"
            } finally {
                busy = false
            }
        }
    }
    fun install() {
        if (busy) return
        busy = true
        scope.launch {
            try { message = graph.openCode.install().message }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message = error.message ?: "opencode_install_failed" }
            finally { busy = false }
        }
    }
    fun upgrade() {
        if (busy) return
        busy = true
        scope.launch {
            try { message = graph.openCode.upgrade().message }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message = error.message ?: "opencode_install_failed" }
            finally { busy = false }
        }
    }
    fun restart() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                graph.openCode.restart()
                val connection = graph.openCode.connection(force = true)
                withContext(Dispatchers.Main) { web.loadUrl(connection.url) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message = error.message ?: "opencode_start_failed"
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(Unit) {
        if (!status.checked) runCatching { graph.openCode.refresh() }
    }
    LaunchedEffect(status.installed) {
        if (status.installed && !loaded) {
            loaded = true
            connect()
        }
    }
    BackHandler(enabled = canGoBack) { web.goBack() }
    Column(Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Extension, null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.opencode), style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                !status.supported -> stringResource(R.string.opencode_not_supported)
                                !status.installed -> stringResource(R.string.opencode_not_installed)
                                else -> status.version.ifBlank { stringResource(R.string.opencode_installed) }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status.supported) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        )
                    }
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                if (status.installed) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(onClick = { connect(true) }) { Text(stringResource(R.string.opencode_reconnect)) }
                        TextButton(enabled = !busy, onClick = { restart() }) { Text(stringResource(R.string.opencode_restart)) }
                        TextButton(enabled = !busy, onClick = { upgrade() }) { Text(stringResource(R.string.opencode_update)) }
                        TextButton(onClick = { openTerminal("~/", "opencode") }) { Text(stringResource(R.string.opencode_tui)) }
                        TextButton(onClick = {
                            val url = status.url.ifBlank { graph.openCode.storedUrl() }
                            if (url.isNotBlank()) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        }) { Text(stringResource(R.string.opencode_open_browser)) }
                    }
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            !status.supported -> EmptyPane(stringResource(R.string.opencode), supportText(status.supportReason), Icons.Outlined.Extension)
            !status.installed || !status.compatible -> OpenCodeSetup(status, busy, ::install, ::upgrade)
            else -> AndroidView(factory = { web }, modifier = Modifier.fillMaxSize().weight(1f))
        }
    }
    message?.let { text ->
        AlertDialog(onDismissRequest = { message = null }, title = { Text(stringResource(R.string.opencode)) },
            text = { Text(errorText(text)) },
            confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.close)) } })
    }
}

@Composable private fun supportText(reason: String): String = when (reason) {
    "opencode_unsupported_android" -> stringResource(R.string.opencode_unsupported_android)
    "opencode_unsupported_cpu" -> stringResource(R.string.opencode_unsupported_cpu)
    else -> stringResource(R.string.opencode_unsupported_android)
}

@Composable private fun OpenCodeSetup(status: OpenCodeStatus, busy: Boolean, install: () -> Unit, upgrade: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(Icons.Outlined.Extension, null, Modifier.padding(18.dp).size(34.dp))
        }
        Text(stringResource(R.string.opencode_setup), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(stringResource(R.string.opencode_install_desc), textAlign = TextAlign.Center)
        Text(stringResource(R.string.opencode_install_requirement), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (status.installed && !status.compatible) {
            Text(stringResource(R.string.opencode_unsupported_version), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        if (status.installed) {
            Button(enabled = !busy, onClick = upgrade, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.opencode_update))
            }
        } else {
            Button(enabled = !busy, onClick = install, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Download, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.opencode_install))
            }
        }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.opencode_installing), textAlign = TextAlign.Center)
        }
        Text(stringResource(R.string.opencode_install_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
