package dev.itszain.termuxfilemanager;

import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Small dependency-free HTTPS client for the ten configured AI APIs. */
public final class AiApiClient {
    private AiApiClient(){}
    public static String send(android.content.Context context,AiProvider provider,String model,String endpoint,String apiKey,JSONArray conversation,String screenshotPath,int maxTokens) throws Exception {
        if(apiKey==null||apiKey.trim().isEmpty())throw new Exception("ماكو API key محفوظ لهذا المزوّد. افتح إعدادات الذكاء الاصطناعي وأضف المفتاح.");
        if(model==null||model.trim().isEmpty())throw new Exception("حدد اسم النموذج من الإعدادات أولًا.");
        if(!AiSecureStore.allowRequest(context,provider.id))throw new Exception("منظّم الاستخدام منع الطلب: بلغت الحد اليومي أو الفاصل الأدنى المحلي. راجع AI Settings أو انتظر قليلًا.");
        return sendRaw(provider,model,endpoint,apiKey,conversation,screenshotPath,maxTokens);
    }
    private static String sendRaw(AiProvider provider,String model,String endpoint,String apiKey,JSONArray history,String screenshotPath,int maxTokens) throws Exception {
        String image=readImageBase64(screenshotPath);
        JSONObject body;
        String url;
        if("gemini".equals(provider.type)){ body=geminiBody(model,history,image,maxTokens); url=endpoint.replace("{model}",model); if(!url.contains("key="))url+=(url.contains("?")?"&":"?")+"key="+URLEncoder.encode(apiKey,"UTF-8"); }
        else if("anthropic".equals(provider.type)){body=anthropicBody(model,history,image,maxTokens);url=endpoint;}
        else if("cohere".equals(provider.type)){body=cohereBody(model,history,image,maxTokens);url=endpoint;}
        else {body=openAiBody(model,history,image,maxTokens);url=endpoint;}
        URL parsed=new URL(url);
        if(!"https".equalsIgnoreCase(parsed.getProtocol()))throw new Exception("لأمان مفتاح API، عنوان المزوّد لازم يستخدم HTTPS.");
        HttpURLConnection conn=(HttpURLConnection)parsed.openConnection(); conn.setRequestMethod("POST"); conn.setConnectTimeout(18000); conn.setReadTimeout(90000); conn.setDoOutput(true); conn.setRequestProperty("Content-Type","application/json; charset=utf-8"); conn.setRequestProperty("Accept","application/json");
        if(!"gemini".equals(provider.type)&&!"anthropic".equals(provider.type))conn.setRequestProperty("Authorization","Bearer "+apiKey);
        if("anthropic".equals(provider.type)){conn.setRequestProperty("x-api-key",apiKey);conn.setRequestProperty("anthropic-version","2023-06-01");}
        if("openrouter".equals(provider.id)){conn.setRequestProperty("HTTP-Referer","https://github.com/its-zain-dev/TermuxFileManger");conn.setRequestProperty("X-Title","Termux File Manager AI Agent");}
        byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8); conn.setFixedLengthStreamingMode(bytes.length);
        try(OutputStream os=conn.getOutputStream()){os.write(bytes);}
        int code=conn.getResponseCode(); String retryHeader=conn.getHeaderField("Retry-After"); InputStream in=code>=200&&code<300?conn.getInputStream():conn.getErrorStream(); String raw=readAll(in); conn.disconnect();
        if(code<200||code>=300){String retry="";try{String r=retryHeader;if(r!=null){try{Long.parseLong(r.trim());retry="\nRetry-After: "+r+" ثانية.";}catch(Exception notSeconds){retry="\nRetry-After: "+r+".";}}}catch(Exception ignored){};String msg=extractError(raw);if(code==429)throw new Exception("HTTP 429: حدّ الاستخدام لدى المزوّد. "+msg+retry);if(code==401||code==403)throw new Exception("HTTP "+code+": رفض المزوّد المفتاح أو صلاحياته. تحقق من API key والرصيد والنموذج. "+msg);throw new Exception("HTTP "+code+": "+msg);}
        JSONObject result=new JSONObject(raw);
        if("gemini".equals(provider.type))return result.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").optJSONObject(0).optString("text",result.toString());
        if("anthropic".equals(provider.type)){JSONArray a=result.optJSONArray("content");StringBuilder sb=new StringBuilder();if(a!=null)for(int i=0;i<a.length();i++){JSONObject part=a.optJSONObject(i);if(part!=null&&"text".equals(part.optString("type")))sb.append(part.optString("text"));}return sb.toString().trim();}
        if("cohere".equals(provider.type)){JSONArray a=result.optJSONObject("message")!=null?result.optJSONObject("message").optJSONArray("content"):null;StringBuilder sb=new StringBuilder();if(a!=null)for(int i=0;i<a.length();i++)sb.append(a.optJSONObject(i)==null?"":a.optJSONObject(i).optString("text",""));return sb.toString().trim();}
        JSONObject first=result.getJSONArray("choices").getJSONObject(0).getJSONObject("message");Object content=first.opt("content"); if(content instanceof String)return (String)content; if(content instanceof JSONArray){StringBuilder sb=new StringBuilder();JSONArray parts=(JSONArray)content;for(int i=0;i<parts.length();i++)sb.append(parts.optJSONObject(i)==null?"":parts.optJSONObject(i).optString("text",""));return sb.toString().trim();}return first.toString();
    }
    public static java.util.List<String> fetchModels(AiProvider provider,String endpoint,String apiKey)throws Exception{
        if(apiKey==null||apiKey.trim().isEmpty())throw new Exception("احفظ مفتاح API أولًا.");
        String url; if("gemini".equals(provider.type)){int at=endpoint.indexOf("/models/");if(at<0)throw new Exception("عنوان Gemini يجب أن يحتوي /models/{model}:generateContent");url=endpoint.substring(0,at)+"/models?key="+URLEncoder.encode(apiKey,"UTF-8");}
        else if("anthropic".equals(provider.type)){url=endpoint.replace("/messages","/models");}
        else if("cohere".equals(provider.type)){url="https://api.cohere.com/v1/models?endpoint=chat";}
        else {url=endpoint.replace("/chat/completions","/models");}
        URL parsed=new URL(url);if(!"https".equalsIgnoreCase(parsed.getProtocol()))throw new Exception("استخدم HTTPS فقط.");HttpURLConnection conn=(HttpURLConnection)parsed.openConnection();conn.setRequestMethod("GET");conn.setConnectTimeout(14000);conn.setReadTimeout(25000);conn.setRequestProperty("Accept","application/json");
        if("gemini".equals(provider.type)){}else if("anthropic".equals(provider.type)){conn.setRequestProperty("x-api-key",apiKey);conn.setRequestProperty("anthropic-version","2023-06-01");}else conn.setRequestProperty("Authorization","Bearer "+apiKey);
        int code=conn.getResponseCode();InputStream in=code>=200&&code<300?conn.getInputStream():conn.getErrorStream();String raw=readAll(in);conn.disconnect();if(code<200||code>=300)throw new Exception("قائمة النماذج غير متاحة (HTTP "+code+"). اكتب model ID يدويًا أو راجع التوثيق. "+extractError(raw));
        JSONObject root=new JSONObject(raw);JSONArray data=root.optJSONArray("data");if(data==null)data=root.optJSONArray("models");if(data==null&&root.optJSONObject("data")!=null)data=root.optJSONObject("data").optJSONArray("models");java.util.LinkedHashSet<String> names=new java.util.LinkedHashSet<>();if(data!=null){for(int i=0;i<data.length()&&names.size()<250;i++){JSONObject m=data.optJSONObject(i);if(m==null)continue;if("gemini".equals(provider.type)){JSONArray methods=m.optJSONArray("supportedGenerationMethods");if(methods!=null){boolean generation=false;for(int j=0;j<methods.length();j++)if("generateContent".equalsIgnoreCase(methods.optString(j))){generation=true;break;}if(!generation)continue;}}String id=m.optString("id",m.optString("name",m.optString("model","")));if(id.startsWith("models/"))id=id.substring("models/".length());if(!id.isEmpty()&& !"null".equals(id))names.add(id);}}if(names.isEmpty())throw new Exception("المزوّد لم يُرجع قائمة نماذج قابلة للاختيار. اكتب معرّف النموذج يدويًا من الكتالوج الرسمي.");return new java.util.ArrayList<>(names);
    }
    private static JSONObject openAiBody(String model,JSONArray history,String image,int maxTokens)throws Exception{
        JSONObject body=new JSONObject().put("model",model).put("max_tokens",Math.max(128,Math.min(8192,maxTokens))).put("temperature",0.35);
        JSONArray messages=new JSONArray();messages.put(new JSONObject().put("role","system").put("content",systemPrompt()));
        for(int i=0;i<history.length();i++){JSONObject m=history.optJSONObject(i);if(m==null)continue;String role=m.optString("role","user"),text=m.optString("text","");JSONObject out=new JSONObject().put("role",role);if(!image.isEmpty()&&i==history.length()-1&&"user".equals(role)){JSONArray content=new JSONArray();content.put(new JSONObject().put("type","text").put("text",text));content.put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url","data:image/jpeg;base64,"+image).put("detail","high")));out.put("content",content);}else out.put("content",text);messages.put(out);}
        return body.put("messages",messages);
    }
    private static JSONObject anthropicBody(String model,JSONArray history,String image,int maxTokens)throws Exception{
        JSONObject body=new JSONObject().put("model",model).put("max_tokens",Math.max(128,Math.min(8192,maxTokens))).put("system",systemPrompt());JSONArray messages=new JSONArray();
        for(int i=0;i<history.length();i++){JSONObject m=history.optJSONObject(i);if(m==null)continue;String role=m.optString("role","user"),text=m.optString("text","");JSONObject out=new JSONObject().put("role","assistant".equals(role)?"assistant":"user");if(!image.isEmpty()&&i==history.length()-1&&"user".equals(role)){JSONArray c=new JSONArray();c.put(new JSONObject().put("type","text").put("text",text));c.put(new JSONObject().put("type","image").put("source",new JSONObject().put("type","base64").put("media_type","image/jpeg").put("data",image)));out.put("content",c);}else out.put("content",text);messages.put(out);}return body.put("messages",messages);
    }
    private static JSONObject cohereBody(String model,JSONArray history,String image,int maxTokens)throws Exception{
        JSONObject body=new JSONObject().put("model",model).put("max_tokens",Math.max(128,Math.min(8192,maxTokens)));JSONArray messages=new JSONArray();messages.put(new JSONObject().put("role","system").put("content",systemPrompt()));
        for(int i=0;i<history.length();i++){JSONObject m=history.optJSONObject(i);if(m==null)continue;String role=m.optString("role","user");JSONObject out=new JSONObject().put("role","assistant".equals(role)?"assistant":"user");String text=m.optString("text","");if(!image.isEmpty()&&i==history.length()-1&&"user".equals(role)){JSONArray c=new JSONArray();c.put(new JSONObject().put("type","text").put("text",text));c.put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url","data:image/jpeg;base64,"+image)));out.put("content",c);}else out.put("content",text);messages.put(out);}return body.put("messages",messages);
    }
    private static JSONObject geminiBody(String model,JSONArray history,String image,int maxTokens)throws Exception{
        JSONObject body=new JSONObject().put("systemInstruction",new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("text",systemPrompt()))));JSONArray contents=new JSONArray();
        for(int i=0;i<history.length();i++){JSONObject m=history.optJSONObject(i);if(m==null)continue;String role="assistant".equals(m.optString("role"))?"model":"user";JSONArray parts=new JSONArray().put(new JSONObject().put("text",m.optString("text","")));if(!image.isEmpty()&&i==history.length()-1&&"user".equals(role))parts.put(new JSONObject().put("inline_data",new JSONObject().put("mime_type","image/jpeg").put("data",image)));contents.put(new JSONObject().put("role",role).put("parts",parts));}
        body.put("contents",contents).put("generationConfig",new JSONObject().put("maxOutputTokens",Math.max(128,Math.min(8192,maxTokens))).put("temperature",0.35));return body;
    }
    private static String systemPrompt(){return "أنت مساعد وكيل برمجي عملي يعمل مع Termux على أندرويد. حلّل أخطاء الأوامر والمخرجات بدقة، وميّز بين الحقائق والافتراضات. عندما تقترح أوامر ضع كل مجموعة قابلة للتنفيذ داخل code fence مع لغة bash. لا تدّعي أنك نفذت أمرًا أو رأيت شاشة لم تُرفق. لا تطلب أسرارًا أو مفاتيح API ضمن الدردشة. قبل الحذف أو الترقية أو أي عملية مؤثرة، اشرح أثرها بوضوح. إذا احتجت رؤية لقطة حديثة من شاشة الهاتف أو مخرجات TUI، أضف الرمز [TFM_SCREENSHOT] مرة واحدة إلى ردك؛ التطبيق لن يلتقط شيئًا إلا إذا كان المستخدم قد فعّل مشاركة الشاشة من نافذة أندرويد مسبقًا. لا تطلب لقطات متكررة في نفس الدور. لا تنفذ الأدوات بنفسك؛ التطبيق يطلب موافقة المستخدم قبل تشغيل الأوامر.";}
    private static String readImageBase64(String path)throws Exception{if(path==null||path.trim().isEmpty())return "";File f=new File(path);if(!f.isFile()||f.length()>5*1024*1024)return "";byte[] b;try(java.io.FileInputStream in=new java.io.FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);b=out.toByteArray();}return Base64.encodeToString(b,Base64.NO_WRAP);}
    private static String readAll(InputStream in)throws Exception{if(in==null)return "";try(InputStream input=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=input.read(b))>0){out.write(b,0,n);if(out.size()>3*1024*1024)break;}return out.toString("UTF-8");}}
    private static String extractError(String raw){try{JSONObject o=new JSONObject(raw);if(o.has("error")){Object e=o.opt("error");if(e instanceof JSONObject)return ((JSONObject)e).optString("message",e.toString());return String.valueOf(e);}return raw.length()>700?raw.substring(0,700):raw;}catch(Exception e){return raw.length()>700?raw.substring(0,700):raw;}}
}
