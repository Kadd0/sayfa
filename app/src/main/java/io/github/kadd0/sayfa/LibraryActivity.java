// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.kadd0.sayfa;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Home screen in the style of Apple Books: a "continue reading" shelf, then every book with its progress. */
public final class LibraryActivity extends Activity {
    private final Handler ui = new Handler();
    private ListView list;
    private LinearLayout header;
    private final Adapter adapter = new Adapter();
    private List<Library.Book> books = new ArrayList<Library.Book>();
    private boolean scanned;
    private int perRow, tileW, coverH, side, gap;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setBackgroundDrawable(new ColorDrawable(Ui.BG));
        list = new ListView(this);
        list.setDivider(null);
        list.setSelector(new ColorDrawable(0));
        list.setCacheColorHint(Ui.BG);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        header = Ui.column(this);
        header.setClipToPadding(false);
        list.addHeaderView(header, null, false);
        TextView about = Ui.link(this, "Hakkında ve lisanslar");
        about.setGravity(Gravity.CENTER);
        about.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 8));
        about.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAbout();
            }
        });
        list.addFooterView(about, null, false);
        measure();
        list.setAdapter(adapter);
        setContentView(list);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Dict.install(LibraryActivity.this);
                } catch (Exception ignored) {
                    // The reader says "not found"; nothing else depends on this.
                }
            }
        }).start();
    }

    /** Grid geometry for the current orientation. */
    private void measure() {
        int width = getResources().getDisplayMetrics().widthPixels;
        side = Ui.dp(this, width > Ui.dp(this, 900) ? 48 : 28);
        gap = Ui.dp(this, 24);
        perRow = Math.max(3, Math.min(7, (width - 2 * side) / Ui.dp(this, 175)));
        tileW = (width - 2 * side - (perRow - 1) * gap) / perRow;
        coverH = tileW * 3 / 2;
        list.setPadding(0, 0, 0, Ui.dp(this, 40));
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        measure();
        list.setAdapter(adapter);  // drop rows recycled at the old column width
        show(books);
    }

    @Override
    protected void onResume() {
        super.onResume();
        show(Library.cached(this));
        if (!scanned) {
            scanned = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final List<Library.Book> found = Library.scan(LibraryActivity.this);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            show(found);
                        }
                    });
                }
            }).start();
        }
    }

    /** Version, license and credits for the bundled fonts, libraries and dictionaries (assets/licenses). */
    private void showAbout() {
        String text;
        try {
            InputStream in = getAssets().open("licenses/NOTICES.txt");
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            in.close();
            text = b.toString("UTF-8");
        } catch (Exception e) {
            text = "GNU GPL v3 · https://github.com/Kadd0/sayfa";
        }
        String version = "";
        try {
            version = " " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            // Shown without a version number.
        }
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Sayfa" + version)
                .setMessage(text)
                .setPositiveButton("Tamam", null)
                .show();
    }

    private void show(List<Library.Book> all) {
        books = all;
        buildHeader();
        adapter.notifyDataSetChanged();
    }

    private void buildHeader() {
        header.removeAllViews();
        header.setPadding(side, Ui.dp(this, 28), side, 0);

        LinearLayout top = Ui.row(this);
        top.setGravity(Gravity.BOTTOM);
        LinearLayout titles = Ui.column(this);
        titles.addView(Ui.text(this, "Kitaplığım", 34, Ui.LABEL, Ui.BOLD));
        int done = 0;
        for (Library.Book b : books) if (b.percent >= 99.5f) done++;
        String sub = books.isEmpty() ? (scanned ? "Kitaplar aranıyor…" : "") :
                books.size() + " kitap" + (done > 0 ? "  ·  " + done + " tanesi bitti" : "");
        TextView subtitle = Ui.text(this, sub, 15, Ui.LABEL_2, Ui.REGULAR);
        subtitle.setPadding(0, Ui.dp(this, 6), 0, 0);
        titles.addView(subtitle);
        top.addView(titles, Ui.lpw(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        int saved = Words.get(this).count();
        TextView words = Ui.button(this, "Kelimelerim" + (saved > 0 ? "  " + saved : ""), false);
        words.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LibraryActivity.this, WordsActivity.class));
            }
        });
        top.addView(words);
        header.addView(top);
        header.addView(Ui.space(this, 1, 30));

        List<Library.Book> recent = new ArrayList<Library.Book>();
        for (Library.Book b : books) if (b.lastRead > 0) recent.add(b);
        if (!recent.isEmpty()) {
            header.addView(Ui.title2(this, "Okumaya Devam Et"));
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
            LinearLayout shelf = Ui.row(this);
            shelf.setGravity(Gravity.TOP);
            shelf.addView(featured(recent.get(0)));
            for (int i = 1; i < recent.size() && i < 6; i++) {
                shelf.addView(Ui.space(this, 22, 1));
                shelf.addView(tile(recent.get(i), null));
            }
            hs.addView(shelf);
            // Run edge to edge like an iOS shelf: a partly visible last book shows the row scrolls.
            hs.setClipToPadding(false);
            hs.setPadding(side, 0, side, 0);
            LinearLayout.LayoutParams hl = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hl.setMargins(-side, 0, -side, 0);
            header.addView(hs, hl);
            header.addView(Ui.space(this, 1, 34));
        }

        if (books.isEmpty()) {
            TextView empty = Ui.text(this, "Henüz kitap yok\n\nEPUB veya PDF dosyalarını tabletteki Kitaplar klasörüne kopyalayın.",
                    17, Ui.LABEL_2, Ui.REGULAR);
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(0, 1.2f);
            empty.setPadding(0, Ui.dp(this, 90), 0, 0);
            header.addView(empty, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            header.addView(Ui.title2(this, "Tüm Kitaplar"));
        }
    }

    /** Wide card for the book read most recently. */
    private View featured(final Library.Book b) {
        LinearLayout card = Ui.row(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundDrawable(Ui.pressable(Ui.GROUPED, Ui.ELEVATED, Ui.dp(this, 18)));
        int p = Ui.dp(this, 18);
        card.setPadding(p, p, Ui.dp(this, 28), p);
        int ch = Math.max(coverH, Ui.dp(this, 220)), cw = ch * 2 / 3;
        CoverView cover = new CoverView(this);
        Covers.load(this, b, cover, cw, ch);
        card.addView(cover, new LinearLayout.LayoutParams(cw, ch));

        LinearLayout info = Ui.column(this);
        info.setPadding(Ui.dp(this, 22), 0, 0, 0);
        TextView title = Ui.text(this, b.title, 24, Ui.LABEL, Ui.SERIF_BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLineSpacing(0, 1.1f);
        info.addView(title);
        TextView author = Ui.oneLine(this, b.author, 16, Ui.LABEL_2, Ui.REGULAR);
        author.setPadding(0, Ui.dp(this, 6), 0, 0);
        info.addView(author);
        String where = b.pdf ? "Sayfa " + (b.chapter + 1)
                : (b.chapter < b.titles.size() ? b.titles.get(b.chapter) : "");
        if (where.length() > 0) {
            TextView w = Ui.oneLine(this, where, 14, Ui.LABEL_3, Ui.REGULAR);
            w.setPadding(0, Ui.dp(this, 14), 0, 0);
            info.addView(w);
        }
        info.addView(Ui.space(this, 1, 16));
        ProgressLine line = new ProgressLine(this);
        line.set(b.percent / 100f);
        info.addView(line, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 4)));
        TextView pct = Ui.text(this, Ui.percent(b.percent) + " tamamlandı", 13, Ui.LABEL_2, Ui.REGULAR);
        pct.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 18));
        info.addView(pct);
        TextView go = Ui.button(this, "Okumaya Devam Et", true);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(b);
            }
        });
        info.addView(go, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(info, Ui.lp(Ui.dp(this, 340), ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(b);
            }
        });
        return card;
    }

    /** Grid tile: cover, title, author, progress. Reuses `old` when the list recycles a row. */
    private View tile(final Library.Book b, View old) {
        Tile t = old instanceof LinearLayout && ((LinearLayout) old).getTag() instanceof Tile
                ? (Tile) ((LinearLayout) old).getTag() : new Tile();
        if (t.root == null) {
            t.root = Ui.column(this);
            t.root.setTag(t);
            t.cover = new CoverView(this);
            int pad = (int) t.cover.padding();
            // The cover view is wider than the cover by the shadow; text lines up with the cover edge.
            t.root.addView(t.cover, new LinearLayout.LayoutParams(tileW + 2 * pad, coverH + 2 * pad));
            t.title = Ui.text(this, "", 15, Ui.LABEL, Ui.MEDIUM);
            t.title.setMaxLines(2);
            t.title.setEllipsize(TextUtils.TruncateAt.END);
            t.title.setLineSpacing(0, 1.1f);
            t.title.setPadding(pad, Ui.dp(this, 4), pad, 0);
            t.root.addView(t.title, Ui.lp(tileW + 2 * pad, ViewGroup.LayoutParams.WRAP_CONTENT));
            t.author = Ui.oneLine(this, "", 13, Ui.LABEL_2, Ui.REGULAR);
            t.author.setPadding(pad, Ui.dp(this, 4), pad, 0);
            t.root.addView(t.author, Ui.lp(tileW + 2 * pad, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout status = Ui.row(this);
            status.setPadding(pad, Ui.dp(this, 9), pad, 0);
            t.line = new ProgressLine(this);
            status.addView(t.line, Ui.lpw(0, Ui.dp(this, 3), 1));
            t.state = Ui.text(this, "", 12, Ui.LABEL_2, Ui.MEDIUM);
            t.state.setPadding(Ui.dp(this, 8), 0, 0, 0);
            status.addView(t.state);
            t.root.addView(status, Ui.lp(tileW + 2 * pad, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        Covers.load(this, b, t.cover, tileW, coverH);
        t.title.setText(b.title);
        t.author.setText(b.author.length() > 0 ? b.author : (b.pdf ? "PDF" : ""));
        if (b.lastRead == 0) {
            t.line.setVisibility(View.INVISIBLE);
            t.state.setText(b.pdf ? "PDF  ·  Yeni" : "Yeni");
            t.state.setTextColor(Ui.ACCENT);
        } else if (b.percent >= 99.5f) {
            t.line.setVisibility(View.INVISIBLE);
            t.state.setText("Bitti ✓");
            t.state.setTextColor(Ui.LABEL_2);
        } else {
            t.line.setVisibility(View.VISIBLE);
            t.line.set(b.percent / 100f);
            t.state.setText(Ui.percent(b.percent));
            t.state.setTextColor(Ui.LABEL_2);
        }
        t.root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(b);
            }
        });
        return t.root;
    }

    private static final class Tile {
        LinearLayout root;
        CoverView cover;
        TextView title, author, state;
        ProgressLine line;
    }

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return (books.size() + perRow - 1) / perRow;
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
        public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout row = convert instanceof LinearLayout && ((LinearLayout) convert).getChildCount() == perRow
                    ? (LinearLayout) convert : null;
            if (row == null) {
                row = Ui.row(LibraryActivity.this);
                row.setGravity(Gravity.TOP);
                row.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                int pad = Ui.dp(LibraryActivity.this, 6);  // CoverView shadow space
                row.setPadding(side - pad, 0, side - pad, Ui.dp(LibraryActivity.this, 26));
                for (int i = 0; i < perRow; i++) {
                    LinearLayout.LayoutParams lp = Ui.lp(tileW + 2 * pad, ViewGroup.LayoutParams.WRAP_CONTENT);
                    if (i > 0) lp.leftMargin = gap - 2 * pad;
                    row.addView(new LinearLayout(LibraryActivity.this), lp);
                }
            }
            for (int i = 0; i < perRow; i++) {
                int idx = pos * perRow + i;
                View cell = row.getChildAt(i);
                if (idx < books.size()) {
                    View v = tile(books.get(idx), cell);
                    if (v != cell) {
                        ViewGroup.LayoutParams lp = cell.getLayoutParams();
                        row.removeViewAt(i);
                        row.addView(v, i, lp);
                    }
                    v.setVisibility(View.VISIBLE);
                } else {
                    cell.setVisibility(View.INVISIBLE);
                }
            }
            return row;
        }
    }

    private void open(Library.Book b) {
        startActivity(new Intent(this, b.pdf ? PdfActivity.class : ReaderActivity.class).putExtra("id", b.id));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
