package dev.itszain.termuxfilemanager;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;

public class CommandResultService extends IntentService {
    public static final String ACTION_RESULT = "dev.itszain.termuxfilemanager.COMMAND_RESULT";
    public static final String EXTRA_REQUEST_ID = "tfm_request_id";

    public CommandResultService() {
        super("TermuxFileManagerResultService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) return;
        int requestId = intent.getIntExtra(EXTRA_REQUEST_ID, -1);
        Bundle result = intent.getBundleExtra("result");
        Intent broadcast = new Intent(ACTION_RESULT);
        broadcast.setPackage(getPackageName());
        broadcast.putExtra(EXTRA_REQUEST_ID, requestId);
        if (result != null) {
            broadcast.putExtra("stdout", result.getString("stdout", ""));
            broadcast.putExtra("stderr", result.getString("stderr", ""));
            broadcast.putExtra("exit_code", result.getInt("exitCode", 0));
            broadcast.putExtra("err_code", result.getInt("err", -1));
            broadcast.putExtra("err_msg", result.getString("errmsg", ""));
        } else {
            broadcast.putExtra("stdout", "");
            broadcast.putExtra("stderr", "لم يصل رد من Termux.");
            broadcast.putExtra("exit_code", -1);
            broadcast.putExtra("err_code", 1);
            broadcast.putExtra("err_msg", "No result bundle returned by Termux");
        }
        sendBroadcast(broadcast);
    }
}
