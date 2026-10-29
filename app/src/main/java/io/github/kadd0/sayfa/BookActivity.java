// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.kadd0.sayfa;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.text.Html;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Locale;

/**
 * Everything the EPUB and PDF readers share: the page WebView, menus, the "Aa" panel,
 * the word sheet with Turkish meanings, read-aloud, volume-key paging and rotation.
 */
abstract class BookActivity extends Activity {
    static final String[] THEME_NAMES = {"Orijinal", "Sepya", "Sakin", "Gece"};
    static final String[] FONT_NAMES = {"Literata", "Garamond", "Sans", "Kitabın"};
    // page, text, secondary text, link, word highlight
    static final int[][] THEMES = {
            {0xFFFFFFFF, 0xFF1C1C1E, 0xFF8E8E93, 0xFF0A63D6, 0x66FFCC00},
            {0xFFF6EEDF, 0xFF3D3021, 0xFF9A8B74, 0xFF9A5B12, 0x80F2C35B},
            {0xFF3A3A3C, 0xFFE5E5EA, 0xFF98989D, 0xFFFFB340, 0x66FF9F0A},
            {0xFF000000, 0xFFC7C7CC, 0xFF636366, 0xFFFF9F0A, 0x66FF9F0A}};

    final Handler ui = new Handler();
    SharedPreferences prefs;
    Library.Book book;

    FrameLayout root;
    WebView web;
    TextView header, footerLeft, footerRight, loading;
    private LinearLayout topBar, bottomBar;
    private TextView barTitle;
    private Icon backIcon, tocIcon, aaIcon, wordsIcon;
    private SeekBar seek;
    private TextView seekLabel;
    private View scrim;
    private LinearLayout card, panel;
    boolean ready;
    private TextToSpeech tts;
    private boolean ttsOk;
    private float seekPreview = -1;

    // ---- what each reader supplies

    /** Load the book; call {@link #pageReady} when the first page is on screen. */
    abstract void start();

    abstract void next();

    abstract void prev();

    /** Jump to a share (0–100) of the book. */
    abstract void seekTo(float percent);

    /** Text for the slider while dragging to `percent`. */
    abstract String describe(float percent);

    /** Re-run layout after rotation or a font-size change. */
    abstract void relayout();

    /** Reload with new colours/fonts. */
    abstract void restyle();

    abstract void showToc();

    /** Whether this reader can change fonts and the two-page spread (PDF pages cannot). */
    boolean textOptions() {
        return true;
    }

    // ---------------------------------------------------------------- setup

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("reader", MODE_PRIVATE);
        book = Library.find(this, getIntent().getStringExtra("id"));
        if (book == null || !new File(book.path).exists()) {
            Toast.makeText(this, "Kitap bulunamadı", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        buildViews();
        applyTheme();
        start();
    }

    int theme() {
        return Math.min(THEMES.length - 1, prefs.getInt("theme", 1));
    }

    int color(int i) {
        return THEMES[theme()][i];
    }

    static String css(int c) {
        return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.2f)", (c >> 16) & 255, (c >> 8) & 255, c & 255, ((c >>> 24) & 255) / 255f);
    }

