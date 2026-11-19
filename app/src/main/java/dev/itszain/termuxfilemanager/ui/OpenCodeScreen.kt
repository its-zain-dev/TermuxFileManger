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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.itszain.termuxfilemanager.AppGraph
import dev.itszain.termuxfilemanager.R
import dev.itszain.termuxfilemanager.data.OpenCodeManager
import dev.itszain.termuxfilemanager.data.OpenCodeStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Full-screen OpenCode experience with an unobtrusive collapsible control pill so users can
 * always return to TFM, reload, reconnect or open in browser, plus transparent F-Droid-friendly
 * install/upgrade script review before adding any third-party Termux repository.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OpenCodeScreen(graph: AppGraph, onExitFullscreen: () -> Unit = {}) {
    val status by graph.openCode.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val bgArgb = MaterialTheme.colorScheme.background.toArgb()

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var pageReady by remember { mutableStateOf(false) }
    var pillExpanded by remember { mutableStateOf(false) }
    var reviewAction by remember { mutableStateOf<String?>(null) }
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

    val web = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(bgArgb)
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
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    pendingFile?.onReceiveValue(null)
                    pendingFile = filePathCallback
                    return try {
                        val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                        filePicker.launch(intent)
                        true
                    } catch (_: Exception) {
                        pendingFile = null
                        false
                    }
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = external(request?.url?.toString())
                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = external(url)
                override fun onPageFinished(view: WebView?, url: String?) {
                    canGoBack = view?.canGoBack() == true
                    pageReady = true
                    CookieManager.getInstance().flush()
                }
                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    canGoBack = view?.canGoBack() == true
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        pageReady = true
                        message = "opencode_start_failed"
                    }
                }
            }
        }
    }

    LaunchedEffect(bgArgb) { web.setBackgroundColor(bgArgb) }

    DisposableEffect(web) {
        onDispose {
            runCatching { web.stopLoading(); (web.parent as? ViewGroup)?.removeView(web); web.destroy() }
        }
    }

    fun load(url: String) {
        pageReady = false
        web.loadUrl(url)
    }

    fun connect(forcePair: Boolean = false) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                var target: String? = null
                var failure: Exception? = null
                for (attempt in 0..1) {
                    try {
                        val connection = graph.openCode.connection(force = attempt == 1)
                        target = if (forcePair || !graph.openCode.pairedRecently()) graph.openCode.pairUrl() else connection.url
                        break
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        failure = error
                    }
                }
                val url = target ?: throw (failure ?: IllegalStateException("opencode_start_failed"))
                load(url)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message = error.message ?: "opencode_start_failed"
                pageReady = true
            } finally {
                busy = false
            }
        }
    }

    fun executeInstall() {
        if (busy) return
        busy = true
        scope.launch {
            try { message = graph.openCode.install().message }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message = error.message ?: "opencode_install_failed" }
            finally { busy = false }
        }
    }

    fun executeUpgrade() {
        if (busy) return
        busy = true
        scope.launch {
            try { message = graph.openCode.upgrade().message }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message = error.message ?: "opencode_install_failed" }
            finally { busy = false }
        }
    }

    LaunchedEffect(Unit) {
        if (!status.checked) {
            try { graph.openCode.refresh() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message = error.message }
        }
    }
    LaunchedEffect(status.installed, status.compatible) {
        if (status.installed && status.compatible && !loaded) {
            loaded = true
            connect()
        }
    }

    BackHandler(enabled = canGoBack) { web.goBack() }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        when {
            !status.supported -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                TextButton(onClick = onExitFullscreen) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.files)) }
                EmptyPane(stringResource(R.string.opencode), supportText(status.supportReason), Icons.Outlined.Extension)
            }
            !status.checked -> LoadingPane(stringResource(R.string.opencode_checking))
            !status.installed || !status.compatible -> OpenCodeSetup(
                status = status,
                busy = busy,
                onBack = onExitFullscreen,
                install = { reviewAction = "install" },
                upgrade = { reviewAction = "upgrade" },
                recheck = { scope.launch { runCatching { graph.openCode.refresh(force = true) } } }
            )
            else -> {
                AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
                if (!pageReady) LoadingOverlay()
                // Unobtrusive collapsible navigation & service control pill
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    tonalElevation = 4.dp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                        if (pillExpanded) {
                            IconButton(onClick = { pillExpanded = false; onExitFullscreen() }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.files), Modifier.size(18.dp))
                            }
                            IconButton(onClick = { pillExpanded = false; web.reload() }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Outlined.Refresh, stringResource(R.string.refresh), Modifier.size(18.dp))
                            }
                            IconButton(onClick = { pillExpanded = false; connect(forcePair = true) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Outlined.Sync, stringResource(R.string.opencode_reconnect), Modifier.size(18.dp))
                            }
                            IconButton(onClick = {
                                pillExpanded = false
                                val u = status.url.ifBlank { graph.openCode.storedUrl() }
                                if (u.isNotBlank()) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
                            }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Outlined.OpenInBrowser, stringResource(R.string.opencode_open_browser), Modifier.size(18.dp))
                            }
                        }
                        IconButton(onClick = { pillExpanded = !pillExpanded }, modifier = Modifier.size(34.dp)) {
                            Icon(if (pillExpanded) Icons.Outlined.Close else Icons.Outlined.Apps, stringResource(R.string.more), Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    // Transparent F-Droid-compliant review dialog showing the exact third-party apt command
    reviewAction?.let { action ->
        val script = if (action == "upgrade") graph.openCode.upgradeCommandPreview() else graph.openCode.installCommandPreview()
        AlertDialog(
            onDismissRequest = { reviewAction = null },
            title = { Text(stringResource(if (action == "upgrade") R.string.opencode_update else R.string.opencode_install)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.opencode_review_body, OpenCodeManager.REPO_URL), style = MaterialTheme.typography.bodySmall)
                    TfmCodeBlock(script, language = "bash", copy = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val chosen = action
                    reviewAction = null
                    if (chosen == "upgrade") executeUpgrade() else executeInstall()
                }) { Text(stringResource(R.string.approve)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(script)); reviewAction = null }) { Text(stringResource(R.string.copy)) }
                    TextButton(onClick = { reviewAction = null }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text(stringResource(R.string.opencode)) },
            text = { Text(errorText(text)) },
            confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.close)) } }
        )
    }
}

