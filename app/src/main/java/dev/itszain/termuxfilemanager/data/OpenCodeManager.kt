package dev.itszain.termuxfilemanager.data

import android.content.Context
import android.os.Build
import dev.itszain.termuxfilemanager.AiSecureStore
import dev.itszain.termuxfilemanager.core.CommandResult
import dev.itszain.termuxfilemanager.core.CommandSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

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
 * pairing links and model discovery.
 *
 * Everything the slow Termux round trips tell us is persisted (install version, service
 * URL, service password), so after an app restart the UI restores instantly from
 * SharedPreferences and the slow verification only happens on a background refresh.
 * Shell work is batched into single RUN_COMMAND scripts to minimise round trips, and
 * lightweight state ops (refresh/connection) never share a lock with heavy ops (install),
 * so installing never freezes the rest of the app.
 */
class OpenCodeManager(private val context: Context, private val gateway: TermuxGateway) {
    companion object {
        const val TERMUX_HOME = "/data/data/com.termux/files/home"
        private const val OWNER = "opencode"
        private const val INSTALL_TIMEOUT = 20 * 60_000L
        private const val VERIFY_COMMAND_TIMEOUT = 60_000L
        private const val SERVICE_COMMAND_TIMEOUT = 150_000L
        private const val CONNECTION_CACHE_MS = 15 * 60_000L
        private const val REFRESH_CACHE_MS = 60_000L
        private const val MODELS_CACHE_MS = 10 * 60_000L
        private const val PAIR_CACHE_MS = 7L * 24 * 60 * 60 * 1000
        private val URL_PATTERN = Regex("""https?://[^ ]+""")
        private val MODEL_PATTERN = Regex("""^[A-Za-z0-9_.@:-]+/[A-Za-z0-9_.@:#-]+$""")
        private val VERSION_PATTERN = Regex("""v?(\d+)\.""")

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
    private val stateMutex = Mutex()
    private val installMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val probe = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).build()

    val status = MutableStateFlow(restoreStatus())
    @Volatile private var connectionCache: OpenCodeConnection? = null
    @Volatile private var connectionAt = 0L
    @Volatile private var lastVerifyAt = 0L
    @Volatile private var modelsCache: List<String> = emptyList()
    @Volatile private var modelsAt = 0L
    @Volatile private var installing = false

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= 29 && isArm64()
    fun supportReason(): String = when {
        Build.VERSION.SDK_INT < 29 -> "opencode_unsupported_android"
        !isArm64() -> "opencode_unsupported_cpu"
        else -> ""
    }
    private fun isArm64() = Build.SUPPORTED_ABIS.any { it.equals("arm64-v8a", ignoreCase = true) }

    /** Restores the last verified install state so a restart never shows a false "not installed". */
    private fun restoreStatus(): OpenCodeStatus {
        val base = OpenCodeStatus(supported = isSupported(), supportReason = supportReason())
        if (!base.supported) return base
        val version = prefs.getString("opencode_version", "").orEmpty()
        if (version.isBlank()) return base
        return base.copy(installed = true, compatible = isCompatible(version), version = version, checked = true)
    }

    private fun isCompatible(version: String): Boolean =
        VERSION_PATTERN.find(version)?.groupValues?.get(1)?.toIntOrNull()?.let { it >= 2 } ?: true

    private fun persistVersion(version: String) {
        prefs.edit().putString("opencode_version", version).apply()
    }

    /**
     * One RUN_COMMAND that answers both questions at once: installed version and the
     * service URL. The exit code is non-zero exactly when OpenCode (the binary) is missing,
     * so a "not installed" answer is only reported after the command really executed.
     */
    private val verifyScript = listOf(
        "V=\"\$(opencode --version 2>/dev/null | head -n1)\"",
        "U=\"\$(opencode service status 2>/dev/null | grep -oE 'https?://[^ ]+' | head -n1)\"",
        "echo \"ver=\$V\"",
        "echo \"url=\$U\"",
        "[ -n \"\$V\" ]"
    ).joinToString("\n")

    suspend fun refresh(force: Boolean = false): OpenCodeStatus {
        if (installing) return status.value
        return stateMutex.withLock {
            val now = System.currentTimeMillis()
            if (!force && status.value.checked && now - lastVerifyAt < REFRESH_CACHE_MS) return@withLock status.value
            verifyNow()
        }
    }

    suspend fun connection(force: Boolean = false): OpenCodeConnection {
        check(isSupported()) { supportReason() }
        val now = System.currentTimeMillis()
        connectionCache?.let { if (!force && now - connectionAt < CONNECTION_CACHE_MS) return it }
        // Fast path: a previously stored connection (URL + password) answers with zero
        // Termux round trips; it is verified in the background and replaced if stale.
        if (!force) storedConnection()?.let { stored ->
            connectionCache = stored
            connectionAt = now
            verifyStored(stored)
            return stored
        }
        return stateMutex.withLock { connectionLocked(force) }
    }

    fun invalidate() {
        connectionCache = null
        connectionAt = 0
        clearStoredConnection()
    }

