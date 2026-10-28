package dev.itszain.termuxfilemanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** Persistent AI Agent settings and memory in Termux home: ~/.TFM-setting/. */
public final class AgentWorkspace {
    public static final String DIR = "~/.TFM-setting";
    public static final String CONFIG = "config.json";
    public static final String MEMORY = "Memory.md";
    public static final String INSTRUCTIONS = "instructions.md";
    // Keep the requested filename for compatibility with the original project/user naming.
    public static final String SYSTEM_PROMPT = "system-propmet.md";
    private AgentWorkspace() {}

    public static String syncCommand(Context context) {
        String config64 = encode(defaultConfig(context).toString());
        String memory64 = encode(defaultMemory());
        String instructions64 = encode(AiSecureStore.settings(context).getString("agent_instructions", ""));
        String system64 = encode(AiSecureStore.settings(context).getString("custom_system_prompt", ""));
        StringBuilder cmd = new StringBuilder("set +e; d=\"$HOME/.TFM-setting\"; mkdir -p \"$d\"; ");
        cmd.append("[ -f \"$d/config.json\" ] || printf '%s' '").append(config64).append("' | base64 -d > \"$d/config.json\"; ");
        cmd.append("[ -f \"$d/Memory.md\" ] || printf '%s' '").append(memory64).append("' | base64 -d > \"$d/Memory.md\"; ");
        cmd.append("[ -f \"$d/instructions.md\" ] || printf '%s' '").append(instructions64).append("' | base64 -d > \"$d/instructions.md\"; ");
        cmd.append("[ -f \"$d/system-propmet.md\" ] || printf '%s' '").append(system64).append("' | base64 -d > \"$d/system-propmet.md\"; ");
        cmd.append("for f in config.json Memory.md instructions.md system-propmet.md; do ")
                .append("printf '__TFM_FILE_%s__\\n' \"$f\"; ")
                .append("head -c 60000 \"$d/$f\" 2>/dev/null | base64 | tr -d '\\n'; printf '\\n'; ")
                .append("done");
        return cmd.toString();
    }

    public static void persistSettings(Context context) {
        writeText(context, "$HOME/.TFM-setting/config.json", defaultConfig(context).toString());
    }

    public static void persistPromptFiles(Context context) {
        SharedPreferences p = AiSecureStore.settings(context);
        String command = "mkdir -p \"$HOME/.TFM-setting\" && printf '%s' '" + encode(p.getString("agent_instructions", ""))
                + "' | base64 -d > \"$HOME/.TFM-setting/instructions.md\" && printf '%s' '"
                + encode(p.getString("custom_system_prompt", ""))
                + "' | base64 -d > \"$HOME/.TFM-setting/system-propmet.md\"";
        runQuietly(context, command, "TFM persist prompt files");
    }

    public static void persistChatOverride(Context context, String chatId, String provider, String terminalSessionId, Boolean autoExecute) {
        if (chatId == null || chatId.trim().isEmpty()) return;
        SharedPreferences p = AiSecureStore.settings(context);
        JSONObject root;
        try { root = new JSONObject(p.getString("agent_workspace_config", "")); }
        catch (Exception e) { root = defaultConfig(context); }
        try {
            JSONObject sessions = root.optJSONObject("sessions");
            if (sessions == null) sessions = new JSONObject();
            JSONObject specific = sessions.optJSONObject(chatId);
            if (specific == null) specific = new JSONObject();
            if (provider != null && AiProvider.byId(provider).id.equals(provider)) specific.put("provider", provider);
            if (terminalSessionId != null) specific.put("terminalSessionId", terminalSessionId);
            if (autoExecute != null) specific.put("autoExecute", autoExecute.booleanValue());
            sessions.put(chatId, specific);
            root.put("sessions", sessions);
            String json = root.toString();
            p.edit().putString("agent_workspace_config", json).apply();
            writeText(context, "$HOME/.TFM-setting/config.json", json);
        } catch (Exception ignored) { }
    }

