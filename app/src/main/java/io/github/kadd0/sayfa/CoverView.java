// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.kadd0.sayfa;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Book cover with rounded corners and a soft drop shadow. The shadow is a few stacked translucent
 * rectangles instead of Paint.setShadowLayer, which would force slow software rendering on KitKat.
 */
final class CoverView extends View {
    private final Paint img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Matrix m = new Matrix();
    private Bitmap bitmap;
    private final float radius, pad;
    Object tag;

    CoverView(Context c) {
        super(c);
        radius = Ui.dp(c, 5);
        pad = Ui.dp(c, 6);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1);
        edge.setColor(0x22FFFFFF);
    }

    void setBitmap(Bitmap b) {
        bitmap = b;
        img.setShader(b == null ? null : new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth() - 2 * pad, h = getHeight() - 2 * pad;
        if (w <= 0 || h <= 0) return;
        for (int i = 4; i >= 1; i--) {
            shade.setColor((14 - i * 2) << 24);
            float grow = i * pad / 4f;
            r.set(pad - grow, pad - grow + pad * 0.5f, pad + w + grow, pad + h + grow + pad * 0.5f);
            c.drawRoundRect(r, radius + grow, radius + grow, shade);
        }
        r.set(pad, pad, pad + w, pad + h);
        if (bitmap == null) {
            shade.setColor(Ui.ELEVATED);
            c.drawRoundRect(r, radius, radius, shade);
            return;
        }
        float scale = Math.max(w / bitmap.getWidth(), h / bitmap.getHeight());
        m.setScale(scale, scale);
        m.postTranslate(pad + (w - bitmap.getWidth() * scale) / 2f, pad + (h - bitmap.getHeight() * scale) / 2f);
        img.getShader().setLocalMatrix(m);
        c.drawRoundRect(r, radius, radius, img);
        c.drawRoundRect(r, radius, radius, edge);
    }

    /** Space the shadow needs around the cover itself. */
    float padding() {
        return pad;
    }
}
