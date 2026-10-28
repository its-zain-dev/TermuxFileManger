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
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Saved named Termux work sessions and command-output panes. Interactive TUI opens in the Termux app. */
public class TerminalSessionsActivity extends Activity {
    private UiTheme theme; private String lastThemeName="midnight"; private int BG,PANEL,PANEL2,TEXT,MUTED,ACCENT,DANGER,DIVIDER; private UiKit kit;
    private Spinner sessionSpinner; private EditText nameInput,workdirInput,commandInput; private TextView outputView; private LinearLayout root;
    private JSONArray sessions; private String activeId=""; private boolean receiverRegistered=false; private final Map<Integer,String> requestSessions=new HashMap<>();
    private final BroadcastReceiver resultReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){int id=i.getIntExtra(CommandResultService.EXTRA_REQUEST_ID,-1);String sid=requestSessions.remove(id);if(sid==null)return;String stdout=i.getStringExtra("stdout"),stderr=i.getStringExtra("stderr");int exit=i.getIntExtra("exit_code",0),err=i.getIntExtra("err_code",-1);String text=(stdout==null?"":stdout)+(stderr==null||stderr.trim().isEmpty()?"":"\n[stderr]\n"+stderr)+"\n[exit="+exit+", err="+err+"]";appendOutput(sid,text);if(sid.equals(activeId))refreshOutput();}};
    @Override public void onCreate(Bundle b){super.onCreate(b);initTheme();UiTheme.applyWindow(this);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);sessions=AiSecureStore.loadArray(this,AiSecureStore.SESSIONS,"items");activeId=AiSecureStore.settings(this).getString("active_terminal_session","");ensureOneSession();build();IntentFilter f=new IntentFilter(CommandResultService.ACTION_RESULT);if(Build.VERSION.SDK_INT>=33)registerReceiver(resultReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(resultReceiver,f);receiverRegistered=true;}
    @Override protected void onResume(){super.onResume();String currentTheme=UiTheme.getName(this);if(lastThemeName!=null&&!lastThemeName.equals(currentTheme)){recreate();}}
    @Override protected void onDestroy(){if(receiverRegistered)try{unregisterReceiver(resultReceiver);}catch(Exception ignored){}super.onDestroy();}
    private void initTheme(){lastThemeName=UiTheme.getName(this);theme=UiTheme.get(this);kit=new UiKit(this);BG=theme.bg;PANEL=theme.panel;PANEL2=theme.panel2;TEXT=theme.text;MUTED=theme.muted;ACCENT=theme.accent;DANGER=theme.danger;DIVIDER=theme.divider;}
    private void ensureOneSession(){if(sessions.length()==0){JSONObject o=new JSONObject();try{o.put("id",Long.toString(System.currentTimeMillis()));o.put("name","جلسة 1");o.put("workdir","~/");o.put("output","جاهز. نفّذ أمرًا من الحقل أدناه.\n");sessions.put(o);activeId=o.optString("id");save();}catch(Exception ignored){}}if(AiSecureStore.findById(sessions,activeId)==null)activeId=sessions.optJSONObject(0).optString("id");}
    private JSONObject current(){JSONObject o=AiSecureStore.findById(sessions,activeId);if(o==null&&sessions.length()>0)o=sessions.optJSONObject(0);return o;}
    private void build(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10),dp(4),dp(10),dp(10)); root.setBackgroundColor(kit.bg); setContentView(root);
        LinearLayout header=kit.subBar("Terminal Workspace","جلسات ومخرجات وأوامر Termux",this::finish);
        Button menu=kit.iconButton("⋮","إجراءات الجلسة"); header.addView(menu,new LinearLayout.LayoutParams(kit.dp(36),kit.dp(36)));
        root.addView(header);
        menu.setOnClickListener(v->kit.sheet("إجراءات الجلسة",
                new String[]{"جلسة جديدة","تنفيذ الأمر بكل الجلسات","مسح المخرجات","نسخ المخرجات","حذف الجلسة"},w->{
                    if(w==0)newSession();
                    else if(w==1)broadcast();
                    else if(w==2){JSONObject s=current();if(s!=null)try{s.put("output","");save();refreshOutput();}catch(Exception ignored){}}
                    else if(w==3){ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("Terminal output",outputView.getText()));Toast.makeText(this,"تم نسخ المخرجات",Toast.LENGTH_SHORT).show();}
                    else removeSession();
                }));
        TextView note=kit.caption("الأوامر غير التفاعلية تعرض النتيجة هنا. برامج TUI مثل nano وvim وhtop تفتح في تطبيق Termux نفسه.");
        note.setPadding(dp(3),dp(5),dp(3),dp(8)); root.addView(note);

        TextView sessionLabel=kit.text("الجلسة",11.5f,kit.muted,true); root.addView(sessionLabel);
        sessionSpinner=new Spinner(this); sessionSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,sessionNames()));
        root.addView(sessionSpinner,new LinearLayout.LayoutParams(-1,dp(40)));
        sessionSpinner.setSelection(Math.max(0,indexOfSession(activeId)));
        sessionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                if(pos>=0&&pos<sessions.length()){activeId=sessions.optJSONObject(pos).optString("id");AiSecureStore.settings(TerminalSessionsActivity.this).edit().putString("active_terminal_session",activeId).apply();loadSessionFields();}
            }
        });

        TextView nameLabel=kit.text("اسم الجلسة",11.5f,kit.muted,true); LinearLayout.LayoutParams labelLp=match(); labelLp.topMargin=dp(4); root.addView(nameLabel,labelLp);
        nameInput=field("مثلاً: مشروع Python"); root.addView(nameInput,new LinearLayout.LayoutParams(-1,dp(44)));
        TextView workLabel=kit.text("مجلد العمل",11.5f,kit.muted,true); LinearLayout.LayoutParams workLabelLp=match(); workLabelLp.topMargin=dp(4); root.addView(workLabel,workLabelLp);
        workdirInput=field("~/"); root.addView(workdirInput,new LinearLayout.LayoutParams(-1,dp(44)));
        Button save=kit.ghost("حفظ بيانات الجلسة"); save.setTextSize(12); LinearLayout.LayoutParams saveLp=new LinearLayout.LayoutParams(-1,dp(40)); saveLp.bottomMargin=dp(6); root.addView(save,saveLp); save.setOnClickListener(v->saveSessionFields());

        TextView outputLabel=kit.text("المخرجات",11.5f,kit.muted,true); root.addView(outputLabel);
        outputView=kit.mono("",12,kit.text); outputView.setTextIsSelectable(true);
        outputView.setGravity(Gravity.TOP|Gravity.START); outputView.setPadding(kit.dp(11),kit.dp(10),kit.dp(11),kit.dp(10)); outputView.setBackground(kit.round(kit.bg,12,kit.outline));
        ScrollView outputScroll=new ScrollView(this); outputScroll.setFillViewport(false); outputScroll.addView(outputView);
        LinearLayout.LayoutParams outputLp=new LinearLayout.LayoutParams(-1,0,1); outputLp.topMargin=dp(4); outputLp.bottomMargin=dp(7); root.addView(outputScroll,outputLp);

        commandInput=field("اكتب أمر Shell أو سكربت متعدد الأسطر");
        commandInput.setTypeface(Typeface.MONOSPACE); commandInput.setGravity(Gravity.TOP|Gravity.START);
        commandInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        commandInput.setMinLines(2); commandInput.setMaxLines(5); root.addView(commandInput,new LinearLayout.LayoutParams(-1,dp(88)));
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); actions.setGravity(Gravity.CENTER_VERTICAL);
        Button run=kit.primary("▶ تنفيذ الأمر"); actions.addView(run,new LinearLayout.LayoutParams(0,dp(44),1));
        Button tui=kit.ghost("فتح TUI في Termux"); tui.setTextSize(12); LinearLayout.LayoutParams tuiLp=new LinearLayout.LayoutParams(0,dp(44),1); tuiLp.leftMargin=dp(6); actions.addView(tui,tuiLp);
        LinearLayout.LayoutParams actionsLp=new LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT); actionsLp.topMargin=dp(6); root.addView(actions,actionsLp);
        run.setOnClickListener(v->runCurrent()); tui.setOnClickListener(v->runTui()); loadSessionFields();
    }

    private void newSession(){kit.promptDialog("جلسة Terminal جديدة","اسم الجلسة", "جلسة "+(sessions.length()+1), "إنشاء", name->{if(name.isEmpty())name="جلسة "+(sessions.length()+1);JSONObject s=new JSONObject();try{s.put("id",Long.toString(System.nanoTime()));s.put("name",name);s.put("workdir",workdirInput==null?"~/":workdirInput.getText().toString());s.put("output","جلسة جديدة\n");sessions.put(s);activeId=s.optString("id");save();rebuildSpinner();loadSessionFields();}catch(Exception ignored){}});}
    private void saveSessionFields(){JSONObject o=current();if(o==null)return;try{o.put("name",nameInput.getText().toString().trim().isEmpty()?"Terminal":nameInput.getText().toString().trim());o.put("workdir",workdirInput.getText().toString().trim().isEmpty()?"~/":workdirInput.getText().toString().trim());save();rebuildSpinner();sessionSpinner.setSelection(indexOfSession(activeId));Toast.makeText(this,"انحفظت الجلسة",Toast.LENGTH_SHORT).show();}catch(Exception ignored){}}
    private void removeSession(){if(sessions.length()<=1){Toast.makeText(this,"لازم تبقى جلسة واحدة على الأقل",Toast.LENGTH_SHORT).show();return;}kit.confirmDialog("حذف الجلسة","راح ينحذف تعريف الجلسة وسجلها المحلي؛ ما راح ينحذف أي ملف في Termux.","حذف",()->{JSONArray a=new JSONArray();for(int i=0;i<sessions.length();i++){JSONObject o=sessions.optJSONObject(i);if(o!=null&&!activeId.equals(o.optString("id")))a.put(o);}sessions=a;activeId=sessions.optJSONObject(0).optString("id");save();rebuildSpinner();loadSessionFields();});}
    private void runCurrent(){String cmd=commandInput.getText().toString().trim();if(cmd.isEmpty())return;JSONObject s=current();String wd=s==null?"~/":s.optString("workdir","~/");kit.confirmDialog("تأكيد الأمر","سيُنفذ داخل Termux بصلاحيات المستخدم الحالي:\n\n"+cmd,"تنفيذ",()->launch(cmd,wd,activeId,true));}
    private void runTui(){String cmd=commandInput.getText().toString().trim();if(cmd.isEmpty())cmd="exec bash -l";JSONObject s=current();String wd=s==null?"~/":s.optString("workdir","~/");String finalCmd=cmd;if(!finalCmd.endsWith("exec bash -l"))finalCmd=finalCmd+"; printf '\\n[انتهى الأمر — الطرفية مفتوحة]\\n'; exec bash -l";String run=finalCmd;kit.confirmDialog("فتح TUI داخل Termux","ستفتح جلسة Termux تفاعلية. هذا مناسب لـ nano وvim وhtop وtop وواجهات TUI الأخرى.\n\n"+run,"فتح Termux",()->{try{TermuxCommandRunner.openTui(this,run,wd,"TFM TUI — "+(s==null?"Terminal":s.optString("name")));appendOutput(activeId,"\n[تم فتح جلسة TUI في Termux]\n"+run+"\n");refreshOutput();}catch(Exception e){alert(e.getMessage());}});}
    private void broadcast(){String cmd=commandInput.getText().toString().trim();if(cmd.isEmpty()){Toast.makeText(this,"اكتب الأمر أولًا",Toast.LENGTH_SHORT).show();return;}kit.confirmDialog("إرسال الأمر لكل الجلسات","سيُنفذ نفس الأمر في مجلد العمل الخاص بكل جلسة ("+sessions.length()+"). راجع الأمر بعناية، خصوصًا أوامر التثبيت أو الحذف.\n\n"+cmd,"نفّذ للجميع",()->{for(int i=0;i<sessions.length();i++){JSONObject s=sessions.optJSONObject(i);if(s!=null)launch(cmd,s.optString("workdir","~/"),s.optString("id"),true);}});}
    private void launch(String cmd,String wd,String sid,boolean background){try{int req=TermuxCommandRunner.run(this,cmd,wd,"TFM — "+nameFor(sid),background);requestSessions.put(req,sid);appendOutput(sid,"\n$ "+cmd+"\n[قيد التنفيذ…]\n");if(sid.equals(activeId))refreshOutput();}catch(Exception e){alert(e.getMessage());}}
    private String nameFor(String id){JSONObject o=AiSecureStore.findById(sessions,id);return o==null?"Terminal":o.optString("name","Terminal");}
    private void appendOutput(String sid,String text){JSONObject o=AiSecureStore.findById(sessions,sid);if(o==null)return;String old=o.optString("output","");if(old.length()>20000)old=old.substring(old.length()-15000);try{o.put("output",old+text);save();}catch(Exception ignored){}}
    private void refreshOutput(){JSONObject o=current();if(o!=null){outputView.setText(o.optString("output",""));outputView.post(()->{if(outputView.getParent() instanceof ScrollView)((ScrollView)outputView.getParent()).fullScroll(View.FOCUS_DOWN);});}}
    private void loadSessionFields(){JSONObject o=current();if(o==null)return;nameInput.setText(o.optString("name","Terminal"));workdirInput.setText(o.optString("workdir","~/"));refreshOutput();}
    private List<String> sessionNames(){List<String> n=new ArrayList<>();for(int i=0;i<sessions.length();i++){JSONObject o=sessions.optJSONObject(i);n.add(o==null?"Terminal":o.optString("name","Terminal")+" — "+o.optString("workdir","~/"));}return n;}
    private int indexOfSession(String id){for(int i=0;i<sessions.length();i++)if(id.equals(sessions.optJSONObject(i).optString("id")))return i;return 0;}
    private void rebuildSpinner(){if(sessionSpinner==null)return;sessionSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,sessionNames()));sessionSpinner.setSelection(indexOfSession(activeId));}
    private void save(){AiSecureStore.saveArray(this,AiSecureStore.SESSIONS,"items",sessions);AiSecureStore.settings(this).edit().putString("active_terminal_session",activeId).apply();}
    private void alert(String s){kit.infoDialog("Terminal", s);}
    private EditText field(String hint){return kit.field(hint,1);}
    private Button button(String s,int bg,int fg){Button b=new Button(this);b.setText(s);b.setTextColor(fg);b.setTextSize(12);b.setAllCaps(false);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(6),dp(4),dp(6),dp(4));b.setBackground(round(bg,10,Color.TRANSPARENT));return b;}
    private TextView text(String s,int size,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(c);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable round(int c,int r,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));if(stroke!=Color.TRANSPARENT)d.setStroke(dp(1),stroke);return d;}
    private LinearLayout.LayoutParams match(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT);p.bottomMargin=dp(5);return p;}
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
}
