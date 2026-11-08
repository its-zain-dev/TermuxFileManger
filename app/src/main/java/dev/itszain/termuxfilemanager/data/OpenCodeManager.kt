package dev.itszain.termuxfilemanager.data

import android.content.Context
import android.os.Build
import dev.itszain.termuxfilemanager.AiSecureStore
import dev.itszain.termuxfilemanager.core.CommandResult
import dev.itszain.termuxfilemanager.core.CommandSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class OpenCodeStatus(
    val supported: Boolean = true,
    val supportReason: String = "",
    val installed: Boolean = false,
    val compatible: Boolean = true,
    val version: String = "",
    val running: Boolean = false,
    val url: String = "",
    val checked: Boolean = false,
    val message: String = ""
)

data class OpenCodeConnection(val url: String, val password: String)

/**
 * Owns the Termux side of OpenCode: the signed Bionic package, the background service,
 * pairing links and model discovery. Every operation is serialized because RUN_COMMAND
 * commands and service restarts must not overlap.
 */
class OpenCodeManager(private val context: Context, private val gateway: TermuxGateway) {
    companion object {
        const val TERMUX_HOME = "/data/data/com.termux/files/home"
        private const val OWNER = "opencode"
        private const val INSTALL_TIMEOUT = 20 * 60_000L
        private const val CONNECTION_CACHE_MS = 120_000L
        private const val MODELS_CACHE_MS = 10 * 60_000L
        private const val PAIR_CACHE_MS = 7L * 24 * 60 * 60 * 1000
        private val URL_PATTERN = Regex("""https?://[^\s]+""")
        private val MODEL_PATTERN = Regex("""^[A-Za-z0-9_.@:-]+/[A-Za-z0-9_.@:#-]+$""")

        /** Converts the app's Termux-relative paths into absolute directories for the local API. */
        fun absoluteWorkdir(path: String): String {
            val raw = path.trim().ifBlank { "~/" }
            return when {
                raw == "~" -> TERMUX_HOME
                raw.startsWith("~/") -> TERMUX_HOME + raw.substring(1)
                raw.startsWith("/") -> raw
                else -> "$TERMUX_HOME/$raw"
            }
        }
    }

    private val prefs = AiSecureStore.settings(context)
    private val mutex = Mutex()
    val status = MutableStateFlow(OpenCodeStatus(supported = isSupported(), supportReason = supportReason()))
    @Volatile private var connectionCache: OpenCodeConnection? = null
    @Volatile private var connectionAt = 0L
    @Volatile private var modelsCache: List<String> = emptyList()
    @Volatile private var modelsAt = 0L

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= 29 && isArm64()
    fun supportReason(): String = when {
        Build.VERSION.SDK_INT < 29 -> "opencode_unsupported_android"
        !isArm64() -> "opencode_unsupported_cpu"
        else -> ""
    }
    private fun isArm64() = Build.SUPPORTED_ABIS.any { it.equals("arm64-v8a", ignoreCase = true) }

    suspend fun refresh(force: Boolean = false): OpenCodeStatus = mutex.withLock { refreshLocked(force) }

    suspend fun connection(force: Boolean = false): OpenCodeConnection = mutex.withLock { connectionLocked(force) }

    fun invalidate() {
        connectionCache = null
        connectionAt = 0
    }

    suspend fun models(force: Boolean = false): List<String> = mutex.withLock {
        check(isSupported()) { supportReason() }
        val now = System.currentTimeMillis()
        if (!force && modelsCache.isNotEmpty() && now - modelsAt < MODELS_CACHE_MS) return@withLock modelsCache
        val current = if (!status.value.checked) refreshLocked(false) else status.value
        check(current.installed) { "opencode_not_installed" }
        check(current.compatible) { "opencode_unsupported_version" }
        val result = run("opencode models 2>/dev/null", 120_000)
        val found = result.stdout.lineSequence().map { it.trim() }.filter { MODEL_PATTERN.matches(it) }.distinct().toList()
        if (found.isNotEmpty()) {
            modelsCache = found
            modelsAt = now
        }
        found
    }

