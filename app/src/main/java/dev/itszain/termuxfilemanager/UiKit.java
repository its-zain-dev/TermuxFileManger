package dev.itszain.termuxfilemanager;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Shared design system for every screen of the app.
 *
 * <p>The app builds all of its UI in code (no XML layouts), so this class is the single
 * source of truth for spacing, radii, typography, colors and interaction feedback.
 * Screens compose these primitives instead of hand-rolling gradients everywhere,
 * which is what makes the file manager, the agent console and the AI chat look
 * like one product instead of three separate apps.
 */
public final class UiKit {

    /** Callback for the bottom sheet component. */
    public interface SheetListener {
        void onPick(int index);
    }

    /** Callback for text input dialogs. */
    public interface TextCallback {
        void onText(String value);
    }

    private final Context ctx;
    private final UiTheme theme;

    public final int bg;
    public final int panel;
    public final int panelAlt;
    public final int text;
    public final int muted;
    public final int accent;
    public final int accentSoft;
    public final int accentInk;
    public final int danger;
    public final int dangerSoft;
    public final int divider;
    public final int outline;
    public final int surfaceHi;

    public UiKit(Context context) {
        this.ctx = context.getApplicationContext();
        this.theme = UiTheme.get(context);
        this.bg = theme.bg;
        this.panel = theme.panel;
        this.panelAlt = theme.panel2;
        this.text = theme.text;
        this.muted = theme.muted;
        this.accent = theme.accent;
        this.danger = theme.danger;
        this.divider = theme.divider;
        this.outline = theme.divider;
        this.accentSoft = blend(accent, bg, 0.16f);
        this.dangerSoft = blend(danger, bg, 0.16f);
        this.surfaceHi = blend(panel, text, 0.06f);
        this.accentInk = readableInk(accent);
    }

    // ---------------------------------------------------------------- palette

    /** Mixes {@code a} over {@code b} with {@code ratio} weight for {@code a}. */
    public static int blend(int a, int b, float ratio) {
        float r = Math.max(0f, Math.min(1f, ratio));
        float inv = 1f - r;
        return Color.argb(
                Math.round(Color.alpha(a) * r + Color.alpha(b) * inv),
                Math.round(Color.red(a) * r + Color.red(b) * inv),
                Math.round(Color.green(a) * r + Color.green(b) * inv),
                Math.round(Color.blue(a) * r + Color.blue(b) * inv));
    }

    /** Picks black or white ink depending on the luminance of the given background. */
    public static int readableInk(int background) {
        double luminance = (0.299 * Color.red(background) + 0.587 * Color.green(background) + 0.114 * Color.blue(background)) / 255.0;
        return luminance > 0.58 ? Color.rgb(12, 16, 26) : Color.WHITE;
    }

