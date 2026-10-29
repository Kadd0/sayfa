// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.kadd0.sayfa;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Finds books on the tablet, caches their details, and stores reading progress. */
final class Library {
    static final String[] FOLDERS = {"Kitaplar", "Download", "Books", "documents"};
    /** Bumped when books are read differently, so saved details are read again. 2: chapter names matched to headings. */
    private static final int FORMAT = 2;

    static final class Book {
        String id, path, title, author;
        boolean pdf;
        long mtime;
        int format;
        final List<String> spine = new ArrayList<String>();
        final List<Long> sizes = new ArrayList<Long>();
        final List<String> titles = new ArrayList<String>();
        // Reading state
        int chapter;
        float frac, percent;
        long lastRead;

        long total() {
            long t = 0;
            for (long s : sizes) t += s;
            return Math.max(1, t);
        }

        /** Share of the whole book read when standing at `frac` of `chapter`. */
        float percentAt(int chapter, float frac) {
            long before = 0;
            for (int i = 0; i < chapter && i < sizes.size(); i++) before += sizes.get(i);
            long here = chapter < sizes.size() ? sizes.get(chapter) : 0;
            return Math.min(100f, 100f * (before + here * frac) / total());
        }

        /** Chapter and position inside it for a share of the whole book. */
        float[] locate(float percent) {
            long target = (long) (total() * percent / 100f), acc = 0;
            for (int i = 0; i < sizes.size(); i++) {
                if (acc + sizes.get(i) > target) return new float[]{i, (target - acc) / (float) sizes.get(i)};
                acc += sizes.get(i);
            }
            return new float[]{Math.max(0, sizes.size() - 1), 1f};
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id).put("path", path).put("title", title).put("author", author)
                    .put("pdf", pdf).put("mtime", mtime).put("format", format);
            JSONArray s = new JSONArray(), z = new JSONArray(), t = new JSONArray();
            for (int i = 0; i < spine.size(); i++) {
                s.put(spine.get(i));
                z.put(sizes.get(i));
                t.put(titles.get(i));
            }
            o.put("spine", s).put("sizes", z).put("titles", t);
            return o;
        }

