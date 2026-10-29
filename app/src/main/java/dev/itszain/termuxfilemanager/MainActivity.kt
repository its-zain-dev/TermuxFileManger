package dev.itszain.termuxfilemanager

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.ContextThemeWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import dev.itszain.termuxfilemanager.data.AppSettings
import dev.itszain.termuxfilemanager.features.*
import dev.itszain.termuxfilemanager.ui.*
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var request by mutableStateOf<Intent?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        request = if(savedInstanceState == null) intent else null
        setContent { TfmApp(graph(), request) { request = null } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); request = intent }
}
private inline fun <reified T : ViewModel> factory(crossinline create: () -> T) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun TfmApp(graph: AppGraph, request: Intent? = null, consumed: () -> Unit = {}) {
    val appearance by graph.settings.appearance.collectAsStateWithLifecycle(AppSettings())
    val ready by graph.ready.collectAsStateWithLifecycle()
    val startupError by graph.startupError.collectAsStateWithLifecycle()
    val originalContext = LocalContext.current
    val originalConfiguration = LocalConfiguration.current
    val localized = remember(appearance.language, originalConfiguration) {
        val config = Configuration(originalConfiguration)
        if(appearance.language != "system") { val locale = Locale.forLanguageTag(appearance.language); config.setLocale(locale); config.setLayoutDirection(locale) }
        // Preserve the Activity in the context chain for result launchers and browser intents.
        ContextThemeWrapper(originalContext, R.style.AppTheme).apply { applyOverrideConfiguration(config) }
    }
    val rtl = localized.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
    CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration,
        LocalLayoutDirection provides if(rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        TfmTheme(appearance.theme) {
            val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            SideEffect { (originalContext as? ComponentActivity)?.let { WindowCompat.getInsetsController(it.window, it.window.decorView).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark } } }
            Surface(Modifier.fillMaxSize()) {
                if(!ready) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        if(startupError != null) {
                            Text(stringResource(R.string.startup_error), style = MaterialTheme.typography.headlineSmall)
                            Text(stringResource(R.string.startup_error_body)); Spacer(Modifier.height(16.dp)); ErrorNotice(startupError!!) { graph.startupError.value = null; graph.initialize() }
                        } else CircularProgressIndicator()
                    }
                } else {
                    val files: FilesModel = viewModel(factory = factory { FilesModel(graph) })
                    val workspace: WorkspaceModel = viewModel(factory = factory { WorkspaceModel(graph) })
                    val editor: EditorModel = viewModel(factory = factory { EditorModel(graph) })
                    val nav = rememberNavController()
                    val entry by nav.currentBackStackEntryAsState()
                    val route = entry?.destination?.route ?: "files"
                    var incomingPath by rememberSaveable { mutableStateOf("") }
                    var incomingCommand by rememberSaveable { mutableStateOf("") }
                    var editorExit by remember { mutableStateOf(false) }
                    val error by workspace.error.collectAsStateWithLifecycle()
                    val destinations = listOf("files" to R.string.files, "terminal" to R.string.terminal, "assistant" to R.string.assistant, "tools" to R.string.tools)
                    val icons = listOf(Icons.Outlined.FolderOpen, Icons.Outlined.Terminal, Icons.Outlined.AutoAwesome, Icons.Outlined.Widgets)
                    val title = destinations.firstOrNull { it.first == route }?.second ?: when { route.startsWith("editor") -> R.string.editor; route == "setup" -> R.string.connect_termux; else -> R.string.settings }
                    val root = destinations.any { it.first == route }
                    fun go(destination: String) { nav.navigate(destination) { popUpTo("files") { saveState = true }; launchSingleTop = true; restoreState = true } }
                    fun openTerminal(path: String, command: String) { incomingPath = path; incomingCommand = command; go("terminal") }
                    LaunchedEffect(request) {
                        request?.let {
                            val shared = it.getStringExtra("shared_command").orEmpty()
                            if(shared.isNotBlank()) openTerminal("~/", shared)
                            else if(it.getStringExtra("route") == "assistant") go("assistant")
                            consumed()
                        }
                    }
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val rail = maxWidth >= 600.dp
                        Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
                            TopAppBar(title = { Column { Text(stringResource(title)); if(root) Text("TERMUX FILE MANAGER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                                navigationIcon = { if(!root) IconButton(onClick = { if(route.startsWith("editor")) editorExit = true else nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.close)) } },
                                actions = { if(root) { IconButton(onClick = { nav.navigate("setup") }) { Icon(Icons.Outlined.Link, stringResource(R.string.connect_termux)) }; IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) } } })
                        }, bottomBar = {
                            if(!rail && root && !WindowInsets.isImeVisible) NavigationBar { destinations.forEachIndexed { index, (destination, label) -> NavigationBarItem(selected = route == destination, onClick = { go(destination) }, icon = { Icon(icons[index], null) }, label = { Text(stringResource(label)) }) } }
                        }) { padding ->
                            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                                if(rail && root) NavigationRail { Spacer(Modifier.height(16.dp)); destinations.forEachIndexed { index, (destination, label) -> NavigationRailItem(selected = route == destination, onClick = { go(destination) }, icon = { Icon(icons[index], null) }, label = { Text(stringResource(label)) }); Spacer(Modifier.height(12.dp)) } }
                                NavHost(navController = nav, startDestination = "files", modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    composable("files") { FilesScreen(files, { nav.navigate("editor/${Uri.encode(it)}") }, ::openTerminal, { nav.navigate("setup") }) }
                                    composable("terminal") { TerminalScreen(workspace, incomingPath, incomingCommand) { incomingPath = ""; incomingCommand = "" } }
                                    composable("assistant") { AssistantScreen(workspace, { nav.navigate("settings") }, ::openTerminal) }
                                    composable("tools") { ToolsScreen(workspace) { workspace.suggestedPrompt.value = it; go("assistant") } }
                                    composable("settings") { SettingsScreen(graph) { nav.navigate("setup") } }
                                    composable("setup") { SetupScreen(graph) { files.refresh() } }
                                    composable("editor/{path}") { back -> EditorScreen(editor, back.arguments?.getString("path").orEmpty(), editorExit, { editorExit = false }) { nav.popBackStack(); files.refresh() } }
                                }
                            }
                        }
                    }
                    error?.let { AlertDialog(onDismissRequest = { workspace.error.value = null }, title = { Text(stringResource(R.string.error_title)) }, text = { Text(errorText(it)) }, confirmButton = { TextButton(onClick = { workspace.error.value = null }) { Text(stringResource(R.string.close)) } }) }
                }
            }
        }
    }
}
