package dev.itszain.termuxfilemanager

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_APPROVE = "dev.itszain.termuxfilemanager.ACTION_APPROVE"
        const val ACTION_DECLINE = "dev.itszain.termuxfilemanager.ACTION_DECLINE"
        const val ACTION_STOP_AI = "dev.itszain.termuxfilemanager.ACTION_STOP_AI"
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_NOTIF_ID = "notif_id"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val app = context.applicationContext as? TfmApplication ?: return
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty()
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)

        if (notifId != -1) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            runCatching { nm?.cancel(notifId) }
        }

        when (action) {
            ACTION_APPROVE, ACTION_DECLINE -> {
                if (chatId.isNotBlank()) {
                    app.graph.agent.approve(chatId, action == ACTION_APPROVE, requestId.ifBlank { null })
                }
            }
            ACTION_STOP_AI -> {
                if (chatId.isNotBlank()) {
                    app.graph.agent.stop(chatId)
                }
            }
        }
    }
}
