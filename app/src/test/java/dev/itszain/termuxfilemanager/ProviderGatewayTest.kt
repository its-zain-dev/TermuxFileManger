package dev.itszain.termuxfilemanager

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.itszain.termuxfilemanager.core.*
import dev.itszain.termuxfilemanager.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ProviderGatewayTest {
    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer(); server.useHttps(serverTls.sslSocketFactory(), false); server.start()
        client = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
    }
    @After fun close() { server.shutdown(); client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    private fun gateway(provider: String, path: String = "/v1/chat/completions"): ProviderGateway {
        AiSecureStore.settings(context).edit().putString("model_$provider", "test-model").putString("endpoint_$provider", server.url(path).toString()).commit()
        return ProviderGateway(context, client, { "test-key" }, { true })
    }
    @Test fun parsesOpenAiSseAndUsesBearerHeader() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}\n\ndata: [DONE]\n\n"))
        val events = gateway("openai").stream(AiRequest("openai", listOf("user" to "Hi"))).toList()
        assertEquals("hello world", (events.last() as AiEvent.Complete).value)
        val request = server.takeRequest(); assertEquals("Bearer test-key", request.getHeader("Authorization"))
        assertEquals("test-model", JSONObject(request.body.readUtf8()).getString("model"))
    }
    @Test fun geminiUsesHeaderAuthAndSeparatesExposedThinking() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: {\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"text\":\"summary\"},{\"text\":\"answer\"}]}}]}\n\n"))
        val events = gateway("gemini", "/v1/models/{model}:generateContent").stream(AiRequest("gemini", listOf("user" to "Hi"))).toList()
        assertEquals("summary", (events.first() as AiEvent.Thinking).value)
        assertEquals("answer", (events.last() as AiEvent.Complete).value)
        val request = server.takeRequest(); assertEquals("test-key", request.getHeader("x-goog-api-key")); assertFalse(request.path!!.contains("test-key"))
        assertTrue(JSONObject(request.body.readUtf8()).has("contents"))
    }
    @Test fun anthropicAndCoherePayloadsAndStreams() = runBlocking {
        val cases = listOf("anthropic" to "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"answer\"}}", "cohere" to "{\"delta\":{\"message\":{\"content\":{\"text\":\"answer\"}}}}")
        for((provider, chunk) in cases) {
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: $chunk\n\n"))
            val result = gateway(provider).stream(AiRequest(provider, listOf("user" to "Hi"))).toList()
            assertEquals("answer", (result.last() as AiEvent.Complete).value)
            val request = server.takeRequest(); val body = JSONObject(request.body.readUtf8()); assertTrue(body.has("messages"))
            if(provider == "anthropic") { assertEquals("test-key", request.getHeader("x-api-key")); assertTrue(body.has("system")) }
        }
    }
    @Test fun rateLimitRetainsRetryAfter() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "20").setBody("{\"error\":{\"message\":\"wait\"}}"))
        try { gateway("openai").stream(AiRequest("openai", listOf("user" to "hi"))).toList(); fail("Expected HTTP error") }
        catch(error: ApiFailure) { assertEquals(429, error.status); assertEquals("20", error.retryAfter) }
    }
    @Test fun normalJsonFallbackAndMalformedEvents() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{\"choices\":[{\"message\":{\"content\":\"answer\"}}]}"))
        assertEquals("answer", (gateway("openai").stream(AiRequest("openai", listOf("user" to "hi"))).toList().last() as AiEvent.Complete).value)
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: not-json\n\n"))
        try { gateway("openai").stream(AiRequest("openai", listOf("user" to "hi"))).toList(); fail("Expected malformed event") } catch(_: org.json.JSONException) { }
    }
    @Test fun cancellationClosesTheInFlightRequest() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = launch(Dispatchers.IO) { gateway("openai").stream(AiRequest("openai", listOf("user" to "hi"))).collect() }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        withTimeout(3000) { job.cancelAndJoin() }
        withTimeout(3000) { while(client.dispatcher.runningCallsCount() != 0) delay(10) }
        assertTrue(job.isCancelled)
    }
    @Test fun allExistingProviderIdsRemainSupported() {
        assertEquals(setOf("chatgpt","gemini","anthropic","openai","openrouter","groq","mistral","cohere","deepseek","xai","together"), AiProvider.all().map { it.id }.toSet())
    }
    @Test fun chatGptNativeToolOnlyResponseIsNotAnEmptyResponse() = runBlocking {
        val final = "{\"response\":{\"status\":\"completed\",\"output\":[{\"type\":\"function_call\",\"namespace\":\"termux\",\"name\":\"app_info\",\"call_id\":\"call-1\",\"arguments\":\"{}\"}]}}"
        server.enqueue(MockResponse().setHeader("Content-Type","text/event-stream").setBody("event: response.completed\ndata: $final\n\n"))
        val routed = client.newBuilder().addInterceptor { chain -> chain.proceed(chain.request().newBuilder().url(server.url("/v1/responses")).build()) }.build()
        AiSecureStore.settings(context).edit().putString("model_chatgpt","test-model").commit()
        val api = ProviderGateway(context,routed,{ "unused" },{ true },{ "plan-token" })
        val events = api.stream(AiRequest("chatgpt",listOf("user" to "Inspect your model"),nativeTools=setOf("app_info"))).toList()
        assertEquals("app_info",events.filterIsInstance<AiEvent.ToolCall>().single().name)
        assertEquals("",(events.last() as AiEvent.Complete).value)
        val request = server.takeRequest();assertEquals("Bearer plan-token",request.getHeader("Authorization"))
        val body=JSONObject(request.body.readUtf8())
        assertEquals("termux",body.getJSONArray("tools").getJSONObject(0).getString("name"))
        assertTrue(body.getString("instructions").contains("Never print tfm_action"))
    }

}
