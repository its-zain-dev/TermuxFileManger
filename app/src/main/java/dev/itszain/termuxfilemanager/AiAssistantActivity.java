package dev.itszain.termuxfilemanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Multi-chat AI coding assistant linked to saved Termux work sessions. */
public class AiAssistantActivity extends Activity {
    private UiTheme theme; private String lastThemeName="midnight"; private int BG,PANEL,PANEL2,TEXT,MUTED,ACCENT,DANGER,DIVIDER;
    private JSONArray chats,sessions; private String activeChatId="",attachedImage=""; private Spinner chatsSpinner,providerSpinner,terminalSpinner; private TextView modelLabel,rateLabel,attachmentLabel; private LinearLayout messagesBox; private ScrollView messagesScroll; private EditText input; private Button sendButton; private boolean receiverRegistered=false; private boolean busy=false; private boolean updatingSpinners=false;
    private final ExecutorService worker=Executors.newFixedThreadPool(3); private final Handler handler=new Handler(Looper.getMainLooper()); private final Map<Integer,String> commandChats=new HashMap<>(); private final Map<Integer,Boolean> agentCommandRequests=new HashMap<>(); private final Map<Integer,PopupCommand> popupCommands=new HashMap<>(); private final Map<Integer,Runnable> workspaceSyncCallbacks=new HashMap<>(); private final Map<String,Boolean> cancelledAgentRuns=new HashMap<>(); private final Map<String,Integer> noProgressContinuations=new HashMap<>(); private final Map<String,StreamViews> activeStreams=new HashMap<>(); private boolean autoScreenshotUsed=false;
    private static final class StreamViews {
        final TextView answer, thinking; final Button toggle; final View thinkingBody;
        StreamViews(TextView answer, TextView thinking, Button toggle, View thinkingBody) { this.answer=answer; this.thinking=thinking; this.toggle=toggle; this.thinkingBody=thinkingBody; }
    }
    private static final class PopupCommand { final TextView output; final String sessionId; PopupCommand(TextView output,String sessionId){this.output=output;this.sessionId=sessionId;} }
    private final BroadcastReceiver resultReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            int id=i.getIntExtra(CommandResultService.EXTRA_REQUEST_ID,-1);
            String stdout=i.getStringExtra("stdout"),stderr=i.getStringExtra("stderr");
            int exit=i.getIntExtra("exit_code",0),err=i.getIntExtra("err_code",-1);
            Runnable syncCallback=workspaceSyncCallbacks.remove(id);
            if(syncCallback!=null){
                AgentWorkspace.applyRead(AiAssistantActivity.this,stdout==null?"":stdout);
                chats=AiSecureStore.loadArray(AiAssistantActivity.this,AiSecureStore.CHATS,"items");
                sessions=AiSecureStore.loadArray(AiAssistantActivity.this,AiSecureStore.SESSIONS,"items");
                handler.post(()->{ updateHeader(); syncCallback.run(); }); return;
            }
            String output=(stdout==null?"":stdout)+(stderr==null||stderr.trim().isEmpty()?"":"\n[stderr]\n"+stderr)+"\n[exit="+exit+", err="+err+"]\n";
            PopupCommand popup=popupCommands.remove(id);
            if(popup!=null){appendSessionOutput(popup.sessionId,output);popup.output.append(output);return;}
            String chatId=commandChats.remove(id);if(chatId==null)return;
            boolean agentRequest=agentCommandRequests.remove(id)!=null;
            JSONObject chat=findChat(chatId);if(chat==null)return;
            if (agentRequest) noProgressContinuations.put(chatId, 0);
            try{JSONArray m=chat.optJSONArray("messages");if(m==null)m=new JSONArray();JSONObject event=msg("tool_output",output);event.put("tool_label","Terminal output");event.put("display_path",extractReadingPath(chat));m.put(event);while(m.length()>80)m.remove(0);chat.put("messages",m);saveChats();if(chatId.equals(activeChatId))renderMessages();}catch(Exception ignored){}
            if(agentRequest){
                if(!busy) autoAnalyzeCommandResult(chatId);
                else handler.postDelayed(()->{if(!busy)autoAnalyzeCommandResult(chatId);else Toast.makeText(AiAssistantActivity.this,"وصلت النتيجة؛ أرسل متابعة يدوية لأن الوكيل مشغول.",Toast.LENGTH_LONG).show();},1800);
            }else if(AiSecureStore.settings(AiAssistantActivity.this).getBoolean("auto_analyze_results",false)){
                if(!busy) autoAnalyzeCommandResult(chatId);
                else Toast.makeText(AiAssistantActivity.this,"وصلت مخرجات Termux؛ أوقفنا التحليل التلقائي لأن هناك طلب AI جارٍ بالفعل.",Toast.LENGTH_LONG).show();
            }
        }
    };
    private final BroadcastReceiver screenshotReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){maybeSubmitPendingScreenshot();}
    };
    @Override public void onCreate(Bundle b){super.onCreate(b);initTheme();UiTheme.applyWindow(this);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);chats=AiSecureStore.loadArray(this,AiSecureStore.CHATS,"items");sessions=AiSecureStore.loadArray(this,AiSecureStore.SESSIONS,"items");activeChatId=AiSecureStore.settings(this).getString("active_ai_chat","");if(chats.length()==0)newChat(false);if(findChat(activeChatId)==null)activeChatId=chats.optJSONObject(0).optString("id");build();IntentFilter f=new IntentFilter(CommandResultService.ACTION_RESULT);IntentFilter sf=new IntentFilter(ScreenCaptureService.ACTION_SCREENSHOT_SAVED);if(Build.VERSION.SDK_INT>=33){registerReceiver(resultReceiver,f,Context.RECEIVER_NOT_EXPORTED);registerReceiver(screenshotReceiver,sf,Context.RECEIVER_NOT_EXPORTED);}else{registerReceiver(resultReceiver,f);registerReceiver(screenshotReceiver,sf);}receiverRegistered=true;syncAgentWorkspace(() -> { });}
    @Override protected void onDestroy(){if(receiverRegistered){try{unregisterReceiver(resultReceiver);}catch(Exception ignored){}try{unregisterReceiver(screenshotReceiver);}catch(Exception ignored){}}worker.shutdownNow();super.onDestroy();}
    private void initTheme(){lastThemeName=UiTheme.getName(this);theme=UiTheme.get(this);BG=theme.bg;PANEL=theme.panel;PANEL2=theme.panel2;TEXT=theme.text;MUTED=theme.muted;ACCENT=theme.accent;DANGER=theme.danger;DIVIDER=theme.divider;}
    private JSONObject current(){return findChat(activeChatId);} private JSONObject findChat(String id){return AiSecureStore.findById(chats,id);}
    private JSONObject msg(String role,String text){JSONObject o=new JSONObject();try{o.put("role",role);o.put("text",text);o.put("time",System.currentTimeMillis());}catch(Exception ignored){}return o;}
    private void newChat(boolean rebuild){String id=Long.toString(System.nanoTime());JSONObject c=new JSONObject();try{c.put("id",id);c.put("name","دردشة "+(chats.length()+1));c.put("provider",AiSecureStore.getActiveProvider(this));c.put("sessionId",AiSecureStore.settings(this).getString("active_terminal_session",""));c.put("messages",new JSONArray());chats.put(c);activeChatId=id;saveChats();if(rebuild)rebuildChatSpinner();}catch(Exception ignored){}}
    private void saveChats(){AiSecureStore.saveArray(this,AiSecureStore.CHATS,"items",chats);AiSecureStore.settings(this).edit().putString("active_ai_chat",activeChatId).apply();}
    private void build() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);
        setContentView(frame);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(5), dp(10), dp(6));
        root.setBackgroundColor(BG);
        frame.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // These spinners are retained as state holders; their pickers are opened from the floating menu.
        chatsSpinner = new Spinner(this);
        chatsSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, chatNames()));
        providerSpinner = new Spinner(this);
        List<String> providerNames = new ArrayList<>();
        for (AiProvider provider : AiProvider.all()) providerNames.add(provider.name);
        providerSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, providerNames));
        terminalSpinner = new Spinner(this);
        terminalSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, terminalNames()));
        modelLabel = text("Model", 10, MUTED, false);
        rateLabel = text("جاهز", 10, MUTED, false);
        chatsSpinner.setSelection(indexOfChat(activeChatId));

        chatsSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> p) { }
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (!updatingSpinners && pos >= 0 && pos < chats.length()) {
                    activeChatId = chats.optJSONObject(pos).optString("id"); saveChats(); updateHeader(); renderMessages();
                }
            }
        });
        providerSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> p) { }
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (updatingSpinners || pos < 0 || pos >= AiProvider.all().size()) return;
                JSONObject c = current(); if (c == null) return;
                try {
                    c.put("provider", AiProvider.all().get(pos).id); saveChats();
                    AgentWorkspace.persistChatOverride(AiAssistantActivity.this, c.optString("id"), c.optString("provider"), c.optString("sessionId", ""), c.has("agent_auto_execute") ? Boolean.valueOf(c.optBoolean("agent_auto_execute")) : null);
                    updateHeader();
                } catch (Exception ignored) { }
            }
        });
        terminalSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> p) { }
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (updatingSpinners) return;
                JSONObject c = current(); if (c != null) try {
                    c.put("sessionId", sessionIdAt(pos)); saveChats();
                    AgentWorkspace.persistChatOverride(AiAssistantActivity.this, c.optString("id"), c.optString("provider", "gemini"), c.optString("sessionId", ""), c.has("agent_auto_execute") ? Boolean.valueOf(c.optBoolean("agent_auto_execute")) : null);
                } catch (Exception ignored) { }
            }
        });

        messagesScroll = new ScrollView(this);
        messagesScroll.setFillViewport(true);
        messagesScroll.setClipToPadding(false);
        messagesScroll.setPadding(0, dp(54), 0, 0);
        messagesBox = new LinearLayout(this);
        messagesBox.setOrientation(LinearLayout.VERTICAL);
        messagesBox.setPadding(dp(2), dp(2), dp(2), dp(8));
        messagesScroll.addView(messagesBox);
        root.addView(messagesScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        attachmentLabel = text("ماكو صورة مرفقة", 10, MUTED, false);
        attachmentLabel.setPadding(dp(3), dp(2), dp(3), dp(2));
        attachmentLabel.setVisibility(View.GONE);
        root.addView(attachmentLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        input = new EditText(this);
        input.setTextColor(TEXT); input.setHintTextColor(MUTED); input.setTextSize(14);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setMinLines(1); input.setMaxLines(4); input.setMaxHeight(dp(104));
        input.setHint("اكتب هدفك…");
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setBackground(round(PANEL2, 16, DIVIDER));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        inputLp.rightMargin = dp(6);
        composer.addView(input, inputLp);

        Button send = button("↑", ACCENT, BG);
        send.setTextSize(24);
        send.setContentDescription("إرسال إلى الوكيل");
        composer.addView(send, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(composer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sendButton = send;
        send.setOnClickListener(v -> sendCurrent());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER && event.isShiftPressed()) return false;
            if (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_UP) { sendCurrent(); return true; }
            return false;
        });

        Button menu = button("⋮", PANEL2, TEXT);
        menu.setTextSize(23);
        menu.setElevation(dp(8));
        menu.setBackground(round(PANEL2, 25, DIVIDER));
        menu.setContentDescription("خيارات الوكيل");
        FrameLayout.LayoutParams menuLp = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.RIGHT);
        menuLp.setMargins(0, dp(10), dp(10), 0);
        frame.addView(menu, menuLp);
        menu.setOnClickListener(v -> showAgentMenu());

        updateHeader();
        renderMessages();
    }

    private void updateHeader() {
        JSONObject c = current(); if (c == null) return;
        String provider = c.optString("provider", AiSecureStore.getActiveProvider(this));
        int providerIndex = AiProvider.indexOf(provider);
        updatingSpinners = true;
        if (providerSpinner != null && providerSpinner.getSelectedItemPosition() != providerIndex) providerSpinner.setSelection(providerIndex);
        AiProvider p = AiProvider.byId(provider);
        if (modelLabel != null) modelLabel.setText("Model: " + AiSecureStore.getModel(this, p.id));
        if (rateLabel != null) rateLabel.setText("متبقي اليوم: " + AiSecureStore.remainingRequests(this, p.id) + " طلب · حدود محلية");
        int sessionIndex = indexOfSession(c.optString("sessionId", ""));
        if (terminalSpinner != null && terminalSpinner.getSelectedItemPosition() != sessionIndex) terminalSpinner.setSelection(Math.max(0, sessionIndex));
        updatingSpinners = false;
    }
    private void renderMessages() {
        if (messagesBox == null) return;
        activeStreams.clear();
        messagesBox.removeAllViews();
        JSONObject chat = current();
        if (chat == null) return;
        JSONArray all = chat.optJSONArray("messages");
        if (all == null || all.length() == 0) {
            TextView welcome = text("المساعد جاهز. كل أدوات الوكيل والتفكير ومخرجاتها راح تنعرض بفقاعة عمل واحدة. الأوامر تبقى خاضعة لإعدادات الموافقة.", 13, MUTED, false);
            welcome.setPadding(dp(12), dp(12), dp(12), dp(12));
            welcome.setBackground(round(PANEL, 12, DIVIDER));
            messagesBox.addView(welcome, match());
            return;
        }
        int i = 0;
        while (i < all.length()) {
            JSONObject item = all.optJSONObject(i);
            if (item == null) { i++; continue; }
            if (isVisibleUserPrompt(item)) {
                addMessageView(item, chat.optString("id"));
                i++;
                continue;
            }
            JSONArray run = new JSONArray();
            while (i < all.length()) {
                JSONObject event = all.optJSONObject(i);
                if (event == null) { i++; continue; }
                if (isVisibleUserPrompt(event)) break;
                run.put(event);
                i++;
            }
            if (run.length() > 0) addAgentRunBubble(chat.optString("id"), run);
        }
        messagesScroll.post(() -> messagesScroll.fullScroll(View.FOCUS_DOWN));
    }

    private boolean isVisibleUserPrompt(JSONObject message) {
        if (message == null || !"user".equals(message.optString("role", ""))) return false;
        String value = message.optString("text", "").trim();
        return !value.startsWith("[نتيجة أداة") && !value.startsWith("[Agent tool result]");
    }

    private void addMessageView(JSONObject m, String chatId) {
        String role = m.optString("role", "user"), txt = m.optString("text", "");
        if ("agent_control".equals(role)) return;
        boolean assistant = "assistant".equals(role), terminal = "terminal".equals(role), toolOutput = "tool_output".equals(role);
        if (toolOutput) { addToolOutputCard(m); return; }
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(11), dp(9), dp(11), dp(9));
        bubble.setBackground(round(assistant ? PANEL2 : (terminal ? Color.rgb(27,34,42) : PANEL), 13, assistant ? ACCENT : DIVIDER));
        TextView label = text(assistant ? AiProvider.byId(providerFor(chatId)).name : (terminal ? "TERMUX" : "أنت"), 10, assistant ? ACCENT : MUTED, true);
        bubble.addView(label, match());
        String thoughtText = m.optString("thinking", "");
        if (assistant && !m.optBoolean("streaming", false) && !thoughtText.trim().isEmpty()) addThinkingCard(bubble, thoughtText);
        String streamId = m.optString("stream_id", "");
        if (assistant && m.optBoolean("streaming", false) && !streamId.isEmpty()) {
            Button toggle = button(thoughtText.isEmpty() ? "تفكير الموديل جارٍ…" : "ملخص التفكير · " + thoughtText.length() + " حرف", PANEL, ACCENT);
            LinearLayout thoughtBody = new LinearLayout(this); thoughtBody.setOrientation(LinearLayout.VERTICAL); thoughtBody.setPadding(dp(8),dp(7),dp(8),dp(7)); thoughtBody.setBackground(round(BG,8,DIVIDER));
            TextView thoughtView = text(thoughtText.isEmpty() ? "بانتظار ملخص التفكير من المزوّد…" : thoughtText, 11, MUTED, false); thoughtView.setGravity(Gravity.TOP|Gravity.START); thoughtView.setTextIsSelectable(true); thoughtBody.addView(thoughtView,match()); thoughtBody.setVisibility(View.GONE);
            toggle.setOnClickListener(v->{boolean show=thoughtBody.getVisibility()!=View.VISIBLE;thoughtBody.setVisibility(show?View.VISIBLE:View.GONE);}); bubble.addView(toggle,match()); bubble.addView(thoughtBody,match());
            TextView liveAnswer=text(txt.isEmpty()?"جارٍ توليد الرد…":txt,13,TEXT,false); liveAnswer.setGravity(Gravity.TOP|Gravity.START); liveAnswer.setTextIsSelectable(true); bubble.addView(liveAnswer,match());
            activeStreams.put(streamId,new StreamViews(liveAnswer,thoughtView,toggle,thoughtBody));
        } else {
        JSONObject action = assistant ? extractAgentAction(txt) : null;
        if (action != null && isSkillActivation(action)) {
            String skillName=action.optString("skill",action.optString("name","skill"));
            bubble.addView(text("يتحقق من المهارة /"+skillName+" ويحمّل تعليماتها…",12,ACCENT,true),match());
        } else if (action != null) {
            String tool = action.optString("tool", action.optString("name", "run_shell"));
            String reason = action.optString("reason", "الوكيل يحتاج أداة حتى يكمل المهمة.");
            boolean skillInstall = "install_skill".equals(tool);
            String display = skillInstall ? action.optString("url", action.optString("source", "")) : agentCommand(action);
            TextView description = text(reason, 13, TEXT, false);
            description.setPadding(0, dp(4), 0, dp(5));
            bubble.addView(description, match());
            TextView code = text(skillInstall ? ("Skill URL: " + display) : ("$ " + display), 12, TEXT, false);
            code.setTypeface(Typeface.MONOSPACE);
            code.setTextIsSelectable(true);
            code.setPadding(dp(9), dp(9), dp(9), dp(9));
            code.setBackground(round(BG, 9, DIVIDER));
            bubble.addView(code, match());
            Button run = button(skillInstall ? "تنصيب المهارة" : "مراجعة وتشغيل الأداة", ACCENT, BG);
            run.setOnClickListener(v -> confirmAgentAction(chatId, action));
            bubble.addView(run, match());
            if (!skillInstall) {
                Button copy = button("نسخ الأمر", PANEL, TEXT);
                copy.setOnClickListener(v -> copyText("AI command", display));
                bubble.addView(copy, match());
            }
        } else {
            addMarkdownContent(bubble, txt, chatId);
        }
        }
        LinearLayout.LayoutParams p = match();
        p.topMargin = dp(5);
        p.width = ViewGroup.LayoutParams.MATCH_PARENT;
        p.gravity = assistant ? Gravity.START : Gravity.END;
        messagesBox.addView(bubble, p);
    }

    /** One compact assistant bubble contains every turn, thought, tool invocation and result until the next user prompt. */
    private void addAgentRunBubble(String chatId, JSONArray events) {
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(11), dp(8), dp(11), dp(8));
        bubble.setBackground(round(PANEL2, 13, ACCENT));
        TextView title = text("AI Agent · " + AiProvider.byId(providerFor(chatId)).name, 10, ACCENT, true);
        bubble.addView(title, compactParams());

        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null) continue;
            String role = event.optString("role", "");
            if ("agent_control".equals(role)) continue;

            if ("tool_output".equals(role)) {
                String label = event.optString("tool_label", "Tool output");
                String raw = event.optString("text", "");
                addAgentExpandableRow(bubble, "TOOL output · " + compactToolLabel(label),
                        raw.length() + " chars · اضغط لعرض المخرجات", raw, null, chatId, false);
                continue;
            }
            if ("user".equals(role) && event.optString("text", "").trim().startsWith("[نتيجة أداة")) {
                String raw = event.optString("text", "");
                addAgentExpandableRow(bubble, "TOOL output · install_skill", raw.length() + " chars · اضغط للتفاصيل", raw, null, chatId, false);
                continue;
            }
            if (!"assistant".equals(role)) continue;

            String rawText = event.optString("text", "");
            String thought = event.optString("thinking", "");
            String streamId = event.optString("stream_id", "");
            boolean streaming = event.optBoolean("streaming", false) && !streamId.isEmpty();
            JSONObject action = event.optJSONObject("agent_action");
            if (action == null) action = extractAgentAction(rawText);

            if (streaming) {
                Button thoughtToggle = button(thought.isEmpty() ? "Thinking · بانتظار ملخص التفكير" : "Thinking · " + thought.length() + " chars", BG, MUTED);
                thoughtToggle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                thoughtToggle.setTextSize(11);
                thoughtToggle.setVisibility(thought.isEmpty() ? View.GONE : View.VISIBLE);
                LinearLayout thoughtBody = new LinearLayout(this);
                thoughtBody.setOrientation(LinearLayout.VERTICAL);
                thoughtBody.setPadding(dp(8), dp(5), dp(8), dp(5));
                thoughtBody.setBackground(round(BG, 8, DIVIDER));
                TextView thoughtView = text(thought.isEmpty() ? "" : thought, 11, MUTED, false);
                thoughtView.setGravity(Gravity.TOP | Gravity.START);
                thoughtView.setTextIsSelectable(true);
                thoughtBody.addView(thoughtView, compactParams());
                thoughtBody.setVisibility(View.GONE);
                thoughtToggle.setOnClickListener(v -> {
                    boolean show = thoughtBody.getVisibility() != View.VISIBLE;
                    thoughtBody.setVisibility(show ? View.VISIBLE : View.GONE);
                });
                bubble.addView(thoughtToggle, compactParams());
                bubble.addView(thoughtBody, compactParams());
                addCompactDivider(bubble);

                boolean looksLikeToolJson = looksLikeAgentActionPayload(rawText);
                TextView liveAnswer = text(rawText.isEmpty() ? "" : (looksLikeToolJson ? "جاري تجهيز استدعاء الأداة…" : rawText), 13, TEXT, false);
                liveAnswer.setGravity(Gravity.TOP | Gravity.START);
                liveAnswer.setTextIsSelectable(true);
                liveAnswer.setVisibility(rawText.isEmpty() || looksLikeToolJson ? View.GONE : View.VISIBLE);
                bubble.addView(liveAnswer, compactParams());
                activeStreams.put(streamId, new StreamViews(liveAnswer, thoughtView, thoughtToggle, thoughtBody));
                continue;
            }

            if (thought != null && !thought.trim().isEmpty()) {
                addAgentExpandableRow(bubble, "Thinking · " + thought.length() + " chars",
                        "اضغط لعرض ملخص التفكير", thought, null, chatId, false);
            }

            if (action != null) {
                String tool = action.optString("tool", action.optString("name", ""));
                if ("finish".equals(tool) || "continue".equals(tool)) {
                    if (!rawText.trim().isEmpty()) addMarkdownContent(bubble, rawText, chatId);
                    continue;
                }
                JSONObject pairedOutput = null;
                if (i + 1 < events.length()) {
                    JSONObject next = events.optJSONObject(i + 1);
                    if (next != null && ("tool_output".equals(next.optString("role", ""))
                            || ("user".equals(next.optString("role", "")) && next.optString("text", "").trim().startsWith("[نتيجة أداة")))) {
                        pairedOutput = next;
                    }
                }
                String details = describeAgentTool(action);
                if (pairedOutput != null) {
                    details += "\n\n--- TOOL OUTPUT ---\n" + pairedOutput.optString("text", "");
                    i++;
                }
                addAgentExpandableRow(bubble, "TOOL called · " + compactToolLabel(tool),
                        pairedOutput == null ? "اضغط لعرض الأداة والإجراء" : "الأداة والمخرجات · اضغط للتفاصيل",
                        details, action, chatId, pairedOutput != null);
                continue;
            }

            if (!rawText.trim().isEmpty()) addMarkdownContent(bubble, rawText, chatId);
        }
        LinearLayout.LayoutParams lp = match();
        lp.topMargin = dp(5);
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.gravity = Gravity.START;
        messagesBox.addView(bubble, lp);
    }

    private void addAgentExpandableRow(LinearLayout parent, String heading, String summary, String details,
                                       JSONObject action, String chatId, boolean alreadyExecuted) {
        Button row = button("›  " + heading, PANEL, ACCENT);
        row.setContentDescription(summary);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setTextSize(11);
        row.setMinHeight(dp(34));
        row.setPadding(dp(7), dp(3), dp(7), dp(3));
        parent.addView(row, compactParams());

        LinearLayout detailBox = new LinearLayout(this);
        detailBox.setOrientation(LinearLayout.VERTICAL);
        detailBox.setPadding(dp(8), dp(6), dp(8), dp(6));
        detailBox.setBackground(round(BG, 8, DIVIDER));
        TextView content = text(details == null ? "" : details, 11, TEXT, false);
        content.setGravity(Gravity.TOP | Gravity.START);
        content.setTextIsSelectable(true);
        content.setTypeface(Typeface.MONOSPACE);
        detailBox.addView(content, compactParams());

        if (action != null && !alreadyExecuted) {
            String tool = action.optString("tool", action.optString("name", ""));
            if (!"finish".equals(tool) && !"continue".equals(tool)
                    && !autoExecutionEnabled(chatId) && isRunnableAgentAction(action)) {
                Button run = button("مراجعة وتشغيل الأداة", ACCENT, BG);
                run.setOnClickListener(v -> confirmAgentAction(chatId, action));
                detailBox.addView(run, compactParams());
                String command = agentCommand(action);
                if (!command.trim().isEmpty()) {
                    Button copy = button("نسخ الأمر", PANEL, TEXT);
                    copy.setOnClickListener(v -> copyText("AI command", command));
                    detailBox.addView(copy, compactParams());
                }
            }
        }
        detailBox.setVisibility(View.GONE);
        parent.addView(detailBox, compactParams());
        row.setOnClickListener(v -> {
            boolean show = detailBox.getVisibility() != View.VISIBLE;
            detailBox.setVisibility(show ? View.VISIBLE : View.GONE);
            row.setText((show ? "⌄  " : "›  ") + heading);
        });
        addCompactDivider(parent);
    }

    private boolean isRunnableAgentAction(JSONObject action) {
        if (action == null) return false;
        String tool = action.optString("tool", action.optString("name", "run_shell"));
        if ("install_skill".equals(tool)) return true;
        if ("run_shell".equals(tool) || "shell".equals(tool) || "run_command".equals(tool)) return !agentCommand(action).trim().isEmpty();
        // These internal/read-only tools are auto-dispatched by the agent loop; showing a second
        // manual-run button could launch the same operation twice while its result is in flight.
        return false;
    }

    private String describeAgentTool(JSONObject action) {
        String tool = action.optString("tool", action.optString("name", "unknown"));
        StringBuilder out = new StringBuilder("Tool: ").append(tool);
        String reason = action.optString("reason", "");
        if (!reason.isEmpty()) out.append("\nReason: ").append(reason);
        String command = agentCommand(action);
        if (!command.isEmpty()) out.append("\nCommand:\n").append(command);
        String query = action.optString("query", "");
        JSONObject args = action.optJSONObject("arguments");
        if (query.isEmpty() && args != null) query = args.optString("query", args.optString("q", ""));
        if (!query.isEmpty()) out.append("\nQuery: ").append(query);
        String url = action.optString("url", action.optString("source", ""));
        if (!url.isEmpty()) out.append("\nURL: ").append(url);
        String workdir = action.optString("workdir", args == null ? "" : args.optString("workdir", ""));
        if (!workdir.isEmpty()) out.append("\nWorking directory: ").append(workdir);
        String risk = action.optString("risk", "");
        if (!risk.isEmpty()) out.append("\nRisk: ").append(risk);
        return out.toString();
    }

    private String compactToolLabel(String source) {
        if (source == null || source.trim().isEmpty()) return "tool";
        String s = source.trim().replace("Terminal output", "run_shell").replace("Web search results", "web_search");
        return s.length() > 40 ? s.substring(0, 37) + "…" : s;
    }

    private LinearLayout.LayoutParams compactParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(2);
        return lp;
    }

    private void addCompactDivider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(2); lp.bottomMargin = dp(3);
        parent.addView(line, lp);
    }

    private void addToolOutputCard(JSONObject item) {
        String raw=item.optString("text","");
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(10),dp(7),dp(10),dp(7)); card.setBackground(round(PANEL,10,DIVIDER));
        String path=item.optString("display_path","");
        TextView heading=text(path.isEmpty()?"Tool output · Termux":"Reading "+path,11,ACCENT,true); card.addView(heading,match());
        TextView preview=text(raw.length()>160?raw.substring(0,160)+"…":"Output received · "+raw.length()+" chars",11,MUTED,false); card.addView(preview,match());
        TextView details=text(raw,11,TEXT,false); details.setTypeface(Typeface.MONOSPACE); details.setTextIsSelectable(true); details.setGravity(Gravity.TOP|Gravity.START); details.setPadding(dp(7),dp(7),dp(7),dp(7)); details.setBackground(round(BG,8,DIVIDER)); details.setVisibility(View.GONE);
        ScrollView sc=new ScrollView(this); sc.addView(details); card.addView(sc,new LinearLayout.LayoutParams(-1,dp(180)));
        Button toggle=button("عرض المخرجات الخام",PANEL2,ACCENT); toggle.setOnClickListener(v->{boolean show=details.getVisibility()!=View.VISIBLE;details.setVisibility(show?View.VISIBLE:View.GONE);toggle.setText(show?"إخفاء المخرجات":"عرض المخرجات الخام");preview.setVisibility(show?View.GONE:View.VISIBLE);}); card.addView(toggle,match());
        LinearLayout.LayoutParams lp=match();lp.topMargin=dp(4);messagesBox.addView(card,lp);
    }

    private String extractReadingPath(JSONObject chat) {
        try { JSONArray a=chat.optJSONArray("messages"); if(a!=null&&a.length()>0){String last=a.optJSONObject(a.length()-1).optString("text","");java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)(?:cat|read|reading)\\s+([~/][^\\s;&|]+)").matcher(last);if(m.find())return m.group(1);} } catch(Exception ignored){} return "";
    }

    private void addThinkingCard(LinearLayout bubble,String thought) {
        Button toggle=button("ملخص التفكير · "+thought.length()+" حرف",PANEL,ACCENT);
        TextView body=text(thought,11,MUTED,false);body.setTypeface(Typeface.MONOSPACE);body.setGravity(Gravity.TOP|Gravity.START);body.setTextIsSelectable(true);body.setPadding(dp(8),dp(7),dp(8),dp(7));body.setBackground(round(BG,8,DIVIDER));body.setVisibility(View.GONE);
        toggle.setOnClickListener(v->{boolean show=body.getVisibility()!=View.VISIBLE;body.setVisibility(show?View.VISIBLE:View.GONE);toggle.setText(show?"إخفاء ملخص التفكير":"ملخص التفكير · "+thought.length()+" حرف");});
        bubble.addView(toggle,match());bubble.addView(body,match());
    }

    private void addMarkdownContent(LinearLayout target,String source,String chatId){
        Matcher matcher=Pattern.compile("```([a-zA-Z0-9_+.-]*)\\s*([\\s\\S]*?)```").matcher(source==null?"":source);
        int cursor=0;
        while(matcher.find()){
            addMarkdownText(target,source.substring(cursor,matcher.start()));
            addCodeCard(target,matcher.group(1),matcher.group(2).trim(),chatId);
            cursor=matcher.end();
        }
        if(cursor==0){addMarkdownText(target,source==null?"":source);}
        else if(cursor<(source==null?0:source.length())) addMarkdownText(target,source.substring(cursor));
    }

    private void addMarkdownText(LinearLayout target,String raw){
        if(raw==null||raw.trim().isEmpty())return;
        String[] lines=raw.split("\\r?\\n");
        for(String original:lines){
            String line=original.trim(); if(line.isEmpty())continue;
            boolean heading=line.startsWith("#");
            if(heading){line=line.replaceFirst("^#{1,4}\\s*","");}
            else if(line.startsWith("- ")||line.startsWith("* "))line="• "+line.substring(2);
            else if(line.matches("^[0-9]+[.)]\\s+.*"))line="• "+line.replaceFirst("^[0-9]+[.)]\\s+","");
            line=line.replace("**","").replace("__","");
            line=line.replaceAll("`([^`]+)`","$1");
            TextView row=text(line,heading?15:13,TEXT,heading);
            row.setGravity(Gravity.TOP|Gravity.START); row.setTextIsSelectable(true);
            LinearLayout.LayoutParams lp=match(); lp.topMargin=dp(1); lp.bottomMargin=dp(3); target.addView(row,lp);
        }
    }

    private void addCodeCard(LinearLayout target,String language,String code,String chatId){
        if(code==null||code.trim().isEmpty())return;
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(9),dp(8),dp(9),dp(8)); card.setBackground(round(BG,10,DIVIDER));
        String lang=language==null?"":language.trim().toLowerCase();
        card.addView(text(lang.isEmpty()?"CODE":("CODE · "+lang),10,ACCENT,true),match());
        TextView body=text(code,12,TEXT,false); body.setTypeface(Typeface.MONOSPACE); body.setTextIsSelectable(true);
        body.setGravity(Gravity.TOP|Gravity.START); LinearLayout.LayoutParams bodyLp=match(); bodyLp.topMargin=dp(4); card.addView(body,bodyLp);
        LinearLayout actions=new LinearLayout(this); actions.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        Button copy=button("نسخ",PANEL,TEXT); copy.setOnClickListener(v->copyText("Code",code));
        actions.addView(copy,new LinearLayout.LayoutParams(dp(72),dp(36)));
        if(lang.isEmpty()||lang.equals("bash")||lang.equals("sh")||lang.equals("shell")||lang.equals("termux")||lang.equals("zsh")){
            Button run=button("تشغيل في Termux",PANEL2,ACCENT); LinearLayout.LayoutParams runLp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(36)); runLp.leftMargin=dp(6);
            run.setOnClickListener(v->confirmSuggestedCommand(chatId,code)); actions.addView(run,runLp);
        }
        LinearLayout.LayoutParams aLp=match(); aLp.topMargin=dp(6); card.addView(actions,aLp);
        LinearLayout.LayoutParams p=match(); p.topMargin=dp(4); target.addView(card,p);
    }

    private void copyText(String label,String value){
        ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label,value));
        Toast.makeText(this,"تم النسخ",Toast.LENGTH_SHORT).show();
    }

    private JSONObject extractAgentAction(String source){
        if(source==null||source.trim().isEmpty())return null;
        Matcher code=Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```").matcher(source);
        while(code.find()){JSONObject found=parseAgentActionCandidate(code.group(1));if(found!=null)return found;}
        JSONObject direct=parseAgentActionCandidate(source.trim());if(direct!=null)return direct;
        int marker=source.indexOf("\\\"tfm_action\\\"");
        if(marker<0)marker=source.indexOf("\"tfm_action\"");
        if(marker>=0){
            int start=source.lastIndexOf('{',marker);
            for(int end=source.lastIndexOf('}');start>=0&&end>=start;end=source.lastIndexOf('}',end-1)){
                JSONObject found=parseAgentActionCandidate(source.substring(start,end+1));if(found!=null)return found;
            }
        }
        return null;
    }

    private JSONObject parseAgentActionCandidate(String candidate) {
        try {
            JSONObject root = new JSONObject(candidate.trim());
            JSONObject action = root.optJSONObject("tfm_action");
            if (action == null) return null;
            String tool = action.optString("tool", action.optString("name", "")).trim();
            if (tool.isEmpty()) return null;
            if ("run_shell".equals(tool) || "shell".equals(tool) || "run_command".equals(tool)) {
                if (agentCommand(action).trim().isEmpty()) return null;
            } else if ("web_search".equals(tool)) {
                JSONObject args = action.optJSONObject("arguments");
                String query = action.optString("query", "").trim();
                if (query.isEmpty() && args != null) query = args.optString("query", args.optString("q", "")).trim();
                if (query.isEmpty()) return null;
            } else if ("save_memory".equals(tool) || "remember".equals(tool)) {
                JSONObject args = action.optJSONObject("arguments");
                String content = action.optString("content", action.optString("memory", action.optString("text", ""))).trim();
                if (content.isEmpty() && args != null) content = args.optString("content", args.optString("text", "")).trim();
                if (content.isEmpty()) return null;
            } else if ("install_skill".equals(tool)) {
                JSONObject args = action.optJSONObject("arguments");
                String url = action.optString("url", action.optString("source", "")).trim();
                if (url.isEmpty() && args != null) url = args.optString("url", args.optString("source", "")).trim();
                if (!url.startsWith("https://")) return null;
            } else if ("activate_skill".equals(tool) || "use_skill".equals(tool)) {
                JSONObject args = action.optJSONObject("arguments");
                String skill = action.optString("skill", action.optString("name", "")).trim();
                if (skill.isEmpty() && args != null) skill = args.optString("skill", args.optString("name", "")).trim();
                if (skill.isEmpty()) return null;
            } else if (!"continue".equals(tool) && !"finish".equals(tool)) {
                return null;
            }
            return action;
        } catch (Exception ignored) { return null; }
    }

    private boolean looksLikeAgentActionPayload(String source) {
        if (source == null) return false;
        String value = source.trim();
        return (value.startsWith("{") || value.startsWith("```"))
                && (value.contains("tfm_action") || value.contains("\"tool\""));
    }

    private String agentCommand(JSONObject action){
        JSONObject args=action.optJSONObject("arguments");
        String command=action.optString("command","");
        if(command.isEmpty()&&args!=null)command=args.optString("command","");
        return command;
    }

    private void confirmAgentAction(String chatId, JSONObject action) {
        if (autoExecutionEnabled(chatId)) {
            executeAgentAction(chatId, action, true);
            return;
        }
        String tool = action.optString("tool", action.optString("name", "run_shell"));
        boolean installSkill = "install_skill".equals(tool);
        String command = installSkill ? action.optString("url", action.optString("source", "")) : agentCommand(action);
        String reason = action.optString("reason", "الوكيل يحتاج تنفيذ أداة حتى يكمل المهمة.");
        String risk = action.optString("risk", "راجع الإجراء قبل التنفيذ");
        JSONObject chat = findChat(chatId);
        if (chat == null) return;
        JSONObject session = AiSecureStore.findById(sessions, chat.optString("sessionId", ""));
        String fallback = session == null ? "~/" : session.optString("workdir", "~/");
        JSONObject args = action.optJSONObject("arguments");
        String workdir = action.optString("workdir", args == null ? fallback : args.optString("workdir", fallback));
        String message = reason + "\n\n" + (installSkill ? "رابط المهارة: " : "مستوى المخاطرة: " + risk + "\nمجلد التشغيل: " + workdir + "\n\n")
                + command + (installSkill ? "\n\nسيتم تنزيل تعليمات SKILL.md وحفظها محليًا، ولن يُشغّل أي كود من المهارة أثناء التثبيت." : "\n\nسيُنفذ الأمر داخل Termux بصلاحيات المستخدم الحالية.");
        new AlertDialog.Builder(this).setTitle(installSkill ? "تثبيت Agent Skill" : "موافقة على أداة الوكيل")
                .setMessage(message)
                .setPositiveButton(installSkill ? "تنصيب" : "موافقة وتشغيل", (d, w) -> executeAgentAction(chatId, action, false))
                .setNeutralButton(installSkill ? "نسخ الرابط" : "نسخ الأمر", (d, w) -> copyText(installSkill ? "Skill URL" : "AI command", command))
                .setNegativeButton("إلغاء", null).show();
    }

    private boolean autoExecutionEnabled() {
        return AiSecureStore.settings(this).getBoolean("agent_auto_execute", false);
    }

    private boolean autoExecutionEnabled(String chatId) {
        JSONObject chat = findChat(chatId);
        if (chat != null && chat.has("agent_auto_execute")) return chat.optBoolean("agent_auto_execute", false);
        return autoExecutionEnabled();
    }

    private void toggleChatAutoExecution() {
        JSONObject chat = current(); if (chat == null) return;
        boolean enabled = !autoExecutionEnabled(chat.optString("id", ""));
        new AlertDialog.Builder(this).setTitle("التنفيذ التلقائي لهذه الدردشة")
                .setMessage(enabled
                        ? "سيشغّل الوكيل أدوات Shell تلقائيًا في هذه الدردشة من دون تأكيد لكل أمر. الأوامر شديدة التدمير تبقى محجوبة. لا تفعّل هذا إلا إذا كنت تثق بالمهمة." 
                        : "ستعود هذه الدردشة إلى طلب موافقتك قبل تشغيل أدوات Shell.")
                .setPositiveButton(enabled ? "تفعيل" : "تعطيل", (d, w) -> {
                    try { chat.put("agent_auto_execute", enabled); saveChats(); }
                    catch (Exception ignored) { }
                    AgentWorkspace.persistChatOverride(this, chat.optString("id"), chat.optString("provider", "gemini"), chat.optString("sessionId", ""), Boolean.valueOf(enabled));
                    Toast.makeText(this, enabled ? "التنفيذ التلقائي مفعّل لهذه الدردشة" : "التنفيذ التلقائي متوقف لهذه الدردشة", Toast.LENGTH_LONG).show();
                    renderMessages();
                }).setNegativeButton("إلغاء", null).show();
    }

    private void executeAgentAction(String chatId, JSONObject action, boolean automatic) {
        JSONObject chat = findChat(chatId);
        if (chat == null || action == null) return;
        String tool = action.optString("tool", action.optString("name", "run_shell"));
        if (!"finish".equals(tool) && !"continue".equals(tool)) noProgressContinuations.put(chatId, 0);
        if (!"finish".equals(tool) && !"continue".equals(tool) && !AgentWorkspace.isToolEnabled(this, tool)) {
            appendChatMessage(chat, "assistant", "الأداة " + tool + " متوقفة حسب config.json؛ عدّل tools.enabled إذا تريد تفعيلها.");
            saveChats(); if (chatId.equals(activeChatId)) renderMessages(); return;
        }
        if (("activate_skill".equals(tool) || "use_skill".equals(tool) || "install_skill".equals(tool))
                && !AiSecureStore.settings(this).getBoolean("agent_skills_enabled", true)) {
            appendChatMessage(chat, "assistant", "المهارات متوقفة حسب skills.enabled في config.json.");
            saveChats(); if (chatId.equals(activeChatId)) renderMessages(); return;
        }
        if ("finish".equals(tool)) return;
        if ("continue".equals(tool)) {
            int stagnant = noProgressContinuations.containsKey(chatId) ? noProgressContinuations.get(chatId) : 0;
            if (stagnant >= 2) {
                cancelledAgentRuns.put(chatId, true);
                appendChatMessage(chat, "assistant", "أوقفت الوكيل لأنّه كرر طلب المتابعة بدون تنفيذ أداة أو جلب معلومة جديدة. ماكو حد عام لعدد الخطوات؛ هذا حاجز يمنع الدوران بلا تقدّم. أرسل طلب متابعة جديد حتى تبدأ دورة جديدة.");
                saveChats();
                if (chatId.equals(activeChatId)) renderMessages();
                if (sendButton != null) sendButton.setEnabled(true);
                busy = false;
                return;
            }
            noProgressContinuations.put(chatId, stagnant + 1);
            try {
                JSONArray messages = chat.optJSONArray("messages"); if (messages == null) messages = new JSONArray();
                String interim = action.optString("message", action.optString("status", action.optString("reason", ""))).trim();
                JSONObject control = msg("agent_control", "[Explicit continuation requested] " + interim
                        + "\nContinue the original task now. Do not repeat the progress report. Take a different useful action or finish honestly. A repeated continue without new tool output is a loop.");
                messages.put(control); while (messages.length() > 80) messages.remove(0); chat.put("messages", messages); saveChats();
            } catch (Exception ignored) { }
            if (!busy && !isAgentRunCancelled(chatId)) autoAnalyzeCommandResult(chatId);
            return;
        }
        noProgressContinuations.put(chatId, 0);
        if ("web_search".equals(tool)) {
            JSONObject searchArgs = action.optJSONObject("arguments");
            String query = action.optString("query", "").trim();
            if (query.isEmpty() && searchArgs != null) query = searchArgs.optString("query", searchArgs.optString("q", "")).trim();
            if (query.isEmpty()) { appendChatMessage(chat, "assistant", "أداة البحث تحتاج query غير فارغ."); saveChats(); if (chatId.equals(activeChatId)) renderMessages(); return; }
            runWebSearch(chatId, query); return;
        }
        if ("save_memory".equals(tool) || "remember".equals(tool)) { saveAgentMemory(chatId, action); return; }
        if ("activate_skill".equals(tool) || "use_skill".equals(tool)) {
            JSONObject args = action.optJSONObject("arguments");
            String skillName = action.optString("skill", action.optString("name", ""));
            if (skillName.isEmpty() && args != null) skillName = args.optString("skill", args.optString("name", ""));
            JSONObject skill = AgentSkillManager.findInstalled(this, skillName);
            if (skill == null) {
                appendChatMessage(chat, "assistant", "ما لقيت المهارة المثبتة /" + skillName + ". افتح إعدادات AI وثبّتها أولًا.");
                saveChats(); if (chatId.equals(activeChatId)) renderMessages(); return;
            }
            String content = skill.optString("content", "");
            if (content.length() > 12000) content = content.substring(0, 12000) + "\n[SKILL TRUNCATED FOR CONTEXT LIMIT]";
            try {
                JSONArray messages = chat.optJSONArray("messages"); if (messages == null) messages = new JSONArray();
                JSONObject result = msg("tool_output", content);
                result.put("tool_label", "Agent Skill /" + skill.optString("name", skillName));
                result.put("display_path", "skill /" + skill.optString("name", skillName));
                messages.put(result); while (messages.length() > 80) messages.remove(0);
                chat.put("messages", messages); saveChats(); if (chatId.equals(activeChatId)) renderMessages();
            } catch (Exception e) { appendChatMessage(chat, "assistant", "تعذر تحميل المهارة: " + e.getMessage()); saveChats(); }
            if (!busy) autoAnalyzeCommandResult(chatId);
            else Toast.makeText(this, "انقرأت المهارة؛ سيكمل الوكيل بعدها.", Toast.LENGTH_SHORT).show();
            return;
        }
        if ("install_skill".equals(tool)) {
            String url = action.optString("url", action.optString("source", ""));
            JSONObject args = action.optJSONObject("arguments");
            if (url.isEmpty() && args != null) url = args.optString("url", args.optString("source", ""));
            String name = action.optString("name", "");
            if (name.isEmpty() && args != null) name = args.optString("name", "");
            if (url.isEmpty() || !url.startsWith("https://")) {
                appendChatMessage(chat, "assistant", "ما قدرت أنصّب المهارة: أحتاج رابط HTTPS واضحًا لـ SKILL.md أو مجلد مهارة على GitHub.");
                saveChats(); if (chatId.equals(activeChatId)) renderMessages();
                return;
            }
            final String skillUrl = url, skillName = name;
            worker.execute(() -> {
                String outcome;
                try {
                    AgentSkillManager.InstallResult installed = AgentSkillManager.installFromUrl(this, skillUrl, skillName);
                    outcome = "نجح تثبيت المهارة /" + installed.name + " — " + installed.description + ". يمكن استدعاؤها بكتابة /" + installed.name + " ثم المهمة.";
                } catch (Exception e) { outcome = "فشل تثبيت المهارة من " + skillUrl + ": " + e.getMessage(); }
                final String result = outcome;
                runOnUiThread(() -> {
                    JSONObject live = findChat(chatId);
                    if (live == null) return;
                    appendChatMessage(live, "user", "[نتيجة أداة install_skill]\n" + result);
                    saveChats();
                    if (chatId.equals(activeChatId)) renderMessages();
                    if (!busy) autoAnalyzeCommandResult(chatId);
                    else Toast.makeText(this, "وصلت نتيجة تثبيت المهارة؛ أكمل التحليل بعد انتهاء الطلب الحالي.", Toast.LENGTH_LONG).show();
                });
            });
            return;
        }
        if (!"run_shell".equals(tool) && !"shell".equals(tool) && !"run_command".equals(tool)) {
            appendChatMessage(chat, "assistant", "الأداة غير مدعومة: " + tool + ". استخدم run_shell أو install_skill.");
            saveChats(); if (chatId.equals(activeChatId)) renderMessages();
            return;
        }
        String command = agentCommand(action);
        if (command.trim().isEmpty()) {
            appendChatMessage(chat, "assistant", "الوكيل رجّع أداة بدون أمر قابل للتنفيذ.");
            saveChats(); if (chatId.equals(activeChatId)) renderMessages();
            return;
        }
        if (automatic && unsafeForUnattendedExecution(command)) {
            appendChatMessage(chat, "assistant", "حاجز الأمان أوقف هذا الأمر في وضع التنفيذ التلقائي لأنه قد يمسح النظام أو يهيّئ قرصًا أو يوقف الجهاز. لم يُنفّذ شيء. راجع الأمر يدويًا من وضع الموافقة: " + command);
            saveChats(); if (chatId.equals(activeChatId)) renderMessages();
            return;
        }
        JSONObject args = action.optJSONObject("arguments");
        String sid = chat.optString("sessionId", "");
        JSONObject session = AiSecureStore.findById(sessions, sid);
        String fallback = session == null ? "~/" : session.optString("workdir", "~/");
        String workdir = action.optString("workdir", args == null ? fallback : args.optString("workdir", fallback));
        try {
            int request = TermuxCommandRunner.run(this, command, workdir, "TFM AI Agent tool", true);
            commandChats.put(request, chatId);
            agentCommandRequests.put(request, Boolean.TRUE);
            Toast.makeText(this, automatic ? "ينفّذ الوكيل الأداة تلقائيًا ثم سيقرأ النتيجة." : "اشتغلت الأداة؛ الوكيل سيقرأ المخرجات ويقترح الخطوة التالية.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            appendChatMessage(chat, "assistant", "تعذر تشغيل أداة Termux: " + e.getMessage());
            saveChats(); if (chatId.equals(activeChatId)) renderMessages();
        }
    }

    private boolean unsafeForUnattendedExecution(String command) {
        String lower = command == null ? "" : command.toLowerCase(java.util.Locale.ROOT).replace("\"", "").replace("'", "");
        return lower.matches("(?s).*\\brm\\s+.*(?:--recursive|-[a-z]*r[a-z]*)\\s+(?:/\\S*|~(?:/\\S*)?|\\$home(?:/\\S*)?|\\$prefix(?:/\\S*)?)(?:\\s|$|[;&|]).*")
                || lower.matches("(?s).*\\brm\\s+.*(?:--recursive|-[a-z]*r[a-z]*)\\s+(?:\\.|\\.\\.|\\*|\\./|\\./\\*)(?:\\s|$|[;&|]).*")
                || lower.contains("rm --recursive --force /")
                || lower.contains("rm --force --recursive /")
                || lower.matches("(?s).*\\bmkfs(?:\\.[a-z0-9]+)?\\b.*")
                || lower.matches("(?s).*\\bdd\\s+.*\\bof=/dev/.*")
                || lower.matches("(?s).*\\b(shutdown|reboot|poweroff)\\b.*")
                || lower.contains(":(){:|:&};:");
    }


    private String providerFor(String chatId){JSONObject c=findChat(chatId);return c==null?"gemini":c.optString("provider","gemini");}
    private void sendCurrent() {
        String userText = input.getText().toString().trim();
        if (userText.isEmpty()) return;
        if (busy) { Toast.makeText(this, "انتظر الرد الحالي أو افتح دردشة أخرى", Toast.LENGTH_SHORT).show(); return; }
        String expanded = AgentSkillManager.expandSlashCommand(this, userText);
        if (expanded == null) { Toast.makeText(this, "المهارة غير مثبتة. افتح إعدادات AI ثم Agent Skills وثبّت رابط SKILL.md أولًا.", Toast.LENGTH_LONG).show(); return; }
        JSONObject chat = current(); if (chat == null) return;
        String image = attachedImage; attachedImage = ""; autoScreenshotUsed = false;
        attachmentLabel.setText("ماكو صورة مرفقة"); attachmentLabel.setVisibility(View.GONE); attachmentLabel.setVisibility(View.GONE); input.setText("");
        String chatId = chat.optString("id");
        cancelledAgentRuns.put(chatId, false);
        noProgressContinuations.put(chatId, 0);
        appendChatMessage(chat, "user", userText);
        if (!expanded.equals(userText)) {
            try { JSONArray saved = chat.optJSONArray("messages"); JSONObject last = saved == null ? null : saved.optJSONObject(saved.length() - 1); if (last != null) last.put("expanded_prompt", expanded); } catch (Exception ignored) { }
        }
        String sessionId = chat.optString("sessionId", "");
        String enriched = withTerminalContext(expanded, sessionId);
        if (AgentWebSearch.shouldRequireSearch(userText)) enriched += "\n\n[MANDATORY WEB RESEARCH] The app will run web_search before this turn and append its returned sources. Use those sources, cite their URLs, and search again only if the evidence is missing or ambiguous. Do not answer from memory alone.";
        saveChats();
        final String requestText = enriched;
        final String imagePath = image;
        final String streamId = appendStreamingAssistant(chat);
        busy = true; sendButton.setEnabled(false);
        if (rateLabel != null) rateLabel.setText("يفكّر الموديل ويرسل الرد تدريجيًا…");

        syncAgentWorkspace(() -> {
            JSONObject liveChat = findChat(chatId);
            if (liveChat == null) { busy = false; if (sendButton != null) sendButton.setEnabled(true); return; }
            String providerId = liveChat.optString("provider", AiSecureStore.getActiveProvider(this));
            String model = AiSecureStore.getModel(this, providerId);
            String endpoint = AiSecureStore.getEndpoint(this, providerId);
            JSONArray history = apiHistory(liveChat);
            if (history.length() > 0) {
                JSONObject tail = history.optJSONObject(history.length() - 1);
                if (tail != null && "assistant".equals(tail.optString("role", "")) && tail.optString("text", "").trim().isEmpty()) history.remove(history.length() - 1);
            }
            for (int i = history.length() - 1; i >= 0; i--) {
                JSONObject turn = history.optJSONObject(i);
                if (turn != null && "user".equals(turn.optString("role", ""))) {
                    try { turn.put("text", requestText); } catch (Exception ignored) { }
                    break;
                }
            }
            final String finalProviderId = providerId, finalModel = model, finalEndpoint = endpoint;
            final boolean mandatorySearch = AgentWebSearch.shouldRequireSearch(userText);
            worker.execute(() -> {
                if (mandatorySearch) {
                    String research;
                    if (!AiSecureStore.settings(this).getBoolean("agent_web_search_enabled", true)) {
                        research = "Web search is disabled in ~/.TFM-setting/config.json; do not pretend a search was performed.";
                    } else {
                        try { research = AgentWebSearch.search(userText); }
                        catch (Exception searchError) { research = "Web search failed: " + searchError.getMessage(); }
                    }
                    try {
                        JSONObject lastRequest = history.optJSONObject(history.length() - 1);
                        String evidence = "[MANDATORY WEB SEARCH RESULTS — verify dates and source URLs]\n" + research;
                        if (lastRequest != null && "user".equals(lastRequest.optString("role", ""))) lastRequest.put("text", lastRequest.optString("text", "") + "\n\n" + evidence);
                        else history.put(new JSONObject().put("role", "user").put("text", evidence));
                    } catch (Exception ignored) { }
                    final String shownResearch = research;
                    runOnUiThread(() -> addAgentToolOutput(chatId, "Mandatory web search", shownResearch));
                }
                String response;
                try {
                    response = AiApiClient.sendStreaming(this, AiProvider.byId(finalProviderId), finalModel, finalEndpoint,
                            AiSecureStore.getKey(this, finalProviderId), history, imagePath, Math.max(128, Math.min(8192, AiSecureStore.settings(this).getInt("output_budget_tokens", 2048))), new AiApiClient.StreamListener() {
                        @Override public void onText(String fragment) { appendStreamDelta(chatId, streamId, false, fragment); }
                        @Override public void onThinking(String fragment) { appendStreamDelta(chatId, streamId, true, fragment); }
                    });
                } catch (Exception e) { response = "[تعذر إكمال الطلب]\n" + e.getMessage(); }
                final String result = response;
                runOnUiThread(() -> {
                    String visible = result.replace("[TFM_SCREENSHOT]", "").replace("[REQUEST_SCREENSHOT]", "").trim();
                    boolean emptyAnswer = visible.isEmpty() && !result.startsWith("[تعذر");
                    String streamedThinking = getStreamThinking(chatId, streamId);
                    boolean thinkingOnly = emptyAnswer && !streamedThinking.trim().isEmpty();
                    JSONObject next = extractAgentAction(visible);
                    if (emptyAnswer && !thinkingOnly) visible = "الموديل ما رجّع نصًا أو ملخص تفكير قابلًا للاستخدام. توقّف هذا الدور بدون إعادة تلقائية؛ أعد المحاولة أو راجع النموذج.";
                    if (next == null && looksLikeAgentActionPayload(visible)) {
                        visible = "أوقفت دورة الوكيل لأن الموديل رجّع أداة غير صالحة أو أمرًا فارغًا. ما نفّذت أي أمر فارغ وما راح أعيد نفس الطلب تلقائيًا. أعد المحاولة أو غيّر النموذج إذا تكررت المشكلة.";
                    }
                    try { JSONObject streamEntry = findStreamMessage(findChat(chatId), streamId); if (streamEntry != null && next != null) streamEntry.put("agent_action", next); } catch (Exception ignored) { }
                    visible = normalizeAgentActionForDisplay(visible, next);
                    finishStreamingAssistant(chatId, streamId, visible);
                    boolean requestedScreenshot = result.contains("[TFM_SCREENSHOT]") || result.contains("[REQUEST_SCREENSHOT]");
                    busy = false; if (sendButton != null) sendButton.setEnabled(true); updateHeader();
                    if (requestedScreenshot && !autoScreenshotUsed) { autoScreenshotUsed = true; beginAgentScreenshotCapture(chatId, finalProviderId, finalModel, finalEndpoint); return; }
                    if (isAgentRunCancelled(chatId)) return;
                    if (thinkingOnly) { queueAgentContinuation(chatId, true); return; }
                    if (emptyAnswer) { noProgressContinuations.put(chatId, 0); return; }
                    if (next == null) noProgressContinuations.put(chatId, 0);
                    if (next != null && !"finish".equals(next.optString("tool", ""))
                            && (isSkillActivation(next) || isInternalAgentAction(next) || autoExecutionEnabled(chatId))) executeAgentAction(chatId, next, true);
                    // A plain-text response is a completed model turn, not an implicit request to call the API again.
                    // Continuation must be explicit via tfm_action.continue; this prevents runaway loops.
                });
            });
        });
    }

    private String appendStreamingAssistant(JSONObject chat) {
        String id=Long.toString(System.nanoTime());
        try {
            JSONArray messages=chat.optJSONArray("messages"); if(messages==null)messages=new JSONArray();
            JSONObject entry=msg("assistant",""); entry.put("stream_id",id); entry.put("streaming",true); entry.put("thinking","");
            messages.put(entry); while(messages.length()>80)messages.remove(0); chat.put("messages",messages); saveChats();
        } catch(Exception ignored) { }
        if(chat.optString("id").equals(activeChatId))renderMessages();
        return id;
    }

    private JSONObject findStreamMessage(JSONObject chat,String streamId) {
        if(chat==null)return null; JSONArray messages=chat.optJSONArray("messages"); if(messages==null)return null;
        for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(m!=null && streamId.equals(m.optString("stream_id","")))return m;}
        return null;
    }

    private void appendStreamDelta(String chatId, String streamId, boolean thinking, String fragment) {
        if (fragment == null || fragment.isEmpty()) return;
        handler.post(() -> {
            JSONObject chat = findChat(chatId);
            JSONObject entry = findStreamMessage(chat, streamId);
            if (entry == null) return;
            String key = thinking ? "thinking" : "text";
            try { entry.put(key, entry.optString(key, "") + fragment); } catch (Exception ignored) { }
            String accumulated = entry.optString(key, "");
            StreamViews views = activeStreams.get(streamId);
            if (views != null) {
                if (thinking) {
                    views.thinking.setText(accumulated);
                    views.toggle.setVisibility(View.VISIBLE);
                    views.toggle.setText("Thinking · " + accumulated.length() + " chars");
                } else {
                    boolean toolJson = looksLikeAgentActionPayload(accumulated);
                    views.answer.setVisibility(accumulated.isEmpty() || toolJson ? View.GONE : View.VISIBLE);
                    views.answer.setText(toolJson ? "جاري تجهيز استدعاء الأداة…" : accumulated);
                }
            }
            if (chatId.equals(activeChatId)) {
                if (rateLabel != null) rateLabel.setText(thinking ? "Thinking · ملخص التفكير يصل مباشرة…" : "الرد يصل مباشرة…");
                if (messagesScroll != null) messagesScroll.post(() -> messagesScroll.fullScroll(View.FOCUS_DOWN));
            }
        });
    }

    private String getStreamThinking(String chatId, String streamId) {
        JSONObject entry = findStreamMessage(findChat(chatId), streamId);
        return entry == null ? "" : entry.optString("thinking", "");
    }

    private void finishStreamingAssistant(String chatId,String streamId,String result) {
        JSONObject chat=findChat(chatId); JSONObject entry=findStreamMessage(chat,streamId);
        if(entry!=null)try{entry.put("text",result==null?"":result);entry.put("streaming",false);}catch(Exception ignored){}
        saveChats(); if(chatId.equals(activeChatId))renderMessages();
    }

    private boolean isSkillActivation(JSONObject action) {
        if(action==null)return false;String tool=action.optString("tool",action.optString("name",""));return "activate_skill".equals(tool)||"use_skill".equals(tool);
    }

    private void appendChatMessage(JSONObject c,String role,String text){try{JSONArray a=c.optJSONArray("messages");if(a==null)a=new JSONArray();a.put(msg(role,text));while(a.length()>80){JSONArray n=new JSONArray();for(int i=1;i<a.length();i++)n.put(a.opt(i));a=n;}c.put("messages",a);}catch(Exception ignored){}}
    private JSONArray apiHistory(JSONObject c){JSONArray src=c.optJSONArray("messages"),out=new JSONArray();if(src==null)return out;int start=Math.max(0,src.length()-32);for(int i=start;i<src.length();i++){JSONObject m=src.optJSONObject(i);if(m==null)continue;String role=m.optString("role","user");if("agent_control".equals(role))role="user";if("terminal".equals(role))role="user";if("tool_output".equals(role)){String raw=m.optString("text","");if(raw.length()>16000)raw=raw.substring(raw.length()-16000);out.put(msg("user","[RAW TOOL OUTPUT — "+m.optString("tool_label","Tool result")+"]\n"+raw+"\n[END TOOL OUTPUT]"));continue;}if(!"assistant".equals(role)&&!"user".equals(role))continue;String historyText=("user".equals(role)&&m.has("expanded_prompt"))?m.optString("expanded_prompt",m.optString("text","")):m.optString("text","");out.put(msg(role,historyText));}return out;}
    private String withTerminalContext(String userText,String sid){JSONObject s=AiSecureStore.findById(sessions,sid);if(s==null)return userText;String out=s.optString("output","");if(out.isEmpty())return userText;out=out.length()>10000?out.substring(out.length()-10000):out;return userText+"\n\n[Terminal session: "+s.optString("name","Terminal")+"; workdir="+s.optString("workdir","~/")+"]\n[Recent terminal output]\n"+out+"\n[حلّل المخرجات عند صلتها بالسؤال، ولا تفترض أن آخر أمر نجح ما لم تظهر النتيجة].";}
    private void broadcastPrompt(){String prompt=input.getText().toString().trim();if(prompt.isEmpty())return;if(busy){Toast.makeText(this,"انتظر حتى ينتهي الطلب الحالي",Toast.LENGTH_SHORT).show();return;}new AlertDialog.Builder(this).setTitle("إرسال الأمر إلى كل الدردشات").setMessage("سيُرسل نفس النص إلى كل دردشة محفوظة لها مفتاح API متاح. قد يستهلك ذلك طلبًا من كل مزوّد. عدد الدردشات: "+chats.length()+". متابعة؟\n\n"+prompt).setPositiveButton("إرسال للجميع",(d,w)->doBroadcast(prompt)).setNegativeButton("إلغاء",null).show();}
    private void doBroadcast(String prompt){input.setText("");JSONArray snapshot=AiSecureStore.cloneArray(chats);String active=activeChatId;busy=true;sendButton.setEnabled(false);rateLabel.setText("إرسال متتابع إلى الدردشات…");worker.execute(()->{List<String> summaries=new ArrayList<>();for(int i=0;i<snapshot.length();i++){JSONObject c=snapshot.optJSONObject(i);if(c==null)continue;String pid=c.optString("provider",AiSecureStore.getActiveProvider(this)),chatId=c.optString("id");try{String key=AiSecureStore.getKey(this,pid);if(key.isEmpty()){summaries.add(c.optString("name")+": تخطّيت لعدم وجود مفتاح");continue;}appendChatMessage(c,"user",prompt);String model=AiSecureStore.getModel(this,pid),endpoint=AiSecureStore.getEndpoint(this,pid);JSONArray history=apiHistory(c);String sid=c.optString("sessionId","");if(history.length()>0)history.put(history.length()-1,new JSONObject().put("role","user").put("text",withTerminalContext(prompt,sid)));String answer=AiApiClient.send(this,AiProvider.byId(pid),model,endpoint,key,history,"",2048);appendChatMessage(c,"assistant",answer);summaries.add(c.optString("name")+": وصل رد");int gap=AiSecureStore.settings(this).getInt("rate_gap_seconds",3);if(gap>0)Thread.sleep(gap*1000L);}catch(Exception e){appendChatMessage(c,"assistant","[خطأ أثناء البث]\n"+e.getMessage());summaries.add(c.optString("name")+": "+e.getMessage());}saveChats();}runOnUiThread(()->{busy=false;sendButton.setEnabled(true);rebuildChatSpinner();activeChatId=active;chatsSpinner.setSelection(indexOfChat(activeChatId));updateHeader();renderMessages();new AlertDialog.Builder(this).setTitle("نتيجة الإرسال الجماعي").setMessage(android.text.TextUtils.join("\n",summaries)).setPositiveButton("حسنًا",null).show();});});}
    private void confirmSuggestedCommand(String chatId,String code){JSONObject c=findChat(chatId);if(c==null)return;String sid=c.optString("sessionId","");JSONObject s=AiSecureStore.findById(sessions,sid);String wd=s==null?"~/":s.optString("workdir","~/");new AlertDialog.Builder(this).setTitle("تأكيد أمر اقترحه AI").setMessage("راجع الأمر بالكامل قبل تشغيله داخل Termux. أي أمر shell قد يعدّل ملفاتك أو يثبت حزمًا.\n\nمجلد التشغيل: "+wd+"\n\n"+code).setPositiveButton("تنفيذ داخل Termux",(d,w)->{try{int id=TermuxCommandRunner.run(this,code,wd,"AI approved command",true);commandChats.put(id,chatId);Toast.makeText(this,"تم إرسال الأمر إلى Termux؛ ستُضاف المخرجات إلى الدردشة",Toast.LENGTH_LONG).show();}catch(Exception e){alert(e.getMessage());}}).setNeutralButton("نسخ الأمر",(d,w)->{ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("AI command",code));}).setNegativeButton("إلغاء",null).show();}
    private String firstCodeBlock(String s){Matcher m=Pattern.compile("```(?:[a-zA-Z0-9_+.-]+)?\\s*([\\s\\S]*?)```").matcher(s==null?"":s);return m.find()?m.group(1).trim():"";}
    private void captureOrAttach() {
        if (!AiSecureStore.isCaptureActive(this)) {
            startActivity(new Intent(this, ScreenCaptureActivity.class));
            Toast.makeText(this, "اسمح بالتقاط الشاشة من حوار أندرويد. بعد ذلك استخدم زر اللقطة في إشعار TFM ثم ارجع هنا.", Toast.LENGTH_LONG).show();
            return;
        }
        String last = AiSecureStore.lastScreenshot(this); long now = System.currentTimeMillis(), stamp = AiSecureStore.lastScreenshotAt(this);
        if (!last.isEmpty() && new File(last).isFile() && stamp > AiSecureStore.lastScreenshotUsedAt(this) && now - stamp < 120000) {
            attachedImage = last; AiSecureStore.markLastScreenshotUsed(this);
            attachmentLabel.setText("لقطة حديثة مرفقة · سترسل مع الرسالة التالية"); attachmentLabel.setVisibility(View.VISIBLE);
            Toast.makeText(this, "أرفقنا آخر لقطة؛ لن نلتقط إطارًا جديدًا", Toast.LENGTH_SHORT).show(); return;
        }
        long before = stamp; attachedImage = "";
        try {
            startService(new Intent(this, ScreenCaptureService.class).setAction(ScreenCaptureService.ACTION_CAPTURE));
            Toast.makeText(this, "طلبنا لقطة للشاشة الحالية. انتظر قليلًا.", Toast.LENGTH_SHORT).show();
            handler.postDelayed(() -> {
                String path = AiSecureStore.lastScreenshot(this); long after = AiSecureStore.lastScreenshotAt(this);
                if (!path.isEmpty() && new File(path).isFile() && after > before) {
                    attachedImage = path; AiSecureStore.markLastScreenshotUsed(this);
                    attachmentLabel.setText("لقطة مرفقة · سترسل مع الرسالة التالية"); attachmentLabel.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "تم إرفاق اللقطة الجديدة", Toast.LENGTH_SHORT).show();
                } else Toast.makeText(this, "ما وصل إطار جديد؛ اعرض الشاشة المطلوبة واضغط زر اللقطة في إشعار TFM.", Toast.LENGTH_LONG).show();
            }, 1900);
        } catch (Exception e) { alert(e.getMessage()); }
    }
    private void beginAgentScreenshotCapture(String chatId,String providerId,String model,String endpoint){
        busy=false;sendButton.setEnabled(true);updateHeader();renderMessages();
        AiSecureStore.savePendingScreenshot(this,chatId,providerId,model,endpoint,System.currentTimeMillis());
        boolean active=AiSecureStore.isCaptureActive(this);
        String message=active
                ? "المساعد يطلب لقطة حديثة من شاشة Termux/TUI. إذا وافقت، سنفتح Termux ونلتقط الشاشة تلقائيًا بعد نحو 4 ثوانٍ. تأكد من إخفاء كلمات السر والرموز الخاصة؛ سترسل اللقطة إلى مزوّد AI المحدد."
                : "المساعد يطلب لقطة حديثة من Termux/TUI. إذا وافقت، ستظهر نافذة أندرويد لمشاركة الشاشة. بعد الموافقة سنفتح Termux ونلتقط الشاشة تلقائيًا بعد نحو 4 ثوانٍ. تأكد من إخفاء كلمات السر والرموز الخاصة؛ سترسل اللقطة إلى مزوّد AI المحدد.";
        new AlertDialog.Builder(this).setTitle("AI يطلب لقطة شاشة")
                .setMessage(message)
                .setPositiveButton(active?"فتح Termux والتقاط":"السماح والتقاط",(d,w)->{if(active)openTermuxForAgentCapture();else startActivity(new Intent(this,ScreenCaptureActivity.class));})
                .setNegativeButton("لاحقًا",(d,w)->AiSecureStore.clearPendingScreenshot(this)).show();
    }
    private void openTermuxForAgentCapture(){
        if(!openTermux()){AiSecureStore.clearPendingScreenshot(this);return;}
        handler.postDelayed(()->requestPendingScreenshotCapture(),3500);
    }
    private void requestPendingScreenshotCapture(){
        if(!AiSecureStore.hasPendingScreenshot(this)||!AiSecureStore.isCaptureActive(this))return;
        try{startService(new Intent(this,ScreenCaptureService.class).setAction(ScreenCaptureService.ACTION_CAPTURE));}
        catch(Exception e){Toast.makeText(this,"تعذر أخذ لقطة الشاشة: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private boolean openTermux(){try{Intent launch=getPackageManager().getLaunchIntentForPackage("com.termux");if(launch==null){alert("Termux غير مثبت.");return false;}launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(launch);return true;}catch(Exception e){alert("تعذر فتح Termux: "+e.getMessage());return false;}}

    private void autoAnalyzeCommandResult(String chatId) {
        if (isAgentRunCancelled(chatId)) return;
        JSONObject chat = findChat(chatId); if (chat == null) return;
        final String streamId = appendStreamingAssistant(chat);
        busy = true; if (sendButton != null) sendButton.setEnabled(false);
        if (rateLabel != null) rateLabel.setText("الوكيل يراجع النتيجة ويختار الخطوة التالية…");
        syncAgentWorkspace(() -> {
            JSONObject liveChat = findChat(chatId);
            if (liveChat == null || isAgentRunCancelled(chatId)) { busy = false; if (sendButton != null) sendButton.setEnabled(true); return; }
            String providerId = liveChat.optString("provider", AiSecureStore.getActiveProvider(this));
            String model = AiSecureStore.getModel(this, providerId), endpoint = AiSecureStore.getEndpoint(this, providerId);
            JSONArray history = apiHistory(liveChat);
            if (history.length() > 0) {
                JSONObject tail = history.optJSONObject(history.length() - 1);
                if (tail != null && "assistant".equals(tail.optString("role", "")) && tail.optString("text", "").trim().isEmpty()) history.remove(history.length() - 1);
            }
            if (history.length() == 0) { busy = false; if (sendButton != null) sendButton.setEnabled(true); return; }
            try { JSONObject last = history.optJSONObject(history.length() - 1); if (last != null) { String value = last.optString("text", ""); if (value.length() > 12000) last.put("text", value.substring(value.length() - 12000)); } } catch (Exception ignored) { }
            final String finalProviderId = providerId, finalModel = model, finalEndpoint = endpoint;
            worker.execute(() -> {
                String answer;
                try {
                    long lastCall = AiSecureStore.settings(this).getLong("rate_last_" + finalProviderId, 0);
                    int gap = Math.max(0, AiSecureStore.settings(this).getInt("rate_gap_seconds", 3));
                    long wait = gap * 1000L - (System.currentTimeMillis() - lastCall);
                    if (wait > 30000L) throw new Exception("الفاصل المحلي المتبقي أطول من 30 ثانية؛ أرسل متابعة لاحقًا.");
                    if (wait > 0) Thread.sleep(wait);
                    answer = AiApiClient.sendStreaming(this, AiProvider.byId(finalProviderId), finalModel, finalEndpoint,
                            AiSecureStore.getKey(this, finalProviderId), history, "", Math.max(128, Math.min(8192, AiSecureStore.settings(this).getInt("output_budget_tokens", 2048))), new AiApiClient.StreamListener() {
                        @Override public void onText(String fragment) { appendStreamDelta(chatId, streamId, false, fragment); }
                        @Override public void onThinking(String fragment) { appendStreamDelta(chatId, streamId, true, fragment); }
                    });
                } catch (Exception e) { answer = "[لم يكتمل تحليل مخرجات Termux]\n" + e.getMessage(); }
                final String result = answer;
                runOnUiThread(() -> {
                    String visible = result == null ? "" : result.trim();
                    boolean emptyAnswer = visible.isEmpty() && (result == null || !result.startsWith("[لم يكتمل تحليل"));
                    String streamedThinking = getStreamThinking(chatId, streamId);
                    boolean thinkingOnly = emptyAnswer && !streamedThinking.trim().isEmpty();
                    JSONObject next = extractAgentAction(visible);
                    if (emptyAnswer && !thinkingOnly) visible = "الموديل ما رجّع نصًا أو ملخص تفكير قابلًا للاستخدام. توقّف هذا الدور بدون إعادة تلقائية؛ أرسل متابعة أو راجع النموذج.";
                    if (next == null && looksLikeAgentActionPayload(visible)) {
                        visible = "أوقفت دورة الوكيل لأن الموديل رجّع أداة غير صالحة أو أمرًا فارغًا. ما نفّذت أي أمر فارغ وما راح أعيد نفس الطلب تلقائيًا. أعد المحاولة أو غيّر النموذج إذا تكررت المشكلة.";
                    }
                    try { JSONObject streamEntry = findStreamMessage(findChat(chatId), streamId); if (streamEntry != null && next != null) streamEntry.put("agent_action", next); } catch (Exception ignored) { }
                    visible = normalizeAgentActionForDisplay(visible, next);
                    finishStreamingAssistant(chatId, streamId, visible);
                    busy = false; if (sendButton != null) sendButton.setEnabled(true); updateHeader();
                    if (isAgentRunCancelled(chatId)) return;
                    if (thinkingOnly) { queueAgentContinuation(chatId, true); return; }
                    if (emptyAnswer) { noProgressContinuations.put(chatId, 0); return; }
                    if (next == null) noProgressContinuations.put(chatId, 0);
                    if (next != null && !"finish".equals(next.optString("tool", ""))
                            && (isSkillActivation(next) || isInternalAgentAction(next) || autoExecutionEnabled(chatId))) executeAgentAction(chatId, next, true);
                    // Do not blindly retry a natural-language or malformed response. Explicit continue is required.
                });
            });
        });
    }

    private void maybeSubmitPendingScreenshot(){
        if(!AiSecureStore.hasPendingScreenshot(this))return;
        long since=AiSecureStore.pendingScreenshotSince(this),stamp=AiSecureStore.lastScreenshotAt(this),now=System.currentTimeMillis();
        if(now-since>5L*60L*1000L){AiSecureStore.clearPendingScreenshot(this);return;}
        if(!AiSecureStore.isCaptureActive(this)||stamp<=since||stamp<=AiSecureStore.lastScreenshotUsedAt(this))return;
        String image=AiSecureStore.lastScreenshot(this);if(image.isEmpty()||!new File(image).isFile())return;
        String chatId=AiSecureStore.pendingScreenshotValue(this,"chat"),providerId=AiSecureStore.pendingScreenshotValue(this,"provider"),model=AiSecureStore.pendingScreenshotValue(this,"model"),endpoint=AiSecureStore.pendingScreenshotValue(this,"endpoint");
        AiSecureStore.clearPendingScreenshot(this);AiSecureStore.markLastScreenshotUsed(this);
        submitScreenshotToAgent(chatId,providerId,model,endpoint,image);
    }
    private void submitScreenshotToAgent(String chatId,String providerId,String model,String endpoint,String image){
        JSONObject chat=findChat(chatId); if(chat==null)return;
        appendChatMessage(chat,"user","[لقطة الشاشة التي طلبتها مرفقة الآن. حلّل ما يظهر فيها، خاصة مخرجات الطرفية أو TUI.]"); saveChats();
        JSONArray history=apiHistory(chat);
        final String streamId=appendStreamingAssistant(chat);
        renderMessages(); busy=true; if(sendButton!=null)sendButton.setEnabled(false); rateLabel.setText("إرسال لقطة الشاشة وتحليلها تدريجيًا…");
        worker.execute(()->{
            String answer;
            try{
                answer=AiApiClient.sendStreaming(this,AiProvider.byId(providerId),model,endpoint,AiSecureStore.getKey(this,providerId),history,image,2048,new AiApiClient.StreamListener(){
                    @Override public void onText(String fragment){appendStreamDelta(chatId,streamId,false,fragment);}
                    @Override public void onThinking(String fragment){appendStreamDelta(chatId,streamId,true,fragment);}
                });
            }catch(Exception e){answer="[تعذر تحليل لقطة الشاشة]\n"+e.getMessage();}
            final String result=answer;
            runOnUiThread(()->{
                String visible=result.replace("[TFM_SCREENSHOT]","").replace("[REQUEST_SCREENSHOT]","").trim();
                if(visible.isEmpty()&&!result.startsWith("[تعذر"))visible="وصلت الصورة لكن المزوّد لم يرجع نص إجابة.";
                finishStreamingAssistant(chatId,streamId,visible); busy=false; if(sendButton!=null)sendButton.setEnabled(true); updateHeader();
                if(result.contains("[TFM_SCREENSHOT]")||result.contains("[REQUEST_SCREENSHOT]"))Toast.makeText(this,"تجاهلنا طلب لقطة إضافية في نفس الدور لمنع الالتقاط المتكرر.",Toast.LENGTH_LONG).show();
            });
        });
    }

    private void showAgentMenu() {
        String[] items = {
                "تبديل الدردشة", "دردشة جديدة", "المزوّد والنموذج لهذه الدردشة", "جلسة Termux المرتبطة",
                "إعدادات الدردشة الحالية", "إعدادات المزوّدين وكل الجلسات", "إدارة جلسات Terminal و TUI",
                "أوامر Terminal سريعة", "إرسال النص إلى كل الدردشات", "فتح تطبيق Termux",
                "لقطة شاشة / إرفاق صورة", "حذف آخر لقطة", "إيقاف دورة الوكيل بعد العملية الحالية", "إيقاف مشاركة الشاشة"
        };
        new AlertDialog.Builder(this).setTitle("خيارات الوكيل").setItems(items, (d, w) -> {
            if (w == 0) chooseAgentChat();
            else if (w == 1) { newChat(true); updateHeader(); renderMessages(); }
            else if (w == 2) chooseProviderForChat();
            else if (w == 3) chooseTerminalSessionForChat();
            else if (w == 4) chatMenu();
            else if (w == 5) startActivity(new Intent(this, AiSettingsActivity.class));
            else if (w == 6) startActivity(new Intent(this, TerminalSessionsActivity.class));
            else if (w == 7) showTerminalPopup();
            else if (w == 8) broadcastPrompt();
            else if (w == 9) openTermux();
            else if (w == 10) captureOrAttach();
            else if (w == 11) { AiSecureStore.clearLastScreenshot(this); attachedImage = ""; attachmentLabel.setText("ماكو صورة مرفقة"); attachmentLabel.setVisibility(View.GONE); Toast.makeText(this, "تم حذف آخر لقطة", Toast.LENGTH_SHORT).show(); }
            else if (w == 12) stopAgentRun();
            else { try { startService(new Intent(this, ScreenCaptureService.class).setAction(ScreenCaptureService.ACTION_STOP)); } catch (Exception ignored) { }
                AiSecureStore.setCaptureActive(this, false); Toast.makeText(this, "تم طلب إيقاف مشاركة الشاشة", Toast.LENGTH_SHORT).show(); }
        }).show();
    }

    private void showTerminalPopup(){
        sessions=AiSecureStore.loadArray(this,AiSecureStore.SESSIONS,"items");if(sessions.length()==0){startActivity(new Intent(this,TerminalSessionsActivity.class));return;}
        android.app.Dialog dialog=new android.app.Dialog(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),dp(10),dp(12),dp(12));box.setBackground(round(BG,16,DIVIDER));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("Terminal popup",17,TEXT,true);header.addView(title,new LinearLayout.LayoutParams(0,dp(38),1));Button close=button("إغلاق",PANEL2,TEXT);header.addView(close,new LinearLayout.LayoutParams(dp(64),dp(36)));close.setOnClickListener(v->dialog.dismiss());box.addView(header);
        Spinner sessionPick=new Spinner(this);List<String> sessionLabels=new ArrayList<>();for(int i=0;i<sessions.length();i++){JSONObject o=sessions.optJSONObject(i);sessionLabels.add(o.optString("name","Terminal")+" · "+o.optString("workdir","~/"));}sessionPick.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,sessionLabels));box.addView(sessionPick,match());
        EditText cmd= new EditText(this);cmd.setHint("أمر Shell متعدد الأسطر");cmd.setTextColor(TEXT);cmd.setHintTextColor(MUTED);cmd.setTypeface(Typeface.MONOSPACE);cmd.setGravity(Gravity.TOP|Gravity.START);cmd.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);cmd.setMinLines(2);cmd.setMaxLines(5);cmd.setPadding(dp(9),dp(6),dp(9),dp(6));cmd.setBackground(round(PANEL2,10,DIVIDER));box.addView(cmd,new LinearLayout.LayoutParams(-1,dp(86)));
        TextView out=text("Ready. Output appears here.",11,TEXT,false);out.setTypeface(Typeface.MONOSPACE);out.setTextIsSelectable(true);out.setGravity(Gravity.TOP|Gravity.START);out.setPadding(dp(8),dp(8),dp(8),dp(8));out.setBackground(round(PANEL,9,DIVIDER));ScrollView scroll=new ScrollView(this);scroll.addView(out);LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,0,1);sl.topMargin=dp(7);sl.bottomMargin=dp(7);box.addView(scroll,sl);
        LinearLayout actions=new LinearLayout(this);Button run=button("تنفيذ وإظهار المخرجات",PANEL2,ACCENT);Button tui=button("TUI في Termux",PANEL2,TEXT);Button manage=button("كل الجلسات",PANEL,TEXT);actions.addView(run,new LinearLayout.LayoutParams(0,dp(42),1));LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,dp(42),1);tp.leftMargin=dp(4);actions.addView(tui,tp);box.addView(actions);manage.setOnClickListener(v->{dialog.dismiss();startActivity(new Intent(this,TerminalSessionsActivity.class));});box.addView(manage,match());
        run.setOnClickListener(v->{String command=cmd.getText().toString().trim();if(command.isEmpty())return;int pos=sessionPick.getSelectedItemPosition();JSONObject session=sessions.optJSONObject(Math.max(0,Math.min(pos,sessions.length()-1)));String sid=session.optString("id"),wd=session.optString("workdir","~/");new AlertDialog.Builder(this).setTitle("تأكيد تشغيل الأمر").setMessage(command).setPositiveButton("تنفيذ",(d,w)->{try{int id=TermuxCommandRunner.run(this,command,wd,"TFM popup terminal",true);popupCommands.put(id,new PopupCommand(out,sid));out.append("\n$ "+command+"\n[قيد التنفيذ]\n");}catch(Exception e){alert(e.getMessage());}}).setNegativeButton("إلغاء",null).show();});
        tui.setOnClickListener(v->{String command=cmd.getText().toString().trim();if(command.isEmpty())command="exec bash -l";int pos=sessionPick.getSelectedItemPosition();JSONObject session=sessions.optJSONObject(Math.max(0,Math.min(pos,sessions.length()-1)));try{TermuxCommandRunner.openTui(this,command,session.optString("workdir","~/"),"TFM popup TUI");out.append("\n[فتحنا الأمر داخل Termux: "+command+"]\n");}catch(Exception e){alert(e.getMessage());}});
        dialog.setContentView(box);android.view.Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout((int)(getResources().getDisplayMetrics().widthPixels*0.94), (int)(getResources().getDisplayMetrics().heightPixels*0.80));}dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*0.94),(int)(getResources().getDisplayMetrics().heightPixels*0.80));
    }
    private void appendSessionOutput(String sid,String output){JSONArray arr=AiSecureStore.loadArray(this,AiSecureStore.SESSIONS,"items");JSONObject session=AiSecureStore.findById(arr,sid);if(session==null)return;String old=session.optString("output","");if(old.length()>20000)old=old.substring(old.length()-15000);try{session.put("output",old+output);AiSecureStore.saveArray(this,AiSecureStore.SESSIONS,"items",arr);}catch(Exception ignored){}}
    private void chatMenu(){String[] actions={"إعادة تسمية الدردشة","حذف الدردشة","إعدادات الذكاء الاصطناعي","إدارة جلسات Terminal","تبديل وضع التنفيذ التلقائي لهذه الدردشة"};new AlertDialog.Builder(this).setTitle("الدردشة الحالية").setItems(actions,(d,w)->{if(w==0)renameChat();if(w==1)deleteChat();if(w==2)startActivity(new Intent(this,AiSettingsActivity.class));if(w==3)startActivity(new Intent(this,TerminalSessionsActivity.class));if(w==4)toggleChatAutoExecution();}).show();}
    private void renameChat(){JSONObject c=current();if(c==null)return;EditText e=new EditText(this);e.setText(c.optString("name"));new AlertDialog.Builder(this).setTitle("اسم الدردشة").setView(e).setPositiveButton("حفظ",(d,w)->{try{c.put("name",e.getText().toString().trim().isEmpty()?"AI Chat":e.getText().toString().trim());saveChats();rebuildChatSpinner();chatsSpinner.setSelection(indexOfChat(activeChatId));}catch(Exception ignored){}}).setNegativeButton("إلغاء",null).show();}
    private void deleteChat(){if(chats.length()<=1){new AlertDialog.Builder(this).setMessage("احتفظ بدردشة واحدة على الأقل.").setPositiveButton("حسنًا",null).show();return;}new AlertDialog.Builder(this).setTitle("حذف الدردشة؟").setMessage("سيُحذف سجل هذه الدردشة المحلي فقط.").setPositiveButton("حذف",(d,w)->{JSONArray n=new JSONArray();for(int i=0;i<chats.length();i++){JSONObject c=chats.optJSONObject(i);if(c!=null&&!activeChatId.equals(c.optString("id")))n.put(c);}chats=n;activeChatId=chats.optJSONObject(0).optString("id");saveChats();rebuildChatSpinner();updateHeader();renderMessages();}).setNegativeButton("إلغاء",null).show();}
    private List<String> chatNames(){List<String> a=new ArrayList<>();for(int i=0;i<chats.length();i++){JSONObject c=chats.optJSONObject(i);a.add(c==null?"AI Chat":c.optString("name","AI Chat")+" · "+AiProvider.byId(c.optString("provider","gemini")).name);}return a;}
    private int indexOfChat(String id){for(int i=0;i<chats.length();i++)if(id.equals(chats.optJSONObject(i).optString("id")))return i;return 0;}
    private void rebuildChatSpinner(){chatsSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,chatNames()));chatsSpinner.setSelection(indexOfChat(activeChatId));}
    private List<String> terminalNames(){List<String> a=new ArrayList<>();if(sessions.length()==0)a.add("ماكو جلسات — افتح Terminal");for(int i=0;i<sessions.length();i++){JSONObject s=sessions.optJSONObject(i);a.add(s==null?"Terminal":s.optString("name","Terminal")+" · "+s.optString("workdir","~/"));}return a;}
    private String sessionIdAt(int p){if(p<0||p>=sessions.length())return "";return sessions.optJSONObject(p).optString("id","");}
    private int indexOfSession(String id){if(id.isEmpty())return 0;for(int i=0;i<sessions.length();i++)if(id.equals(sessions.optJSONObject(i).optString("id")))return i;return 0;}
    @Override protected void onResume(){super.onResume();String currentTheme=UiTheme.getName(this);if(lastThemeName!=null&&!lastThemeName.equals(currentTheme)){recreate();return;}if(providerSpinner!=null){sessions=AiSecureStore.loadArray(this,AiSecureStore.SESSIONS,"items");updatingSpinners=true;terminalSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,terminalNames()));updatingSpinners=false;updateHeader();maybeSubmitPendingScreenshot();}}
    private TextView text(String s,int size,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(c);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private Button button(String s,int bg,int fg){Button b=new Button(this);b.setText(s);b.setTextColor(fg);b.setTextSize(12);b.setAllCaps(false);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(5),dp(4),dp(5),dp(4));b.setBackground(round(bg,10,Color.TRANSPARENT));return b;}
    private GradientDrawable round(int c,int r,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));if(stroke!=Color.TRANSPARENT)d.setStroke(dp(1),stroke);return d;}
    private LinearLayout.LayoutParams match(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.bottomMargin=dp(4);return p;}
    private void alert(String s){new AlertDialog.Builder(this).setMessage(s).setPositiveButton("حسنًا",null).show();}
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}

    private void chooseAgentChat() {
        String[] names = chatNames().toArray(new String[0]);
        if (names.length == 0) { newChat(true); return; }
        new AlertDialog.Builder(this).setTitle("اختيار الدردشة").setItems(names, (d, which) -> {
            JSONObject chat = chats.optJSONObject(which); if (chat == null) return;
            activeChatId = chat.optString("id", ""); saveChats(); updateHeader(); renderMessages();
        }).show();
    }

    private void chooseProviderForChat() {
        List<AiProvider> providers = AiProvider.all(); String[] labels = new String[providers.size()];
        for (int i = 0; i < providers.size(); i++) labels[i] = providers.get(i).name + " · " + AiSecureStore.getModel(this, providers.get(i).id);
        JSONObject chat = current(); if (chat == null) return;
        int checked = AiProvider.indexOf(chat.optString("provider", AiSecureStore.getActiveProvider(this)));
        new AlertDialog.Builder(this).setTitle("المزوّد والنموذج").setSingleChoiceItems(labels, checked, (dialog, which) -> {
            String provider = providers.get(which).id;
            try { chat.put("provider", provider); } catch (Exception ignored) { }
            saveChats();
            AgentWorkspace.persistChatOverride(this, chat.optString("id"), provider, chat.optString("sessionId", ""), chat.has("agent_auto_execute") ? Boolean.valueOf(chat.optBoolean("agent_auto_execute")) : null);
            updateHeader(); renderMessages(); dialog.dismiss();
        }).setNegativeButton("إلغاء", null).show();
    }

    private void chooseTerminalSessionForChat() {
        sessions = AiSecureStore.loadArray(this, AiSecureStore.SESSIONS, "items");
        List<String> labels = terminalNames(); String[] names = labels.toArray(new String[0]);
        JSONObject chat = current(); if (chat == null) return;
        int checked = Math.max(0, indexOfSession(chat.optString("sessionId", "")));
        new AlertDialog.Builder(this).setTitle("جلسة Termux المرتبطة").setSingleChoiceItems(names, checked, (dialog, which) -> {
            String sid = sessionIdAt(which);
            try { chat.put("sessionId", sid); } catch (Exception ignored) { }
            saveChats(); AgentWorkspace.persistChatOverride(this, chat.optString("id"), chat.optString("provider", "gemini"), sid,
                    chat.has("agent_auto_execute") ? Boolean.valueOf(chat.optBoolean("agent_auto_execute")) : null);
            updateHeader(); renderMessages(); dialog.dismiss();
        }).setNegativeButton("إلغاء", null).show();
    }

    private void syncAgentWorkspace(Runnable afterSync) {
        final Runnable callback = afterSync == null ? () -> { } : afterSync;
        try {
            int requestId = TermuxCommandRunner.run(this, AgentWorkspace.syncCommand(this), "~/", "TFM agent workspace sync", true);
            workspaceSyncCallbacks.put(requestId, callback);
            handler.postDelayed(() -> {
                Runnable pending = workspaceSyncCallbacks.remove(requestId);
                if (pending != null) pending.run();
            }, 10000L);
        } catch (Exception e) {
            // Continue with cached application settings when Termux is unavailable.
            handler.post(callback);
        }
    }


    private boolean isAgentRunCancelled(String chatId) {
        return Boolean.TRUE.equals(cancelledAgentRuns.get(chatId));
    }

    private void queueAgentContinuation(String chatId) {
        queueAgentContinuation(chatId, false);
    }

    /** Continues a reasoning-only turn; the guard only stops repeated no-progress thinking, never total task steps. */
    private void queueAgentContinuation(String chatId, boolean thinkingOnly) {
        if (isAgentRunCancelled(chatId)) return;
        JSONObject chat = findChat(chatId); if (chat == null) return;
        if (thinkingOnly) {
            int count = noProgressContinuations.containsKey(chatId) ? noProgressContinuations.get(chatId) + 1 : 1;
            noProgressContinuations.put(chatId, count);
            if (count >= 6) {
                appendChatMessage(chat, "assistant", "أوقفت المتابعة لأن الموديل رجّع التفكير فقط 6 مرات متتالية بدون نص قابل للاستخدام أو أداة أو نتيجة جديدة. ماكو حد على عدد خطوات المهمة؛ هذا الحاجز يمنع استهلاك الطلبات داخل حلقة بلا تقدّم. غيّر النموذج أو أرسل متابعة جديدة.");
                saveChats();
                if (chatId.equals(activeChatId)) renderMessages();
                return;
            }
        } else {
            noProgressContinuations.put(chatId, 0);
        }
        try {
            JSONArray messages = chat.optJSONArray("messages"); if (messages == null) messages = new JSONArray();
            String instruction = thinkingOnly
                    ? "[Agent continuation control] The previous provider turn produced a thinking summary but no answer text or tool call. This is a valid planning-only turn, not an error. Continue the original task now: choose the next useful tool/action and return one valid tfm_action, or return finish with a concise user-facing answer if the task is done. Do not repeat the same thinking summary. Do not invent a tool output."
                    : "[Agent continuation control] The previous response was a progress update, not the end of the user's task. Continue from verified state. Return a useful next tool action or finish with the final user-facing answer. Do not repeat work already verified.";
            messages.put(msg("agent_control", instruction));
            while (messages.length() > 80) messages.remove(0);
            chat.put("messages", messages); saveChats();
        } catch (Exception ignored) { }
        if (chatId.equals(activeChatId)) {
            if (rateLabel != null) rateLabel.setText(thinkingOnly ? "Thinking-only turn · الوكيل يختار الخطوة التالية…" : "تقرير مرحلي؛ الوكيل يكمل المهمة…");
            renderMessages();
        }
        autoAnalyzeCommandResult(chatId);
    }

    /**
     * Appends tool/search results to the current agent run. renderMessages() groups
     * all non-user events into one compact work bubble.
     */
    private void addAgentToolOutput(String chatId, String label, String output) {
        JSONObject chat = findChat(chatId);
        if (chat == null) return;
        try {
            JSONArray messages = chat.optJSONArray("messages");
            if (messages == null) messages = new JSONArray();
            JSONObject event = msg("tool_output", output == null ? "" : output);
            event.put("tool_label", label == null || label.trim().isEmpty() ? "Tool output" : label);
            event.put("display_path", "");
            messages.put(event);
            while (messages.length() > 80) messages.remove(0);
            chat.put("messages", messages);
            saveChats();
            if (chatId.equals(activeChatId)) renderMessages();
        } catch (Exception e) {
            appendChatMessage(chat, "assistant", "تعذر عرض مخرجات الأداة: " + e.getMessage());
            saveChats();
            if (chatId.equals(activeChatId)) renderMessages();
        }
    }

    /** Convert internal control/tool JSON into readable text without hiding real shell actions. */
    private String normalizeAgentActionForDisplay(String original, JSONObject action) {
        if (action == null) return original == null ? "" : original;
        String tool = action.optString("tool", action.optString("name", ""));
        if ("finish".equals(tool)) {
            return action.optString("message", action.optString("content",
                    action.optString("result", "اكتملت المهمة.")));
        }
        if ("continue".equals(tool)) {
            return action.optString("message", action.optString("status",
                    action.optString("reason", "أكمل العمل بالخطوة التالية.")));
        }
        if ("web_search".equals(tool)) {
            JSONObject args = action.optJSONObject("arguments");
            String query = action.optString("query", "");
            if (query.trim().isEmpty() && args != null) query = args.optString("query", args.optString("q", ""));
            return "أبحث بالإنترنت عن: " + (query.trim().isEmpty() ? "الموضوع المطلوب" : query)
                    + "\n" + action.optString("reason", "أتحقق من مصادر قبل إكمال الإجابة.");
        }
        if ("save_memory".equals(tool) || "remember".equals(tool)) {
            return action.optString("message", "أحفظ المعلومة في الذاكرة الدائمة ~/.TFM-setting/Memory.md.");
        }
        return original == null ? "" : original;
    }

    /** Actions managed by the agent loop rather than a shell command. */
    private boolean isInternalAgentAction(JSONObject action) {
        if (action == null) return false;
        String tool = action.optString("tool", action.optString("name", ""));
        return "finish".equals(tool) || "continue".equals(tool) || "web_search".equals(tool)
                || "save_memory".equals(tool) || "remember".equals(tool);
    }

    /** Stops further automatic turns for the active conversation. In-flight HTTP/shell work may finish. */
    private void stopAgentRun() {
        JSONObject chat = current();
        if (chat == null) return;
        String chatId = chat.optString("id", "");
        if (chatId.isEmpty()) return;
        cancelledAgentRuns.put(chatId, true);
        if (rateLabel != null) rateLabel.setText("تم طلب إيقاف دورة الوكيل.");
        Toast.makeText(this,
                "توقف الوكيل عن بدء خطوات جديدة؛ العملية الجارية قد تكمل قبل الإيقاف.",
                Toast.LENGTH_LONG).show();
    }

    private void runWebSearch(String chatId, String query) {
        if (!AiSecureStore.settings(this).getBoolean("agent_web_search_enabled", true)) {
            appendChatMessage(findChat(chatId), "assistant", "البحث عبر الإنترنت متوقف حسب config.json. فعّل agent.webSearch إذا تحتاج مصادر حديثة.");
            saveChats(); if (chatId.equals(activeChatId)) renderMessages();
            if (!isAgentRunCancelled(chatId)) autoAnalyzeCommandResult(chatId);
            return;
        }
        busy = true; if (sendButton != null) sendButton.setEnabled(false);
        worker.execute(() -> {
            String result;
            try { result = AgentWebSearch.search(query); }
            catch (Exception e) { result = "Web search failed: " + e.getMessage(); }
            final String output = result;
            runOnUiThread(() -> {
                addAgentToolOutput(chatId, "Web search results", output);
                busy = false; if (sendButton != null) sendButton.setEnabled(true); updateHeader();
                if (!isAgentRunCancelled(chatId)) autoAnalyzeCommandResult(chatId);
            });
        });
    }

    private void saveAgentMemory(String chatId, JSONObject action) {
        JSONObject memoryArgs = action.optJSONObject("arguments");
        String content = action.optString("content", action.optString("memory", action.optString("text", ""))).trim();
        if (content.isEmpty() && memoryArgs != null) content = memoryArgs.optString("content", memoryArgs.optString("text", "")).trim();
        if (content.isEmpty()) { appendChatMessage(findChat(chatId), "assistant", "أداة الذاكرة تحتاج content غير فارغ."); saveChats(); if (chatId.equals(activeChatId)) renderMessages(); return; }
        try {
            int requestId = TermuxCommandRunner.run(this, AgentWorkspace.appendMemoryCommand(content), "~/", "TFM save agent memory", true);
            commandChats.put(requestId, chatId); agentCommandRequests.put(requestId, Boolean.TRUE);
            Toast.makeText(this, "جاري حفظ المعلومة في الذاكرة الدائمة…", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            appendChatMessage(findChat(chatId), "assistant", "تعذر حفظ الذاكرة داخل Termux: " + e.getMessage());
            saveChats(); if (chatId.equals(activeChatId)) renderMessages();
        }
    }
}
