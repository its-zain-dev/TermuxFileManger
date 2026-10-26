package dev.itszain.termuxfilemanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores provider API keys encrypted with a non-exportable Android Keystore AES-GCM key. */
public final class AiSecureStore {
    public static final String PREF="tfm_ai_secure", SETTINGS="tfm_ai_settings", CHATS="tfm_ai_chats", SESSIONS="tfm_terminal_sessions";
    private static final String ALIAS="tfm.ai.keys.aesgcm.v1";
    private AiSecureStore() {}
    private static SharedPreferences p(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    private static SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        java.security.Key existing=ks.getKey(ALIAS,null); if(existing instanceof SecretKey) return (SecretKey)existing;
        KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());
        return kg.generateKey();
    }
    public static void saveKey(Context c,String provider,String value) throws Exception {
        if(value==null||value.trim().isEmpty()){p(c).edit().remove("key_"+provider).apply();return;}
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(value.trim().getBytes(StandardCharsets.UTF_8));
        String packed=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP);
        p(c).edit().putString("key_"+provider,packed).apply();
    }
    public static String getKey(Context c,String provider) throws Exception {
        String packed=p(c).getString("key_"+provider,""); if(packed.isEmpty()) return "";
        String[] parts=packed.split(":",2); if(parts.length!=2) throw new Exception("صيغة المفتاح المشفر غير صالحة؛ أعد حفظ المفتاح من الإعدادات.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    public static SharedPreferences settings(Context c){return c.getSharedPreferences(SETTINGS,Context.MODE_PRIVATE);}
    public static String getModel(Context c,String id){return settings(c).getString("model_"+id,AiProvider.byId(id).defaultModel);}
    public static String getEndpoint(Context c,String id){return settings(c).getString("endpoint_"+id,AiProvider.byId(id).endpoint);}
    public static String getActiveProvider(Context c){return settings(c).getString("provider","gemini");}
    public static void saveProvider(Context c,String id,String model,String endpoint){settings(c).edit().putString("provider",id).putString("model_"+id,model.trim()).putString("endpoint_"+id,endpoint.trim()).apply();}
    public static JSONArray loadArray(Context c,String pref,String key){try{return new JSONArray(c.getSharedPreferences(pref,Context.MODE_PRIVATE).getString(key,"[]"));}catch(Exception e){return new JSONArray();}}
    public static void saveArray(Context c,String pref,String key,JSONArray value){c.getSharedPreferences(pref,Context.MODE_PRIVATE).edit().putString(key,value.toString()).apply();}
    public static JSONObject findById(JSONArray arr,String id){for(int i=0;i<arr.length();i++){JSONObject o=arr.optJSONObject(i);if(o!=null&&id.equals(o.optString("id")))return o;}return null;}
    public static JSONArray cloneArray(JSONArray src){JSONArray out=new JSONArray();if(src==null)return out;for(int i=0;i<src.length();i++)out.put(src.optJSONObject(i));return out;}
    public static String lastScreenshot(Context c){return settings(c).getString("last_screenshot","");}
    public static void setLastScreenshot(Context c,String path){settings(c).edit().putString("last_screenshot",path).putLong("last_screenshot_at",System.currentTimeMillis()).apply();}
    public static long lastScreenshotAt(Context c){return settings(c).getLong("last_screenshot_at",0);}
    public static long lastScreenshotUsedAt(Context c){return settings(c).getLong("last_screenshot_used_at",0);}
    public static void markLastScreenshotUsed(Context c){settings(c).edit().putLong("last_screenshot_used_at",lastScreenshotAt(c)).apply();}
    public static void clearLastScreenshot(Context c){String path=lastScreenshot(c);if(path!=null&&!path.isEmpty())try{new java.io.File(path).delete();}catch(Exception ignored){}settings(c).edit().remove("last_screenshot").remove("last_screenshot_at").remove("last_screenshot_used_at").apply();}
    public static void savePendingScreenshot(Context c,String chatId,String provider,String model,String endpoint,long since){settings(c).edit().putBoolean("pending_ai_screenshot",true).putString("pending_screen_chat",chatId).putString("pending_screen_provider",provider).putString("pending_screen_model",model).putString("pending_screen_endpoint",endpoint).putLong("pending_screen_since",since).apply();}
    public static boolean hasPendingScreenshot(Context c){return settings(c).getBoolean("pending_ai_screenshot",false);}
    public static String pendingScreenshotValue(Context c,String key){return settings(c).getString("pending_screen_"+key,"");}
    public static long pendingScreenshotSince(Context c){return settings(c).getLong("pending_screen_since",0);}
    public static void clearPendingScreenshot(Context c){settings(c).edit().remove("pending_ai_screenshot").remove("pending_screen_chat").remove("pending_screen_provider").remove("pending_screen_model").remove("pending_screen_endpoint").remove("pending_screen_since").apply();}
    public static void setCaptureActive(Context c,boolean active){settings(c).edit().putBoolean("capture_active",active).apply();}
    public static boolean isCaptureActive(Context c){return settings(c).getBoolean("capture_active",false);}
    public static boolean allowRequest(Context c,String provider) {
        synchronized(AiSecureStore.class){
            SharedPreferences s=settings(c); long now=System.currentTimeMillis(); String day=java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT,java.util.Locale.US).format(new java.util.Date(now));
            String saved=s.getString("rate_day_"+provider,""); int count=s.getInt("rate_count_"+provider,0); if(!day.equals(saved)) count=0;
            int max=Math.max(1,s.getInt("rate_daily_max",40)); long last=s.getLong("rate_last_"+provider,0); int gap=Math.max(0,s.getInt("rate_gap_seconds",3));
            if(count>=max)return false; if(now-last<gap*1000L)return false;
            s.edit().putString("rate_day_"+provider,day).putInt("rate_count_"+provider,count+1).putLong("rate_last_"+provider,now).apply(); return true;
        }
    }
    public static int remainingRequests(Context c,String provider){SharedPreferences s=settings(c);long now=System.currentTimeMillis();String day=java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT,java.util.Locale.US).format(new java.util.Date(now));if(!day.equals(s.getString("rate_day_"+provider,"")))return Math.max(1,s.getInt("rate_daily_max",40));return Math.max(0,s.getInt("rate_daily_max",40)-s.getInt("rate_count_"+provider,0));}
    public static String safeJsonString(JSONObject o,String key,String fallback){return o==null?fallback:o.optString(key,fallback);}
}