    public static String appendMemoryCommand(String content) {
        String encoded = encode(content == null ? "" : content.trim());
        return "mkdir -p \"$HOME/.TFM-setting\" && touch \"$HOME/.TFM-setting/Memory.md\""
                + " && printf '\\n## %s\\n' \"$(date '+%Y-%m-%d %H:%M:%S %Z')\" >> \"$HOME/.TFM-setting/Memory.md\""
                + " && printf '%s' '" + encoded + "' | base64 -d >> \"$HOME/.TFM-setting/Memory.md\""
                + " && printf '\\n' >> \"$HOME/.TFM-setting/Memory.md\""
                + " && printf 'Saved durable memory to ~/.TFM-setting/Memory.md\\n'";
    }

    public static void applyRead(Context context, String output) {
        String configText = decodeFile(output, CONFIG);
        String memory = decodeFile(output, MEMORY);
        String instructions = decodeFile(output, INSTRUCTIONS);
        String systemPrompt = decodeFile(output, SYSTEM_PROMPT);
        SharedPreferences p = AiSecureStore.settings(context);
        SharedPreferences.Editor e = p.edit();
        if (memory != null) e.putString("agent_workspace_memory", trim(memory, 24000));
        if (instructions != null) {
            e.putString("agent_workspace_instructions", trim(instructions, 20000));
            e.putString("agent_instructions", trim(instructions, 20000));
        }
        if (systemPrompt != null) {
            e.putString("agent_workspace_system_prompt", trim(systemPrompt, 20000));
            e.putString("custom_system_prompt", trim(systemPrompt, 20000));
        }
        if (configText != null) {
            e.putString("agent_workspace_config", trim(configText, 60000));
            try {
                JSONObject config = new JSONObject(configText);
                String provider = config.optString("defaultProvider", "");
                if (isProvider(provider)) e.putString("provider", provider);
                JSONObject providers = config.optJSONObject("providers");
                if (providers != null) for (AiProvider known : AiProvider.all()) {
                    JSONObject item = providers.optJSONObject(known.id);
                    if (item == null) continue;
                    String model = item.optString("model", "").trim();
                    String endpoint = item.optString("endpoint", "").trim();
                    if (!model.isEmpty()) e.putString("model_" + known.id, model);
                    if (endpoint.startsWith("https://")) e.putString("endpoint_" + known.id, endpoint);
                }
                JSONObject agent = config.optJSONObject("agent");
                if (agent != null) {
                    if (agent.has("autoExecute")) e.putBoolean("agent_auto_execute", agent.optBoolean("autoExecute", false));
                    if (agent.has("autoAnalyzeResults")) e.putBoolean("auto_analyze_results", agent.optBoolean("autoAnalyzeResults", false));
                    if (agent.has("webSearch")) e.putBoolean("agent_web_search_enabled", agent.optBoolean("webSearch", true));
                    if (agent.has("thinkingBudgetTokens")) e.putInt("thinking_budget_tokens", clamp(agent.optInt("thinkingBudgetTokens", 0), 0, 8192));
                    if (agent.has("outputBudgetTokens")) e.putInt("output_budget_tokens", clamp(agent.optInt("outputBudgetTokens", 2048), 128, 8192));
                    String level = agent.optString("thinkingLevel", "medium");
                    if ("minimal".equals(level) || "low".equals(level) || "medium".equals(level) || "high".equals(level)) e.putString("thinking_level", level);
                }
                JSONObject limits = config.optJSONObject("rateLimits");
                if (limits != null) {
                    if (limits.has("dailyMax")) e.putInt("rate_daily_max", clamp(limits.optInt("dailyMax", 40), 1, 10000));
                    if (limits.has("gapSeconds")) e.putInt("rate_gap_seconds", clamp(limits.optInt("gapSeconds", 3), 0, 3600));
                }
                JSONObject tools = config.optJSONObject("tools");
                JSONArray enabledTools = tools == null ? null : tools.optJSONArray("enabled");
                if (enabledTools != null) e.putString("agent_enabled_tools", enabledTools.toString());
                JSONObject skills = config.optJSONObject("skills");
                if (skills != null && skills.has("enabled")) e.putBoolean("agent_skills_enabled", skills.optBoolean("enabled", true));
                e.remove("agent_workspace_config_error");
                e.apply();
                applySessionOverrides(context, config.optJSONObject("sessions"), provider);
            } catch (Exception invalid) {
                e.putString("agent_workspace_config_error", "config.json is invalid JSON: " + invalid.getMessage()).apply();
            }
        } else e.apply();
    }

