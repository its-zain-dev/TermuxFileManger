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
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
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
    private UiTheme theme;
    private Ui.StatusPill pill;
    private LinearLayout crumbs;
    private HorizontalScrollView crumbScroll;
    private View skeleton, emptyView;
    private int animatedUpTo = -1;
    private int listGen = 0;
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
        theme = UiTheme.get(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(10), dp(16), dp(8));
        FrameLayout screen = Ui.setContent(this, theme, root);

        pill = new Ui.StatusPill(this, theme);
        pill.set(false, "Termux");
        ImageView help = Ui.iconButton(this, theme, Icons.INFO, false);
        help.setContentDescription("إعداد الربط");
        help.setOnClickListener(v -> showHelpDialog(false));
        root.addView(Ui.header(this, theme, "TFM", "مساحة عمل Termux", false, pill, help));

        connectionLabel = text("", 11, MUTED, false);

        // path card: up button + breadcrumbs + edit toggle
        LinearLayout pathCard = new LinearLayout(this);
        pathCard.setOrientation(LinearLayout.HORIZONTAL);
        pathCard.setGravity(Gravity.CENTER_VERTICAL);
        pathCard.setPadding(dp(6), dp(6), dp(6), dp(6));
        pathCard.setBackground(Ui.surface(this, theme, theme.panel, 16, 0));
        ImageView up = Ui.iconButton(this, theme, Icons.UP, false);
        up.setContentDescription("مجلد أعلى");
        up.setOnClickListener(v -> {
            String parent = new java.io.File(currentPath).getParent();
            if (parent != null) loadDirectory(parent, false);
        });
        pathCard.addView(up, new LinearLayout.LayoutParams(dp(40), dp(40)));
        crumbScroll = new HorizontalScrollView(this);
        crumbScroll.setHorizontalScrollBarEnabled(false);
        crumbScroll.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        crumbs = new LinearLayout(this);
        crumbs.setOrientation(LinearLayout.HORIZONTAL);
        crumbs.setGravity(Gravity.CENTER_VERTICAL);
        crumbs.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        crumbScroll.addView(crumbs, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams csLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        csLp.setMarginStart(dp(8));
        csLp.setMarginEnd(dp(8));
        pathCard.addView(crumbScroll, csLp);
        ImageView edit = Ui.iconButton(this, theme, Icons.EDIT, false);
        edit.setContentDescription("تعديل المسار");
        pathCard.addView(edit, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout.LayoutParams pcLp = new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT);
        pcLp.topMargin = dp(10);
        root.addView(pathCard, pcLp);
        Ui.enter(pathCard, 1);

        // editable path row (hidden until the pen is tapped)
        pathBar = new LinearLayout(this);
        pathBar.setGravity(Gravity.CENTER_VERTICAL);
        pathBar.setVisibility(View.GONE);
        pathInput = new EditText(this);
        pathInput.setSingleLine(true);
        pathInput.setTextSize(13);
        pathInput.setHint("المسار الحالي أو ~/...");
        pathInput.setPadding(dp(14), 0, dp(14), 0);
        Ui.styleField(this, theme, pathInput);
        pathInput.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        pathBar.addView(pathInput, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button go = button("فتح", ACCENT, BG);
        go.setOnClickListener(v -> {
            String path = pathInput.getText().toString().trim();
            if (!path.isEmpty()) { loadDirectory(path, false); Ui.reveal(pathBar, false); }
        });
        LinearLayout.LayoutParams goLp = new LinearLayout.LayoutParams(dp(66), dp(44));
        goLp.setMarginStart(dp(8));
        pathBar.addView(go, goLp);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT);
        pbLp.topMargin = dp(8);
        root.addView(pathBar, pbLp);
        edit.setOnClickListener(v -> Ui.reveal(pathBar, pathBar.getVisibility() != View.VISIBLE));

        // quick-action chips
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chipScroll.addView(chips);
        addChip(chips, "Home", Icons.HOME, () -> loadDirectory(TERMUX_HOME, false));
        addChip(chips, "Prefix", Icons.BOX, () -> loadDirectory(TERMUX_PREFIX, false));
        addChip(chips, "التخزين", Icons.PHONE, () -> loadDirectory("/storage/emulated/0", false));
        addChip(chips, "بحث", Icons.SEARCH, () -> showSearchDialog());
        hiddenButton = Ui.chip(this, theme, showHidden ? "المخفية ظاهرة" : "المخفية مخفية", showHidden ? Icons.EYE : Icons.EYE_OFF, showHidden);
        hiddenButton.setOnClickListener(v -> {
            showHidden = !showHidden;
            hiddenButton.setText(showHidden ? "المخفية ظاهرة" : "المخفية مخفية");
            Ui.applyChip(this, theme, hiddenButton, showHidden ? Icons.EYE : Icons.EYE_OFF, showHidden);
            loadDirectory(currentPath, false);
        });
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hLp.setMarginEnd(dp(8));
        chips.addView(hiddenButton, hLp);
        addChip(chips, "تحديث", Icons.REFRESH, () -> loadDirectory(currentPath, false));
        LinearLayout.LayoutParams chipsLp = new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipsLp.topMargin = dp(10);
        root.addView(chipScroll, chipsLp);
        Ui.enter(chipScroll, 2);

        // list + skeleton + empty state
        FrameLayout listHost = new FrameLayout(this);
        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setBackgroundColor(Color.TRANSPARENT);
        listView.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        listView.setClipToPadding(false);
        listView.setPadding(0, dp(6), 0, dp(96));
        listView.setVerticalScrollBarEnabled(false);
        listView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        adapter = new FileAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> onFileClicked(files.get(position)));
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            openFileActions(files.get(position));
            return true;
        });
        listHost.addView(listView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout sk = new LinearLayout(this);
        sk.setOrientation(LinearLayout.VERTICAL);
        sk.setPadding(0, dp(10), 0, 0);
        for (int i = 0; i < 7; i++) {
            View bar = new View(this);
            bar.setBackground(Ui.shape(theme.glass, Ui.dpf(this, 20), 0, 0));
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(-1, dp(62));
            bLp.bottomMargin = dp(8);
            sk.addView(bar, bLp);
            Ui.pulse(bar, 1f, 700 + i * 90);
        }
        sk.setVisibility(View.GONE);
        skeleton = sk;
        listHost.addView(sk, new FrameLayout.LayoutParams(-1, -2));

        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        ImageView emptyIcon = Icons.view(this, Icons.FOLDER, Ui.alpha(theme.muted, 150), 64);
        empty.addView(emptyIcon);
        TextView emptyText = text("المجلد فاضي", 14, MUTED, false);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(0, dp(10), 0, 0);
        empty.addView(emptyText);
        empty.setVisibility(View.GONE);
        emptyView = empty;
        listHost.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams lhLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        lhLp.topMargin = dp(4);
        root.addView(listHost, lhLp);

        // status row
        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        statusLabel = text("جاهز", 11, MUTED, false);
        statusLabel.setPadding(dp(4), dp(4), dp(4), dp(2));
        bottom.addView(statusLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        moreButton = Ui.button(this, theme, "تحميل المزيد", theme.panel2, theme.accent);
        moreButton.setVisibility(View.GONE);
        moreButton.setOnClickListener(v -> loadMore());
        bottom.addView(moreButton, new LinearLayout.LayoutParams(dp(120), dp(36)));
        root.addView(bottom);

        root.addView(Ui.dock(this, theme, 0));

        ImageView fab = Ui.iconButton(this, theme, Icons.PLUS, true);
        fab.setElevation(dp(8));
        fab.setContentDescription("إنشاء ملف أو مجلد");
        int fp = dp(15);
        fab.setPadding(fp, fp, fp, fp);
        fab.setOnClickListener(v -> { v.animate().rotationBy(90f).setDuration(280).start(); showCreateMenu(); });
        FrameLayout.LayoutParams fLp = new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.BOTTOM | Gravity.END);
        fLp.bottomMargin = dp(100);
        fLp.setMarginEnd(dp(22));
        screen.addView(fab, fLp);
        Ui.hideOnKeyboard(this, fab);
        Ui.enter(fab, 6);
        updatePathView();
    }

    private TextView addChip(LinearLayout row, String label, int icon, Runnable action) {
        TextView c = Ui.chip(this, theme, label, icon, false);
        c.setOnClickListener(v -> { if (action != null) action.run(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        row.addView(c, lp);
        return c;
    }

    private void rebuildCrumbs() {
        if (crumbs == null) return;
        crumbs.removeAllViews();
        String p = searchMode ? searchRoot : currentPath;
        if (p == null || p.isEmpty()) p = "/";
        ArrayList<String[]> parts = new ArrayList<>();
        String base;
        String rest;
        if (p.equals(TERMUX_HOME) || p.startsWith(TERMUX_HOME + "/")) { parts.add(new String[]{"~", TERMUX_HOME}); base = TERMUX_HOME; rest = p.substring(TERMUX_HOME.length()); }
        else if (p.equals(TERMUX_PREFIX) || p.startsWith(TERMUX_PREFIX + "/")) { parts.add(new String[]{"$PREFIX", TERMUX_PREFIX}); base = TERMUX_PREFIX; rest = p.substring(TERMUX_PREFIX.length()); }
        else { parts.add(new String[]{"/", "/"}); base = ""; rest = p; }
        for (String seg : rest.split("/")) {
            if (seg.isEmpty()) continue;
            base = base + "/" + seg;
            parts.add(new String[]{seg, base});
        }
        for (int i = 0; i < parts.size(); i++) {
            final String target = parts.get(i)[1];
            boolean last = i == parts.size() - 1;
            if (i > 0) {
                ImageView sep = Icons.view(this, Icons.CHEVRON, Ui.alpha(theme.muted, 170), 14);
                crumbs.addView(sep);
            }
            TextView c = Ui.chip(this, theme, parts.get(i)[0], 0, last);
            c.setPadding(dp(12), dp(7), dp(12), dp(7));
            c.setOnClickListener(v -> loadDirectory(target, false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(i > 0 ? dp(2) : 0);
            lp.setMarginEnd(dp(2));
            crumbs.addView(c, lp);
        }
        crumbScroll.post(() -> crumbScroll.fullScroll(View.FOCUS_RIGHT));
    }

    private void setLoading(boolean loading) {
        if (skeleton == null || emptyView == null) return;
        skeleton.setVisibility(loading && files.isEmpty() ? View.VISIBLE : View.GONE);
        boolean empty = !loading && files.isEmpty() && connected;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) Ui.enter(emptyView, 0);
    }

    private void updatePathView() {
        if (pathInput != null && !searchMode) pathInput.setText(currentPath);
        if (pill != null) pill.set(connected, connected ? (searchMode ? "نتائج البحث" : "Termux متصل") : "غير متصل");
        rebuildCrumbs();
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
            if (pill != null) pill.set(true, "نتائج البحث");
            rebuildCrumbs();
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
        int[] icons = new int[ops.length];
        int dangerIdx = -1;
        for (int i = 0; i < ops.length; i++) {
            switch (ops[i]) {
                case "edit": icons[i] = Icons.EDIT; break;
                case "stat": icons[i] = Icons.INFO; break;
                case "terminal_edit": icons[i] = Icons.TERMINAL; break;
                case "copy": icons[i] = Icons.COPY; break;
                case "move": icons[i] = Icons.FOLDER; break;
                case "rename": icons[i] = Icons.EDIT; break;
                case "chmod": icons[i] = Icons.KEY; break;
                case "run": icons[i] = Icons.PLAY; break;
                default: icons[i] = Icons.TRASH; dangerIdx = i; break;
            }
        }
        Ui.actionSheet(this, file.optString("name", path), path, labels, icons, dangerIdx, which -> {
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
                new Ui.Sheet(this).setTitle("ملف ثنائي")
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
        Ui.styleField(this, th(), editor);
        editor.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.addView(editor, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(330)));
        AlertDialog dialog = new Ui.Sheet(this)
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
                    Ui.toast(this, response.optString("message", "انحفظ الملف"));
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
            new Ui.Sheet(this).setTitle(item.optString("name", "معلومات الملف"))
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
        new Ui.Sheet(this).setTitle(move ? "نقل العنصر" : "نسخ العنصر")
                .setMessage("اكتب مسار المجلد الوجهة. اسم العنصر راح يبقى نفسه.")
                .setView(input)
                .setPositiveButton(move ? "نقل" : "نسخ", (dialog, which) -> {
                    JSONObject payload = new JSONObject();
                    try { payload.put("path", file.optString("path")); payload.put("destDir", input.getText().toString().trim()); } catch (JSONException ignored) { }
                    runBridge(move ? "move" : "copy", payload, (response, error) -> {
                        if (error != null) showBridgeError(error);
                        else {
                            Ui.toast(this, response.optString("message", "اكتملت العملية"));
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
        Ui.styleField(this, th(), input);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        new Ui.Sheet(this).setTitle("إعادة تسمية")
                .setView(input)
                .setPositiveButton("حفظ", (dialog, which) -> {
                    JSONObject payload = new JSONObject();
                    try { payload.put("path", file.optString("path")); payload.put("name", input.getText().toString().trim()); } catch (JSONException ignored) { }
                    runBridge("rename", payload, (response, error) -> {
                        if (error != null) showBridgeError(error);
                        else { Ui.toast(this, "تغير الاسم"); loadDirectory(currentPath, false); }
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
        new Ui.Sheet(this).setTitle("صلاحيات الملف")
                .setView(content)
                .setPositiveButton("تطبيق", (dialog, which) -> {
                    int newMode = 0;
                    for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) if (checks[row][col].isChecked()) newMode |= bits[row][col];
                    JSONObject payload = new JSONObject();
                    try { payload.put("path", file.optString("path")); payload.put("mode", String.format(java.util.Locale.ROOT, "%03o", newMode)); } catch (JSONException ignored) { }
                    runBridge("chmod", payload, (response, error) -> {
                        if (error != null) showBridgeError(error);
                        else { Ui.toast(this, "الصلاحيات الجديدة: " + response.optString("mode", "")); loadDirectory(currentPath, false); }
                    });
                }).setNegativeButton("إلغاء", null).show();
    }

    private void confirmDelete(JSONObject file) {
        String path = file.optString("path");
        boolean dir = file.optBoolean("isDir", false);
        new Ui.Sheet(this).setTitle("تأكيد الحذف")
                .setMessage("راح ينحذف " + (dir ? "المجلد وكل محتوياته" : "الملف") + " نهائيًا:\n\n" + path + "\n\nماكو سلة محذوفات داخل Termux.")
                .setPositiveButton("حذف نهائي", (dialog, which) -> {
                    JSONObject payload = new JSONObject();
                    try { payload.put("path", path); } catch (JSONException ignored) { }
                    runBridge("delete", payload, (response, error) -> {
                        if (error != null) showBridgeError(error);
                        else { Ui.toast(this, "انحذف العنصر"); loadDirectory(currentPath, false); }
                    });
                }).setNegativeButton("إلغاء", null).show();
    }

    private void showCreateMenu() {
        Ui.actionSheet(this, "إنشاء عنصر جديد", currentPath, new String[]{"مجلد جديد", "ملف فارغ جديد"},
                new int[]{Icons.FOLDER, Icons.FILE}, -1, which -> showCreateDialog(which == 0));
    }

    private void showCreateDialog(boolean folder) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(folder ? "اسم المجلد" : "اسم الملف، مثل script.py");
        input.setTextColor(TEXT);
        Ui.styleField(this, th(), input);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        new Ui.Sheet(this).setTitle(folder ? "مجلد جديد" : "ملف جديد")
                .setMessage("المكان: " + currentPath)
                .setView(input)
                .setPositiveButton("إنشاء", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty() || name.contains("/")) { showMessage("اكتب اسم صالح بدون /."); return; }
                    JSONObject payload = new JSONObject();
                    try { payload.put("path", currentPath + "/" + name); } catch (JSONException ignored) { }
                    runBridge(folder ? "mkdir" : "create", payload, (response, error) -> {
                        if (error != null) showBridgeError(error);
                        else { Ui.toast(this, response.optString("message", "تم الإنشاء")); loadDirectory(currentPath, false); }
                    });
                }).setNegativeButton("إلغاء", null).show();
    }

    private void showSearchDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("جزء من اسم الملف أو المجلد");
        input.setTextColor(TEXT);
        Ui.styleField(this, th(), input);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        new Ui.Sheet(this).setTitle("بحث داخل المجلد الحالي")
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
                new Ui.Sheet(this).setTitle("فتح الملف")
                        .setMessage("هذا النوع يُفتح كتطبيق/مستند، مو كأمر Shell. تقدر تفتح الملف بتطبيق أندرويد أو تستخدم أمرًا مخصصًا من مركز الأدوات.\n\n" + path)
                        .setPositiveButton("نسخ المسار", (d, w) -> {
                            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            cm.setPrimaryClip(ClipData.newPlainText("path", path));
                            Ui.toast(this, "تم نسخ المسار");
                        }).setNegativeButton("إلغاء", null).show();
                return;
            default:
                showCustomRunDialog(path);
                return;
        }
        String fullCommand = "cd " + shellQuote(parentOf(path)) + " && " + command;
        new Ui.Sheet(this).setTitle("تشغيل الملف")
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
        new Ui.Sheet(this).setTitle("أمر تشغيل مخصص")
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
    private UiTheme th() {
        if (theme == null) theme = UiTheme.get(this);
        return theme;
    }

    private int dp(float value) {
        return Ui.dp(this, value);
    }

    private android.graphics.drawable.Drawable round(int color, int radiusDp, int strokeColor) {
        return Ui.surface(this, th(), color, radiusDp, strokeColor);
    }

    private Button button(String label, int background, int color) {
        return Ui.button(this, th(), label, background, color);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = Ui.text(this, value, size, color, bold);
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
        animatedUpTo = -1;
        listGen++;
        listView.animate().cancel();
        listView.animate().alpha(0.35f).setDuration(90).start();
        setLoading(true);
    }

    private void animateListIn() {
        if (listView == null) return;
        listView.animate().cancel();
        listView.animate().alpha(1f).setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
        setLoading(false);
    }

    private String parentOf(String path) {
        String parent = new java.io.File(path).getParent();
        return parent == null ? TERMUX_HOME : parent;
    }

    private void showPermissionDialog() {
        new Ui.Sheet(this).setTitle("إكمال ربط Termux")
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
        AlertDialog dialog = new Ui.Sheet(this).setTitle(termuxMissing ? "Termux غير مثبت" : "إعداد الربط")
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
                Ui.toast(this, "انسخنا أمر الإعداد. افتح Termux والصقه ونفّذه.");
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
        AlertDialog.Builder builder = new Ui.Sheet(this)
                .setTitle("تعذر تنفيذ العملية")
                .setMessage(message)
                .setPositiveButton("حسنًا", null);
        if (setupIssue) builder.setNeutralButton("إعداد الربط", (d, w) -> showHelpDialog(false));
        builder.show();
    }

    private void showMessage(String message) {
        new Ui.Sheet(this).setMessage(message).setPositiveButton("حسنًا", null).show();
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
            FrameLayout wrap;
            if (convertView instanceof FrameLayout) wrap = (FrameLayout) convertView;
            else {
                wrap = new FrameLayout(MainActivity.this);
                wrap.setPadding(0, dp(4), 0, dp(4));
                LinearLayout card = new LinearLayout(MainActivity.this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(dp(10), dp(9), dp(12), dp(9));
                float r = Ui.dpf(MainActivity.this, 18f);
                card.setBackground(new android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(Ui.alpha(th().accent, 60)),
                        Ui.surface(MainActivity.this, th(), th().panel, 14, 0),
                        Ui.shape(Color.WHITE, r, 0, 0)));
                ImageView tile = new ImageView(MainActivity.this);
                tile.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                tile.setPadding(dp(10), dp(10), dp(10), dp(10));
                card.addView(tile, new LinearLayout.LayoutParams(dp(46), dp(46)));
                LinearLayout texts = new LinearLayout(MainActivity.this);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setGravity(Gravity.CENTER_VERTICAL);
                TextView name = text("", 15, TEXT, true);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                TextView meta = text("", 11, MUTED, false);
                meta.setSingleLine(true);
                meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
                texts.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                mLp.topMargin = dp(2);
                texts.addView(meta, mLp);
                LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                tLp.setMarginStart(dp(12));
                tLp.setMarginEnd(dp(8));
                card.addView(texts, tLp);
                ImageView trail = new ImageView(MainActivity.this);
                trail.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                card.addView(trail, new LinearLayout.LayoutParams(dp(20), dp(20)));
                wrap.addView(card, new FrameLayout.LayoutParams(-1, -2));
                wrap.setTag(new View[]{tile, name, meta, trail});
            }
            View[] f = (View[]) wrap.getTag();
            String nm = item.optString("name", "(بدون اسم)");
            String kind = item.optString("kind", "file");
            boolean dir = item.optBoolean("isDir", false);
            boolean link = item.optBoolean("isLink", false);
            boolean hidden = item.optBoolean("hidden", false);
            int tint = Icons.tintForFile(th(), nm, dir);
            ImageView tile = (ImageView) f[0];
            tile.setImageDrawable(Icons.get(MainActivity.this, Icons.forFile(nm, dir, link), tint));
            tile.setBackground(Ui.shape(Ui.alpha(tint, 38), Ui.dpf(MainActivity.this, 15f), 0, 0));
            ((TextView) f[1]).setText(nm);
            StringBuilder meta = new StringBuilder();
            if (dir) meta.append("مجلد");
            else if ("symlink".equals(kind)) meta.append("رابط رمزي");
            else if ("unknown".equals(kind)) meta.append("غير قابل للقراءة");
            else meta.append(humanSize(item.optLong("size", 0)));
            meta.append("  •  ").append(item.optString("mode", "????"));
            if (searchMode) meta.append("  •  ").append(item.optString("path", ""));
            ((TextView) f[2]).setText(meta.toString());
            ((ImageView) f[3]).setImageDrawable(Icons.get(MainActivity.this, dir ? Icons.CHEVRON : Icons.MORE, Ui.alpha(MUTED, 200)));
            wrap.setAlpha(hidden ? 0.62f : 1f);
            long key = (long) listGen * 100000L + position;
            Object prev = wrap.getTag(R.id.tfm_pos);
            if (prev == null || ((Long) prev) != key) {
                if (position > animatedUpTo && position < 14) Ui.enter(wrap, position);
                else Ui.settle(wrap);
                if (hidden && !(position > animatedUpTo && position < 14)) wrap.setAlpha(0.62f);
                wrap.setTag(R.id.tfm_pos, key);
            }
            animatedUpTo = Math.max(animatedUpTo, position);
            return wrap;
        }
    }
}
