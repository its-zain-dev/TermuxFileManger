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
    private final ExecutorService worker=Executors.newFixedThreadPool(3); private final Handler handler=new Handler(Looper.getMainLooper()); private final Map<Integer,String> commandChats=new HashMap<>(); private final Map<Integer,Boolean> agentCommandRequests=new HashMap<>(); private final Map<Integer,PopupCommand> popupCommands=new HashMap<>(); private final Map<String,Integer> autoStepsByChat=new HashMap<>(); private boolean autoScreenshotUsed=false;
    private static final class PopupCommand { final TextView output; final String sessionId; PopupCommand(TextView output,String sessionId){this.output=output;this.sessionId=sessionId;} }
    private final BroadcastReceiver resultReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            int id=i.getIntExtra(CommandResultService.EXTRA_REQUEST_ID,-1);
            String stdout=i.getStringExtra("stdout"),stderr=i.getStringExtra("stderr");
            int exit=i.getIntExtra("exit_code",0),err=i.getIntExtra("err_code",-1);
            String output=(stdout==null?"":stdout)+(stderr==null||stderr.trim().isEmpty()?"":"\n[stderr]\n"+stderr)+"\n[exit="+exit+", err="+err+"]\n";
            PopupCommand popup=popupCommands.remove(id);
            if(popup!=null){appendSessionOutput(popup.sessionId,output);popup.output.append(output);return;}
            String chatId=commandChats.remove(id);if(chatId==null)return;
            boolean agentRequest=agentCommandRequests.remove(id)!=null;
            JSONObject chat=findChat(chatId);if(chat==null)return;
            try{JSONArray m=chat.optJSONArray("messages");if(m==null)m=new JSONArray();m.put(msg("user","[مخرجات Termux]\n"+output));chat.put("messages",m);saveChats();if(chatId.equals(activeChatId))renderMessages();}catch(Exception ignored){}
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
    @Override public void onCreate(Bundle b){super.onCreate(b);initTheme();UiTheme.applyWindow(this);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);chats=AiSecureStore.loadArray(this,AiSecureStore.CHATS,"items");sessions=AiSecureStore.loadArray(this,AiSecureStore.SESSIONS,"items");activeChatId=AiSecureStore.settings(this).getString("active_ai_chat","");if(chats.length()==0)newChat(false);if(findChat(activeChatId)==null)activeChatId=chats.optJSONObject(0).optString("id");build();IntentFilter f=new IntentFilter(CommandResultService.ACTION_RESULT);IntentFilter sf=new IntentFilter(ScreenCaptureService.ACTION_SCREENSHOT_SAVED);if(Build.VERSION.SDK_INT>=33){registerReceiver(resultReceiver,f,Context.RECEIVER_NOT_EXPORTED);registerReceiver(screenshotReceiver,sf,Context.RECEIVER_NOT_EXPORTED);}else{registerReceiver(resultReceiver,f);registerReceiver(screenshotReceiver,sf);}receiverRegistered=true;}
    @Override protected void onDestroy(){if(receiverRegistered){try{unregisterReceiver(resultReceiver);}catch(Exception ignored){}try{unregisterReceiver(screenshotReceiver);}catch(Exception ignored){}}worker.shutdownNow();super.onDestroy();}
    private void initTheme(){lastThemeName=UiTheme.getName(this);theme=UiTheme.get(this);BG=theme.bg;PANEL=theme.panel;PANEL2=theme.panel2;TEXT=theme.text;MUTED=theme.muted;ACCENT=theme.accent;DANGER=theme.danger;DIVIDER=theme.divider;}
    private JSONObject current(){return findChat(activeChatId);} private JSONObject findChat(String id){return AiSecureStore.findById(chats,id);}
    private JSONObject msg(String role,String text){JSONObject o=new JSONObject();try{o.put("role",role);o.put("text",text);o.put("time",System.currentTimeMillis());}catch(Exception ignored){}return o;}
    private void newChat(boolean rebuild){String id=Long.toString(System.nanoTime());JSONObject c=new JSONObject();try{c.put("id",id);c.put("name","دردشة "+(chats.length()+1));c.put("provider",AiSecureStore.getActiveProvider(this));c.put("sessionId",AiSecureStore.settings(this).getString("active_terminal_session",""));c.put("messages",new JSONArray());chats.put(c);activeChatId=id;saveChats();if(rebuild)rebuildChatSpinner();}catch(Exception ignored){}}
    private void saveChats(){AiSecureStore.saveArray(this,AiSecureStore.CHATS,"items",chats);AiSecureStore.settings(this).edit().putString("active_ai_chat",activeChatId).apply();}
    private void build(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(8),dp(14),dp(10));
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back=button("‹",PANEL2,TEXT); back.setTextSize(26);
        header.addView(back,new LinearLayout.LayoutParams(dp(42),dp(42)));
        back.setOnClickListener(v->finish());
        LinearLayout titleBox=new LinearLayout(this); titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(text("AI Agent",19,TEXT,true));
        titleBox.addView(text("وكيل برمجي متصل بـ Termux",11,MUTED,false));
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1); titleLp.leftMargin=dp(8);
        header.addView(titleBox,titleLp);
        Button settings=button("⚙",PANEL,TEXT); header.addView(settings,new LinearLayout.LayoutParams(dp(44),dp(40)));
        settings.setOnClickListener(v->startActivity(new Intent(this,AiSettingsActivity.class)));
        root.addView(header);

        LinearLayout chatBar=new LinearLayout(this); chatBar.setGravity(Gravity.CENTER_VERTICAL);
        chatsSpinner=new Spinner(this); chatsSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,chatNames()));
        chatBar.addView(chatsSpinner,new LinearLayout.LayoutParams(0,dp(42),1));
        Button add=button("+",PANEL2,ACCENT); chatBar.addView(add,new LinearLayout.LayoutParams(dp(42),dp(40)));
        LinearLayout.LayoutParams menuLp=new LinearLayout.LayoutParams(dp(42),dp(40)); menuLp.leftMargin=dp(5);
        Button chatMenuButton=button("⋮",PANEL,TEXT); chatBar.addView(chatMenuButton,menuLp);
        root.addView(chatBar);
        chatsSpinner.setSelection(indexOfChat(activeChatId));
        chatsSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                if(pos>=0&&pos<chats.length()){activeChatId=chats.optJSONObject(pos).optString("id");saveChats();updateHeader();renderMessages();}
            }
        });
        add.setOnClickListener(v->{newChat(true);updateHeader();renderMessages();});
        chatMenuButton.setOnClickListener(v->chatMenu());

        TextView providerTitle=text("المزوّد",11,MUTED,true);
        LinearLayout.LayoutParams smallTop=match(); smallTop.topMargin=dp(5);
        root.addView(providerTitle,smallTop);
        providerSpinner=new Spinner(this); List<String> pnames=new ArrayList<>();
        for(AiProvider p:AiProvider.all())pnames.add(p.name);
        providerSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,pnames));
        root.addView(providerSpinner,new LinearLayout.LayoutParams(-1,dp(40)));
        modelLabel=text("Model",10,MUTED,false);
        modelLabel.setPadding(dp(3),dp(2),0,dp(2)); root.addView(modelLabel);

        TextView terminalTitle=text("سياق جلسة Termux",11,MUTED,true);
        root.addView(terminalTitle);
        terminalSpinner=new Spinner(this); terminalSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,terminalNames()));
        root.addView(terminalSpinner,new LinearLayout.LayoutParams(-1,dp(38)));
        providerSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                if(updatingSpinners)return; JSONObject c=current(); if(c==null||pos<0||pos>=AiProvider.all().size())return;
                try{c.put("provider",AiProvider.all().get(pos).id);saveChats();updateHeader();}catch(Exception ignored){}
            }
        });
        terminalSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                if(updatingSpinners)return; JSONObject c=current();
                if(c!=null)try{c.put("sessionId",sessionIdAt(pos));saveChats();}catch(Exception ignored){}
            }
        });

        rateLabel=text("جاهز",10,MUTED,false); rateLabel.setPadding(dp(3),dp(3),0,dp(4)); root.addView(rateLabel);
        messagesScroll=new ScrollView(this); messagesScroll.setFillViewport(true);
        messagesBox=new LinearLayout(this); messagesBox.setOrientation(LinearLayout.VERTICAL);
        messagesBox.setPadding(dp(2),dp(2),dp(2),dp(5)); messagesScroll.addView(messagesBox);
        root.addView(messagesScroll,new LinearLayout.LayoutParams(-1,0,1));

        attachmentLabel=text("ماكو صورة مرفقة",10,MUTED,false);
        attachmentLabel.setPadding(dp(3),dp(3),dp(3),dp(3)); root.addView(attachmentLabel);
        input=new EditText(this); input.setTextColor(TEXT); input.setHintTextColor(MUTED); input.setTextSize(14);
        input.setGravity(Gravity.TOP|Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setMinLines(2); input.setMaxLines(5);
        input.setHint("اكتب الهدف؛ الوكيل يفحص، ينفّذ أداة، ويراجع النتيجة…");
        input.setPadding(dp(12),dp(9),dp(12),dp(9)); input.setBackground(round(PANEL2,13,DIVIDER));
        root.addView(input,new LinearLayout.LayoutParams(-1,dp(88)));

        LinearLayout actions=new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL);
        Button send=button("إرسال إلى الوكيل",ACCENT,BG);
        actions.addView(send,new LinearLayout.LayoutParams(0,dp(44),1));
        Button screenshot=button("لقطة",PANEL,TEXT);
        LinearLayout.LayoutParams shotLp=new LinearLayout.LayoutParams(dp(76),dp(44)); shotLp.leftMargin=dp(6);
        actions.addView(screenshot,shotLp);
        Button more=button("⋮",PANEL,TEXT); more.setTextSize(21);
        LinearLayout.LayoutParams moreLp=new LinearLayout.LayoutParams(dp(44),dp(44)); moreLp.leftMargin=dp(6);
        actions.addView(more,moreLp); root.addView(actions);
        sendButton=send; send.setOnClickListener(v->sendCurrent());
        screenshot.setOnClickListener(v->captureOrAttach());
        more.setOnClickListener(v->showAgentMenu());
        updateHeader(); renderMessages();
    }

    private void updateHeader(){JSONObject c=current();if(c==null)return;String provider=c.optString("provider",AiSecureStore.getActiveProvider(this));int pi=AiProvider.indexOf(provider);updatingSpinners=true;if(providerSpinner.getSelectedItemPosition()!=pi)providerSpinner.setSelection(pi);AiProvider p=AiProvider.byId(provider);modelLabel.setText("Model: "+AiSecureStore.getModel(this,p.id));rateLabel.setText("متبقي اليوم: "+AiSecureStore.remainingRequests(this,p.id)+" طلب · حدود محلية");int si=indexOfSession(c.optString("sessionId",""));if(terminalSpinner!=null&&terminalSpinner.getSelectedItemPosition()!=si)terminalSpinner.setSelection(Math.max(0,si));updatingSpinners=false;}
    private void renderMessages(){if(messagesBox==null)return;messagesBox.removeAllViews();JSONObject c=current();if(c==null)return;JSONArray m=c.optJSONArray("messages");if(m==null||m.length()==0){TextView welcome=text("المساعد جاهز. اضبط المزوّد والمفتاح من ⚙. اربط الدردشة بجلسة Terminal للحصول على سياق آخر مخرجاتها. كل أمر يقترحه الذكاء الاصطناعي يحتاج موافقتك قبل التنفيذ.",13,MUTED,false);welcome.setPadding(dp(12),dp(12),dp(12),dp(12));welcome.setBackground(round(PANEL,12,DIVIDER));messagesBox.addView(welcome,match());return;}for(int i=0;i<m.length();i++){JSONObject msg=m.optJSONObject(i);if(msg!=null)addMessageView(msg,c.optString("id"));}messagesScroll.post(()->messagesScroll.fullScroll(View.FOCUS_DOWN));}
    private void addMessageView(JSONObject m, String chatId) {
        String role = m.optString("role", "user"), txt = m.optString("text", "");
        boolean assistant = "assistant".equals(role), terminal = "terminal".equals(role);
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(11), dp(9), dp(11), dp(9));
        bubble.setBackground(round(assistant ? PANEL2 : (terminal ? Color.rgb(27,34,42) : PANEL), 13, assistant ? ACCENT : DIVIDER));
        TextView label = text(assistant ? AiProvider.byId(providerFor(chatId)).name : (terminal ? "TERMUX" : "أنت"), 10, assistant ? ACCENT : MUTED, true);
        bubble.addView(label, match());
        JSONObject action = assistant ? extractAgentAction(txt) : null;
        if (action != null) {
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
        LinearLayout.LayoutParams p = match();
        p.topMargin = dp(5);
        p.width = ViewGroup.LayoutParams.MATCH_PARENT;
        p.gravity = assistant ? Gravity.START : Gravity.END;
        messagesBox.addView(bubble, p);
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

    private JSONObject parseAgentActionCandidate(String candidate){
        try{JSONObject root=new JSONObject(candidate.trim());return root.optJSONObject("tfm_action");}catch(Exception ignored){return null;}
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
        JSONObject chat = current();
        if (chat == null) return;
        boolean enabled = !autoExecutionEnabled(chat.optString("id", ""));
        new AlertDialog.Builder(this).setTitle("التنفيذ التلقائي لهذه الدردشة")
                .setMessage(enabled
                        ? "سيتم تشغيل أدوات Shell وتثبيت المهارات تلقائيًا في هذه الدردشة، من دون تأكيد لكل أمر. الأوامر الشديدة التدمير تبقى محجوبة."
                        : "ستعود هذه الدردشة إلى طلب موافقتك قبل تشغيل كل أداة.")
                .setPositiveButton(enabled ? "تفعيل" : "تعطيل", (d, w) -> {
                    try { chat.put("agent_auto_execute", enabled); saveChats(); }
                    catch (Exception ignored) {}
                    Toast.makeText(this, enabled ? "التنفيذ التلقائي مفعّل لهذه الدردشة" : "التنفيذ التلقائي متوقف لهذه الدردشة", Toast.LENGTH_LONG).show();
                    renderMessages();
                }).setNegativeButton("إلغاء", null).show();
    }

    private void executeAgentAction(String chatId, JSONObject action, boolean automatic) {
        JSONObject chat = findChat(chatId);
        if (chat == null || action == null) return;
        if (automatic) {
            int current = autoStepsByChat.containsKey(chatId) ? autoStepsByChat.get(chatId) : 0;
            int max = Math.max(1, Math.min(100, AiSecureStore.settings(this).getInt("agent_auto_max_steps", 16)));
            if (current >= max) {
                appendChatMessage(chat, "assistant", "توقف التنفيذ التلقائي بعد " + max + " خطوة لهذه المهمة حتى لا يدخل الوكيل في حلقة لا نهائية. راجع آخر مخرجاته ثم ابدأ مهمة جديدة أو زد الحد من الإعدادات.");
                saveChats(); if (chatId.equals(activeChatId)) renderMessages();
                return;
            }
            autoStepsByChat.put(chatId, current + 1);
        }
        String tool = action.optString("tool", action.optString("name", "run_shell"));
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
    private void sendCurrent(){String text=input.getText().toString().trim();if(text.isEmpty())return;if(busy){Toast.makeText(this,"انتظر الرد الحالي أو افتح دردشة أخرى",Toast.LENGTH_SHORT).show();return;}String expanded=AgentSkillManager.expandSlashCommand(this,text);if(expanded==null){Toast.makeText(this,"المهارة غير مثبتة. افتح إعدادات AI ثم Agent Skills وثبّت رابط SKILL.md أولًا.",Toast.LENGTH_LONG).show();return;}JSONObject c=current();if(c==null)return;String image=attachedImage;attachedImage="";autoScreenshotUsed=false;attachmentLabel.setText("ماكو صورة مرفقة");input.setText("");appendChatMessage(c,"user",text);autoStepsByChat.put(c.optString("id"),0);if(!expanded.equals(text)){try{JSONArray saved=c.optJSONArray("messages");JSONObject last=saved==null?null:saved.optJSONObject(saved.length()-1);if(last!=null)last.put("expanded_prompt",expanded);}catch(Exception ignored){}}String sid=c.optString("sessionId","");String enriched=withTerminalContext(expanded,sid);JSONArray requestHistory=apiHistory(c);if(requestHistory.length()>0){try{requestHistory.put(requestHistory.length()-1,new JSONObject().put("role","user").put("text",enriched));}catch(Exception ignored){}}saveChats();renderMessages();busy=true;sendButton.setEnabled(false);rateLabel.setText("جاري الاتصال بالمزوّد…");final String chatId=c.optString("id"),providerId=c.optString("provider",AiSecureStore.getActiveProvider(this));final String model=AiSecureStore.getModel(this,providerId),endpoint=AiSecureStore.getEndpoint(this,providerId),imagePath=image;
        worker.execute(()->{String response;try{String key=AiSecureStore.getKey(this,providerId);response=AiApiClient.send(this,AiProvider.byId(providerId),model,endpoint,key,requestHistory,imagePath,2048);}catch(Exception e){response="[تعذر إكمال الطلب]\n"+e.getMessage();}final String result=response;runOnUiThread(()->{JSONObject live=findChat(chatId);String visible=result.replace("[TFM_SCREENSHOT]","").replace("[REQUEST_SCREENSHOT]","").trim();if(live!=null){appendChatMessage(live,"assistant",visible);saveChats();}boolean requested=(result.contains("[TFM_SCREENSHOT]")||result.contains("[REQUEST_SCREENSHOT]"));if(requested&&!autoScreenshotUsed){autoScreenshotUsed=true;beginAgentScreenshotCapture(chatId,providerId,model,endpoint);return;}else{busy=false;sendButton.setEnabled(true);}updateHeader();renderMessages();JSONObject next=extractAgentAction(visible);if(next!=null&&autoExecutionEnabled(chatId))executeAgentAction(chatId,next,true);});});
    }
    private void appendChatMessage(JSONObject c,String role,String text){try{JSONArray a=c.optJSONArray("messages");if(a==null)a=new JSONArray();a.put(msg(role,text));while(a.length()>80){JSONArray n=new JSONArray();for(int i=1;i<a.length();i++)n.put(a.opt(i));a=n;}c.put("messages",a);}catch(Exception ignored){}}
    private JSONArray apiHistory(JSONObject c){JSONArray src=c.optJSONArray("messages"),out=new JSONArray();if(src==null)return out;int start=Math.max(0,src.length()-32);for(int i=start;i<src.length();i++){JSONObject m=src.optJSONObject(i);if(m==null)continue;String role=m.optString("role","user");if("terminal".equals(role))role="user";if(!"assistant".equals(role)&&!"user".equals(role))continue;String historyText=("user".equals(role)&&m.has("expanded_prompt"))?m.optString("expanded_prompt",m.optString("text","")):m.optString("text","");out.put(msg(role,historyText));}return out;}
    private String withTerminalContext(String userText,String sid){JSONObject s=AiSecureStore.findById(sessions,sid);if(s==null)return userText;String out=s.optString("output","");if(out.isEmpty())return userText;out=out.length()>10000?out.substring(out.length()-10000):out;return userText+"\n\n[Terminal session: "+s.optString("name","Terminal")+"; workdir="+s.optString("workdir","~/")+"]\n[Recent terminal output]\n"+out+"\n[حلّل المخرجات عند صلتها بالسؤال، ولا تفترض أن آخر أمر نجح ما لم تظهر النتيجة].";}
    private void broadcastPrompt(){String prompt=input.getText().toString().trim();if(prompt.isEmpty())return;if(busy){Toast.makeText(this,"انتظر حتى ينتهي الطلب الحالي",Toast.LENGTH_SHORT).show();return;}new AlertDialog.Builder(this).setTitle("إرسال الأمر إلى كل الدردشات").setMessage("سيُرسل نفس النص إلى كل دردشة محفوظة لها مفتاح API متاح. قد يستهلك ذلك طلبًا من كل مزوّد. عدد الدردشات: "+chats.length()+". متابعة؟\n\n"+prompt).setPositiveButton("إرسال للجميع",(d,w)->doBroadcast(prompt)).setNegativeButton("إلغاء",null).show();}
    private void doBroadcast(String prompt){input.setText("");JSONArray snapshot=AiSecureStore.cloneArray(chats);String active=activeChatId;busy=true;sendButton.setEnabled(false);rateLabel.setText("إرسال متتابع إلى الدردشات…");worker.execute(()->{List<String> summaries=new ArrayList<>();for(int i=0;i<snapshot.length();i++){JSONObject c=snapshot.optJSONObject(i);if(c==null)continue;String pid=c.optString("provider",AiSecureStore.getActiveProvider(this)),chatId=c.optString("id");try{String key=AiSecureStore.getKey(this,pid);if(key.isEmpty()){summaries.add(c.optString("name")+": تخطّيت لعدم وجود مفتاح");continue;}appendChatMessage(c,"user",prompt);String model=AiSecureStore.getModel(this,pid),endpoint=AiSecureStore.getEndpoint(this,pid);JSONArray history=apiHistory(c);String sid=c.optString("sessionId","");if(history.length()>0)history.put(history.length()-1,new JSONObject().put("role","user").put("text",withTerminalContext(prompt,sid)));String answer=AiApiClient.send(this,AiProvider.byId(pid),model,endpoint,key,history,"",2048);appendChatMessage(c,"assistant",answer);summaries.add(c.optString("name")+": وصل رد");int gap=AiSecureStore.settings(this).getInt("rate_gap_seconds",3);if(gap>0)Thread.sleep(gap*1000L);}catch(Exception e){appendChatMessage(c,"assistant","[خطأ أثناء البث]\n"+e.getMessage());summaries.add(c.optString("name")+": "+e.getMessage());}saveChats();}runOnUiThread(()->{busy=false;sendButton.setEnabled(true);rebuildChatSpinner();activeChatId=active;chatsSpinner.setSelection(indexOfChat(activeChatId));updateHeader();renderMessages();new AlertDialog.Builder(this).setTitle("نتيجة الإرسال الجماعي").setMessage(android.text.TextUtils.join("\n",summaries)).setPositiveButton("حسنًا",null).show();});});}
    private void confirmSuggestedCommand(String chatId,String code){JSONObject c=findChat(chatId);if(c==null)return;String sid=c.optString("sessionId","");JSONObject s=AiSecureStore.findById(sessions,sid);String wd=s==null?"~/":s.optString("workdir","~/");new AlertDialog.Builder(this).setTitle("تأكيد أمر اقترحه AI").setMessage("راجع الأمر بالكامل قبل تشغيله داخل Termux. أي أمر shell قد يعدّل ملفاتك أو يثبت حزمًا.\n\nمجلد التشغيل: "+wd+"\n\n"+code).setPositiveButton("تنفيذ داخل Termux",(d,w)->{try{int id=TermuxCommandRunner.run(this,code,wd,"AI approved command",true);commandChats.put(id,chatId);Toast.makeText(this,"تم إرسال الأمر إلى Termux؛ ستُضاف المخرجات إلى الدردشة",Toast.LENGTH_LONG).show();}catch(Exception e){alert(e.getMessage());}}).setNeutralButton("نسخ الأمر",(d,w)->{ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("AI command",code));}).setNegativeButton("إلغاء",null).show();}
    private String firstCodeBlock(String s){Matcher m=Pattern.compile("```(?:[a-zA-Z0-9_+.-]+)?\\s*([\\s\\S]*?)```").matcher(s==null?"":s);return m.find()?m.group(1).trim():"";}
    private void captureOrAttach(){
        if(!AiSecureStore.isCaptureActive(this)){startActivity(new Intent(this,ScreenCaptureActivity.class));Toast.makeText(this,"اسمح بالتقاط الشاشة من حوار أندرويد. بعد ذلك اعرض Termux أو TUI واستخدم زر لقطة الشاشة في إشعار TFM، ثم ارجع هنا واضغط لقطة لإرفاق الصورة الأخيرة.",Toast.LENGTH_LONG).show();return;}
        String last=AiSecureStore.lastScreenshot(this);long now=System.currentTimeMillis(),stamp=AiSecureStore.lastScreenshotAt(this);
        if(!last.isEmpty()&&new File(last).isFile()&&stamp>AiSecureStore.lastScreenshotUsedAt(this)&&now-stamp<120000){attachedImage=last;AiSecureStore.markLastScreenshotUsed(this);attachmentLabel.setText("لقطة حديثة مرفقة · سترسل مع الرسالة التالية");Toast.makeText(this,"أرفقنا آخر لقطة صُوّرت من الإشعار؛ لن نلتقط إطارًا جديدًا",Toast.LENGTH_SHORT).show();return;}
        long before=stamp;attachedImage="";try{startService(new Intent(this,ScreenCaptureService.class).setAction(ScreenCaptureService.ACTION_CAPTURE));Toast.makeText(this,"طلبنا لقطة للشاشة الحالية. انتظر قليلًا ثم ستُرفق إذا اكتملت.",Toast.LENGTH_SHORT).show();handler.postDelayed(()->{String path=AiSecureStore.lastScreenshot(this);long after=AiSecureStore.lastScreenshotAt(this);if(!path.isEmpty()&&new File(path).isFile()&&after>before){attachedImage=path;AiSecureStore.markLastScreenshotUsed(this);attachmentLabel.setText("لقطة مرفقة · سترسل مع الرسالة التالية");Toast.makeText(this,"تم إرفاق اللقطة الجديدة",Toast.LENGTH_SHORT).show();}else Toast.makeText(this,"ما وصل إطار جديد بعد؛ اعرض الشاشة المطلوبة واضغط زر لقطة في إشعار TFM.",Toast.LENGTH_LONG).show();},1900);}catch(Exception e){alert(e.getMessage());}
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
        JSONObject chat = findChat(chatId);
        if (chat == null) return;
        String providerId = chat.optString("provider", AiSecureStore.getActiveProvider(this));
        String model = AiSecureStore.getModel(this, providerId), endpoint = AiSecureStore.getEndpoint(this, providerId);
        JSONArray history = apiHistory(chat);
        if (history.length() == 0) return;
        try {
            JSONObject last = history.optJSONObject(history.length() - 1);
            if (last != null) { String value = last.optString("text", ""); if (value.length() > 12000) last.put("text", value.substring(value.length() - 12000)); }
        } catch (Exception ignored) {}
        busy = true;
        if (sendButton != null) sendButton.setEnabled(false);
        rateLabel.setText("الوكيل يحلل المخرجات ويقرر الخطوة التالية…");
        worker.execute(() -> {
            String answer;
            try {
                long lastCall = AiSecureStore.settings(this).getLong("rate_last_" + providerId, 0);
                int gap = Math.max(0, AiSecureStore.settings(this).getInt("rate_gap_seconds", 3));
                long wait = gap * 1000L - (System.currentTimeMillis() - lastCall);
                if (wait > 30000L) throw new Exception("الفاصل المحلي المتبقي أطول من 30 ثانية؛ أرسل متابعة لاحقًا.");
                if (wait > 0) Thread.sleep(wait);
                answer = AiApiClient.send(this, AiProvider.byId(providerId), model, endpoint, AiSecureStore.getKey(this, providerId), history, "", 2048);
            } catch (Exception e) { answer = "[لم يكتمل تحليل مخرجات Termux]\n" + e.getMessage(); }
            final String result = answer;
            runOnUiThread(() -> {
                JSONObject live = findChat(chatId);
                if (live != null) { appendChatMessage(live, "assistant", result); saveChats(); }
                busy = false;
                if (sendButton != null) sendButton.setEnabled(true);
                updateHeader();
                if (chatId.equals(activeChatId)) renderMessages();
                JSONObject next = extractAgentAction(result);
                if (next != null && autoExecutionEnabled(chatId)) executeAgentAction(chatId, next, true);
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
        JSONObject chat=findChat(chatId);if(chat==null)return;
        appendChatMessage(chat,"user","[لقطة الشاشة التي طلبتها مرفقة الآن. حلّل ما يظهر فيها، خاصة مخرجات الطرفية أو TUI.] ");saveChats();renderMessages();busy=true;if(sendButton!=null)sendButton.setEnabled(false);rateLabel.setText("إرسال لقطة الشاشة إلى المزوّد…");
        JSONArray history=apiHistory(chat);
        worker.execute(()->{String answer;try{answer=AiApiClient.send(this,AiProvider.byId(providerId),model,endpoint,AiSecureStore.getKey(this,providerId),history,image,2048);}catch(Exception e){answer="[تعذر تحليل لقطة الشاشة]\n"+e.getMessage();}final String result=answer;runOnUiThread(()->{JSONObject live=findChat(chatId);String visible=result.replace("[TFM_SCREENSHOT]","").replace("[REQUEST_SCREENSHOT]","").trim();if(live!=null){appendChatMessage(live,"assistant",visible);saveChats();}busy=false;if(sendButton!=null)sendButton.setEnabled(true);updateHeader();renderMessages();if(result.contains("[TFM_SCREENSHOT]")||result.contains("[REQUEST_SCREENSHOT]"))Toast.makeText(this,"تجاهلنا طلب لقطة إضافية في نفس الدور لمنع الالتقاط المتكرر.",Toast.LENGTH_LONG).show();});});
    }
    private void showAgentMenu(){
        String[] items={"إرسال الطلب إلى كل الدردشات","إدارة جلسات Terminal","أوامر Terminal سريعة","فتح تطبيق Termux","حذف آخر لقطة","إيقاف مشاركة الشاشة"};
        new AlertDialog.Builder(this).setTitle("أدوات الوكيل").setItems(items,(d,w)->{
            if(w==0)broadcastPrompt();
            else if(w==1)startActivity(new Intent(this,TerminalSessionsActivity.class));
            else if(w==2)showTerminalPopup();
            else if(w==3)openTermux();
            else if(w==4){AiSecureStore.clearLastScreenshot(this);attachedImage="";attachmentLabel.setText("ماكو صورة مرفقة");Toast.makeText(this,"تم حذف آخر لقطة",Toast.LENGTH_SHORT).show();}
            else {try{startService(new Intent(this,ScreenCaptureService.class).setAction(ScreenCaptureService.ACTION_STOP));}catch(Exception ignored){}AiSecureStore.setCaptureActive(this,false);Toast.makeText(this,"تم طلب إيقاف مشاركة الشاشة",Toast.LENGTH_SHORT).show();}
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
}
