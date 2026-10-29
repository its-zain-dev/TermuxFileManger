package dev.itszain.termuxfilemanager

import android.app.Application
import dev.itszain.termuxfilemanager.core.AiRequest
import dev.itszain.termuxfilemanager.data.*
import org.json.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.*
import java.security.interfaces.RSAPublicKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ChatGptTest {
    private fun signed(): Pair<String,JSONObject> {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val public = pair.public as RSAPublicKey
        val header = ChatGptAuth.encode("{\"alg\":\"RS256\",\"kid\":\"test\"}".toByteArray())
        val claims = JSONObject().put("iss","https://auth.openai.com").put("sub","account1").put("aud","issued-client").put("nonce","expected-nonce").put("exp",System.currentTimeMillis()/1000 + 300)
        val payload = ChatGptAuth.encode(claims.toString().toByteArray())
        val signature = Signature.getInstance("SHA256withRSA").apply { initSign(pair.private); update("$header.$payload".toByteArray()) }.sign()
        val jwks = JSONObject().put("keys", JSONArray().put(JSONObject().put("kid","test").put("kty","RSA").put("n",ChatGptAuth.encode(public.modulus.toByteArray())).put("e",ChatGptAuth.encode(public.publicExponent.toByteArray()))))
        return "$header.$payload.${ChatGptAuth.encode(signature)}" to jwks
    }
    @Test fun signatureNonceAndAudienceAreValidated() {
        val (token, keys) = signed()
        assertEquals("account1", ChatGptAuth.verifyIdToken(token, keys, "issued-client", "expected-nonce").getString("sub"))
        for((client,nonce) in listOf("other-client" to "expected-nonce", "issued-client" to "wrong-nonce")) {
            try { ChatGptAuth.verifyIdToken(token, keys, client, nonce); fail("Accepted wrong identity") } catch(_: IllegalArgumentException) { }
        }
        val parts = token.split('.'); val changed = parts[0]+"."+ChatGptAuth.encode("{}".toByteArray())+"."+parts[2]
        try { ChatGptAuth.verifyIdToken(changed, keys, "issued-client", "expected-nonce"); fail("Accepted altered claims") } catch(_: IllegalArgumentException) { }
    }
    @Test fun planPayloadHasOnlySupportedFields() {
        val payload = ResponsesCodec.payload("account-model", AiRequest("chatgpt", listOf("user" to "Hi")), "Instructions")
        assertFalse(payload.getBoolean("store")); assertTrue(payload.getBoolean("stream"))
        assertFalse(payload.has("temperature")); assertFalse(payload.has("max_output_tokens")); assertFalse(payload.has("previous_response_id"))
        assertEquals("Instructions", payload.getString("instructions"))
    }
    @Test fun partialTextIsNotCompletionAndPlanLimitErrorsFail() {
        val listener = object: AiApiClient.StreamListener { override fun onText(fragment:String) {}; override fun onThinking(fragment:String) {} }
        val output = StringBuilder()
        assertFalse(ResponsesCodec.decode("{\"type\":\"response.output_text.delta\",\"delta\":\"hello\"}",listener,output))
        assertEquals("hello", output.toString())
        assertTrue(ResponsesCodec.decode("{\"type\":\"response.completed\"}",listener,output))
        try { ResponsesCodec.decode("{\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"subscription_sharing_usage_limit_exceeded\"}}}",listener,output); fail("Accepted limit error") }
        catch(error: ApiFailure) { assertEquals("subscription_sharing_usage_limit_exceeded", error.detail) }
    }
    @Test fun eventNamesAndBufferedResponsesRetainAnswers() {
        val listener = object: AiApiClient.StreamListener { override fun onText(fragment:String) {}; override fun onThinking(fragment:String) {} }
        val output = StringBuilder()
        assertFalse(ResponsesCodec.decode("{\"delta\":\"Hello\"}", listener, output, "response.output_text.delta"))
        assertTrue(ResponsesCodec.decode("{}", listener, output, "response.completed"))
        assertEquals("Hello", output.toString())
        val buffered = "{\"error\":null,\"status\":\"completed\",\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"Buffered answer\"}]}]}"
        val final = StringBuilder()
        assertTrue(ResponsesCodec.decode(buffered, listener, final))
        assertEquals("Buffered answer", final.toString())
        assertTrue(ResponsesCodec.decode(buffered, listener, final))
        assertEquals("Buffered answer", final.toString())
    }
    @Test fun untypedPlanErrorsKeepTheActualServerReason() {
        val listener = object: AiApiClient.StreamListener { override fun onText(fragment:String) {}; override fun onThinking(fragment:String) {} }
        for(raw in listOf(
            "{\"error\":{\"code\":\"insufficient_scope\",\"message\":\"Plan permission required\"}}",
            "{\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"}}"
        )) {
            try { ResponsesCodec.decode(raw, listener, StringBuilder()); fail("Accepted unsuccessful response") }
            catch(error: ApiFailure) { assertFalse(error.detail == "chatgpt_incomplete") }
        }
    }

}
