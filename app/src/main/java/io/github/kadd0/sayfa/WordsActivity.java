// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.kadd0.sayfa;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Saved words as an iOS inset-grouped list. Long-press a word to remove it. */
public final class WordsActivity extends Activity {
    private LinearLayout page;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setBackgroundDrawable(new ColorDrawable(Ui.BG));
        ScrollView scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        page = Ui.column(this);
        scroll.addView(page);
        setContentView(scroll);
        render();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        render();
    }

    private void render() {
        page.removeAllViews();
        int width = getResources().getDisplayMetrics().widthPixels;
        int side = Math.max(Ui.dp(this, 24), (width - Ui.dp(this, 820)) / 2);
        page.setPadding(side, Ui.dp(this, 12), side, Ui.dp(this, 40));
        final List<Words.Saved> words = Words.get(this).all();

        TextView back = Ui.link(this, "‹  Kitaplık");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        page.addView(back, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView title = Ui.text(this, "Kelimelerim", 34, Ui.LABEL, Ui.BOLD);
        title.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 6));
        page.addView(title);
        page.addView(Ui.text(this, words.isEmpty() ? "Henüz kelime yok" : words.size() + " kelime", 15, Ui.LABEL_2, Ui.REGULAR));
        page.addView(Ui.space(this, 1, 26));

        if (words.isEmpty()) {
            TextView hint = Ui.text(this, "Okurken bir kelimeye dokunun ve\n“Kelimelerime Ekle” deyin.", 17, Ui.LABEL_2, Ui.REGULAR);
            hint.setGravity(Gravity.CENTER);
            hint.setLineSpacing(0, 1.2f);
            hint.setPadding(0, Ui.dp(this, 70), 0, 0);
            page.addView(hint, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }

        LinearLayout group = Ui.column(this);
        group.setBackgroundDrawable(Ui.round(Ui.GROUPED, Ui.dp(this, 14)));
        for (int i = 0; i < words.size(); i++) {
            final Words.Saved w = words.get(i);
            if (i > 0) {
                View sep = Ui.hairline(this);
                ((LinearLayout.LayoutParams) sep.getLayoutParams()).leftMargin = Ui.dp(this, 20);
                group.addView(sep);
            }
            LinearLayout row = Ui.column(this);
            row.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 20), Ui.dp(this, 14));
            row.setBackgroundDrawable(Ui.pressable(0, Ui.ELEVATED, Ui.dp(this, 14)));
            LinearLayout top = Ui.row(this);
            top.setGravity(Gravity.BOTTOM);
            top.addView(Ui.text(this, w.word, 21, Ui.LABEL, Ui.SERIF_BOLD));
            TextView tr = Ui.oneLine(this, w.turkish == null ? "" : w.turkish, 17, Ui.ACCENT, Ui.REGULAR);
            tr.setPadding(Ui.dp(this, 14), 0, 0, Ui.dp(this, 1));
            top.addView(tr, Ui.lpw(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(top);
            if (w.sentence != null && w.sentence.length() > 0) {
                TextView s = Ui.text(this, w.sentence, 14, Ui.LABEL_2, Ui.SERIF);
                s.setLineSpacing(0, 1.15f);
                s.setMaxLines(3);
                s.setEllipsize(TextUtils.TruncateAt.END);
                s.setPadding(0, Ui.dp(this, 8), 0, 0);
                row.addView(s);
            }
            if (w.book != null && w.book.length() > 0) {
                TextView b = Ui.oneLine(this, w.book, 12, Ui.LABEL_3, Ui.REGULAR);
                b.setPadding(0, Ui.dp(this, 6), 0, 0);
                row.addView(b);
            }
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    new AlertDialog.Builder(WordsActivity.this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                            .setMessage("“" + w.word + "” listeden çıkarılsın mı?")
                            .setPositiveButton("Çıkar", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int which) {
                                    Words.get(WordsActivity.this).remove(w.word);
                                    render();
                                }
                            })
                            .setNegativeButton("Vazgeç", null)
                            .show();
                    return true;
                }
            });
            group.addView(row);
        }
        page.addView(group, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView tip = Ui.text(this, "Silmek için kelimeye basılı tutun.", 13, Ui.LABEL_3, Ui.REGULAR);
        tip.setPadding(Ui.dp(this, 20), Ui.dp(this, 10), 0, 0);
        page.addView(tip);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