    String asset(String name) {
        try {
            InputStream in = getAssets().open(name);
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            in.close();
            return b.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface", "ClickableViewAccessibility"})
    private void buildViews() {
        root = new FrameLayout(this);
        int side = Ui.dp(this, 40), top = Ui.dp(this, 38), bottom = Ui.dp(this, 40);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        // Book pages are untrusted (an EPUB can carry its own scripts): they may load their own pictures and
        // styles, but never reach the internet or read other files. Only the PDF viewer page, which is ours,
        // reads the book file through script.
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(scriptReadsFiles());
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setBlockNetworkLoads(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setUseWideViewPort(false);
        s.setDefaultTextEncodingName("utf-8");
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setDatabaseEnabled(false);
        s.setSaveFormData(false);
        s.setGeolocationEnabled(false);
        s.setTextZoom(prefs.getInt("zoom", 120));
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;  // no text-selection toolbar
            }
        });
        web.setHapticFeedbackEnabled(false);
        web.addJavascriptInterface(new Bridge(), "SY");
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage m) {
                Log.i("SayfaJS", m.message() + " @" + m.sourceId() + ":" + m.lineNumber());
                return true;
            }
        });
        web.setAlpha(0f);
        FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        wl.setMargins(side, top, side, bottom);
        root.addView(web, wl);

        header = Ui.oneLine(this, "", 13, 0, Ui.REGULAR);
        header.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, top, Gravity.TOP);
        hl.setMargins(side * 3, 0, side * 3, 0);
        root.addView(header, hl);

        footerLeft = Ui.text(this, "", 12, 0, Ui.REGULAR);
        footerLeft.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, bottom, Gravity.BOTTOM | Gravity.LEFT);
        fl.setMargins(side, 0, 0, 0);
        root.addView(footerLeft, fl);
        footerRight = Ui.text(this, "", 12, 0, Ui.MEDIUM);
        footerRight.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams fr = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, bottom, Gravity.BOTTOM | Gravity.RIGHT);
        fr.setMargins(0, 0, side, 0);
        root.addView(footerRight, fr);

        loading = Ui.text(this, "Açılıyor…", 15, 0, Ui.REGULAR);
        root.addView(loading, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        // Touches that land in the page margins: swipe or tap the sides to turn pages, tap top/bottom for menus.
        root.setOnTouchListener(new View.OnTouchListener() {
            float x0, y0;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    x0 = e.getX();
                    y0 = e.getY();
                } else if (e.getAction() == MotionEvent.ACTION_UP) {
                    float dx = e.getX() - x0;
                    if (Math.abs(dx) > Ui.dp(BookActivity.this, 40)) {
                        if (dx < 0) next(); else prev();
                    } else if (Math.abs(dx) < 20 && Math.abs(e.getY() - y0) < 20) {
                        v.performClick();  // lets accessibility services see the tap
                        tapAt(e.getX() / root.getWidth());
                    }
                }
                return true;
            }
        });

        buildBars();
        setContentView(root);
    }

    private void buildBars() {
        int icon = Ui.dp(this, 44);
        topBar = Ui.row(this);
        topBar.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        topBar.setClickable(true);
        backIcon = new Icon(this, Icon.BACK, Ui.ACCENT);
        backIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        topBar.addView(backIcon, new LinearLayout.LayoutParams(icon, icon));
        // The bar is narrow in portrait: the main title only ("Moonwalking with Einstein", not its subtitle).
        int colon = book.title.indexOf(": ");
        barTitle = Ui.oneLine(this, colon > 0 ? book.title.substring(0, colon) : book.title, 17, 0, Ui.MEDIUM);
        barTitle.setGravity(Gravity.CENTER);
        barTitle.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        topBar.addView(barTitle, Ui.lpw(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        tocIcon = new Icon(this, Icon.LIST, Ui.ACCENT);
        tocIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showToc();
            }
        });
        topBar.addView(tocIcon, new LinearLayout.LayoutParams(icon, icon));
        aaIcon = new Icon(this, Icon.TEXT, Ui.ACCENT);
        aaIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePanel();
            }
        });
        topBar.addView(aaIcon, new LinearLayout.LayoutParams(icon, icon));
        wordsIcon = new Icon(this, Icon.BOOKMARK, Ui.ACCENT);
        wordsIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(BookActivity.this, WordsActivity.class));
            }
        });
        topBar.addView(wordsIcon, new LinearLayout.LayoutParams(icon, icon));
        topBar.setVisibility(View.GONE);
        root.addView(topBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        bottomBar = Ui.column(this);
        bottomBar.setPadding(Ui.dp(this, 40), Ui.dp(this, 12), Ui.dp(this, 40), Ui.dp(this, 14));
        bottomBar.setClickable(true);
        seek = new SeekBar(this);
        seek.setMax(1000);
        seek.setProgressDrawable(seekTrack());
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(0xFFFFFFFF);
        thumb.setStroke(Ui.dp(this, 1), 0x33000000);
        thumb.setSize(Ui.dp(this, 24), Ui.dp(this, 24));
        seek.setThumb(thumb);
        seek.setThumbOffset(Ui.dp(this, 12));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int v, boolean user) {
                if (user) {
                    seekPreview = v / 10f;
                    seekLabel.setText(describe(seekPreview));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                seekPreview = -1;
                seekTo(s.getProgress() / 10f);
            }
        });
        bottomBar.addView(seek, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 40)));
        seekLabel = Ui.text(this, "", 13, 0, Ui.REGULAR);
        seekLabel.setGravity(Gravity.CENTER);
        bottomBar.addView(seekLabel, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bottomBar.setVisibility(View.GONE);
        root.addView(bottomBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    }

    private Drawable seekTrack() {
        GradientDrawable back = Ui.round(0x33808080, Ui.dp(this, 2));
        GradientDrawable fill = Ui.round(Ui.ACCENT, Ui.dp(this, 2));
        ClipDrawable clip = new ClipDrawable(fill, Gravity.LEFT, ClipDrawable.HORIZONTAL);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{back, clip});
        ld.setId(0, android.R.id.background);
        ld.setId(1, android.R.id.progress);
        int inset = Ui.dp(this, 18);
        ld.setLayerInset(0, 0, inset, 0, inset);
        ld.setLayerInset(1, 0, inset, 0, inset);
        return ld;
    }

    void applyTheme() {
        int bg = color(0);
        root.setBackgroundColor(bg);
        getWindow().setBackgroundDrawable(new ColorDrawable(bg));
        web.setBackgroundColor(bg);
        header.setTextColor(color(2));
        footerLeft.setTextColor(color(2));
        footerRight.setTextColor(color(2));
        loading.setTextColor(color(2));
        int bar = (bg & 0x00FFFFFF) | 0xF7000000;
        topBar.setBackgroundColor(bar);
        bottomBar.setBackgroundColor(bar);
        barTitle.setTextColor(color(1));
        seekLabel.setTextColor(color(2));
        int accent = color(3);
        backIcon.setColor(accent);
        tocIcon.setColor(accent);
        aaIcon.setColor(accent);
        wordsIcon.setColor(accent);
    }

    // ---------------------------------------------------------------- page state

    /** Called by readers whenever the visible page changes. */
    void showPosition(String left, float percent) {
        footerLeft.setText(left);
        footerRight.setText(Ui.percent(percent));
        if (seekPreview < 0) {
            seek.setProgress(Math.round(percent * 10));
            seekLabel.setText(describe(percent));
        }
    }

    void pageReady() {
        ready = true;
        loading.setVisibility(View.GONE);
        web.animate().alpha(1f).setDuration(150).start();
    }

    void hidePage() {
        ready = false;
        web.animate().cancel();
        web.setAlpha(0f);
    }

    void js(String code) {
        if (ready) web.evaluateJavascript(code, null);
    }

    /** A tap that hit no word: sides turn pages, the middle toggles the menus. */
    void tapAt(float x) {
        if (card != null) hideWord();
        else if (panel != null) togglePanel();
        else if (topBar.getVisibility() == View.VISIBLE) toggleBars();
        else if (x < 0.25f) prev();
        else if (x > 0.75f) next();
        else toggleBars();
    }

    void toggleBars() {
        final boolean show = topBar.getVisibility() != View.VISIBLE;
        if (!show && panel != null) togglePanel();
        fade(topBar, show);
        fade(bottomBar, show);
        if (!show) {
            sidebar(false, 0, 0);
            return;
        }
        // Wait one layout pass so the bars have their real heights.
        ui.post(new Runnable() {
            @Override
            public void run() {
                sidebar(true, topBar.getHeight(), bottomBar.getHeight());
            }
        });
    }

    boolean barsShown() {
        return topBar.getVisibility() == View.VISIBLE;
    }

    /** The page-overview strip on the left, shown together with the menus. `top`/`bottom`: bar heights in px. */
    void sidebar(boolean show, int top, int bottom) {
    }

    private static void fade(final View v, boolean show) {
        v.animate().cancel();
        if (show) {
            v.setAlpha(0f);
            v.setVisibility(View.VISIBLE);
            v.animate().alpha(1f).setDuration(160).setListener(null).start();
        } else {
            v.animate().alpha(0f).setDuration(140).withEndAction(new Runnable() {
                @Override
                public void run() {
                    v.setVisibility(View.GONE);
                }
            }).start();
        }
    }

    // ---------------------------------------------------------------- "Aa" panel

    private void togglePanel() {
        if (panel != null) {
            root.removeView(panel);
            panel = null;
            return;
        }
        panel = Ui.column(this);
        panel.setClickable(true);
        panel.setBackgroundDrawable(Ui.round(Ui.ELEVATED, Ui.dp(this, 16)));
        int p = Ui.dp(this, 18);
        panel.setPadding(p, p, p, p);

        if (textOptions()) {
            LinearLayout size = segmented();
            TextView smaller = segment(size, "A", 15, true);
            smaller.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    zoom(-10);
                }
            });
            TextView bigger = segment(size, "A", 24, false);
            bigger.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    zoom(10);
                }
            });
            panel.addView(size, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));
            panel.addView(Ui.space(this, 1, 18));
        }
        LinearLayout themes = Ui.row(this);
        themes.setGravity(Gravity.CENTER);
        for (int i = 0; i < THEMES.length; i++) {
            final int t = i;
            LinearLayout col = Ui.column(this);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView dot = Ui.text(this, "Aa", 16, THEMES[i][1], Ui.SERIF);
            dot.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(THEMES[i][0]);
            g.setStroke(Ui.dp(this, i == theme() ? 3 : 1), i == theme() ? Ui.ACCENT : 0x55FFFFFF);
            dot.setBackgroundDrawable(g);
            col.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));
            TextView name = Ui.text(this, THEME_NAMES[i], 12, i == theme() ? Ui.LABEL : Ui.LABEL_2, Ui.REGULAR);
            name.setPadding(0, Ui.dp(this, 6), 0, 0);
            col.addView(name);
            col.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.edit().putInt("theme", t).apply();
                    applyTheme();
                    togglePanel();
                    togglePanel();
                    restyle();
                }
            });
            themes.addView(col, Ui.lpw(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        panel.addView(themes, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (textOptions()) {
            panel.addView(Ui.space(this, 1, 18));
            LinearLayout fonts = segmented();
            String[] names = FONT_NAMES;
            for (int i = 0; i < names.length; i++) {
                final int f = i;
                TextView t = segment(fonts, names[i], 15, i == 0);
                boolean on = prefs.getInt("font", 0) == i;
                t.setTextColor(on ? Ui.ACCENT : Ui.LABEL);
                t.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        prefs.edit().putInt("font", f).apply();
                        togglePanel();
                        togglePanel();
                        restyle();
                    }
                });
            }
            panel.addView(fonts, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));
        }

        panel.addView(Ui.space(this, 1, 18));
        LinearLayout spreadRow = Ui.row(this);
        spreadRow.addView(Ui.text(this, "Yatayda çift sayfa", 16, Ui.LABEL, Ui.REGULAR), Ui.lpw(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView toggle = Ui.text(this, "", 15, 0, Ui.MEDIUM);
        toggle.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        boolean spread = prefs.getBoolean("spread", true);
        toggle.setText(spread ? "Açık" : "Kapalı");
        toggle.setTextColor(spread ? 0xFF000000 : Ui.LABEL);
        toggle.setBackgroundDrawable(Ui.round(spread ? 0xFF34C759 : Ui.FILL, Ui.dp(this, 14)));
        spreadRow.addView(toggle);
        spreadRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().putBoolean("spread", !prefs.getBoolean("spread", true)).apply();
                togglePanel();
                togglePanel();
                restyle();
            }
        });
        panel.addView(spreadRow, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 40)));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(this, 340), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
        lp.setMargins(0, topBar.getHeight() + Ui.dp(this, 6), Ui.dp(this, 14), 0);
        root.addView(panel, lp);
    }

    private LinearLayout segmented() {
        LinearLayout l = Ui.row(this);
        l.setBackgroundDrawable(Ui.round(Ui.FILL, Ui.dp(this, 10)));
        l.setPadding(Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2));
        return l;
    }

    private TextView segment(LinearLayout parent, String s, float sp, boolean first) {
        if (!first) {
            View sep = new View(this);
            sep.setBackgroundColor(0x33FFFFFF);
            parent.addView(sep, new LinearLayout.LayoutParams(1, Ui.dp(this, 22)));
        }
        TextView t = Ui.text(this, s, sp, Ui.LABEL, Ui.REGULAR);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundDrawable(Ui.pressable(0, 0x33FFFFFF, Ui.dp(this, 8)));
        t.setClickable(true);
        parent.addView(t, Ui.lpw(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return t;
    }

    private void zoom(int delta) {
        int z = Math.max(70, Math.min(230, prefs.getInt("zoom", 120) + delta));
        prefs.edit().putInt("zoom", z).apply();
        web.getSettings().setTextZoom(z);
        onZoom(delta);
    }

    /** EPUB reflows text; the PDF reader zooms the page instead. */
    void onZoom(int delta) {
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                relayout();
            }
        }, 250);
    }

    // ---------------------------------------------------------------- "back to where you were"

    private TextView returnPill;
    private Runnable returnAction;

    /** After jumping to a footnote or link target: a button (and the Back key) that returns to the reading spot. */
    void showReturn(Runnable action) {
        hideReturn();
        returnAction = action;
        returnPill = Ui.button(this, "‹  Okuduğun yere dön", true);
        returnPill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                useReturn();
            }
        });
        // At the top: footnotes and endnotes usually sit at the bottom of the page.
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = Ui.dp(this, 6);
        root.addView(returnPill, lp);
    }

    void hideReturn() {
        if (returnPill != null) root.removeView(returnPill);
        returnPill = null;
        returnAction = null;
    }

    private void useReturn() {
        Runnable r = returnAction;
        hideReturn();
        if (r != null) r.run();
    }

    // ---------------------------------------------------------------- word sheet

    void showWord(String tapped, final String sentence, float y) {
        hideWord();
        if (topBar.getVisibility() == View.VISIBLE) toggleBars();
        final Dict.Entry e = Dict.lookup(this, tapped);
        final boolean found = e.head != null;
        // A PDF word rejoined across a line break arrives as "hap-pens"; show it as the book prints it.
        String shown = tapped;
        if (tapped.indexOf('-') > 0 && sentence != null) {
            String plain = tapped.replace("-", "");
            if (sentence.contains(plain) && !sentence.contains(tapped)) shown = plain;
        }
        final String word = shown;

        scrim = new View(this);
        scrim.setBackgroundColor(0x33000000);
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideWord();
            }
        });
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        card = Ui.column(this);
        card.setClickable(true);
        card.setBackgroundDrawable(Ui.round(Ui.GROUPED, Ui.dp(this, 22)));
        int p = Ui.dp(this, 24);
        card.setPadding(p, Ui.dp(this, 20), p, Ui.dp(this, 20));

        LinearLayout head = Ui.row(this);
        LinearLayout words = Ui.column(this);
        words.addView(Ui.text(this, word, 30, Ui.LABEL, Ui.SERIF_BOLD));
        if (found && !e.head.equals(word.toLowerCase(Locale.ENGLISH)) && !e.head.equals(word.toLowerCase(Locale.ENGLISH).replace("-", ""))) {
            TextView base = Ui.text(this, "kök: " + e.head, 14, Ui.LABEL_2, Ui.REGULAR);
            base.setPadding(0, Ui.dp(this, 4), 0, 0);
            words.addView(base);
        }
        head.addView(words, Ui.lpw(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Icon speak = new Icon(this, Icon.SPEAKER, Ui.ACCENT);
        speak.setBackgroundDrawable(Ui.pressable(Ui.ACCENT_TINT, 0x55FF9F0A, Ui.dp(this, 22)));
        speak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                speak(word);
            }
        });
        head.addView(speak, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        head.addView(Ui.space(this, 10, 1));
        Icon close = new Icon(this, Icon.CLOSE, Ui.LABEL_2);
        close.setBackgroundDrawable(Ui.pressable(Ui.FILL, Ui.ELEVATED, Ui.dp(this, 22)));
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideWord();
            }
        });
        head.addView(close, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        card.addView(head);
        card.addView(Ui.space(this, 1, 16));

        card.addView(Ui.header(this, "Türkçe"));
        if (found) {
            StringBuilder tr = new StringBuilder();
            for (int i = 0; i < e.turkish.size() && i < 6; i++) {
                if (i > 0) tr.append(", ");
                tr.append(e.turkish.get(i));
            }
            TextView meaning = Ui.text(this, tr.toString(), 22, Ui.LABEL, Ui.REGULAR);
            meaning.setLineSpacing(0, 1.15f);
            card.addView(meaning);
            if (e.baseHead != null) {
                // "understood": its own entry is the adjective; the verb it comes from is usually what's meant.
                TextView base = Ui.text(this, "", 17, Ui.LABEL_2, Ui.REGULAR);
                base.setText(Html.fromHtml(TextUtils.htmlEncode(e.baseHead) + " &nbsp;<font color='#FF9F0A'>"
                        + TextUtils.htmlEncode(TextUtils.join(", ", e.baseTurkish)) + "</font>"));
                base.setPadding(0, Ui.dp(this, 10), 0, 0);
                card.addView(base);
            }
        } else {
            card.addView(Ui.text(this, "Sözlükte yok. İnternetten çevirebilirsiniz.", 17, Ui.LABEL_2, Ui.REGULAR));
        }

        if (!e.senses.isEmpty()) {
            card.addView(Ui.space(this, 1, 16));
            card.addView(Ui.header(this, "Anlamlar"));
            for (int i = 0; i < e.senses.size() && i < 3; i++) {
                String[] s = e.senses.get(i);
                TextView t = Ui.text(this, "", 15, Ui.LABEL_2, Ui.REGULAR);
                t.setText(Html.fromHtml((i + 1) + ".&nbsp;&nbsp;" + TextUtils.htmlEncode(s[0])
                        + "&nbsp;&nbsp;<font color='#FF9F0A'>" + TextUtils.htmlEncode(s[1]) + "</font>"));
                t.setLineSpacing(0, 1.15f);
                t.setPadding(0, 0, 0, Ui.dp(this, 6));
                card.addView(t);
            }
        }

        final TextView translated = Ui.text(this, "", 16, Ui.ACCENT, Ui.REGULAR);
        if (sentence != null && sentence.length() > 0) {
            card.addView(Ui.space(this, 1, 14));
            LinearLayout box = Ui.column(this);
            box.setBackgroundDrawable(Ui.round(Ui.ELEVATED, Ui.dp(this, 12)));
            int bp = Ui.dp(this, 14);
            box.setPadding(bp, bp, bp, bp);
            TextView st = Ui.text(this, "", 16, Ui.LABEL_2, Ui.SERIF);
            st.setLineSpacing(0, 1.2f);
            String safe = TextUtils.htmlEncode(sentence), safeWord = TextUtils.htmlEncode(word);
            int at = safe.indexOf(safeWord);
            st.setText(Html.fromHtml(at < 0 ? safe : safe.substring(0, at) + "<font color='#FFFFFF'><b>"
                    + safeWord + "</b></font>" + safe.substring(at + safeWord.length())));
            box.addView(st);
            translated.setPadding(0, Ui.dp(this, 10), 0, 0);
            translated.setLineSpacing(0, 1.2f);
            translated.setVisibility(View.GONE);
            box.addView(translated);
            card.addView(box);
        }

        card.addView(Ui.space(this, 1, 18));
        LinearLayout actions = Ui.row(this);
        final Words store = Words.get(this);
        final String key = found ? e.head : word.toLowerCase(Locale.ENGLISH);
        final TextView save = Ui.button(this, store.has(key) ? "✓  Kaydedildi" : "Kelimelerime Ekle", true);
        final String meaning = found ? TextUtils.join(", ", e.turkish.subList(0, Math.min(4, e.turkish.size()))) : "";
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (store.has(key)) {
                    store.remove(key);
                    save.setText("Kelimelerime Ekle");
                } else {
                    store.save(key, meaning.length() > 0 ? meaning : translated.getText().toString(), sentence, book.title);
                    save.setText("✓  Kaydedildi");
                }
            }
        });
        actions.addView(save, Ui.lpw(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final String toTranslate = found ? sentence : word;
        if (toTranslate != null && toTranslate.length() > 0) {
            actions.addView(Ui.space(this, 12, 1));
            final TextView online = Ui.button(this, found ? "Cümleyi Çevir" : "İnternetten Çevir", false);
            online.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    online.setText("Çevriliyor…");
                    online.setEnabled(false);
                    translateOnline(toTranslate, translated, online, found);
                }
            });
            actions.addView(online, Ui.lpw(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        card.addView(actions, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int width = Math.min(getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 40), Ui.dp(this, 620));
        boolean top = y > 0.55f;
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT,
                (top ? Gravity.TOP : Gravity.BOTTOM) | Gravity.CENTER_HORIZONTAL);
        cl.setMargins(0, Ui.dp(this, 20), 0, Ui.dp(this, 20));
        root.addView(card, cl);
        card.setAlpha(0f);
        card.setTranslationY(Ui.dp(this, top ? -30 : 30));
        card.animate().alpha(1f).translationY(0).setDuration(200).start();
    }

    private void translateOnline(final String text, final TextView out, final TextView button, final boolean sentence) {
        out.setVisibility(View.GONE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String result;
                boolean ok = true;
                try {
                    result = Translate.toTurkish(text);
                } catch (Exception ex) {
                    Log.w("Sayfa", "online translation failed", ex);
                    result = "Çeviri alınamadı. Wi-Fi bağlantısını kontrol edin.";
                    ok = false;
                }
                final String r = result;
                final boolean good = ok;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (out.getParent() == null || card == null) return;
                        out.setText(r);
                        out.setTextColor(good ? Ui.ACCENT : Ui.LABEL_2);
                        out.setVisibility(View.VISIBLE);
                        button.setText(good ? "Çevrildi" : (sentence ? "Cümleyi Çevir" : "İnternetten Çevir"));
                        button.setEnabled(!good);
                    }
                });
            }
        }).start();
    }

    boolean wordShown() {
        return card != null;
    }

    void hideWord() {
        if (card == null) return;
        root.removeView(card);
        root.removeView(scrim);
        card = null;
        scrim = null;
        clearHighlight();
    }

    void clearHighlight() {
        js("sy.clearHl()");
    }

    private void speak(final String word) {
        if (tts == null) {
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    ttsOk = status == TextToSpeech.SUCCESS;
                    if (ttsOk) {
                        tts.setLanguage(Locale.US);
                        tts.speak(word, TextToSpeech.QUEUE_FLUSH, null);
                    } else {
                        Toast.makeText(BookActivity.this, "Sesli okuma kullanılamıyor", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } else if (ttsOk) {
            tts.speak(word, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    // ---------------------------------------------------------------- system

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            next();
            return true;
        }
        if (code == KeyEvent.KEYCODE_VOLUME_UP) {
            prev();
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK) {
            if (card != null) {
                hideWord();
                return true;
            }
            if (panel != null) {
                togglePanel();
                return true;
            }
            if (returnPill != null) {
                useReturn();
                return true;
            }
            if (topBar.getVisibility() == View.VISIBLE) {
                toggleBars();
                return true;
            }
        }
        return super.onKeyDown(code, e);
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent e) {
        return code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_UP || super.onKeyUp(code, e);
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        hideWord();
        if (panel != null) togglePanel();
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                rotated();
            }
        }, 350);
    }

    /** After the screen turns; readers that can re-lay out in place keep the default. */
    void rotated() {
        relayout();
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        if (web != null) {
            root.removeView(web);
            web.destroy();
        }
        super.onDestroy();
    }

    /** Whether the page's own script needs to read local files (the PDF viewer reading the book). */
    boolean scriptReadsFiles() {
        return false;
    }

    // ---------------------------------------------------------------- JS bridge

    /** Page scripts call these; each runs on the UI thread. */
    void onJsReady(int page, int pages) {
    }

    void onJsPage(int page, int pages) {
    }

    void onJsEdge(int dir) {
    }

    void onJsLink(String href) {
    }

    void onJsToc(String json) {
    }

    void onJsError(String message) {
        loading.setText(message);
        loading.setVisibility(View.VISIBLE);
    }

    private final class Bridge {
        @JavascriptInterface
        public void onReady(final int p, final int n) {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    onJsReady(p, n);
                }
            });
        }

        @JavascriptInterface
        public void onPage(final int p, final int n) {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    onJsPage(p, n);
                }
            });
        }

        @JavascriptInterface
        public void onEdge(final int dir) {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    onJsEdge(dir);
                }
            });
        }

        @JavascriptInterface
        public void onWord(final String word, final String sentence, final float y) {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    if (card != null || panel != null) {
                        tapAt(0.5f);
                        clearHighlight();
                        return;
                    }
                    showWord(word, sentence, y);
                }
            });
        }

        @JavascriptInterface
        public void onTap(final float x) {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    tapAt(x);
                }
            });
        }

        @JavascriptInterface
        public void onLink(final String href) {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    onJsLink(href);
                }
            });
        }

        @JavascriptInterface
        public void onError(final String message) {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    onJsError(message);
                }
            });
        }

        @JavascriptInterface
        public void onToc(final String json) {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    onJsToc(json);
                }
            });
        }

        /** The PDF page sends a JPEG of page one as the library cover. */
        @JavascriptInterface
        public void saveCover(String dataUrl) {
            int comma = dataUrl.indexOf(',');
            if (comma < 0) return;
            Library.saveCoverJpeg(BookActivity.this, book.id, Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT));
        }

        @JavascriptInterface
        public boolean hasCover() {
            return Library.coverFile(BookActivity.this, book.id).exists();
        }
    }
}
