// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.kadd0.sayfa;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Decodes covers off the UI thread, sized for display, and keeps recent ones in memory. */
final class Covers {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(
            (int) Math.min(24L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 6)) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
            return b.getRowBytes() * b.getHeight();
        }
    };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler UI = new Handler(Looper.getMainLooper());

    private Covers() {}

    private static String key(Context c, Library.Book b, int h) {
        File f = Library.coverFile(c, b.id);
        return b.id + ":" + (f.exists() ? f.lastModified() : 0) + ":" + h;
    }

    static void load(final Context c, final Library.Book b, final CoverView v, final int w, final int h) {
        final String key = key(c, b, h);
        v.tag = key;
        Bitmap hit = CACHE.get(key);
        if (hit != null) {
            v.setBitmap(hit);
            return;
        }
        v.setBitmap(null);
        final Context app = c.getApplicationContext();
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bm = decode(app, b, w, h);
                if (bm == null) return;
                CACHE.put(key, bm);
                UI.post(new Runnable() {
                    @Override
                    public void run() {
                        if (key.equals(v.tag)) v.setBitmap(bm);
                    }
                });
            }
        });
    }

    private static Bitmap decode(Context c, Library.Book b, int w, int h) {
        File f = Library.coverFile(c, b.id);
        if (f.exists()) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            o.inJustDecodeBounds = false;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            o.inSampleSize = 1;
            while (o.outHeight / (o.inSampleSize * 2) >= h) o.inSampleSize *= 2;
            Bitmap bm = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (bm != null) return bm;
        }
        return Ui.fallbackCover(b.title, b.author, Math.max(160, w), Math.max(240, h));
    }

    static void forget(String bookId) {
        for (String k : CACHE.snapshot().keySet()) if (k.startsWith(bookId + ":")) CACHE.remove(k);
    }
}
