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
    private int BG, PANEL, PANEL_2, TEXT, MUTED, ACCENT;
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
        UiTheme palette=UiTheme.get(this); BG=palette.bg; PANEL=palette.panel; PANEL_2=palette.panel2; TEXT=palette.text; MUTED=palette.muted; ACCENT=palette.accent; lastThemeName=UiTheme.getName(this);
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(8), dp(16), dp(12));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("‹", PANEL_2, TEXT);
        back.setTextSize(28);
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(46)));
        back.setOnClickListener(v -> finish());
        TextView title = text("مركز أدوات Termux", 20, TEXT, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, dp(46), 1);
        titleLp.setMargins(dp(8), 0, 0, 0);
        header.addView(title, titleLp);
        root.addView(header);
        TextView subtitle = text("أوامر Shell، الحزم، المكتبات، لغات البرمجة وأدوات التطوير — التنفيذ يتم داخل Termux.", 12, MUTED, false);
        subtitle.setGravity(Gravity.TOP | Gravity.START);
        subtitle.setPadding(dp(3), dp(4), dp(3), dp(12));
        root.addView(subtitle);
        LinearLayout workspace = new LinearLayout(this); workspace.setGravity(Gravity.CENTER_VERTICAL);
        Button sessionsButton=button("Terminal Sessions / TUI",PANEL_2,TEXT); sessionsButton.setOnClickListener(v->startActivity(new Intent(this,TerminalSessionsActivity.class)));
        Button settingsButton=button("الإعدادات",PANEL_2,TEXT); settingsButton.setOnClickListener(v->startActivity(new Intent(this,AiSettingsActivity.class)));
        workspace.addView(sessionsButton,new LinearLayout.LayoutParams(0,dp(40),1)); LinearLayout.LayoutParams setLp=new LinearLayout.LayoutParams(dp(90),dp(40));setLp.leftMargin=dp(5);workspace.addView(settingsButton,setLp);root.addView(workspace);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        content.addView(sectionTitle("تنفيذ الأوامر"));
        LinearLayout commandCard = card();
        TextView commandNote = text("اكتب أي أمر أو سكربت متعدد الأسطر. تقدر تستخدم أدوات Termux المثبتة، أوامر pkg وpip وnpm وgit والمترجمات وغيرها.", 12, MUTED, false);
        commandNote.setGravity(Gravity.TOP | Gravity.START);
        commandCard.addView(commandNote, matchWrap());
        commandInput = new EditText(this);
        commandInput.setTextColor(TEXT);
        commandInput.setHintTextColor(MUTED);
        commandInput.setHint("مثال: pkg install python\npython --version\nls -la");
        commandInput.setTextSize(13);
        commandInput.setTypeface(Typeface.MONOSPACE);
        commandInput.setGravity(Gravity.TOP | Gravity.START);
        commandInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        commandInput.setMinLines(4);
        commandInput.setMaxLines(12);
        commandInput.setPadding(dp(11), dp(10), dp(11), dp(10));
        commandInput.setBackground(round(PANEL_2, 10, Color.rgb(58, 75, 103)));
        LinearLayout.LayoutParams commandLp = matchWrap();
        commandLp.topMargin = dp(10);
        commandCard.addView(commandInput, commandLp);
        TextView workdirLabel = text("مجلد التشغيل (WORKDIR)", 12, MUTED, true);
        LinearLayout.LayoutParams labelLp = matchWrap();
        labelLp.topMargin = dp(10);
        commandCard.addView(workdirLabel, labelLp);
        workdirInput = new EditText(this);
        workdirInput.setSingleLine(true);
        workdirInput.setText("~/");
        workdirInput.setTextColor(TEXT);
        workdirInput.setHintTextColor(MUTED);
        workdirInput.setTextSize(13);
        workdirInput.setPadding(dp(10), 0, dp(10), 0);
        workdirInput.setBackground(round(PANEL_2, 9, Color.TRANSPARENT));
        LinearLayout.LayoutParams workLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(43));
        workLp.topMargin = dp(5);
        commandCard.addView(workdirInput, workLp);
        Button run = button("▶  RUN IN TERMUX", ACCENT, BG);
        LinearLayout.LayoutParams runLp = matchWrap();
        runLp.topMargin = dp(12);
        commandCard.addView(run, runLp);
        run.setOnClickListener(v -> confirmAndRun(commandInput.getText().toString(), workdirInput.getText().toString(), "أمر من مركز الأدوات"));
        outputView=text("مخرجات التنفيذ تظهر هنا داخل التطبيق.",11,TEXT,false); outputView.setTypeface(Typeface.MONOSPACE); outputView.setTextIsSelectable(true); outputView.setGravity(Gravity.TOP|Gravity.START); outputView.setPadding(dp(8),dp(8),dp(8),dp(8)); outputView.setBackground(round(PANEL,9,Color.rgb(42,53,77))); LinearLayout.LayoutParams outLp=matchWrap();outLp.topMargin=dp(9);commandCard.addView(outputView,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(118)));
        content.addView(commandCard);

        content.addView(sectionTitle("الحزم والمكتبات"));
        LinearLayout pkgCard = card();
        addAction(pkgCard, "مدير حزم Termux (pkg)", "بحث، تثبيت، تحديث وترقية أدوات النظام", () -> showPackageMenu());
        addAction(pkgCard, "مكتبات Python (pip)", "تثبيت مكتبات Python أو تشغيل pip", () -> showInstallDialog("Python / pip", "python -m pip install", "اسم المكتبة أو الحزمة"));
        addAction(pkgCard, "مكتبات Node.js (npm)", "تثبيت حزم JavaScript داخل مجلد المشروع", () -> showInstallDialog("Node.js / npm", "npm install", "اسم الحزمة أو المكتبة"));
        addAction(pkgCard, "أدوات Rust (cargo)", "تثبيت أداة Rust أو مكتبة من crates.io", () -> showInstallDialog("Rust / Cargo", "cargo install", "اسم الأداة"));
        addAction(pkgCard, "أدوات Ruby (gem)", "تثبيت Gems", () -> showInstallDialog("Ruby / gem", "gem install", "اسم الحزمة"));
        addAction(pkgCard, "أدوات Go", "تثبيت أداة Go عبر go install", () -> showInstallDialog("Go", "go install", "اسم الحزمة الكامل مثل example.com/tool@latest"));
        addAction(pkgCard, "حزم pnpm / yarn / bun", "إدارة مكتبات JavaScript باستخدام مدير الحزم المثبت", () -> showInstallDialog("Node.js packages", "npm install", "اسم المكتبة"));
        addAction(pkgCard, "Lua / luarocks", "تثبيت مكتبة Lua إذا كانت luarocks مثبتة", () -> showInstallDialog("LuaRocks", "luarocks install", "اسم المكتبة"));
        addAction(pkgCard, "PHP / Composer", "تثبيت مكتبة PHP داخل المشروع إذا كان Composer متاحًا", () -> showInstallDialog("Composer", "composer require", "vendor/package"));
        addAction(pkgCard, "تنزيل ملف من رابط HTTPS", "تنزيل ملفات أو أرشيفات مباشرة إلى مسار تختاره", () -> showDownloadDialog());
        content.addView(pkgCard);

        content.addView(sectionTitle("اللغات والتشخيص"));
        LinearLayout runtimes = card();
        addAction(runtimes, "فحص اللغات والمترجمات", "يعرض الموجود فعليًا في Termux بدل افتراض أن كل لغة مثبتة", () -> setCommandAndRun(runtimeCheck(), "~/", "فحص لغات وأدوات Termux"));
        addAction(runtimes, "ملخص النظام والتخزين", "إصدار Termux، معمارية الجهاز، مساحة التخزين والذاكرة", () -> setCommandAndRun(systemCheck(), "~/", "تشخيص Termux"));
        addAction(runtimes, "قائمة الحزم المثبتة", "يعرض قائمة حزم Termux المثبتة", () -> setCommandAndRun("pkg list-installed", "~/", "الحزم المثبتة"));
        addAction(runtimes, "تحديث مستودعات الحزم", "يشغّل pkg update داخل جلسة Termux تفاعلية", () -> confirmAndRun("pkg update", "~/", "تحديث مستودعات Termux"));
        addAction(runtimes, "ترقية الحزم", "تحديث الحزم مع قبول التأكيد الافتراضي (-y)", () -> confirmAndRun("pkg upgrade -y", "~/", "ترقية حزم Termux"));
        content.addView(runtimes);

        content.addView(sectionTitle("ملفات ومشاريع"));
        LinearLayout projectCard = card();
        addAction(projectCard, "ضغط مجلد بصيغة tar.gz", "يطلب مسار المصدر واسم الأرشيف", () -> showArchiveDialog());
        addAction(projectCard, "فك أرشيف tar.gz أو zip", "يفك الملفات داخل مجلد تحدده", () -> showExtractDialog());
        addAction(projectCard, "تنزيل ملف من الإنترنت", "يدعم HTTPS ويعرض النتيجة هنا", () -> showDownloadDialog());
        addAction(projectCard, "فتح جلسة Shell في المجلد", "يفتح Termux على مسار العمل المحدد", () -> confirmAndRun("exec bash -l", workdirInput.getText().toString(), "جلسة Shell من مدير الملفات"));
        content.addView(projectCard);

        TextView warning = text("تنبيه أمان: أوامر Shell والمثبتات تستطيع تعديل ملفاتك وتنزيل وتشغيل كود. راجع الأمر قبل التنفيذ؛ لا يمنح التطبيق صلاحيات root.", 11, MUTED, false);
        warning.setGravity(Gravity.TOP | Gravity.START);
        warning.setPadding(dp(4), dp(14), dp(4), dp(8));
        content.addView(warning);
    }

    private void showPackageMenu() {
        String[] items = {"البحث عن حزمة", "تثبيت حزمة", "تثبيت تحديثات النظام (pkg update && pkg upgrade)", "تحديث الفهرس (pkg update)", "عرض الحزم المثبتة", "فحص أوامر pkg"};
        new AlertDialog.Builder(this).setTitle("مدير حزم Termux").setItems(items, (d, which) -> {
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
        new AlertDialog.Builder(this).setTitle(title).setView(input).setPositiveButton("متابعة", (d, w) -> {
            String value = input.getText().toString().trim();
            if (value.isEmpty()) return;
            String cmd = prefix + " " + shellQuote(value);
            confirmAndRun(cmd, "~/", title);
        }).setNegativeButton("إلغاء", null).show();
    }

    private void showInstallDialog(String title, String prefix, String hint) {
        EditText input = input(hint);
        new AlertDialog.Builder(this).setTitle("" + title + " — تثبيت").setMessage("أدخل اسم الحزمة أو المكتبة. للأوامر المتقدمة استخدم مربع Shell الرئيسي.").setView(input)
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
        new AlertDialog.Builder(this).setTitle("ضغط مجلد tar.gz").setView(layout).setPositiveButton("إنشاء", (d, w) -> {
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
        new AlertDialog.Builder(this).setTitle("فك الأرشيف").setView(layout).setPositiveButton("فك", (d, w) -> {
            String a = archive.getText().toString().trim(), out = dest.getText().toString().trim();
            if (a.isEmpty() || out.isEmpty()) return;
            String quotedA = shellPath(a), quotedOut = shellPath(out);
            String command = "mkdir -p " + quotedOut + " && case " + quotedA + " in *.zip) unzip " + quotedA + " -d " + quotedOut + ";; *.tar.gz|*.tgz) tar -xzf " + quotedA + " -C " + quotedOut + ";; *.tar) tar -xf " + quotedA + " -C " + quotedOut + ";; *) echo 'امتداد غير مدعوم في هذه الأداة؛ استخدم unzip أو tar يدويًا.'; exit 2;; esac";
            confirmAndRun(command, "~/", "فك أرشيف");
        }).setNegativeButton("إلغاء", null).show();
    }

    private void showDownloadDialog(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);EditText url=input("رابط HTTPS للملف");EditText dest=input("مسار الوجهة مثل ~/Downloads/file.zip");url.setText("https://");dest.setText("~/Downloads/download.bin");layout.addView(url,matchWrap());layout.addView(dest,matchWrap());new AlertDialog.Builder(this).setTitle("تنزيل ملف").setMessage("يتطلب هذا الإجراء اتصالًا مباشرًا من Termux. استخدم الروابط التي تثق بها فقط.").setView(layout).setPositiveButton("تنزيل",(d,w)->{String u=url.getText().toString().trim(),to=dest.getText().toString().trim();if(!u.startsWith("https://")){new AlertDialog.Builder(this).setMessage("استخدم رابط HTTPS فقط.").setPositiveButton("حسنًا",null).show();return;}if(to.isEmpty())return;String cmd="mkdir -p \"$(dirname "+shellPath(to)+")\" && curl -fL --show-error --retry 2 "+shellQuote(u)+" -o "+shellPath(to)+" && ls -lh "+shellPath(to);confirmAndRun(cmd, "~/", "تنزيل ملف HTTPS");}).setNegativeButton("إلغاء",null).show();}

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
            new AlertDialog.Builder(this).setTitle("Termux غير مثبت").setMessage("ثبّت Termux ثم أعد المحاولة.").setPositiveButton("حسنًا", null).show();
            return;
        }
        if (checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this).setTitle("صلاحية Termux مطلوبة").setMessage("امنح هذا التطبيق صلاحية Run commands in Termux environment وفعّل allow-external-apps=true داخل Termux.").setPositiveButton("فتح الإعدادات", (d, w) -> {
                try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) { }
            }).setNegativeButton("إلغاء", null).show();
            return;
        }
        String normalizedWorkdir = workdir == null || workdir.trim().isEmpty() ? "~/" : workdir.trim();
        new AlertDialog.Builder(this).setTitle("تأكيد التنفيذ في Termux")
                .setMessage("سيعمل الأمر في جلسة Termux مع صلاحيات مستخدم Termux. راجعه قبل التنفيذ:\n\n" + command)
                .setPositiveButton("تنفيذ", (d, w) -> launchCommand(command, normalizedWorkdir, label))
                .setNegativeButton("إلغاء", null).show();
    }

    private void launchCommand(String commandText, String workdir, String label) {
        try {
            if(outputView!=null)outputView.append("\n\n$ "+commandText+"\n[قيد التنفيذ…]");
            boolean interactive=commandText.trim().equals("exec bash -l");
            if(interactive){TermuxCommandRunner.openTui(this,commandText,workdir,label);Toast.makeText(this,"فُتحت جلسة Termux تفاعلية",Toast.LENGTH_SHORT).show();return;}
            int request=TermuxCommandRunner.run(this,commandText,workdir,label,true);requests.put(request,label);
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("تعذر تشغيل الأمر").setMessage(String.valueOf(e.getMessage())).setPositiveButton("حسنًا", null).show();
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
        LinearLayout item = new LinearLayout(this);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(8), dp(8), dp(8), dp(8));
        item.setBackground(round(PANEL, 9, Color.TRANSPARENT));
        item.setClickable(true);
        item.setFocusable(true);
        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        TextView main = text(title, 14, TEXT, true);
        TextView detail = text(desc, 11, MUTED, false);
        detail.setGravity(Gravity.TOP | Gravity.START);
        textBox.addView(main, matchWrap());
        LinearLayout.LayoutParams detailLp = matchWrap();
        detailLp.topMargin = dp(3);
        textBox.addView(detail, detailLp);
        item.addView(textBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView arrow = text("›", 25, ACCENT, true);
        arrow.setGravity(Gravity.CENTER);
        item.addView(arrow, new LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.MATCH_PARENT));
        item.setOnClickListener(v -> { v.animate().alpha(0.72f).setDuration(50).withEndAction(() -> { v.animate().alpha(1f).setDuration(110).start(); action.run(); }).start(); });
        card.addView(item, matchWrap());
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(42, 53, 77));
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
