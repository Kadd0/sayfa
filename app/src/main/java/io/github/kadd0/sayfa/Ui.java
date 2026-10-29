// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.kadd0.sayfa;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** iOS-style dark palette, type scale and small view factories shared by every screen. */
final class Ui {
    // iOS dark system colours
    static final int BG = 0xFF000000;
    static final int GROUPED = 0xFF1C1C1E;
    static final int ELEVATED = 0xFF2C2C2E;
    static final int FILL = 0xFF3A3A3C;
    static final int SEPARATOR = 0xFF38383A;
    static final int LABEL = 0xFFFFFFFF;
    static final int LABEL_2 = 0x99EBEBF5;
    static final int LABEL_3 = 0x4DEBEBF5;
    static final int ACCENT = 0xFFFF9F0A;
    static final int ACCENT_TINT = 0x33FF9F0A;
    static final int BAR = 0xF21C1C1E;

    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    // Roboto Medium only ships from Android 5; KitKat gets bold instead.
    static final Typeface MEDIUM = Build.VERSION.SDK_INT >= 21
            ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.create("sans-serif", Typeface.BOLD);
    static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static final Typeface SERIF = Typeface.create(Typeface.SERIF, Typeface.NORMAL);
    static final Typeface SERIF_BOLD = Typeface.create(Typeface.SERIF, Typeface.BOLD);

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static TextView text(Context c, String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView oneLine(Context c, String s, float sp, int color, Typeface tf) {
        TextView t = text(c, s, sp, color, tf);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /** iOS grouped-table section header: small, uppercase, grey. */
    static TextView header(Context c, String s) {
        TextView t = text(c, s.toUpperCase(new java.util.Locale("tr")), 13, LABEL_2, REGULAR);
        t.setPadding(dp(c, 4), 0, 0, dp(c, 10));
        return t;
    }

    /** Section title used in the library ("Okumaya Devam Et"). */
    static TextView title2(Context c, String s) {
        TextView t = text(c, s, 22, LABEL, BOLD);
        t.setPadding(0, 0, 0, dp(c, 14));
        return t;
    }

    static GradientDrawable round(int fill, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusPx);
        return g;
    }

    static StateListDrawable pressable(int fill, int pressed, float radiusPx) {
        StateListDrawable st = new StateListDrawable();
        st.addState(new int[]{android.R.attr.state_pressed}, round(pressed, radiusPx));
        st.addState(new int[]{}, round(fill, radiusPx));
        return st;
    }

    /** iOS 15 button styles: filled (accent background) or tinted (pale accent background, accent text). */
    static TextView button(Context c, String s, boolean filled) {
        TextView t = text(c, s, 16, filled ? 0xFF000000 : ACCENT, MEDIUM);
        t.setGravity(Gravity.CENTER);
        float r = dp(c, 12);
        t.setBackgroundDrawable(filled ? pressable(ACCENT, 0xFFD98608, r) : pressable(ACCENT_TINT, 0x55FF9F0A, r));
        t.setPadding(dp(c, 18), dp(c, 11), dp(c, 18), dp(c, 11));
        t.setClickable(true);
        return t;
    }

    /** Plain accent-coloured text button, like a navigation-bar item. */
    static TextView link(Context c, String s) {
        TextView t = text(c, s, 17, ACCENT, REGULAR);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(c, 10), dp(c, 10), dp(c, 10), dp(c, 10));
        t.setBackgroundDrawable(pressable(0, 0x22FFFFFF, dp(c, 10)));
        t.setClickable(true);
        return t;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static View space(Context c, int wDp, int hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, wDp), dp(c, hDp)));
        return v;
    }

    static View hairline(Context c) {
        View v = new View(c);
        v.setBackgroundColor(SEPARATOR);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lpw(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    static String percent(float p) {
        return "%" + (int) Math.floor(p);
    }

    private static final int[][] COVER_COLORS = {
            {0xFF5E5CE6, 0xFF2B2A6B}, {0xFF30B0C7, 0xFF124A55}, {0xFFFF6961, 0xFF6B2420},
            {0xFF34C759, 0xFF14521F}, {0xFFFF9F0A, 0xFF6B3F00}, {0xFFBF5AF2, 0xFF4D1F66}};

    /** A clean typographic cover for books without one (and PDFs before their first page is rendered). */
    static Bitmap fallbackCover(String title, String author, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas cv = new Canvas(b);
        int[] col = COVER_COLORS[Math.abs((title + author).hashCode()) % COVER_COLORS.length];
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w * 0.4f, h, col[0], col[1], Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setColor(0x26FFFFFF);
        cv.drawRect(new RectF(0, 0, w * 0.035f, h), p);

        float m = w * 0.1f;
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(0xFFFFFFFF);
        tp.setTypeface(SERIF_BOLD);
        tp.setTextSize(w * 0.115f);
        int tw = (int) (w - 2 * m);
        StaticLayout sl = new StaticLayout(TextUtils.ellipsize(title, tp, tw * 4, TextUtils.TruncateAt.END),
                tp, tw, Layout.Alignment.ALIGN_NORMAL, 1.05f, 0, false);
        cv.save();
        cv.translate(m, h * 0.14f);
        sl.draw(cv);
        cv.restore();

        tp.setTypeface(REGULAR);
        tp.setColor(0xCCFFFFFF);
        tp.setTextSize(w * 0.07f);
        StaticLayout al = new StaticLayout(TextUtils.ellipsize(author, tp, tw * 2, TextUtils.TruncateAt.END),
                tp, tw, Layout.Alignment.ALIGN_NORMAL, 1f, 0, false);
        cv.save();
        cv.translate(m, h * 0.86f - al.getHeight());
        al.draw(cv);
        cv.restore();
        return b;
    }
}
