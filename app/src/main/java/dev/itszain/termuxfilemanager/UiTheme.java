package dev.itszain.termuxfilemanager;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;

/** Design tokens for every TFM screen. The theme name is persisted by the settings screen. */
public final class UiTheme {
    public final String name;
    public final boolean light;
    public final int bg, panel, panel2, text, muted, accent, accent2, onAccent, danger, divider;
    public final int glass, glass2, glassStroke;
    public final int aurora1, aurora2, aurora3, auroraAlpha;

    private UiTheme(String name, boolean light, int bg, int panel, int panel2, int text, int muted,
                    int accent, int accent2, int onAccent, int danger, int divider,
                    int glass, int glass2, int glassStroke,
                    int aurora1, int aurora2, int aurora3, int auroraAlpha) {
        this.name = name; this.light = light;
        this.bg = bg; this.panel = panel; this.panel2 = panel2; this.text = text; this.muted = muted;
        this.accent = accent; this.accent2 = accent2; this.onAccent = onAccent; this.danger = danger; this.divider = divider;
        this.glass = glass; this.glass2 = glass2; this.glassStroke = glassStroke;
        this.aurora1 = aurora1; this.aurora2 = aurora2; this.aurora3 = aurora3; this.auroraAlpha = auroraAlpha;
    }

    public static String getName(Context c) { return prefs(c).getString("theme", "midnight"); }
    public static void setName(Context c, String value) { prefs(c).edit().putString("theme", value).apply(); }
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("tfm_settings", Context.MODE_PRIVATE); }

    public static boolean isLight(Context c) { return "light".equals(getName(c)); }

    public static UiTheme get(Context c) {
        String t = getName(c);
        if ("light".equals(t)) {
            return new UiTheme("light", true,
                    Color.rgb(243, 246, 252), Color.WHITE, Color.rgb(231, 237, 247),
                    Color.rgb(22, 31, 50), Color.rgb(94, 107, 130),
                    Color.rgb(0, 150, 128), Color.rgb(79, 107, 255), Color.WHITE,
                    Color.rgb(200, 40, 70), Color.rgb(214, 222, 234),
                    Color.argb(200, 255, 255, 255), Color.argb(240, 255, 255, 255), Color.argb(38, 30, 50, 110),
                    Color.rgb(80, 220, 190), Color.rgb(150, 130, 255), Color.rgb(110, 170, 255), 96);
        }
        if ("amoled".equals(t)) {
            return new UiTheme("amoled", false,
                    Color.BLACK, Color.rgb(11, 11, 14), Color.rgb(24, 24, 30),
                    Color.rgb(245, 247, 255), Color.rgb(140, 148, 168),
                    Color.rgb(105, 240, 200), Color.rgb(170, 140, 255), Color.rgb(0, 14, 11),
                    Color.rgb(255, 105, 125), Color.rgb(36, 38, 46),
                    Color.argb(20, 255, 255, 255), Color.argb(34, 255, 255, 255), Color.argb(40, 255, 255, 255),
                    Color.rgb(36, 214, 181), Color.rgb(112, 92, 255), Color.rgb(33, 129, 255), 58);
        }
        if ("nebula".equals(t)) {
            return new UiTheme("nebula", false,
                    Color.rgb(14, 8, 30), Color.rgb(30, 19, 56), Color.rgb(44, 29, 80),
                    Color.rgb(246, 238, 255), Color.rgb(173, 152, 206),
                    Color.rgb(255, 124, 200), Color.rgb(120, 200, 255), Color.rgb(34, 6, 32),
                    Color.rgb(255, 110, 110), Color.rgb(60, 42, 98),
                    Color.argb(28, 255, 255, 255), Color.argb(44, 255, 255, 255), Color.argb(52, 255, 255, 255),
                    Color.rgb(255, 84, 190), Color.rgb(110, 80, 255), Color.rgb(40, 170, 255), 110);
        }
        return new UiTheme("midnight", false,
                Color.rgb(7, 11, 24), Color.rgb(17, 25, 46), Color.rgb(27, 38, 68),
                Color.rgb(236, 242, 255), Color.rgb(142, 157, 188),
                Color.rgb(98, 230, 200), Color.rgb(129, 140, 255), Color.rgb(5, 16, 30),
                Color.rgb(255, 107, 129), Color.rgb(38, 50, 80),
                Color.argb(26, 255, 255, 255), Color.argb(42, 255, 255, 255), Color.argb(48, 255, 255, 255),
                Color.rgb(36, 214, 181), Color.rgb(112, 92, 255), Color.rgb(33, 129, 255), 100);
    }

    public static void applyWindow(Activity a) {
        UiTheme t = get(a);
        a.getWindow().setStatusBarColor(t.bg);
        a.getWindow().setNavigationBarColor(t.bg);
        View decor = a.getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        int lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        if (t.light) flags |= lightFlags; else flags &= ~lightFlags;
        decor.setSystemUiVisibility(flags);
        a.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(t.bg));
    }
}
