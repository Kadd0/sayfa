// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.kadd0.sayfa;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** EPUB reader: each chapter is laid out as pages (two side by side in landscape). */
public final class ReaderActivity extends BookActivity {
    /** Same order as {@link BookActivity#FONT_NAMES}; null keeps the book's own fonts. */
    private static final String[] FONTS = {"'SayfaLiterata', serif", "'SayfaCrimson', serif", "Roboto, 'Droid Sans', sans-serif", null};
    /** Crimson's letters are small for their size, so it gets a little more. */
    private static final int[] FONT_SIZES = {0, 112, 0, 0};

    private File dir;
    private String readerJs;
    private int chapter, page, pages = 1;
    private float pendingFrac;
    private String pendingAnchor;

    @Override
    void start() {
        dir = Library.bookDir(this, book.id);
        readerJs = asset("hyph.js") + "\n" + asset("reader.js");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                onJsLink(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                if (url != null && url.startsWith("file:")) web.evaluateJavascript(readerJs + "\nsy.init(" + config() + ");", null);
            }
        });
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Epub.extract(new File(book.path), dir);
                    Dict.install(ReaderActivity.this);
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ReaderActivity.this, "Kitap açılamadı: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            finish();
                        }
                    });
                    return;
                }
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        openChapter(Math.min(book.chapter, book.spine.size() - 1), book.frac, null);
                    }
                });
            }
        }).start();
    }

    private String config() {
        try {
            JSONObject cfg = new JSONObject();
            cfg.put("bg", css(color(0))).put("fg", css(color(1))).put("link", css(color(3)))
                    .put("hl", css(color(4))).put("lh", 1.55).put("frac", pendingFrac)
                    .put("spread", prefs.getBoolean("spread", true));
            int f = Math.min(FONTS.length - 1, prefs.getInt("font", 0));
            if (FONTS[f] != null) cfg.put("font", FONTS[f]);
            if (FONT_SIZES[f] > 0) cfg.put("size", FONT_SIZES[f]);
            if (pendingAnchor != null) cfg.put("anchor", pendingAnchor);
            return cfg.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private void openChapter(int index, float frac, String anchor) {
        if (index < 0 || index >= book.spine.size()) return;
        hideWord();
        chapter = index;
        pendingFrac = frac;
        pendingAnchor = anchor;
        hidePage();
        web.loadUrl(Uri.fromFile(new File(dir, book.spine.get(index))).toString());
        header.setText(book.titles.get(index));
    }

    private float frac() {
        return pages > 1 ? (float) page / pages : 0f;
    }

    @Override
    void next() {
        if (wordShown()) hideWord();
        else js("sy.next()");
    }

    @Override
    void prev() {
        if (wordShown()) hideWord();
        else js("sy.prev()");
    }

    @Override
    void seekTo(float percent) {
        float[] at = book.locate(percent);
        openChapter((int) at[0], at[1], null);
    }

    @Override
    String describe(float percent) {
        float[] at = book.locate(percent);
        String t = book.titles.get((int) at[0]);
        return Ui.percent(percent) + (t.length() > 0 ? "   ·   " + t : "");
    }

    @Override
    void relayout() {
        forgetSnapshot();
        js("sy.relayout(" + frac() + ")");
    }

    /** KitKat's WebView keeps the old zoom after rotating, so reload the chapter at the same spot instead. */
    @Override
    void rotated() {
        forgetSnapshot();
        openChapter(chapter, frac(), null);
    }

    @Override
    void restyle() {
        forgetSnapshot();
        openChapter(chapter, frac(), null);
    }

    @Override
    void onJsReady(int p, int n) {
        pageReady();
        onJsPage(p, n);
    }

    @Override
    void onJsPage(int p, int n) {
        page = p;
        pages = Math.max(1, n);
        Library.saveProgress(this, book, chapter, frac(), (p + 1f) / pages);
        showPosition(String.format(Locale.ROOT, "Bölüm %d / %d   ·   Sayfa %d / %d", chapter + 1, book.spine.size(), p + 1, pages),
                book.percent);
        stripFollow();
    }

    @Override
    void onJsEdge(int dir) {
        if (dir > 0 && chapter < book.spine.size() - 1) openChapter(chapter + 1, 0f, null);
        else if (dir < 0 && chapter > 0) openChapter(chapter - 1, 1f, null);
        else if (dir > 0) Toast.makeText(this, "Kitabın sonu", Toast.LENGTH_SHORT).show();
    }

    @Override
    void onJsLink(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            // The book decides what this link says, so ask before leaving the app.
            final Uri target = Uri.parse(url);
            new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setTitle("Bağlantıyı aç?")
                    .setMessage(target.getHost())
                    .setNegativeButton("Vazgeç", null)
                    .setPositiveButton("Tarayıcıda aç", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int which) {
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, target));
                            } catch (Exception ignored) {
                                // No browser installed.
                            }
                        }
                    }).show();
            return;
        }
        if (!url.startsWith("file:")) return;
        Uri u = Uri.parse(url);
        String path = u.getPath(), frag = u.getFragment(), rootPath = dir.getAbsolutePath() + "/";
        if (path == null || !path.startsWith(rootPath)) return;
        int idx = book.spine.indexOf(path.substring(rootPath.length()));
        if (idx < 0) return;
        final int backChapter = chapter, backPage = page, backPages = pages;
        final float backFrac = frac();
        showReturn(new Runnable() {
            @Override
            public void run() {
                if (backChapter == chapter) js("sy.goPage(" + backPage + "," + backPages + ")");
                else openChapter(backChapter, backFrac, null);
            }
        });
        if (idx == chapter && frag != null) js("sy.anchor(" + JSONObject.quote(frag) + ")");
        else openChapter(idx, 0f, frag);
    }

    // ---- page overview strip (like WPS and the PDF reader): small pictures of this chapter's pages,
    // with the previous and next chapter at the ends. The pictures come from one snapshot of the laid-out
    // chapter, cut into pages on a background thread.

    private static final int TW = 120;
    private ListView strip;
    private BaseAdapter stripAdapter;
    private Picture shot;
    /** Geometry of the snapshot in CSS px: page width and height, gap between pages, content width; and its chapter. */
    private int geoW, geoH, geoG, geoScroll, geoPages, geoChapter = -1, shotToken;
    private boolean capturing;
    private final LruCache<Integer, Bitmap> thumbs =
            new LruCache<Integer, Bitmap>(40);
    private final Set<Integer> asked = new HashSet<Integer>();
    private HandlerThread painterThread;
    private Handler painter;

    @Override
    void sidebar(boolean show, int top, int bottom) {
        if (!show) {
            if (strip != null) strip.setVisibility(View.GONE);
            return;
        }
        if (strip == null) buildStrip();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(this, TW + 28),
                ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(0, top, 0, bottom);
        strip.setLayoutParams(lp);
        strip.setVisibility(View.VISIBLE);
        if (geoChapter != chapter || geoPages != pages || shot == null) capture();
        else refreshRows();
        centerCurrent();
    }

    /** The page pictures no longer match the page (new theme, font, size or orientation): retake them. */
    private void forgetSnapshot() {
        shot = null;
        geoChapter = -1;
    }

    private boolean stripShown() {
        return strip != null && strip.getVisibility() == View.VISIBLE;
    }

    /** The rows show this chapter and page count; they change only together with notifyDataSetChanged(). */
    private int listChapter, listPages = 1;

    private void refreshRows() {
        listChapter = chapter;
        listPages = pages;
        stripAdapter.notifyDataSetChanged();
    }

    /** Keep the rows in step with page turns and new chapters; while open, keep the current page in view. */
    private void stripFollow() {
        if (strip == null) return;
        refreshRows();
        if (!stripShown()) return;
        if (geoChapter != chapter || geoPages != pages) {
            if (!capturing) capture();
            return;
        }
        refreshRows();
        int row = rowOf(page);
        if (row <= strip.getFirstVisiblePosition() || row >= strip.getLastVisiblePosition()) centerCurrent();
    }

    private int hasPrev() {
        return listChapter > 0 ? 1 : 0;
    }

    /** List position of page p: the previous-chapter row and the chapter title come first. */
    private int rowOf(int p) {
        return hasPrev() + 1 + p;
    }

    private void centerCurrent() {
        int h = strip.getHeight() > 0 ? strip.getHeight() : web.getHeight();
        strip.setSelectionFromTop(rowOf(page), Math.max(0, h / 2 - thumbHeight() / 2));
    }

    /** Pictures show the whole screen page, margins included. */
    private int thumbHeight() {
        return Math.round(Ui.dp(this, TW) * (float) root.getHeight() / Math.max(1, root.getWidth()));
    }

    private void buildStrip() {
        painterThread = new HandlerThread("thumbs", Process.THREAD_PRIORITY_BACKGROUND);
        painterThread.start();
        painter = new Handler(painterThread.getLooper());
        strip = new ListView(this);
        strip.setDivider(null);
        strip.setSelector(new ColorDrawable(0));
        strip.setVerticalScrollBarEnabled(false);
        strip.setBackgroundColor(0xFF1C1C1E);
        strip.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        strip.setClipToPadding(false);
        stripAdapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return hasPrev() + 1 + listPages + (listChapter < book.spine.size() - 1 ? 1 : 0);
            }

            @Override
            public Object getItem(int i) {
                return i;
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public int getViewTypeCount() {
                return 2;
            }

            @Override
            public int getItemViewType(int i) {
                int p = i - hasPrev() - 1;
                return p >= 0 && p < listPages ? 1 : 0;
            }

            @Override
            public boolean isEnabled(int i) {
                return i != hasPrev();
            }

            @Override
            public View getView(int i, View old, ViewGroup parent) {
                int p = i - hasPrev() - 1;
                if (p >= 0 && p < listPages) return pageRow(p, old);
                TextView t = old instanceof TextView ? (TextView) old
                        : Ui.text(ReaderActivity.this, "", 13, 0, Ui.REGULAR);
                t.setPadding(Ui.dp(ReaderActivity.this, 14), Ui.dp(ReaderActivity.this, 8),
                        Ui.dp(ReaderActivity.this, 10), Ui.dp(ReaderActivity.this, 14));
                t.setMaxLines(3);
                t.setEllipsize(TextUtils.TruncateAt.END);
                if (i == hasPrev()) {
                    t.setText(title(listChapter));
                    t.setTextColor(Ui.ACCENT);
                    t.setTypeface(Ui.MEDIUM);
                } else {
                    String near = book.titles.get(i == 0 ? listChapter - 1 : listChapter + 1);
                    if (i == 0) t.setText("‹  " + (near.length() > 0 ? near : "Önceki bölüm"));
                    else t.setText((near.length() > 0 ? near : "Sonraki bölüm") + "  ›");
                    t.setTextColor(Ui.LABEL_2);
                    t.setTypeface(Ui.REGULAR);
                }
                return t;
            }
        };
        strip.setAdapter(stripAdapter);
        strip.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> a, View v, int i, long id) {
                int p = i - hasPrev() - 1;
                if (p >= 0 && p < listPages) js("sy.goPage(" + p + "," + listPages + ")");
                else if (i == 0 && hasPrev() == 1) openChapter(listChapter - 1, 0f, null);
                else if (p >= listPages) openChapter(listChapter + 1, 0f, null);
            }
        });
        strip.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView v, int state) {
            }

            @Override
            public void onScroll(AbsListView v, int first, int count, int total) {
                // Draw the pictures just past the edges too, so they are ready when scrolled in.
                for (int i = Math.max(0, first - 2); i < first + count + 2; i++) ask(i - hasPrev() - 1);
            }
        });
        root.addView(strip);
    }

    private String title(int ch) {
        String t = ch >= 0 && ch < book.titles.size() ? book.titles.get(ch) : "";
        return t.length() > 0 ? t : "Bölüm " + (ch + 1);
    }

    /** A page picture with its number in the corner; the current page is outlined. */
    private View pageRow(int p, View old) {
        int b = Ui.dp(this, 3);
        FrameLayout row;
        if (old instanceof FrameLayout) row = (FrameLayout) old;
        else {
            row = new FrameLayout(this);
            row.setPadding(Ui.dp(this, 14) - b, 0, 0, Ui.dp(this, 14) - b);
            FrameLayout frame = new FrameLayout(this);
            frame.setPadding(b, b, b, b);
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.FIT_XY);
            frame.addView(img, new FrameLayout.LayoutParams(-1, -1));
            TextView badge = Ui.text(this, "", 12, Ui.LABEL, Ui.REGULAR);
            badge.setGravity(Gravity.CENTER);
            badge.setMinWidth(Ui.dp(this, 22));
            badge.setPadding(Ui.dp(this, 5), Ui.dp(this, 2), Ui.dp(this, 5), Ui.dp(this, 2));
            frame.addView(badge, new FrameLayout.LayoutParams(-2, -2,
                    Gravity.LEFT | Gravity.BOTTOM));
            row.addView(frame);
        }
        FrameLayout frame = (FrameLayout) row.getChildAt(0);
        frame.setLayoutParams(new FrameLayout.LayoutParams(Ui.dp(this, TW) + 2 * b, thumbHeight() + 2 * b));
        boolean here = p == page;
        frame.setBackgroundColor(here ? Ui.ACCENT : 0);
        ImageView img = (ImageView) frame.getChildAt(0);
        Bitmap bmp = thumbs.get(p);
        if (bmp != null) img.setImageBitmap(bmp);
        else {
            img.setImageDrawable(new ColorDrawable(color(0)));
            ask(p);
        }
        TextView badge = (TextView) frame.getChildAt(1);
        badge.setText(String.valueOf(p + 1));
        badge.setBackgroundColor(here ? Ui.ACCENT : 0xD93C3C43);
        return row;
    }

    /** Takes a fresh snapshot of the chapter; pictures are then cut from it as rows come into view. */
    private void capture() {
        final int token = ++shotToken;
        capturing = true;
        shot = null;
        geoChapter = -1;
        thumbs.evictAll();
        asked.clear();
        if (painter != null) painter.removeCallbacksAndMessages(null);
        refreshRows();
        web.evaluateJavascript("sy.geom()", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String v) {
                if (token != shotToken) return;
                capturing = false;
                if (!stripShown()) return;
                try {
                    JSONArray a = new JSONArray(v);
                    geoW = a.getInt(0);
                    geoH = a.getInt(1);
                    geoG = a.getInt(2);
                    geoScroll = a.getInt(3);
                    geoPages = a.getInt(4);
                } catch (Exception e) {
                    return;
                }
                geoChapter = chapter;
                shot = web.capturePicture();
                refreshRows();
                centerCurrent();
            }
        });
    }

    private void ask(final int p) {
        if (p < 0 || p >= geoPages || shot == null || geoChapter != chapter || asked.contains(p)) return;
        asked.add(p);
        final int token = shotToken, bg = color(0), tw = Ui.dp(this, TW), th = thumbHeight();
        final Picture pic = shot;
        final float k = pic.getWidth() / (float) Math.max(1, geoScroll), x = p * (geoW + geoG) * k, w = geoW * k, h = geoH * k;
        // Where the text area sits on the screen, scaled down to the picture.
        int[] at = new int[2], base = new int[2];
        web.getLocationOnScreen(at);
        root.getLocationOnScreen(base);
        final float s = tw / (float) Math.max(1, root.getWidth()), left = (at[0] - base[0]) * s, top = (at[1] - base[1]) * s,
                cw = web.getWidth() * s, ch = web.getHeight() * s;
        painter.post(new Runnable() {
            @Override
            public void run() {
                final Bitmap b = Bitmap.createBitmap(tw, th, Bitmap.Config.RGB_565);
                Canvas c = new Canvas(b);
                c.drawColor(bg);
                c.save();
                c.translate(left, top);
                c.scale(cw / w, ch / h);
                c.translate(-x, 0);
                c.clipRect(x, 0, x + w, h);
                pic.draw(c);
                c.restore();
                // The snapshot ignores the clip, so paint the margins over whatever spilled into them.
                Paint m = new Paint();
                m.setColor(bg);
                c.drawRect(0, 0, left, th, m);
                c.drawRect(left + cw, 0, tw, th, m);
                c.drawRect(0, 0, tw, top, m);
                c.drawRect(0, top + ch, tw, th, m);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (token != shotToken) return;
                        thumbs.put(p, b);
                        if (stripShown()) refreshRows();
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (painterThread != null) painterThread.quit();
        super.onDestroy();
    }

    @Override
    void showToc() {
        final List<String> names = new ArrayList<String>();
        final List<Integer> where = new ArrayList<Integer>();
        String last = null;
        int current = 0;
        for (int i = 0; i < book.titles.size(); i++) {
            String t = book.titles.get(i);
            if (t.length() > 0 && !t.equals(last)) {
                names.add(t);
                where.add(i);
            }
            if (i == chapter) current = Math.max(0, names.size() - 1);
            last = t;
        }
        if (names.isEmpty()) {
            Toast.makeText(this, "Bu kitapta içindekiler listesi yok", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("İçindekiler")
                .setSingleChoiceItems(names.toArray(new String[0]), current, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int i) {
                        d.dismiss();
                        toggleBars();
                        openChapter(where.get(i), 0f, null);
                    }
                }).show();
    }
}
