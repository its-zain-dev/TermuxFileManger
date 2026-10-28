package dev.itszain.termuxfilemanager;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * TFM design system: glass surfaces, gradient accents, icons, motion and a floating dock.
 * Every screen builds its UI through these helpers so the app stays visually consistent.
 */
public final class Ui {
    private Ui() { }

    // ───────────────────────── basics ─────────────────────────
    public static int dp(Context c, float v) { return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f); }
    public static float dpf(Context c, float v) { return v * c.getResources().getDisplayMetrics().density; }
    public static int alpha(int color, int a) { return (Math.max(0, Math.min(255, a)) << 24) | (color & 0x00FFFFFF); }

    public static int mix(int a, int b, float t) {
        float u = 1f - t;
        return Color.argb(Math.round(Color.alpha(a) * u + Color.alpha(b) * t), Math.round(Color.red(a) * u + Color.red(b) * t),
                Math.round(Color.green(a) * u + Color.green(b) * t), Math.round(Color.blue(a) * u + Color.blue(b) * t));
    }

    /** Composite a translucent colour over an opaque base. */
    public static int over(int base, int top) {
        float f = Color.alpha(top) / 255f;
        return Color.rgb(Math.round(Color.red(top) * f + Color.red(base) * (1 - f)),
                Math.round(Color.green(top) * f + Color.green(base) * (1 - f)),
                Math.round(Color.blue(top) * f + Color.blue(base) * (1 - f)));
    }

    public static Typeface medium() { return Typeface.create("sans-serif-medium", Typeface.NORMAL); }
    public static Typeface semibold() { return Typeface.create("sans-serif-medium", Typeface.BOLD); }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(bold ? semibold() : Typeface.create("sans-serif", Typeface.NORMAL));
        return t;
    }

    // ───────────────────────── surfaces ─────────────────────────
    public static GradientDrawable shape(int color, float radiusPx, int strokeColor, int strokePx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        if (strokePx > 0 && strokeColor != Color.TRANSPARENT) d.setStroke(strokePx, strokeColor);
        return d;
    }

    public static GradientDrawable gradient(int c1, int c2, float radiusPx) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{c1, c2});
        d.setCornerRadius(radiusPx);
        return d;
    }

    /** Maps legacy palette colours onto glass surfaces, so old helpers pick up the new look automatically. */
    public static Drawable surface(Context c, UiTheme t, int color, float radiusDp, int strokeColor) {
        float r = dpf(c, radiusDp * 1.3f);
        int hair = Math.max(1, dp(c, 1f));
        int st = strokeColor == t.accent ? alpha(t.accent, 150) : t.glassStroke;
        if (color == t.panel) return shape(t.glass, r, st, hair);
        if (color == t.panel2) return shape(t.glass2, r, st, hair);
        if (color == t.bg) return shape(t.light ? alpha(Color.BLACK, 12) : alpha(Color.BLACK, 96), r, t.glassStroke, hair);
        if (strokeColor == Color.TRANSPARENT) return shape(color, r, 0, 0);
        return shape(color, r, strokeColor, hair);
    }

    public static Drawable buttonBg(Context c, UiTheme t, int bg, float radiusDp) {
        float r = dpf(c, radiusDp);
        int hair = Math.max(1, dp(c, 1f));
        Drawable content;
        int ripple;
        if (bg == t.accent) { content = gradient(t.accent, t.accent2, r); ripple = alpha(Color.WHITE, 90); }
        else if (bg == t.panel || bg == t.panel2) { content = shape(t.glass2, r, t.glassStroke, hair); ripple = alpha(t.accent, 70); }
        else if (bg == t.bg) { content = shape(t.light ? alpha(Color.BLACK, 10) : alpha(Color.BLACK, 90), r, t.glassStroke, hair); ripple = alpha(t.accent, 70); }
        else { content = shape(bg, r, 0, 0); ripple = alpha(Color.WHITE, 80); }
        return new RippleDrawable(ColorStateList.valueOf(ripple), content, shape(Color.WHITE, r, 0, 0));
    }

    public static Button button(Context c, UiTheme t, String label, int bg, int fg) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(medium());
        b.setMinHeight(0); b.setMinimumHeight(0); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setStateListAnimator(null);
        b.setElevation(0f);
        b.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6));
        b.setBackground(buttonBg(c, t, bg, 26));
        b.setTextColor(fg);
        press(b);
        return b;
    }

    public static ImageView iconButton(Context c, UiTheme t, int icon, boolean filled) {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = dp(c, 11);
        v.setPadding(pad, pad, pad, pad);
        v.setImageDrawable(Icons.get(c, icon, filled ? t.onAccent : t.text));
        v.setBackground(buttonBg(c, t, filled ? t.accent : t.panel2, 30));
        v.setClickable(true);
        v.setFocusable(true);
        press(v);
        return v;
    }

    public static TextView chip(Context c, UiTheme t, String label, int icon, boolean active) {
        TextView v = new TextView(c);
        v.setText(label);
        v.setTextSize(12);
        v.setTypeface(medium());
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setSingleLine(true);
        v.setPadding(dp(c, 12), dp(c, 8), dp(c, 14), dp(c, 8));
        applyChip(c, t, v, icon, active);
        v.setClickable(true);
        v.setFocusable(true);
        press(v);
        return v;
    }

    public static void applyChip(Context c, UiTheme t, TextView v, int icon, boolean active) {
        int hair = Math.max(1, dp(c, 1f));
        float r = dpf(c, 22);
        Drawable content = active ? shape(alpha(t.accent, 40), r, alpha(t.accent, 160), hair) : shape(t.glass, r, t.glassStroke, hair);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(t.accent, 70)), content, shape(Color.WHITE, r, 0, 0)));
        v.setTextColor(active ? t.accent : t.text);
        if (icon != 0) {
            Drawable d = Icons.get(c, icon, active ? t.accent : t.muted);
            d.setBounds(0, 0, dp(c, 16), dp(c, 16));
            v.setCompoundDrawablesRelative(d, null, null, null);
            v.setCompoundDrawablePadding(dp(c, 7));
        }
    }

    public static LinearLayout card(Context c, UiTheme t) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 14), dp(c, 14), dp(c, 14), dp(c, 14));
        l.setBackground(surface(c, t, t.panel, 16, 0));
        return l;
    }

    public static void styleField(Context c, UiTheme t, EditText e) {
        float r = dpf(c, 16);
        int hair = Math.max(1, dp(c, 1f));
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, shape(alpha(t.accent, 26), r, t.accent, Math.max(2, dp(c, 1.5f))));
        sl.addState(new int[]{}, shape(t.light ? alpha(Color.BLACK, 10) : alpha(Color.BLACK, 90), r, t.glassStroke, hair));
        sl.setEnterFadeDuration(160);
        sl.setExitFadeDuration(200);
        e.setBackground(sl);
        e.setTextColor(t.text);
        e.setHintTextColor(t.muted);
        e.setHighlightColor(alpha(t.accent, 80));
    }

    public static LinearLayout sectionTitle(Context c, UiTheme t, String title, int icon) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(c, 2), dp(c, 18), dp(c, 2), dp(c, 8));
        if (icon != 0) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            int p = dp(c, 6);
            iv.setPadding(p, p, p, p);
            iv.setImageDrawable(Icons.get(c, icon, t.accent));
            iv.setBackground(shape(alpha(t.accent, 34), dpf(c, 10), 0, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 30), dp(c, 30));
            lp.setMarginEnd(dp(c, 10));
            row.addView(iv, lp);
        }
        TextView tv = text(c, title, 15, t.text, true);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    public static Switch toggle(Context c) {
        UiTheme t = UiTheme.get(c);
        Switch s = new Switch(c);
        s.setTextColor(t.text);
        s.setTextSize(13);
        s.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        s.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{t.accent, mix(t.muted, t.bg, 0.15f)}));
        s.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{alpha(t.accent, 120), alpha(t.muted, 80)}));
        s.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10));
        s.setBackground(surface(c, t, t.panel, 14, 0));
        return s;
    }

    // ───────────────────────── motion ─────────────────────────
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    public static void press(final View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).setStartDelay(0).setInterpolator(new DecelerateInterpolator()).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(260).setStartDelay(0).setInterpolator(new OvershootInterpolator(2.4f)).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private static void cancelAnim(View v) {
        Object old = v.getTag(R.id.tfm_anim);
        if (old instanceof Animator) ((Animator) old).cancel();
        v.setTag(R.id.tfm_anim, null);
    }

    /** Fade + rise + settle. {@code index} staggers siblings. */
    public static void enter(View v, int index) {
        cancelAnim(v);
        float rise = dpf(v.getContext(), 18);
        v.setAlpha(0f);
        v.setTranslationY(rise);
        v.setScaleX(0.96f);
        v.setScaleY(0.96f);
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, rise, 0f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.96f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.96f, 1f));
        a.setStartDelay(Math.min(Math.max(index, 0), 14) * 45L);
        a.setDuration(440);
        a.setInterpolator(new DecelerateInterpolator(1.8f));
        v.setTag(R.id.tfm_anim, a);
        a.start();
    }

    /** Snap a view to its resting state (used for recycled list rows). */
    public static void settle(View v) {
        cancelAnim(v);
        v.setAlpha(1f);
        v.setTranslationX(0f);
        v.setTranslationY(0f);
        v.setScaleX(1f);
        v.setScaleY(1f);
    }

    /** Slide + fade a view in from the side (chat bubbles). */
    public static void enterFrom(View v, int dirDp, int index) {
        cancelAnim(v);
        float dx = dpf(v.getContext(), dirDp);
        v.setAlpha(0f);
        v.setTranslationX(dx);
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, dx, 0f));
        a.setStartDelay(Math.min(Math.max(index, 0), 10) * 35L);
        a.setDuration(360);
        a.setInterpolator(new DecelerateInterpolator(1.6f));
        v.setTag(R.id.tfm_anim, a);
        a.start();
    }

    public static void enterChildren(ViewGroup g, int startIndex) {
        for (int i = 0; i < g.getChildCount(); i++) enter(g.getChildAt(i), startIndex + i);
    }

    /** Animated expand / collapse for a child of a vertical layout. */
    public static void reveal(final View v, boolean show) {
        final ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp == null || !(v.getParent() instanceof View)) { v.setVisibility(show ? View.VISIBLE : View.GONE); return; }
        cancelAnim(v);
        if (show) {
            if (v.getVisibility() == View.VISIBLE && lp.height != 0) return;
            int pw = ((View) v.getParent()).getWidth();
            v.measure(View.MeasureSpec.makeMeasureSpec(pw > 0 ? pw : 1000, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            final int target = Math.max(1, v.getMeasuredHeight());
            lp.height = 0;
            v.setAlpha(0f);
            v.setVisibility(View.VISIBLE);
            ValueAnimator va = ValueAnimator.ofInt(0, target);
            va.addUpdateListener(a -> {
                lp.height = (int) a.getAnimatedValue();
                v.setLayoutParams(lp);
                v.setAlpha(a.getAnimatedFraction());
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    v.setLayoutParams(lp);
                    v.setAlpha(1f);
                }
            });
            va.setDuration(260);
            va.setInterpolator(new DecelerateInterpolator(1.5f));
            v.setTag(R.id.tfm_anim, va);
            va.start();
        } else {
            if (v.getVisibility() != View.VISIBLE) return;
            final int start = Math.max(1, v.getHeight());
            ValueAnimator va = ValueAnimator.ofInt(start, 0);
            va.addUpdateListener(a -> {
                lp.height = (int) a.getAnimatedValue();
                v.setLayoutParams(lp);
                v.setAlpha(1f - a.getAnimatedFraction());
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    v.setVisibility(View.GONE);
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    v.setLayoutParams(lp);
                    v.setAlpha(1f);
                }
            });
            va.setDuration(200);
            va.setInterpolator(new AccelerateDecelerateInterpolator());
            v.setTag(R.id.tfm_anim, va);
            va.start();
        }
    }

    /** Endless breathing glow for status dots and halos. */
    public static void pulse(final View v, float to, long duration) {
        final ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, to),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, to),
                PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.45f));
        a.setDuration(duration);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.setInterpolator(new AccelerateDecelerateInterpolator());
        v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { if (!a.isStarted()) a.start(); }
            @Override public void onViewDetachedFromWindow(View view) { a.cancel(); view.setScaleX(1f); view.setScaleY(1f); view.setAlpha(1f); }
        });
        if (v.isAttachedToWindow()) a.start();
    }

    public static View dot(Context c, int color, int sizeDp) {
        View v = new View(c);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        v.setBackground(d);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    /** Pill with a breathing dot and a label, e.g. the Termux connection indicator. */
    public static final class StatusPill extends LinearLayout {
        private final View dot;
        private final TextView label;
        private final UiTheme t;

        public StatusPill(Context c, UiTheme t) {
            super(c);
            this.t = t;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(c, 10), dp(c, 5), dp(c, 12), dp(c, 5));
            dot = dot(c, t.muted, 8);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(c, 8), dp(c, 8));
            dlp.setMarginEnd(dp(c, 7));
            addView(dot, dlp);
            label = text(c, "…", 11, t.muted, false);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            setBackground(shape(t.glass, dpf(c, 20), t.glassStroke, Math.max(1, dp(c, 1f))));
            pulse(dot, 1.5f, 1100);
        }

        public void set(boolean ok, String message) {
            ((GradientDrawable) dot.getBackground()).setColor(ok ? t.accent : t.danger);
            label.setTextColor(ok ? t.text : t.muted);
            label.setText(message);
        }
    }

    // ───────────────────────── toast ─────────────────────────
    public static void toast(Context c, CharSequence msg) {
        if (!(c instanceof Activity) || ((Activity) c).isFinishing()) { Toast.makeText(c, msg, Toast.LENGTH_SHORT).show(); return; }
        final Activity a = (Activity) c;
        final ViewGroup content = a.findViewById(android.R.id.content);
        if (content == null) { Toast.makeText(c, msg, Toast.LENGTH_SHORT).show(); return; }
        UiTheme t = UiTheme.get(a);
        View old = content.findViewWithTag("tfm_toast");
        if (old != null) content.removeView(old);
        final TextView tv = new TextView(a);
        tv.setTag("tfm_toast");
        tv.setText(msg);
        tv.setTextSize(13);
        tv.setTypeface(medium());
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(t.light ? Color.WHITE : t.text);
        tv.setPadding(dp(a, 18), dp(a, 11), dp(a, 18), dp(a, 11));
        tv.setMaxWidth((int) (a.getResources().getDisplayMetrics().widthPixels * 0.88f));
        int fill = t.light ? Color.rgb(28, 36, 56) : Color.rgb(Math.min(255, Color.red(t.panel2) + 6), Math.min(255, Color.green(t.panel2) + 6), Math.min(255, Color.blue(t.panel2) + 10));
        tv.setBackground(shape(fill, dpf(a, 24), alpha(t.accent, 130), Math.max(1, dp(a, 1f))));
        tv.setElevation(dpf(a, 14));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = dp(a, 96);
        content.addView(tv, lp);
        tv.setAlpha(0f);
        tv.setTranslationY(dpf(a, 28));
        tv.setScaleX(0.9f);
        tv.setScaleY(0.9f);
        tv.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new OvershootInterpolator(1.6f)).start();
        tv.postDelayed(() -> tv.animate().alpha(0f).translationY(dpf(a, 16)).setDuration(220).withEndAction(() -> content.removeView(tv)).start(), 2300);
    }

    // ───────────────────────── screens ─────────────────────────
    /** Sets the activity content to an animated aurora backdrop with {@code root} on top. */
    public static FrameLayout setContent(Activity a, UiTheme t, View root) {
        FrameLayout f = new FrameLayout(a);
        f.setBackgroundColor(t.bg);
        f.addView(new Aurora(a, t), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Color.TRANSPARENT);
        f.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        a.setContentView(f);
        return f;
    }

    public static LinearLayout header(final Activity a, UiTheme t, String title, String subtitle, boolean back, View... trailing) {
        LinearLayout h = new LinearLayout(a);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        if (back) {
            ImageView b = iconButton(a, t, Icons.BACK, false);
            b.setContentDescription("رجوع");
            b.setOnClickListener(v -> a.finish());
            h.addView(b, new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44)));
        }
        LinearLayout titles = new LinearLayout(a);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text(a, title, 21, t.text, true));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = text(a, subtitle, 11, t.muted, false);
            sub.setPadding(0, dp(a, 2), 0, 0);
            titles.addView(sub);
        }
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(back ? dp(a, 12) : dp(a, 2));
        tlp.setMarginEnd(dp(a, 8));
        h.addView(titles, tlp);
        for (View v : trailing) {
            LinearLayout.LayoutParams lp;
            if (v.getLayoutParams() instanceof LinearLayout.LayoutParams) lp = (LinearLayout.LayoutParams) v.getLayoutParams();
            else if (v instanceof StatusPill) lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            else lp = new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44));
            lp.setMarginStart(dp(a, 6));
            h.addView(v, lp);
        }
        enter(h, 0);
        return h;
    }

    // ───────────────────────── dock ─────────────────────────
    private static final Class<?>[] DEST = {MainActivity.class, ToolsActivity.class, AiAssistantActivity.class,
            TerminalSessionsActivity.class, AiSettingsActivity.class};
    private static final int[] DOCK_ICONS = {Icons.FOLDER, Icons.GRID, Icons.SPARK, Icons.TERMINAL, Icons.SETTINGS};
    private static final String[] DOCK_LABELS = {"الملفات", "الأدوات", "الوكيل", "Terminal", "الإعدادات"};

    public static View dock(final Activity a, final UiTheme t, final int selected) {
        final LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(a, 6);
        bar.setPadding(pad, pad, pad, pad);
        int fill = t.light ? alpha(Color.WHITE, 238) : alpha(over(t.bg, alpha(t.panel2, 255)), 238);
        bar.setBackground(shape(fill, dpf(a, 32), t.glassStroke, Math.max(1, dp(a, 1f))));
        bar.setElevation(dpf(a, 12));
        for (int i = 0; i < 5; i++) {
            final int index = i;
            View item;
            if (i == 2) {
                FrameLayout orb = new FrameLayout(a);
                View halo = new View(a);
                halo.setBackground(shape(alpha(t.accent, 70), dpf(a, 30), 0, 0));
                FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(dp(a, 54), dp(a, 54), Gravity.CENTER);
                orb.addView(halo, hlp);
                pulse(halo, 1.22f, 1500);
                ImageView core = new ImageView(a);
                core.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                int cp = dp(a, 11);
                core.setPadding(cp, cp, cp, cp);
                core.setImageDrawable(Icons.get(a, Icons.SPARK, t.onAccent));
                core.setBackground(gradient(t.accent, t.accent2, dpf(a, 30)));
                core.setElevation(dpf(a, 4));
                orb.addView(core, new FrameLayout.LayoutParams(dp(a, 46), dp(a, 46), Gravity.CENTER));
                orb.setContentDescription(DOCK_LABELS[i]);
                item = orb;
            } else {
                LinearLayout col = new LinearLayout(a);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.CENTER);
                boolean on = i == selected;
                ImageView ic = new ImageView(a);
                ic.setImageDrawable(Icons.get(a, DOCK_ICONS[i], on ? t.accent : t.muted));
                col.addView(ic, new LinearLayout.LayoutParams(dp(a, 22), dp(a, 22)));
                TextView lb = text(a, DOCK_LABELS[i], 10, on ? t.accent : t.muted, on);
                lb.setGravity(Gravity.CENTER);
                lb.setSingleLine(true);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                llp.topMargin = dp(a, 3);
                col.addView(lb, llp);
                if (on) col.setBackground(shape(alpha(t.accent, 34), dpf(a, 22), 0, 0));
                item = col;
            }
            item.setClickable(true);
            item.setFocusable(true);
            press(item);
            item.setOnClickListener(v -> go(a, index, selected));
            bar.addView(item, new LinearLayout.LayoutParams(0, dp(a, 54), 1f));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(a, 8);
        lp.bottomMargin = dp(a, 2);
        bar.setLayoutParams(lp);
        hideOnKeyboard(a, bar);
        enter(bar, 4);
        return bar;
    }

    private static void go(Activity a, int target, int current) {
        if (target == current) return;
        Intent i = new Intent(a, DEST[target]);
        if (target == 0) {
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            a.startActivity(i);
            return;
        }
        a.startActivity(i);
        if (current != 0) a.finish();
    }

    public static void hideOnKeyboard(Activity a, final View target) {
        final View decor = a.getWindow().getDecorView();
        decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            decor.getWindowVisibleDisplayFrame(r);
            int full = decor.getRootView().getHeight();
            boolean open = full > 0 && (full - r.bottom) > full * 0.15f;
            int want = open ? View.GONE : View.VISIBLE;
            if (target.getVisibility() != want) target.setVisibility(want);
        });
    }

    // ───────────────────────── dialogs ─────────────────────────
    /** Drop-in replacement for {@code AlertDialog.Builder}: rounded glass sheet with an animated entrance. */
    public static class Sheet extends AlertDialog.Builder {
        private final Context ctx;

        public Sheet(Context c) {
            super(c, UiTheme.isLight(c) ? R.style.TfmDialogLight : R.style.TfmDialogDark);
            ctx = c;
        }

        @Override public AlertDialog create() {
            final AlertDialog d = super.create();
            final UiTheme t = UiTheme.get(ctx);
            Window w = d.getWindow();
            if (w != null) {
                GradientDrawable bg = shape(t.light ? Color.WHITE : t.panel, dpf(ctx, 28), t.glassStroke, Math.max(1, dp(ctx, 1f)));
                w.setBackgroundDrawable(new InsetDrawable(bg, dp(ctx, 14)));
                w.setDimAmount(0.62f);
            }
            d.setOnShowListener(di -> {
                int[] which = {AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL};
                for (int btn : which) {
                    Button b = d.getButton(btn);
                    if (b == null) continue;
                    b.setAllCaps(false);
                    b.setTypeface(medium());
                    b.setTextColor(btn == AlertDialog.BUTTON_POSITIVE ? t.accent : t.muted);
                    b.setBackground(buttonBg(ctx, t, t.panel, 20));
                }
                int titleId = ctx.getResources().getIdentifier("alertTitle", "id", "android");
                View title = titleId != 0 ? d.findViewById(titleId) : null;
                if (title instanceof TextView) { ((TextView) title).setTextColor(t.text); ((TextView) title).setTypeface(semibold()); }
                View msg = d.findViewById(android.R.id.message);
                if (msg instanceof TextView) ((TextView) msg).setTextColor(t.muted);
                if (d.getListView() != null) d.getListView().setDivider(null);
            });
            return d;
        }
    }

    // ───────────────────────── custom views ─────────────────────────
    /** Slow drifting colour blobs behind every screen. */
    public static final class Aurora extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] colors = new int[3];
        private final RadialGradient[] shaders = new RadialGradient[3];
        private final int strength;
        private float phase;
        private float radius;
        private boolean running;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable tick = new Runnable() {
            @Override public void run() {
                if (!running) return;
                phase += 0.012f;
                invalidate();
                handler.postDelayed(this, 56);
            }
        };

        public Aurora(Context c, UiTheme t) {
            super(c);
            colors[0] = t.aurora1; colors[1] = t.aurora2; colors[2] = t.aurora3;
            strength = t.auroraAlpha;
            setWillNotDraw(false);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            radius = Math.max(Math.max(w, h) * 0.62f, 1f);
            for (int i = 0; i < 3; i++) {
                shaders[i] = new RadialGradient(0f, 0f, radius, alpha(colors[i], strength), alpha(colors[i], 0), Shader.TileMode.CLAMP);
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            if (shaders[0] == null) return;
            int w = getWidth(), h = getHeight();
            for (int i = 0; i < 3; i++) {
                float a = phase * (0.7f + i * 0.35f) + i * 2.1f;
                float cx = w * (0.5f + 0.42f * (float) Math.sin(a));
                float cy = h * (0.5f + 0.40f * (float) Math.cos(a * 0.83f + i));
                paint.setShader(shaders[i]);
                canvas.save();
                canvas.translate(cx, cy);
                canvas.drawCircle(0f, 0f, radius, paint);
                canvas.restore();
            }
        }

        private void start() { if (!running) { running = true; handler.post(tick); } }
        private void stop() { running = false; handler.removeCallbacks(tick); }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); start(); }
        @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
        @Override protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if (visibility == View.VISIBLE) start(); else stop();
        }
    }

    /** The AI avatar: a rotating gradient orb with a breathing glow and a spark. */
    public static final class Orb extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int c1, c2, c3;
        private final Drawable icon;
        private final boolean animated;
        private SweepGradient sweep;
        private RadialGradient glow, shine;
        private float angle, breath, speed = 46f;
        private long last;
        private ValueAnimator driver;

        public Orb(Context c, UiTheme t, boolean animated) {
            super(c);
            this.animated = animated;
            c1 = t.accent; c2 = t.accent2; c3 = t.aurora2;
            icon = Icons.get(c, Icons.SPARK, Color.WHITE);
        }

        public void setBusy(boolean busy) { speed = busy ? 170f : 46f; }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f * 0.72f;
            sweep = new SweepGradient(cx, cy, new int[]{c1, c2, c3, c1}, null);
            glow = new RadialGradient(cx, cy, Math.max(r * 1.45f, 1f), alpha(c1, 120), alpha(c1, 0), Shader.TileMode.CLAMP);
            shine = new RadialGradient(cx - r * 0.3f, cy - r * 0.42f, Math.max(r * 1.1f, 1f), alpha(Color.WHITE, 100), alpha(Color.WHITE, 0), Shader.TileMode.CLAMP);
        }

        @Override protected void onDraw(Canvas canvas) {
            if (sweep == null) return;
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(getWidth(), getHeight()) / 2f * 0.72f;
            float s = 1f + 0.07f * (float) Math.sin(breath);
            canvas.save();
            canvas.scale(s, s, cx, cy);
            p.setShader(glow);
            canvas.drawCircle(cx, cy, r * 1.45f, p);
            canvas.restore();
            canvas.save();
            canvas.rotate(angle, cx, cy);
            p.setShader(sweep);
            canvas.drawCircle(cx, cy, r, p);
            canvas.restore();
            p.setShader(shine);
            canvas.drawCircle(cx, cy, r, p);
            int is = (int) (r * 1.05f);
            icon.setBounds((int) (cx - is / 2f), (int) (cy - is / 2f), (int) (cx + is / 2f), (int) (cy + is / 2f));
            icon.draw(canvas);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!animated) return;
            last = SystemClock.uptimeMillis();
            driver = ValueAnimator.ofFloat(0f, 1f);
            driver.setDuration(1000);
            driver.setRepeatCount(ValueAnimator.INFINITE);
            driver.setInterpolator(null);
            driver.addUpdateListener(a -> {
                long now = SystemClock.uptimeMillis();
                float dt = Math.min(0.1f, (now - last) / 1000f);
                last = now;
                angle = (angle + speed * dt) % 360f;
                breath += dt * 3.6f;
                invalidate();
            });
            driver.start();
        }

        @Override protected void onDetachedFromWindow() {
            if (driver != null) { driver.cancel(); driver = null; }
            super.onDetachedFromWindow();
        }
    }

    /** Three bouncing dots: "the model is thinking". */
    public static final class Dots extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ValueAnimator driver;
        private float t;

        public Dots(Context c, int color) {
            super(c);
            p.setColor(color);
        }

        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(dp(getContext(), 38), dp(getContext(), 20)); }

        @Override protected void onDraw(Canvas canvas) {
            float r = dpf(getContext(), 3.2f), gap = dpf(getContext(), 11f);
            float cx = getWidth() / 2f - gap, cy = getHeight() / 2f;
            for (int i = 0; i < 3; i++) {
                float ph = (t + i * 0.16f) % 1f;
                float b = (float) Math.abs(Math.sin(ph * Math.PI));
                p.setAlpha((int) (90 + 165 * b));
                canvas.drawCircle(cx + i * gap, cy - b * dpf(getContext(), 4f), r, p);
            }
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            driver = ValueAnimator.ofFloat(0f, 1f);
            driver.setDuration(1100);
            driver.setRepeatCount(ValueAnimator.INFINITE);
            driver.setInterpolator(null);
            driver.addUpdateListener(a -> { t = (float) a.getAnimatedValue(); invalidate(); });
            driver.start();
        }

        @Override protected void onDetachedFromWindow() {
            if (driver != null) { driver.cancel(); driver = null; }
            super.onDetachedFromWindow();
        }
    }
}
