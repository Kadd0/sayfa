// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.kadd0.sayfa;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Reads what the app needs from an EPUB: title, author, cover, reading order and chapter names. */
final class Epub {
    String title = "", author = "", coverEntry;
    /** Zip paths of the chapters in reading order, with their uncompressed sizes. */
    final List<String> spine = new ArrayList<String>();
    final List<Long> sizes = new ArrayList<Long>();
    /** Chapter name for each spine entry (carried forward when a file has no table-of-contents entry). */
    final List<String> titles = new ArrayList<String>();

    static Epub read(File file) throws Exception {
        ZipFile zip = new ZipFile(file);
        try {
            Epub e = new Epub();
            e.parse(zip);
            return e;
        } finally {
            zip.close();
        }
    }

    private void parse(ZipFile zip) throws Exception {
        String opf = null;
        XmlPullParser p = parser(zip, "META-INF/container.xml");
        for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
            if (ev == XmlPullParser.START_TAG && "rootfile".equals(local(p.getName()))) {
                opf = p.getAttributeValue(null, "full-path");
                break;
            }
        }
        if (opf == null) throw new IOException("no OPF");
        String base = opf.contains("/") ? opf.substring(0, opf.lastIndexOf('/') + 1) : "";

        Map<String, String> hrefs = new HashMap<String, String>();
        Map<String, String> types = new HashMap<String, String>();
        List<String> order = new ArrayList<String>();
        String coverId = null, tocId = null, navHref = null;
        p = parser(zip, opf);
        String inTag = null;
        for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
            if (ev == XmlPullParser.START_TAG) {
                String n = local(p.getName());
                inTag = n;
                if ("item".equals(n)) {
                    String id = p.getAttributeValue(null, "id"), href = p.getAttributeValue(null, "href");
                    String type = p.getAttributeValue(null, "media-type"), props = p.getAttributeValue(null, "properties");
                    if (id != null && href != null) {
                        hrefs.put(id, base + decode(href));
                        types.put(id, type == null ? "" : type);
                        if (props != null && props.contains("cover-image")) coverId = id;
                        if (props != null && props.contains("nav")) navHref = base + decode(href);
                    }
                } else if ("itemref".equals(n)) {
                    String id = p.getAttributeValue(null, "idref");
                    if (id != null) order.add(id);
                } else if ("spine".equals(n)) {
                    tocId = p.getAttributeValue(null, "toc");
                } else if ("meta".equals(n) && "cover".equals(p.getAttributeValue(null, "name")) && coverId == null) {
                    coverId = p.getAttributeValue(null, "content");
                }
            } else if (ev == XmlPullParser.TEXT && inTag != null) {
                String t = p.getText().trim();
                if (t.length() > 0) {
                    if ("title".equals(inTag) && title.length() == 0) title = t;
                    else if ("creator".equals(inTag) && author.length() == 0) author = t;
                }
            } else if (ev == XmlPullParser.END_TAG) {
                inTag = null;
            }
        }

        if (coverId != null && hrefs.containsKey(coverId) && types.get(coverId).startsWith("image")) {
            coverEntry = hrefs.get(coverId);
        } else {
            for (String id : hrefs.keySet()) {
                String h = hrefs.get(id).toLowerCase(Locale.ROOT);
                if (types.get(id).startsWith("image") && (id.toLowerCase(Locale.ROOT).contains("cover") || h.contains("cover"))) {
                    coverEntry = hrefs.get(id);
                    break;
                }
            }
        }

        for (String id : order) {
            String h = hrefs.get(id);
            if (h == null) continue;
            ZipEntry ze = zip.getEntry(h);
            if (ze == null) continue;
            spine.add(h);
            sizes.add(Math.max(1, ze.getSize()));
        }

        Map<String, String> names = new HashMap<String, String>();
        try {
            if (tocId != null && hrefs.containsKey(tocId)) readNcx(zip, hrefs.get(tocId), names);
            else if (navHref != null) readNav(zip, navHref, names);
        } catch (Exception ignored) {
            // A broken table of contents only costs us chapter names.
        }
        names = alignToHeadings(zip, names);
        String last = "";
        for (int i = 0; i < spine.size(); i++) {
            String n = names.get(spine.get(i));
            if (n != null) last = n;
            titles.add(last);
        }
        if (title.length() == 0) title = "Adsız kitap";
    }

    /**
     * Some converted books point each contents entry at the end of the previous chapter's file. When an entry's
     * file does not start with that heading but one of the next few files does, the entry belongs to that file.
     */
    private Map<String, String> alignToHeadings(ZipFile zip, Map<String, String> names) {
        Map<String, String> heads = new HashMap<String, String>(), out = new HashMap<String, String>();
        for (Map.Entry<String, String> e : names.entrySet()) {
            String target = e.getKey(), label = e.getValue();
            boolean good = matches(heading(zip, target, heads), label);
            int at = spine.indexOf(target);
            for (int k = at + 1; !good && at >= 0 && k < spine.size() && k <= at + 3; k++) {
                if (matches(heading(zip, spine.get(k), heads), label)) {
                    target = spine.get(k);
                    good = true;
                }
            }
            if (good || !out.containsKey(target)) out.put(target, label);
        }
        return out;
    }

    private static final Pattern HEADING = Pattern.compile("<h[1-6][^>]*>(.*?)</h[1-6]>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    /** The first heading of a book file, simplified for comparing; "" when there is none. */
    private static String heading(ZipFile zip, String path, Map<String, String> seen) {
        String h = seen.get(path);
        if (h != null) return h;
        h = "";
        try {
            ZipEntry e = zip.getEntry(path);
            if (e != null) {
                InputStream in = zip.getInputStream(e);
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; b.size() < 65536 && (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
                in.close();
                Matcher m = HEADING.matcher(b.toString("UTF-8"));
                if (m.find()) h = simplify(m.group(1).replaceAll("<[^>]+>", " "));
            }
        } catch (Exception ignored) {
            // Unreadable file: treated as having no heading.
        }
        seen.put(path, h);
        return h;
    }

    /** "10. The Law of Small Numbers" and "The Law of Small Numbers" are the same chapter. */
    static boolean matches(String heading, String label) {
        String core = core(label);
        return heading.length() >= 3 && core.length() >= 3 && (heading.contains(core) || core.contains(heading));
    }

    /** A contents entry without its numbering: "10. Anchors" and "Chapter IV Anchors" become "anchors". */
    static String core(String label) {
        return simplify(label).replaceFirst("^(chapter |part |bölüm )?[0-9]+ ", "")
                .replaceFirst("^(chapter|part|bölüm) [ivxlc]+ ", "");
    }

    static String simplify(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("&[a-z#0-9]+;", " ").replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static void readNcx(ZipFile zip, String path, Map<String, String> names) throws Exception {
        String dir = path.contains("/") ? path.substring(0, path.lastIndexOf('/') + 1) : "";
        XmlPullParser p = parser(zip, path);
        String label = null;
        boolean inText = false;
        for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
            if (ev == XmlPullParser.START_TAG) {
                String n = local(p.getName());
                if ("text".equals(n)) inText = true;
                else if ("content".equals(n) && label != null) {
                    String src = p.getAttributeValue(null, "src");
                    if (src != null) {
                        String key = dir + decode(src.split("#")[0]);
                        if (!names.containsKey(key)) names.put(key, label);
                    }
                    label = null;
                }
            } else if (ev == XmlPullParser.TEXT && inText) {
                label = p.getText().trim();
            } else if (ev == XmlPullParser.END_TAG && "text".equals(local(p.getName()))) {
                inText = false;
            }
        }
    }

    private static void readNav(ZipFile zip, String path, Map<String, String> names) throws Exception {
        String dir = path.contains("/") ? path.substring(0, path.lastIndexOf('/') + 1) : "";
        XmlPullParser p = parser(zip, path);
        String href = null;
        StringBuilder text = null;
        for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
            if (ev == XmlPullParser.START_TAG && "a".equals(local(p.getName()))) {
                href = p.getAttributeValue(null, "href");
                text = new StringBuilder();
            } else if (ev == XmlPullParser.TEXT && text != null) {
                text.append(p.getText());
            } else if (ev == XmlPullParser.END_TAG && "a".equals(local(p.getName())) && href != null) {
                String key = dir + decode(href.split("#")[0]);
                String label = text.toString().trim().replaceAll("\\s+", " ");
                if (label.length() > 0 && !names.containsKey(key)) names.put(key, label);
                href = null;
                text = null;
            }
        }
    }

    private static XmlPullParser parser(ZipFile zip, String path) throws Exception {
        ZipEntry e = zip.getEntry(path);
        if (e == null) throw new IOException("missing " + path);
        XmlPullParser p = Xml.newPullParser();
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        try {
            p.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true);
        } catch (Exception ignored) {
            // Not every parser supports relaxed mode.
        }
        p.setInput(zip.getInputStream(e), null);
        return p;
    }

    private static String local(String name) {
        int i = name.indexOf(':');
        return i < 0 ? name : name.substring(i + 1);
    }

    static String decode(String href) {
        try {
            return URLDecoder.decode(href.replace("+", "%2B"), "UTF-8");
        } catch (Exception e) {
            return href;
        }
    }

    /** Unpacks the whole book into dir (once) so the WebView can load chapters as plain files. */
    static void extract(File file, File dir) throws IOException {
        File done = new File(dir, ".ok-" + file.lastModified());
        if (done.exists()) return;
        dir.mkdirs();
        String root = dir.getCanonicalPath() + File.separator;
        ZipFile zip = new ZipFile(file);
        byte[] buf = new byte[16384];
        try {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                File out = new File(dir, e.getName());
                if (!out.getCanonicalPath().startsWith(root)) continue;
                if (e.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                out.getParentFile().mkdirs();
                InputStream in = zip.getInputStream(e);
                FileOutputStream os = new FileOutputStream(out);
                try {
                    for (int n; (n = in.read(buf)) > 0; ) os.write(buf, 0, n);
                } finally {
                    os.close();
                    in.close();
                }
            }
        } finally {
            zip.close();
        }
        done.createNewFile();
    }
}
