package dev.itszain.termuxfilemanager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/** Explicit Android system-consent flow for screen sharing; never starts capture silently. */
public class ScreenCaptureActivity extends Activity {
    private static final int REQUEST_CAPTURE=701;
    @Override protected void onCreate(Bundle b){super.onCreate(b);if(b!=null)return;try{MediaProjectionManager manager=(MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);startActivityForResult(manager.createScreenCaptureIntent(),REQUEST_CAPTURE);}catch(Exception e){Toast.makeText(this,"تعذر فتح إذن التقاط الشاشة: "+e.getMessage(),Toast.LENGTH_LONG).show();finish();}}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=REQUEST_CAPTURE){finish();return;}
        if(result!=RESULT_OK||data==null){
            if(AiSecureStore.hasPendingScreenshot(this))AiSecureStore.clearPendingScreenshot(this);
            Toast.makeText(this,"تم إلغاء مشاركة الشاشة؛ لم تُلتقط أو تُرسل صورة.",Toast.LENGTH_SHORT).show();finish();return;
        }
        try{
            Intent service=new Intent(this,ScreenCaptureService.class)
                    .setAction(ScreenCaptureService.ACTION_START)
                    .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE,result)
                    .putExtra(ScreenCaptureService.EXTRA_RESULT_DATA,data);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(service);else startService(service);
            boolean aiRequested=AiSecureStore.hasPendingScreenshot(this);
            Toast.makeText(this,aiRequested
                    ?"تم تفعيل المشاركة بإذنك. سنفتح Termux ونلتقط لقطة واحدة بعد لحظات."
                    :"مشاركة الشاشة مفعّلة بإذن أندرويد. استخدم زر اللقطة في إشعار TFM عند الحاجة.",Toast.LENGTH_LONG).show();
            Intent launch=getPackageManager().getLaunchIntentForPackage("com.termux");
            if(launch!=null){launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(launch);}
            else if(aiRequested){
                AiSecureStore.clearPendingScreenshot(this);
                Toast.makeText(this,"Termux غير مثبت؛ ألغينا طلب لقطة AI حتى لا نرسل الشاشة الخطأ.",Toast.LENGTH_LONG).show();
                finish();return;
            }
            if(aiRequested){
                Context app=getApplicationContext();
                new Handler(Looper.getMainLooper()).postDelayed(()->{
                    if(AiSecureStore.hasPendingScreenshot(app)&&AiSecureStore.isCaptureActive(app)){
                        try{
                            Intent capture=new Intent(app,ScreenCaptureService.class).setAction(ScreenCaptureService.ACTION_CAPTURE);
                            if(Build.VERSION.SDK_INT>=26)app.startForegroundService(capture);else app.startService(capture);
                        }catch(Exception ignored){}
                    }
                },3500);
            }
        }catch(Exception e){
            if(AiSecureStore.hasPendingScreenshot(this))AiSecureStore.clearPendingScreenshot(this);
            Toast.makeText(this,"تعذر بدء الالتقاط: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
