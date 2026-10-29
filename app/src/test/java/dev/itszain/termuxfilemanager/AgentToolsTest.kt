package dev.itszain.termuxfilemanager

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.itszain.termuxfilemanager.core.*
import dev.itszain.termuxfilemanager.data.*
import dev.itszain.termuxfilemanager.features.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AgentToolsTest {
    @Test fun extractsMixedArabicAndQuotedBracesWithoutExecutingProse() {
        val args = JSONObject().put("tool", "run_shell").put("command", "python - <<'PY'\nprint({'text': 'brace } inside'})\nPY")
        val text = "سأفحص الإعدادات دون مفاتيح. " + JSONObject().put("tfm_action", args) + "\nثم ألخص النتيجة."
        assertEquals(args.getString("command"), AgentAction.parse(text)!!.value("command"))
        assertNull(AgentAction.parse("مرحبا! hello is not a shell instruction."))
        assertNull(AgentAction.parse("{\"tfm_action\":{\"tool\":\"erase_everything\"}}"))
    }
    @Test fun toolSchemaAndPolicyAgreeAndValidateArguments() {
        val schemas = AgentTools.schemas(AgentTools.all.map { it.name }.toSet())
        assertEquals("namespace", schemas.getJSONObject(0).getString("type"))
        val functions = schemas.getJSONObject(0).getJSONArray("tools")
        assertEquals(13, functions.length())
        for(i in 0 until functions.length()) {
            val f = functions.getJSONObject(i);val p = f.getJSONObject("parameters")
            assertFalse(p.getBoolean("additionalProperties"));assertEquals(p.getJSONObject("properties").length(),p.getJSONArray("required").length())
        }
        assertFalse(AgentTools.needsReview(AgentAction("app_info","{}"),false))
        assertTrue(AgentTools.needsReview(AgentAction("write_file","{}"),false))
        assertTrue(AgentTools.needsReview(AgentAction("run_shell","{\"command\":\"rm -rf ~/x\"}"),true))
        assertTrue(AgentTools.isGreeting("مرحبا!"));assertFalse(AgentTools.isGreeting("مرحبا افحص المشروع"))
        try { AgentAction.fromCall(AiEvent.ToolCall("a","run_shell","{\"command\":123}"));fail("Accepted invalid schema") } catch(_: IllegalArgumentException) {}
    }
    @Test fun nativeHistoryKeepsCallOutputPairsAndRecoversInterruptedCalls() {
        val function = JSONObject().put("type","function_call").put("namespace","termux").put("name","app_info").put("call_id","call-1").put("arguments","{}")
        val rows = listOf(MessageRecord("a","c","assistant","",metadata=JSONObject().put("response_output",JSONArray().put(function)).toString()),
            MessageRecord("b","c","tool_output","actual-model",metadata="{\"call_id\":\"call-1\"}"))
        val input = ResponsesHistory.build(rows)
        assertEquals(2,input.size);assertEquals("function_call_output",JSONObject(input[1]).getString("type"))
        assertEquals("actual-model",JSONObject(input[1]).getString("output"))
        assertTrue(JSONObject(ResponsesHistory.build(rows.take(1))[1]).getString("output").contains("interrupted"))
        assertTrue(ResponsesHistory.build(rows.takeLast(1)).isEmpty())
    }
    @Test fun nativeDecoderEmitsARealCallWithoutTextAndRetainsReasoningItems() {
        val events = mutableListOf<AiEvent.ToolCall>();var items:List<String> = emptyList()
        val call = JSONObject().put("type","function_call").put("namespace","termux").put("name","app_info").put("call_id","call-1").put("arguments","{}")
        val response = JSONObject().put("type","response.completed").put("response",JSONObject().put("status","completed").put("output",JSONArray().put(JSONObject().put("type","reasoning").put("id","r1").put("encrypted_content","opaque")).put(call)))
        val listener = object:AiApiClient.StreamListener {override fun onText(fragment:String){};override fun onThinking(fragment:String){}}
        assertTrue(ResponsesCodec.decode(response.toString(),listener,StringBuilder(),onCall={events+=it},onOutput={items=it}))
        assertEquals("app_info",events.single().name);assertEquals(2,items.size)
        events.clear(); call.put("namespace", JSONObject.NULL)
        ResponsesCodec.decode(response.toString(),listener,StringBuilder(),onCall={events+=it})
        assertEquals("app_info",AgentAction.fromCall(events.single()).tool)
        val request = AiRequest("chatgpt",emptyList(),nativeTools=setOf("app_info"),responseItems=items)
        val body = ResponsesCodec.payload("actual-model",request,"instructions")
        assertTrue(body.has("tools"));assertFalse(body.getBoolean("parallel_tool_calls"));assertFalse(body.has("previous_response_id"))
    }
    @Test fun modelInspectionExecutesLocallyAndIsReturnedToTheModel() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = AppGraph(context)
        AiSecureStore.settings(context).edit().clear().putString("model_chatgpt","actual-model").putInt("rate_gap_seconds",0).commit()
        val chat = ChatRecord("native-loop","Inspect","chatgpt")
        graph.dao.saveChat(chat)
        var rounds=0
        val fake = object:AiGateway {
            override fun stream(request:AiRequest) = flow {
                rounds++
                if(rounds==1) {
                    emit(AiEvent.ToolCall("call-1","app_info","{}"))
                    emit(AiEvent.ToolCall("call-1","app_info","{}"))
                    emit(AiEvent.Complete(""))
                } else {
                    val output = request.responseItems!!.map { JSONObject(it) }.first { it.optString("type")=="function_call_output" }
                    val info = JSONObject(output.getString("output"))
                    assertEquals("actual-model",info.getString("model"));assertEquals("chatgpt",info.getString("provider"))
                    assertFalse(info.toString().contains("access_token"));assertFalse(info.toString().contains("endpoint"))
                    emit(AiEvent.Complete("The configured model is actual-model."))
                }
            }
        }
        try {
            val agent = AgentController(graph,fake)
            agent.start(chat.id,"Which model are you using?")
            withTimeout(15000) { while(graph.dao.history(chat.id).none { it.text=="The configured model is actual-model." }) delay(30) }
            assertEquals(2,rounds)
            assertEquals(1,graph.dao.history(chat.id).count { it.role=="tool_call" && it.status=="complete" })
            agent.stopAndJoin(chat.id)
        } finally { graph.scope.cancel();graph.database.close() }
        Unit
    }
    @Test fun shellRequestWaitsForApprovalAndDeclineDoesNotExecute() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = AppGraph(context)
        AiSecureStore.settings(context).edit().clear().putString("model_chatgpt","actual-model").putInt("rate_gap_seconds",0).commit()
        val chat = ChatRecord("approval-loop","Review","chatgpt")
        graph.dao.saveChat(chat)
        val fake = object:AiGateway {
            override fun stream(request:AiRequest) = flow {
                emit(AiEvent.ToolCall("shell-1","run_shell","{\"command\":\"printf should-not-run\",\"workdir\":\"~/\",\"reason\":\"test\"}"))
                emit(AiEvent.Complete(""))
            }
        }
        val agent = AgentController(graph,fake)
        try {
            agent.start(chat.id,"Inspect the environment")
            withTimeout(10000) { while(graph.dao.history(chat.id).none { it.role=="tool_call" && it.status=="awaiting_approval" }) delay(30) }
            assertTrue(graph.dao.history(chat.id).none { it.role=="tool_output" })
            agent.approve(chat.id,true,"stale-request-id")
            assertTrue(graph.dao.history(chat.id).none { it.role=="tool_output" })
            agent.approve(chat.id,false,agent.pending.value.getValue(chat.id).id)
            withTimeout(10000) { while(graph.dao.history(chat.id).none { it.role=="tool_output" && it.text.contains("action_declined") }) delay(30) }
            assertTrue(graph.dao.commands(chat.id).first().isEmpty())
        } finally { agent.stopAndJoin(chat.id);graph.scope.cancel();graph.database.close() }
        Unit
    }

    @Test fun encryptedReasoningIsScopedToTheAccountAndModel() {
        val output = JSONArray().put(JSONObject().put("type","reasoning").put("encrypted_content","opaque"))
            .put(JSONObject().put("type","message").put("role","assistant").put("content","answer"))
        val metadata = JSONObject().put("response_output",output).put("request_model","model-a").put("request_account","account-a")
        val rows = listOf(MessageRecord("a","chat","assistant","answer",metadata=metadata.toString()))
        assertEquals(2,ResponsesHistory.build(rows,"model-a","account-a").size)
        assertEquals(1,ResponsesHistory.build(rows,"model-a","account-b").size)
        assertEquals(1,ResponsesHistory.build(rows,"model-b","account-a").size)
    }

}
