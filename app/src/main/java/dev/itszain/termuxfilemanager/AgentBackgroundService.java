package dev.itszain.termuxfilemanager;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** Keeps the app process foregrounded during an agent task and posts periodic background summaries. */
public final class AgentBackgroundService extends Service {
    public static final String ACTION_START = "dev.itszain.termuxfilemanager.AGENT_START";
    public static final String ACTION_PROGRESS = "dev.itszain.termuxfilemanager.AGENT_PROGRESS";
    public static final String ACTION_BACKGROUND = "dev.itszain.termuxfilemanager.AGENT_BACKGROUND";
    public static final String ACTION_COMPLETE = "dev.itszain.termuxfilemanager.AGENT_COMPLETE";
    public static final String ACTION_WAITING = "dev.itszain.termuxfilemanager.AGENT_WAITING";
    private static final String CHANNEL = "tfm_agent_work";
    private static final int RUNNING_ID = 8871, COMPLETE_ID = 8872;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String chatId = "", summary = "الوكيل يشتغل بالخلفية.";
    private boolean appBackground = true, foregroundStarted = false;
    private static volatile long lastProgressDispatch = 0L;
    private long lastUpdate = 0L;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!AiSecureStore.settings(AgentBackgroundService.this).getBoolean("agent_background_active", false)) return;
            if (appBackground) updateRunningNotification();
            handler.postDelayed(this, 60_000L);
        }
    };

    public static boolean isActive(Context c) { return AiSecureStore.settings(c).getBoolean("agent_background_active", false); }
    public static void start(Context c, String id) {
        AiSecureStore.settings(c).edit().putBoolean("agent_background_active", true).putString("agent_background_chat", id).apply();
        Intent i = new Intent(c, AgentBackgroundService.class).setAction(ACTION_START).putExtra("chat_id", id);
        startSafely(c, i);
    }
    public static void progress(Context c, String text) {
        if (!isActive(c)) return;
        long now = System.currentTimeMillis();
        if (now - lastProgressDispatch < 12000L) return;
        lastProgressDispatch = now;
        Intent i = new Intent(c, AgentBackgroundService.class).setAction(ACTION_PROGRESS).putExtra("summary", text == null ? "" : text);
        startSafely(c, i);
    }
    public static void setAppBackground(Context c, boolean background) {
        if (!isActive(c)) return;
        Intent i = new Intent(c, AgentBackgroundService.class).setAction(ACTION_BACKGROUND).putExtra("background", background);
        startSafely(c, i);
    }
    public static void waiting(Context c, String text) {
        if (!isActive(c)) return;
        Intent i = new Intent(c, AgentBackgroundService.class).setAction(ACTION_WAITING).putExtra("summary", text == null ? "بانتظار موافقتك." : text);
        startSafely(c, i);
    }
    public static void complete(Context c, String text) {
        if (!isActive(c)) return;
        Intent i = new Intent(c, AgentBackgroundService.class).setAction(ACTION_COMPLETE).putExtra("summary", text == null ? "اكتمل طلب الوكيل." : text);
        startSafely(c, i);
    }
    private static void startSafely(Context c, Intent i) {
        try { if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i); }
        catch (Exception ignored) { }
    }

    @Override public void onCreate() { super.onCreate(); createChannel(); }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            chatId = intent.getStringExtra("chat_id"); if (chatId == null) chatId = "";
            appBackground = false; summary = "بدأ الوكيل تنفيذ المهمة. تقدر تطلع من التطبيق؛ الإشعار يتابع التقدم.";
            ensureForeground(); handler.removeCallbacks(ticker); handler.postDelayed(ticker, 60_000L);
            return START_STICKY;
        }
        ensureForeground();
        if (ACTION_BACKGROUND.equals(action)) {
            appBackground = intent.getBooleanExtra("background", true);
            if (appBackground) updateRunningNotification();
            else updateRunningNotification();
        } else if (ACTION_PROGRESS.equals(action)) {
            String value = intent.getStringExtra("summary");
            if (value != null && !value.trim().isEmpty()) summary = tail(value, 420);
            if (appBackground && System.currentTimeMillis() - lastUpdate > 2500L) updateRunningNotification();
        } else if (ACTION_WAITING.equals(action)) {
            String value = intent.getStringExtra("summary");
            summary = "بانتظار مراجعتك: " + shorten(value == null ? "يوجد إجراء ينتظر الموافقة." : value, 300);
            updateRunningNotification();
        } else if (ACTION_COMPLETE.equals(action)) {
            String value = intent.getStringExtra("summary");
            String finalSummary = shorten(value == null ? "اكتملت المهمة." : value, 500);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(COMPLETE_ID, buildNotification("اكتمل عمل AI Agent", finalSummary, false));
            AiSecureStore.settings(this).edit().putBoolean("agent_background_active", false).remove("agent_background_chat").remove("context_compact_system_override").apply();
            handler.removeCallbacks(ticker);
            sendBroadcast(new Intent(ACTION_COMPLETE).setPackage(getPackageName()));
            stopForeground(true); stopSelf(startId);
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void ensureForeground() {
        if (foregroundStarted) return;
        try { startForeground(RUNNING_ID, buildNotification("TFM AI Agent يعمل", summary, true)); foregroundStarted = true; }
        catch (Exception ignored) { }
    }
    private void updateRunningNotification() {
        lastUpdate = System.currentTimeMillis();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(RUNNING_ID, buildNotification("TFM AI Agent يعمل بالخلفية", summary, true));
    }
    private Notification buildNotification(String title, String text, boolean ongoing) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle(title).setContentText(shorten(text, 180))
          .setStyle(new Notification.BigTextStyle().bigText(text)).setOngoing(ongoing).setOnlyAlertOnce(true)
          .setCategory(ongoing ? Notification.CATEGORY_SERVICE : Notification.CATEGORY_STATUS);
        Intent open = new Intent(this, AiAssistantActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int f = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        b.setContentIntent(PendingIntent.getActivity(this, 8873, open, f));
        return b.build();
    }
    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL) == null)
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, "TFM AI Agent background work", NotificationManager.IMPORTANCE_LOW));
        }
    }
    private static String shorten(String s, int limit) {
        if (s == null) return "";
        s = s.replace('\n', ' ').replace('\r', ' ').trim();
        return s.length() <= limit ? s : s.substring(0, Math.max(0, limit - 1)) + "…";
    }
    private static String tail(String s, int limit) {
        if (s == null) return "";
        s = s.replace('\n', ' ').replace('\r', ' ').trim();
        return s.length() <= limit ? s : "…" + s.substring(s.length() - Math.max(0, limit - 1));
    }
    @Override public void onDestroy() {
        handler.removeCallbacks(ticker);
        if (AiSecureStore.settings(this).getBoolean("agent_background_active", false)) {
            AiSecureStore.settings(this).edit().putBoolean("agent_background_active", false).apply();
        }
        stopForeground(true); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
