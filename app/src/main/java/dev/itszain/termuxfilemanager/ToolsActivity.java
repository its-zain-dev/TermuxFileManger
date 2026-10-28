package dev.itszain.termuxfilemanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

/** Command center for Termux: commands, packages, libraries and runtime diagnostics. */
public class ToolsActivity extends Activity {
    private int BG, PANEL, PANEL_2, TEXT, MUTED, ACCENT, DIVIDER;
    private UiKit kit;
    private String lastThemeName="midnight";
    private EditText commandInput;
    private EditText workdirInput;
    private TextView outputView;
    private boolean receiverRegistered=false;
    private final Map<Integer,String> requests=new HashMap<>();
    private final BroadcastReceiver resultReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){int id=i.getIntExtra(CommandResultService.EXTRA_REQUEST_ID,-1);String label=requests.remove(id);if(label==null)return;String out=i.getStringExtra("stdout"),err=i.getStringExtra("stderr");int code=i.getIntExtra("exit_code",0);String block="\n\n=== "+label+" | exit="+code+" ===\n"+(out==null?"":out)+(err==null||err.trim().isEmpty()?"":"\n[stderr]\n"+err);outputView.append(block);if(outputView.length()>22000)outputView.setText(outputView.getText().subSequence(outputView.length()-18000,outputView.length()));}};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        kit=new UiKit(this);
        UiTheme palette=UiTheme.get(this); BG=palette.bg; PANEL=palette.panel; PANEL_2=palette.panel2; TEXT=palette.text; MUTED=palette.muted; ACCENT=palette.accent; DIVIDER=Color.rgb(42,53,77); lastThemeName=UiTheme.getName(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        UiTheme.applyWindow(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        buildUi();
        IntentFilter filter=new IntentFilter(CommandResultService.ACTION_RESULT); if(Build.VERSION.SDK_INT>=33)registerReceiver(resultReceiver,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(resultReceiver,filter);receiverRegistered=true;
    }

    @Override protected void onResume(){super.onResume();String now=UiTheme.getName(this);if(lastThemeName!=null&&!lastThemeName.equals(now))recreate();}
    @Override protected void onDestroy(){if(receiverRegistered)try{unregisterReceiver(resultReceiver);}catch(Exception ignored){}super.onDestroy();}

    private void buildUi() {
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(kit.bg); root.setPadding(dp(12),dp(4),dp(12),dp(10)); setContentView(root);
        LinearLayout header=kit.subBar("مركز الأدوات","أوامر وحزم وتشخيص Termux",this::finish); root.addView(header);
        Button terminal=kit.ghost("Terminal"); terminal.setTextSize(12);
        LinearLayout headerRow=new LinearLayout(this); headerRow.setOrientation(LinearLayout.HORIZONTAL); headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.addView(terminal,new LinearLayout.LayoutParams(dp(92),dp(36)));
        headerRow.addView(kit.text("كل العمليات تعمل بصلاحيات Termux العادية. راجع أوامر التثبيت والتعديل قبل الموافقة.",11,kit.muted,false),new LinearLayout.LayoutParams(0,dp(36),1));
        root.addView(headerRow);

        ScrollView scroll=new ScrollView(this); LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content.addView(kit.sectionTitle("منفّذ الأوامر"));
        LinearLayout commandCard=kit.card();
        commandCard.addView(kit.caption("نفّذ أوامر Shell أو سكربت متعدد الأسطر، وشاهد الخرج هنا."),matchWrap());
        commandInput=new EditText(this); commandInput.setTextColor(kit.text); commandInput.setHintTextColor(kit.muted); commandInput.setHint("pkg list-installed\npython --version\nls -la");
        commandInput.setTextSize(12.5f); commandInput.setTypeface(Typeface.MONOSPACE); commandInput.setGravity(Gravity.TOP|Gravity.START);
        commandInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        commandInput.setMinLines(3); commandInput.setMaxLines(8); commandInput.setPadding(kit.dp(11),kit.dp(10),kit.dp(11),kit.dp(10)); commandInput.setBackground(kit.round(kit.panelAlt,12,kit.outline));
        LinearLayout.LayoutParams commandLp=matchWrap(); commandLp.topMargin=dp(8); commandCard.addView(commandInput,commandLp);
        TextView workLabel=kit.text("مجلد التشغيل",11,kit.muted,true); LinearLayout.LayoutParams workLabelLp=matchWrap(); workLabelLp.topMargin=dp(8); commandCard.addView(workLabel,workLabelLp);
        workdirInput=new EditText(this); workdirInput.setSingleLine(true); workdirInput.setText("~/"); workdirInput.setTextColor(kit.text); workdirInput.setHintTextColor(kit.muted);
        workdirInput.setTextSize(13); workdirInput.setPadding(kit.dp(11),0,kit.dp(11),0); workdirInput.setBackground(kit.round(kit.panelAlt,12,kit.outline));
        LinearLayout.LayoutParams workLp=new LinearLayout.LayoutParams(-1,dp(44)); workLp.topMargin=dp(4); commandCard.addView(workdirInput,workLp);
        Button run=kit.primary("▶ تشغيل داخل Termux"); LinearLayout.LayoutParams runLp=matchWrap(); runLp.topMargin=dp(9); commandCard.addView(run,runLp);
        run.setOnClickListener(v->confirmAndRun(commandInput.getText().toString(),workdirInput.getText().toString(),"أمر من مركز الأدوات"));
        outputView=kit.mono("بانتظار تنفيذ أمر…",12,kit.text); outputView.setTextIsSelectable(true);
        outputView.setGravity(Gravity.TOP|Gravity.START); outputView.setPadding(kit.dp(11),kit.dp(10),kit.dp(11),kit.dp(10)); outputView.setBackground(kit.round(kit.bg,12,kit.outline));
        LinearLayout.LayoutParams outputLp=new LinearLayout.LayoutParams(-1,dp(148)); outputLp.topMargin=dp(8); commandCard.addView(outputView,outputLp);
        content.addView(commandCard);

        content.addView(kit.sectionTitle("الأدوات حسب المهمة"));
        LinearLayout tools=kit.card();
        addAction(tools,"مدير الحزم","بحث، تثبيت، تحديث وفحص حزم Termux",()->showPackageMenu());
        addAction(tools,"مكتبات ولغات البرمجة","pip وnpm وcargo وgem وGo وComposer وLuaRocks",()->showLibrariesMenu());
        addAction(tools,"فحص وتشخيص النظام","المعمارية والذاكرة والتخزين والمترجمات المثبتة",()->showDiagnosticsMenu());
        addAction(tools,"ملفات ومشاريع","تنزيل HTTPS، ضغط وفك الأرشيفات وفتح Shell بالمجلد",()->showProjectToolsMenu());
        content.addView(tools);
        TextView warning=kit.caption("التنفيذ ما يمنح root ولا يتجاوز عزل أندرويد. الأوامر التي تحذف ملفات أو تثبّت كود قد تغيّر بيئة Termux بالكامل.");
        warning.setPadding(dp(3),dp(12),dp(3),dp(5)); content.addView(warning);
    }

    private void showLibrariesMenu(){
        String[] items={"Python / pip","Node.js / npm","Rust / Cargo","Ruby / gem","Go install","LuaRocks","PHP Composer"};
        kit.sheet("مكتبات ولغات البرمجة",items,w->{
            if(w==0)showInstallDialog("Python / pip","python -m pip install","اسم المكتبة");
            else if(w==1)showInstallDialog("Node.js / npm","npm install","اسم الحزمة");
            else if(w==2)showInstallDialog("Rust / Cargo","cargo install","اسم الأداة");
            else if(w==3)showInstallDialog("Ruby / gem","gem install","اسم الحزمة");
            else if(w==4)showInstallDialog("Go","go install","اسم الحزمة الكامل مثل example.com/tool@latest");
            else if(w==5)showInstallDialog("LuaRocks","luarocks install","اسم المكتبة");
            else showInstallDialog("PHP / Composer","composer require","vendor/package");
        });
    }

    private void showDiagnosticsMenu(){
        String[] items={"فحص اللغات والمترجمات","ملخص النظام والتخزين","الحزم المثبتة","تحديث فهرس الحزم","ترقية الحزم"};
        kit.sheet("فحص وتشخيص",items,w->{
            if(w==0)setCommandAndRun(runtimeCheck(),"~/","فحص لغات وأدوات Termux");
            else if(w==1)setCommandAndRun(systemCheck(),"~/","تشخيص Termux");
            else if(w==2)setCommandAndRun("pkg list-installed","~/","الحزم المثبتة");
            else if(w==3)confirmAndRun("pkg update","~/","تحديث فهرس الحزم");
            else confirmAndRun("pkg upgrade -y","~/","ترقية حزم Termux");
        });
    }

    private void showProjectToolsMenu(){
        String[] items={"تنزيل ملف من HTTPS","ضغط مجلد tar.gz","فك أرشيف zip أو tar.gz","فتح جلسة Shell"};
        kit.sheet("ملفات ومشاريع",items,w->{
            if(w==0)showDownloadDialog();
            else if(w==1)showArchiveDialog();
            else if(w==2)showExtractDialog();
            else confirmAndRun("exec bash -l",workdirInput.getText().toString(),"جلسة Shell من مركز الأدوات");
        });
    }

    private void showPackageMenu() {
        String[] items = {"البحث عن حزمة", "تثبيت حزمة", "تثبيت تحديثات النظام (pkg update && pkg upgrade)", "تحديث الفهرس (pkg update)", "عرض الحزم المثبتة", "فحص أوامر pkg"};
        kit.sheet("مدير حزم Termux", items, which -> {
            switch (which) {
                case 0: showPackageNameDialog("بحث عن حزمة", "pkg search", "جزء من اسم الحزمة"); break;
                case 1: showPackageNameDialog("تثبيت حزمة", "pkg install -y", "اسم الحزمة"); break;
                case 2: confirmAndRun("pkg update && pkg upgrade -y", "~/", "تحديث وترقية Termux"); break;
                case 3: confirmAndRun("pkg update", "~/", "تحديث فهرس الحزم"); break;
                case 4: setCommandAndRun("pkg list-installed", "~/", "الحزم المثبتة"); break;
                case 5: setCommandAndRun("pkg --help", "~/", "مساعدة pkg"); break;
            }
        });
    }

    private void showPackageNameDialog(String title, String prefix, String hint) {
        kit.promptDialog(title, hint, "", "متابعة", value -> {
            if (value.isEmpty()) return;
            confirmAndRun(prefix + " " + shellQuote(value), "~/", title);
        });
    }

    private void showInstallDialog(String title, String prefix, String hint) {
        kit.promptDialog(title + " — تثبيت", hint + " — للأوامر المتقدمة استخدم مربع Shell الرئيسي", "", "تثبيت", value -> {
            if (value.isEmpty()) return;
            confirmAndRun(prefix + " " + shellQuote(value), workdirInput.getText().toString(), title);
        });
    }

    private void showArchiveDialog() {
        kit.promptDialog("ضغط مجلد tar.gz", "المسار المصدر ثم اسم الأرشيف، كل واحد في سطر", "~/my-folder\narchive.tar.gz", "إنشاء", value -> {
            String[] parts = value.split("\n");
            if (parts.length < 2) return;
            String src = parts[0].trim(), out = parts[1].trim();
            if (src.isEmpty() || out.isEmpty()) return;
            confirmAndRun("tar -czf " + shellPath(out) + " -C " + shellPath(parentOfShell(src)) + " " + shellQuote(baseName(src)), "~/", "إنشاء أرشيف tar.gz");
        });
    }

    private void showExtractDialog() {
        kit.promptDialog("فك الأرشيف", "مسار الأرشيف ثم مجلد الوجهة، كل واحد في سطر", "~/archive.tar.gz\n~/extracted", "فك", value -> {
            String[] parts = value.split("\n");
            if (parts.length < 2) return;
            String a = parts[0].trim(), out = parts[1].trim();
            if (a.isEmpty() || out.isEmpty()) return;
            String quotedA = shellPath(a), quotedOut = shellPath(out);
            String command = "mkdir -p " + quotedOut + " && case " + quotedA + " in *.zip) unzip " + quotedA + " -d " + quotedOut + ";; *.tar.gz|*.tgz) tar -xzf " + quotedA + " -C " + quotedOut + ";; *.tar) tar -xf " + quotedA + " -C " + quotedOut + ";; *) echo 'امتداد غير مدعوم في هذه الأداة؛ استخدم unzip أو tar يدويًا.'; exit 2;; esac";
            confirmAndRun(command, "~/", "فك أرشيف");
        });
    }

    private void showDownloadDialog(){kit.promptDialog("تنزيل ملف","رابط HTTPS ثم مسار الوجهة، كل واحد في سطر. يتطلب اتصالًا مباشرًا من Termux — استخدم الروابط التي تثق بها فقط.","https://\n~/Downloads/download.bin","تنزيل",value->{String[] parts=value.split("\n");if(parts.length<2)return;String u=parts[0].trim(),to=parts[1].trim();if(!u.startsWith("https://")){kit.infoDialog("تنزيل ملف","استخدم رابط HTTPS فقط.");return;}if(to.isEmpty())return;String cmd="mkdir -p \"$(dirname "+shellPath(to)+")\" && curl -fL --show-error --retry 2 "+shellQuote(u)+" -o "+shellPath(to)+" && ls -lh "+shellPath(to);confirmAndRun(cmd, "~/", "تنزيل ملف HTTPS");});}

    private void setCommandAndRun(String command, String workdir, String label) {
        commandInput.setText(command);
        workdirInput.setText(workdir);
        commandInput.setSelection(commandInput.length());
        commandInput.requestFocus();
        confirmAndRun(command, workdir, label);
    }

    private void confirmAndRun(String command, String workdir, String label) {
        if (command == null || command.trim().isEmpty()) {
            Toast.makeText(this, "اكتب أمرًا أولًا.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isTermuxInstalled()) {
            kit.infoDialog("Termux غير مثبت", "ثبّت Termux ثم أعد المحاولة.");
            return;
        }
        if (checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) {
            kit.confirmDialog("صلاحية Termux مطلوبة",
                    "امنح هذا التطبيق صلاحية Run commands in Termux environment وفعّل allow-external-apps=true داخل Termux.",
                    "فتح الإعدادات", () -> {
                        try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) { }
                    });
            return;
        }
        String normalizedWorkdir = workdir == null || workdir.trim().isEmpty() ? "~/" : workdir.trim();
        kit.confirmDialog("تأكيد التنفيذ في Termux",
                "سيعمل الأمر في جلسة Termux مع صلاحيات مستخدم Termux. راجعه قبل التنفيذ:\n\n" + command,
                "تنفيذ", () -> launchCommand(command, normalizedWorkdir, label));
    }

    private void launchCommand(String commandText, String workdir, String label) {
        try {
            if(outputView!=null)outputView.append("\n\n$ "+commandText+"\n[قيد التنفيذ…]");
            boolean interactive=commandText.trim().equals("exec bash -l");
            if(interactive){TermuxCommandRunner.openTui(this,commandText,workdir,label);Toast.makeText(this,"فُتحت جلسة Termux تفاعلية",Toast.LENGTH_SHORT).show();return;}
            int request=TermuxCommandRunner.run(this,commandText,workdir,label,true);requests.put(request,label);
        } catch (Exception e) {
            kit.infoDialog("تعذر تشغيل الأمر", String.valueOf(e.getMessage()));
        }
    }

    private String runtimeCheck() {
        return "printf '\\n=== Termux / Runtime inventory ===\\n'; " +
                "printf 'Shell: '; printf '%s\\n' \"$SHELL\"; " +
                "printf 'Arch: '; uname -m; " +
                "for c in bash sh zsh fish python python3 pip pip3 node npm npx bun deno git gh clang clang++ gcc g++ make cmake ninja rustc cargo go javac java kotlin kotlinc ruby gem php perl lua Rscript swift tclsh sqlite3 curl wget tar zip unzip ffmpeg openssh; do " +
                "if command -v \"$c\" >/dev/null 2>&1; then printf '[OK] %-10s %s\\n' \"$c\" \"$(command -v \"$c\")\"; else printf '[--] %s\\n' \"$c\"; fi; done; " +
                "printf '\\nUse pkg search <name> / pkg install <package> to add tools.\\n'";
    }

    private String systemCheck() {
        return "printf '=== Termux system check ===\\n'; termux-info 2>/dev/null | head -n 24; " +
                "printf '\\n=== Storage ===\\n'; df -h \"$HOME\"; " +
                "printf '\\n=== Memory ===\\n'; (free -h 2>/dev/null || cat /proc/meminfo | head -n 8); " +
                "printf '\\n=== OS / CPU ===\\n'; uname -a; getconf LONG_BIT 2>/dev/null || true; " +
                "printf '\\n=== Package manager ===\\n'; pkg --version 2>/dev/null || true";
    }

    private void addAction(LinearLayout card, String title, String desc, Runnable action) {
        LinearLayout item = kit.row();
        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        TextView main = kit.text(title, 13.5f, kit.text, true);
        TextView detail = kit.caption(desc);
        textBox.addView(main, matchWrap());
        LinearLayout.LayoutParams detailLp = matchWrap();
        detailLp.topMargin = dp(3);
        textBox.addView(detail, detailLp);
        item.addView(textBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView arrow = kit.text("›", 22, kit.accent, false);
        arrow.setGravity(Gravity.CENTER);
        item.addView(arrow, new LinearLayout.LayoutParams(kit.dp(26), ViewGroup.LayoutParams.WRAP_CONTENT));
        item.setOnClickListener(v -> { v.animate().alpha(0.72f).setDuration(50).withEndAction(() -> { v.animate().alpha(1f).setDuration(110).start(); action.run(); }).start(); });
        card.addView(item, matchWrap());
        View divider = kit.divider();
        card.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(9), dp(9), dp(9), dp(9));
        box.setBackground(round(PANEL, 14, Color.rgb(38, 51, 76)));
        LinearLayout.LayoutParams lp = matchWrap();
        lp.bottomMargin = dp(7);
        box.setLayoutParams(lp);
        return box;
    }

    private TextView sectionTitle(String value) {
        TextView title = text(value, 15, ACCENT, true);
        title.setPadding(dp(4), dp(15), 0, dp(7));
        return title;
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setSingleLine(true);
        input.setPadding(dp(10), 0, dp(10), 0);
        input.setBackground(round(PANEL_2, 9, Color.TRANSPARENT));
        return input;
    }

    private Button button(String label, int background, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(color);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(9), dp(5), dp(9), dp(5));
        b.setBackground(round(background, 11, Color.TRANSPARENT));
        return b;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeColor != Color.TRANSPARENT) d.setStroke(dp(1), strokeColor);
        return d;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean isTermuxInstalled() {
        try { getPackageManager().getPackageInfo("com.termux", 0); return true; }
        catch (Exception e) { return false; }
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private String parentOfShell(String p) {
        String value = p.trim().replaceAll("/+\\s*$", "");
        int idx = value.lastIndexOf('/');
        return idx < 0 ? "~/" : (idx == 0 ? "/" : value.substring(0, idx));
    }

    private String shellPath(String value) {
        String p = value == null ? "" : value.trim();
        if (p.equals("~")) return "\"$HOME\"";
        if (p.startsWith("~/")) return "\"$HOME\"/" + shellQuote(p.substring(2));
        if (p.equals("$HOME")) return "\"$HOME\"";
        if (p.startsWith("$HOME/")) return "\"$HOME\"/" + shellQuote(p.substring(6));
        return shellQuote(p);
    }

    private String baseName(String p) {
        String value = p.trim().replaceAll("/+\\s*$", "");
        int idx = value.lastIndexOf('/');
        return idx < 0 ? value : value.substring(idx + 1);
    }
}