        static Book fromJson(JSONObject o) throws Exception {
            Book b = new Book();
            b.id = o.getString("id");
            b.path = o.getString("path");
            b.title = o.getString("title");
            b.author = o.optString("author");
            b.pdf = o.optBoolean("pdf");
            b.mtime = o.optLong("mtime");
            b.format = o.optInt("format");
            JSONArray s = o.getJSONArray("spine"), z = o.getJSONArray("sizes"), t = o.getJSONArray("titles");
            for (int i = 0; i < s.length(); i++) {
                b.spine.add(s.getString(i));
                b.sizes.add(z.getLong(i));
                b.titles.add(t.getString(i));
            }
            return b;
        }
    }

    private static List<Book> cache;

    static File coverFile(Context c, String id) {
        return new File(new File(c.getFilesDir(), "covers"), id + ".jpg");
    }

    static File bookDir(Context c, String id) {
        return new File(new File(c.getFilesDir(), "books"), id);
    }

    /** Books from the last scan (or the saved list), with progress filled in. Cheap; no disk scan. */
    static synchronized List<Book> cached(Context c) {
        if (cache == null) {
            cache = new ArrayList<Book>();
            try {
                File f = new File(c.getFilesDir(), "library.json");
                if (f.exists()) {
                    JSONArray a = new JSONArray(readAll(f));
                    for (int i = 0; i < a.length(); i++) cache.add(Book.fromJson(a.getJSONObject(i)));
                }
            } catch (Exception ignored) {
                // A damaged list is rebuilt by the next scan.
            }
        }
        for (Book b : cache) loadProgress(c, b);
        sort(cache);
        return new ArrayList<Book>(cache);
    }

    static Book find(Context c, String id) {
        for (Book b : cached(c)) if (b.id.equals(id)) return b;
        return null;
    }

    /** Walks the book folders; reuses cached details for files that have not changed. Run off the UI thread. */
    static synchronized List<Book> scan(Context c) {
        Map<String, Book> known = new HashMap<String, Book>();
        for (Book b : cached(c)) known.put(b.path, b);
        Map<String, Book> byId = new HashMap<String, Book>();
        List<Book> out = new ArrayList<Book>();
        File root = Environment.getExternalStorageDirectory();
        for (String folder : FOLDERS) {
            List<File> files = new ArrayList<File>();
            collect(new File(root, folder), files, 0);
            for (File f : files) {
                Book b = known.get(f.getAbsolutePath());
                if (b == null || b.mtime != f.lastModified() || b.format != FORMAT) b = describe(c, f);
                if (b == null || byId.containsKey(b.id)) continue;  // same book already found in an earlier folder
                byId.put(b.id, b);
                out.add(b);
            }
        }
        try {
            JSONArray a = new JSONArray();
            for (Book b : out) a.put(b.toJson());
            FileOutputStream os = new FileOutputStream(new File(c.getFilesDir(), "library.json"));
            os.write(a.toString().getBytes("UTF-8"));
            os.close();
        } catch (Exception ignored) {
            // Not fatal: we scan again next time.
        }
        cache = out;
        return cached(c);
    }

    private static void collect(File dir, List<File> out, int depth) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            String n = f.getName().toLowerCase(Locale.ENGLISH);
            if (f.isDirectory()) {
                if (depth < 2 && !n.startsWith(".") && !n.endsWith(".sdr")) collect(f, out, depth + 1);
            } else if (n.endsWith(".epub") || n.endsWith(".pdf")) {
                out.add(f);
            }
        }
    }

    private static Book describe(Context c, File f) {
        Book b = new Book();
        b.path = f.getAbsolutePath();
        b.mtime = f.lastModified();
        b.format = FORMAT;
        String name = f.getName();
        try {
            if (name.toLowerCase(Locale.ENGLISH).endsWith(".pdf")) {
                b.pdf = true;
                String base = name.substring(0, name.length() - 4);
                int dash = base.indexOf(" - ");
                b.author = dash > 0 ? base.substring(0, dash).trim() : "";
                b.title = dash > 0 ? base.substring(dash + 3).trim() : base;
            } else {
                Epub e = Epub.read(f);
                b.title = e.title;
                b.author = e.author;
                b.spine.addAll(e.spine);
                b.sizes.addAll(e.sizes);
                b.titles.addAll(e.titles);
                if (b.spine.isEmpty()) return null;
                b.id = md5(norm(b.title) + "|" + norm(b.author));
                if (e.coverEntry != null) saveCover(c, f, e.coverEntry, b.id);
                return b;
            }
        } catch (Exception ex) {
            return null;
        }
        // PDFs carry no reliable title, so identical copies under different names are matched by size.
        b.id = md5("pdf|" + f.length());
        return b;
    }

    private static void saveCover(Context c, File book, String entry, String id) {
        File out = coverFile(c, id);
        if (out.exists()) return;
        try {
            ZipFile zip = new ZipFile(book);
            try {
                ZipEntry e = zip.getEntry(entry);
                if (e == null) return;
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                InputStream in = zip.getInputStream(e);
                BitmapFactory.decodeStream(in, null, o);
                in.close();
                o.inJustDecodeBounds = false;
                o.inSampleSize = 1;
                while (o.outHeight / (o.inSampleSize * 2) >= 600) o.inSampleSize *= 2;
                in = zip.getInputStream(e);
                Bitmap bm = BitmapFactory.decodeStream(in, null, o);
                in.close();
                if (bm == null) return;
                out.getParentFile().mkdirs();
                FileOutputStream os = new FileOutputStream(out);
                bm.compress(Bitmap.CompressFormat.JPEG, 88, os);
                os.close();
                bm.recycle();
            } finally {
                zip.close();
            }
        } catch (Exception ignored) {
            // Falls back to a drawn cover.
        }
    }

    /** Stores a JPEG cover made elsewhere (the PDF reader renders page one for this). */
    static void saveCoverJpeg(Context c, String id, byte[] jpeg) {
        try {
            File out = coverFile(c, id);
            out.getParentFile().mkdirs();
            FileOutputStream os = new FileOutputStream(out);
            os.write(jpeg);
            os.close();
            Covers.forget(id);
        } catch (Exception ignored) {
            // Keeps the drawn cover.
        }
    }

    // ---- reading progress ----

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("progress", Context.MODE_PRIVATE);
    }

    /** `frac` is where the current page starts (used to reopen it); `readTo` is where it ends (used for %). */
    static void saveProgress(Context c, Book b, int chapter, float frac, float readTo) {
        saveRaw(c, b, chapter, frac, b.percentAt(chapter, readTo));
    }

    /** PDFs: `chapter` is the page index and the percentage is pages read. */
    static void saveRaw(Context c, Book b, int chapter, float frac, float percent) {
        b.chapter = chapter;
        b.frac = frac;
        b.percent = percent;
        b.lastRead = System.currentTimeMillis();
        prefs(c).edit().putString(b.id, chapter + "|" + frac + "|" + b.percent + "|" + b.lastRead).apply();
    }

    private static void loadProgress(Context c, Book b) {
        String s = prefs(c).getString(b.id, null);
        if (s == null) return;
        String[] p = s.split("\\|");
        try {
            b.chapter = Integer.parseInt(p[0]);
            b.frac = Float.parseFloat(p[1]);
            b.percent = Float.parseFloat(p[2]);
            b.lastRead = Long.parseLong(p[3]);
        } catch (Exception ignored) {
            // Keep the defaults (start of book).
        }
    }

    /** Recently read first, then the rest by title. */
    private static void sort(List<Book> list) {
        Collections.sort(list, new Comparator<Book>() {
            @Override
            public int compare(Book a, Book b) {
                if (a.lastRead != b.lastRead) return a.lastRead > b.lastRead ? -1 : 1;
                return a.title.compareToIgnoreCase(b.title);
            }
        });
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ENGLISH).replaceAll("[\\p{Punct}\\s’‘“”]", "");
    }

    private static String md5(String s) {
        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 8; i++) b.append(String.format("%02x", d[i]));
            return b.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    static String readAll(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[(int) f.length()];
            int off = 0;
            for (int n; off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0; ) off += n;
            return new String(buf, 0, off, "UTF-8");
        } finally {
            in.close();
        }
    }
}
