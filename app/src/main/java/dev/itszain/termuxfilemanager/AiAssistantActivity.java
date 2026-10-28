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
import android.widget.HorizontalScrollView;
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
    private UiKit kit;
    private JSONArray chats,sessions; private String activeChatId="",attachedImage=""; private Spinner chatsSpinner,providerSpinner,terminalSpinner; private TextView modelLabel,rateLabel,attachmentLabel; private TextView chatTitle,chatSubtitle; private TextView providerChip,sessionChip,autoChip; private LinearLayout agentStatusHolder; private LinearLayout messagesBox; private ScrollView messagesScroll; private EditText input; private Button sendButton; private boolean receiverRegistered=false; private boolean busy=false; private boolean updatingSpinners=false;
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
    private void initTheme(){lastThemeName=UiTheme.getName(this);theme=UiTheme.get(this);kit=new UiKit(this);BG=theme.bg;PANEL=theme.panel;PANEL2=theme.panel2;TEXT=theme.text;MUTED=theme.muted;ACCENT=theme.accent;DANGER=theme.danger;DIVIDER=theme.divider;}
    private JSONObject current(){return findChat(activeChatId);} private JSONObject findChat(String id){return AiSecureStore.findById(chats,id);}
    private JSONObject msg(String role,String text){JSONObject o=new JSONObject();try{o.put("role",role);o.put("text",text);o.put("time",System.currentTimeMillis());}catch(Exception ignored){}return o;}
    private void newChat(boolean rebuild){String id=Long.toString(System.nanoTime());JSONObject c=new JSONObject();try{c.put("id",id);c.put("name","دردشة "+(chats.length()+1));c.put("provider",AiSecureStore.getActiveProvider(this));c.put("sessionId",AiSecureStore.settings(this).getString("active_terminal_session",""));c.put("messages",new JSONArray());chats.put(c);activeChatId=id;saveChats();if(rebuild)rebuildChatSpinner();}catch(Exception ignored){}}
    private void saveChats(){AiSecureStore.saveArray(this,AiSecureStore.CHATS,"items",chats);AiSecureStore.settings(this).edit().putString("active_ai_chat",activeChatId).apply();}
    private void build() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(kit.bg);
        setContentView(frame);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(kit.bg);
        frame.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // These spinners are retained as state holders; their pickers are opened from the header chips.
        chatsSpinner = new Spinner(this);
        chatsSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, chatNames()));
        providerSpinner = new Spinner(this);
        List<String> providerNames = new ArrayList<>();
        for (AiProvider provider : AiProvider.all()) providerNames.add(provider.name);
        providerSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, providerNames));
        terminalSpinner = new Spinner(this);
        terminalSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, terminalNames()));
        modelLabel = kit.text("Model", 10, kit.muted, false);
        rateLabel = kit.text("جاهز", 10.5f, kit.muted, false);
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

        buildAgentHeader(column);
        buildMessages(column);
        buildComposer(column);

        updateHeader();
        renderMessages();
    }

    /** Header: chat identity, live status, and the controls that shape the agent run. */
    private void buildAgentHeader(LinearLayout parent) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(kit.dp(14), kit.dp(12), kit.dp(12), kit.dp(8));
        header.setBackground(kit.round(kit.panel, 0, kit.outline));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        chatTitle = kit.text("دردشة", 19, kit.text, true);
        titles.addView(chatTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        chatSubtitle = kit.caption("");
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleLp.topMargin = kit.dp(3);
        titles.addView(chatSubtitle, subtitleLp);
        titleRow.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button newChat = kit.iconButton("✚", "دردشة جديدة");
        newChat.setTextSize(17);
        newChat.setOnClickListener(v -> {
            newChat(true);
            updateHeader();
            renderMessages();
        });
        titleRow.addView(newChat, new LinearLayout.LayoutParams(kit.dp(36), kit.dp(36)));

        Button menu = kit.iconButton("⋮", "خيارات الوكيل");
        menu.setOnClickListener(v -> showAgentMenu());
        LinearLayout.LayoutParams menuLp = new LinearLayout.LayoutParams(kit.dp(36), kit.dp(36));
        menuLp.leftMargin = kit.dp(6);
        titleRow.addView(menu, menuLp);
        header.addView(titleRow);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, kit.dp(8), 0, 0);
        agentStatusHolder = new LinearLayout(this);
        statusRow.addView(agentStatusHolder, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rateLabel.setPadding(kit.dp(10), 0, 0, 0);
        statusRow.addView(rateLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(statusRow);

        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setPadding(0, kit.dp(8), 0, 0);
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setGravity(Gravity.CENTER_VERTICAL);

        providerChip = kit.chip("المزوّد", false);
        providerChip.setOnClickListener(v -> chooseProviderForChat());
        chipRow.addView(providerChip, chipLp());

        sessionChip = kit.chip("الجلسة", false);
        sessionChip.setOnClickListener(v -> chooseTerminalSessionForChat());
        chipRow.addView(sessionChip, chipLp());

        autoChip = kit.chip("تنفيذ تلقائي", false);
        autoChip.setOnClickListener(v -> toggleChatAutoExecution());
        chipRow.addView(autoChip, chipLp());

        chipScroll.addView(chipRow);
        header.addView(chipScroll);

        View line = kit.divider();
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(1));
        lineLp.topMargin = kit.dp(10);
        header.addView(line, lineLp);
        parent.addView(header);
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, kit.dp(30));
        lp.rightMargin = kit.dp(8);
        return lp;
    }

    private void buildMessages(LinearLayout parent) {
        messagesScroll = new ScrollView(this);
        messagesScroll.setFillViewport(true);
        messagesScroll.setClipToPadding(false);
        messagesBox = new LinearLayout(this);
        messagesBox.setOrientation(LinearLayout.VERTICAL);
        messagesBox.setPadding(kit.dp(12), kit.dp(6), kit.dp(12), kit.dp(14));
        messagesScroll.addView(messagesBox);
        parent.addView(messagesScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    private void buildComposer(LinearLayout parent) {
        LinearLayout composerWrap = new LinearLayout(this);
        composerWrap.setOrientation(LinearLayout.VERTICAL);
        composerWrap.setPadding(kit.dp(10), kit.dp(6), kit.dp(10), kit.dp(10));
        composerWrap.setBackground(kit.round(kit.panel, 20, kit.outline));

        attachmentLabel = kit.caption("ماكو صورة مرفقة");
        attachmentLabel.setPadding(kit.dp(6), kit.dp(2), kit.dp(6), kit.dp(4));
        attachmentLabel.setVisibility(View.GONE);
        composerWrap.addView(attachmentLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.BOTTOM | Gravity.CENTER_VERTICAL);

        Button attach = kit.iconButton("＋", "إرفاق لقطة شاشة");
        attach.setTextSize(17);
        attach.setOnClickListener(v -> captureOrAttach());
        composer.addView(attach, new LinearLayout.LayoutParams(kit.dp(40), kit.dp(44)));

        input = new EditText(this);
        input.setTextColor(kit.text);
        input.setHintTextColor(kit.muted);
        input.setTextSize(13.5f);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setMinLines(1);
        input.setMaxLines(4);
        input.setMaxHeight(kit.dp(104));
        input.setHint("اكتب هدفك…");
        input.setPadding(kit.dp(12), kit.dp(10), kit.dp(12), kit.dp(10));
        input.setBackground(kit.round(kit.panelAlt, 16, kit.outline));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        inputLp.rightMargin = kit.dp(8);
        composer.addView(input, inputLp);

        Button send = kit.primary("↑");
        send.setTextSize(21);
        send.setContentDescription("إرسال إلى الوكيل");
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(kit.dp(48), kit.dp(46));
        composer.addView(send, sendLp);
        composerWrap.addView(composer);
        parent.addView(composerWrap);

        sendButton = send;
        send.setOnClickListener(v -> sendCurrent());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER && event.isShiftPressed()) return false;
            if (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_UP) { sendCurrent(); return true; }
            return false;
        });
    }

    private void updateHeader() {
        JSONObject c = current(); if (c == null) return;
        String provider = c.optString("provider", AiSecureStore.getActiveProvider(this));
        int providerIndex = AiProvider.indexOf(provider);
        updatingSpinners = true;
        if (providerSpinner != null && providerSpinner.getSelectedItemPosition() != providerIndex) providerSpinner.setSelection(providerIndex);
        AiProvider p = AiProvider.byId(provider);
        int sessionIndex = indexOfSession(c.optString("sessionId", ""));
        if (terminalSpinner != null && terminalSpinner.getSelectedItemPosition() != sessionIndex) terminalSpinner.setSelection(Math.max(0, sessionIndex));
        updatingSpinners = false;

        if (chatTitle != null) chatTitle.setText(c.optString("name", "دردشة"));
        if (chatSubtitle != null) chatSubtitle.setText(p.name + " · " + AiSecureStore.getModel(this, p.id));
        if (providerChip != null) providerChip.setText(p.name);
        JSONObject session = AiSecureStore.findById(sessions, c.optString("sessionId", ""));
        if (sessionChip != null) sessionChip.setText(session == null ? "بدون جلسة" : session.optString("name", "Terminal"));
        if (autoChip != null) {
            boolean auto = autoExecutionEnabled(c.optString("id", ""));
            autoChip.setText(auto ? "تنفيذ تلقائي: مفعّل" : "تنفيذ تلقائي: مغلق");
            autoChip.setTextColor(auto ? kit.accent : kit.muted);
        }
        if (modelLabel != null) modelLabel.setText("Model: " + AiSecureStore.getModel(this, p.id));
        if (rateLabel != null && !busy) rateLabel.setText("متبقي اليوم: " + AiSecureStore.remainingRequests(this, p.id) + " طلب · حدود محلية");
        updateStatusPill();
    }

    /** Live status pill in the header: ready vs. agent working. */
    private void updateStatusPill() {
        if (agentStatusHolder == null) return;
        agentStatusHolder.removeAllViews();
        agentStatusHolder.addView(kit.statusPill(busy ? "الوكيل يعمل" : "جاهز", busy));
    }
    private void renderMessages() {
        if (messagesBox == null) return;
        activeStreams.clear();
        messagesBox.removeAllViews();
        JSONObject chat = current();
        if (chat == null) return;
        JSONArray all = chat.optJSONArray("messages");
        if (all == null || all.length() == 0) {
            LinearLayout welcome = buildWelcomeCard();
            messagesBox.addView(welcome, kit.match());
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

    /** First-run panel: explains the agent in two lines and offers ready-made starting points. */
    private LinearLayout buildWelcomeCard() {
        LinearLayout card = kit.card();
        TextView head = kit.text("ابدأ بمهمة", 16, kit.text, true);
        card.addView(head, kit.matchNoMargin());
        TextView sub = kit.caption("اكتب هدفك بالعربي، والوكيل يفحص الملفات ويقترح أدوات. كل أمر يحتاج موافقتك، إلا إذا فعّلت التنفيذ التلقائي من الشريحة فوق.");
        sub.setPadding(0, kit.dp(6), 0, kit.dp(12));
        card.addView(sub, kit.matchNoMargin());
        String[] suggestions = {
                "افحص ملفات المشروع وحدد المشاكل",
                "اشرح لي هذا الكود خطوة بخطوة",
                "شغّل اختبارات المشروع وحلّل النتيجة"
        };
        for (String suggestion : suggestions) {
            LinearLayout row = kit.row();
            TextView label = kit.text(suggestion, 13, kit.text, false);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView arrow = kit.text("›", 18, kit.accent, false);
            arrow.setGravity(Gravity.CENTER);
            row.addView(arrow, new LinearLayout.LayoutParams(kit.dp(24), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setOnClickListener(v -> {
                if (input != null) {
                    input.setText(suggestion);
                    input.requestFocus();
                }
            });
            LinearLayout.LayoutParams rowLp = kit.match();
            rowLp.bottomMargin = kit.dp(4);
            card.addView(row, rowLp);
        }
        return card;
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
        bubble.setPadding(kit.dp(13), kit.dp(10), kit.dp(13), kit.dp(11));
        int bubbleColor = assistant ? kit.panel : (terminal ? kit.panelAlt : kit.accentSoft);
        int bubbleStroke = assistant ? kit.outline : Color.TRANSPARENT;
        bubble.setBackground(kit.round(bubbleColor, 18, bubbleStroke));

        TextView label = kit.text(assistant ? AiProvider.byId(providerFor(chatId)).name : (terminal ? "TERMUX" : "أنت"),
                10.5f, assistant ? kit.accent : kit.muted, true);
        bubble.addView(label, kit.wrap());

        String thoughtText = m.optString("thinking", "");
        if (assistant && !m.optBoolean("streaming", false) && !thoughtText.trim().isEmpty()) addThinkingCard(bubble, thoughtText);

        String streamId = m.optString("stream_id", "");
        if (assistant && m.optBoolean("streaming", false) && !streamId.isEmpty()) {
            Button toggle = kit.button(thoughtText.isEmpty() ? "التفكير جارٍ…" : ("ملخص التفكير · " + thoughtText.length() + " حرف"),
                    kit.panelAlt, kit.accent);
            toggle.setTextSize(11);
            toggle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            LinearLayout thoughtBody = new LinearLayout(this);
            thoughtBody.setOrientation(LinearLayout.VERTICAL);
            thoughtBody.setPadding(kit.dp(9), kit.dp(7), kit.dp(9), kit.dp(7));
            thoughtBody.setBackground(kit.round(kit.bg, 10, kit.outline));
            TextView thoughtView = kit.text(thoughtText.isEmpty() ? "بانتظار ملخص التفكير من المزوّد…" : thoughtText, 11, kit.muted, false);
            thoughtView.setGravity(Gravity.TOP | Gravity.START);
            thoughtView.setTextIsSelectable(true);
            thoughtBody.addView(thoughtView, kit.matchNoMargin());
            thoughtBody.setVisibility(View.GONE);
            toggle.setOnClickListener(v -> {
                boolean show = thoughtBody.getVisibility() != View.VISIBLE;
                thoughtBody.setVisibility(show ? View.VISIBLE : View.GONE);
            });
            bubble.addView(toggle, kit.wrap());
            bubble.addView(thoughtBody, kit.wrap());

            TextView liveAnswer = kit.text(txt.isEmpty() ? "جارٍ توليد الرد…" : txt, 13.5f, kit.text, false);
            liveAnswer.setGravity(Gravity.TOP | Gravity.START);
            liveAnswer.setTextIsSelectable(true);
            bubble.addView(liveAnswer, kit.wrap());
            activeStreams.put(streamId, new StreamViews(liveAnswer, thoughtView, toggle, thoughtBody));
        } else {
            JSONObject action = assistant ? extractAgentAction(txt) : null;
            if (action != null && isSkillActivation(action)) {
                String skillName = action.optString("skill", action.optString("name", "skill"));
                bubble.addView(kit.text("يتحقق من المهارة /" + skillName + " ويحمّل تعليماتها…", 12.5f, kit.accent, true), kit.wrap());
            } else if (action != null) {
                String tool = action.optString("tool", action.optString("name", "run_shell"));
                String reason = action.optString("reason", "الوكيل يحتاج أداة حتى يكمل المهمة.");
                boolean skillInstall = "install_skill".equals(tool);
                String display = skillInstall ? action.optString("url", action.optString("source", "")) : agentCommand(action);
                TextView description = kit.text(reason, 13, kit.text, false);
                description.setPadding(0, kit.dp(4), 0, kit.dp(6));
                bubble.addView(description, kit.wrap());
                TextView code = kit.mono(skillInstall ? ("Skill URL: " + display) : ("$ " + display), 12, kit.text);
                code.setPadding(kit.dp(10), kit.dp(9), kit.dp(10), kit.dp(9));
                code.setBackground(kit.round(kit.bg, 10, kit.outline));
                bubble.addView(code, kit.wrap());
                Button run = kit.primary(skillInstall ? "تنصيب المهارة" : "مراجعة وتشغيل الأداة");
                run.setOnClickListener(v -> confirmAgentAction(chatId, action));
                bubble.addView(run, kit.wrap());
                if (!skillInstall) {
                    Button copy = kit.ghost("نسخ الأمر");
                    copy.setOnClickListener(v -> copyText("AI command", display));
                    bubble.addView(copy, kit.wrap());
                }
            } else {
                addMarkdownContent(bubble, txt, chatId);
            }
        }
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = kit.dp(4);
        p.bottomMargin = kit.dp(4);
        p.gravity = assistant ? Gravity.START : Gravity.END;
        messagesBox.addView(bubble, p);
    }

    /**
     * One compact card holds a whole agent run: thinking, tool calls, tool output and
     * the final answer. Every step is a row in a readable timeline.
     */
    private void addAgentRunBubble(String chatId, JSONArray events) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(kit.dp(13), kit.dp(11), kit.dp(13), kit.dp(12));
        card.setBackground(kit.round(kit.panel, 20, kit.outline));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = kit.text("✦", 13, kit.accent, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(kit.round(kit.accentSoft, 14));
        header.addView(badge, new LinearLayout.LayoutParams(kit.dp(26), kit.dp(26)));
        TextView title = kit.text("AI Agent · " + AiProvider.byId(providerFor(chatId)).name, 12.5f, kit.text, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleLp.leftMargin = kit.dp(8);
        header.addView(title, titleLp);
        TextView steps = kit.text(events.length() + " خطوة", 10.5f, kit.muted, false);
        header.addView(steps);
        card.addView(header, kit.matchNoMargin());
        addCompactDivider(card);

        int step = 0;
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null) continue;
            String role = event.optString("role", "");
            if ("agent_control".equals(role)) continue;

            if ("tool_output".equals(role)) {
                step++;
                String label = event.optString("tool_label", "Tool output");
                String raw = event.optString("text", "");
                addTimelineRow(card, "▤", "مخرجات الأداة · " + compactToolLabel(label),
                        raw.length() + " حرف · اضغط لعرض المخرجات", raw, null, chatId, false, step);
                continue;
            }
            if ("user".equals(role) && event.optString("text", "").trim().startsWith("[نتيجة أداة")) {
                step++;
                String raw = event.optString("text", "");
                addTimelineRow(card, "▤", "نتيجة أداة · install_skill",
                        raw.length() + " حرف · اضغط للتفاصيل", raw, null, chatId, false, step);
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
                step++;
                Button thoughtToggle = kit.button(thought.isEmpty() ? "التفكير جارٍ…" : ("Thinking · " + thought.length() + " chars"),
                        kit.panelAlt, kit.muted);
                thoughtToggle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                thoughtToggle.setTextSize(11);
                thoughtToggle.setVisibility(thought.isEmpty() ? View.GONE : View.VISIBLE);
                LinearLayout thoughtBody = new LinearLayout(this);
                thoughtBody.setOrientation(LinearLayout.VERTICAL);
                thoughtBody.setPadding(kit.dp(9), kit.dp(6), kit.dp(9), kit.dp(6));
                thoughtBody.setBackground(kit.round(kit.bg, 10, kit.outline));
                TextView thoughtView = kit.text(thought.isEmpty() ? "" : thought, 11, kit.muted, false);
                thoughtView.setGravity(Gravity.TOP | Gravity.START);
                thoughtView.setTextIsSelectable(true);
                thoughtBody.addView(thoughtView, kit.matchNoMargin());
                thoughtBody.setVisibility(View.GONE);
                thoughtToggle.setOnClickListener(v -> {
                    boolean show = thoughtBody.getVisibility() != View.VISIBLE;
                    thoughtBody.setVisibility(show ? View.VISIBLE : View.GONE);
                });
                card.addView(thoughtToggle, kit.matchNoMargin());
                card.addView(thoughtBody, kit.matchNoMargin());

                boolean looksLikeToolJson = looksLikeAgentActionPayload(rawText);
                TextView liveAnswer = kit.text(rawText.isEmpty() ? "" : (looksLikeToolJson ? "جاري تجهيز استدعاء الأداة…" : rawText), 13.5f, kit.text, false);
                liveAnswer.setGravity(Gravity.TOP | Gravity.START);
                liveAnswer.setTextIsSelectable(true);
                liveAnswer.setVisibility(rawText.isEmpty() || looksLikeToolJson ? View.GONE : View.VISIBLE);
                card.addView(liveAnswer, kit.matchNoMargin());
                activeStreams.put(streamId, new StreamViews(liveAnswer, thoughtView, thoughtToggle, thoughtBody));
                continue;
            }

            if (thought != null && !thought.trim().isEmpty()) {
                step++;
                addTimelineRow(card, "✳", "التفكير · " + thought.length() + " حرف",
                        "اضغط لعرض ملخص التفكير", thought, null, chatId, false, step);
            }

            if (action != null) {
                String tool = action.optString("tool", action.optString("name", ""));
                if ("finish".equals(tool) || "continue".equals(tool)) {
                    if (!rawText.trim().isEmpty()) addMarkdownContent(card, rawText, chatId);
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
                step++;
                String details = describeAgentTool(action);
                if (pairedOutput != null) {
                    details += "\n\n--- TOOL OUTPUT ---\n" + pairedOutput.optString("text", "");
                    i++;
                }
                addTimelineRow(card, "⚙", "استدعاء أداة · " + compactToolLabel(tool),
                        pairedOutput == null ? "اضغط لعرض الأداة والإجراء" : "الأداة والمخرجات · اضغط للتفاصيل",
                        details, action, chatId, pairedOutput != null, step);
                continue;
            }

            if (!rawText.trim().isEmpty()) addMarkdownContent(card, rawText, chatId);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = kit.dp(6);
        lp.bottomMargin = kit.dp(4);
        messagesBox.addView(card, lp);
    }

    /**
     * A single timeline row: icon badge, title, summary and an expandable detail box.
     * Tool rows can also offer the run / copy actions directly.
     */
    private void addTimelineRow(LinearLayout parent, String glyph, String heading, String summary, String details,
                                JSONObject action, String chatId, boolean alreadyExecuted, int step) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.setGravity(Gravity.TOP);

        TextView badge = kit.text(glyph, 11, kit.accent, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(kit.round(kit.accentSoft, 12));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(kit.dp(24), kit.dp(24));
        wrapper.addView(badge, badgeLp);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(kit.dp(9), 0, 0, kit.dp(6));

        Button row = kit.button("›  " + heading, kit.panelAlt, kit.accent);
        row.setContentDescription(summary);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setTextSize(11.5f);
        row.setMinHeight(kit.dp(32));
        row.setPadding(kit.dp(8), kit.dp(4), kit.dp(8), kit.dp(4));
        body.addView(row, kit.matchNoMargin());

        LinearLayout detailBox = new LinearLayout(this);
        detailBox.setOrientation(LinearLayout.VERTICAL);
        detailBox.setPadding(kit.dp(9), kit.dp(7), kit.dp(9), kit.dp(7));
        detailBox.setBackground(kit.round(kit.bg, 10, kit.outline));
        TextView content = kit.mono(details == null ? "" : details, 11, kit.text);
        content.setGravity(Gravity.TOP | Gravity.START);
        content.setTextIsSelectable(true);
        detailBox.addView(content, kit.matchNoMargin());

        if (action != null && !alreadyExecuted) {
            String tool = action.optString("tool", action.optString("name", ""));
            if (!"finish".equals(tool) && !"continue".equals(tool)
                    && !autoExecutionEnabled(chatId) && isRunnableAgentAction(action)) {
                Button run = kit.primary("مراجعة وتشغيل الأداة");
                run.setOnClickListener(v -> confirmAgentAction(chatId, action));
                detailBox.addView(run, kit.matchNoMargin());
                String command = agentCommand(action);
                if (!command.trim().isEmpty()) {
                    Button copy = kit.ghost("نسخ الأمر");
                    copy.setOnClickListener(v -> copyText("AI command", command));
                    detailBox.addView(copy, kit.matchNoMargin());
                }
            }
        }
        detailBox.setVisibility(View.GONE);
        body.addView(detailBox, kit.matchNoMargin());
        wrapper.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        row.setOnClickListener(v -> {
            boolean show = detailBox.getVisibility() != View.VISIBLE;
            detailBox.setVisibility(show ? View.VISIBLE : View.GONE);
            row.setText((show ? "⌄  " : "›  ") + heading);
        });
        parent.addView(wrapper, kit.match());
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
        String raw = item.optString("text", "");
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(kit.dp(12), kit.dp(10), kit.dp(12), kit.dp(10));
        card.setBackground(kit.round(kit.panel, 18, kit.outline));

        String path = item.optString("display_path", "");
        TextView heading = kit.text(path.isEmpty() ? "مخرجات الأداة · Termux" : "قراءة " + path, 11.5f, kit.accent, true);
        card.addView(heading, kit.matchNoMargin());

        TextView preview = kit.text(raw.length() > 160 ? raw.substring(0, 160) + "…" : "وصلت المخرجات · " + raw.length() + " حرف",
                11, kit.muted, false);
        card.addView(preview, kit.matchNoMargin());

        TextView details = kit.mono(raw, 11, kit.text);
        details.setGravity(Gravity.TOP | Gravity.START);
        details.setTextIsSelectable(true);
        details.setPadding(kit.dp(9), kit.dp(8), kit.dp(9), kit.dp(8));
        details.setBackground(kit.round(kit.bg, 10, kit.outline));
        details.setVisibility(View.GONE);
        ScrollView scroller = new ScrollView(this);
        scroller.addView(details);
        card.addView(scroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(170)));

        Button toggle = kit.ghost("عرض المخرجات الخام");
        toggle.setOnClickListener(v -> {
            boolean show = details.getVisibility() != View.VISIBLE;
            details.setVisibility(show ? View.VISIBLE : View.GONE);
            toggle.setText(show ? "إخفاء المخرجات" : "عرض المخرجات الخام");
            preview.setVisibility(show ? View.GONE : View.VISIBLE);
        });
        card.addView(toggle, kit.matchNoMargin());

        LinearLayout.LayoutParams lp = kit.match();
        lp.topMargin = kit.dp(4);
        messagesBox.addView(card, lp);
    }

    private String extractReadingPath(JSONObject chat) {
        try { JSONArray a=chat.optJSONArray("messages"); if(a!=null&&a.length()>0){String last=a.optJSONObject(a.length()-1).optString("text","");java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)(?:cat|read|reading)\\s+([~/][^\\s;&|]+)").matcher(last);if(m.find())return m.group(1);} } catch(Exception ignored){} return "";
    }

    private void addThinkingCard(LinearLayout bubble, String thought) {
        Button toggle = kit.button("ملخص التفكير · " + thought.length() + " حرف", kit.panelAlt, kit.accent);
        toggle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        toggle.setTextSize(11);
        TextView body = kit.mono(thought, 11, kit.muted);
        body.setGravity(Gravity.TOP | Gravity.START);
        body.setTextIsSelectable(true);
        body.setPadding(kit.dp(9), kit.dp(7), kit.dp(9), kit.dp(7));
        body.setBackground(kit.round(kit.bg, 10, kit.outline));
        body.setVisibility(View.GONE);
        toggle.setOnClickListener(v -> {
            boolean show = body.getVisibility() != View.VISIBLE;
            body.setVisibility(show ? View.VISIBLE : View.GONE);
            toggle.setText(show ? "إخفاء ملخص التفكير" : "ملخص التفكير · " + thought.length() + " حرف");
        });
        bubble.addView(toggle, kit.wrap());
        bubble.addView(body, kit.wrap());
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
            LinearLayout.LayoutParams lp=kit.wrap(); lp.topMargin=dp(1); lp.bottomMargin=dp(3); target.addView(row,lp);
        }
    }

    private void addCodeCard(LinearLayout target, String language, String code, String chatId) {
        if (code == null || code.trim().isEmpty()) return;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(kit.dp(11), kit.dp(9), kit.dp(11), kit.dp(9));
        card.setBackground(kit.round(kit.bg, 12, kit.outline));
        String lang = language == null ? "" : language.trim().toLowerCase();
        TextView header = kit.text(lang.isEmpty() ? "CODE" : ("CODE · " + lang), 10, kit.accent, true);
        header.setLetterSpacing(0.06f);
        card.addView(header, kit.wrap());
        TextView body = kit.mono(code, 12, kit.text);
        body.setGravity(Gravity.TOP | Gravity.START);
        body.setTextIsSelectable(true);
        LinearLayout.LayoutParams bodyLp = kit.wrap();
        bodyLp.topMargin = kit.dp(5);
        card.addView(body, bodyLp);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Button copy = kit.ghost("نسخ");
        copy.setOnClickListener(v -> copyText("Code", code));
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(kit.dp(74), kit.dp(36));
        actions.addView(copy, copyLp);
        if (lang.isEmpty() || lang.equals("bash") || lang.equals("sh") || lang.equals("shell") || lang.equals("termux") || lang.equals("zsh")) {
            Button run = kit.primary("تشغيل في Termux");
            run.setOnClickListener(v -> confirmSuggestedCommand(chatId, code));
            LinearLayout.LayoutParams runLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, kit.dp(36));
            runLp.leftMargin = kit.dp(8);
            actions.addView(run, runLp);
        }
        LinearLayout.LayoutParams aLp = kit.wrap();
        aLp.topMargin = kit.dp(8);
        card.addView(actions, aLp);
        LinearLayout.LayoutParams p = kit.wrap();
        p.topMargin = kit.dp(4);
        target.addView(card, p);
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
        final android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout card = kit.dialogCard(installSkill ? "تثبيت Agent Skill" : "موافقة على أداة الوكيل", null);

        TextView reasonView = kit.text(reason, 13, kit.text, false);
        reasonView.setGravity(Gravity.TOP | Gravity.START);
        reasonView.setLineSpacing(kit.dp(2), 1f);
        card.addView(reasonView, kit.matchNoMargin());

        LinearLayout facts = new LinearLayout(this);
        facts.setOrientation(LinearLayout.VERTICAL);
        facts.setPadding(0, kit.dp(10), 0, kit.dp(4));
        facts.addView(kit.caption(installSkill ? "رابط المهارة" : "مستوى المخاطرة: " + risk), kit.matchNoMargin());
        facts.addView(kit.caption("مجلد التشغيل: " + workdir), kit.matchNoMargin());
        card.addView(facts, kit.matchNoMargin());

        TextView commandView = kit.mono(command, 12, kit.text);
        commandView.setGravity(Gravity.TOP | Gravity.START);
        commandView.setTextIsSelectable(true);
        commandView.setPadding(kit.dp(11), kit.dp(9), kit.dp(11), kit.dp(9));
        commandView.setBackground(kit.round(kit.bg, 12, kit.outline));
        card.addView(commandView, kit.matchNoMargin());

        TextView footnote = kit.caption(installSkill
                ? "سيتم تنزيل تعليمات SKILL.md وحفظها محليًا، ولن يُشغّل أي كود من المهارة أثناء التثبيت."
                : "سيُنفذ الأمر داخل Termux بصلاحيات المستخدم الحالية.");
        footnote.setPadding(0, kit.dp(8), 0, 0);
        card.addView(footnote, kit.matchNoMargin());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button cancel = kit.ghost("إلغاء");
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, kit.dp(44), 1));
        Button copy = kit.ghost(installSkill ? "نسخ الرابط" : "نسخ الأمر");
        copy.setOnClickListener(v -> copyText(installSkill ? "Skill URL" : "AI command", command));
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, kit.dp(44), 1);
        copyLp.leftMargin = kit.dp(8);
        actions.addView(copy, copyLp);
        Button approve = kit.primary(installSkill ? "تنصيب" : "موافقة وتشغيل");
        approve.setOnClickListener(v -> {
            dialog.dismiss();
            executeAgentAction(chatId, action, false);
        });
        LinearLayout.LayoutParams approveLp = new LinearLayout.LayoutParams(0, kit.dp(44), 1.4f);
        approveLp.leftMargin = kit.dp(8);
        actions.addView(approve, approveLp);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = kit.dp(14);
        card.addView(actions, actionsLp);

        kit.showCard(dialog, card);
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
        kit.confirmDialog("التنفيذ التلقائي لهذه الدردشة",
                enabled
                        ? "سيشغّل الوكيل أدوات Shell تلقائيًا في هذه الدردشة من دون تأكيد لكل أمر. الأوامر شديدة التدمير تبقى محجوبة. لا تفعّل هذا إلا إذا كنت تثق بالمهمة."
                        : "ستعود هذه الدردشة إلى طلب موافقتك قبل تشغيل أدوات Shell.",
                enabled ? "تفعيل" : "تعطيل", () -> {
                    try { chat.put("agent_auto_execute", enabled); saveChats(); }
                    catch (Exception ignored) { }
                    AgentWorkspace.persistChatOverride(this, chat.optString("id"), chat.optString("provider", "gemini"), chat.optString("sessionId", ""), Boolean.valueOf(enabled));
                    Toast.makeText(this, enabled ? "التنفيذ التلقائي مفعّل لهذه الدردشة" : "التنفيذ التلقائي متوقف لهذه الدردشة", Toast.LENGTH_LONG).show();
                    updateHeader();
                    renderMessages();
                });
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
                busy = false; updateStatusPill();
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
        busy = true; updateStatusPill(); sendButton.setEnabled(false);
        if (rateLabel != null) rateLabel.setText("يفكّر الموديل ويرسل الرد تدريجيًا…");

        syncAgentWorkspace(() -> {
            JSONObject liveChat = findChat(chatId);
            if (liveChat == null) { busy = false; updateStatusPill(); if (sendButton != null) sendButton.setEnabled(true); return; }
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
                    busy = false; updateStatusPill(); if (sendButton != null) sendButton.setEnabled(true); updateHeader();
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
    private void broadcastPrompt(){String prompt=input.getText().toString().trim();if(prompt.isEmpty())return;if(busy){Toast.makeText(this,"انتظر حتى ينتهي الطلب الحالي",Toast.LENGTH_SHORT).show();return;}kit.confirmDialog("إرسال الأمر إلى كل الدردشات","سيُرسل نفس النص إلى كل دردشة محفوظة لها مفتاح API متاح. قد يستهلك ذلك طلبًا من كل مزوّد. عدد الدردشات: "+chats.length()+". متابعة؟\n\n"+prompt,"إرسال للجميع",()->doBroadcast(prompt));}
    private void doBroadcast(String prompt){input.setText("");JSONArray snapshot=AiSecureStore.cloneArray(chats);String active=activeChatId;busy = true; updateStatusPill();sendButton.setEnabled(false);rateLabel.setText("إرسال متتابع إلى الدردشات…");worker.execute(()->{List<String> summaries=new ArrayList<>();for(int i=0;i<snapshot.length();i++){JSONObject c=snapshot.optJSONObject(i);if(c==null)continue;String pid=c.optString("provider",AiSecureStore.getActiveProvider(this)),chatId=c.optString("id");try{String key=AiSecureStore.getKey(this,pid);if(key.isEmpty()){summaries.add(c.optString("name")+": تخطّيت لعدم وجود مفتاح");continue;}appendChatMessage(c,"user",prompt);String model=AiSecureStore.getModel(this,pid),endpoint=AiSecureStore.getEndpoint(this,pid);JSONArray history=apiHistory(c);String sid=c.optString("sessionId","");if(history.length()>0)history.put(history.length()-1,new JSONObject().put("role","user").put("text",withTerminalContext(prompt,sid)));String answer=AiApiClient.send(this,AiProvider.byId(pid),model,endpoint,key,history,"",2048);appendChatMessage(c,"assistant",answer);summaries.add(c.optString("name")+": وصل رد");int gap=AiSecureStore.settings(this).getInt("rate_gap_seconds",3);if(gap>0)Thread.sleep(gap*1000L);}catch(Exception e){appendChatMessage(c,"assistant","[خطأ أثناء البث]\n"+e.getMessage());summaries.add(c.optString("name")+": "+e.getMessage());}saveChats();}runOnUiThread(()->{busy = false; updateStatusPill();sendButton.setEnabled(true);rebuildChatSpinner();activeChatId=active;chatsSpinner.setSelection(indexOfChat(activeChatId));updateHeader();renderMessages();kit.infoDialog("نتيجة الإرسال الجماعي",android.text.TextUtils.join("\n",summaries));});});}
    private void confirmSuggestedCommand(String chatId,String code){JSONObject c=findChat(chatId);if(c==null)return;String sid=c.optString("sessionId","");JSONObject s=AiSecureStore.findById(sessions,sid);String wd=s==null?"~/":s.optString("workdir","~/");kit.confirmDialog("تأكيد أمر اقترحه AI","راجع الأمر بالكامل قبل تشغيله داخل Termux. أي أمر shell قد يعدّل ملفاتك أو يثبت حزمًا.\n\nمجلد التشغيل: "+wd+"\n\n"+code,"تنفيذ داخل Termux",()->{try{int id=TermuxCommandRunner.run(this,code,wd,"AI approved command",true);commandChats.put(id,chatId);Toast.makeText(this,"تم إرسال الأمر إلى Termux؛ ستُضاف المخرجات إلى الدردشة",Toast.LENGTH_LONG).show();}catch(Exception e){alert(e.getMessage());}});}
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
        busy = false; updateStatusPill();sendButton.setEnabled(true);updateHeader();renderMessages();
        AiSecureStore.savePendingScreenshot(this,chatId,providerId,model,endpoint,System.currentTimeMillis());
        boolean active=AiSecureStore.isCaptureActive(this);
        String message=active
                ? "المساعد يطلب لقطة حديثة من شاشة Termux/TUI. إذا وافقت، سنفتح Termux ونلتقط الشاشة تلقائيًا بعد نحو 4 ثوانٍ. تأكد من إخفاء كلمات السر والرموز الخاصة؛ سترسل اللقطة إلى مزوّد AI المحدد."
                : "المساعد يطلب لقطة حديثة من Termux/TUI. إذا وافقت، ستظهر نافذة أندرويد لمشاركة الشاشة. بعد الموافقة سنفتح Termux ونلتقط الشاشة تلقائيًا بعد نحو 4 ثوانٍ. تأكد من إخفاء كلمات السر والرموز الخاصة؛ سترسل اللقطة إلى مزوّد AI المحدد.";
        kit.confirmDialog("AI يطلب لقطة شاشة",message,active?"فتح Termux والتقاط":"السماح والتقاط",()->{if(active)openTermuxForAgentCapture();else startActivity(new Intent(this,ScreenCaptureActivity.class));});
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
        busy = true; updateStatusPill(); if (sendButton != null) sendButton.setEnabled(false);
        if (rateLabel != null) rateLabel.setText("الوكيل يراجع النتيجة ويختار الخطوة التالية…");
        syncAgentWorkspace(() -> {
            JSONObject liveChat = findChat(chatId);
            if (liveChat == null || isAgentRunCancelled(chatId)) { busy = false; updateStatusPill(); if (sendButton != null) sendButton.setEnabled(true); return; }
            String providerId = liveChat.optString("provider", AiSecureStore.getActiveProvider(this));
            String model = AiSecureStore.getModel(this, providerId), endpoint = AiSecureStore.getEndpoint(this, providerId);
            JSONArray history = apiHistory(liveChat);
            if (history.length() > 0) {
                JSONObject tail = history.optJSONObject(history.length() - 1);
                if (tail != null && "assistant".equals(tail.optString("role", "")) && tail.optString("text", "").trim().isEmpty()) history.remove(history.length() - 1);
            }
            if (history.length() == 0) { busy = false; updateStatusPill(); if (sendButton != null) sendButton.setEnabled(true); return; }
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
                    busy = false; updateStatusPill(); if (sendButton != null) sendButton.setEnabled(true); updateHeader();
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
        renderMessages(); busy = true; updateStatusPill(); if(sendButton!=null)sendButton.setEnabled(false); rateLabel.setText("إرسال لقطة الشاشة وتحليلها تدريجيًا…");
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
                finishStreamingAssistant(chatId,streamId,visible); busy = false; updateStatusPill(); if(sendButton!=null)sendButton.setEnabled(true); updateHeader();
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
        kit.sheet("خيارات الوكيل", items, w -> {
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
        });
    }

    private void showTerminalPopup(){
        sessions=AiSecureStore.loadArray(this,AiSecureStore.SESSIONS,"items");if(sessions.length()==0){startActivity(new Intent(this,TerminalSessionsActivity.class));return;}
        android.app.Dialog dialog=new android.app.Dialog(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(kit.dp(16),kit.dp(14),kit.dp(16),kit.dp(14));box.setBackground(kit.round(kit.panel,22,kit.outline));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);TextView title=kit.text("نافذة Terminal",17,kit.text,true);header.addView(title,new LinearLayout.LayoutParams(0,kit.dp(38),1));Button close=kit.ghost("إغلاق");close.setTextSize(12);header.addView(close,new LinearLayout.LayoutParams(kit.dp(72),kit.dp(36)));close.setOnClickListener(v->dialog.dismiss());box.addView(header);
        Spinner sessionPick=new Spinner(this);List<String> sessionLabels=new ArrayList<>();for(int i=0;i<sessions.length();i++){JSONObject o=sessions.optJSONObject(i);sessionLabels.add(o.optString("name","Terminal")+" · "+o.optString("workdir","~/"));}sessionPick.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,sessionLabels));box.addView(sessionPick,match());
        EditText cmd= new EditText(this);cmd.setHint("أمر Shell متعدد الأسطر");cmd.setTextColor(kit.text);cmd.setHintTextColor(kit.muted);cmd.setTypeface(Typeface.MONOSPACE);cmd.setGravity(Gravity.TOP|Gravity.START);cmd.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);cmd.setMinLines(2);cmd.setMaxLines(5);cmd.setPadding(kit.dp(10),kit.dp(8),kit.dp(10),kit.dp(8));cmd.setBackground(kit.round(kit.panelAlt,12,kit.outline));box.addView(cmd,new LinearLayout.LayoutParams(-1,kit.dp(86)));
        TextView out=kit.mono("جاهز. المخرجات تظهر هنا.",11,kit.text);out.setTextIsSelectable(true);out.setGravity(Gravity.TOP|Gravity.START);out.setPadding(kit.dp(10),kit.dp(9),kit.dp(10),kit.dp(9));out.setBackground(kit.round(kit.bg,10,kit.outline));ScrollView scroll=new ScrollView(this);scroll.addView(out);LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,0,1);sl.topMargin=dp(7);sl.bottomMargin=dp(7);box.addView(scroll,sl);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);Button run=kit.primary("تنفيذ وإظهار المخرجات");run.setTextSize(12);Button tui=kit.ghost("TUI في Termux");tui.setTextSize(12);Button manage=kit.ghost("كل الجلسات");manage.setTextSize(12);actions.addView(run,new LinearLayout.LayoutParams(0,kit.dp(42),1.3f));LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,kit.dp(42),1);tp.leftMargin=kit.dp(6);actions.addView(tui,tp);LinearLayout.LayoutParams mgp=new LinearLayout.LayoutParams(0,kit.dp(42),1);mgp.leftMargin=kit.dp(6);actions.addView(manage,mgp);box.addView(actions);manage.setOnClickListener(v->{dialog.dismiss();startActivity(new Intent(this,TerminalSessionsActivity.class));});
        run.setOnClickListener(v->{String command=cmd.getText().toString().trim();if(command.isEmpty())return;int pos=sessionPick.getSelectedItemPosition();JSONObject session=sessions.optJSONObject(Math.max(0,Math.min(pos,sessions.length()-1)));String sid=session.optString("id"),wd=session.optString("workdir","~/");kit.confirmDialog("تأكيد تشغيل الأمر",command,"تنفيذ",()->{try{int id=TermuxCommandRunner.run(this,command,wd,"TFM popup terminal",true);popupCommands.put(id,new PopupCommand(out,sid));out.append("\n$ "+command+"\n[قيد التنفيذ]\n");}catch(Exception e){alert(e.getMessage());}});});
        tui.setOnClickListener(v->{String command=cmd.getText().toString().trim();if(command.isEmpty())command="exec bash -l";int pos=sessionPick.getSelectedItemPosition();JSONObject session=sessions.optJSONObject(Math.max(0,Math.min(pos,sessions.length()-1)));try{TermuxCommandRunner.openTui(this,command,session.optString("workdir","~/"),"TFM popup TUI");out.append("\n[فتحنا الأمر داخل Termux: "+command+"]\n");}catch(Exception e){alert(e.getMessage());}});
        dialog.setContentView(box);android.view.Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout((int)(getResources().getDisplayMetrics().widthPixels*0.94), (int)(getResources().getDisplayMetrics().heightPixels*0.80));}dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*0.94),(int)(getResources().getDisplayMetrics().heightPixels*0.80));
    }
    private void appendSessionOutput(String sid,String output){JSONArray arr=AiSecureStore.loadArray(this,AiSecureStore.SESSIONS,"items");JSONObject session=AiSecureStore.findById(arr,sid);if(session==null)return;String old=session.optString("output","");if(old.length()>20000)old=old.substring(old.length()-15000);try{session.put("output",old+output);AiSecureStore.saveArray(this,AiSecureStore.SESSIONS,"items",arr);}catch(Exception ignored){}}
    private void chatMenu(){String[] actions={"إعادة تسمية الدردشة","حذف الدردشة","إعدادات الذكاء الاصطناعي","إدارة جلسات Terminal","تبديل وضع التنفيذ التلقائي لهذه الدردشة"};kit.sheet("الدردشة الحالية",actions,w->{if(w==0)renameChat();if(w==1)deleteChat();if(w==2)startActivity(new Intent(this,AiSettingsActivity.class));if(w==3)startActivity(new Intent(this,TerminalSessionsActivity.class));if(w==4)toggleChatAutoExecution();});}
    private void renameChat(){JSONObject c=current();if(c==null)return;kit.promptDialog("اسم الدردشة","اسم جديد للدردشة",c.optString("name"),"حفظ",value->{try{c.put("name",value.isEmpty()?"AI Chat":value);saveChats();rebuildChatSpinner();chatsSpinner.setSelection(indexOfChat(activeChatId));updateHeader();}catch(Exception ignored){}});}
    private void deleteChat(){if(chats.length()<=1){alert("احتفظ بدردشة واحدة على الأقل.");return;}kit.confirmDialog("حذف الدردشة؟","سيُحذف سجل هذه الدردشة المحلي فقط.","حذف",()->{JSONArray n=new JSONArray();for(int i=0;i<chats.length();i++){JSONObject c=chats.optJSONObject(i);if(c!=null&&!activeChatId.equals(c.optString("id")))n.put(c);}chats=n;activeChatId=chats.optJSONObject(0).optString("id");saveChats();rebuildChatSpinner();updateHeader();renderMessages();});}
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
    private void alert(String s){kit.infoDialog(null, s);}
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}

    private void chooseAgentChat() {
        String[] names = chatNames().toArray(new String[0]);
        if (names.length == 0) { newChat(true); return; }
        kit.sheet("اختيار الدردشة", names, which -> {
            JSONObject chat = chats.optJSONObject(which); if (chat == null) return;
            activeChatId = chat.optString("id", ""); saveChats(); updateHeader(); renderMessages();
        });
    }

    private void chooseProviderForChat() {
        List<AiProvider> providers = AiProvider.all(); String[] labels = new String[providers.size()];
        for (int i = 0; i < providers.size(); i++) labels[i] = providers.get(i).name + " · " + AiSecureStore.getModel(this, providers.get(i).id);
        JSONObject chat = current(); if (chat == null) return;
        int checked = AiProvider.indexOf(chat.optString("provider", AiSecureStore.getActiveProvider(this)));
        kit.sheet("المزوّد والنموذج", labels, which -> {
            String provider = providers.get(which).id;
            try { chat.put("provider", provider); } catch (Exception ignored) { }
            saveChats();
            AgentWorkspace.persistChatOverride(this, chat.optString("id"), provider, chat.optString("sessionId", ""), chat.has("agent_auto_execute") ? Boolean.valueOf(chat.optBoolean("agent_auto_execute")) : null);
            updateHeader(); renderMessages();
        });
    }

    private void chooseTerminalSessionForChat() {
        sessions = AiSecureStore.loadArray(this, AiSecureStore.SESSIONS, "items");
        List<String> labels = terminalNames(); String[] names = labels.toArray(new String[0]);
        JSONObject chat = current(); if (chat == null) return;
        int checked = Math.max(0, indexOfSession(chat.optString("sessionId", "")));
        kit.sheet("جلسة Termux المرتبطة", names, which -> {
            String sid = sessionIdAt(which);
            try { chat.put("sessionId", sid); } catch (Exception ignored) { }
            saveChats(); AgentWorkspace.persistChatOverride(this, chat.optString("id"), chat.optString("provider", "gemini"), sid,
                    chat.has("agent_auto_execute") ? Boolean.valueOf(chat.optBoolean("agent_auto_execute")) : null);
            updateHeader(); renderMessages();
        });
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
        busy = true; updateStatusPill(); if (sendButton != null) sendButton.setEnabled(false);
        worker.execute(() -> {
            String result;
            try { result = AgentWebSearch.search(query); }
            catch (Exception e) { result = "Web search failed: " + e.getMessage(); }
            final String output = result;
            runOnUiThread(() -> {
                addAgentToolOutput(chatId, "Web search results", output);
                busy = false; updateStatusPill(); if (sendButton != null) sendButton.setEnabled(true); updateHeader();
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