    suspend fun pairUrl(): String = mutex.withLock {
        val connection = connectionLocked(false)
        val result = run("opencode pair 2>&1 | grep -oE 'https?://[^ ]+/auth/connect/[A-Za-z0-9_-]+' | head -n1", 90_000, secret = true)
        val link = result.stdout.lineSequence().map { it.trim() }.firstOrNull { it.contains("/auth/connect/") }
        check(!link.isNullOrBlank()) { "opencode_pair_failed" }
        prefs.edit().putLong("opencode_paired_at", System.currentTimeMillis()).putString("opencode_url", connection.url).apply()
        link
    }

    fun pairedRecently(): Boolean =
        prefs.getLong("opencode_paired_at", 0) > System.currentTimeMillis() - PAIR_CACHE_MS

    fun storedUrl(): String = prefs.getString("opencode_url", "").orEmpty()

    /**
     * Installs the Termux/Bionic build only after an explicit installation check.
     * The OpenCode web UI and local API are built for v2, so this refuses to touch
     * a different package manager source and always verifies the result afterwards.
     */
    suspend fun install(): OpenCodeStatus = mutex.withLock {
        val base = OpenCodeStatus(supported = isSupported(), supportReason = supportReason())
        check(base.supported) { base.supportReason }
        val existing = refreshLocked(true)
        // Only the signed Bionic package is trusted. A compatible OpenCode that is
        // already owned by dpkg counts as complete; anything else is replaced.
        val owned = existing.installed && run(
            "dpkg -S \"\$(command -v opencode)\" >/dev/null 2>&1 && echo yes || echo no", 30_000
        ).stdout.trim() == "yes"
        if (existing.installed && existing.compatible && owned) {
            return@withLock existing.copy(message = "opencode_already_installed")
        }
        status.value = base.copy(checked = true, message = "opencode_installing")
        val command = repoSetup() + "\n" + listOf(
            "pkg update -y",
            "BIN=\"\$(command -v opencode || true)\"",
            "if [ -n \"\$BIN\" ] && ! dpkg -S \"\$BIN\" >/dev/null 2>&1; then rm -f \"\$BIN\"; fi",
            "pkg install -y opencode",
            "opencode --version"
        ).joinToString("\n")
        val result = run(command, INSTALL_TIMEOUT)
        check(result.exitCode == 0) { "opencode_install_failed" }
        connectionCache = null
        modelsCache = emptyList()
        modelsAt = 0
        val next = refreshLocked(true)
        check(next.installed && next.compatible) { "opencode_install_failed" }
        next.copy(message = "opencode_installed")
    }

    suspend fun upgrade(): OpenCodeStatus = mutex.withLock {
        val current = refreshLocked(true)
        check(current.installed) { "opencode_not_installed" }
        status.value = current.copy(message = "opencode_installing")
        val result = run(repoSetup() + "\npkg update -y && pkg upgrade -y opencode", INSTALL_TIMEOUT)
        check(result.exitCode == 0) { "opencode_install_failed" }
        connectionCache = null
        modelsCache = emptyList()
        modelsAt = 0
        val next = refreshLocked(true)
        check(next.installed && next.compatible) { "opencode_install_failed" }
        next.copy(message = "opencode_updated")
    }

    private fun repoSetup(): String = listOf(
        "set -e",
        "export DEBIAN_FRONTEND=noninteractive",
        "mkdir -p \"\$PREFIX/etc/apt/sources.list.d\" \"\$PREFIX/etc/apt/trusted.gpg.d\"",
        "curl -fsSL https://bd-loser.github.io/opencode-bionic/opencode-bionic.gpg -o \"\$PREFIX/etc/apt/trusted.gpg.d/opencode-bionic.gpg\"",
        "chmod 644 \"\$PREFIX/etc/apt/trusted.gpg.d/opencode-bionic.gpg\"",
        "echo 'deb [arch=aarch64] https://bd-loser.github.io/opencode-bionic stable main' > \"\$PREFIX/etc/apt/sources.list.d/opencode-bionic.list\""
    ).joinToString("\n")