    private static void applySessionOverrides(Context context, JSONObject sessionSettings, String defaultProvider) {
        if (sessionSettings == null) return;
        JSONObject defaults = sessionSettings.optJSONObject("defaults");
        JSONArray chats = AiSecureStore.loadArray(context, AiSecureStore.CHATS, "items");
        boolean changed = false;
        for (int i = 0; i < chats.length(); i++) {
            JSONObject chat = chats.optJSONObject(i);
            if (chat == null) continue;
            JSONObject specific = sessionSettings.optJSONObject(chat.optString("id", ""));
            try {
                if (isProvider(defaultProvider)) chat.put("provider", defaultProvider);
                if (defaults != null) {
                    String provider = defaults.optString("provider", "");
                    if (isProvider(provider)) chat.put("provider", provider);
                    if (defaults.has("terminalSessionId")) chat.put("sessionId", defaults.optString("terminalSessionId", ""));
                }
                if (specific != null) {
                    String provider = specific.optString("provider", "");
                    if (isProvider(provider)) chat.put("provider", provider);
                    if (specific.has("terminalSessionId")) chat.put("sessionId", specific.optString("terminalSessionId", ""));
                    if (specific.has("autoExecute")) chat.put("agent_auto_execute", specific.optBoolean("autoExecute", false));
                    else chat.remove("agent_auto_execute");
                } else chat.remove("agent_auto_execute");
                changed = true;
            } catch (Exception ignored) { }
        }
        if (changed) AiSecureStore.saveArray(context, AiSecureStore.CHATS, "items", chats);
    }

    private static JSONObject defaultConfig(Context context) {
        SharedPreferences p = AiSecureStore.settings(context);
        JSONObject root = new JSONObject();
        JSONObject providers = new JSONObject(), agent = new JSONObject(), limits = new JSONObject();
        try {
            root.put("version", 1);
            root.put("defaultProvider", AiSecureStore.getActiveProvider(context));
            for (AiProvider provider : AiProvider.all()) providers.put(provider.id, new JSONObject()
                    .put("model", AiSecureStore.getModel(context, provider.id))
                    .put("endpoint", AiSecureStore.getEndpoint(context, provider.id)));
            agent.put("autoExecute", p.getBoolean("agent_auto_execute", false));
            agent.put("autoAnalyzeResults", p.getBoolean("auto_analyze_results", false));
            agent.put("webSearch", p.getBoolean("agent_web_search_enabled", true));
            agent.put("thinkingBudgetTokens", p.getInt("thinking_budget_tokens", 0));
            agent.put("thinkingLevel", p.getString("thinking_level", "medium"));
            agent.put("outputBudgetTokens", p.getInt("output_budget_tokens", 2048));
            limits.put("dailyMax", p.getInt("rate_daily_max", 40));
            limits.put("gapSeconds", p.getInt("rate_gap_seconds", 3));
            root.put("providers", providers).put("agent", agent).put("rateLimits", limits);
            String savedTools = p.getString("agent_enabled_tools", "").trim();
            JSONArray configuredTools = null;
            try { if (!savedTools.isEmpty()) configuredTools = new JSONArray(savedTools); } catch (Exception ignored) { }
            if (configuredTools == null) configuredTools = new JSONArray().put("run_shell").put("web_search").put("save_memory").put("install_skill").put("activate_skill");
            root.put("tools", new JSONObject().put("enabled", configuredTools));
            root.put("skills", new JSONObject().put("enabled", p.getBoolean("agent_skills_enabled", true)));
            root.put("apiKeyStorage", "API keys are intentionally excluded from this file and kept encrypted using Android Keystore.");
            root.put("workspace", new JSONObject().put("directory", "~/.TFM-setting")
                    .put("memory", MEMORY).put("instructions", INSTRUCTIONS).put("systemPrompt", SYSTEM_PROMPT));
            JSONObject cached = null;
            try { cached = new JSONObject(p.getString("agent_workspace_config", "")); } catch (Exception ignored) { }
            JSONObject sessions = cached == null ? null : cached.optJSONObject("sessions");
            if (sessions == null) sessions = new JSONObject();
            JSONObject defaults = sessions.optJSONObject("defaults");
            if (defaults == null) defaults = new JSONObject();
            // Preserve explicitly configured sessions.defaults. Do not override each chat's saved provider/session.
            sessions.put("defaults", defaults);
            root.put("sessions", sessions);
        } catch (Exception ignored) { }
        return root;
    }

