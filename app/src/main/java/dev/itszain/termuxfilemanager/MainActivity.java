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
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
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
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {
    private int BG, PANEL, PANEL_2, TEXT, MUTED, ACCENT, DANGER, DIVIDER;
    private UiKit kit;
    private String lastThemeName = "midnight";
    private static final String TERMUX_HOME = "/data/data/com.termux/files/home";
    private static final String TERMUX_PREFIX = "/data/data/com.termux/files/usr";
    private static final String ANDROID_STORAGE = "/storage/emulated/0";
    private static final String RUN_ACTION = "com.termux.RUN_COMMAND";
    private static final String RESULT_ACTION = CommandResultService.ACTION_RESULT;
    private static final String REQUEST_ID_KEY = CommandResultService.EXTRA_REQUEST_ID;
    private static final AtomicInteger NEXT_ID = new AtomicInteger((int) (System.currentTimeMillis() % 100000));

    private LinearLayout root;
    private LinearLayout breadcrumbBar;
    private LinearLayout quickBar;
    private EditText searchInput;
    private TextView titleLabel;
    private TextView subtitleLabel;
    private TextView connectionLabel;
    private TextView statusLabel;
    private TextView hiddenChip;
    private TextView sortChip;
    private TextView viewChip;
    private TextView countLabel;
    private LinearLayout connectionHolder;
    private Button moreButton;
    private ListView listView;
    private GridView gridView;
    private FrameLayout listArea;
    private LinearLayout emptyStateBox;
    private FileAdapter listAdapter;
    private FileAdapter gridAdapter;
    private boolean gridMode = false;
    private boolean suppressSearchWatcher = false;
    private int sortMode = 0;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<JSONObject> files = new ArrayList<>();
    private final Map<Integer, ResultCallback> callbacks = new HashMap<>();
    private String currentPath = TERMUX_HOME;
    private boolean showHidden = true;
    private boolean connected = false;
    private boolean searchMode = false;
    private String searchRoot = TERMUX_HOME;
    private String lastQuery = "";
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
        kit = new UiKit(this);
        UiTheme palette = UiTheme.get(this);
        BG=palette.bg; PANEL=palette.panel; PANEL_2=palette.panel2; TEXT=palette.text; MUTED=palette.muted; ACCENT=palette.accent; DANGER=palette.danger; DIVIDER=Color.rgb(42, 53, 77); lastThemeName=UiTheme.getName(this);
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
            clearSearch();
            return;
        }
        String parent = new java.io.File(currentPath).getParent();
        if (parent != null && !parent.equals(currentPath)) navigateTo(parent);
        else super.onBackPressed();
    }

    private void buildUi() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(kit.bg);
        setContentView(frame);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(kit.bg);
        frame.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildTopBar();
        buildPathBar();
        buildQuickRow();
        buildToolbar();
        buildListArea();
        buildBottomBar();

        // Floating action button for creating folders and files.
        Button fab = kit.primary("+");
        fab.setTextSize(26);
        fab.setElevation(kit.dp(8));
        fab.setContentDescription("إنشاء ملف أو مجلد جديد");
        fab.setOnClickListener(v -> showCreateMenu());
        FrameLayout.LayoutParams fabLp = new FrameLayout.LayoutParams(kit.dp(56), kit.dp(56), Gravity.BOTTOM | Gravity.END);
        fabLp.setMargins(0, 0, kit.dp(18), kit.dp(74));
        frame.addView(fab, fabLp);

        updatePathView();
    }

    private void buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(kit.dp(18), kit.dp(14), kit.dp(12), kit.dp(6));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titleLabel = kit.text("مدير الملفات", 24, kit.text, true);
        titles.addView(titleLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        subtitleLabel = kit.caption("مساحة عمل Termux");
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleLp.topMargin = kit.dp(3);
        titles.addView(subtitleLabel, subtitleLp);
        bar.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView agent = kit.chip("الوكيل", false);
        agent.setTextColor(kit.accent);
        agent.setPadding(kit.dp(14), kit.dp(8), kit.dp(14), kit.dp(8));
        agent.setContentDescription("فتح وكيل الذكاء الاصطناعي");
        agent.setOnClickListener(v -> {
            startActivity(new Intent(this, AiAssistantActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
        bar.addView(agent, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, kit.dp(36)));

        Button menu = kit.iconButton("⋮", "خيارات إضافية");
        menu.setOnClickListener(v -> showMainMenu());
        LinearLayout.LayoutParams menuLp = new LinearLayout.LayoutParams(kit.dp(36), kit.dp(36));
        menuLp.leftMargin = kit.dp(8);
        bar.addView(menu, menuLp);
        root.addView(bar);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(kit.dp(18), 0, kit.dp(16), 0);
        connectionHolder = new LinearLayout(this);
        statusRow.addView(connectionHolder, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        countLabel = kit.text("", 11.5f, kit.muted, false);
        countLabel.setPadding(kit.dp(10), 0, 0, 0);
        statusRow.addView(countLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(statusRow);
    }

    private void buildPathBar() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(kit.dp(16), kit.dp(4), kit.dp(12), kit.dp(2));

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        breadcrumbBar = new LinearLayout(this);
        breadcrumbBar.setOrientation(LinearLayout.HORIZONTAL);
        breadcrumbBar.setGravity(Gravity.CENTER_VERTICAL);
        scroller.addView(breadcrumbBar);
        row.addView(scroller, new LinearLayout.LayoutParams(0, kit.dp(38), 1));

        Button edit = kit.iconButton("✎", "كتابة مسار");
        edit.setTextSize(15);
        edit.setOnClickListener(v -> kit.promptDialog("انتقل إلى مسار", "اكتب المسار، مثل ~/projects", currentPath, "فتح", value -> {
            if (!value.isEmpty()) navigateTo(value);
        }));
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(kit.dp(36), kit.dp(36));
        editLp.leftMargin = kit.dp(6);
        row.addView(edit, editLp);
        root.addView(row);
    }

    private void buildQuickRow() {
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setPadding(kit.dp(16), kit.dp(2), kit.dp(16), kit.dp(2));
        quickBar = new LinearLayout(this);
        quickBar.setOrientation(LinearLayout.HORIZONTAL);
        quickBar.setGravity(Gravity.CENTER_VERTICAL);
        scroller.addView(quickBar);

        addQuickChip("الرئيسية", TERMUX_HOME);
        addQuickChip("PREFIX", TERMUX_PREFIX);
        addQuickChip("التخزين", ANDROID_STORAGE);
        addQuickChip("التنزيلات", ANDROID_STORAGE + "/Download");
        root.addView(scroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(40)));
    }

    private void addQuickChip(String label, String path) {
        TextView chip = kit.chip(label, false);
        chip.setOnClickListener(v -> navigateTo(path));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, kit.dp(32));
        lp.rightMargin = kit.dp(8);
        quickBar.addView(chip, lp);
    }

    private void buildToolbar() {
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(kit.dp(16), kit.dp(8), kit.dp(16), kit.dp(2));

        LinearLayout searchBox = new LinearLayout(this);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setPadding(kit.dp(12), 0, kit.dp(10), 0);
        searchBox.setBackground(kit.round(kit.panelAlt, 14, kit.outline));
        TextView glyph = kit.text("⌕", 17, kit.muted, false);
        glyph.setGravity(Gravity.CENTER);
        searchBox.addView(glyph, new LinearLayout.LayoutParams(kit.dp(20), kit.dp(44)));

        searchInput = new EditText(this);
        searchInput.setTextColor(kit.text);
        searchInput.setHintTextColor(kit.muted);
        searchInput.setTextSize(13);
        searchInput.setSingleLine(true);
        searchInput.setBackgroundColor(Color.TRANSPARENT);
        searchInput.setPadding(kit.dp(8), 0, kit.dp(4), 0);
        searchInput.setHint("ابحث داخل هذا المجلد");
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchBox.addView(searchInput, new LinearLayout.LayoutParams(0, kit.dp(44), 1));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { if (!suppressSearchWatcher) scheduleSearch(s.toString()); }
        });
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            runSearchNow(searchInput.getText().toString().trim());
            return true;
        });
        searchRow.addView(searchBox, new LinearLayout.LayoutParams(0, kit.dp(44), 1));
        root.addView(searchRow);

        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setPadding(kit.dp(16), kit.dp(6), kit.dp(16), kit.dp(2));
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setGravity(Gravity.CENTER_VERTICAL);

        hiddenChip = kit.chip("المخفية: ظاهرة", false);
        hiddenChip.setOnClickListener(v -> {
            showHidden = !showHidden;
            updateToolbarChips();
            loadDirectory(currentPath, false);
        });
        chipRow.addView(hiddenChip, chipLp());

        sortChip = kit.chip("الترتيب: الاسم", false);
        sortChip.setOnClickListener(v -> showSortSheet());
        chipRow.addView(sortChip, chipLp());

        viewChip = kit.chip("العرض: قائمة", false);
        viewChip.setOnClickListener(v -> toggleGridMode());
        chipRow.addView(viewChip, chipLp());

        chipScroll.addView(chipRow);
        root.addView(chipScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(42)));
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, kit.dp(30));
        lp.rightMargin = kit.dp(8);
        return lp;
    }

    private void updateToolbarChips() {
        if (hiddenChip != null) hiddenChip.setText(showHidden ? "المخفية: ظاهرة" : "المخفية: مخفية");
        if (sortChip != null) sortChip.setText("الترتيب: " + sortLabel());
        if (viewChip != null) viewChip.setText("العرض: " + (gridMode ? "شبكة" : "قائمة"));
    }

    private String sortLabel() {
        switch (sortMode) {
            case 1: return "الحجم";
            case 2: return "التاريخ";
            case 3: return "النوع";
            default: return "الاسم";
        }
    }

    private void showSortSheet() {
        final String[] labels = {"الاسم (أ - ي)", "الحجم (الأكبر أولًا)", "التاريخ (الأحدث أولًا)", "النوع (امتداد الملف)"};
        kit.sheet("ترتيب العناصر", labels, index -> {
            sortMode = index;
            updateToolbarChips();
            applySort();
        });
    }

    private void toggleGridMode() {
        gridMode = !gridMode;
        updateToolbarChips();
        if (gridMode) {
            listView.setVisibility(View.GONE);
            gridView.setVisibility(View.VISIBLE);
        } else {
            listView.setVisibility(View.VISIBLE);
            gridView.setVisibility(View.GONE);
        }
    }

    private void buildListArea() {
        listArea = new FrameLayout(this);
        listArea.setBackgroundColor(kit.bg);

        listView = new ListView(this);
        listView.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        listView.setDividerHeight(kit.dp(8));
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setBackgroundColor(Color.TRANSPARENT);
        listView.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        listView.setClipToPadding(false);
        listView.setPadding(kit.dp(10), kit.dp(4), kit.dp(10), kit.dp(12));
        listAdapter = new FileAdapter(false);
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((parent, view, position, id) -> onFileClicked(files.get(position)));
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            openFileActions(files.get(position));
            return true;
        });
        listArea.addView(listView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        gridView = new GridView(this);
        gridView.setNumColumns(2);
        gridView.setHorizontalSpacing(kit.dp(10));
        gridView.setVerticalSpacing(kit.dp(10));
        gridView.setPadding(kit.dp(12), kit.dp(4), kit.dp(12), kit.dp(12));
        gridView.setBackgroundColor(Color.TRANSPARENT);
        gridView.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        gridView.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        gridAdapter = new FileAdapter(true);
        gridView.setAdapter(gridAdapter);
        gridView.setOnItemClickListener((parent, view, position, id) -> onFileClicked(files.get(position)));
        gridView.setOnItemLongClickListener((parent, view, position, id) -> {
            openFileActions(files.get(position));
            return true;
        });
        gridView.setVisibility(View.GONE);
        listArea.addView(gridView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyStateBox = new LinearLayout(this);
        emptyStateBox.setOrientation(LinearLayout.VERTICAL);
        emptyStateBox.setVisibility(View.GONE);
        listArea.addView(emptyStateBox, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(listArea, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    private void buildBottomBar() {
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(kit.dp(18), kit.dp(2), kit.dp(16), kit.dp(10));
        statusLabel = kit.text("جاهز", 11.5f, kit.muted, false);
        bottom.addView(statusLabel, new LinearLayout.LayoutParams(0, kit.dp(34), 1));
        moreButton = kit.ghost("تحميل المزيد");
        moreButton.setTextSize(12);
        moreButton.setVisibility(View.GONE);
        moreButton.setOnClickListener(v -> loadMore());
        bottom.addView(moreButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, kit.dp(32)));
        root.addView(bottom);
    }

    private void rebuildBreadcrumbs() {
        if (breadcrumbBar == null) return;
        breadcrumbBar.removeAllViews();
        List<String> labels = new ArrayList<>();
        List<String> targets = new ArrayList<>();
        String path = currentPath == null ? "" : currentPath;
        if (path.startsWith(TERMUX_HOME)) {
            labels.add("~");
            targets.add(TERMUX_HOME);
            String relative = path.substring(TERMUX_HOME.length());
            if (relative.startsWith("/")) relative = relative.substring(1);
            String accumulated = TERMUX_HOME;
            if (!relative.isEmpty()) {
                for (String segment : relative.split("/")) {
                    if (segment.isEmpty()) continue;
                    accumulated = accumulated + "/" + segment;
                    labels.add(segment);
                    targets.add(accumulated);
                }
            }
        } else {
            labels.add("/");
            targets.add("/");
            String accumulated = "";
            for (String segment : path.split("/")) {
                if (segment.isEmpty()) continue;
                accumulated = accumulated + "/" + segment;
                labels.add(segment);
                targets.add(accumulated);
            }
        }
        for (int i = 0; i < labels.size(); i++) {
            final String target = targets.get(i);
            final boolean last = i == labels.size() - 1;
            TextView crumb = kit.chip(labels.get(i), last);
            if (last) crumb.setTextColor(kit.text);
            crumb.setOnClickListener(v -> navigateTo(target));
            breadcrumbBar.addView(crumb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, kit.dp(30)));
            if (!last) {
                TextView separator = kit.text("›", 13, kit.muted, false);
                separator.setGravity(Gravity.CENTER);
                breadcrumbBar.addView(separator, new LinearLayout.LayoutParams(kit.dp(14), kit.dp(30)));
            }
        }
    }

    private void updatePathView() {
        if (titleLabel != null) titleLabel.setText(currentNameOf(currentPath));
        if (subtitleLabel != null) subtitleLabel.setText(searchMode ? ("نتائج البحث داخل " + searchRoot) : currentPath);
        if (connectionHolder != null) {
            connectionHolder.removeAllViews();
            connectionHolder.addView(kit.statusPill(connected ? "متصل بـ Termux" : "غير متصل", connected));
        }
        rebuildBreadcrumbs();
        updateToolbarChips();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (emptyStateBox == null) return;
        boolean empty = files.isEmpty() && !listingInProgress && connected;
        emptyStateBox.removeAllViews();
        if (!empty) {
            emptyStateBox.setVisibility(View.GONE);
            return;
        }
        String title = searchMode ? "ماكو نتائج مطابقة" : "المجلد فاضي";
        String subtitle = searchMode
                ? ("ما لقينا أي ملف يطابق «" + lastQuery + "» داخل هذا المجلد")
                : "اضغط + لإنشاء ملف أو مجلد جديد، أو ارجع لمجلد آخر من المسار فوق.";
        String action = searchMode ? "ارجع للمجلد" : "إنشاء عنصر";
        emptyStateBox.addView(kit.emptyState(searchMode ? "⌕" : "▤", title, subtitle, action, () -> {
            if (searchMode) clearSearch();
            else showCreateMenu();
        }));
        emptyStateBox.setVisibility(View.VISIBLE);
    }

    private void refreshAdapters() {
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
        if (gridAdapter != null) gridAdapter.notifyDataSetChanged();
    }

    private void applySort() {
        Collections.sort(files, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject left, JSONObject right) {
                boolean leftDir = left.optBoolean("isDir", false);
                boolean rightDir = right.optBoolean("isDir", false);
                if (leftDir != rightDir) return leftDir ? -1 : 1;
                if (sortMode == 1) {
                    int bySize = Long.compare(right.optLong("size", 0), left.optLong("size", 0));
                    if (bySize != 0) return bySize;
                } else if (sortMode == 2) {
                    int byDate = Long.compare(right.optLong("mtime", 0), left.optLong("mtime", 0));
                    if (byDate != 0) return byDate;
                } else if (sortMode == 3) {
                    int byType = extensionOf(left).compareTo(extensionOf(right));
                    if (byType != 0) return byType;
                }
                return left.optString("name", "").compareToIgnoreCase(right.optString("name", ""));
            }
        });
        refreshAdapters();
    }

    private String extensionOf(JSONObject item) {
        String name = item.optString("name", "");
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return item.optBoolean("isDir", false) ? "📁 مجلد" : "";
        return name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private String currentNameOf(String path) {
        if (path == null || path.isEmpty()) return "مدير الملفات";
        if (path.equals(TERMUX_HOME)) return "الرئيسية";
        if (path.equals(TERMUX_PREFIX)) return "PREFIX";
        if (path.equals(ANDROID_STORAGE)) return "التخزين";
        String name = new java.io.File(path).getName();
        return name.isEmpty() ? path : name;
    }

    private String formatDate(long epochSeconds) {
        if (epochSeconds <= 0) return "";
        try {
            return new java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault())
                    .format(new java.util.Date(epochSeconds * 1000L));
        } catch (Exception e) {
            return "";
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
            refreshAdapters();
            searchHandler.removeCallbacksAndMessages(null);
            lastQuery = "";
            suppressSearchWatcher = true;
            if (searchInput != null) searchInput.setText("");
            suppressSearchWatcher = false;
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
            applySort();
            animateListIn();
            updatePathView();
            int total = response.optInt("total", files.size());
            String note = response.optInt("metadataErrors", 0) > 0 ? " • بعض العناصر صلاحيتها محدودة" : "";
            if (countLabel != null) countLabel.setText(files.size() + " معروض من " + total);
            setStatus(files.size() + " معروض من " + total + " عنصر" + note, MUTED);
        });
    }

    private void loadMore() {
        if (searchMode || !moreAvailable) return;
        loadDirectory(currentPath, true);
    }

    /** Debounced search so typing in the toolbar does not spam the Termux bridge. */
    private void scheduleSearch(String query) {
        searchHandler.removeCallbacksAndMessages(null);
        final String value = query == null ? "" : query.trim();
        if (value.isEmpty()) {
            if (searchMode) clearSearch();
            return;
        }
        searchHandler.postDelayed(() -> runSearchNow(value), 420);
    }

    private void runSearchNow(String query) {
        if (query == null || query.isEmpty()) {
            if (searchMode) clearSearch();
            return;
        }
        lastQuery = query;
        searchFiles(query);
    }

    private void clearSearch() {
        searchHandler.removeCallbacksAndMessages(null);
        lastQuery = "";
        searchMode = false;
        suppressSearchWatcher = true;
        if (searchInput != null) searchInput.setText("");
        suppressSearchWatcher = false;
        loadDirectory(currentPath, false);
    }

    /** Navigating to another folder always drops the active search query. */
    private void navigateTo(String path) {
        searchHandler.removeCallbacksAndMessages(null);
        lastQuery = "";
        suppressSearchWatcher = true;
        if (searchInput != null) searchInput.setText("");
        suppressSearchWatcher = false;
        loadDirectory(path, false);
    }

    private void showMainMenu() {
        final String[] labels = {"جلسات Terminal", "مركز الأدوات", "إعدادات الذكاء الاصطناعي", "إعداد وربط Termux", "تحديث القائمة"};
        kit.sheet("المزيد", labels, index -> {
            switch (index) {
                case 0: startActivity(new Intent(this, TerminalSessionsActivity.class)); break;
                case 1: startActivity(new Intent(this, ToolsActivity.class)); break;
                case 2: startActivity(new Intent(this, AiSettingsActivity.class)); break;
                case 3: showHelpDialog(false); break;
                default: navigateTo(currentPath); break;
            }
        });
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
        refreshAdapters();
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
            applySort();
            animateListIn();
            updatePathView();
            if (countLabel != null) countLabel.setText(files.size() + " نتيجة");
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
        choices.add("تشغيل بأمر مخصص"); actions.add("custom_run");
        choices.add("حذف نهائي"); actions.add("delete");
        String[] labels = choices.toArray(new String[0]);
        String[] ops = actions.toArray(new String[0]);
        kit.sheet(file.optString("name", path), labels, which -> {
                    switch (ops[which]) {
                        case "edit": openTextFile(path); break;
                        case "stat": showInfo(path); break;
                        case "terminal_edit": launchTerminalCommand("cd " + shellQuote(parentOf(path)) + " && if command -v nano >/dev/null 2>&1; then nano -- " + shellQuote(new java.io.File(path).getName()) + "; else vi " + shellQuote(new java.io.File(path).getName()) + "; fi", parentOf(path), "تحرير ملف داخل Termux"); break;
                        case "copy": showDestinationDialog(file, false); break;
                        case "move": showDestinationDialog(file, true); break;
                        case "rename": showRenameDialog(file); break;
                        case "chmod": showChmodDialog(file); break;
                        case "run": runFileInTermux(path); break;
                        case "custom_run": showCustomRunDialog(path); break;
                        case "delete": confirmDelete(file); break;
                    }
        });
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
                kit.infoDialog("ملف ثنائي",
                        "هذا الملف يبدو ثنائيًا وما ينفتح كمحرر نصوص.\nالحجم: " + humanSize(response.optLong("size", 0)) + "\n\nتقدر تستخدم تشغيل الملف أو فتحه داخل Termux حسب نوعه.");
                return;
            }
            showEditor(path, response.optString("text", ""));
        });
    }

    private void showEditor(String path, String originalText) {
        final android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout card = kit.dialogCard("محرر النصوص", null);

        TextView note = kit.mono(path, 10.5f, kit.muted);
        note.setPadding(0, 0, 0, kit.dp(8));
        card.addView(note, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText editor = new EditText(this);
        editor.setTextColor(kit.text);
        editor.setHintTextColor(kit.muted);
        editor.setTextSize(12.5f);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setSingleLine(false);
        editor.setHorizontallyScrolling(false);
        editor.setMinLines(12);
        editor.setMaxLines(22);
        editor.setText(originalText);
        editor.setSelection(editor.length());
        editor.setPadding(kit.dp(11), kit.dp(10), kit.dp(11), kit.dp(10));
        editor.setBackground(kit.round(kit.panelAlt, 12, kit.outline));
        LinearLayout.LayoutParams editorLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(320));
        editorLp.topMargin = kit.dp(2);
        card.addView(editor, editorLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button cancel = kit.ghost("إلغاء");
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, kit.dp(44), 1));
        final Button save = kit.primary("حفظ");
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, kit.dp(44), 1);
        saveLp.leftMargin = kit.dp(8);
        actions.addView(save, saveLp);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = kit.dp(14);
        card.addView(actions, actionsLp);

        save.setOnClickListener(v -> {
            String value = editor.getText().toString();
            JSONObject payload = new JSONObject();
            try { payload.put("path", path); payload.put("text", value); } catch (JSONException ignored) { }
            save.setEnabled(false);
            save.setText("جارٍ الحفظ…");
            runBridge("write", payload, (response, error) -> {
                save.setEnabled(true);
                save.setText("حفظ");
                if (error != null) showBridgeError(error);
                else {
                    Toast.makeText(this, response.optString("message", "انحفظ الملف"), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    if (!searchMode) loadDirectory(currentPath, false);
                }
            });
        });
        kit.showCard(dialog, card);
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
            kit.infoDialog(item.optString("name", "معلومات الملف"), msg.toString());
        });
    }

    private void showDestinationDialog(JSONObject file, boolean move) {
        kit.promptDialog(move ? "نقل العنصر" : "نسخ العنصر",
                "مسار المجلد الوجهة — اسم العنصر راح يبقى نفسه",
                currentPath, move ? "نقل" : "نسخ", value -> {
                    if (value.isEmpty()) return;
                    JSONObject payload = new JSONObject();
                    try { payload.put("path", file.optString("path")); payload.put("destDir", value); } catch (JSONException ignored) { }
                    runBridge(move ? "move" : "copy", payload, (response, error) -> {
                        if (error != null) showBridgeError(error);
                        else {
                            Toast.makeText(this, response.optString("message", "اكتملت العملية"), Toast.LENGTH_SHORT).show();
                            loadDirectory(currentPath, false);
                        }
                    });
                });
    }

    private void showRenameDialog(JSONObject file) {
        kit.promptDialog("إعادة تسمية", "الاسم الجديد", file.optString("name", ""), "حفظ", value -> {
            if (value.isEmpty()) return;
            JSONObject payload = new JSONObject();
            try { payload.put("path", file.optString("path")); payload.put("name", value); } catch (JSONException ignored) { }
            runBridge("rename", payload, (response, error) -> {
                if (error != null) showBridgeError(error);
                else { Toast.makeText(this, "تغير الاسم", Toast.LENGTH_SHORT).show(); loadDirectory(currentPath, false); }
            });
        });
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
            TextView label = kit.text(labels[row], 13, kit.text, true);
            line.addView(label, new LinearLayout.LayoutParams(kit.dp(73), kit.dp(44)));
            for (int col = 0; col < 3; col++) {
                CheckBox cb = new CheckBox(this);
                cb.setText(bitLabels[col]);
                cb.setTextSize(10);
                cb.setTextColor(kit.text);
                cb.setButtonTintList(android.content.res.ColorStateList.valueOf(kit.accent));
                cb.setChecked((mode & bits[row][col]) != 0);
                checks[row][col] = cb;
                line.addView(cb, new LinearLayout.LayoutParams(0, dp(44), 1));
            }
            content.addView(line);
        }
        TextView warning = kit.caption("هذا يغيّر rwx فقط؛ الروابط الرمزية وبعض الملفات الخاصة قد ترفض chmod.");
        warning.setPadding(dp(4), dp(7), dp(4), 0);
        content.addView(warning);
        final android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout card = kit.dialogCard("صلاحيات الملف", null);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        contentLp.topMargin = kit.dp(8);
        card.addView(content, contentLp);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button cancel = kit.ghost("إلغاء");
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, kit.dp(44), 1));
        Button apply = kit.primary("تطبيق");
        LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(0, kit.dp(44), 1);
        applyLp.leftMargin = kit.dp(8);
        actions.addView(apply, applyLp);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = kit.dp(14);
        card.addView(actions, actionsLp);
        apply.setOnClickListener(v -> {
            int newMode = 0;
            for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) if (checks[row][col].isChecked()) newMode |= bits[row][col];
            JSONObject payload = new JSONObject();
            try { payload.put("path", file.optString("path")); payload.put("mode", String.format(java.util.Locale.ROOT, "%03o", newMode)); } catch (JSONException ignored) { }
            dialog.dismiss();
            runBridge("chmod", payload, (response, error) -> {
                if (error != null) showBridgeError(error);
                else { Toast.makeText(this, "الصلاحيات الجديدة: " + response.optString("mode", ""), Toast.LENGTH_SHORT).show(); loadDirectory(currentPath, false); }
            });
        });
        kit.showCard(dialog, card);
    }

    private void confirmDelete(JSONObject file) {
        final String path = file.optString("path");
        boolean dir = file.optBoolean("isDir", false);
        kit.confirmDialog("تأكيد الحذف",
                "راح ينحذف " + (dir ? "المجلد وكل محتوياته" : "الملف") + " نهائيًا:\n\n" + path + "\n\nماكو سلة محذوفات داخل Termux.",
                "حذف نهائي", () -> {
                    JSONObject payload = new JSONObject();
                    try { payload.put("path", path); } catch (JSONException ignored) { }
                    runBridge("delete", payload, (response, error) -> {
                        if (error != null) showBridgeError(error);
                        else { Toast.makeText(this, "انحذف العنصر", Toast.LENGTH_SHORT).show(); loadDirectory(currentPath, false); }
                    });
                });
    }

    private void showCreateMenu() {
        final String[] labels = {"مجلد جديد", "ملف فارغ جديد"};
        kit.sheet("إنشاء عنصر جديد", labels, which -> showCreateDialog(which == 0));
    }

    private void showCreateDialog(boolean folder) {
        kit.promptDialog(folder ? "مجلد جديد" : "ملف جديد",
                (folder ? "اسم المجلد" : "اسم الملف، مثل script.py") + " — المكان: " + currentPath,
                "", "إنشاء", name -> {
                    if (name.isEmpty() || name.contains("/")) { showMessage("اكتب اسم صالح بدون /."); return; }
                    JSONObject payload = new JSONObject();
                    try { payload.put("path", currentPath + "/" + name); } catch (JSONException ignored) { }
                    runBridge(folder ? "mkdir" : "create", payload, (response, error) -> {
                        if (error != null) showBridgeError(error);
                        else { Toast.makeText(this, response.optString("message", "تم الإنشاء"), Toast.LENGTH_SHORT).show(); loadDirectory(currentPath, false); }
                    });
                });
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
                kit.confirmDialog("فتح الملف",
                        "هذا النوع يُفتح كتطبيق/مستند، مو كأمر Shell. تقدر تفتح الملف بتطبيق أندرويد أو تستخدم أمرًا مخصصًا من مركز الأدوات.\n\n" + path,
                        "نسخ المسار", () -> {
                            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            cm.setPrimaryClip(ClipData.newPlainText("path", path));
                            Toast.makeText(this, "تم نسخ المسار", Toast.LENGTH_SHORT).show();
                        });
                return;
            default:
                showCustomRunDialog(path);
                return;
        }
        String fullCommand = "cd " + shellQuote(parentOf(path)) + " && " + command;
        kit.confirmDialog("تشغيل الملف",
                "المشغّل المقترح: " + command + "\n\nراح ينفتح Termux وينفّذ الأمر ضمن مجلد الملف. لازم تكون اللغة أو المترجم مثبتًا داخل Termux. شغّل بس ملفات تثق بيها.\n\n" + path,
                "تشغيل", () -> launchTerminalCommand(fullCommand, parentOf(path), "تشغيل " + fileName));
    }

    private void showCustomRunDialog(String path) {
        String fileName = new java.io.File(path).getName();
        kit.promptDialog("أمر تشغيل مخصص",
                "المسار الحالي هو مجلد الملف. مثال: python 'script.py' أو node 'app.js'",
                "./" + shellQuote(fileName), "تشغيل في Termux", custom -> {
                    if (!custom.isEmpty()) launchTerminalCommand("cd " + shellQuote(parentOf(path)) + " && " + custom, parentOf(path), "أمر مخصص من مدير الملفات");
                });
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
        kit.confirmDialog("إكمال ربط Termux",
                "التطبيق يحتاج إذن Run commands in Termux environment حتى يقرأ ويعدّل الملفات عن طريق Termux نفسه.\n\nمن إعدادات التطبيق افتح Permissions / Additional permissions وفعّل Run commands in Termux environment. ومن داخل Termux فعّل allow-external-apps=true، بعدها أعد تشغيل Termux.",
                "فتح إعدادات التطبيق", this::openAppSettings);
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

        final android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout card = kit.dialogCard(termuxMissing ? "Termux غير مثبت" : "إعداد الربط", null);

        ScrollView scroll = new ScrollView(this);
        TextView bodyView = kit.text(body, 12.5f, kit.text, false);
        bodyView.setGravity(Gravity.TOP | Gravity.START);
        bodyView.setLineSpacing(kit.dp(2), 1f);
        scroll.addView(bodyView);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(260));
        scrollLp.topMargin = kit.dp(6);
        card.addView(scroll, scrollLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button settings = kit.ghost("إعدادات التطبيق");
        settings.setOnClickListener(v -> {
            dialog.dismiss();
            openAppSettings();
        });
        actions.addView(settings, new LinearLayout.LayoutParams(0, kit.dp(44), 1));
        Button copy = kit.primary(termuxMissing ? "إغلاق" : "نسخ أمر الإعداد");
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, kit.dp(44), 1);
        copyLp.leftMargin = kit.dp(8);
        actions.addView(copy, copyLp);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = kit.dp(14);
        card.addView(actions, actionsLp);

        copy.setOnClickListener(v -> {
            if (termuxMissing) {
                dialog.dismiss();
                return;
            }
            String setup = "mkdir -p ~/.termux; touch ~/.termux/termux.properties; if grep -q '^allow-external-apps=' ~/.termux/termux.properties; then sed -i 's/^allow-external-apps=.*/allow-external-apps=true/' ~/.termux/termux.properties; else echo 'allow-external-apps=true' >> ~/.termux/termux.properties; fi; pkg install -y python";
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Termux File Manager setup", setup));
            Toast.makeText(this, "انسخنا أمر الإعداد. افتح Termux والصقه ونفّذه.", Toast.LENGTH_LONG).show();
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.termux");
            if (launch != null) startActivity(launch);
            dialog.dismiss();
        });
        kit.showCard(dialog, card);
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
        if (setupIssue) {
            kit.confirmDialog("تعذر تنفيذ العملية", message, "إعداد الربط", () -> showHelpDialog(false));
            return;
        }
        kit.infoDialog("تعذر تنفيذ العملية", message);
    }

    private void showMessage(String message) {
        kit.infoDialog(null, message);
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(java.util.Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * Renders one file entry either as a comfortable list row or as a grid card.
     * Both modes share the same data source and the same metadata rules.
     */
    private class FileAdapter extends BaseAdapter {
        private final boolean grid;

        FileAdapter(boolean grid) {
            this.grid = grid;
        }

        @Override public int getCount() { return files.size(); }
        @Override public Object getItem(int position) { return files.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            JSONObject item = files.get(position);
            if (item == null) return convertView;
            return grid ? gridCell(item, convertView) : listRow(item, convertView);
        }

        private View listRow(JSONObject item, View convertView) {
            LinearLayout row;
            TextView icon;
            TextView name;
            TextView meta;
            View tile;
            if (convertView instanceof LinearLayout && convertView.getTag() instanceof Object[]) {
                row = (LinearLayout) convertView;
                Object[] fields = (Object[]) row.getTag();
                icon = (TextView) fields[0];
                name = (TextView) fields[1];
                meta = (TextView) fields[2];
                tile = (View) fields[3];
            } else {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(kit.dp(12), kit.dp(9), kit.dp(8), kit.dp(9));
                row.setBackground(kit.interactive(kit.panel, 16, kit.outline));

                FrameLayout iconTile = new FrameLayout(MainActivity.this);
                icon = kit.text("", 17, kit.accent, true);
                icon.setGravity(Gravity.CENTER);
                iconTile.addView(icon, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                row.addView(iconTile, new LinearLayout.LayoutParams(kit.dp(40), kit.dp(40)));

                LinearLayout texts = new LinearLayout(MainActivity.this);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setGravity(Gravity.CENTER_VERTICAL);
                name = kit.text("", 14.5f, kit.text, true);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                texts.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                meta = kit.text("", 11, kit.muted, false);
                meta.setSingleLine(true);
                meta.setPadding(0, kit.dp(3), 0, 0);
                texts.addView(meta, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                textsLp.leftMargin = kit.dp(12);
                row.addView(texts, textsLp);

                TextView chevron = kit.text("›", 22, kit.muted, false);
                chevron.setGravity(Gravity.CENTER);
                row.addView(chevron, new LinearLayout.LayoutParams(kit.dp(24), kit.dp(40)));

                tile = iconTile;
                row.setTag(new Object[]{icon, name, meta, iconTile});
            }
            bindEntry(item, icon, name, meta, tile, true);
            return row;
        }

        private View gridCell(JSONObject item, View convertView) {
            LinearLayout cell;
            TextView icon;
            TextView name;
            TextView meta;
            View tile;
            if (convertView instanceof LinearLayout && convertView.getTag() instanceof Object[]) {
                cell = (LinearLayout) convertView;
                Object[] fields = (Object[]) cell.getTag();
                icon = (TextView) fields[0];
                name = (TextView) fields[1];
                meta = (TextView) fields[2];
                tile = (View) fields[3];
            } else {
                cell = new LinearLayout(MainActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(kit.dp(10), kit.dp(14), kit.dp(10), kit.dp(12));
                cell.setBackground(kit.round(kit.panel, 18, kit.outline));

                FrameLayout iconTile = new FrameLayout(MainActivity.this);
                icon = kit.text("", 21, kit.accent, true);
                icon.setGravity(Gravity.CENTER);
                iconTile.addView(icon, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(kit.dp(46), kit.dp(46));
                tileLp.bottomMargin = kit.dp(10);
                cell.addView(iconTile, tileLp);

                name = kit.text("", 13, kit.text, true);
                name.setGravity(Gravity.CENTER);
                name.setMaxLines(2);
                name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                cell.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                meta = kit.text("", 10.5f, kit.muted, false);
                meta.setGravity(Gravity.CENTER);
                meta.setSingleLine(true);
                meta.setPadding(0, kit.dp(4), 0, 0);
                cell.addView(meta, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                tile = iconTile;
                cell.setTag(new Object[]{icon, name, meta, iconTile});
            }
            bindEntry(item, icon, name, meta, tile, false);
            return cell;
        }

        /** Shared binding so list rows and grid cards always describe the entry the same way. */
        private void bindEntry(JSONObject item, TextView icon, TextView name, TextView meta, View tile, boolean withDate) {
            String kind = item.optString("kind", "file");
            boolean dir = item.optBoolean("isDir", false);
            boolean hidden = item.optBoolean("hidden", false);
            boolean link = item.optBoolean("isLink", false);

            String glyph = dir ? "▰" : (link ? "↗" : (hidden ? "·" : "▤"));
            icon.setText(glyph);
            int tileColor = dir ? kit.accentSoft : kit.panelAlt;
            tile.setBackground(kit.round(tileColor, 14));
            int iconColor = dir ? kit.accent : (hidden ? kit.muted : kit.blend(kit.accent, kit.bg, 0.42f));
            icon.setTextColor(iconColor);

            String label = item.optString("name", "(بدون اسم)");
            name.setText(hidden ? label + "  ·" : label);

            StringBuilder description = new StringBuilder();
            if (dir) description.append("مجلد");
            else if (link) description.append("رابط رمزي");
            else if ("unknown".equals(kind)) description.append("غير قابل للقراءة");
            else description.append(humanSize(item.optLong("size", 0)));
            description.append("  ·  ").append(item.optString("mode", "????"));
            if (withDate) {
                String stamp = formatDate(item.optLong("mtime", 0));
                if (!stamp.isEmpty()) description.append("  ·  ").append(stamp);
            }
            if (searchMode) description.append("\n").append(item.optString("path", ""));
            meta.setText(description.toString());
        }
    }
}