    public int dp(float value) {
        return (int) (value * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }

    public float sp(float value) {
        return value * ctx.getResources().getDisplayMetrics().scaledDensity;
    }

    // ---------------------------------------------------------------- shapes

    public GradientDrawable round(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    public GradientDrawable round(int color, float radiusDp, int strokeColor) {
        GradientDrawable drawable = round(color, radiusDp);
        if (strokeColor != Color.TRANSPARENT) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    /** A rounded surface with press feedback (ripple) — the default interactive background. */
    public Drawable interactive(int color, float radiusDp, int strokeColor) {
        GradientDrawable content = round(color, radiusDp, strokeColor);
        GradientDrawable mask = round(Color.WHITE, radiusDp);
        int rippleColor = blend(text, color, 0.22f);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    /** Press feedback for a flat (transparent) surface. */
    public Drawable interactiveFlat(float radiusDp) {
        GradientDrawable mask = round(Color.WHITE, radiusDp);
        int rippleColor = blend(text, bg, 0.20f);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask);
    }

    public View divider() {
        View line = new View(ctx);
        line.setBackgroundColor(divider);
        return line;
    }

    public LinearLayout dividerRow() {
        LinearLayout holder = new LinearLayout(ctx);
        holder.addView(divider(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return holder;
    }

    // ---------------------------------------------------------------- typography

    public TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(ctx);
        view.setText(value == null ? "" : value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        return view;
    }

    public TextView mono(String value, float size, int color) {
        TextView view = text(value, size, color, false);
        view.setTypeface(Typeface.MONOSPACE);
        return view;
    }

    /** Small uppercase-ish caption used for section eyebrows. */
    public TextView eyebrow(String value) {
        TextView view = text(value, 11, accent, true);
        view.setLetterSpacing(0.08f);
        return view;
    }

    public TextView sectionTitle(String value) {
        TextView view = text(value, 15, text, true);
        view.setPadding(dp(2), dp(14), 0, dp(6));
        return view;
    }

    public TextView caption(String value) {
        TextView view = text(value, 11.5f, muted, false);
        view.setGravity(Gravity.TOP | Gravity.START);
        return view;
    }

    // ---------------------------------------------------------------- containers

    /** Elevated panel used to group related content. */
    public LinearLayout card() {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.setBackground(round(panel, 18, outline));
        return box;
    }

    /** A flat, tappable row used inside cards. */
    public LinearLayout row() {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(8), dp(9), dp(8), dp(9));
        box.setBackground(interactiveFlat(14));
        box.setClickable(true);
        box.setFocusable(true);
        return box;
    }

    public LinearLayout column() {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        return box;
    }

    public LinearLayout spacer(int heightDp) {
        LinearLayout box = new LinearLayout(ctx);
        box.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)));
        return box;
    }

    public LinearLayout.LayoutParams match() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        return params;
    }

    public LinearLayout.LayoutParams matchNoMargin() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** Wrap-content width: keeps chat bubbles hugging their text instead of stretching. */
    public LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    // ---------------------------------------------------------------- controls

    public Button button(String label, int background, int foreground) {
        Button view = new Button(ctx);
        view.setText(label);
        view.setTextColor(foreground);
        view.setTextSize(13);
        view.setAllCaps(false);
        view.setMinHeight(0);
        view.setMinimumHeight(0);
        view.setMinimumWidth(0);
        view.setPadding(dp(12), dp(7), dp(12), dp(7));
        view.setGravity(Gravity.CENTER);
        view.setBackground(interactive(background, 13, Color.TRANSPARENT));
        return view;
    }

    public Button primary(String label) {
        return button(label, accent, accentInk);
    }

    public Button ghost(String label) {
        return button(label, panelAlt, text);
    }

    public Button outlineButton(String label) {
        Button view = button(label, Color.TRANSPARENT, accent);
        view.setBackground(interactive(Color.TRANSPARENT, 13, outline));
        return view;
    }

    public Button dangerButton(String label) {
        return button(label, dangerSoft, danger);
    }

    /** Compact pill used for filters, quick paths and status. */
    public TextView chip(String label, boolean active) {
        TextView view = text(label, 11.5f, active ? accent : muted, active);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(7), dp(12), dp(7));
        view.setBackground(interactive(active ? accentSoft : panelAlt, 20, active ? blend(accent, bg, 0.45f) : Color.TRANSPARENT));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    /** Square icon-ish button (glyph based, no drawable assets needed). */
    public Button iconButton(String glyph, String description) {
        Button view = new Button(ctx);
        view.setText(glyph);
        view.setTextColor(text);
        view.setTextSize(19);
        view.setAllCaps(false);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(0);
        view.setMinimumHeight(0);
        view.setMinimumWidth(0);
        view.setPadding(dp(4), dp(4), dp(4), dp(4));
        view.setBackground(interactive(panelAlt, 16, Color.TRANSPARENT));
        if (description != null) view.setContentDescription(description);
        return view;
    }

