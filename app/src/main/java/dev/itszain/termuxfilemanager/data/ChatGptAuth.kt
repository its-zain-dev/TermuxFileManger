package dev.itszain.termuxfilemanager.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import dev.itszain.termuxfilemanager.AiSecureStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.*
import java.security.spec.RSAPublicKeySpec
import java.math.BigInteger
import java.util.UUID

/** Official public-client dynamic registration: no embedded client secret or copied Codex credentials. */
class ChatGptAuth(private val context: Context, private val scope: CoroutineScope, private val stopRequests: suspend () -> Unit = {}) {
    private val http = OkHttpClient.Builder().followRedirects(false).build()
    private val lock = Mutex()
    val accounts = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val active = MutableStateFlow("")
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    private var loginJob: Job? = null
    private fun records() = AiSecureStore.getKey(context, "chatgpt_oauth").let { if(it.isBlank()) JSONObject() else JSONObject(it) }
    private fun save(records: JSONObject) { AiSecureStore.saveKey(context, "chatgpt_oauth", records.toString()) }
    suspend fun load() = withContext(Dispatchers.IO) { lock.withLock { publish(records()) } }
    private fun publish(records: JSONObject) {
        accounts.value = records.keys().asSequence().map { id -> id to (records.getJSONObject(id).optString("email").ifBlank { "ChatGPT" } + " · " + id.takeLast(6)) }.toList()
        active.value = AiSecureStore.settings(context).getString("chatgpt_account", "").orEmpty()
    }
    fun cancel() { loginJob?.cancel() }
    fun login(existing: String? = null) {
        if(busy.value) return
        busy.value = true; error.value = null
        loginJob = scope.launch {
            try {
                val previous = lock.withLock { existing?.let { records().optJSONObject(it) } }
                val prefs = AiSecureStore.settings(context)
                val host = prefs.getString("chatgpt_host", null) ?: ("urn:uuid:" + UUID.randomUUID()).also { prefs.edit().putString("chatgpt_host", it).commit() }
                val state = random(); val nonce = random(); val verifier = random()
                ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")).use { server ->
                    server.soTimeout = 1000
                    val redirect = "http://127.0.0.1:${server.localPort}/auth/callback"
                    val builder = Uri.parse("https://auth.openai.com/api/accounts/authorize").buildUpon()
                    val parameters = linkedMapOf("client_id" to (existing ?: "dynamic_agent_client"), "ext_agent_host_id" to host,
                        "response_type" to "code", "redirect_uri" to redirect, "scope" to SCOPES, "resource" to RESOURCE,
                        "state" to state, "nonce" to nonce, "code_challenge_method" to "S256", "code_challenge" to encode(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())))
                    if(existing == null) parameters["agent_name_hint"] = "Termux File Manager"
                    previous?.optString("id_token")?.takeIf { it.isNotBlank() }?.let { parameters["id_token_hint"] = it }
                    parameters.forEach { (k,v) -> builder.appendQueryParameter(k,v) }
                    withContext(Dispatchers.Main) { context.startActivity(Intent(Intent.ACTION_VIEW, builder.build()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    val deadline = System.currentTimeMillis() + 600_000
                    var callback: Uri? = null
                    while(callback == null && System.currentTimeMillis() < deadline) {
                        ensureActive()
                        try { server.accept().use { socket ->
                            socket.soTimeout = 2000
                            val input = socket.getInputStream(); val line = StringBuilder()
                            while(line.length < 8192) { val char = input.read(); if(char < 0 || char == 10) break; line.append(char.toChar()) }
                            val request = line.toString().split(' ')
                            val uri = request.getOrNull(1)?.let { Uri.parse("http://127.0.0.1$it") }
                            val valid = request.firstOrNull() == "GET" && uri?.path == "/auth/callback" && MessageDigest.isEqual(uri.getQueryParameter("state").orEmpty().toByteArray(), state.toByteArray())
                            val body = if(valid) "Return to Termux File Manager to finish signing in." else "Invalid authorization callback."
                            socket.getOutputStream().write("HTTP/1.1 ${if(valid) "200 OK" else "400 Bad Request"}\r\nContent-Type: text/plain; charset=utf-8\r\nCache-Control: no-store\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body".toByteArray())
                            if(valid) callback = uri
                        } } catch(_: SocketTimeoutException) { }
                    }
                    val result = callback ?: kotlin.error("chatgpt_login_timeout")
                    check(result.getQueryParameter("error") == null) { "chatgpt_login_declined" }
                    val issued = result.getQueryParameter("client_id") ?: existing ?: kotlin.error("chatgpt_registration_incomplete")
                    require(issued != "dynamic_agent_client" && (existing == null || issued == existing)) { "chatgpt_invalid_identity" }
                    val code = result.getQueryParameter("code") ?: kotlin.error("chatgpt_registration_incomplete")
                    val tokens = token(mapOf("grant_type" to "authorization_code", "client_id" to issued, "code" to code, "code_verifier" to verifier, "redirect_uri" to redirect, "resource" to RESOURCE))
                    val jwks = get("https://auth.openai.com/.well-known/jwks.json")
                    val identity = verifyIdToken(tokens.getString("id_token"), jwks, issued, nonce)
                    check(previous == null || previous.getString("subject") == identity.getString("sub")) { "chatgpt_invalid_identity" }
                    check(tokens.optString("scope").split(' ').contains("chatgpt.tokens.use.direct")) { "chatgpt_plan_not_granted" }
                    tokens.put("client_id", issued).put("subject", identity.getString("sub")).put("email", identity.optString("email"))
                        .put("expires_at", System.currentTimeMillis() + tokens.getLong("expires_in") * 1000)
                    ensureActive()
                    stopRequests()
                    lock.withLock { val all = records(); all.put(issued, tokens); save(all); prefs.edit().putString("chatgpt_account", issued).commit(); publish(all) }
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(failure: Exception) { error.value = failure.message ?: "chatgpt_login_failed" }
            finally { busy.value = false }
        }
    }
    suspend fun accessToken(): String = withContext(Dispatchers.IO) { lock.withLock {
        val all = records(); val id = AiSecureStore.settings(context).getString("chatgpt_account", "").orEmpty()
        var entry = all.optJSONObject(id) ?: kotlin.error("chatgpt_sign_in_required")
        check(entry.optString("access_token").isNotBlank()) { "chatgpt_sign_in_required" }
        if(entry.optLong("expires_at") <= System.currentTimeMillis() + 60_000) {
            val renewed = token(mapOf("grant_type" to "refresh_token", "client_id" to id, "refresh_token" to entry.getString("refresh_token"), "resource" to RESOURCE))
            check(renewed.optString("scope", entry.optString("scope")).split(' ').contains("chatgpt.tokens.use.direct")) { "chatgpt_plan_not_granted" }
            renewed.keys().forEach { entry.put(it, renewed.get(it)) }
            entry.put("expires_at", System.currentTimeMillis() + renewed.getLong("expires_in") * 1000)
            all.put(id, entry); save(all)
        }
        entry.getString("access_token")
    } }
    suspend fun logout(id: String) = withContext(Dispatchers.IO) {
        cancel()
        loginJob?.join()
        stopRequests()
        lock.withLock {
        val all = records(); val entry = all.optJSONObject(id) ?: return@withLock
        var revoked = false
        try {
            val endpoint = get("https://auth.openai.com/.well-known/openid-configuration").getString("revocation_endpoint")
            require(Uri.parse(endpoint).host == "auth.openai.com" && endpoint.startsWith("https://"))
            val body = FormBody.Builder().add("token", entry.optString("refresh_token")).add("token_type_hint", "refresh_token").add("client_id", id).build()
            for(attempt in 0..2) {
                val status = runCatching { http.newCall(Request.Builder().url(endpoint).post(body).build()).execute().use { it.code } }.getOrDefault(503)
                if(status == 200) { revoked = true; break }
                if(status < 500) break
                if(attempt < 2) delay(500L * (attempt + 1))
            }
        } finally {
            listOf("access_token", "refresh_token", "id_token").forEach { entry.remove(it) }
            all.put(id, entry); save(all); if(active.value == id) AiSecureStore.settings(context).edit().remove("chatgpt_account").commit(); publish(all)
            if(!revoked) error.value = "chatgpt_revocation_unconfirmed"
        }
    } }
    private fun token(values: Map<String,String>): JSONObject {
        val body = FormBody.Builder().apply { values.forEach { (k,v) -> add(k,v) } }.build()
        return http.newCall(Request.Builder().url("https://auth.openai.com/api/accounts/oauth/token").post(body).build()).execute().use {
            if(!it.isSuccessful) throw IllegalStateException("chatgpt_reauthorize")
            JSONObject(it.body!!.string())
        }
    }
    private fun get(url: String) = http.newCall(Request.Builder().url(url).build()).execute().use { check(it.isSuccessful) { "chatgpt_login_failed" }; JSONObject(it.body!!.string()) }
    companion object {
        const val RESOURCE = "https://api.openai.com/v1"
        const val SCOPES = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
        fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        private fun random() = encode(ByteArray(32).also { SecureRandom().nextBytes(it) })
        fun verifyIdToken(jwt: String, jwks: JSONObject, client: String, nonce: String): JSONObject {
            val parts = jwt.split('.'); require(parts.size == 3) { "chatgpt_invalid_identity" }
            fun decode(value: String) = Base64.decode(value, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val header = JSONObject(String(decode(parts[0]), Charsets.UTF_8)); require(header.getString("alg") == "RS256") { "chatgpt_invalid_identity" }
            val keys = jwks.getJSONArray("keys"); val key = (0 until keys.length()).map { keys.getJSONObject(it) }.firstOrNull { it.optString("kid") == header.optString("kid") && it.optString("kty") == "RSA" } ?: kotlin.error("chatgpt_invalid_identity")
            val rsa = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(BigInteger(1, decode(key.getString("n"))), BigInteger(1, decode(key.getString("e")))))
            val signature = Signature.getInstance("SHA256withRSA").apply { initVerify(rsa); update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII)) }
            require(signature.verify(decode(parts[2]))) { "chatgpt_invalid_identity" }
            val claims = JSONObject(String(decode(parts[1]), Charsets.UTF_8)); val aud = claims.opt("aud")
            val audience = if(aud is JSONArray) (0 until aud.length()).map { aud.getString(it) } else listOf(aud?.toString().orEmpty())
            require(claims.getString("iss") == "https://auth.openai.com" && client in audience && claims.getLong("exp") > System.currentTimeMillis()/1000 && claims.getString("nonce") == nonce && claims.getString("sub").isNotBlank()) { "chatgpt_invalid_identity" }
            if(audience.size > 1 || claims.has("azp")) require(claims.optString("azp") == client) { "chatgpt_invalid_identity" }
            return claims
        }
    }
}
