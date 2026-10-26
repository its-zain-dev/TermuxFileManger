package dev.itszain.termuxfilemanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Window;
import android.widget.TextView;

/** Android text-selection action: lets the user run selected shell text in Termux. */
public class ProcessTextActivity extends Activity {
    private String selectedCode = "";
    private static final int BG = Color.rgb(11, 16, 32);
    private static final int TEXT = Color.rgb(239, 244, 255);
    private static final int MUTED = Color.rgb(153, 168, 195);
    private static final int ACCENT = Color.rgb(116, 224, 196);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        CharSequence chosen = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        selectedCode = chosen == null ? "" : chosen.toString().trim();
        if (TextUtils.isEmpty(selectedCode)) {
            new AlertDialog.Builder(this)
                    .setTitle("RUN CODE ON TERMUX")
                    .setMessage("ما وصل نص محدد من التطبيق السابق.")
                    .setPositiveButton("إغلاق", (d, w) -> finish())
                    .setOnCancelListener(d -> finish())
                    .show();
            return;
        }
        if (!isTermuxInstalled()) {
            new AlertDialog.Builder(this)
                    .setTitle("Termux غير مثبت")
                    .setMessage("ثبّت Termux حتى تتمكن من تنفيذ النص المحدد.")
                    .setPositiveButton("إغلاق", (d, w) -> finish())
                    .setOnCancelListener(d -> finish())
                    .show();
            return;
        }
        if (checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this)
                    .setTitle("صلاحية الربط مطلوبة")
                    .setMessage("افتح Termux File Manager، فعّل صلاحية Run commands in Termux environment وتأكد من تفعيل allow-external-apps=true داخل Termux.")
                    .setPositiveButton("إغلاق", (d, w) -> finish())
                    .setOnCancelListener(d -> finish())
                    .show();
            return;
        }
        showRunConfirmation();
    }

    private void showRunConfirmation() {
        TextView preview = new TextView(this);
        preview.setText(selectedCode);
        preview.setTextColor(TEXT);
        preview.setTextSize(13);
        preview.setTextIsSelectable(true);
        preview.setPadding(dp(15), dp(12), dp(15), dp(12));
        preview.setBackgroundColor(BG);
        preview.setTypeface(android.graphics.Typeface.MONOSPACE);
        preview.setMaxLines(14);
        preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
        new AlertDialog.Builder(this)
                .setTitle("RUN CODE ON TERMUX")
                .setMessage("سيُنفّذ النص التالي كأمر Bash داخل جلسة Termux، مع صلاحيات مستخدم Termux. لا تنفّذ نصًا من مصدر لا تثق به.")
                .setView(preview)
                .setPositiveButton("تنفيذ الآن", (dialog, which) -> runSelectedCode())
                .setNegativeButton("إلغاء", (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void runSelectedCode() {
        try {
            Intent command = new Intent();
            command.setClassName("com.termux", "com.termux.app.RunCommandService");
            command.setAction("com.termux.RUN_COMMAND");
            command.putExtra("com.termux.RUN_COMMAND_PATH", "$PREFIX/bin/bash");
            command.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", selectedCode});
            command.putExtra("com.termux.RUN_COMMAND_WORKDIR", "~/");
            command.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
            command.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
            command.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "RUN CODE ON TERMUX");
            command.putExtra("com.termux.RUN_COMMAND_COMMAND_DESCRIPTION", "نص محدد من قائمة إجراءات تحديد النص في أندرويد.");
            startService(command);
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("تعذر إرسال الأمر إلى Termux")
                    .setMessage(String.valueOf(e.getMessage()))
                    .setPositiveButton("إغلاق", null)
                    .show();
            return;
        }
        finish();
    }

    private boolean isTermuxInstalled() {
        try { getPackageManager().getPackageInfo("com.termux", 0); return true; }
        catch (Exception e) { return false; }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
