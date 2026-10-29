package dev.itszain.termuxfilemanager;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

/** Hand-drawn 24-unit line icons. No resources, so they tint and scale freely. */
public final class Icons {
    private Icons() { }

    public static final int FOLDER = 1, FILE = 2, CODE = 3, IMAGE = 4, ARCHIVE = 5, SEARCH = 6, PLUS = 7, REFRESH = 8,
            EYE = 9, EYE_OFF = 10, UP = 11, BACK = 12, CHEVRON = 13, MORE = 14, SEND = 15, STOP = 16, TERMINAL = 17,
            GRID = 18, SETTINGS = 19, SPARK = 20, HOME = 21, PHONE = 22, COPY = 23, TRASH = 24, CLOSE = 25, CHECK = 26,
            PLAY = 27, BULB = 28, EDIT = 29, INFO = 30, KEY = 31, SHIELD = 32, SLIDERS = 33, THEME = 34, BOLT = 35,
            GLOBE = 36, LAYERS = 37, CHAT = 38, CLOCK = 39, BOX = 40, PULSE = 41, DOWNLOAD = 42, DOC = 43, DOTS_H = 44;

    public static Drawable get(Context c, int id, int color) { return new IconDrawable(id, color); }

    public static ImageView view(Context c, int id, int color, int sizeDp) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(new IconDrawable(id, color));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int px = Ui.dp(c, sizeDp);
        v.setLayoutParams(new android.widget.LinearLayout.LayoutParams(px, px));
        return v;
    }

    /** Picks an icon + tint for a file entry so the file list reads at a glance. */
    public static int forFile(String name, boolean dir, boolean link) {
        if (dir) return FOLDER;
        if (link) return CHEVRON;
        String n = name == null ? "" : name.toLowerCase();
        int dot = n.lastIndexOf('.');
        String ext = dot >= 0 ? n.substring(dot + 1) : "";
        switch (ext) {
            case "java": case "kt": case "py": case "js": case "ts": case "c": case "cpp": case "h": case "rs": case "go":
            case "rb": case "php": case "lua": case "html": case "css": case "json": case "xml": case "yml": case "yaml":
            case "toml": case "gradle": case "sh": case "bash": case "zsh": case "sql":
                return CODE;
            case "png": case "jpg": case "jpeg": case "gif": case "webp": case "svg": case "bmp":
                return IMAGE;
            case "zip": case "tar": case "gz": case "xz": case "bz2": case "7z": case "rar": case "apk": case "jar": case "deb":
                return ARCHIVE;
            case "txt": case "md": case "log": case "csv": case "ini": case "conf": case "cfg":
                return DOC;
            default:
                return FILE;
        }
    }

    public static int tintForFile(UiTheme t, String name, boolean dir) {
        if (dir) return t.accent;
        int icon = forFile(name, false, false);
        if (icon == CODE) return t.accent2;
        if (icon == IMAGE) return Color.rgb(255, 170, 90);
        if (icon == ARCHIVE) return Color.rgb(255, 207, 94);
        if (icon == DOC) return Ui.mix(t.text, t.muted, 0.35f);
        return t.muted;
    }

    static final class IconDrawable extends Drawable {
        private final int id;
        private int color;
        private int alpha = 255;
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path sp = new Path();
        private final Path fp = new Path();

        IconDrawable(int id, int color) {
            this.id = id;
            this.color = color;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setStrokeWidth(1.9f);
            fill.setStyle(Paint.Style.FILL);
            setAutoMirrored(id == BACK || id == CHEVRON);
            build();
            applyColor();
        }

        void setColor(int c) { color = c; applyColor(); invalidateSelf(); }

        private void applyColor() {
            int a = Color.alpha(color) * alpha / 255;
            stroke.setColor(color); stroke.setAlpha(a);
            fill.setColor(color); fill.setAlpha(a);
        }

        private void build() {
            Path.Direction cw = Path.Direction.CW;
            switch (id) {
                case FOLDER:
                    sp.moveTo(3.5f, 6.5f); sp.lineTo(3.5f, 17.5f); sp.quadTo(3.5f, 19.5f, 5.5f, 19.5f);
                    sp.lineTo(18.5f, 19.5f); sp.quadTo(20.5f, 19.5f, 20.5f, 17.5f); sp.lineTo(20.5f, 9.5f);
                    sp.quadTo(20.5f, 7.5f, 18.5f, 7.5f); sp.lineTo(11.5f, 7.5f); sp.lineTo(10f, 5.4f);
                    sp.quadTo(9.7f, 4.5f, 8.7f, 4.5f); sp.lineTo(5.5f, 4.5f); sp.quadTo(3.5f, 4.5f, 3.5f, 6.5f); sp.close();
                    break;
                case FILE:
                    sp.moveTo(7f, 3f); sp.lineTo(13.5f, 3f); sp.lineTo(19f, 8.5f); sp.lineTo(19f, 19f);
                    sp.quadTo(19f, 21f, 17f, 21f); sp.lineTo(7f, 21f); sp.quadTo(5f, 21f, 5f, 19f);
                    sp.lineTo(5f, 5f); sp.quadTo(5f, 3f, 7f, 3f); sp.close();
                    sp.moveTo(13.5f, 3f); sp.lineTo(13.5f, 8.5f); sp.lineTo(19f, 8.5f);
                    break;
                case DOC:
                    sp.moveTo(7f, 3f); sp.lineTo(13.5f, 3f); sp.lineTo(19f, 8.5f); sp.lineTo(19f, 19f);
                    sp.quadTo(19f, 21f, 17f, 21f); sp.lineTo(7f, 21f); sp.quadTo(5f, 21f, 5f, 19f);
                    sp.lineTo(5f, 5f); sp.quadTo(5f, 3f, 7f, 3f); sp.close();
                    sp.moveTo(13.5f, 3f); sp.lineTo(13.5f, 8.5f); sp.lineTo(19f, 8.5f);
                    sp.moveTo(8.5f, 13f); sp.lineTo(15.5f, 13f); sp.moveTo(8.5f, 16.5f); sp.lineTo(13f, 16.5f);
                    break;
                case CODE:
                    sp.moveTo(8.5f, 7.5f); sp.lineTo(4f, 12f); sp.lineTo(8.5f, 16.5f);
                    sp.moveTo(15.5f, 7.5f); sp.lineTo(20f, 12f); sp.lineTo(15.5f, 16.5f);
                    sp.moveTo(13.5f, 5.5f); sp.lineTo(10.5f, 18.5f);
                    break;
                case IMAGE:
                    sp.addRoundRect(new RectF(3.5f, 4.5f, 20.5f, 19.5f), 3f, 3f, cw);
                    sp.addCircle(9f, 10f, 1.6f, cw);
                    sp.moveTo(4f, 17.5f); sp.lineTo(9.5f, 13f); sp.lineTo(13f, 16f); sp.lineTo(16f, 13.5f); sp.lineTo(20f, 17f);
                    break;
                case ARCHIVE:
                    sp.addRoundRect(new RectF(3.5f, 4f, 20.5f, 9f), 1.8f, 1.8f, cw);
                    sp.moveTo(5f, 9f); sp.lineTo(5f, 18f); sp.quadTo(5f, 20f, 7f, 20f); sp.lineTo(17f, 20f);
                    sp.quadTo(19f, 20f, 19f, 18f); sp.lineTo(19f, 9f);
                    sp.moveTo(10f, 13f); sp.lineTo(14f, 13f);
                    break;
                case SEARCH:
                    sp.addCircle(10.5f, 10.5f, 6.3f, cw);
                    sp.moveTo(15.2f, 15.2f); sp.lineTo(20.5f, 20.5f);
                    break;
                case PLUS:
                    sp.moveTo(12f, 5f); sp.lineTo(12f, 19f); sp.moveTo(5f, 12f); sp.lineTo(19f, 12f);
                    break;
                case REFRESH:
                    sp.addArc(new RectF(5f, 5f, 19f, 19f), -50f, 290f);
                    sp.moveTo(16.8f, 2.8f); sp.lineTo(16.8f, 6.8f); sp.lineTo(20.8f, 6.8f);
                    break;
                case EYE:
                case EYE_OFF:
                    sp.moveTo(2.5f, 12f); sp.quadTo(12f, 3f, 21.5f, 12f); sp.quadTo(12f, 21f, 2.5f, 12f); sp.close();
                    sp.addCircle(12f, 12f, 3f, cw);
                    if (id == EYE_OFF) { sp.moveTo(4f, 4f); sp.lineTo(20f, 20f); }
                    break;
                case UP:
                case SEND:
                    sp.moveTo(12f, 19f); sp.lineTo(12f, 5f);
                    sp.moveTo(6f, 11f); sp.lineTo(12f, 5f); sp.lineTo(18f, 11f);
                    break;
                case BACK:
                    sp.moveTo(15f, 5f); sp.lineTo(8f, 12f); sp.lineTo(15f, 19f);
                    break;
                case CHEVRON:
                    sp.moveTo(9f, 5f); sp.lineTo(16f, 12f); sp.lineTo(9f, 19f);
                    break;
                case MORE:
                    fp.addCircle(12f, 5.5f, 1.8f, cw); fp.addCircle(12f, 12f, 1.8f, cw); fp.addCircle(12f, 18.5f, 1.8f, cw);
                    break;
                case DOTS_H:
                    fp.addCircle(5.5f, 12f, 1.8f, cw); fp.addCircle(12f, 12f, 1.8f, cw); fp.addCircle(18.5f, 12f, 1.8f, cw);
                    break;
                case STOP:
                    fp.addRoundRect(new RectF(7f, 7f, 17f, 17f), 2.5f, 2.5f, cw);
                    break;
                case TERMINAL:
                    sp.addRoundRect(new RectF(3f, 4.5f, 21f, 19.5f), 3f, 3f, cw);
                    sp.moveTo(7f, 9.5f); sp.lineTo(10.5f, 12f); sp.lineTo(7f, 14.5f);
                    sp.moveTo(12.5f, 15f); sp.lineTo(17f, 15f);
                    break;
                case GRID:
                    sp.addRoundRect(new RectF(4f, 4f, 10.5f, 10.5f), 2f, 2f, cw);
                    sp.addRoundRect(new RectF(13.5f, 4f, 20f, 10.5f), 2f, 2f, cw);
                    sp.addRoundRect(new RectF(4f, 13.5f, 10.5f, 20f), 2f, 2f, cw);
                    sp.addRoundRect(new RectF(13.5f, 13.5f, 20f, 20f), 2f, 2f, cw);
                    break;
                case SETTINGS:
                    sp.addCircle(12f, 12f, 2.7f, cw);
                    sp.addCircle(12f, 12f, 6.2f, cw);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.toRadians(i * 45);
                        float cx = (float) Math.cos(a), cy = (float) Math.sin(a);
                        sp.moveTo(12f + cx * 6.2f, 12f + cy * 6.2f);
                        sp.lineTo(12f + cx * 9.2f, 12f + cy * 9.2f);
                    }
                    break;
                case SPARK:
                    fp.moveTo(11f, 3.5f); fp.quadTo(11.8f, 10.2f, 18.5f, 11f); fp.quadTo(11.8f, 11.8f, 11f, 18.5f);
                    fp.quadTo(10.2f, 11.8f, 3.5f, 11f); fp.quadTo(10.2f, 10.2f, 11f, 3.5f); fp.close();
                    fp.moveTo(18.5f, 14.6f); fp.quadTo(18.8f, 17.2f, 21.4f, 17.5f); fp.quadTo(18.8f, 17.8f, 18.5f, 20.4f);
                    fp.quadTo(18.2f, 17.8f, 15.6f, 17.5f); fp.quadTo(18.2f, 17.2f, 18.5f, 14.6f); fp.close();
                    break;
                case HOME:
                    sp.moveTo(3.5f, 11f); sp.lineTo(12f, 4f); sp.lineTo(20.5f, 11f);
                    sp.moveTo(5.5f, 9.5f); sp.lineTo(5.5f, 19f); sp.quadTo(5.5f, 20f, 6.5f, 20f); sp.lineTo(17.5f, 20f);
                    sp.quadTo(18.5f, 20f, 18.5f, 19f); sp.lineTo(18.5f, 9.5f);
                    sp.moveTo(10f, 20f); sp.lineTo(10f, 14f); sp.lineTo(14f, 14f); sp.lineTo(14f, 20f);
                    break;
                case PHONE:
                    sp.addRoundRect(new RectF(6.5f, 2.5f, 17.5f, 21.5f), 3f, 3f, cw);
                    sp.moveTo(10.5f, 18.5f); sp.lineTo(13.5f, 18.5f);
                    break;
                case COPY:
                    sp.addRoundRect(new RectF(8.5f, 8.5f, 19.5f, 19.5f), 2.5f, 2.5f, cw);
                    sp.moveTo(15.5f, 8.5f); sp.lineTo(15.5f, 6.5f); sp.quadTo(15.5f, 4.5f, 13.5f, 4.5f); sp.lineTo(6.5f, 4.5f);
                    sp.quadTo(4.5f, 4.5f, 4.5f, 6.5f); sp.lineTo(4.5f, 13.5f); sp.quadTo(4.5f, 15.5f, 6.5f, 15.5f); sp.lineTo(8.5f, 15.5f);
                    break;
                case TRASH:
                    sp.moveTo(4f, 7f); sp.lineTo(20f, 7f);
                    sp.moveTo(9f, 7f); sp.lineTo(9f, 4.5f); sp.lineTo(15f, 4.5f); sp.lineTo(15f, 7f);
                    sp.moveTo(6f, 7f); sp.lineTo(7f, 19f); sp.quadTo(7.1f, 20.5f, 8.6f, 20.5f); sp.lineTo(15.4f, 20.5f);
                    sp.quadTo(16.9f, 20.5f, 17f, 19f); sp.lineTo(18f, 7f);
                    sp.moveTo(10.5f, 11f); sp.lineTo(10.5f, 17f); sp.moveTo(13.5f, 11f); sp.lineTo(13.5f, 17f);
                    break;
                case CLOSE:
                    sp.moveTo(6f, 6f); sp.lineTo(18f, 18f); sp.moveTo(18f, 6f); sp.lineTo(6f, 18f);
                    break;
                case CHECK:
                    sp.moveTo(5f, 12.5f); sp.lineTo(10f, 17.5f); sp.lineTo(19f, 7f);
                    break;
                case PLAY:
                    fp.moveTo(8f, 5.5f); fp.lineTo(19f, 12f); fp.lineTo(8f, 18.5f); fp.close();
                    sp.addPath(fp);
                    break;
                case BULB:
                    sp.addCircle(12f, 9.5f, 5.5f, cw);
                    sp.moveTo(9.5f, 17.5f); sp.lineTo(14.5f, 17.5f); sp.moveTo(10.5f, 20.5f); sp.lineTo(13.5f, 20.5f);
                    sp.moveTo(9.8f, 15f); sp.lineTo(14.2f, 15f);
                    break;
                case EDIT:
                    sp.moveTo(4f, 20f); sp.lineTo(4.8f, 15.8f); sp.lineTo(16.3f, 4.3f); sp.quadTo(17f, 3.6f, 17.7f, 4.3f);
                    sp.lineTo(19.7f, 6.3f); sp.quadTo(20.4f, 7f, 19.7f, 7.7f); sp.lineTo(8.2f, 19.2f); sp.close();
                    sp.moveTo(14.5f, 6.2f); sp.lineTo(17.8f, 9.5f);
                    break;
                case INFO:
                    sp.addCircle(12f, 12f, 9f, cw);
                    sp.moveTo(12f, 11f); sp.lineTo(12f, 16.5f);
                    fp.addCircle(12f, 7.8f, 1.1f, cw);
                    break;
                case KEY:
                    sp.addCircle(8f, 15.5f, 4f, cw);
                    sp.moveTo(10.9f, 12.6f); sp.lineTo(20f, 3.5f); sp.moveTo(16.5f, 7f); sp.lineTo(19f, 9.5f);
                    sp.moveTo(13.5f, 10f); sp.lineTo(15.5f, 12f);
                    break;
                case SHIELD:
                    sp.moveTo(12f, 3f); sp.lineTo(19.5f, 6f); sp.lineTo(19.5f, 12f); sp.quadTo(19.5f, 18f, 12f, 21f);
                    sp.quadTo(4.5f, 18f, 4.5f, 12f); sp.lineTo(4.5f, 6f); sp.close();
                    sp.moveTo(8.8f, 12f); sp.lineTo(11f, 14.2f); sp.lineTo(15.4f, 9.6f);
                    break;
                case SLIDERS:
                    sp.moveTo(4f, 7f); sp.lineTo(6.8f, 7f); sp.moveTo(11.2f, 7f); sp.lineTo(20f, 7f);
                    sp.moveTo(4f, 12f); sp.lineTo(12.8f, 12f); sp.moveTo(17.2f, 12f); sp.lineTo(20f, 12f);
                    sp.moveTo(4f, 17f); sp.lineTo(5.8f, 17f); sp.moveTo(10.2f, 17f); sp.lineTo(20f, 17f);
                    sp.addCircle(9f, 7f, 2.2f, cw); sp.addCircle(15f, 12f, 2.2f, cw); sp.addCircle(8f, 17f, 2.2f, cw);
                    break;
                case THEME:
                    sp.addCircle(12f, 12f, 8.5f, cw);
                    fp.moveTo(12f, 3.5f); fp.arcTo(new RectF(3.5f, 3.5f, 20.5f, 20.5f), -90f, 180f); fp.close();
                    break;
                case BOLT:
                    fp.moveTo(13f, 2.5f); fp.lineTo(5.5f, 13f); fp.lineTo(11f, 13f); fp.lineTo(10f, 21.5f);
                    fp.lineTo(18.5f, 10.5f); fp.lineTo(13f, 10.5f); fp.close();
                    sp.addPath(fp);
                    break;
                case GLOBE:
                    sp.addCircle(12f, 12f, 9f, cw);
                    sp.moveTo(3f, 12f); sp.lineTo(21f, 12f);
                    sp.addOval(new RectF(8f, 3f, 16f, 21f), cw);
                    break;
                case LAYERS:
                    sp.moveTo(12f, 3.5f); sp.lineTo(21f, 8.5f); sp.lineTo(12f, 13.5f); sp.lineTo(3f, 8.5f); sp.close();
                    sp.moveTo(3f, 12.5f); sp.lineTo(12f, 17.5f); sp.lineTo(21f, 12.5f);
                    sp.moveTo(3f, 16.5f); sp.lineTo(12f, 21.5f); sp.lineTo(21f, 16.5f);
                    break;
                case CHAT:
                    sp.addRoundRect(new RectF(3.5f, 4.5f, 20.5f, 16.5f), 4f, 4f, cw);
                    sp.moveTo(9f, 16.5f); sp.lineTo(9f, 20.5f); sp.lineTo(13f, 16.5f);
                    break;
                case CLOCK:
                    sp.addCircle(12f, 12f, 9f, cw);
                    sp.moveTo(12f, 7f); sp.lineTo(12f, 12f); sp.lineTo(15.5f, 14f);
                    break;
                case BOX:
                    sp.moveTo(12f, 3f); sp.lineTo(20.5f, 7.5f); sp.lineTo(20.5f, 16.5f); sp.lineTo(12f, 21f);
                    sp.lineTo(3.5f, 16.5f); sp.lineTo(3.5f, 7.5f); sp.close();
                    sp.moveTo(3.5f, 7.5f); sp.lineTo(12f, 12f); sp.lineTo(20.5f, 7.5f);
                    sp.moveTo(12f, 12f); sp.lineTo(12f, 21f);
                    break;
                case PULSE:
                    sp.moveTo(2.5f, 12f); sp.lineTo(7f, 12f); sp.lineTo(9.5f, 6f); sp.lineTo(13f, 18f);
                    sp.lineTo(15.5f, 12f); sp.lineTo(21.5f, 12f);
                    break;
                case DOWNLOAD:
                    sp.moveTo(12f, 4f); sp.lineTo(12f, 15f);
                    sp.moveTo(7f, 10.5f); sp.lineTo(12f, 15.5f); sp.lineTo(17f, 10.5f);
                    sp.moveTo(5f, 19.5f); sp.lineTo(19f, 19.5f);
                    break;
                default:
                    sp.addCircle(12f, 12f, 8f, cw);
                    break;
            }
        }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            float s = Math.min(b.width(), b.height()) / 24f;
            int save = canvas.save();
            canvas.translate(b.left + (b.width() - 24f * s) / 2f, b.top + (b.height() - 24f * s) / 2f);
            if (isAutoMirrored() && getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                canvas.translate(24f * s, 0f);
                canvas.scale(-s, s);
            } else {
                canvas.scale(s, s);
            }
            if (!sp.isEmpty()) canvas.drawPath(sp, stroke);
            if (!fp.isEmpty()) canvas.drawPath(fp, fill);
            canvas.restoreToCount(save);
        }

        @Override public void setAlpha(int a) { alpha = a; applyColor(); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter cf) { stroke.setColorFilter(cf); fill.setColorFilter(cf); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return Math.round(24f * Resources.getSystem().getDisplayMetrics().density); }
        @Override public int getIntrinsicHeight() { return getIntrinsicWidth(); }
    }
}
