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
    private final ExecutorService worker=Executors.newFixedThreadPool(3); private final Handler handler=new Handler(Looper.getMainLooper()); private final Map<Integer,String> commandChats=new HashMap<>(); private final Map<Integer,PopupCommand> popupCommands=new HashMap<>(); private boolean autoScreenshotUsed=false;
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
            JSONObject chat=findChat(chatId);if(chat==null)return;
            try{JSONArray m=chat.optJSONArray("messages");if(m==null)m=new JSONArray();m.put(msg("user","[مخرجات Termux]\n"+output));chat.put("messages",m);saveChats();if(chatId.equals(activeChatId))renderMessages();}catch(Exception ignored){}
            if(AiSecureStore.settings(AiAssistantActivity.this).getBoolean("auto_analyze_results",false)){
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
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(7),dp(12),dp(9));root.setBackgroundColor(BG);setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);Button back=button("‹",PANEL2,TEXT);back.setTextSize(26);header.addView(back,new LinearLayout.LayoutParams(dp(42),dp(42)));back.setOnClickListener(v->finish());TextView title=text("AI Assistant Agent",18,TEXT,true);LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(0,dp(42),1);tl.leftMargin=dp(6);header.addView(title,tl);Button settings=button("⚙",PANEL,TEXT);header.addView(settings,new LinearLayout.LayoutParams(dp(43),dp(40)));settings.setOnClickListener(v->startActivity(new Intent(this,AiSettingsActivity.class)));root.addView(header);
        LinearLayout chatBar=new LinearLayout(this);chatBar.setGravity(Gravity.CENTER_VERTICAL);chatsSpinner=new Spinner(this);chatsSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,chatNames()));chatBar.addView(chatsSpinner,new LinearLayout.LayoutParams(0,dp(40),1));Button add=button("+",PANEL2,ACCENT);LinearLayout.LayoutParams al=new LinearLayout.LayoutParams(dp(42),dp(38));al.leftMargin=dp(5);chatBar.addView(add,al);Button rename=button("⋮",PANEL,TEXT);LinearLayout.LayoutParams rl=new LinearLayout.LayoutParams(dp(42),dp(38));rl.leftMargin=dp(4);chatBar.addView(rename,rl);root.addView(chatBar);chatsSpinner.setSelection(indexOfChat(activeChatId));chatsSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){if(pos>=0&&pos<chats.length()){activeChatId=chats.optJSONObject(pos).optString("id");saveChats();updateHeader();renderMessages();}}});add.setOnClickListener(v->{newChat(true);updateHeader();renderMessages();});rename.setOnClickListener(v->chatMenu());
        LinearLayout providerRow=new LinearLayout(this);providerRow.setGravity(Gravity.CENTER_VERTICAL);providerSpinner=new Spinner(this);List<String> pnames=new ArrayList<>();for(AiProvider p:AiProvider.all())pnames.add(p.name);providerSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,pnames));providerRow.addView(providerSpinner,new LinearLayout.LayoutParams(0,dp(38),1));modelLabel=text("model",10,MUTED,false);LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(0,dp(38),1);ml.leftMargin=dp(5);providerRow.addView(modelLabel,ml);root.addView(providerRow);
        terminalSpinner=new Spinner(this);terminalSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,terminalNames()));root.addView(terminalSpinner,new LinearLayout.LayoutParams(-1,dp(36)));providerSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){if(updatingSpinners)return;JSONObject c=current();if(c==null||pos<0||pos>=AiProvider.all().size())return;String provider=AiProvider.all().get(pos).id;try{c.put("provider",provider);saveChats();updateHeader();}catch(Exception ignored){}}});terminalSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){if(updatingSpinners)return;JSONObject c=current();if(c!=null){try{c.put("sessionId",sessionIdAt(pos));saveChats();}catch(Exception ignored){}}}});
        rateLabel=text("Ready · local rate limit enabled",10,MUTED,false);rateLabel.setPadding(dp(3),dp(4),0,dp(4));root.addView(rateLabel);
        messagesScroll=new ScrollView(this);messagesBox=new LinearLayout(this);messagesBox.setOrientation(LinearLayout.VERTICAL);messagesBox.setPadding(dp(2),dp(2),dp(2),dp(4));messagesScroll.addView(messagesBox);root.addView(messagesScroll,new LinearLayout.LayoutParams(-1,0,1));
        attachmentLabel=text("ماكو صورة مرفقة",10,MUTED,false);attachmentLabel.setPadding(dp(3),dp(3),dp(3),dp(3));root.addView(attachmentLabel);
        input=new EditText(this);input.setTextColor(TEXT);input.setHintTextColor(MUTED);input.setTextSize(14);input.setGravity(Gravity.TOP|Gravity.START);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setMinLines(2);input.setMaxLines(6);input.setHint("احچي ويا الوكيل: حلل الخطأ، اقترح إصلاحًا أو أوامر…");input.setPadding(dp(10),dp(7),dp(10),dp(7));input.setBackground(round(PANEL2,11,DIVIDER));root.addView(input,new LinearLayout.LayoutParams(-1,dp(90)));
        LinearLayout actions=new LinearLayout(this);Button send=button("إرسال",PANEL2,ACCENT);Button all=button("إرسال لكل الدردشات",PANEL,TEXT);Button screenshot=button("لقطة",PANEL,TEXT);Button sessionsBtn=button("Terminal",PANEL,TEXT);actions.addView(send,new LinearLayout.LayoutParams(0,dp(42),1));LinearLayout.LayoutParams x=new LinearLayout.LayoutParams(0,dp(42),1);x.leftMargin=dp(4);actions.addView(all,x);LinearLayout.LayoutParams sx=new LinearLayout.LayoutParams(dp(54),dp(42));sx.leftMargin=dp(4);actions.addView(screenshot,sx);LinearLayout.LayoutParams tx=new LinearLayout.LayoutParams(dp(74),dp(42));tx.leftMargin=dp(4);actions.addView(sessionsBtn,tx);root.addView(actions);
        sendButton=send;send.setOnClickListener(v->sendCurrent());all.setOnClickListener(v->broadcastPrompt());screenshot.setOnClickListener(v->captureOrAttach());sessionsBtn.setOnClickListener(v->showTerminalPopup());
        LinearLayout bottom=new LinearLayout(this);Button open=button("فتح Termux",PANEL,TEXT);Button clearShot=button("حذف آخر لقطة",PANEL,TEXT);Button stopCapture=button("إيقاف مشاركة الشاشة",PANEL,DANGER);bottom.addView(open,new LinearLayout.LayoutParams(0,dp(34),1));LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(0,dp(34),1);cl.leftMargin=dp(4);bottom.addView(clearShot,cl);LinearLayout.LayoutParams sc=new LinearLayout.LayoutParams(0,dp(34),1);sc.leftMargin=dp(4);bottom.addView(stopCapture,sc);open.setOnClickListener(v->{try{Intent i=getPackageManager().getLaunchIntentForPackage("com.termux");if(i!=null)startActivity(i);else alert("Termux غير مثبت.");}catch(Exception e){alert(e.getMessage());}});clearShot.setOnClickListener(v->{AiSecureStore.clearLastScreenshot(this);attachedImage="";attachmentLabel.setText("ماكو صورة مرفقة");Toast.makeText(this,"تم حذف آخر لقطة من مساحة التطبيق",Toast.LENGTH_SHORT).show();});stopCapture.setOnClickListener(v->{try{startService(new Intent(this,ScreenCaptureService.class).setAction(ScreenCaptureService.ACTION_STOP));}catch(Exception ignored){}AiSecureStore.setCaptureActive(this,false);Toast.makeText(this,"تم طلب إيقاف مشاركة الشاشة",Toast.LENGTH_SHORT).show();});root.addView(bottom);
        updateHeader();renderMessages();
    }
    private void updateHeader(){JSONObject c=current();if(c==null)return;String provider=c.optString("provider",AiSecureStore.getActiveProvider(this));int pi=AiProvider.indexOf(provider);updatingSpinners=true;if(providerSpinner.getSelectedItemPosition()!=pi)providerSpinner.setSelection(pi);AiProvider p=AiProvider.byId(provider);modelLabel.setText("Model: "+AiSecureStore.getModel(this,p.id));rateLabel.setText("متبقي اليوم: "+AiSecureStore.remainingRequests(this,p.id)+" طلب · حدود محلية");int si=indexOfSession(c.optString("sessionId",""));if(terminalSpinner!=null&&terminalSpinner.getSelectedItemPosition()!=si)terminalSpinner.setSelection(Math.max(0,si));updatingSpinners=false;}
    private void renderMessages(){if(messagesBox==null)return;messagesBox.removeAllViews();JSONObject c=current();if(c==null)return;JSONArray m=c.optJSONArray("messages");if(m==null||m.length()==0){TextView welcome=text("المساعد جاهز. اضبط المزوّد والمفتاح من ⚙. اربط الدردشة بجلسة Terminal للحصول على سياق آخر مخرجاتها. كل أمر يقترحه الذكاء الاصطناعي يحتاج موافقتك قبل التنفيذ.",13,MUTED,false);welcome.setPadding(dp(12),dp(12),dp(12),dp(12));welcome.setBackground(round(PANEL,12,DIVIDER));messagesBox.addView(welcome,match());return;}for(int i=0;i<m.length();i++){JSONObject msg=m.optJSONObject(i);if(msg!=null)addMessageView(msg,c.optString("id"));}messagesScroll.post(()->messagesScroll.fullScroll(View.FOCUS_DOWN));}
    private void addMessageView(JSONObject m,String chatId){String role=m.optString("role","user"),txt=m.optString("text","");boolean assistant="assistant".equals(role);boolean terminal="terminal".equals(role);LinearLayout bubble=new LinearLayout(this);bubble.setOrientation(LinearLayout.VERTICAL);bubble.setPadding(dp(10),dp(8),dp(10),dp(8));bubble.setBackground(round(assistant?PANEL2:(terminal?Color.rgb(32,38,46):PANEL),11,assistant?ACCENT:DIVIDER));TextView label=text(assistant?AiProvider.byId(providerFor(chatId)).name:(terminal?"TERMUX":"أنت"),10,assistant?ACCENT:MUTED,true);bubble.addView(label,match());TextView content=text(txt,13,TEXT,false);content.setTextIsSelectable(true);content.setTypeface(assistant?Typeface.DEFAULT:Typeface.DEFAULT);content.setGravity(Gravity.TOP|Gravity.START);LinearLayout.LayoutParams cp=match();cp.topMargin=dp(3);bubble.addView(content,cp);
        if(assistant){String code=firstCodeBlock(txt);if(!code.trim().isEmpty()){Button run=button("مراجعة وتنفيذ أمر من الرد",PANEL,ACCENT);run.setOnClickListener(v->confirmSuggestedCommand(chatId,code));bubble.addView(run,match());}Button copy=button("نسخ الرد",PANEL,TEXT);copy.setOnClickListener(v->{ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("AI response",txt));Toast.makeText(this,"انسخ الرد",Toast.LENGTH_SHORT).show();});bubble.addView(copy,match());}
        LinearLayout.LayoutParams p=match();p.topMargin=dp(4);p.gravity=assistant?Gravity.START:Gravity.END;p.width=ViewGroup.LayoutParams.MATCH_PARENT;messagesBox.addView(bubble,p);
    }
    private String providerFor(String chatId){JSONObject c=findChat(chatId);return c==null?"gemini":c.optString("provider","gemini");}
    private void sendCurrent(){String text=input.getText().toString().trim();if(text.isEmpty())return;if(busy){Toast.makeText(this,"انتظر الرد الحالي أو افتح دردشة أخرى",Toast.LENGTH_SHORT).show();return;}JSONObject c=current();if(c==null)return;String image=attachedImage;attachedImage="";autoScreenshotUsed=false;attachmentLabel.setText("ماكو صورة مرفقة");input.setText("");appendChatMessage(c,"user",text);String sid=c.optString("sessionId","");String enriched=withTerminalContext(text,sid);JSONArray requestHistory=apiHistory(c);if(requestHistory.length()>0){try{requestHistory.put(requestHistory.length()-1,new JSONObject().put("role","user").put("text",enriched));}catch(Exception ignored){}}saveChats();renderMessages();busy=true;sendButton.setEnabled(false);rateLabel.setText("جاري الاتصال بالمزوّد…");final String chatId=c.optString("id"),providerId=c.optString("provider",AiSecureStore.getActiveProvider(this));final String model=AiSecureStore.getModel(this,providerId),endpoint=AiSecureStore.getEndpoint(this,providerId),imagePath=image;
        worker.execute(()->{String response;try{String key=AiSecureStore.getKey(this,providerId);response=AiApiClient.send(this,AiProvider.byId(providerId),model,endpoint,key,requestHistory,imagePath,2048);}catch(Exception e){response="[تعذر إكمال الطلب]\n"+e.getMessage();}final String result=response;runOnUiThread(()->{JSONObject live=findChat(chatId);String visible=result.replace("[TFM_SCREENSHOT]","").replace("[REQUEST_SCREENSHOT]","").trim();if(live!=null){appendChatMessage(live,"assistant",visible);saveChats();}boolean requested=(result.contains("[TFM_SCREENSHOT]")||result.contains("[REQUEST_SCREENSHOT]"));if(requested&&!autoScreenshotUsed){autoScreenshotUsed=true;beginAgentScreenshotCapture(chatId,providerId,model,endpoint);return;}else{busy=false;sendButton.setEnabled(true);}updateHeader();renderMessages();});});
    }
    private void appendChatMessage(JSONObject c,String role,String text){try{JSONArray a=c.optJSONArray("messages");if(a==null)a=new JSONArray();a.put(msg(role,text));while(a.length()>80){JSONArray n=new JSONArray();for(int i=1;i<a.length();i++)n.put(a.opt(i));a=n;}c.put("messages",a);}catch(Exception ignored){}}
    private JSONArray apiHistory(JSONObject c){JSONArray src=c.optJSONArray("messages"),out=new JSONArray();if(src==null)return out;int start=Math.max(0,src.length()-32);for(int i=start;i<src.length();i++){JSONObject m=src.optJSONObject(i);if(m==null)continue;String role=m.optString("role","user");if("terminal".equals(role))role="user";if(!"assistant".equals(role)&&!"user".equals(role))continue;out.put(msg(role,m.optString("text","")));}return out;}
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

    private void autoAnalyzeCommandResult(String chatId){
        JSONObject chat=findChat(chatId);if(chat==null)return;
        String providerId=chat.optString("provider",AiSecureStore.getActiveProvider(this));
        String model=AiSecureStore.getModel(this,providerId),endpoint=AiSecureStore.getEndpoint(this,providerId);
        JSONArray history=apiHistory(chat);
        if(history.length()==0)return;
        try{JSONObject last=history.optJSONObject(history.length()-1);if(last!=null){String txt=last.optString("text","");if(txt.length()>12000)last.put("text",txt.substring(txt.length()-12000));}}catch(Exception ignored){}
        busy=true;if(sendButton!=null)sendButton.setEnabled(false);rateLabel.setText("تحليل مخرجات Termux تلقائيًا…");
        worker.execute(()->{
            String answer;
            try{
                long lastCall=AiSecureStore.settings(this).getLong("rate_last_"+providerId,0);int gap=Math.max(0,AiSecureStore.settings(this).getInt("rate_gap_seconds",3));long wait=gap*1000L-(System.currentTimeMillis()-lastCall);
                if(wait>30000L)throw new Exception("الفاصل المحلي المتبقي أطول من 30 ثانية؛ أرسل متابعة يدويًا لاحقًا حتى لا تتجاوز الحد الذي حددته.");
                if(wait>0)Thread.sleep(wait);
                answer=AiApiClient.send(this,AiProvider.byId(providerId),model,endpoint,AiSecureStore.getKey(this,providerId),history,"",2048);
            }catch(Exception e){answer="[لم يكتمل التحليل التلقائي]\n"+e.getMessage()+"\nيمكنك إرسال سؤال يدويًا لمتابعة إصلاح المشكلة.";}
            final String result=answer;
            runOnUiThread(()->{JSONObject live=findChat(chatId);if(live!=null){appendChatMessage(live,"assistant",result);saveChats();}busy=false;if(sendButton!=null)sendButton.setEnabled(true);updateHeader();if(chatId.equals(activeChatId))renderMessages();});
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
    private void chatMenu(){String[] actions={"إعادة تسمية الدردشة","حذف الدردشة","إعدادات الذكاء الاصطناعي","إدارة جلسات Terminal"};new AlertDialog.Builder(this).setTitle("الدردشة الحالية").setItems(actions,(d,w)->{if(w==0)renameChat();if(w==1)deleteChat();if(w==2)startActivity(new Intent(this,AiSettingsActivity.class));if(w==3)startActivity(new Intent(this,TerminalSessionsActivity.class));}).show();}
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
