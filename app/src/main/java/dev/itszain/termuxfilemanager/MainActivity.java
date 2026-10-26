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
    private static final int BG = Color.rgb(11, 16, 32);
    private static final int PANEL = Color.rgb(20, 28, 48);
    private static final int PANEL_2 = Color.rgb(28, 38, 62);
    private static final int TEXT = Color.rgb(239, 244, 255);
    private static final int MUTED = Color.rgb(153, 168, 195);
    private static final int ACCENT = Color.rgb(116, 224, 196);
    private static final int DANGER = Color.rgb(255, 112, 129);
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
            raw = f.read(32769)
        if b'\\x00' in raw[:4096]:
            emit({'ok': True, 'binary': True, 'size': os.path.getsize(path), 'path': path}); return
        if len(raw) > 32768:
            emit({'ok': False, 'tooLarge': True, 'error': 'الملف أكبر من الحد الآمن للعرض داخل التطبيق (32 KB).'}); return
        text = raw.decode('utf-8', errors='replace')
        emit({'ok': True, 'binary': False, 'text': text, 'size': os.path.getsize(path), 'path': path}); return
    if action == 'write':
        path = norm(req.get('path'))
        text = req.get('text', '')
        if not isinstance(text, str):
            fail('محتوى الملف غير صالح.'); return
        if len(text.encode('utf-8')) > 140000:
            fail('النص أكبر من الحد المسموح للتحرير بهالنسخة.'); return
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
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
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
        root.setPadding(dp(14), dp(8), dp(14), dp(8));
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = text("TFM  |  مدير ملفات Termux", 19, TEXT, true);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button help = button("الربط", PANEL_2, ACCENT);
        help.setOnClickListener(v -> showHelpDialog(false));
        top.addView(help, new LinearLayout.LayoutParams(dp(72), dp(40)));
        root.addView(top);

        connectionLabel = text("غير متصل بـ Termux", 12, MUTED, false);
        connectionLabel.setPadding(dp(3), 0, 0, dp(6));
        root.addView(connectionLabel);

        pathBar = new LinearLayout(this);
        pathBar.setGravity(Gravity.CENTER_VERTICAL);
        pathInput = new EditText(this);
        pathInput.setSingleLine(true);
        pathInput.setTextColor(TEXT);
        pathInput.setHintTextColor(MUTED);
        pathInput.setTextSize(12);
        pathInput.setHint("المسار الكامل أو ~/...");
        pathInput.setPadding(dp(11), 0, dp(11), 0);
        pathInput.setBackground(round(PANEL, 12, Color.TRANSPARENT));
        pathBar.addView(pathInput, new LinearLayout.LayoutParams(0, dp(45), 1));
        Button go = button("فتح", PANEL_2, ACCENT);
        go.setOnClickListener(v -> {
            String path = pathInput.getText().toString().trim();
            if (!path.isEmpty()) loadDirectory(path, false);
        });
        LinearLayout.LayoutParams goLp = new LinearLayout.LayoutParams(dp(58), dp(42));
        goLp.setMargins(dp(6), 0, 0, 0);
        pathBar.addView(go, goLp);
        Button up = button("↑", PANEL_2, TEXT);
        up.setTextSize(20);
        up.setOnClickListener(v -> {
            String parent = new java.io.File(currentPath).getParent();
            if (parent != null) loadDirectory(parent, false);
        });
        LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(dp(44), dp(42));
        upLp.setMargins(dp(6), 0, 0, 0);
        pathBar.addView(up, upLp);
        root.addView(pathBar);

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setGravity(Gravity.CENTER_VERTICAL);
        quick.addView(buttonSmall("HOME", () -> loadDirectory(TERMUX_HOME, false)), weightLp());
        quick.addView(buttonSmall("PREFIX", () -> loadDirectory(TERMUX_PREFIX, false)), weightLp());
        quick.addView(buttonSmall("/", () -> loadDirectory("/", false)), weightLp());
        quick.addView(buttonSmall("التخزين", () -> loadDirectory("/storage/emulated/0", false)), weightLp());
        root.addView(quick);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        Button search = button("بحث", PANEL, TEXT);
        search.setOnClickListener(v -> showSearchDialog());
        tools.addView(search, new LinearLayout.LayoutParams(0, dp(40), 1));
        hiddenButton = button("المخفية: ظاهرة", PANEL, ACCENT);
        hiddenButton.setTextSize(11);
        hiddenButton.setOnClickListener(v -> {
            showHidden = !showHidden;
            hiddenButton.setText(showHidden ? "المخفية: ظاهرة" : "المخفية: مخفية");
            loadDirectory(currentPath, false);
        });
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(0, dp(40), 1.4f);
        hlp.setMargins(dp(5), 0, dp(5), 0);
        tools.addView(hiddenButton, hlp);
        Button refresh = button("تحديث", PANEL, TEXT);
        refresh.setOnClickListener(v -> loadDirectory(currentPath, false));
        tools.addView(refresh, new LinearLayout.LayoutParams(0, dp(40), 1));
        Button create = button("+", PANEL_2, ACCENT);
        create.setTextSize(20);
        create.setOnClickListener(v -> showCreateMenu());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(43), dp(40));
        clp.setMargins(dp(5), 0, 0, 0);
        tools.addView(create, clp);
        root.addView(tools);

        TextView heading = text("الملفات والمجلدات", 14, MUTED, true);
        heading.setPadding(dp(3), dp(8), 0, dp(4));
        root.addView(heading);

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
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        statusLabel = text("جاهز", 11, MUTED, false);
        bottom.addView(statusLabel, new LinearLayout.LayoutParams(0, dp(38), 1));
        moreButton = button("المزيد ↓", PANEL_2, ACCENT);
        moreButton.setVisibility(View.GONE);
        moreButton.setOnClickListener(v -> loadMore());
        bottom.addView(moreButton, new LinearLayout.LayoutParams(dp(100), dp(36)));
        root.addView(bottom);
        updatePathView();
    }

    private LinearLayout.LayoutParams weightLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1);
        lp.setMargins(dp(2), dp(6), dp(2), dp(6));
        return lp;
    }

    private Button buttonSmall(String label, Runnable action) {
        Button b = button(label, PANEL_2, TEXT);
        b.setTextSize(11);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private Button button(String label, int background, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(color);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(6), 0, dp(6), 0);
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

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean isTermuxInstalled() {
        try {
            getPackageManager().getPackageInfo("com.termux", 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void setStatus(String text, int color) {
        if (statusLabel != null) {
            statusLabel.setText(text);
            statusLabel.setTextColor(color);
        }
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
        searchMode = false;
        if (!append) {
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
            if (error != null) {
                connected = false;
                updatePathView();
                setStatus("فشل الاتصال", DANGER);
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
        String rootPath = currentPath;
        searchRoot = rootPath;
        searchMode = true;
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
                        .setMessage("هذا الملف يبدو ثنائيًا وما ينفتح كمحرر نصوص.\nالحجم: " + humanSize(response.optLong("size", 0)))
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
        String quoted = "'" + path.replace("'", "'\\''") + "'";
        String command;
        if (path.endsWith(".sh")) command = "cd '" + parentOf(path).replace("'", "'\\''") + "' && exec bash " + quoted;
        else if (path.endsWith(".py")) command = "cd '" + parentOf(path).replace("'", "'\\''") + "' && exec python " + quoted;
        else if (path.endsWith(".js")) command = "cd '" + parentOf(path).replace("'", "'\\''") + "' && exec node " + quoted;
        else command = "cd '" + parentOf(path).replace("'", "'\\''") + "' && exec " + quoted;
        new AlertDialog.Builder(this).setTitle("تشغيل الملف")
                .setMessage("راح ينفتح Termux ويشغّل الملف داخل جلسة طرفية. شغّل بس الملفات اللي تثق بيها.\n\n" + path)
                .setPositiveButton("تشغيل", (dialog, which) -> {
                    try {
                        Intent intent = new Intent();
                        intent.setClassName("com.termux", "com.termux.app.RunCommandService");
                        intent.setAction(RUN_ACTION);
                        intent.putExtra("com.termux.RUN_COMMAND_PATH", "$PREFIX/bin/bash");
                        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", command});
                        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", parentOf(path));
                        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
                        intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
                        intent.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "تشغيل ملف من Termux File Manager");
                        startService(intent);
                    } catch (Exception e) {
                        showBridgeError("تعذر تشغيل الملف: " + e.getMessage());
                    }
                }).setNegativeButton("إلغاء", null).show();
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
