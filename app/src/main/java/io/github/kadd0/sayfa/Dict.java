// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.kadd0.sayfa;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Offline English→Turkish lookups from the bundled dictionary (WikDict + FreeDict). */
final class Dict {
    /** Bump whenever assets/dict.db changes, so installed copies are replaced. 2: repaired FreeDict items. 3: English example phrases removed. */
    private static final int VERSION = 3;
    private static SQLiteDatabase db;

    static final class Entry {
        String asked, head;
        final List<String> turkish = new ArrayList<String>();
        /** For inflected words found as-is ("understood"), the verb they come from ("understand"). */
        String baseHead;
        final List<String> baseTurkish = new ArrayList<String>();
        /** {English definition, Turkish words} pairs. */
        final List<String[]> senses = new ArrayList<String[]>();
    }

    /** Copies the dictionary out of the APK on first start. Run off the UI thread. */
    static synchronized void install(Context c) throws Exception {
        File f = c.getDatabasePath("dict.db");
        int have = c.getSharedPreferences("app", 0).getInt("dictVersion", 0);
        if (have == VERSION && f.exists()) return;
        f.getParentFile().mkdirs();
        InputStream in = c.getAssets().open("dict.db");
        FileOutputStream os = new FileOutputStream(f);
        byte[] buf = new byte[65536];
        for (int n; (n = in.read(buf)) > 0; ) os.write(buf, 0, n);
        os.close();
        in.close();
        c.getSharedPreferences("app", 0).edit().putInt("dictVersion", VERSION).apply();
    }

