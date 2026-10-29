// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.kadd0.sayfa;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Locale;

/** PDF reader: pages are drawn by pdf.js with a text layer, so word taps work here too. */
public final class PdfActivity extends BookActivity {
    private int page, pages;

    @Override
    boolean textOptions() {
        return false;
    }

    @Override
    boolean scriptReadsFiles() {
        return true;
    }

    @Override
    void start() {
        // The page border is part of the PDF, so trim our own margins.
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) web.getLayoutParams();
        lp.setMargins(Ui.dp(this, 8), Ui.dp(this, 30), Ui.dp(this, 8), Ui.dp(this, 30));
        web.setLayoutParams(lp);
        page = book.chapter;
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                web.evaluateJavascript("pv.init(" + config(true) + ");", null);
            }
        });
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Dict.install(PdfActivity.this);
                } catch (Exception ignored) {
                    // Lookups then report "not found".
                }
            }
        }).start();
        web.loadUrl("file:///android_asset/pdf.html");
    }

    private String config(boolean withFile) {
        try {
            JSONObject c = new JSONObject();
            c.put("bg", css(color(0))).put("hl", css(color(4))).put("spread", prefs.getBoolean("spread", true))
                    .put("dark", theme() >= 2);
            if (withFile) c.put("url", Uri.fromFile(new File(book.path)).toString()).put("page", page);
            return c.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    @Override
    void next() {
        if (wordShown()) hideWord();
        else js("pv.next()");
    }

    @Override
    void prev() {
        if (wordShown()) hideWord();
        else js("pv.prev()");
    }

    @Override
    void seekTo(float percent) {
        if (pages > 0) js("pv.go(" + Math.min(pages - 1, (int) (pages * percent / 100f)) + ")");
    }

    @Override
    String describe(float percent) {
        if (pages == 0) return "";
        // The stored % counts the current page as read, so it maps back to the page itself;
        // a dragged slider value maps to the page seekTo() would open.
        int p = Math.abs(percent - book.percent) < 0.001f ? page + 1 : Math.min(pages, (int) (pages * percent / 100f) + 1);
        return String.format(Locale.ROOT, "Sayfa %d / %d", p, pages);
    }

    @Override
    void relayout() {
        js("pv.relayout()");
    }

    @Override
    void restyle() {
        js("pv.style(" + config(false) + ")");
    }

    @Override
    void onJsReady(int p, int n) {
        pageReady();
        onJsPage(p, n);
    }

    @Override
    void onJsPage(int p, int n) {
        page = p;
        pages = n;
        Library.saveRaw(this, book, p, 0f, n == 0 ? 0 : 100f * (p + 1) / n);
        showPosition(String.format(Locale.ROOT, "Sayfa %d / %d", p + 1, n), book.percent);
    }

    @Override
    void onJsEdge(int dir) {
        if (dir > 0) Toast.makeText(this, "Kitabın sonu", Toast.LENGTH_SHORT).show();
    }

    @Override
    void showToc() {
        js("pv.outline()");
    }

    /** Page previews on the left, between the top and bottom bars (sizes converted to CSS pixels). */
    @Override
    void sidebar(boolean show, int top, int bottom) {
        float d = getResources().getDisplayMetrics().density;
        int t = Math.max(0, Math.round((top - web.getTop()) / d));
        int b = Math.max(0, Math.round((bottom - (root.getHeight() - web.getBottom())) / d));
        js("pv.strip(" + show + "," + t + "," + b + ")");
    }

    @Override
    void onJsToc(String json) {
        try {
            final JSONArray a = new JSONArray(json);
            if (a.length() == 0) {
                Toast.makeText(this, "Bu PDF'te içindekiler listesi yok", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] names = new String[a.length()];
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                names[i] = o.getString("title") + "   ·   " + (o.getInt("page") + 1);
            }
            new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setTitle("İçindekiler")
                    .setItems(names, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int i) {
                            try {
                                toggleBars();
                                js("pv.go(" + a.getJSONObject(i).getInt("page") + ")");
                            } catch (Exception ignored) {
                                // Malformed entry: stay on the current page.
                            }
                        }
                    }).show();
        } catch (Exception e) {
            Toast.makeText(this, "İçindekiler okunamadı", Toast.LENGTH_SHORT).show();
        }
    }
}
