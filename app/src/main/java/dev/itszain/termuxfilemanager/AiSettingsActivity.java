package dev.itszain.termuxfilemanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Persistent AI, rate-limit, Termux, and appearance settings. */
public class AiSettingsActivity extends Activity {
    private UiTheme theme; private int BG,PANEL,PANEL2,TEXT,MUTED,ACCENT,DANGER,DIVIDER;
    private Spinner providerSpinner,themeSpinner; private EditText keyInput,modelInput,endpointInput,dailyInput,gapInput;
    private EditText agentSystemPromptInput,agentInstructionsInput,skillNameInput,skillUrlInput,agentAutoMaxStepsInput;
    private EditText thinkingBudgetInput,outputBudgetInput; private Spinner thinkingLevelSpinner;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private List<AiProvider> providers;
    @Override public void onCreate(Bundle b){super.onCreate(b);initTheme();UiTheme.applyWindow(this);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);build();}
    private void initTheme(){theme=UiTheme.get(this);BG=theme.bg;PANEL=theme.panel;PANEL2=theme.panel2;TEXT=theme.text;MUTED=theme.muted;ACCENT=theme.accent;DANGER=theme.danger;DIVIDER=theme.divider;}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(8),dp(14),dp(12));root.setBackgroundColor(BG);setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);Button back=button("‹",PANEL2,TEXT);back.setTextSize(27);header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(44)));back.setOnClickListener(v->finish());TextView title=text("الإعدادات",20,TEXT,true);LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(0,dp(44),1);tlp.leftMargin=dp(9);header.addView(title,tlp);root.addView(header);
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(section("الذكاء الاصطناعي"));body.addView(note("مفاتيح API تُحفظ مشفّرة باستخدام Android Keystore على هذا الجهاز. اختر مزوّدًا وأدخل اسم النموذج وفق كتالوجه الرسمي؛ قد تختلف الإتاحة حسب الحساب."));
        providers=AiProvider.all();providerSpinner=new Spinner(this);List<String> names=new ArrayList<>();for(AiProvider p:providers)names.add(p.name);providerSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));body.addView(providerSpinner,match());
        modelInput=field("معرّف النموذج",false);endpointInput=field("عنوان API الكامل (HTTPS)",false);keyInput=field("API key — يُحفظ مشفّرًا",true);keyInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        body.addView(label("النموذج"));body.addView(modelInput,match());body.addView(label("عنوان API"));body.addView(endpointInput,match());body.addView(note("تنبيه: مفتاح API يُرسل إلى المضيف المكتوب في هذا العنوان عبر HTTPS. لا تغيّر العنوان إلا إذا كنت تثق بالمضيف، لأن HTTPS وحده لا يثبت أنه مزوّدك الرسمي."));body.addView(label("مفتاح API"));body.addView(keyInput,match());
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);Button save=button("حفظ",PANEL2,ACCENT);Button test=button("اختبار الاتصال",PANEL2,TEXT);actions.addView(save,new LinearLayout.LayoutParams(0,dp(44),1));LinearLayout.LayoutParams testLp=new LinearLayout.LayoutParams(0,dp(44),1);testLp.leftMargin=dp(6);actions.addView(test,testLp);body.addView(actions,match());save.setOnClickListener(v->saveProvider());test.setOnClickListener(v->testProvider());
        Button clearKey=button("حذف مفتاح API المحفوظ لهذا المزوّد",PANEL,DANGER);clearKey.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("حذف مفتاح API؟").setMessage("سيُحذف المفتاح المشفّر محليًا لهذا المزوّد فقط. لن يتغير مفتاح أي مزوّد آخر.").setPositiveButton("حذف المفتاح",(d,w)->{try{AiSecureStore.saveKey(this,selectedProvider().id,"");keyInput.setText("");keyInput.setHint("لا يوجد مفتاح محفوظ");Toast.makeText(this,"تم حذف المفتاح المحلي",Toast.LENGTH_SHORT).show();}catch(Exception e){alert(e.getMessage());}}).setNegativeButton("إلغاء",null).show());body.addView(clearKey,match());providerSpinner.setSelection(AiProvider.indexOf(AiSecureStore.getActiveProvider(this)));providerSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){loadProvider();}});loadProvider();
        Button discover=button("تحميل قائمة النماذج من API",PANEL2,ACCENT);discover.setOnClickListener(v->discoverModels());body.addView(discover,match());Button docs=button("فتح توثيق المزوّد",PANEL,TEXT);docs.setOnClickListener(v->{AiProvider p=selectedProvider();try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(p.docs)));}catch(Exception e){Toast.makeText(this,p.docs,Toast.LENGTH_LONG).show();}});body.addView(docs,match());
        body.addView(section("إدارة الاستخدام / Rate limits"));body.addView(note("هذه حدود محلية لتقليل الطلبات غير المقصودة، وليست بديلًا عن limits التي يفرضها المزوّد. خطأ HTTP 429 يعرض رسالة الانتظار إن وفرها المزوّد."));dailyInput=field("أقصى طلب لكل مزوّد باليوم",false);dailyInput.setInputType(InputType.TYPE_CLASS_NUMBER);gapInput=field("أقل عدد ثوانٍ بين طلبين من نفس المزوّد",false);gapInput.setInputType(InputType.TYPE_CLASS_NUMBER);body.addView(dailyInput,match());body.addView(gapInput,match());dailyInput.setText(String.valueOf(AiSecureStore.settings(this).getInt("rate_daily_max",40)));gapInput.setText(String.valueOf(AiSecureStore.settings(this).getInt("rate_gap_seconds",3)));Button rateSave=button("حفظ حدود الاستخدام",PANEL2,ACCENT);rateSave.setOnClickListener(v->{int daily=parse(dailyInput.getText().toString(),40,1,10000),gap=parse(gapInput.getText().toString(),3,0,3600);AiSecureStore.settings(this).edit().putInt("rate_daily_max",daily).putInt("rate_gap_seconds",gap).apply();Toast.makeText(this,"انحفظت حدود الاستخدام المحلية",Toast.LENGTH_SHORT).show();});body.addView(rateSave,match());
        CheckBox autoAnalyze=new CheckBox(this);autoAnalyze.setText("حلّل مخرجات أوامر AI تلقائيًا بعد تنفيذها");autoAnalyze.setTextColor(TEXT);autoAnalyze.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));autoAnalyze.setChecked(AiSecureStore.settings(this).getBoolean("auto_analyze_results",false));autoAnalyze.setOnCheckedChangeListener((button,checked)->AiSecureStore.settings(this).edit().putBoolean("auto_analyze_results",checked).apply());body.addView(autoAnalyze,match());body.addView(note("عند تفعيل هذا الخيار، بعد تنفيذ أمر وافقت عليه داخل دردشة AI، تُرسل المخرجات إلى المزوّد نفسه ليقترح الخطوة التالية تلقائيًا. هذا قد يستهلك طلب API إضافيًا؛ تنفيذ أي أمر جديد يبقى بحاجة إلى موافقتك."));
        body.addView(section("Thinking & Output budgets"));
        body.addView(note("ميزانية الإخراج تحدد الحد الأعلى للرموز التي يرجعها النموذج. ميزانية التفكير تُرسل فقط إلى APIs/models التي تدعمها (مثل Gemini models الداعمة لـ thinking وبعض نماذج Anthropic). القيمة 0 تترك سلوك التفكير الافتراضي للمزوّد؛ لا يمكن للتطبيق إجبار موديل لا يدعم الميزة على التفكير أو كشف reasoning داخلي غير متاح من المزوّد."));
        thinkingBudgetInput=field("Thinking budget tokens (0 = provider default)",false);
        thinkingBudgetInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        thinkingBudgetInput.setText(String.valueOf(AiSecureStore.settings(this).getInt("thinking_budget_tokens",0)));
        outputBudgetInput=field("Output budget tokens (128–8192)",false);
        outputBudgetInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        outputBudgetInput.setText(String.valueOf(AiSecureStore.settings(this).getInt("output_budget_tokens",2048)));
        body.addView(thinkingBudgetInput,match()); body.addView(outputBudgetInput,match());
        body.addView(label("Gemini 3.x thinking level"));
        final String[] thinkingLevels={"minimal","low","medium","high"};
        final String[] thinkingLevelLabels={"Minimal — أسرع","Low — تفكير خفيف","Medium — متوازن","High — أعمق"};
        thinkingLevelSpinner=new Spinner(this);
        thinkingLevelSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,thinkingLevelLabels));
        String savedLevel=AiSecureStore.settings(this).getString("thinking_level","medium");
        int levelIndex=1; for(int i=0;i<thinkingLevels.length;i++) if(thinkingLevels[i].equals(savedLevel)) levelIndex=i;
        thinkingLevelSpinner.setSelection(levelIndex); body.addView(thinkingLevelSpinner,match());
        body.addView(note("Gemini 3.x يستخدم thinkingLevel، بينما بعض النماذج الأقدم تستخدم thinkingBudget. ما يعرضه التطبيق هو ملخص التفكير الذي يتيحه المزوّد، وليس سجل التفكير الداخلي الكامل."));
        Button saveBudgets=button("حفظ ميزانيات AI",PANEL2,ACCENT);
        saveBudgets.setOnClickListener(v->{int thinking=parse(thinkingBudgetInput.getText().toString(),0,0,8192);int output=parse(outputBudgetInput.getText().toString(),2048,128,8192);int ix=Math.max(0,Math.min(thinkingLevels.length-1,thinkingLevelSpinner.getSelectedItemPosition()));AiSecureStore.settings(this).edit().putInt("thinking_budget_tokens",thinking).putInt("output_budget_tokens",output).putString("thinking_level",thinkingLevels[ix]).apply();Toast.makeText(this,"انحفظت ميزانيات AI ومستوى التفكير",Toast.LENGTH_SHORT).show();});
        body.addView(saveBudgets,match());
        buildAgentOptions(body);
        body.addView(section("Termux"));body.addView(note("إدارة الحزم والمكتبات وتشغيل الأوامر تتم من خلال Termux RUN_COMMAND، بصلاحيات مستخدم Termux فقط. الأوامر المؤثرة تعرض تأكيدًا قبل تنفيذها."));Button link=button("إعداد الربط مع Termux",PANEL2,ACCENT);link.setOnClickListener(v->showTermuxLinkDialog());body.addView(link,match());Button appPerm=button("فتح إعدادات صلاحيات التطبيق",PANEL2,TEXT);appPerm.setOnClickListener(v->{try{startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(Exception e){alert("افتح إعدادات أندرويد > التطبيقات > Termux File Manager.");}});body.addView(appPerm,match());Button tools=button("فتح مركز الأدوات",PANEL2,TEXT);tools.setOnClickListener(v->startActivity(new Intent(this,ToolsActivity.class)));body.addView(tools,match());Button sessions=button("جلسات Terminal و TUI",PANEL2,TEXT);sessions.setOnClickListener(v->startActivity(new Intent(this,TerminalSessionsActivity.class)));body.addView(sessions,match());
        body.addView(section("مظهر التطبيق"));themeSpinner=new Spinner(this);String[] themeLabels={"Midnight — داكن أزرق","AMOLED — أسود","Light — فاتح"};themeSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,themeLabels));String tn=UiTheme.getName(this);themeSpinner.setSelection("light".equals(tn)?2:("amoled".equals(tn)?1:0));body.addView(themeSpinner,match());Button themeSave=button("تطبيق المظهر",PANEL2,ACCENT);themeSave.setOnClickListener(v->{int i=themeSpinner.getSelectedItemPosition();UiTheme.setName(this,i==2?"light":(i==1?"amoled":"midnight"));Toast.makeText(this,"تم حفظ المظهر. ارجع للشاشات لتطبيقه.",Toast.LENGTH_SHORT).show();recreate();});body.addView(themeSave,match());
        body.addView(section("المزوّدون العشرة"));for(AiProvider p:providers){LinearLayout item=card();item.addView(text(p.name,14,TEXT,true));TextView desc=text("Model: "+AiSecureStore.getModel(this,p.id)+"\n"+p.docs,11,MUTED,false);desc.setTextIsSelectable(true);item.addView(desc,match());LinearLayout.LayoutParams ilp=match();ilp.bottomMargin=dp(6);body.addView(item,ilp);}
        TextView security=note("لا تشارك المفتاح داخل المحادثات. واجهات API تُتصل مباشرة من التطبيق إلى المزوّد. قد يسجل المزوّد الطلبات وفق سياسة حسابك. صور الشاشة لا تُرسل إلا عند إرفاقها برسالة.");body.addView(security);
    }
    private void buildAgentOptions(LinearLayout body) {
        body.addView(section("سلوك وكيل الذكاء الاصطناعي"));
        body.addView(note("الاختيار التالي يغيّر تنفيذ الأدوات التي يقترحها الوكيل داخل Termux. الوضع التلقائي لا يستخدم root؛ لكنه يسمح للأوامر التي يولدها النموذج بتعديل ملفاتك أو تثبيت حزم دون نافذة تأكيد لكل أمر."));
        CheckBox autoExecute = new CheckBox(this);
        autoExecute.setText("تنفيذ أدوات الوكيل تلقائيًا بدون موافقة لكل أمر");
        autoExecute.setTextColor(TEXT);
        autoExecute.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        autoExecute.setChecked(AiSecureStore.settings(this).getBoolean("agent_auto_execute", false));
        autoExecute.setOnCheckedChangeListener((button, checked) ->
                AiSecureStore.settings(this).edit().putBoolean("agent_auto_execute", checked).apply());
        body.addView(autoExecute, match());
        body.addView(note("عند تشغيله: أوامر Shell وعمليات تثبيت Skills تُنفذ مباشرة، ثم يقرأ الوكيل النتيجة ويكمل الخطوة التالية. يوجد حد خطوات لكل مهمة وحاجز للأوامر التي تمسح النظام أو تهيّئ الأقراص. أوقف الخيار للعودة للموافقة اليدوية."));

        agentAutoMaxStepsInput = field("أقصى عدد خطوات تلقائية للمهمة (افتراضي 16)", false);
        agentAutoMaxStepsInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        agentAutoMaxStepsInput.setText(String.valueOf(AiSecureStore.settings(this).getInt("agent_auto_max_steps", 16)));
        body.addView(agentAutoMaxStepsInput, match());

        body.addView(section("System Prompt وتعليمات الوكيل"));
        body.addView(note("يُضاف النص المخصص إلى التعليمات الأساسية التي تصف بيئة Android وTermux والأدوات. اترك الحقول فارغة لاستخدام التعليمات الافتراضية. لا تضع مفاتيح API أو كلمات مرور هنا."));
        agentSystemPromptInput = field("System Prompt إضافي", false);
        agentSystemPromptInput.setMinLines(3);
        agentSystemPromptInput.setMaxLines(8);
        agentSystemPromptInput.setGravity(Gravity.TOP | Gravity.START);
        agentSystemPromptInput.setText(AiSecureStore.settings(this).getString("custom_system_prompt", ""));
        body.addView(label("System Prompt إضافي"));
        body.addView(agentSystemPromptInput, match());

        agentInstructionsInput = field("تعليمات وسياق عمل الوكيل", false);
        agentInstructionsInput.setMinLines(3);
        agentInstructionsInput.setMaxLines(8);
        agentInstructionsInput.setGravity(Gravity.TOP | Gravity.START);
        agentInstructionsInput.setText(AiSecureStore.settings(this).getString("agent_instructions", ""));
        body.addView(label("Agent Instructions"));
        body.addView(agentInstructionsInput, match());

        Button saveAgent = button("حفظ إعدادات الوكيل والتعليمات", PANEL2, ACCENT);
        saveAgent.setOnClickListener(v -> {
            int steps = parse(agentAutoMaxStepsInput.getText().toString(), 16, 1, 100);
            AiSecureStore.settings(this).edit()
                    .putString("custom_system_prompt", agentSystemPromptInput.getText().toString().trim())
                    .putString("agent_instructions", agentInstructionsInput.getText().toString().trim())
                    .putInt("agent_auto_max_steps", steps)
                    .apply();
            Toast.makeText(this, "انحفظت إعدادات الوكيل", Toast.LENGTH_SHORT).show();
        });
        body.addView(saveAgent, match());

        body.addView(section("Agent Skills — مهارات الوكيل"));
        body.addView(note("يدعم ملف SKILL.md القياسي المستخدم في مهارات Claude Code وAgents أخرى. أدخل رابط SKILL.md أو مجلد Skill على GitHub. يتم استيراد SKILL.md الأساسي فقط في هذه النسخة؛ الموارد والملفات المساعدة المشار إليها خارجه لا تُنزّل تلقائيًا. تُحفظ التعليمات محليًا داخل التطبيق وتُستدعى بكتابة /اسم-المهارة ثم المهمة. المهارات نص خارجي؛ راجع مصدرها."));
        skillNameInput = field("اسم اختياري للمهارة مثل frontend-design", false);
        skillNameInput.setSingleLine(true);
        body.addView(skillNameInput, match());
        skillUrlInput = field("رابط HTTPS لـ SKILL.md أو مجلد Skill في GitHub", false);
        skillUrlInput.setSingleLine(true);
        skillUrlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        body.addView(skillUrlInput, match());
        LinearLayout skillActions = new LinearLayout(this);
        skillActions.setGravity(Gravity.CENTER_VERTICAL);
        Button installSkill = button("تنصيب Skill من الرابط", PANEL2, ACCENT);
        Button manageSkills = button("المهارات المثبتة", PANEL, TEXT);
        skillActions.addView(installSkill, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams manageLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        manageLp.leftMargin = dp(6);
        skillActions.addView(manageSkills, manageLp);
        body.addView(skillActions, match());
        installSkill.setOnClickListener(v -> installSkillFromSettings());
        manageSkills.setOnClickListener(v -> showInstalledSkills());
    }

    private void installSkillFromSettings() {
        String url = skillUrlInput.getText().toString().trim();
        String name = skillNameInput.getText().toString().trim();
        if (url.isEmpty()) { alert("أدخل رابط Skill أولًا."); return; }
        if (!url.startsWith("https://")) { alert("لأمان الاتصال، رابط المهارة لازم يبدأ بـ HTTPS."); return; }
        Toast.makeText(this, "جاري تنزيل SKILL.md والتحقق من محتواه…", Toast.LENGTH_SHORT).show();
        worker.execute(() -> {
            String result;
            try {
                AgentSkillManager.InstallResult installed = AgentSkillManager.installFromUrl(this, url, name);
                result = "تم تنصيب /" + installed.name + " — " + installed.description;
            } catch (Exception e) { result = "فشل تنصيب المهارة: " + e.getMessage(); }
            final String finalResult = result;
            runOnUiThread(() -> {
                new AlertDialog.Builder(this).setTitle("Agent Skills").setMessage(finalResult).setPositiveButton("حسنًا", null).show();
                if (finalResult.startsWith("تم تنصيب /")) { skillUrlInput.setText(""); skillNameInput.setText(""); }
            });
        });
    }

    private void showInstalledSkills() {
        org.json.JSONArray skills = AgentSkillManager.loadAll(this);
        if (skills.length() == 0) { alert("ماكو مهارات مثبتة بعد. أضف رابط SKILL.md أعلاه أو اطلب من الوكيل تنصيب مهارة من رابط."); return; }
        String[] labels = new String[skills.length()];
        for (int i = 0; i < skills.length(); i++) {
            org.json.JSONObject s = skills.optJSONObject(i);
            labels[i] = (s == null ? "Skill" : "/" + s.optString("name", "skill") + " — " + s.optString("description", "بدون وصف"));
        }
        new AlertDialog.Builder(this).setTitle("المهارات المثبتة — اضغط لإدارة مهارة")
                .setItems(labels, (d, which) -> {
                    org.json.JSONObject skill = skills.optJSONObject(which);
                    if (skill == null) return;
                    String name = skill.optString("name", "skill");
                    String details = "اسم الاستدعاء: /" + name + "\n\n" + skill.optString("description", "") + "\n\nالمصدر: " + skill.optString("source", "");
                    new AlertDialog.Builder(this).setTitle(name).setMessage(details)
                            .setNeutralButton("حذف المهارة", (dialog, w) -> {
                                AgentSkillManager.delete(this, name);
                                Toast.makeText(this, "انحذفت /" + name, Toast.LENGTH_SHORT).show();
                            }).setPositiveButton("إغلاق", null).show();
                }).setNegativeButton("إغلاق", null).show();
    }

    private void loadProvider(){if(modelInput==null||providers==null)return;AiProvider p=selectedProvider();modelInput.setText(AiSecureStore.getModel(this,p.id));endpointInput.setText(AiSecureStore.getEndpoint(this,p.id));keyInput.setText("");try{String existing=AiSecureStore.getKey(this,p.id);keyInput.setHint(existing.isEmpty()?"أدخل API key — لا يوجد مفتاح محفوظ":"مفتاح محفوظ ومشفّر — اتركه فارغًا للاحتفاظ به");}catch(Exception e){keyInput.setHint("تعذر فك المفتاح؛ أعد إدخاله");}}
    private AiProvider selectedProvider(){int i=providerSpinner==null?0:providerSpinner.getSelectedItemPosition();return providers.get(Math.max(0,Math.min(i,providers.size()-1)));}
    private void saveProvider(){AiProvider p=selectedProvider();String model=modelInput.getText().toString().trim(),endpoint=endpointInput.getText().toString().trim(),key=keyInput.getText().toString().trim();if(model.isEmpty()||endpoint.isEmpty()){alert("النموذج وعنوان API مطلوبان.");return;}if(!endpoint.startsWith("https://")){alert("عنوان API يجب أن يبدأ بـ https://");return;}try{if(!key.isEmpty())AiSecureStore.saveKey(this,p.id,key);AiSecureStore.saveProvider(this,p.id,model,endpoint);Toast.makeText(this,"تم حفظ إعدادات "+p.name,Toast.LENGTH_SHORT).show();}catch(Exception e){alert("تعذر حفظ المفتاح بشكل آمن: "+e.getMessage());}}
    private void discoverModels(){saveProvider();AiProvider p=selectedProvider();final String endpoint=endpointInput.getText().toString().trim();String key;try{key=AiSecureStore.getKey(this,p.id);}catch(Exception e){alert(e.getMessage());return;}final String apiKey=key;worker.execute(()->{java.util.List<String> found;String error=null;try{found=AiApiClient.fetchModels(p,endpoint,apiKey);}catch(Exception e){found=new java.util.ArrayList<>();error=e.getMessage();}final java.util.List<String> names=found;final String err=error;runOnUiThread(()->{if(err!=null){alert(err);return;}String[] options=names.toArray(new String[0]);new AlertDialog.Builder(this).setTitle("اختيار نموذج — "+p.name).setItems(options,(d,w)->{modelInput.setText(options[w]);AiSecureStore.saveProvider(this,p.id,options[w],endpoint);Toast.makeText(this,"تم اختيار "+options[w],Toast.LENGTH_SHORT).show();}).setNegativeButton("إلغاء",null).show();});});}
    private void testProvider(){saveProvider();AiProvider p=selectedProvider();String key;try{key=AiSecureStore.getKey(this,p.id);}catch(Exception e){alert(e.getMessage());return;}if(key.isEmpty()){alert("احفظ API key أولًا.");return;}JSONArray history=new JSONArray().put(new JSONObjectSafe().message("user","Reply with exactly: OK"));AiProvider provider=p;String model=modelInput.getText().toString().trim(),endpoint=endpointInput.getText().toString().trim();Toast.makeText(this,"جاري اختبار الاتصال…",Toast.LENGTH_SHORT).show();worker.execute(()->{String result;try{result=AiApiClient.send(this,provider,model,endpoint,key,history,"",128);}catch(Exception e){result="فشل الاتصال: "+e.getMessage();}final String r=result;runOnUiThread(()->new AlertDialog.Builder(this).setTitle("نتيجة اختبار "+provider.name).setMessage(r).setPositiveButton("حسنًا",null).show());});}
    private static class JSONObjectSafe {JSONObject message(String role,String text){try{return new JSONObject().put("role",role).put("text",text);}catch(Exception e){return new JSONObject();}}}
    private int parse(String s,int fallback,int min,int max){try{return Math.max(min,Math.min(max,Integer.parseInt(s.trim())));}catch(Exception e){return fallback;}}
    private void showTermuxLinkDialog(){String command="mkdir -p ~/.termux; touch ~/.termux/termux.properties; if grep -q '^allow-external-apps=' ~/.termux/termux.properties; then sed -i 's/^allow-external-apps=.*/allow-external-apps=true/' ~/.termux/termux.properties; else echo 'allow-external-apps=true' >> ~/.termux/termux.properties; fi; pkg install -y python curl";new AlertDialog.Builder(this).setTitle("ربط Termux").setMessage("1) انسخ الأمر وافتح Termux والصقه.\n2) أعد تشغيل Termux بالكامل.\n3) افتح إعدادات أندرويد لهذا التطبيق وفعّل إذن Run commands in Termux environment إذا ظهر.\n\nالأمر يغيّر allow-external-apps إلى true، وهو إعداد يسمح للتطبيقات الخارجية بإرسال أوامر إلى Termux. استخدمه فقط إذا كنت تثق بهذا التطبيق.").setPositiveButton("نسخ الأمر",(d,w)->{android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(android.content.ClipData.newPlainText("Termux setup",command));Toast.makeText(this,"انسخنا أمر الربط",Toast.LENGTH_SHORT).show();Intent launch=getPackageManager().getLaunchIntentForPackage("com.termux");if(launch!=null)startActivity(launch);}).setNegativeButton("إغلاق",null).show();}
    private void alert(String s){new AlertDialog.Builder(this).setMessage(s).setPositiveButton("حسنًا",null).show();}
    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(12),dp(10),dp(12),dp(10));l.setBackground(round(PANEL,12,DIVIDER));return l;}
    private void bodySpacer(LinearLayout body){}
    private TextView section(String s){TextView t=text(s,16,ACCENT,true);t.setPadding(dp(2),dp(16),0,dp(7));return t;}
    private TextView label(String s){TextView t=text(s,12,MUTED,true);t.setPadding(0,dp(8),0,dp(3));return t;}
    private TextView note(String s){TextView t=text(s,12,MUTED,false);t.setGravity(Gravity.TOP|Gravity.START);t.setPadding(dp(4),dp(5),dp(4),dp(10));return t;}
    private EditText field(String hint,boolean secret){EditText e=new EditText(this);e.setHint(hint);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setTextSize(13);e.setSingleLine(false);e.setMinLines(1);e.setMaxLines(secret?2:3);e.setPadding(dp(10),dp(7),dp(10),dp(7));e.setBackground(round(PANEL2,9,DIVIDER));if(secret)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return e;}
    private Button button(String s,int bg,int fg){Button b=new Button(this);b.setText(s);b.setTextColor(fg);b.setTextSize(13);b.setAllCaps(false);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(8),dp(4),dp(8),dp(4));b.setBackground(round(bg,10,Color.TRANSPARENT));return b;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable round(int c,int r,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));if(stroke!=Color.TRANSPARENT)d.setStroke(dp(1),stroke);return d;}
    private LinearLayout.LayoutParams match(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.bottomMargin=dp(5);return p;}
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
}