    private static synchronized SQLiteDatabase db(Context c) throws Exception {
        if (db == null) {
            install(c);
            db = SQLiteDatabase.openDatabase(c.getDatabasePath("dict.db").getPath(), null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        }
        return db;
    }

    /** Looks up the word as written, then its likely dictionary forms ("running" → "run"). */
    static Entry lookup(Context c, String raw) {
        String w = raw.toLowerCase(Locale.ENGLISH).replace('’', '\'').replaceAll("^[^a-z]+|[^a-z]+$", "");
        Entry e = new Entry();
        e.asked = raw;
        if (w.length() == 0) return e;
        try {
            SQLiteDatabase d = db(c);
            for (String cand : forms(w)) {
                Cursor cur = d.rawQuery("select tr, senses from entries where w=?", new String[]{cand});
                try {
                    if (cur.moveToFirst()) {
                        e.head = cand;
                        for (String t : cur.getString(0).split(" \\| ")) if (t.trim().length() > 0) e.turkish.add(t.trim());
                        String s = cur.getString(1);
                        if (s != null && s.length() > 0) {
                            for (String line : s.split("\n")) {
                                String[] p = line.split("\t", 2);
                                if (p.length == 2) e.senses.add(p);
                            }
                        }
                        break;
                    }
                } finally {
                    cur.close();
                }
            }
            if (e.head != null && e.head.equals(w)) addBase(d, e, w);
        } catch (Exception ignored) {
            // Shown as "not found".
        }
        return e;
    }

    /** Irregular past forms and -ed/-ing words that have their own entry also get the base verb's meaning. */
    private static void addBase(SQLiteDatabase d, Entry e, String w) {
        List<String> bases = new ArrayList<String>();
        if (IRREGULAR.containsKey(w)) bases.add(IRREGULAR.get(w));
        else if ((w.endsWith("ed") && w.length() > 4) || (w.endsWith("ing") && w.length() > 5)) {
            for (String f : forms(w)) if (!f.equals(w) && f.length() >= 3) bases.add(f);
        }
        for (String b : bases) {
            Cursor cur = d.rawQuery("select tr from entries where w=?", new String[]{b});
            try {
                if (cur.moveToFirst()) {
                    e.baseHead = b;
                    String[] t = cur.getString(0).split(" \\| ");
                    for (int i = 0; i < t.length && i < 4; i++) if (t[i].trim().length() > 0) e.baseTurkish.add(t[i].trim());
                    return;
                }
            } finally {
                cur.close();
            }
        }
    }

    private static final Map<String, String> IRREGULAR = new HashMap<String, String>();

    static {
        String[] pairs = {
                "was", "be", "were", "be", "been", "be", "is", "be", "are", "be", "am", "be", "has", "have", "had", "have",
                "did", "do", "does", "do", "done", "do", "went", "go", "gone", "go", "saw", "see", "seen", "see",
                "took", "take", "taken", "take", "came", "come", "made", "make", "said", "say", "got", "get",
                "gotten", "get", "knew", "know", "known", "know", "thought", "think", "told", "tell", "found", "find",
                "gave", "give", "given", "give", "felt", "feel", "left", "leave", "kept", "keep", "began", "begin",
                "begun", "begin", "brought", "bring", "bought", "buy", "caught", "catch", "taught", "teach",
                "fought", "fight", "sought", "seek", "stood", "stand", "understood", "understand", "wrote", "write",
                "written", "write", "spoke", "speak", "spoken", "speak", "broke", "break", "broken", "break",
                "chose", "choose", "chosen", "choose", "drove", "drive", "driven", "drive", "ate", "eat", "eaten", "eat",
                "fell", "fall", "fallen", "fall", "forgot", "forget", "forgotten", "forget", "grew", "grow",
                "grown", "grow", "held", "hold", "heard", "hear", "led", "lead", "lost", "lose", "meant", "mean",
                "met", "meet", "paid", "pay", "ran", "run", "rose", "rise", "risen", "rise", "sat", "sit", "sent", "send",
                "slept", "sleep", "sold", "sell", "spent", "spend", "stole", "steal", "stolen", "steal", "swam", "swim",
                "threw", "throw", "thrown", "throw", "woke", "wake", "woken", "wake", "won", "win", "wore", "wear",
                "worn", "wear", "built", "build", "drew", "draw", "drawn", "draw", "flew", "fly", "flown", "fly",
                "hid", "hide", "hidden", "hide", "laid", "lay", "lay", "lie", "lain", "lie", "rode", "ride",
                "ridden", "ride", "rang", "ring", "rung", "ring", "sang", "sing", "sung", "sing", "shook", "shake",
                "shaken", "shake", "shot", "shoot", "shut", "shut", "sank", "sink", "struck", "strike", "swore", "swear",
                "tore", "tear", "torn", "tear", "wept", "weep", "children", "child", "men", "man", "women", "woman",
                "people", "person", "feet", "foot", "teeth", "tooth", "mice", "mouse", "better", "good", "best", "good",
                "worse", "bad", "worst", "bad", "n't", "not", "won't", "will", "can't", "can", "don't", "do",
                "doesn't", "do", "didn't", "do", "isn't", "be", "aren't", "be", "wasn't", "be", "weren't", "be",
                "i'm", "i", "i've", "i", "i'd", "i", "i'll", "i", "you're", "you", "they're", "they", "we're", "we",
                "it's", "it", "that's", "that", "there's", "there", "he's", "he", "she's", "she", "let's", "let"};
        for (int i = 0; i + 1 < pairs.length; i += 2) IRREGULAR.put(pairs[i], pairs[i + 1]);
    }

    private static boolean vowel(char c) {
        return "aeiou".indexOf(c) >= 0;
    }

    /** The word itself first, then plausible base forms, most likely first. */
    static List<String> forms(String w) {
        Set<String> out = new LinkedHashSet<String>();
        out.add(w);
        if (IRREGULAR.containsKey(w)) out.add(IRREGULAR.get(w));
        if (w.endsWith("'s")) out.add(w.substring(0, w.length() - 2));
        if (w.endsWith("s'")) out.add(w.substring(0, w.length() - 1));
        int n = w.length();
        if (n > 4 && w.endsWith("ies")) out.add(w.substring(0, n - 3) + "y");
        if (n > 4 && w.endsWith("ied")) out.add(w.substring(0, n - 3) + "y");
        if (n > 3 && w.endsWith("es")) out.add(w.substring(0, n - 2));
        if (n > 3 && w.endsWith("s") && !w.endsWith("ss")) out.add(w.substring(0, n - 1));
        if (n > 4 && w.endsWith("ed")) {
            String s = w.substring(0, n - 2);
            out.add(s);
            out.add(s + "e");
            if (s.length() > 2 && s.charAt(s.length() - 1) == s.charAt(s.length() - 2)) out.add(s.substring(0, s.length() - 1));
            out.add(w.substring(0, n - 1));
        }
        if (n > 5 && w.endsWith("ing")) {
            String s = w.substring(0, n - 3);
            out.add(s);
            out.add(s + "e");
            if (s.length() > 2 && s.charAt(s.length() - 1) == s.charAt(s.length() - 2)) out.add(s.substring(0, s.length() - 1));
            if (s.endsWith("y")) out.add(s.substring(0, s.length() - 1) + "ie");
        }
        if (n > 5 && w.endsWith("ily")) out.add(w.substring(0, n - 3) + "y");
        if (n > 4 && w.endsWith("ly")) {
            out.add(w.substring(0, n - 2));
            out.add(w.substring(0, n - 2) + "e");
            if (w.endsWith("ally")) out.add(w.substring(0, n - 4));
        }
        if (n > 5 && w.endsWith("iest")) out.add(w.substring(0, n - 4) + "y");
        if (n > 4 && w.endsWith("ier")) out.add(w.substring(0, n - 3) + "y");
        if (n > 4 && w.endsWith("est")) {
            String s = w.substring(0, n - 3);
            out.add(s);
            out.add(s + "e");
            if (s.length() > 2 && s.charAt(s.length() - 1) == s.charAt(s.length() - 2) && !vowel(s.charAt(s.length() - 1)))
                out.add(s.substring(0, s.length() - 1));
        }
        if (n > 4 && w.endsWith("er")) {
            String s = w.substring(0, n - 2);
            out.add(s);
            out.add(s + "e");
            if (s.length() > 2 && s.charAt(s.length() - 1) == s.charAt(s.length() - 2) && !vowel(s.charAt(s.length() - 1)))
                out.add(s.substring(0, s.length() - 1));
        }
        if (n > 5 && w.endsWith("ness")) out.add(w.substring(0, n - 4));
        if (n > 6 && w.endsWith("iness")) out.add(w.substring(0, n - 5) + "y");
        if (w.contains("-")) {
            // A word rejoined across a line break ("influ-enced"): try every form of "influenced" too.
            String joined = w.replace("-", "");
            out.add(w.replace("-", " "));
            out.addAll(forms(joined));
        }
        // Last resorts: the other spelling ("realise" / "realize") and the root of -ize verbs ("stylize" → "style").
        List<String> found = new ArrayList<String>(out);
        for (String f : found) out.addAll(spellings(f));
        for (String f : new ArrayList<String>(out)) {
            int m = f.length();
            if (m > 6 && (f.endsWith("ize") || f.endsWith("ise"))) {
                String stem = f.substring(0, m - 3);
                out.add(stem + "e");
                out.add(stem);
                out.add(stem + "y");
            }
        }
        return new ArrayList<String>(out);
    }

    /** British ↔ American spellings of one word. */
    static List<String> spellings(String w) {
        List<String> out = new ArrayList<String>();
        int n = w.length();
        if (n > 4) {
            if (w.contains("is")) out.add(w.replaceFirst("is(e|ed|es|ing|ation|ations)$", "iz$1"));
            if (w.contains("iz")) out.add(w.replaceFirst("iz(e|ed|es|ing|ation|ations)$", "is$1"));
            if (w.contains("yse")) out.add(w.replaceFirst("yse(s|d)?$", "yze$1"));
            if (w.contains("our")) out.add(w.replaceFirst("our(s|ed|ing|ite|ites|able|ful)?$", "or$1"));
            if (w.endsWith("re") || w.endsWith("res")) out.add(w.replaceFirst("([^aeiou])re(s)?$", "$1er$2"));
            if (w.endsWith("ence")) out.add(w.substring(0, n - 4) + "ense");
            if (w.endsWith("ense")) out.add(w.substring(0, n - 4) + "ence");
            if (w.matches(".*[aeiou]ll(ed|ing|er|ers)$")) out.add(w.replaceFirst("ll(ed|ing|er|ers)$", "l$1"));
            if (w.matches(".*[aeiou]l(ed|ing|er|ers)$")) out.add(w.replaceFirst("l(ed|ing|er|ers)$", "ll$1"));
            if (w.equals("grey")) out.add("gray");
        }
        out.remove(w);
        return out;
    }
}
