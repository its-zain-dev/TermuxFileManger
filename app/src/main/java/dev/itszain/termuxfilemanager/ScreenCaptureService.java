package dev.itszain.termuxfilemanager;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

/** User-authorized MediaProjection. Frames are discarded unless a screenshot is explicitly requested. */
public class ScreenCaptureService extends Service {
    public static final String ACTION_START="dev.itszain.termuxfilemanager.SCREEN_START", ACTION_CAPTURE="dev.itszain.termuxfilemanager.SCREEN_CAPTURE", ACTION_STOP="dev.itszain.termuxfilemanager.SCREEN_STOP", ACTION_SCREENSHOT_SAVED="dev.itszain.termuxfilemanager.SCREENSHOT_SAVED";
    public static final String EXTRA_RESULT_CODE="screen_result_code", EXTRA_RESULT_DATA="screen_result_data";
    private static final String CHANNEL="tfm_screen_capture", CHANNEL_NAME="TFM screen sharing"; private static final int NOTIFICATION_ID=9061;
    private MediaProjection projection; private VirtualDisplay display; private ImageReader reader; private HandlerThread thread; private Handler handler; private volatile boolean captureRequested=false; private volatile long captureAfter=0;
    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(AppLocale.wrap(base)); }
    @Override public void onCreate(){super.onCreate();createChannel();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(intent==null)return START_NOT_STICKY;String action=intent.getAction();if(ACTION_STOP.equals(action)){stopSelf();return START_NOT_STICKY;}if(ACTION_CAPTURE.equals(action)){if(projection!=null){captureRequested=true;captureAfter=System.currentTimeMillis()+650;}return START_NOT_STICKY;}if(ACTION_START.equals(action)){int code=intent.getIntExtra(EXTRA_RESULT_CODE,0);Intent data=getIntentExtra(intent,EXTRA_RESULT_DATA);if(data==null||code==0){stopSelf();return START_NOT_STICKY;}startForegroundSafe();startProjection(code,data);}return START_NOT_STICKY;}
    private Intent getIntentExtra(Intent i,String key){if(Build.VERSION.SDK_INT>=33)return i.getParcelableExtra(key,Intent.class);return i.getParcelableExtra(key);}
    private void startForegroundSafe(){Notification n=buildNotification();if(Build.VERSION.SDK_INT>=29){try{startForeground(NOTIFICATION_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);}catch(Exception e){startForeground(NOTIFICATION_ID,n);}}else startForeground(NOTIFICATION_ID,n);}
    private void startProjection(int code,Intent data){try{if(projection!=null)return;MediaProjectionManager manager=(MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);projection=manager.getMediaProjection(code,data);if(projection==null){stopSelf();return;}DisplayMetrics dm=new DisplayMetrics();WindowManager wm=(WindowManager)getSystemService(WINDOW_SERVICE);wm.getDefaultDisplay().getRealMetrics(dm);int width=Math.max(1,dm.widthPixels),height=Math.max(1,dm.heightPixels),density=Math.max(1,dm.densityDpi);thread=new HandlerThread("TFMScreenCapture");thread.start();handler=new Handler(thread.getLooper());reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,3);reader.setOnImageAvailableListener(this::onImage,handler);projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){AiSecureStore.setCaptureActive(ScreenCaptureService.this,false);stopSelf();}},handler);display=projection.createVirtualDisplay("TFM authorized screen sharing",width,height,density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,handler);AiSecureStore.setCaptureActive(this,true);}catch(Exception e){AiSecureStore.setCaptureActive(this,false);stopSelf();}}
    private void onImage(ImageReader source){Image image=null;try{image=source.acquireLatestImage();if(image==null)return;if(!captureRequested||System.currentTimeMillis()<captureAfter)return;captureRequested=false;Image.Plane[] planes=image.getPlanes();if(planes==null||planes.length==0)return;ByteBuffer buffer=planes[0].getBuffer();int pixelStride=planes[0].getPixelStride(),rowStride=planes[0].getRowStride(),width=image.getWidth(),height=image.getHeight();int rowPadding=rowStride-pixelStride*width;Bitmap padded=Bitmap.createBitmap(width+Math.max(0,rowPadding/pixelStride),height,Bitmap.Config.ARGB_8888);padded.copyPixelsFromBuffer(buffer);Bitmap bitmap=(rowPadding==0)?padded:Bitmap.createBitmap(padded,0,0,width,height);if(bitmap!=padded)padded.recycle();File file=new File(getFilesDir(),"ai-last-screen.jpg");try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.JPEG,70,out);out.flush();}bitmap.recycle();AiSecureStore.setLastScreenshot(this,file.getAbsolutePath());Intent saved=new Intent(ACTION_SCREENSHOT_SAVED);saved.setPackage(getPackageName());sendBroadcast(saved);}catch(Exception e){captureRequested=false;}finally{if(image!=null)image.close();}}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm.getNotificationChannel(CHANNEL)==null)nm.createNotificationChannel(new NotificationChannel(CHANNEL,CHANNEL_NAME,NotificationManager.IMPORTANCE_LOW));}}
    private Notification buildNotification(){Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);b.setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle(getString(R.string.screen_title)).setContentText(getString(R.string.screen_body)).setOngoing(true).setCategory(Notification.CATEGORY_SERVICE).setOnlyAlertOnce(true);Intent capture=new Intent(this,ScreenCaptureService.class).setAction(ACTION_CAPTURE);int flags=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0);PendingIntent cp=PendingIntent.getService(this,9062,capture,flags);Intent stop=new Intent(this,ScreenCaptureService.class).setAction(ACTION_STOP);PendingIntent sp=PendingIntent.getService(this,9063,stop,flags);b.addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_camera,getString(R.string.attach_screenshot),cp).build());b.addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause,getString(R.string.stop),sp).build());Intent open=new Intent(this,MainActivity.class).putExtra("route","assistant").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);PendingIntent op=PendingIntent.getActivity(this,9064,open,flags);b.setContentIntent(op);return b.build();}
    @Override public void onDestroy(){AiSecureStore.setCaptureActive(this,false);try{if(display!=null)display.release();}catch(Exception ignored){}try{if(reader!=null)reader.close();}catch(Exception ignored){}try{if(projection!=null)projection.stop();}catch(Exception ignored){}try{if(thread!=null)thread.quitSafely();}catch(Exception ignored){}stopForeground(true);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
