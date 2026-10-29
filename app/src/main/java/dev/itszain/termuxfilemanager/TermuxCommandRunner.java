package dev.itszain.termuxfilemanager;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.concurrent.atomic.AtomicInteger;

/** Routes commands through Termux RUN_COMMAND, never through a shell hosted by this app. */
public final class TermuxCommandRunner {
    private static final AtomicInteger IDS=new AtomicInteger(2000000 + (int)(System.currentTimeMillis()%100000000));
    private TermuxCommandRunner(){}
    public static int run(Context c,String command,String workdir,String label,boolean background) throws Exception {
        return LegacyCommands.run(c, command, workdir, label, background);
    }

    public static void openTui(Context c,String command,String workdir,String label) throws Exception { run(c,command,workdir,label,false); }
    public static boolean hasPermission(Context c){try{return c.getPackageManager().getPackageInfo("com.termux",0)!=null&&c.checkSelfPermission("com.termux.permission.RUN_COMMAND")==android.content.pm.PackageManager.PERMISSION_GRANTED;}catch(Exception e){return false;}}
}
