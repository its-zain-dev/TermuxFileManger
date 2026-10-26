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
        if(command==null||command.trim().isEmpty())throw new IllegalArgumentException("الأمر فارغ");
        if(!hasPermission(c))throw new SecurityException("فعّل إذن Run commands in Termux environment وallow-external-apps=true داخل Termux.");
        int id=IDS.incrementAndGet();
        Intent callback=new Intent(c,CommandResultService.class); callback.putExtra(CommandResultService.EXTRA_REQUEST_ID,id);
        int flags=PendingIntent.FLAG_ONE_SHOT; if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S)flags|=PendingIntent.FLAG_MUTABLE;
        PendingIntent result=PendingIntent.getService(c,id,callback,flags);
        Intent intent=new Intent(); intent.setClassName("com.termux","com.termux.app.RunCommandService"); intent.setAction("com.termux.RUN_COMMAND");
        intent.putExtra("com.termux.RUN_COMMAND_PATH","$PREFIX/bin/bash"); intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS",new String[]{"-lc",command});
        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR",(workdir==null||workdir.trim().isEmpty())?"~/":workdir.trim());
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND",background); intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION","0");
        intent.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL",label==null?"TFM command":label);
        intent.putExtra("com.termux.RUN_COMMAND_COMMAND_DESCRIPTION","User-approved command launched by Termux File Manager.");
        intent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",result);
        c.startService(intent); return id;
    }
    public static void openTui(Context c,String command,String workdir,String label) throws Exception { run(c,command,workdir,label,false); }
    public static boolean hasPermission(Context c){try{return c.getPackageManager().getPackageInfo("com.termux",0)!=null&&c.checkSelfPermission("com.termux.permission.RUN_COMMAND")==android.content.pm.PackageManager.PERMISSION_GRANTED;}catch(Exception e){return false;}}
}