    suspend fun restart(): OpenCodeStatus = mutex.withLock {
        val current = refreshLocked(false)
        check(current.installed) { "opencode_not_installed" }
        run("opencode service restart >/dev/null 2>&1 || opencode service start >/dev/null 2>&1; opencode service status 2>/dev/null | head -n1", 120_000)
        connectionCache = null
        refreshLocked(true)
    }

    suspend fun stopService(): OpenCodeStatus = mutex.withLock {
        run("opencode service stop >/dev/null 2>&1 || true", 60_000)
        connectionCache = null
        status.value = status.value.copy(running = false, url = "")
        refreshLocked(true)
    }

    fun sessionFor(key: String): String = prefs.getString(sessionPref(key), "").orEmpty()

    fun saveSession(key: String, id: String) {
        prefs.edit().putString(sessionPref(key), id).apply()
    }

    fun forgetSession(key: String) {
        prefs.edit().remove(sessionPref(key)).apply()
    }

    private fun sessionPref(key: String) = "opencode_session_" + key.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    private suspend fun connectionLocked(force: Boolean): OpenCodeConnection {
        check(isSupported()) { supportReason() }
        val now = System.currentTimeMillis()
        connectionCache?.let { if (!force && now - connectionAt < CONNECTION_CACHE_MS) return it }
        var current = if (force || !status.value.checked) refreshLocked(force) else status.value
        check(current.installed) { "opencode_not_installed" }
        check(current.compatible) { "opencode_unsupported_version" }
        var url = current.url
        if (url.isBlank()) {
            run("opencode service start >/dev/null 2>&1 || true", 120_000)
            val service = run("opencode service status 2>/dev/null | head -n1", 30_000)
            url = URL_PATTERN.find(service.stdout)?.value.orEmpty()
            if (url.isBlank()) {
                run("opencode service restart >/dev/null 2>&1 || true", 120_000)
                val retry = run("opencode service status 2>/dev/null | head -n1", 30_000)
                url = URL_PATTERN.find(retry.stdout)?.value.orEmpty()
            }
        }
        check(url.isNotBlank()) { "opencode_start_failed" }
        val password = run("opencode service get password 2>/dev/null", 30_000, secret = true).stdout.trim()
        check(password.isNotBlank()) { "opencode_no_password" }
        val connection = OpenCodeConnection(url.trimEnd('/'), password)
        connectionCache = connection
        connectionAt = now
        current = current.copy(running = true, url = connection.url, checked = true)
        status.value = current
        return connection
    }

    private suspend fun refreshLocked(force: Boolean): OpenCodeStatus {
        val base = OpenCodeStatus(supported = isSupported(), supportReason = supportReason())
        if (!base.supported) {
            status.value = base.copy(checked = true)
            return status.value
        }
        val versionResult = run("opencode --version 2>/dev/null | head -n1", 30_000)
        val version = versionResult.stdout.lineSequence().firstOrNull()?.trim().orEmpty()
        if (versionResult.exitCode != 0 || version.isBlank()) {
            connectionCache = null
            status.value = base.copy(checked = true)
            return status.value
        }
        val service = run("opencode service status 2>/dev/null | head -n1", 30_000)
        val url = URL_PATTERN.find(service.stdout)?.value.orEmpty()
        if (url.isBlank()) connectionCache = null
        val major = Regex("""v?(\d+)\.""").find(version)?.groupValues?.get(1)?.toIntOrNull()
        val next = base.copy(installed = true, compatible = major == null || major >= 2, version = version,
            running = url.isNotBlank(), url = url, checked = true)
        status.value = next
        return next
    }

    private suspend fun run(command: String, timeoutMs: Long = 120_000, secret: Boolean = false): CommandResult {
        val result = try {
            withContext(Dispatchers.IO) {
                gateway.execute(CommandSpec(command = command, workdir = "~/", label = "OpenCode", ownerId = OWNER, kind = "opencode"), timeoutMs)
            }
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            // The command may still finish in Termux; report it instead of cancelling the UI scope.
            throw IllegalStateException("command_pending", timeout)
        }
        if (secret) runCatching { gateway.deleteCommand(result.id) }
        return result
    }
}
