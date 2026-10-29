package dev.itszain.termuxfilemanager

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*

class CommandResultService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var inFlight = 0
    private var latestStart = 0
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopSelfResult(startId); return START_NOT_STICKY }
        inFlight++
        latestStart = startId
        scope.launch {
            try { graph().commands.accept(intent) }
            finally {
                withContext(Dispatchers.Main) {
                    inFlight--
                    if (inFlight == 0) stopSelfResult(latestStart)
                }
            }
        }
        return START_REDELIVER_INTENT
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object {
        const val ACTION_RESULT = "dev.itszain.termuxfilemanager.COMMAND_RESULT"
        const val EXTRA_REQUEST_ID = "tfm_request_id"
    }
}
