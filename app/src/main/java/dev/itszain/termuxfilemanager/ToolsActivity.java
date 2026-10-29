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
        root.setPadding(dp(16),dp(10),dp(16),dp(8)); Ui.setContent(this, UiTheme.get(this), root);
        root.addView(Ui.header(this, UiTheme.get(this), "مركز الأدوات", "أوامر وحزم وتشخيص Termux", true));
        TextView note=text("كل العمليات تعمل بصلاحيات Termux العادية. راجع أوامر التثبيت والتعديل قبل الموافقة.",11,MUTED,false);
        note.setPadding(dp(3),dp(5),dp(3),dp(8)); root.addView(note);

        ScrollView scroll=new ScrollView(this); LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content.addView(sectionTitle("منفّذ الأوامر"));
        LinearLayout commandCard=card();
        commandCard.addView(text("نفّذ أوامر Shell أو سكربت متعدد الأسطر، وشاهد الخرج هنا.",12,MUTED,false),matchWrap());
        commandInput=new EditText(this); commandInput.setTextColor(TEXT); commandInput.setHintTextColor(MUTED); commandInput.setHint("pkg list-installed\npython --version\nls -la");
        commandInput.setTextSize(13); commandInput.setTypeface(Typeface.MONOSPACE); commandInput.setGravity(Gravity.TOP|Gravity.START);
        commandInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        commandInput.setMinLines(3); commandInput.setMaxLines(8); commandInput.setPadding(dp(11),dp(10),dp(11),dp(10)); Ui.styleField(this,UiTheme.get(this),commandInput);
        LinearLayout.LayoutParams commandLp=matchWrap(); commandLp.topMargin=dp(8); commandCard.addView(commandInput,commandLp);
        TextView workLabel=text("مجلد التشغيل",11,MUTED,true); LinearLayout.LayoutParams workLabelLp=matchWrap(); workLabelLp.topMargin=dp(8); commandCard.addView(workLabel,workLabelLp);
        workdirInput=new EditText(this); workdirInput.setSingleLine(true); workdirInput.setText("~/"); workdirInput.setTextColor(TEXT); workdirInput.setHintTextColor(MUTED);
        workdirInput.setTextSize(13); workdirInput.setPadding(dp(10),0,dp(10),0); Ui.styleField(this,UiTheme.get(this),workdirInput);
        LinearLayout.LayoutParams workLp=new LinearLayout.LayoutParams(-1,dp(42)); workLp.topMargin=dp(4); commandCard.addView(workdirInput,workLp);
        Button run=button("▶ تشغيل داخل Termux",ACCENT,BG); LinearLayout.LayoutParams runLp=matchWrap(); runLp.topMargin=dp(9); commandCard.addView(run,runLp);
        run.setOnClickListener(v->confirmAndRun(commandInput.getText().toString(),workdirInput.getText().toString(),"أمر من مركز الأدوات"));
        outputView=text("بانتظار تنفيذ أمر…",12,TEXT,false); outputView.setTypeface(Typeface.MONOSPACE); outputView.setTextIsSelectable(true);
        outputView.setGravity(Gravity.TOP|Gravity.START); outputView.setPadding(dp(10),dp(9),dp(10),dp(9)); outputView.setBackground(round(Color.rgb(13,17,23),10,DIVIDER));
        LinearLayout.LayoutParams outputLp=new LinearLayout.LayoutParams(-1,dp(148)); outputLp.topMargin=dp(8); commandCard.addView(outputView,outputLp);
        content.addView(commandCard);

        content.addView(sectionTitle("الأدوات حسب المهمة"));
        LinearLayout tools=card();
        addAction(tools,"مدير الحزم","بحث، تثبيت، تحديث وفحص حزم Termux",()->showPackageMenu());
        addAction(tools,"مكتبات ولغات البرمجة","pip وnpm وcargo وgem وGo وComposer وLuaRocks",()->showLibrariesMenu());
        addAction(tools,"فحص وتشخيص النظام","المعمارية والذاكرة والتخزين والمترجمات المثبتة",()->showDiagnosticsMenu());
        addAction(tools,"ملفات ومشاريع","تنزيل HTTPS، ضغط وفك الأرشيفات وفتح Shell بالمجلد",()->showProjectToolsMenu());
        content.addView(tools);
        TextView warning=text("التنفيذ ما يمنح root ولا يتجاوز عزل أندرويد. الأوامر التي تحذف ملفات أو تثبّت كود قد تغيّر بيئة Termux بالكامل.",11,MUTED,false);
        warning.setPadding(dp(3),dp(12),dp(3),dp(5)); content.addView(warning);
        root.addView(Ui.dock(this, UiTheme.get(this), 1));
        Ui.enterChildren(content, 1);
    }

    private void showLibrariesMenu(){
        String[] items={"Python / pip","Node.js / npm","Rust / Cargo","Ruby / gem","Go install","LuaRocks","PHP Composer"};
        new Ui.Sheet(this).setTitle("مكتبات ولغات البرمجة").setItems(items,(d,w)->{
            if(w==0)showInstallDialog("Python / pip","python -m pip install","اسم المكتبة");
            else if(w==1)showInstallDialog("Node.js / npm","npm install","اسم الحزمة");
            else if(w==2)showInstallDialog("Rust / Cargo","cargo install","اسم الأداة");
            else if(w==3)showInstallDialog("Ruby / gem","gem install","اسم الحزمة");
            else if(w==4)showInstallDialog("Go","go install","اسم الحزمة الكامل مثل example.com/tool@latest");
            else if(w==5)showInstallDialog("LuaRocks","luarocks install","اسم المكتبة");
            else showInstallDialog("PHP / Composer","composer require","vendor/package");
        }).show();
    }

    private void showDiagnosticsMenu(){
        String[] items={"فحص اللغات والمترجمات","ملخص النظام والتخزين","الحزم المثبتة","تحديث فهرس الحزم","ترقية الحزم"};
        new Ui.Sheet(this).setTitle("فحص وتشخيص").setItems(items,(d,w)->{
            if(w==0)setCommandAndRun(runtimeCheck(),"~/","فحص لغات وأدوات Termux");
            else if(w==1)setCommandAndRun(systemCheck(),"~/","تشخيص Termux");
            else if(w==2)setCommandAndRun("pkg list-installed","~/","الحزم المثبتة");
            else if(w==3)confirmAndRun("pkg update","~/","تحديث فهرس الحزم");
            else confirmAndRun("pkg upgrade -y","~/","ترقية حزم Termux");
        }).show();
    }

    private void showProjectToolsMenu(){
        String[] items={"تنزيل ملف من HTTPS","ضغط مجلد tar.gz","فك أرشيف zip أو tar.gz","فتح جلسة Shell"};
        new Ui.Sheet(this).setTitle("ملفات ومشاريع").setItems(items,(d,w)->{
            if(w==0)showDownloadDialog();
            else if(w==1)showArchiveDialog();
            else if(w==2)showExtractDialog();
            else confirmAndRun("exec bash -l",workdirInput.getText().toString(),"جلسة Shell من مركز الأدوات");
        }).show();
    }

    private void showPackageMenu() {
        String[] items = {"البحث عن حزمة", "تثبيت حزمة", "تثبيت تحديثات النظام (pkg update && pkg upgrade)", "تحديث الفهرس (pkg update)", "عرض الحزم المثبتة", "فحص أوامر pkg"};
        new Ui.Sheet(this).setTitle("مدير حزم Termux").setItems(items, (d, which) -> {
            switch (which) {
                case 0: showPackageNameDialog("بحث عن حزمة", "pkg search", "جزء من اسم الحزمة"); break;
                case 1: showPackageNameDialog("تثبيت حزمة", "pkg install -y", "اسم الحزمة"); break;
                case 2: confirmAndRun("pkg update && pkg upgrade -y", "~/", "تحديث وترقية Termux"); break;
                case 3: confirmAndRun("pkg update", "~/", "تحديث فهرس الحزم"); break;
                case 4: setCommandAndRun("pkg list-installed", "~/", "الحزم المثبتة"); break;
                case 5: setCommandAndRun("pkg --help", "~/", "مساعدة pkg"); break;
            }
        }).setNegativeButton("إلغاء", null).show();
    }

    private void showPackageNameDialog(String title, String prefix, String hint) {
        EditText input = input(hint);
        new Ui.Sheet(this).setTitle(title).setView(input).setPositiveButton("متابعة", (d, w) -> {
            String value = input.getText().toString().trim();
            if (value.isEmpty()) return;
            String cmd = prefix + " " + shellQuote(value);
            confirmAndRun(cmd, "~/", title);
        }).setNegativeButton("إلغاء", null).show();
    }

    private void showInstallDialog(String title, String prefix, String hint) {
        EditText input = input(hint);
        new Ui.Sheet(this).setTitle("" + title + " — تثبيت").setMessage("أدخل اسم الحزمة أو المكتبة. للأوامر المتقدمة استخدم مربع Shell الرئيسي.").setView(input)
                .setPositiveButton("تثبيت", (d, w) -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) return;
                    confirmAndRun(prefix + " " + shellQuote(value), workdirInput.getText().toString(), title);
                }).setNegativeButton("إلغاء", null).show();
    }

    private void showArchiveDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        EditText source = input("مسار المجلد المراد ضغطه");
        source.setText("~/my-folder");
        EditText dest = input("اسم الأرشيف الناتج");
        dest.setText("archive.tar.gz");
        layout.addView(source, matchWrap());
        layout.addView(dest, matchWrap());
        new Ui.Sheet(this).setTitle("ضغط مجلد tar.gz").setView(layout).setPositiveButton("إنشاء", (d, w) -> {
            String src = source.getText().toString().trim(), out = dest.getText().toString().trim();
            if (src.isEmpty() || out.isEmpty()) return;
            confirmAndRun("tar -czf " + shellPath(out) + " -C " + shellPath(parentOfShell(src)) + " " + shellQuote(baseName(src)), "~/", "إنشاء أرشيف tar.gz");
        }).setNegativeButton("إلغاء", null).show();
    }

    private void showExtractDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        EditText archive = input("مسار archive.zip أو archive.tar.gz");
        archive.setText("~/archive.tar.gz");
        EditText dest = input("مجلد الوجهة");
        dest.setText("~/extracted");
        layout.addView(archive, matchWrap());
        layout.addView(dest, matchWrap());
        new Ui.Sheet(this).setTitle("فك الأرشيف").setView(layout).setPositiveButton("فك", (d, w) -> {
            String a = archive.getText().toString().trim(), out = dest.getText().toString().trim();
            if (a.isEmpty() || out.isEmpty()) return;
            String quotedA = shellPath(a), quotedOut = shellPath(out);
            String command = "mkdir -p " + quotedOut + " && case " + quotedA + " in *.zip) unzip " + quotedA + " -d " + quotedOut + ";; *.tar.gz|*.tgz) tar -xzf " + quotedA + " -C " + quotedOut + ";; *.tar) tar -xf " + quotedA + " -C " + quotedOut + ";; *) echo 'امتداد غير مدعوم في هذه الأداة؛ استخدم unzip أو tar يدويًا.'; exit 2;; esac";
            confirmAndRun(command, "~/", "فك أرشيف");
        }).setNegativeButton("إلغاء", null).show();
    }

    private void showDownloadDialog(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);EditText url=input("رابط HTTPS للملف");EditText dest=input("مسار الوجهة مثل ~/Downloads/file.zip");url.setText("https://");dest.setText("~/Downloads/download.bin");layout.addView(url,matchWrap());layout.addView(dest,matchWrap());new Ui.Sheet(this).setTitle("تنزيل ملف").setMessage("يتطلب هذا الإجراء اتصالًا مباشرًا من Termux. استخدم الروابط التي تثق بها فقط.").setView(layout).setPositiveButton("تنزيل",(d,w)->{String u=url.getText().toString().trim(),to=dest.getText().toString().trim();if(!u.startsWith("https://")){new Ui.Sheet(this).setMessage("استخدم رابط HTTPS فقط.").setPositiveButton("حسنًا",null).show();return;}if(to.isEmpty())return;String cmd="mkdir -p \"$(dirname "+shellPath(to)+")\" && curl -fL --show-error --retry 2 "+shellQuote(u)+" -o "+shellPath(to)+" && ls -lh "+shellPath(to);confirmAndRun(cmd, "~/", "تنزيل ملف HTTPS");}).setNegativeButton("إلغاء",null).show();}

    private void setCommandAndRun(String command, String workdir, String label) {
        commandInput.setText(command);
        workdirInput.setText(workdir);
        commandInput.setSelection(commandInput.length());
        commandInput.requestFocus();
        confirmAndRun(command, workdir, label);
    }

    private void confirmAndRun(String command, String workdir, String label) {
        if (command == null || command.trim().isEmpty()) {
            Ui.toast(this, "اكتب أمرًا أولًا.");
            return;
        }
        if (!isTermuxInstalled()) {
            new Ui.Sheet(this).setTitle("Termux غير مثبت").setMessage("ثبّت Termux ثم أعد المحاولة.").setPositiveButton("حسنًا", null).show();
            return;
        }
        if (checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) {
            new Ui.Sheet(this).setTitle("صلاحية Termux مطلوبة").setMessage("امنح هذا التطبيق صلاحية Run commands in Termux environment وفعّل allow-external-apps=true داخل Termux.").setPositiveButton("فتح الإعدادات", (d, w) -> {
                try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) { }
            }).setNegativeButton("إلغاء", null).show();
            return;
        }
        String normalizedWorkdir = workdir == null || workdir.trim().isEmpty() ? "~/" : workdir.trim();
        new Ui.Sheet(this).setTitle("تأكيد التنفيذ في Termux")
                .setMessage("سيعمل الأمر في جلسة Termux مع صلاحيات مستخدم Termux. راجعه قبل التنفيذ:\n\n" + command)
                .setPositiveButton("تنفيذ", (d, w) -> launchCommand(command, normalizedWorkdir, label))
                .setNegativeButton("إلغاء", null).show();
    }

    private void launchCommand(String commandText, String workdir, String label) {
        try {
            if(outputView!=null)outputView.append("\n\n$ "+commandText+"\n[قيد التنفيذ…]");
            boolean interactive=commandText.trim().equals("exec bash -l");
            if(interactive){TermuxCommandRunner.openTui(this,commandText,workdir,label);Ui.toast(this, "فُتحت جلسة Termux تفاعلية");return;}
            int request=TermuxCommandRunner.run(this,commandText,workdir,label,true);requests.put(request,label);
        } catch (Exception e) {
            new Ui.Sheet(this).setTitle("تعذر تشغيل الأمر").setMessage(String.valueOf(e.getMessage())).setPositiveButton("حسنًا", null).show();
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

    private UiTheme th() { return UiTheme.get(this); }

    private int iconFor(String title) {
        if (title.contains("حزم") && !title.contains("مكتبات")) return Icons.BOX;
        if (title.contains("مكتبات")) return Icons.CODE;
        if (title.contains("فحص")) return Icons.PULSE;
        if (title.contains("ملفات")) return Icons.FOLDER;
        return Icons.SETTINGS;
    }

    private void addAction(LinearLayout card, String title, String desc, Runnable action) {
        UiTheme t = th();
        LinearLayout item = new LinearLayout(this);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(8), dp(8), dp(10), dp(8));
        item.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Ui.alpha(t.accent, 60)), null, Ui.shape(Color.WHITE, Ui.dpf(this, 18), 0, 0)));
        item.setClickable(true);
        item.setFocusable(true);
        android.widget.ImageView tile = new android.widget.ImageView(this);
        tile.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        tile.setPadding(dp(11), dp(11), dp(11), dp(11));
        tile.setImageDrawable(Icons.get(this, iconFor(title), t.accent));
        tile.setBackground(Ui.shape(Ui.alpha(t.accent, 34), Ui.dpf(this, 15), 0, 0));
        item.addView(tile, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        TextView main = text(title, 15, TEXT, true);
        TextView detail = text(desc, 11, MUTED, false);
        detail.setGravity(Gravity.TOP | Gravity.START);
        textBox.addView(main, matchWrap());
        LinearLayout.LayoutParams detailLp = matchWrap();
        detailLp.topMargin = dp(3);
        textBox.addView(detail, detailLp);
        LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tbLp.setMarginStart(dp(12));
        item.addView(textBox, tbLp);
        item.addView(Icons.view(this, Icons.CHEVRON, Ui.alpha(MUTED, 200), 18));
        Ui.press(item);
        item.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams ilp = matchWrap();
        ilp.bottomMargin = dp(2);
        card.addView(item, ilp);
    }

    private LinearLayout card() {
        LinearLayout box = Ui.card(this, th());
        box.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams lp = matchWrap();
        lp.bottomMargin = dp(8);
        box.setLayoutParams(lp);
        return box;
    }

    private View sectionTitle(String value) {
        int icon = value.contains("منفّذ") ? Icons.TERMINAL : Icons.GRID;
        return Ui.sectionTitle(this, th(), value, icon);
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setPadding(dp(14), 0, dp(14), 0);
        Ui.styleField(this, th(), input);
        return input;
    }

    private Button button(String label, int background, int color) {
        return Ui.button(this, th(), label, background, color);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = Ui.text(this, value, size, color, bold);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private android.graphics.drawable.Drawable round(int color, int radiusDp, int strokeColor) {
        return Ui.surface(this, th(), color, radiusDp, strokeColor);
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