    public EditText field(String hint, int lines) {
        EditText input = new EditText(ctx);
        input.setHint(hint);
        input.setTextColor(text);
        input.setHintTextColor(muted);
        input.setTextSize(13);
        input.setPadding(dp(12), dp(9), dp(12), dp(9));
        input.setBackground(round(panelAlt, 14, outline));
        if (lines <= 1) {
            input.setSingleLine(true);
        } else {
            input.setSingleLine(false);
            input.setMinLines(lines);
            input.setMaxLines(lines + 4);
            input.setGravity(Gravity.TOP | Gravity.START);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }
        return input;
    }

    /** Status pill with a leading dot, e.g. "متصل بـ Termux". */
    public LinearLayout statusPill(String label, boolean ok) {
        LinearLayout pill = new LinearLayout(ctx);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(10), dp(5), dp(12), dp(5));
        pill.setBackground(round(ok ? accentSoft : panelAlt, 20, ok ? blend(accent, bg, 0.4f) : Color.TRANSPARENT));
        TextView dot = text(ok ? "●" : "○", 10, ok ? accent : muted, false);
        dot.setPadding(dp(0), 0, dp(6), 0);
        pill.addView(dot);
        pill.addView(text(label, 11.5f, ok ? accent : muted, false));
        return pill;
    }

    /** Big friendly placeholder for empty folders, empty chats and empty search results. */
    public LinearLayout emptyState(String glyph, String title, String subtitle, String actionLabel, Runnable action) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(24), dp(38), dp(24), dp(38));
        TextView icon = text(glyph, 40, accent, false);
        icon.setGravity(Gravity.CENTER);
        icon.setAlpha(0.85f);
        box.addView(icon, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)));
        TextView head = text(title, 15, text, true);
        head.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headLp.topMargin = dp(10);
        box.addView(head, headLp);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = caption(subtitle);
            sub.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = dp(6);
            box.addView(sub, subLp);
        }
        if (actionLabel != null && action != null) {
            Button go = outlineButton(actionLabel);
            go.setOnClickListener(v -> action.run());
            LinearLayout.LayoutParams goLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
            goLp.topMargin = dp(14);
            box.addView(go, goLp);
        }
        return box;
    }

    // ---------------------------------------------------------------- app bar

    /**
     * Top app bar: a large title, an optional subtitle and a slot for trailing actions.
     * Returns the bar so callers can add their own trailing widgets.
     */
    public LinearLayout appBar(String title, String subtitle) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), dp(10), dp(14), dp(8));
        TextView head = text(title, 24, text, true);
        bar.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = caption(subtitle);
            sub.setPadding(0, dp(3), 0, 0);
            bar.addView(sub, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return bar;
    }

    /** Back arrow plus title, used by the secondary screens. */
    public LinearLayout subBar(String title, String subtitle, Runnable onBack) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(8), dp(14), dp(8));
        Button back = iconButton("‹", "رجوع");
        back.setTextSize(26);
        back.setOnClickListener(v -> onBack.run());
        bar.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout titles = new LinearLayout(ctx);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_VERTICAL);
        titles.addView(text(title, 18, text, true), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = caption(subtitle);
            sub.setPadding(0, dp(2), 0, 0);
            titles.addView(sub, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titlesLp.leftMargin = dp(10);
        bar.addView(titles, titlesLp);
        return bar;
    }

    // ---------------------------------------------------------------- bottom sheet

    /**
     * Bottom sheet with a title and a list of actions. Replaces the plain
     * {@code AlertDialog.setItems} lists so every menu in the app looks the same.
     */
    public void sheet(String title, String[] labels, SheetListener listener) {
        Dialog dialog = new Dialog(ctx);
        LinearLayout sheet = new LinearLayout(ctx);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(16), dp(12), dp(16), dp(16));
        sheet.setBackground(round(panel, 24, outline));
        View handle = new View(ctx);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(divider);
        handleBg.setCornerRadius(dp(3));
        handle.setBackground(handleBg);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(38), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(12);
        sheet.addView(handle, handleLp);

        if (title != null && !title.isEmpty()) {
            TextView head = text(title, 16, text, true);
            head.setPadding(dp(4), 0, dp(4), dp(8));
            sheet.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView item = text(labels[i], 14, text, false);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(14), dp(13), dp(14), dp(13));
            item.setBackground(interactiveFlat(14));
            item.setClickable(true);
            item.setFocusable(true);
            item.setOnClickListener(v -> {
                dialog.dismiss();
                if (listener != null) listener.onPick(index);
            });
            sheet.addView(item, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        TextView cancel = text("إلغاء", 14, muted, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(14), dp(13), dp(14), dp(13));
        cancel.setBackground(interactiveFlat(14));
        cancel.setClickable(true);
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelLp.topMargin = dp(6);
        sheet.addView(cancel, cancelLp);

        dialog.setContentView(sheet);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
        }
        dialog.show();
        Window shown = dialog.getWindow();
        if (shown != null) {
            shown.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            shown.setGravity(Gravity.BOTTOM);
        }
    }

    // ---------------------------------------------------------------- dialogs

    /** Builds the shared dialog chrome: rounded card, title, body and an action row. */
    public LinearLayout dialogCard(String title, String body) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(12));
        card.setBackground(round(panel, 22, outline));
        if (title != null && !title.isEmpty()) {
            TextView head = text(title, 17, text, true);
            head.setGravity(Gravity.TOP | Gravity.START);
            head.setPadding(dp(2), 0, dp(2), dp(6));
            card.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (body != null && !body.isEmpty()) {
            TextView content = text(body, 13, text, false);
            content.setGravity(Gravity.TOP | Gravity.START);
            content.setTextIsSelectable(true);
            content.setLineSpacing(dp(3), 1f);
            card.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return card;
    }

    public void showCard(Dialog dialog, LinearLayout card) {
        dialog.setContentView(card);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
        Window shown = dialog.getWindow();
        if (shown != null) shown.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** Read-only information card (file details, command output, hints). */
    public void infoDialog(String title, String body) {
        Dialog dialog = new Dialog(ctx);
        LinearLayout card = dialogCard(title, body);
        Button close = primary("تمام");
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        closeLp.topMargin = dp(14);
        card.addView(close, closeLp);
        showCard(dialog, card);
    }

    /** Confirmation card with a destructive-looking primary action. */
    public void confirmDialog(String title, String body, String positiveLabel, Runnable onConfirm) {
        Dialog dialog = new Dialog(ctx);
        LinearLayout card = dialogCard(title, body);
        LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button cancel = ghost("إلغاء");
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button confirm = dangerButton(positiveLabel);
        confirm.setOnClickListener(v -> {
            dialog.dismiss();
            if (onConfirm != null) onConfirm.run();
        });
        LinearLayout.LayoutParams confirmLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        confirmLp.leftMargin = dp(8);
        actions.addView(confirm, confirmLp);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(16);
        card.addView(actions, actionsLp);
        showCard(dialog, card);
    }

    /** Single input dialog used for paths, names and destinations. */
    public void promptDialog(String title, String hint, String initial, String positiveLabel, TextCallback callback) {
        Dialog dialog = new Dialog(ctx);
        LinearLayout card = dialogCard(title, null);
        EditText input = field(hint, initial != null && initial.contains("\n") ? 3 : 1);
        if (initial != null) input.setText(initial);
        input.setSelectAllOnFocus(false);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        inputLp.topMargin = dp(6);
        card.addView(input, inputLp);
        LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button cancel = ghost("إلغاء");
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button confirm = primary(positiveLabel);
        confirm.setOnClickListener(v -> {
            dialog.dismiss();
            if (callback != null) callback.onText(input.getText().toString().trim());
        });
        LinearLayout.LayoutParams confirmLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        confirmLp.leftMargin = dp(8);
        actions.addView(confirm, confirmLp);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(14);
        card.addView(actions, actionsLp);
        showCard(dialog, card);
    }
}