    public static boolean isToolEnabled(Context context, String tool) {
        if (tool == null || tool.trim().isEmpty()) return false;
        String raw = AiSecureStore.settings(context).getString("agent_enabled_tools", "").trim();
        if (raw.isEmpty()) return true;
        try {
            JSONArray enabled = new JSONArray(raw);
            for (int i = 0; i < enabled.length(); i++) if (tool.equals(enabled.optString(i))) return true;
            return false;
        } catch (Exception ignored) { return true; }
    }

    private static void writeText(Context context, String path, String value) {
        String command;
        if (path.endsWith("config.json")) command = "mkdir -p \"$HOME/.TFM-setting\" && printf '%s' '" + encode(value) + "' | base64 -d > \"$HOME/.TFM-setting/config.json\"";
        else command = "mkdir -p \"$HOME/.TFM-setting\" && printf '%s' '" + encode(value) + "' | base64 -d > \"" + path + "\"";
        runQuietly(context, command, "TFM persist agent config");
    }
    private static void runQuietly(Context context, String command, String label) {
        try { TermuxCommandRunner.run(context, command, "~/", label, true); } catch (Exception ignored) { }
    }
    private static boolean isProvider(String id) { return id != null && !id.trim().isEmpty() && AiProvider.byId(id).id.equals(id); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static String encode(String s) { return Base64.encodeToString((s == null ? "" : s).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP); }
    private static String decodeFile(String output, String file) {
        if (output == null) return null;
        String marker = "__TFM_FILE_" + file + "__";
        int found = output.indexOf(marker);
        if (found < 0) return null;
        int valueStart = output.indexOf('\n', found);
        if (valueStart < 0) return null;
        valueStart++;
        int valueEnd = output.indexOf('\n', valueStart);
        if (valueEnd < 0) valueEnd = output.length();
        try { String data = output.substring(valueStart, valueEnd).trim(); return data.isEmpty() ? "" : new String(Base64.decode(data, Base64.DEFAULT), StandardCharsets.UTF_8); }
        catch (Exception e) { return null; }
    }
    private static String trim(String value, int max) { if (value == null) return ""; return value.length() <= max ? value : value.substring(value.length() - max); }
    private static String defaultMemory() {
        return "# ذاكرة الوكيل الدائمة\n\n"
                + "احفظ هنا المعلومات الطويلة الأمد اللازمة للمحادثات القادمة: تفضيلات المستخدم، بيئة العمل، المشاريع، القرارات والمهام المفتوحة.\n"
                + "لا تحفظ كلمات المرور أو مفاتيح API أو رموز الدخول أو الأسرار. حدّث المعلومات القديمة بدل تكرارها.\n";
    }
}
