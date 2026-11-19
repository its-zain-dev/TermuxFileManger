package dev.itszain.termuxfilemanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

data class BackgroundTaskInfo(
    val id: String,
    val kind: String,
    val title: String,
    val detail: String,
    val progress: Int = -1,
    val max: Int = 0,
    val chatId: String = ""
)

/**
 * Coordinates foreground progress notifications for active AI turns, batch file work,
 * and Termux package installations. Starts only while work is active and stops when idle.
 */
object BackgroundWorkCoordinator {
    const val CHANNEL_WORK = "tfm_background_work"
    const val CHANNEL_ALERTS = "tfm_agent_alerts"
    const val SERVICE_NOTIFICATION_ID = 4101
    private const val ALERT_BASE_ID = 5200

    private val tasks = ConcurrentHashMap<String, BackgroundTaskInfo>()
    @Volatile private var lastNotifyAt = 0L

    private fun enabled(context: Context): Boolean =
        AiSecureStore.settings(context).getBoolean("background_notifications", true)

    private fun canNotify(context: Context): Boolean {
        if (!enabled(context)) return false
        return if (Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_WORK) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_WORK,
                    context.getString(R.string.notif_channel_work),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.notif_channel_work_desc)
                    setShowBadge(false)
                }
            )
        }
        if (nm.getNotificationChannel(CHANNEL_ALERTS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ALERTS,
                    context.getString(R.string.notif_channel_alerts),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notif_channel_alerts_desc)
                }
            )
        }
    }

    fun snapshot(): List<BackgroundTaskInfo> = tasks.values.toList()

    fun beginTask(
        context: Context,
        id: String,
        kind: String,
        title: String,
        detail: String,
        progress: Int = -1,
        max: Int = 0,
        chatId: String = ""
    ) {
        tasks[id] = BackgroundTaskInfo(id, kind, title, detail, progress, max, chatId)
        syncService(context, force = true)
    }

    fun updateTask(context: Context, id: String, detail: String, progress: Int = -1, max: Int = 0) {
        val existing = tasks[id] ?: return
        tasks[id] = existing.copy(detail = detail, progress = progress, max = max)
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotifyAt >= 500L) {
            lastNotifyAt = now
            syncService(context, force = false)
        }
    }

    fun finishTask(context: Context, id: String) {
        tasks.remove(id)
        if (tasks.isEmpty()) {
            runCatching { context.stopService(Intent(context, BackgroundWorkService::class.java)) }
        } else {
            syncService(context, force = true)
        }
    }

    fun notifyApprovalNeeded(context: Context, requestId: String, chatId: String, title: String, detail: String) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notifId = ALERT_BASE_ID + (requestId.hashCode() and 0x3FF)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val openIntent = Intent(context, MainActivity::class.java)
            .putExtra("route", "assistant")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val openPending = PendingIntent.getActivity(context, notifId, openIntent, flags)

        val approveIntent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(NotificationActionReceiver.ACTION_APPROVE)
            .putExtra(NotificationActionReceiver.EXTRA_REQUEST_ID, requestId)
            .putExtra(NotificationActionReceiver.EXTRA_CHAT_ID, chatId)
            .putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
        val approvePending = PendingIntent.getBroadcast(context, notifId + 1, approveIntent, flags)

        val declineIntent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(NotificationActionReceiver.ACTION_DECLINE)
            .putExtra(NotificationActionReceiver.EXTRA_REQUEST_ID, requestId)
            .putExtra(NotificationActionReceiver.EXTRA_CHAT_ID, chatId)
            .putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
        val declinePending = PendingIntent.getBroadcast(context, notifId + 2, declineIntent, flags)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ALERTS)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(detail.take(160))
            .setStyle(Notification.BigTextStyle().bigText(detail.take(500)))
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_input_add, context.getString(R.string.approve), approvePending).build())
            .addAction(Notification.Action.Builder(android.R.drawable.ic_delete, context.getString(R.string.decline), declinePending).build())
            .build()
        runCatching { nm.notify(notifId, notification) }
    }

    fun clearApprovalNotification(context: Context, requestId: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notifId = ALERT_BASE_ID + (requestId.hashCode() and 0x3FF)
        runCatching { nm.cancel(notifId) }
    }

    private fun syncService(context: Context, force: Boolean) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val intent = Intent(context, BackgroundWorkService::class.java).setAction(BackgroundWorkService.ACTION_SYNC)
        runCatching {
            if (force && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}

class BackgroundWorkService : Service() {
    companion object {
        const val ACTION_SYNC = "dev.itszain.termuxfilemanager.BG_SYNC"
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        BackgroundWorkCoordinator.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val active = BackgroundWorkCoordinator.snapshot()
        if (active.isEmpty()) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        val notification = buildNotification(active)
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                startForeground(
                    BackgroundWorkCoordinator.SERVICE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            }.onFailure {
                startForeground(BackgroundWorkCoordinator.SERVICE_NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(BackgroundWorkCoordinator.SERVICE_NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(active: List<BackgroundTaskInfo>): Notification {
        val primary = active.first()
        val route = when (primary.kind) {
            "ai" -> "assistant"
            "opencode" -> "opencode"
            "files" -> "files"
            else -> "terminal"
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openPending = PendingIntent.getActivity(
            this,
            BackgroundWorkCoordinator.SERVICE_NOTIFICATION_ID,
            Intent(this, MainActivity::class.java)
                .putExtra("route", route)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags
        )
        val title = if (active.size == 1) primary.title else getString(R.string.bg_active_tasks, active.size)
        val detail = if (active.size == 1) primary.detail else active.joinToString(" · ") { it.title }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, BackgroundWorkCoordinator.CHANNEL_WORK)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        builder.setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(detail.take(140))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPending)
        if (primary.max > 0 && primary.progress >= 0) {
            builder.setProgress(primary.max, primary.progress.coerceAtMost(primary.max), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        if (primary.kind == "ai" && primary.chatId.isNotBlank()) {
            val stopIntent = Intent(this, NotificationActionReceiver::class.java)
                .setAction(NotificationActionReceiver.ACTION_STOP_AI)
                .putExtra(NotificationActionReceiver.EXTRA_CHAT_ID, primary.chatId)
            val stopPending = PendingIntent.getBroadcast(this, 4102, stopIntent, flags)
            builder.addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause, getString(R.string.stop), stopPending).build())
        }
        return builder.build()
    }

    override fun onDestroy() {
        @Suppress("DEPRECATION")
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
