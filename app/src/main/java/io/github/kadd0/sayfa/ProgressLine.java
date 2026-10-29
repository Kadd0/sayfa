// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.kadd0.sayfa;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Thin rounded reading-progress bar. */
final class ProgressLine extends View {
    private final Paint back = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float value;

    ProgressLine(Context c) {
        super(c);
        back.setColor(Ui.FILL);
        fill.setColor(Ui.ACCENT);
    }

    void set(float fraction) {
        float v = Math.max(0, Math.min(1, fraction));
        if (v != value) {
            value = v;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float h = getHeight(), rad = h / 2f;
        r.set(0, 0, getWidth(), h);
        c.drawRoundRect(r, rad, rad, back);
        if (value > 0) {
            r.set(0, 0, Math.max(h, getWidth() * value), h);
            c.drawRoundRect(r, rad, rad, fill);
        }
    }
}