@Composable
private fun LoadingPane(label: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Spacer(Modifier.height(14.dp))
        Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadingOverlay() {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.opencode_loading), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun supportText(reason: String): String = when (reason) {
    "opencode_unsupported_android" -> stringResource(R.string.opencode_unsupported_android)
    "opencode_unsupported_cpu" -> stringResource(R.string.opencode_unsupported_cpu)
    else -> stringResource(R.string.opencode_unsupported_android)
}

@Composable
private fun OpenCodeSetup(
    status: OpenCodeStatus,
    busy: Boolean,
    onBack: () -> Unit,
    install: () -> Unit,
    upgrade: () -> Unit,
    recheck: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.files))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = recheck, enabled = !busy) { Icon(Icons.Outlined.Refresh, stringResource(R.string.refresh)) }
        }
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(Icons.Outlined.Extension, null, Modifier.padding(18.dp).size(34.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(stringResource(R.string.opencode_setup), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(stringResource(R.string.opencode_install_desc), textAlign = TextAlign.Center)
        Text(stringResource(R.string.opencode_install_requirement), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (status.installed && !status.compatible) {
            Text(stringResource(R.string.opencode_unsupported_version), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        if (status.installed) {
            Button(enabled = !busy, onClick = upgrade, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text(stringResource(R.string.opencode_update))
            }
        } else {
            Button(enabled = !busy, onClick = install, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.Download, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.opencode_install))
            }
        }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.opencode_installing), textAlign = TextAlign.Center)
        }
        Text(stringResource(R.string.opencode_install_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
