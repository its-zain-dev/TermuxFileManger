package dev.itszain.termuxfilemanager;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {
    private int BG, PANEL, PANEL_2, TEXT, MUTED, ACCENT, DANGER, DIVIDER;
    private String lastThemeName = "midnight";
    private static final String TERMUX_HOME = "/data/data/com.termux/files/home";
    private static final String TERMUX_PREFIX = "/data/data/com.termux/files/usr";
    private static final String RUN_ACTION = "com.termux.RUN_COMMAND";
    private static final String RESULT_ACTION = CommandResultService.ACTION_RESULT;
    private static final String REQUEST_ID_KEY = CommandResultService.EXTRA_REQUEST_ID;
    private static final AtomicInteger NEXT_ID = new AtomicInteger((int) (System.currentTimeMillis() % 100000));

    private LinearLayout root;
    private LinearLayout pathBar;
    private EditText pathInput;
    private TextView connectionLabel;
    private TextView statusLabel;
    private TextView hiddenButton;
    private Button moreButton;
    private ListView listView;
    private FileAdapter adapter;
    private final ArrayList<JSONObject> files = new ArrayList<>();
    private final Map<Integer, ResultCallback> callbacks = new HashMap<>();
    private String currentPath = TERMUX_HOME;
    private boolean showHidden = true;
    private boolean connected = false;
    private boolean searchMode = false;
    private String searchRoot = TERMUX_HOME;
    private int nextOffset = 0;
    private boolean moreAvailable = false;
    private boolean receiverRegistered = false;
    private int viewRequestGeneration = 0;
    private boolean listingInProgress = false;

    private interface ResultCallback {
        void onResult(JSONObject response, String error);
    }

    private static final String PYTHON_BRIDGE = """
import os, sys, json, stat, shutil, tempfile, time, fnmatch

def emit(value):
    print(json.dumps(value, ensure_ascii=False, separators=(',', ':')))

def fail(message):
    emit({'ok': False, 'error': str(message)})

def norm(path):
    path = str(path or os.path.expanduser('~'))
    home = '/data/data/com.termux/files/home'
    prefix = '/data/data/com.termux/files/usr'
    for token, actual in (('${PREFIX}', prefix), ('$PREFIX', prefix), ('${HOME}', home), ('$HOME', home)):
        if path == token:
            path = actual
            break
        if path.startswith(token + '/'):
            path = actual + path[len(token):]
            break
    path = os.path.expandvars(path)
    return os.path.abspath(os.path.expanduser(path))

def info(path, include_path=True):
    path = norm(path)
    st = os.lstat(path)
    is_link = stat.S_ISLNK(st.st_mode)
    try:
        is_dir = os.path.isdir(path)
    except Exception:
        is_dir = False
    if is_link:
        kind = 'symlink'
    elif stat.S_ISDIR(st.st_mode):
        kind = 'dir'
    elif stat.S_ISREG(st.st_mode):
        kind = 'file'
    else:
        kind = 'other'
    item = {
        'name': os.path.basename(path) or path,
        'path': path,
        'kind': kind,
        'isDir': bool(is_dir),
        'isLink': bool(is_link),
        'hidden': os.path.basename(path).startswith('.'),
        'size': int(st.st_size),
        'mtime': int(st.st_mtime),
        'mode': format(stat.S_IMODE(st.st_mode), '04o'),
        'modeText': stat.filemode(st.st_mode),
        'uid': int(st.st_uid),
        'gid': int(st.st_gid),
        'readable': bool(os.access(path, os.R_OK)),
        'writable': bool(os.access(path, os.W_OK)),
        'executable': bool(os.access(path, os.X_OK)),
    }
    try:
        item['linkTarget'] = os.readlink(path) if is_link else ''
    except Exception:
        item['linkTarget'] = ''
    if not include_path:
        item.pop('path', None)
    return item

def run():
    action = sys.argv[1]
    req = json.loads(sys.stdin.read() or '{}')
    if action == 'list':
        path = norm(req.get('path') or os.path.expanduser('~'))
        if not os.path.isdir(path):
            fail('المسار مو مجلد أو ما تكدر توصله: ' + path); return
        show_hidden = bool(req.get('showHidden', True))
        offset = max(0, int(req.get('offset', 0)))
        limit = min(100, max(1, int(req.get('limit', 100))))
        items = []
        errors = 0
        with os.scandir(path) as it:
            for entry in it:
                if not show_hidden and entry.name.startswith('.'):
                    continue
                try:
                    item = info(entry.path)
                    item['name'] = entry.name
                    items.append(item)
                except (PermissionError, FileNotFoundError, OSError):
                    errors += 1
                    items.append({'name': entry.name, 'path': entry.path, 'kind': 'unknown', 'isDir': False,
                                  'isLink': False, 'hidden': entry.name.startswith('.'), 'size': 0, 'mode': '????',
                                  'modeText': '??????????', 'readable': False, 'writable': False, 'executable': False})
        items.sort(key=lambda x: (not x.get('isDir', False), x.get('name', '').casefold()))
        page = items[offset:offset + limit]
        emit({'ok': True, 'action': action, 'path': path, 'items': page,
              'offset': offset, 'nextOffset': offset + len(page), 'more': offset + len(page) < len(items),
              'total': len(items), 'metadataErrors': errors})
        return
    if action == 'search':
        root = norm(req.get('path') or os.path.expanduser('~'))
        needle = str(req.get('query') or '').casefold()
        show_hidden = bool(req.get('showHidden', True))
        max_results = min(150, max(1, int(req.get('limit', 150))))
        results = []
        seen = 0
        if not needle:
            fail('اكتب اسم أو جزء من اسم الملف للبحث.'); return
        if not os.path.isdir(root):
            fail('مجلد البحث غير موجود أو ما عندك صلاحية عليه.'); return
        for base, dirs, names in os.walk(root, followlinks=False, onerror=lambda e: None):
            if not show_hidden:
                dirs[:] = [d for d in dirs if not d.startswith('.')]
                names = [n for n in names if not n.startswith('.')]
            for name in list(dirs) + names:
                if needle in name.casefold():
                    candidate = os.path.join(base, name)
                    try:
                        results.append(info(candidate))
                    except OSError:
                        continue
                    seen += 1
                    if len(results) >= max_results:
                        emit({'ok': True, 'action': action, 'path': root, 'items': results, 'more': True, 'totalShown': len(results)})
                        return
        results.sort(key=lambda x: (not x.get('isDir', False), x.get('name', '').casefold()))
        emit({'ok': True, 'action': action, 'path': root, 'items': results, 'more': False, 'totalShown': len(results)})
        return
    if action == 'stat':
        emit({'ok': True, 'item': info(req.get('path'))}); return
    if action == 'read':
        path = norm(req.get('path'))
        if not os.path.isfile(path):
            fail('هذا مو ملف عادي أو الملف غير موجود.'); return
        with open(path, 'rb') as f:
            raw = f.read(262145)
        if b'\\x00' in raw[:4096]:
            emit({'ok': True, 'binary': True, 'size': os.path.getsize(path), 'path': path}); return
        if len(raw) > 262144:
            emit({'ok': False, 'tooLarge': True, 'error': 'الملف أكبر من الحد الآمن للعرض داخل التطبيق (256 KB).'}); return
        text = raw.decode('utf-8', errors='replace')
        emit({'ok': True, 'binary': False, 'text': text, 'size': os.path.getsize(path), 'path': path}); return
    if action == 'write':
        path = norm(req.get('path'))
        text = req.get('text', '')
        if not isinstance(text, str):
            fail('محتوى الملف غير صالح.'); return
        if len(text.encode('utf-8')) > 262144:
            fail('النص أكبر من حد المحرر (256 KB).'); return
        target = os.path.realpath(path) if os.path.islink(path) else path
        if not os.path.isfile(target):
            fail('الملف غير موجود.'); return
        old_mode = stat.S_IMODE(os.stat(target).st_mode)
        directory = os.path.dirname(target) or '.'
        fd, tmp = tempfile.mkstemp(prefix='.tfm-', dir=directory)
        try:
            with os.fdopen(fd, 'w', encoding='utf-8', newline='') as f:
                f.write(text)
                f.flush()
                os.fsync(f.fileno())
            os.chmod(tmp, old_mode)
            os.replace(tmp, target)
        finally:
            try:
                if os.path.exists(tmp): os.unlink(tmp)
            except OSError: pass
        emit({'ok': True, 'message': 'انحفظ الملف.', 'path': path}); return
    if action == 'mkdir':
        path = norm(req.get('path'))
        os.mkdir(path)
        emit({'ok': True, 'message': 'انعمل المجلد.', 'path': path}); return
    if action == 'create':
        path = norm(req.get('path'))
        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
        fd = os.open(path, flags, 0o644)
        os.close(fd)
        emit({'ok': True, 'message': 'انعمل الملف.', 'path': path}); return
    if action == 'rename':
        source = norm(req.get('path'))
        name = str(req.get('name') or '')
        if not name or name in ('.', '..') or '/' in name or '\\\\' in name or '\\x00' in name:
            fail('اسم الملف غير صالح.'); return
        dest = os.path.join(os.path.dirname(source), name)
        if os.path.lexists(dest):
            fail('أكو ملف أو مجلد بنفس الاسم.'); return
        os.rename(source, dest)
        emit({'ok': True, 'message': 'تغير الاسم.', 'path': dest}); return
    if action in ('copy', 'move'):
        source = norm(req.get('path'))
        dest_dir = norm(req.get('destDir'))
        if not os.path.lexists(source):
            fail('المصدر مو موجود.'); return
        if not os.path.isdir(dest_dir):
            fail('مجلد الوجهة مو موجود.'); return
        dest = os.path.join(dest_dir, os.path.basename(source.rstrip(os.sep)) or os.path.basename(source))
        if os.path.lexists(dest):
            fail('الوجهة تحتوي على عنصر بنفس الاسم؛ ما راح أستبدله حتى ما ينمسح شي بالغلط.'); return
        if os.path.isdir(source) and not os.path.islink(source):
            try:
                if os.path.commonpath([source, dest]) == source:
                    fail('ما يصير نسخ أو نقل مجلد إلى داخل نفسه.'); return
            except ValueError:
                pass
        if action == 'copy':
            if os.path.islink(source): os.symlink(os.readlink(source), dest)
            elif os.path.isdir(source): shutil.copytree(source, dest, symlinks=True)
            else: shutil.copy2(source, dest)
            message = 'اكتمل النسخ.'
        else:
            shutil.move(source, dest)
            message = 'اكتمل النقل.'
        emit({'ok': True, 'message': message, 'path': dest}); return
    if action == 'delete':
        path = norm(req.get('path'))
        if not os.path.lexists(path):
            fail('العنصر مو موجود.'); return
        if os.path.islink(path) or not os.path.isdir(path): os.unlink(path)
        else: shutil.rmtree(path)
        emit({'ok': True, 'message': 'انحذف العنصر.', 'path': path}); return
    if action == 'chmod':
        path = norm(req.get('path'))
        mode_value = str(req.get('mode') or '')
        try: mode = int(mode_value, 8)
        except Exception:
            fail('الصلاحية لازم تكون رقم ثماني مثل 755 أو 644.'); return
        if mode < 0 or mode > 0o7777:
            fail('قيمة الصلاحيات خارج النطاق.'); return
        os.chmod(path, mode)
        emit({'ok': True, 'message': 'تحدثت الصلاحيات.', 'mode': format(stat.S_IMODE(os.stat(path).st_mode), '04o'), 'path': path}); return
    fail('عملية غير معروفة: ' + action)

try:
    run()
except PermissionError as e:
    fail('ما عندك صلاحية: ' + str(e))
except FileExistsError as e:
    fail('العنصر موجود مسبقاً: ' + str(e))
except FileNotFoundError as e:
    fail('المسار غير موجود: ' + str(e))
except OSError as e:
    fail((e.strerror or str(e)) + ' (' + str(e.filename or '') + ')')
except Exception as e:
    fail(type(e).__name__ + ': ' + str(e))
""";

    private final BroadcastReceiver resultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int requestId = intent.getIntExtra(REQUEST_ID_KEY, -1);
            ResultCallback callback = callbacks.remove(requestId);
            if (callback == null) return;
            String stdout = intent.getStringExtra("stdout");
            String stderr = intent.getStringExtra("stderr");
            int exitCode = intent.getIntExtra("exit_code", 0);
            int errCode = intent.getIntExtra("err_code", -1);
            String errMessage = intent.getStringExtra("err_msg");
            if (errCode != -1 || exitCode != 0) {
                String reason = (errMessage == null ? "" : errMessage) + "\n" + (stderr == null ? "" : stderr);
                if (reason.trim().isEmpty()) reason = "فشل تنفيذ الأمر داخل Termux. تأكد من إعداد الربط وصلاحية Run commands.";
                callback.onResult(null, reason.trim());
                return;
            }
            try {
                JSONObject response = new JSONObject(stdout == null ? "" : stdout.trim());
                if (!response.optBoolean("ok", false)) callback.onResult(response, response.optString("error", "فشلت العملية."));
                else callback.onResult(response, null);
            } catch (Exception e) {
                String message = "تعذر فهم رد Termux.";
                if (stdout != null && !stdout.trim().isEmpty()) message += "\n" + stdout.trim();
                if (stderr != null && !stderr.trim().isEmpty()) message += "\n" + stderr.trim();
                callback.onResult(null, message);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiTheme palette = UiTheme.get(this); BG=palette.bg; PANEL=palette.panel; PANEL_2=palette.panel2; TEXT=palette.text; MUTED=palette.muted; ACCENT=palette.accent; DANGER=palette.danger; DIVIDER=Color.rgb(42, 53, 77); lastThemeName=UiTheme.getName(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        UiTheme.applyWindow(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        buildUi();
        IntentFilter filter = new IntentFilter(RESULT_ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(resultReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(resultReceiver, filter);
        receiverRegistered = true;

        if (!isTermuxInstalled()) {
            setStatus("Termux مو مثبت", DANGER);
            showHelpDialog(true);
        } else if (checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) {
            setStatus("كمّل صلاحية الربط", DANGER);
            showPermissionDialog();
        } else {
            loadDirectory(currentPath, false);
        }
    }

    @Override
    protected void onResume() { super.onResume(); String current=UiTheme.getName(this); if(lastThemeName!=null&&!lastThemeName.equals(current)){recreate();} }

    @Override
    protected void onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(resultReceiver); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (searchMode) {
            searchMode = false;
            loadDirectory(searchRoot, false);
            return;
        }
        String parent = new java.io.File(currentPath).getParent();
        if (parent != null && !parent.equals(currentPath)) loadDirectory(parent, false);
        else super.onBackPressed();
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(10), dp(16), dp(10));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.addView(text("TFM Workspace", 21, TEXT, true));
        heading.addView(text("مساحة عمل Termux", 11, MUTED, false));
        header.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button menu = button("⋮", PANEL, TEXT);
        menu.setTextSize(22);
        header.addView(menu, new LinearLayout.LayoutParams(dp(44), dp(44)));
        menu.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("المزيد")
                .setItems(new String[]{"جلسات Terminal", "مركز الأدوات", "إعدادات الذكاء الاصطناعي", "إعداد وربط Termux"},
                        (dialog, which) -> {
                            if (which == 0) startActivity(new Intent(this, TerminalSessionsActivity.class));
                            else if (which == 1) startActivity(new Intent(this, ToolsActivity.class));
                            else if (which == 2) startActivity(new Intent(this, AiSettingsActivity.class));
                            else showHelpDialog(false);
                        }).show());
        root.addView(header);

        LinearLayout agentCard = new LinearLayout(this);
        agentCard.setOrientation(LinearLayout.HORIZONTAL);
        agentCard.setGravity(Gravity.CENTER_VERTICAL);
        agentCard.setPadding(dp(14), dp(12), dp(12), dp(12));
        agentCard.setBackground(round(PANEL_2, 16, ACCENT));
        LinearLayout agentInfo = new LinearLayout(this);
        agentInfo.setOrientation(LinearLayout.VERTICAL);
        agentInfo.addView(text("AI Agent", 17, TEXT, true));
        TextView agentDescription = text("يفحص المخرجات وينفّذ أدوات داخل Termux بعد موافقتك", 11, MUTED, false);
        LinearLayout.LayoutParams sub = new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT);
        sub.topMargin = dp(3);
        agentInfo.addView(agentDescription, sub);
        agentCard.addView(agentInfo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button openAgent = button("فتح الوكيل  ›", ACCENT, BG);
        openAgent.setOnClickListener(v -> {
            startActivity(new Intent(this, AiAssistantActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
        agentCard.addView(openAgent, new LinearLayout.LayoutParams(dp(108), dp(42)));
        LinearLayout.LayoutParams agentLp = new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT);
        agentLp.topMargin = dp(10);
        agentLp.bottomMargin = dp(10);
        root.addView(agentCard, agentLp);

        connectionLabel = text("جارِ فحص اتصال Termux…", 11, MUTED, false);
        connectionLabel.setPadding(dp(2), 0, 0, dp(7));
        root.addView(connectionLabel);

        pathBar = new LinearLayout(this);
        pathBar.setGravity(Gravity.CENTER_VERTICAL);
        pathInput = new EditText(this);
        pathInput.setSingleLine(true);
        pathInput.setTextColor(TEXT);
        pathInput.setHintTextColor(MUTED);
        pathInput.setTextSize(13);
        pathInput.setHint("المسار الحالي أو ~/...");
        pathInput.setPadding(dp(12), 0, dp(12), 0);
        pathInput.setBackground(round(PANEL, 12, DIVIDER));
        pathBar.addView(pathInput, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button go = button("فتح", PANEL_2, ACCENT);
        go.setOnClickListener(v -> {
            String path = pathInput.getText().toString().trim();
            if (!path.isEmpty()) loadDirectory(path, false);
        });
        LinearLayout.LayoutParams goLp = new LinearLayout.LayoutParams(dp(58), dp(42));
        goLp.leftMargin = dp(6);
        pathBar.addView(go, goLp);
        Button up = button("↑", PANEL, TEXT);
        up.setTextSize(20);
        up.setOnClickListener(v -> {
            String parent = new java.io.File(currentPath).getParent();
            if (parent != null) loadDirectory(parent, false);
        });
        LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        upLp.leftMargin = dp(5);
        pathBar.addView(up, upLp);
        root.addView(pathBar);

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setGravity(Gravity.CENTER_VERTICAL);
        quick.addView(buttonSmall("HOME", () -> loadDirectory(TERMUX_HOME, false)), weightLp());
        quick.addView(buttonSmall("PREFIX", () -> loadDirectory(TERMUX_PREFIX, false)), weightLp());
        quick.addView(buttonSmall("التخزين", () -> loadDirectory("/storage/emulated/0", false)), weightLp());
        root.addView(quick);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button search = button("بحث", PANEL, TEXT);
        search.setOnClickListener(v -> showSearchDialog());
        actions.addView(search, new LinearLayout.LayoutParams(0, dp(42), 1));
        hiddenButton = button(showHidden ? "المخفية ظاهرة" : "المخفية مخفية", PANEL, ACCENT);
        hiddenButton.setOnClickListener(v -> {
            showHidden = !showHidden;
            hiddenButton.setText(showHidden ? "المخفية ظاهرة" : "المخفية مخفية");
            loadDirectory(currentPath, false);
        });
        LinearLayout.LayoutParams hiddenLp = new LinearLayout.LayoutParams(0, dp(42), 1.35f);
        hiddenLp.setMargins(dp(6), 0, dp(6), 0);
        actions.addView(hiddenButton, hiddenLp);
        Button refresh = button("تحديث", PANEL, TEXT);
        refresh.setOnClickListener(v -> loadDirectory(currentPath, false));
        actions.addView(refresh, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button create = button("+", PANEL_2, ACCENT);
        create.setTextSize(23);
        create.setOnClickListener(v -> showCreateMenu());
        LinearLayout.LayoutParams createLp = new LinearLayout.LayoutParams(dp(44), dp(42));
        createLp.leftMargin = dp(6);
        actions.addView(create, createLp);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(8);
        root.addView(actions, actionsLp);

        TextView filesHeading = text("الملفات والمجلدات", 14, TEXT, true);
        filesHeading.setPadding(dp(3), dp(10), 0, dp(5));
        root.addView(filesHeading);

        listView = new ListView(this);
        listView.setDivider(new android.graphics.drawable.ColorDrawable(Color.rgb(34, 45, 69)));
        listView.setDividerHeight(dp(1));
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setBackgroundColor(BG);
        listView.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        adapter = new FileAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> onFileClicked(files.get(position)));
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            openFileActions(files.get(position));
            return true;
        });
        root.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        statusLabel = text("جاهز", 11, MUTED, false);
        bottom.addView(statusLabel, new LinearLayout.LayoutParams(0, dp(38), 1));
        moreButton = button("تحميل المزيد", PANEL_2, ACCENT);
        moreButton.setVisibility(View.GONE);
        moreButton.setOnClickListener(v -> loadMore());
        bottom.addView(moreButton, new LinearLayout.LayoutParams(dp(112), dp(36)));
        root.addView(bottom);
        updatePathView();
    }

    private void updatePathView() {
        if (pathInput != null && !searchMode) pathInput.setText(currentPath);
        if (connectionLabel != null) {
            connectionLabel.setText(connected ? "● متصل بـ Termux  •  " + (searchMode ? "نتائج البحث" : currentPath) : "○ غير متصل بـ Termux");
            connectionLabel.setTextColor(connected ? ACCENT : MUTED);
        }
    }

    private boolean ensureReady() {
        if (!isTermuxInstalled()) {
            showHelpDialog(true);
            return false;
        }
        if (checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) {
            showPermissionDialog();
            return false;
        }
        return true;
    }

    private void loadDirectory(String path, boolean append) {
        if (!ensureReady()) return;
        if (append && listingInProgress) return;
        final int requestGeneration = ++viewRequestGeneration;
        listingInProgress = true;
        searchMode = false;
        if (!append) {
            animateListOut();
            currentPath = path;
            nextOffset = 0;
            moreAvailable = false;
            files.clear();
            adapter.notifyDataSetChanged();
        }
        updatePathView();
        setStatus("جاري قراءة الملفات…", MUTED);
        JSONObject payload = new JSONObject();
        try {
            payload.put("path", path);
            payload.put("showHidden", showHidden);
            payload.put("offset", append ? nextOffset : 0);
            payload.put("limit", 100);
        } catch (JSONException ignored) { }
        runBridge("list", payload, (response, error) -> {
            if (requestGeneration != viewRequestGeneration) return;
            listingInProgress = false;
            if (error != null) {
                connected = false;
                updatePathView();
                setStatus("فشل الاتصال", DANGER);
                animateListIn();
                showBridgeError(error);
                return;
            }
            connected = true;
            currentPath = response.optString("path", path);
            JSONArray array = response.optJSONArray("items");
            if (!append) files.clear();
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item != null) files.add(item);
                }
            }
            nextOffset = response.optInt("nextOffset", 0);
            moreAvailable = response.optBoolean("more", false);
            moreButton.setVisibility(moreAvailable ? View.VISIBLE : View.GONE);
            adapter.notifyDataSetChanged();
            animateListIn();
            updatePathView();
            int total = response.optInt("total", files.size());
            String note = response.optInt("metadataErrors", 0) > 0 ? " • بعض العناصر صلاحيتها محدودة" : "";
            setStatus(files.size() + " معروض من " + total + " عنصر" + note, MUTED);
        });
    }

    private void loadMore() {
        if (searchMode || !moreAvailable) return;
        loadDirectory(currentPath, true);
    }

    private void searchFiles(String query) {
        if (!ensureReady()) return;
        final int requestGeneration = ++viewRequestGeneration;
        listingInProgress = true;
        String rootPath = currentPath;
        searchRoot = rootPath;
        searchMode = true;
        animateListOut();
        files.clear();
        adapter.notifyDataSetChanged();
        moreButton.setVisibility(View.GONE);
        setStatus("دا أبحث داخل " + rootPath + "…", MUTED);
        JSONObject payload = new JSONObject();
        try {
            payload.put("path", rootPath);
            payload.put("query", query);
            payload.put("showHidden", showHidden);
            payload.put("limit", 150);
        } catch (JSONException ignored) { }
        runBridge("search", payload, (response, error) -> {
            if (requestGeneration != viewRequestGeneration) return;
            listingInProgress = false;
            if (error != null) {
                searchMode = false;
                showBridgeError(error);
                loadDirectory(rootPath, false);
                return;
            }
            connected = true;
            JSONArray array = response.optJSONArray("items");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item != null) files.add(item);
                }
            }
            moreAvailable = response.optBoolean("more", false);
            adapter.notifyDataSetChanged();
            animateListIn();
            connectionLabel.setText("نتائج البحث داخل: " + rootPath);
            connectionLabel.setTextColor(ACCENT);
            setStatus("نتائج: " + files.size() + (moreAvailable ? " • أكو نتائج أكثر من الحد المعروض" : ""), MUTED);
        });
    }

    private void runBridge(String action, JSONObject payload, ResultCallback callback) {
        if (!isTermuxInstalled()) {
            if (callback != null) callback.onResult(null, "تطبيق Termux مو مثبت.");
            return;
        }
        int id = NEXT_ID.incrementAndGet();
        if (callback != null) callbacks.put(id, callback);
        try {
            Intent command = new Intent();
            command.setClassName("com.termux", "com.termux.app.RunCommandService");
            command.setAction(RUN_ACTION);
            command.putExtra("com.termux.RUN_COMMAND_PATH", "$PREFIX/bin/python");
            command.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", PYTHON_BRIDGE, action});
            command.putExtra("com.termux.RUN_COMMAND_WORKDIR", "~/");
            command.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            command.putExtra("com.termux.RUN_COMMAND_STDIN", payload == null ? "{}" : payload.toString());
            command.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "Termux File Manager");
            command.putExtra("com.termux.RUN_COMMAND_COMMAND_DESCRIPTION", "إدارة ملفات Termux من الواجهة.");

            Intent resultIntent = new Intent(this, CommandResultService.class);
            resultIntent.putExtra(REQUEST_ID_KEY, id);
            int flags = PendingIntent.FLAG_ONE_SHOT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pending = PendingIntent.getService(this, id, resultIntent, flags);
            command.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pending);
            startService(command);
        } catch (SecurityException e) {
            callbacks.remove(id);
            if (callback != null) callback.onResult(null, "Termux رفض تشغيل الأمر. فعّل allow-external-apps وامنح التطبيق صلاحية Run commands من إعدادات أندرويد.");
        } catch (Exception e) {
            callbacks.remove(id);
            if (callback != null) callback.onResult(null, "تعذر بدء الربط: " + e.getMessage());
        }
    }

    private void onFileClicked(JSONObject file) {
        if (searchMode) {
            if (file.optBoolean("isDir", false)) loadDirectory(file.optString("path"), false);
            else openFileActions(file);
            return;
        }
        if (file.optBoolean("isDir", false)) loadDirectory(file.optString("path"), false);
        else openFileActions(file);
    }

    private void openFileActions(JSONObject file) {
        String path = file.optString("path", currentPath + "/" + file.optString("name"));
        boolean isDir = file.optBoolean("isDir", false);
        ArrayList<String> choices = new ArrayList<>();
        ArrayList<String> actions = new ArrayList<>();
        if (!isDir) { choices.add("عرض / تحرير الملف"); actions.add("edit"); }
        choices.add("معلومات العنصر"); actions.add("stat");
        if (!isDir) { choices.add("تحرير داخل طرفية Termux (nano/vi)"); actions.add("terminal_edit"); }
        choices.add("نسخ إلى مجلد"); actions.add("copy");
        choices.add("نقل إلى مجلد"); actions.add("move");
        choices.add("إعادة تسمية"); actions.add("rename");
        choices.add("تعديل الصلاحيات"); actions.add("chmod");
        if (!isDir) { choices.add("تشغيل في Termux"); actions.add("run"); }
        choices.add("حذف نهائي"); actions.add("delete");
        String[] labels = choices.toArray(new String[0]);
        String[] ops = actions.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(file.optString("name", path))
                .setItems(labels, (dialog, which) -> {
                    switch (ops[which]) {
                        case "edit": openTextFile(path); break;
                        case "stat": showInfo(path); break;
                        case "terminal_edit": launchTerminalCommand("cd " + shellQuote(parentOf(path)) + " && if command -v nano >/dev/null 2>&1; then nano -- " + shellQuote(new java.io.File(path).getName()) + "; else vi " + shellQuote(new java.io.File(path).getName()) + "; fi", parentOf(path), "تحرير ملف داخل Termux"); break;
                        case "copy": showDestinationDialog(file, false); break;
                        case "move": showDestinationDialog(file, true); break;
                        case "rename": showRenameDialog(file); break;
                        case "chmod": showChmodDialog(file); break;
                        case "run": runFileInTermux(path); break;
                        case "delete": confirmDelete(file); break;
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void openTextFile(String path) {
        JSONObject payload = new JSONObject();
        try { payload.put("path", path); } catch (JSONException ignored) { }
        setStatus("جاري فتح الملف…", MUTED);
        runBridge("read", payload, (response, error) -> {
            if (error != null) {
                showBridgeError(error);
                return;
            }
            if (response.optBoolean("binary", false)) {
                new AlertDialog.Builder(this).setTitle("ملف ثنائي")
                        .setMessage("هذا الملف يبدو ثنائيًا وما ينفتح كمحرر نصوص.\nالحجم: " + humanSize(response.optLong("size", 0)) + "\n\nتقدر تستخدم تشغيل الملف أو فتحه داخل Termux حسب نوعه.")
                        .setPositiveButton("حسنًا", null).show();
                return;
            }
            showEditor(path, response.optString("text", ""));
        });
    }

    private void showEditor(String path, String originalText) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(4), dp(8), dp(4));
        TextView note = text(path, 10, MUTED, false);
        note.setPadding(dp(3), 0, dp(3), dp(7));
        box.addView(note);
        EditText editor = new EditText(this);
        editor.setTextColor(TEXT);
        editor.setHintTextColor(MUTED);
        editor.setTextSize(13);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setSingleLine(false);
        editor.setHorizontallyScrolling(false);
        editor.setMinLines(12);
        editor.setMaxLines(22);
        editor.setText(originalText);
        editor.setSelection(editor.length());
        editor.setPadding(dp(10), dp(10), dp(10), dp(10));
        editor.setBackground(round(PANEL, 9, Color.rgb(53, 68, 94)));
        box.addView(editor, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(330)));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("محرر النصوص")
                .setView(box)
                .setPositiveButton("حفظ", null)
                .setNegativeButton("إلغاء", null)
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            String text = editor.getText().toString();
            JSONObject payload = new JSONObject();
            try { payload.put("path", path); payload.put("text", text); } catch (JSONException ignored) { }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            runBridge("write", payload, (response, error) -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                if (error != null) showBridgeError(error);
                else {
                    Toast.makeText(this, response.optString("message", "انحفظ الملف"), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    if (!searchMode) loadDirectory(currentPath, false);
                }
            });
        }));
        dialog.show();
    }

    private void showInfo(String path) {
        JSONObject payload = new JSONObject();
        try { payload.put("path", path); } catch (JSONException ignored) { }
        runBridge("stat", payload, (response, error) -> {
            if (error != null) { showBridgeError(error); return; }
            JSONObject item = response.optJSONObject("item");
            if (item == null) return;
            String type = item.optString("kind", "unknown");
            StringBuilder msg = new StringBuilder();
            msg.append("المسار: ").append(item.optString("path", path)).append("\n\n");
            msg.append("النوع: ").append(type).append(item.optBoolean("isLink", false) ? " (رابط رمزي)" : "").append("\n");
            msg.append("الحجم: ").append(humanSize(item.optLong("size", 0))).append("\n");
            msg.append("الصلاحيات: ").append(item.optString("mode", "????")).append("  ").append(item.optString("modeText", "")).append("\n");
            msg.append("UID / GID: ").append(item.optInt("uid", -1)).append(" / ").append(item.optInt("gid", -1)).append("\n");
            msg.append("قراءة: ").append(item.optBoolean("readable") ? "نعم" : "لا").append("  كتابة: ").append(item.optBoolean("writable") ? "نعم" : "لا").append("  تنفيذ: ").append(item.optBoolean("executable") ? "نعم" : "لا").append("\n");
            if (!item.optString("linkTarget", "").isEmpty()) msg.append("الهدف: ").append(item.optString("linkTarget")).append("\n");
            msg.append("آخر تعديل: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date(item.optLong("mtime", 0) * 1000L)));
            new AlertDialog.Builder(this).setTitle(item.optString("name", "معلومات الملف"))
                    .setMessage(msg.toString()).setPositiveButton("إغلاق", null).show();
        });
    }

    private void showDestinationDialog(JSONObject file, boolean move) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(currentPath);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        new AlertDialog.Builder(this).setTitle(move ? "نقل العنصر" : "نسخ العنصر")
                .setMessage("اكتب مسار المجلد الوجهة. اسم العنصر راح يبقى نفسه.")
                .setView(input)
                .setPositiveButton(move ? "نقل" : "نسخ", (dialog, which) -> {
                    JSONObject payload = new JSONObject();
                    try { payload.put("path", file.optString("path")); payload.put("destDir", input.getText().toString().trim()); } catch (JSONException ignored) { }
                    runBridge(move ? "move" : "copy", payload, (response, error) -> {
                        if (error != null) showBridgeError(error);
                        else {
                            Toast.makeText(this, response.optString("message", "اكتملت العملية"), Toast.LENGTH_SHORT).show();
                            loadDirectory(currentPath, false);
                        }
                    });
                }).setNegativeButton("إلغاء", null).show();
    }

    private void showRenameDialog(JSONObject file) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(file.optString("name", ""));
        input.setTextColor(TEXT);
        new AlertDialog.Builder(this).setTitle("إعادة تسمية")
                .setView(input)
                .setPositiveButton("حفظ", (dialog, which) -> {
                    JSONObject payload = new JSONObject();
                    try { payload.put("path", file.optString("path")); payload.put("name", input.getText().toString().trim()); } catch (JSONException ignored) { }
                    runBridge("rename", payload, (response, error) -> {
                        if (error != null) showBridgeError(error);
                        else { Toast.makeText(this, "تغير الاسم", Toast.LENGTH_SHORT).show(); loadDirectory(currentPath, false); }
                    });
                }).setNegativeButton("إلغاء", null).show();
    }

    private void showChmodDialog(JSONObject file) {
        final String[] labels = {"المالك", "المجموعة", "الآخرون"};
        final int[][] bits = {{0400, 0200, 0100}, {0040, 0020, 0010}, {0004, 0002, 0001}};
        final String[] bitLabels = {"قراءة", "كتابة", "تنفيذ"};
        final CheckBox[][] checks = new CheckBox[3][3];
        LinearLayout content = new LinearLayout(this);
        content.setPadding(dp(10), dp(8), dp(10), dp(8));
        content.setOrientation(LinearLayout.VERTICAL);
        int mode = 0;
        try { mode = Integer.parseInt(file.optString("mode", "0644"), 8); } catch (Exception ignored) { }
        for (int row = 0; row < 3; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = text(labels[row], 13, TEXT, true);
            line.addView(label, new LinearLayout.LayoutParams(dp(73), dp(44)));
            for (int col = 0; col < 3; col++) {
                CheckBox cb = new CheckBox(this);
                cb.setText(bitLabels[col]);
                cb.setTextSize(10);
                cb.setTextColor(TEXT);
                cb.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
                cb.setChecked((mode & bits[row][col]) != 0);
                checks[row][col] = cb;
                line.addView(cb, new LinearLayout.LayoutParams(0, dp(44), 1));
            }
            content.addView(line);
        }
        TextView warning = text("هذا يغيّر rwx فقط؛ الروابط الرمزية وبعض الملفات الخاصة قد ترفض chmod.", 10, MUTED, false);
        warning.setPadding(dp(4), dp(7), dp(4), 0);
        content.addView(warning);
        new AlertDialog.Builder(this).setTitle("صلاحيات الملف")
                .setView(content)
                .setPositiveButton("تطبيق", (dialog, which) -> {
                    int newMode = 0;
                    for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) if (checks[row][col].isChecked()) newMode |= bits[row][col];
                    JSONObject payload = new JSONObject();
                    try { payload.put("path", file.optString("path")); payload.put("mode", String.format(java.util.Locale.ROOT, "%03o", newMode)); } catch (JSONException ignored) { }
                    runBridge("chmod", payload, (response, error) -> {
                        if (error != null) showBridgeError(error);
                        else { Toast.makeText(this, "الصلاحيات الجديدة: " + response.optString("mode", ""), Toast.LENGTH_SHORT).show(); loadDirectory(currentPath, false); }
                    });
                }).setNegativeButton("إلغاء", null).show();
    }

    private void confirmDelete(JSONObject file) {
        String path = file.optString("path");
        boolean dir = file.optBoolean("isDir", false);
        new AlertDialog.Builder(this).setTitle("تأكيد الحذف")
                .setMessage("راح ينحذف " + (dir ? "المجلد وكل محتوياته" : "الملف") + " نهائيًا:\n\n" + path + "\n\nماكو سلة محذوفات داخل Termux.")
                .setPositiveButton("حذف نهائي", (dialog, which) -> {
                    JSONObject payload = new JSONObject();
                    try { payload.put("path", path); } catch (JSONException ignored) { }
                    runBridge("delete", payload, (response, error) -> {
                        if (error != null) showBridgeError(error);
                        else { Toast.makeText(this, "انحذف العنصر", Toast.LENGTH_SHORT).show(); loadDirectory(currentPath, false); }
                    });
                }).setNegativeButton("إلغاء", null).show();
    }

    private void showCreateMenu() {
        new AlertDialog.Builder(this).setTitle("إنشاء عنصر جديد")
                .setItems(new String[]{"مجلد جديد", "ملف فارغ جديد"}, (dialog, which) -> showCreateDialog(which == 0))
                .setNegativeButton("إلغاء", null).show();
    }

    private void showCreateDialog(boolean folder) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(folder ? "اسم المجلد" : "اسم الملف، مثل script.py");
        input.setTextColor(TEXT);
        new AlertDialog.Builder(this).setTitle(folder ? "مجلد جديد" : "ملف جديد")
                .setMessage("المكان: " + currentPath)
                .setView(input)
                .setPositiveButton("إنشاء", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty() || name.contains("/")) { showMessage("اكتب اسم صالح بدون /."); return; }
                    JSONObject payload = new JSONObject();
                    try { payload.put("path", currentPath + "/" + name); } catch (JSONException ignored) { }
                    runBridge(folder ? "mkdir" : "create", payload, (response, error) -> {
                        if (error != null) showBridgeError(error);
                        else { Toast.makeText(this, response.optString("message", "تم الإنشاء"), Toast.LENGTH_SHORT).show(); loadDirectory(currentPath, false); }
                    });
                }).setNegativeButton("إلغاء", null).show();
    }

    private void showSearchDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("جزء من اسم الملف أو المجلد");
        input.setTextColor(TEXT);
        new AlertDialog.Builder(this).setTitle("بحث داخل المجلد الحالي")
                .setMessage("البحث يبدأ من: " + currentPath)
                .setView(input)
                .setPositiveButton("بحث", (dialog, which) -> {
                    String q = input.getText().toString().trim();
                    if (!q.isEmpty()) searchFiles(q);
                }).setNegativeButton("إلغاء", null).show();
    }

    private void runFileInTermux(String path) {
        if (!ensureReady()) return;
        String ext = "";
        String fileName = new java.io.File(path).getName();
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) ext = fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        String qFile = shellQuote(fileName);
        String command;
        switch (ext) {
            case "sh": case "bash": command = "bash " + qFile; break;
            case "zsh": command = "zsh " + qFile; break;
            case "fish": command = "fish " + qFile; break;
            case "py": case "pyw": command = "python " + qFile; break;
            case "js": case "cjs": case "mjs": command = "node " + qFile; break;
            case "ts": case "tsx": command = "npx --yes tsx " + qFile; break;
            case "rb": command = "ruby " + qFile; break;
            case "php": command = "php " + qFile; break;
            case "pl": command = "perl " + qFile; break;
            case "lua": command = "lua " + qFile; break;
            case "r": command = "Rscript " + qFile; break;
            case "go": command = "go run " + qFile; break;
            case "swift": command = "swift " + qFile; break;
            case "hs": command = "runghc " + qFile; break;
            case "ex": case "exs": command = "elixir " + qFile; break;
            case "erl": command = "erl -noshell -s " + shellQuote(fileName.substring(0, dot)) + " start -s init stop"; break;
            case "nim": command = "nim r " + qFile; break;
            case "zig": command = "zig run " + qFile; break;
            case "dart": command = "dart run " + qFile; break;
            case "clj": case "cljs": command = "clojure " + qFile; break;
            case "m": command = "octave " + qFile; break;
            case "sql": command = "sqlite3 :memory: < " + qFile; break;
            case "c": {
                String output = ".tfm-build-" + fileName.replaceAll("[^A-Za-z0-9_.-]", "_");
                command = "clang " + qFile + " -o " + shellQuote(output) + " && ./'" + output + "'; code=$?; rm -f " + shellQuote(output) + "; exit $code";
                break;
            }
            case "cc": case "cpp": case "cxx": case "c++": {
                String output = ".tfm-build-" + fileName.replaceAll("[^A-Za-z0-9_.-]", "_");
                command = "clang++ " + qFile + " -o " + shellQuote(output) + " && ./'" + output + "'; code=$?; rm -f " + shellQuote(output) + "; exit $code";
                break;
            }
            case "rs": {
                String output = "/data/data/com.termux/files/usr/tmp/tfm-rust-" + NEXT_ID.incrementAndGet();
                command = "rustc --edition=2021 " + qFile + " -o " + shellQuote(output) + " && " + shellQuote(output) + "; code=$?; rm -f " + shellQuote(output) + "; exit $code";
                break;
            }
            case "java": command = "javac " + qFile + " && java " + shellQuote(fileName.substring(0, dot)); break;
            case "kt": {
                String output = "/data/data/com.termux/files/usr/tmp/tfm-kotlin-" + NEXT_ID.incrementAndGet() + ".jar";
                command = "kotlinc " + qFile + " -include-runtime -d " + shellQuote(output) + " && java -jar " + shellQuote(output) + "; code=$?; rm -f " + shellQuote(output) + "; exit $code";
                break;
            }
            case "html": case "htm": case "pdf": case "png": case "jpg": case "jpeg": case "webp":
                new AlertDialog.Builder(this).setTitle("فتح الملف")
                        .setMessage("هذا النوع يُفتح كتطبيق/مستند، مو كأمر Shell. تقدر تفتح الملف بتطبيق أندرويد أو تستخدم أمرًا مخصصًا من مركز الأدوات.\n\n" + path)
                        .setPositiveButton("نسخ المسار", (d, w) -> {
                            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            cm.setPrimaryClip(ClipData.newPlainText("path", path));
                            Toast.makeText(this, "تم نسخ المسار", Toast.LENGTH_SHORT).show();
                        }).setNegativeButton("إلغاء", null).show();
                return;
            default:
                showCustomRunDialog(path);
                return;
        }
        String fullCommand = "cd " + shellQuote(parentOf(path)) + " && " + command;
        new AlertDialog.Builder(this).setTitle("تشغيل الملف")
                .setMessage("المشغّل المقترح: " + command + "\n\nراح ينفتح Termux وينفّذ الأمر ضمن مجلد الملف. لازم تكون اللغة أو المترجم مثبتًا داخل Termux. شغّل بس ملفات تثق بيها.\n\n" + path)
                .setPositiveButton("تشغيل", (dialog, which) -> launchTerminalCommand(fullCommand, parentOf(path), "تشغيل " + fileName))
                .setNeutralButton("أمر مخصص…", (dialog, which) -> showCustomRunDialog(path))
                .setNegativeButton("إلغاء", null).show();
    }

    private void showCustomRunDialog(String path) {
        String fileName = new java.io.File(path).getName();
        EditText input = new EditText(this);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setTypeface(Typeface.MONOSPACE);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setText("./" + shellQuote(fileName));
        input.setSelection(input.length());
        input.setPadding(dp(10), dp(8), dp(10), dp(8));
        input.setBackground(round(PANEL, 9, Color.rgb(53, 68, 94)));
        new AlertDialog.Builder(this).setTitle("أمر تشغيل مخصص")
                .setMessage("اكتب أمر أي لغة أو أداة مثبتة في Termux. المسار الحالي هو مجلد الملف. مثال: python 'script.py' أو node 'app.js' أو clang 'main.c' -o main && ./main")
                .setView(input)
                .setPositiveButton("تشغيل في Termux", (d, w) -> {
                    String custom = input.getText().toString().trim();
                    if (!custom.isEmpty()) launchTerminalCommand("cd " + shellQuote(parentOf(path)) + " && " + custom, parentOf(path), "أمر مخصص من مدير الملفات");
                }).setNegativeButton("إلغاء", null).show();
    }

    private void launchTerminalCommand(String commandText, String workdir, String label) {
        if (!ensureReady()) return;
        try {
            Intent intent = new Intent();
            intent.setClassName("com.termux", "com.termux.app.RunCommandService");
            intent.setAction(RUN_ACTION);
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "$PREFIX/bin/bash");
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", commandText});
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", workdir == null ? TERMUX_HOME : workdir);
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
            intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
            intent.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", label);
            intent.putExtra("com.termux.RUN_COMMAND_COMMAND_DESCRIPTION", "تشغيل من Termux File Manager");
            startService(intent);
        } catch (Exception e) {
            showBridgeError("تعذر تشغيل الأمر: " + e.getMessage());
        }
    }

    // Small programmatic UI helpers shared by the redesigned file-manager screen.
    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable round(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != Color.TRANSPARENT) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private Button button(String label, int background, int color) {
        Button view = new Button(this);
        view.setText(label);
        view.setTextColor(color);
        view.setTextSize(13);
        view.setAllCaps(false);
        view.setMinHeight(0);
        view.setMinimumHeight(0);
        view.setPadding(dp(9), dp(5), dp(9), dp(5));
        view.setBackground(round(background, 11, Color.TRANSPARENT));
        return view;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private Button buttonSmall(String label, Runnable action) {
        Button view = button(label, PANEL, TEXT);
        view.setTextSize(11);
        view.setOnClickListener(v -> {
            if (action != null) action.run();
        });
        return view;
    }

    private LinearLayout.LayoutParams weightLp() {
        return new LinearLayout.LayoutParams(0, dp(38), 1f);
    }

    private void setStatus(String message, int color) {
        if (statusLabel != null) {
            statusLabel.setText(message == null ? "" : message);
            statusLabel.setTextColor(color);
        }
    }

    private boolean isTermuxInstalled() {
        try {
            getPackageManager().getPackageInfo("com.termux", 0);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void animateListOut() {
        if (listView == null) return;
        listView.animate().cancel();
        listView.animate().alpha(0.72f).translationY(dp(4)).setDuration(75).start();
    }

    private void animateListIn() {
        if (listView == null) return;
        listView.animate().cancel();
        listView.setAlpha(0.72f);
        listView.setTranslationY(dp(4));
        listView.animate().alpha(1f).translationY(0f).setDuration(165)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private String parentOf(String path) {
        String parent = new java.io.File(path).getParent();
        return parent == null ? TERMUX_HOME : parent;
    }

    private void showPermissionDialog() {
        new AlertDialog.Builder(this).setTitle("إكمال ربط Termux")
                .setMessage("التطبيق يحتاج إذن Run commands in Termux environment حتى يقرأ ويعدّل الملفات عن طريق Termux نفسه.\n\nمن إعدادات التطبيق افتح Permissions / Additional permissions وفعّل Run commands in Termux environment. ومن داخل Termux فعّل allow-external-apps=true، بعدها أعد تشغيل Termux.")
                .setPositiveButton("فتح إعدادات التطبيق", (d, w) -> openAppSettings())
                .setNeutralButton("طريقة الربط", (d, w) -> showHelpDialog(false))
                .setNegativeButton("لاحقًا", null).show();
    }

    private void showHelpDialog(boolean termuxMissing) {
        String body = termuxMissing
                ? "ثبّت Termux أولًا من نفس مصدر الإضافات اللي تستخدمه، بعدها ارجع لهذا التطبيق. الربط يعتمد على واجهة RUN_COMMAND الرسمية في Termux."
                : "حتى يشتغل مدير الملفات، لازم تسمح للتطبيق بتنفيذ أوامر عبر Termux. هالشي مطلوب لأن أندرويد يعزل ملفات كل تطبيق عن التطبيقات الثانية.\n\n" +
                  "1) افتح Termux ونفّذ أمر الإعداد الموجود أدناه مرة وحدة.\n" +
                  "2) أعد تشغيل Termux بالكامل.\n" +
                  "3) من إعدادات أندرويد > التطبيقات > Termux File Manager > الأذونات الإضافية، فعّل Run commands in Termux environment إذا ظهر الخيار.\n" +
                  "4) ارجع للتطبيق واضغط تحديث.\n\n" +
                  "الأمر: mkdir -p ~/.termux; touch ~/.termux/termux.properties; if grep -q '^allow-external-apps=' ~/.termux/termux.properties; then sed -i 's/^allow-external-apps=.*/allow-external-apps=true/' ~/.termux/termux.properties; else echo 'allow-external-apps=true' >> ~/.termux/termux.properties; fi\n\n" +
                  "تثبيت Python مطلوب داخل Termux: pkg install python\n\n" +
                  "ملاحظة: يعرض الملفات المخفية مثل .bashrc و .termux افتراضيًا. يظل الوصول محكومًا بصلاحيات أندرويد وملفات المستخدم؛ ماكو وصول root تلقائي.";
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(4), dp(8), 0);
        TextView text = text(body, 13, TEXT, false);
        text.setGravity(Gravity.TOP | Gravity.START);
        text.setPadding(dp(5), dp(7), dp(5), dp(7));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        box.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(termuxMissing ? "Termux غير مثبت" : "إعداد الربط")
                .setView(box)
                .setPositiveButton(termuxMissing ? "إغلاق" : "نسخ أمر الإعداد", null)
                .setNeutralButton("إعدادات التطبيق", null)
                .setNegativeButton("إغلاق", null).create();
        dialog.setOnShowListener(v -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
                if (termuxMissing) { dialog.dismiss(); return; }
                String setup = "mkdir -p ~/.termux; touch ~/.termux/termux.properties; if grep -q '^allow-external-apps=' ~/.termux/termux.properties; then sed -i 's/^allow-external-apps=.*/allow-external-apps=true/' ~/.termux/termux.properties; else echo 'allow-external-apps=true' >> ~/.termux/termux.properties; fi; pkg install -y python";
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("Termux File Manager setup", setup));
                Toast.makeText(this, "انسخنا أمر الإعداد. افتح Termux والصقه ونفّذه.", Toast.LENGTH_LONG).show();
                Intent launch = getPackageManager().getLaunchIntentForPackage("com.termux");
                if (launch != null) startActivity(launch);
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(btn -> { dialog.dismiss(); openAppSettings(); });
        });
        dialog.show();
    }

    private void openAppSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            showMessage("افتح إعدادات أندرويد ثم التطبيقات واختَر Termux File Manager.");
        }
    }

    private void showBridgeError(String message) {
        if (message == null || message.trim().isEmpty()) message = "حدث خطأ غير معروف.";
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        boolean setupIssue = lower.contains("external") || lower.contains("permission") || lower.contains("run_command") || lower.contains("run commands") || lower.contains("not allowed");
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("تعذر تنفيذ العملية")
                .setMessage(message)
                .setPositiveButton("حسنًا", null);
        if (setupIssue) builder.setNeutralButton("إعداد الربط", (d, w) -> showHelpDialog(false));
        builder.show();
    }

    private void showMessage(String message) {
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("حسنًا", null).show();
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(java.util.Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private class FileAdapter extends BaseAdapter {
        @Override public int getCount() { return files.size(); }
        @Override public Object getItem(int position) { return files.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            JSONObject item = files.get(position);
            LinearLayout row;
            if (convertView instanceof LinearLayout) row = (LinearLayout) convertView;
            else {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(8), dp(6), dp(4), dp(6));
                TextView icon = text("", 22, ACCENT, true);
                icon.setGravity(Gravity.CENTER);
                row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(50)));
                LinearLayout texts = new LinearLayout(MainActivity.this);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setGravity(Gravity.CENTER_VERTICAL);
                texts.setPadding(dp(8), 0, dp(4), 0);
                TextView name = text("", 14, TEXT, true);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                TextView meta = text("", 10, MUTED, false);
                meta.setSingleLine(true);
                texts.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(25)));
                texts.addView(meta, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(21)));
                row.addView(texts, new LinearLayout.LayoutParams(0, dp(50), 1));
                TextView arrow = text("⋮", 21, MUTED, true);
                arrow.setGravity(Gravity.CENTER);
                row.addView(arrow, new LinearLayout.LayoutParams(dp(26), dp(44)));
                row.setTag(new TextView[]{icon, name, meta});
            }
            TextView[] fields = (TextView[]) row.getTag();
            String kind = item.optString("kind", "file");
            boolean dir = item.optBoolean("isDir", false);
            boolean hidden = item.optBoolean("hidden", false);
            fields[0].setText(dir ? "▰" : (item.optBoolean("isLink", false) ? "↗" : (hidden ? "·" : "▤")));
            fields[0].setTextColor(dir ? ACCENT : (hidden ? MUTED : Color.rgb(164, 188, 255)));
            fields[1].setText(item.optString("name", "(بدون اسم)") + (hidden ? "  ·" : ""));
            StringBuilder meta = new StringBuilder();
            if (dir) meta.append("مجلد");
            else if ("symlink".equals(kind)) meta.append("رابط رمزي");
            else if ("unknown".equals(kind)) meta.append("غير قابل للقراءة");
            else meta.append(humanSize(item.optLong("size", 0)));
            meta.append("   •   ").append(item.optString("mode", "????"));
            if (searchMode) meta.append("   •   ").append(item.optString("path", ""));
            fields[2].setText(meta.toString());
            row.setBackgroundColor(position % 2 == 0 ? BG : Color.rgb(13, 20, 38));
            return row;
        }
    }
}
