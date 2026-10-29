// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.kadd0.sayfa;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** Online sentence translation. Needs Wi-Fi; call off the UI thread. */
final class Translate {
    private Translate() {}

    /** Tries Google Translate's web endpoint first (it accepts KitKat's TLS), then MyMemory. */
    static String toTurkish(String text) throws Exception {
        try {
            JSONArray parts = new JSONArray(get("https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=tr&dt=t&q="
                    + URLEncoder.encode(text, "UTF-8"))).getJSONArray(0);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) out.append(parts.getJSONArray(i).optString(0, ""));
            if (out.length() > 0) return out.toString();
        } catch (Exception ignored) {
            // Fall through to the second service.
        }
        JSONObject o = new JSONObject(get("https://api.mymemory.translated.net/get?langpair=en%7Ctr&q="
                + URLEncoder.encode(text, "UTF-8")));
        String t = o.getJSONObject("responseData").getString("translatedText");
        if (t == null || t.length() == 0 || o.optInt("responseStatus", 200) != 200) throw new IOException("no result");
        return t;
    }

    private static String get(String address) throws Exception {
        HttpsURLConnection con = (HttpsURLConnection) new URL(address).openConnection();
        con.setSSLSocketFactory(new Tls12(SSLContext.getDefault().getSocketFactory()));
        con.setRequestProperty("User-Agent", "Mozilla/5.0");
        con.setConnectTimeout(8000);
        con.setReadTimeout(10000);
        InputStream in = con.getInputStream();
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
        in.close();
        return b.toString("UTF-8");
    }

    /** Android 4.4 supports TLS 1.2 but leaves it switched off for apps; this turns it on. */
    private static final class Tls12 extends SSLSocketFactory {
        /** Cipher suites that are broken or give no authentication; never offered. */
        private static final String WEAK = ".*(NULL|anon|EXPORT|RC4|_DES_|DES40|3DES|MD5).*";
        private final SSLSocketFactory base;

        Tls12(SSLSocketFactory base) {
            this.base = base;
        }

        private Socket on(Socket s) {
            if (s instanceof SSLSocket) {
                SSLSocket ssl = (SSLSocket) s;
                List<String> protocols = new ArrayList<String>();
                for (String p : ssl.getSupportedProtocols()) if (p.equals("TLSv1.2") || p.equals("TLSv1.1")) protocols.add(p);
                if (!protocols.isEmpty()) ssl.setEnabledProtocols(protocols.toArray(new String[0]));
                // The modern (GCM/ECDHE) suites exist on 4.4 but are switched off by default.
                List<String> suites = new ArrayList<String>();
                for (String c : ssl.getSupportedCipherSuites()) if (!c.matches(WEAK)) suites.add(c);
                ssl.setEnabledCipherSuites(suites.toArray(new String[0]));
            }
            return s;
        }

        @Override public String[] getDefaultCipherSuites() { return base.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return base.getSupportedCipherSuites(); }
        @Override public Socket createSocket(Socket s, String h, int p, boolean a) throws IOException { return on(base.createSocket(s, h, p, a)); }
        @Override public Socket createSocket(String h, int p) throws IOException { return on(base.createSocket(h, p)); }
        @Override public Socket createSocket(String h, int p, InetAddress l, int lp) throws IOException { return on(base.createSocket(h, p, l, lp)); }
        @Override public Socket createSocket(InetAddress h, int p) throws IOException { return on(base.createSocket(h, p)); }
        @Override public Socket createSocket(InetAddress h, int p, InetAddress l, int lp) throws IOException { return on(base.createSocket(h, p, l, lp)); }
    }
}
