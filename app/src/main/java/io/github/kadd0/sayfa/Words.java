// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.kadd0.sayfa;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The reader's saved-words list ("Kelimelerim"). */
final class Words extends SQLiteOpenHelper {
    static final class Saved {
        String word, turkish, sentence, book;
        long time;
    }

    private static Words instance;

    static synchronized Words get(Context c) {
        if (instance == null) instance = new Words(c.getApplicationContext());
        return instance;
    }

    private Words(Context c) {
        super(c, "words.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("create table saved(word text primary key, turkish text, sentence text, book text, time integer)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int from, int to) {
    }

    void save(String word, String turkish, String sentence, String book) {
        ContentValues v = new ContentValues();
        v.put("word", word.toLowerCase(Locale.ENGLISH));
        v.put("turkish", turkish);
        v.put("sentence", sentence);
        v.put("book", book);
        v.put("time", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("saved", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void remove(String word) {
        getWritableDatabase().delete("saved", "word=?", new String[]{word.toLowerCase(Locale.ENGLISH)});
    }

    boolean has(String word) {
        Cursor c = getReadableDatabase().rawQuery("select 1 from saved where word=?", new String[]{word.toLowerCase(Locale.ENGLISH)});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    int count() {
        Cursor c = getReadableDatabase().rawQuery("select count(*) from saved", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    List<Saved> all() {
        List<Saved> out = new ArrayList<Saved>();
        Cursor c = getReadableDatabase().rawQuery("select word, turkish, sentence, book, time from saved order by time desc", null);
        try {
            while (c.moveToNext()) {
                Saved s = new Saved();
                s.word = c.getString(0);
                s.turkish = c.getString(1);
                s.sentence = c.getString(2);
                s.book = c.getString(3);
                s.time = c.getLong(4);
                out.add(s);
            }
        } finally {
            c.close();
        }
        return out;
    }
}