    suspend fun models(force: Boolean = false): List<String> = stateMutex.withLock {
        check(isSupported()) { supportReason() }
        val now = System.currentTimeMillis()
        if (!force && modelsCache.isNotEmpty() && now - modelsAt < MODELS_CACHE_MS) return@withLock modelsCache
        val current = if (!status.value.checked) verifyNow() else status.value
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

    suspend fun pairUrl(): String = stateMutex.withLock {
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

    /** Verifies install/version (+ service URL) with one shell call. Never reports
     *  "not installed" unless the verification command really ran and failed, and
     *  transient Termux errors keep the last known good state. */
    private suspend fun verifyNow(): OpenCodeStatus {
        val base = OpenCodeStatus(supported = isSupported(), supportReason = supportReason())
        if (!base.supported) {
            status.value = base.copy(checked = true)
            return status.value
        }
        val previous = status.value
        val verified = try { run(verifyScript, VERIFY_COMMAND_TIMEOUT) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) {
            // Termux slow/pending/permission: keep the persisted truth, do not flip to "not installed".
            status.value = previous.copy(checked = true)
            return status.value
        }
        if (verified.exitCode != 0) {
            // The command executed and confirmed OpenCode is missing.
            connectionCache = null
            connectionAt = 0
            clearStoredConnection()
            val next = base.copy(checked = true)
            persistVersion("")
            lastVerifyAt = System.currentTimeMillis()
            status.value = next
            return next
        }
        val stdout = verified.stdout
        val version = stdout.lineSequence().firstOrNull { it.startsWith("ver=") }?.removePrefix("ver=")?.trim().orEmpty()
        val url = stdout.lineSequence().firstOrNull { it.startsWith("url=") }?.removePrefix("url=")?.trim().orEmpty()
        if (url.isBlank()) connectionCache = null
        val next = base.copy(installed = true, compatible = isCompatible(version), version = version,
            running = url.isNotBlank(), url = url, checked = true)
        persistVersion(version)
        lastVerifyAt = System.currentTimeMillis()
        status.value = next
        return next
    }

    /**
     * Installs the Termux/Bionic build only after an explicit installation check.
     * The OpenCode web UI and local API are built for v2, so this refuses to touch
     * a different package manager source and always verifies the result afterwards.
     * Runs under its own lock, so the app stays usable during the (potentially long) install.
     */
    suspend fun install(): OpenCodeStatus = installMutex.withLock {
        installing = true
        try {
            val base = status.value
            check(base.supported) { base.supportReason }
            val existing = if (base.checked && base.version.isNotBlank()) base else verifyNow()
            // Only the signed Bionic package is trusted. A compatible OpenCode that is
            // already owned by dpkg counts as complete; anything else is replaced.
            val owned = existing.installed && run(
                "dpkg -S \"\$(command -v opencode)\" >/dev/null 2>&1 && echo yes || echo no", 30_000
            ).stdout.trim() == "yes"
            if (existing.installed && existing.compatible && owned) {
                return@withLock existing.copy(message = "opencode_already_installed")
            }
            status.value = existing.copy(checked = true, message = "opencode_installing")
            val command = repoSetup() + "\n" + listOf(
                "pkg update -y",
                "BIN=\"\$(command -v opencode || true)\"",
                "if [ -n \"\$BIN\" ] && ! dpkg -S \"\$BIN\" >/dev/null 2>&1; then rm -f \"\$BIN\"; fi",
                "pkg install -y opencode",
                "opencode --version"
            ).joinToString("\n")
            val result = run(command, INSTALL_TIMEOUT)
            check(result.exitCode == 0) { "opencode_install_failed" }
            dropConnection()
            modelsCache = emptyList()
            modelsAt = 0
            val next = verifyNow() // also re-persists the version
            check(next.installed && next.compatible) { "opencode_install_failed" }
            next.copy(message = "opencode_installed")
        } finally { installing = false }
    }

    suspend fun upgrade(): OpenCodeStatus = installMutex.withLock {
        installing = true
        try {
            val current = if (status.value.checked && status.value.version.isNotBlank()) status.value else verifyNow()
            check(current.installed) { "opencode_not_installed" }
            status.value = current.copy(message = "opencode_installing")
            val result = run(repoSetup() + "\npkg update -y && pkg upgrade -y opencode", INSTALL_TIMEOUT)
            check(result.exitCode == 0) { "opencode_install_failed" }
            dropConnection()
            modelsCache = emptyList()
            modelsAt = 0
            val next = verifyNow()
            check(next.installed && next.compatible) { "opencode_install_failed" }
            next.copy(message = "opencode_updated")
        } finally { installing = false }
    }

    private fun repoSetup(): String = listOf(
        "set -e",
        "export DEBIAN_FRONTEND=noninteractive",
        "mkdir -p \"\$PREFIX/etc/apt/sources.list.d\" \"\$PREFIX/etc/apt/trusted.gpg.d\"",
        "curl -fsSL https://bd-loser.github.io/opencode-bionic/opencode-bionic.gpg -o \"\$PREFIX/etc/apt/trusted.gpg.d/opencode-bionic.gpg\"",
        "chmod 644 \"\$PREFIX/etc/apt/trusted.gpg.d/opencode-bionic.gpg\"",
        "echo 'deb [arch=aarch64] https://bd-loser.github.io/opencode-bionic stable main' > \"\$PREFIX/etc/apt/sources.list.d/opencode-bionic.list\""
    ).joinToString("\n")

    suspend fun restart(): OpenCodeStatus = installMutex.withLock {
        val current = status.value
        check(current.installed) { "opencode_not_installed" }
        run("opencode service restart >/dev/null 2>&1 || opencode service start >/dev/null 2>&1; opencode service status 2>/dev/null | head -n1", 120_000)
        dropConnection()
        stateMutex.withLock { verifyNow() }
    }

    suspend fun stopService(): OpenCodeStatus = installMutex.withLock {
        run("opencode service stop >/dev/null 2>&1 || true", 60_000)
        dropConnection()
        stateMutex.withLock { verifyNow() }
    }

    fun sessionFor(key: String): String = prefs.getString(sessionPref(key), "").orEmpty()

    fun saveSession(key: String, id: String) {
        prefs.edit().putString(sessionPref(key), id).apply()
    }

    fun forgetSession(key: String) {
        prefs.edit().remove(sessionPref(key)).apply()
    }

    private fun sessionPref(key: String) = "opencode_session_" + key.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    /** Starts the service, falls back to a restart, and returns URL + password in one shell call. */
    private val serviceScript = listOf(
        "url=\"\$(opencode service status 2>/dev/null | grep -oE 'https?://[^ ]+' | head -n1)\"",
        "if [ -z \"\$url\" ]; then opencode service start >/dev/null 2>&1; sleep 2; url=\"\$(opencode service status 2>/dev/null | grep -oE 'https?://[^ ]+' | head -n1)\"; fi",
        "if [ -z \"\$url\" ]; then opencode service restart >/dev/null 2>&1; sleep 2; url=\"\$(opencode service status 2>/dev/null | grep -oE 'https?://[^ ]+' | head -n1)\"; fi",
        "if [ -z \"\$url\" ]; then exit 3; fi",
        "pwd=\"\$(opencode service get password 2>/dev/null)\"",
        "if [ -z \"\$pwd\" ]; then exit 4; fi",
        "echo \"url=\$url\"",
        "echo \"pwd=\$pwd\""
    ).joinToString("\n")

    private suspend fun connectionLocked(force: Boolean): OpenCodeConnection {
        val current = status.value
        check(current.installed) { "opencode_not_installed" }
        val now = System.currentTimeMillis()
        connectionCache?.let { if (!force && now - connectionAt < CONNECTION_CACHE_MS) return it }
        val result = run(serviceScript, SERVICE_COMMAND_TIMEOUT)
        val url = result.stdout.lineSequence().firstOrNull { it.startsWith("url=") }?.removePrefix("url=")?.trim().orEmpty()
        val password = result.stdout.lineSequence().firstOrNull { it.startsWith("pwd=") }?.removePrefix("pwd=")?.trim().orEmpty()
        check(url.isNotBlank()) { "opencode_start_failed" }
        check(password.isNotBlank()) { "opencode_no_password" }
        val connection = OpenCodeConnection(url.trimEnd('/'), password)
        connectionCache = connection
        connectionAt = now
        storeConnection(connection)
        status.value = status.value.copy(running = true, url = connection.url, checked = true)
        return connection
    }

    private fun verifyStored(connection: OpenCodeConnection) {
        scope.launch {
            val alive = runCatching { probe.isAlive(connection) }.getOrDefault(false)
            if (alive) {
                connectedStatus(connection)
            } else {
                if (connectionCache == connection) dropConnection()
                // Rebuild the connection through Termux so the next connection() is valid.
                runCatching { stateMutex.withLock { connectionLocked(true) } }
            }
        }
    }

    private fun connectedStatus(connection: OpenCodeConnection) {
        status.value = status.value.copy(running = true, url = connection.url, checked = true)
    }

    private fun OkHttpClient.isAlive(connection: OpenCodeConnection): Boolean =
        newCall(Request.Builder().url(connection.url + "/api/model/default")
            .header("Authorization", Credentials.basic("opencode", connection.password)).build())
            .execute().use { it.code != 401 && it.code != 403 }

    private fun storedConnection(): OpenCodeConnection? {
        val url = prefs.getString("opencode_conn_url", "").orEmpty()
        val password = prefs.getString("opencode_conn_pw", "").orEmpty()
        if (url.isBlank() || password.isBlank()) return null
        return OpenCodeConnection(url, password)
    }

    private fun storeConnection(connection: OpenCodeConnection) {
        prefs.edit().putString("opencode_conn_url", connection.url).putString("opencode_conn_pw", connection.password).apply()
    }

    private fun clearStoredConnection() {
        prefs.edit().remove("opencode_conn_url").remove("opencode_conn_pw").apply()
    }

    private fun dropConnection() {
        connectionCache = null
        connectionAt = 0
        clearStoredConnection()
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
