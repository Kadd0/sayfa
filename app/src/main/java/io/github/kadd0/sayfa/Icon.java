// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.kadd0.sayfa;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Line icons drawn in code (SF Symbols look-alikes), so the APK needs no image assets. */
@SuppressLint("ViewConstructor")  // only ever created from code
final class Icon extends View {
    static final int BACK = 0, LIST = 1, TEXT = 2, BOOKMARK = 3, SPEAKER = 4, CLOSE = 5, BOOKMARK_FILLED = 6;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF arc = new RectF();
    private int kind;

    Icon(Context c, int kind, int color) {
        super(c);
        this.kind = kind;
        p.setColor(color);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        setClickable(true);
        setBackgroundDrawable(Ui.pressable(0, 0x22FFFFFF, Ui.dp(c, 10)));
    }

    void setKind(int k) {
        kind = k;
        invalidate();
    }

    void setColor(int color) {
        p.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h) * 0.5f, cx = w / 2, cy = h / 2;
        p.setStrokeWidth(s * 0.13f);
        p.setStyle(Paint.Style.STROKE);
        path.reset();
        switch (kind) {
            case BACK:
                path.moveTo(cx + s * 0.22f, cy - s * 0.5f);
                path.lineTo(cx - s * 0.28f, cy);
                path.lineTo(cx + s * 0.22f, cy + s * 0.5f);
                c.drawPath(path, p);
                break;
            case LIST:
                for (int i = -1; i <= 1; i++) {
                    float y = cy + i * s * 0.38f;
                    c.drawLine(cx - s * 0.25f, y, cx + s * 0.55f, y, p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx - s * 0.55f, y, s * 0.09f, p);
                    p.setStyle(Paint.Style.STROKE);
                }
                break;
            case TEXT:
                p.setStyle(Paint.Style.FILL);
                p.setTypeface(Ui.REGULAR);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(s * 0.8f);
                c.drawText("A", cx - s * 0.33f, cy + s * 0.32f, p);
                p.setTextSize(s * 1.25f);
                c.drawText("A", cx + s * 0.28f, cy + s * 0.45f, p);
                break;
            case BOOKMARK:
            case BOOKMARK_FILLED:
                path.moveTo(cx - s * 0.4f, cy - s * 0.6f);
                path.lineTo(cx + s * 0.4f, cy - s * 0.6f);
                path.lineTo(cx + s * 0.4f, cy + s * 0.62f);
                path.lineTo(cx, cy + s * 0.3f);
                path.lineTo(cx - s * 0.4f, cy + s * 0.62f);
                path.close();
                if (kind == BOOKMARK_FILLED) p.setStyle(Paint.Style.FILL_AND_STROKE);
                c.drawPath(path, p);
                break;
            case SPEAKER:
                p.setStyle(Paint.Style.FILL);
                path.moveTo(cx - s * 0.6f, cy - s * 0.2f);
                path.lineTo(cx - s * 0.3f, cy - s * 0.2f);
                path.lineTo(cx + s * 0.05f, cy - s * 0.52f);
                path.lineTo(cx + s * 0.05f, cy + s * 0.52f);
                path.lineTo(cx - s * 0.3f, cy + s * 0.2f);
                path.lineTo(cx - s * 0.6f, cy + s * 0.2f);
                path.close();
                c.drawPath(path, p);
                p.setStyle(Paint.Style.STROKE);
                arc.set(cx - s * 0.2f, cy - s * 0.35f, cx + s * 0.5f, cy + s * 0.35f);
                c.drawArc(arc, -50, 100, false, p);
                arc.set(cx - s * 0.25f, cy - s * 0.65f, cx + s * 0.8f, cy + s * 0.65f);
                c.drawArc(arc, -50, 100, false, p);
                break;
            case CLOSE:
                c.drawLine(cx - s * 0.4f, cy - s * 0.4f, cx + s * 0.4f, cy + s * 0.4f, p);
                c.drawLine(cx + s * 0.4f, cy - s * 0.4f, cx - s * 0.4f, cy + s * 0.4f, p);
                break;
        }
    }
}
