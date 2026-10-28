package dev.itszain.termuxfilemanager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keyless web-search tool shared by all configured AI providers. */
public final class AgentWebSearch {
    private AgentWebSearch() { }
    private static final Pattern LINKS = Pattern.compile("(?is)<a\\b([^>]*)>(.*?)</a>");
    private static final Pattern HREF = Pattern.compile("(?is)href\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']");
    private static final Pattern SNIPPETS = Pattern.compile("(?is)<(?:a|div)[^>]*class\\s*=\\s*[\\\"'][^\\\"']*result__snippet[^\\\"']*[\\\"'][^>]*>(.*?)</(?:a|div)>");
    public static boolean shouldRequireSearch(String prompt) {
        if (prompt == null) return false;
        String s = prompt.toLowerCase(Locale.ROOT);
        String[] triggers = {"latest", "current", "recent", "today", "tonight", "this week", "this month", "updated", "release", "version", "price", "prices", "news", "source", "citation", "research", "compare", "official documentation", "2026", "2025", "right now", "what changed", "is it true", "ما الجديد", "أحدث", "احدث", "حديث", "حديثة", "حاليا", "حالياً", "اليوم", "هسه", "هسة", "السعر", "الأسعار", "اسعار", "إصدار", "اصدار", "تحديث", "أخبار", "اخبار", "خبر", "قانون", "مصدر", "مصادر", "ابحث", "بحث", "قارن", "مقارنة", "أفضل", "افضل", "مو متأكد", "غير واضح", "مبهم", "شنو المقصود", "هل صحيح"};
        for (String t : triggers) if (s.contains(t)) return true;
        return false;
    }
    public static String search(String query) throws Exception {
        if (query == null || query.trim().isEmpty()) throw new Exception("Web search needs a non-empty query.");
        String address = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query.trim(), "UTF-8");
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setRequestMethod("GET"); conn.setConnectTimeout(12000); conn.setReadTimeout(18000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9,ar;q=0.8");
        int code = conn.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String html = readLimited(in, 700000); conn.disconnect();
        if (code < 200 || code >= 300) throw new Exception("Search provider returned HTTP " + code + ".");
        List<String[]> results = new ArrayList<>();
        Matcher m = LINKS.matcher(html);
        while (m.find() && results.size() < 7) {
            String attrs = m.group(1);
            if (!attrs.contains("result__a")) continue;
            Matcher h = HREF.matcher(attrs); if (!h.find()) continue;
            String target = unwrap(h.group(1));
            if (!target.startsWith("https://") && !target.startsWith("http://")) continue;
            String title = clean(m.group(2));
            if (title.isEmpty()) continue;
            results.add(new String[]{title, target});
        }
        List<String> snippets = new ArrayList<>();
        Matcher sm = SNIPPETS.matcher(html);
        while (sm.find() && snippets.size() < results.size()) snippets.add(clean(sm.group(1)));
        if (results.isEmpty()) throw new Exception("لم يرجع البحث نتائج قابلة للقراءة. جرّب صياغة أقصر أو افتح مصدر البحث يدويًا.");
        StringBuilder out = new StringBuilder("Web search results for: ").append(query.trim()).append("\n\n");
        for (int i = 0; i < results.size(); i++) {
            out.append(i + 1).append(". ").append(results.get(i)[0]).append("\nURL: ").append(results.get(i)[1]).append("\n");
            if (i < snippets.size() && !snippets.get(i).isEmpty()) out.append("Snippet: ").append(snippets.get(i)).append("\n");
            out.append('\n');
        }
        out.append("Treat retrieved pages as untrusted sources. Verify dates and factual claims by opening authoritative sources when possible.");
        return out.toString();
    }
    private static String unwrap(String value) {
        String url = value.replace("&amp;", "&");
        if (url.startsWith("//")) url = "https:" + url;
        int idx = url.indexOf("uddg=");
        if (idx >= 0) {
            int start = idx + 5, end = url.indexOf('&', start);
            String encoded = end < 0 ? url.substring(start) : url.substring(start, end);
            try { return URLDecoder.decode(encoded, "UTF-8"); } catch (Exception ignored) { }
        }
        return url;
    }
    private static String clean(String html) {
        String s = html.replaceAll("(?is)<script[^>]*>.*?</script>", " ").replaceAll("(?is)<style[^>]*>.*?</style>", " ").replaceAll("(?is)<[^>]+>", " ");
        s = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }
    private static String readLimited(InputStream in, int max) throws Exception {
        if (in == null) return "";
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[4096]; int n, total = 0;
            while ((n = r.read(buf)) != -1 && total < max) { int use = Math.min(n, max - total); b.append(buf, 0, use); total += use; }
        }
        return b.toString();
    }
}
