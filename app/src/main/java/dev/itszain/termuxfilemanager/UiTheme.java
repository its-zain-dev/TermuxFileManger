package dev.itszain.termuxfilemanager;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;

/** Shared palette persisted by SettingsActivity. */
public final class UiTheme {
    public final int bg, panel, panel2, text, muted, accent, danger, divider;
    private UiTheme(int bg, int panel, int panel2, int text, int muted, int accent, int danger, int divider) {
        this.bg=bg; this.panel=panel; this.panel2=panel2; this.text=text; this.muted=muted; this.accent=accent; this.danger=danger; this.divider=divider;
    }
    public static String getName(Context c) { return prefs(c).getString("theme", "midnight"); }
    public static void setName(Context c, String value) { prefs(c).edit().putString("theme", value).apply(); }
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("tfm_settings", Context.MODE_PRIVATE); }
    public static UiTheme get(Context c) {
        String t=getName(c);
        if ("light".equals(t)) return new UiTheme(Color.rgb(246,248,252),Color.WHITE,Color.rgb(230,236,245),Color.rgb(27,36,54),Color.rgb(91,105,128),Color.rgb(0,126,112),Color.rgb(190,45,65),Color.rgb(213,221,232));
        if ("amoled".equals(t)) return new UiTheme(Color.BLACK,Color.rgb(10,10,12),Color.rgb(24,24,27),Color.rgb(245,247,255),Color.rgb(143,151,170),Color.rgb(105,231,196),Color.rgb(255,105,125),Color.rgb(41,43,50));
        return new UiTheme(Color.rgb(11,16,32),Color.rgb(20,28,48),Color.rgb(28,38,62),Color.rgb(239,244,255),Color.rgb(153,168,195),Color.rgb(116,224,196),Color.rgb(255,112,129),Color.rgb(42,53,77));
    }
    public static void applyWindow(Activity a) {
        UiTheme t=get(a); a.getWindow().setStatusBarColor(t.bg); a.getWindow().setNavigationBarColor(t.bg);
        View decor=a.getWindow().getDecorView(); int flags=decor.getSystemUiVisibility();
        int lightFlags=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        if("light".equals(getName(a))) flags|=lightFlags; else flags&=~lightFlags;
        decor.setSystemUiVisibility(flags);
    }
}
