package it.paddeu.quantomanca;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Controlla su GitHub se c'è una versione nuova e la installa.
 * GitHub pubblica a ogni compilazione: QuantoManca.apk + version.json {"versionCode":N,"versionName":"1.0.N"}.
 */
public final class Updater {

    private static final String PREFS = "updater";
    private static final long CHECK_EVERY = 6L * 3600 * 1000;   // al massimo ogni 6 ore

    private Updater() {}

    private static String base() {
        String repo = BuildConfig.UPDATE_REPO;
        if (repo == null || repo.isEmpty()) return null;
        return "https://github.com/" + repo + "/releases/latest/download/";
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** C'è una versione più nuova di quella installata? (usa l'ultimo controllo salvato, nessuna rete) */
    public static boolean available(Context c) {
        return prefs(c).getInt("latestCode", 0) > BuildConfig.VERSION_CODE;
    }

    public static String latestName(Context c) {
        return prefs(c).getString("latestName", "");
    }

    /** Controllo in rete. Da chiamare FUORI dal thread principale. Restituisce true se c'è un aggiornamento. */
    public static boolean check(Context c, boolean force) {
        SharedPreferences p = prefs(c);
        long last = p.getLong("lastCheck", 0);
        if (!force && System.currentTimeMillis() - last < CHECK_EVERY) return available(c);
        String b = base();
        if (b == null) return false;
        HttpURLConnection con = null;
        try {
            con = open(b + "version.json");
            if (con.getResponseCode() != 200) return available(c);
            JSONObject j = new JSONObject(readAll(con.getInputStream()));
            p.edit()
                    .putInt("latestCode", j.optInt("versionCode", 0))
                    .putString("latestName", j.optString("versionName", ""))
                    .putLong("lastCheck", System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {
            // niente rete: riproverà più tardi
        } finally {
            if (con != null) con.disconnect();
        }
        return available(c);
    }

    public interface Progress {
        void onProgress(int percent);
        void onError(String message);
    }

    /** Scarica l'APK e avvia l'installazione. Da chiamare FUORI dal thread principale. */
    public static void downloadAndInstall(Context c, Progress cb) {
        String b = base();
        if (b == null) { cb.onError("Indirizzo aggiornamenti non configurato"); return; }
        HttpURLConnection con = null;
        PackageInstaller.Session session = null;
        try {
            con = open(b + "QuantoManca.apk");
            if (con.getResponseCode() != 200) { cb.onError("Download non riuscito (" + con.getResponseCode() + ")"); return; }
            long total = con.getContentLengthLong();

            PackageInstaller pi = c.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(c.getPackageName());
            int id = pi.createSession(params);
            session = pi.openSession(id);

            try (InputStream in = con.getInputStream();
                 OutputStream out = session.openWrite("QuantoManca.apk", 0, total > 0 ? total : -1)) {
                byte[] buf = new byte[64 * 1024];
                long done = 0;
                int n, lastPct = -1;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (total > 0) {
                        int pct = (int) (done * 100 / total);
                        if (pct != lastPct) { lastPct = pct; cb.onProgress(pct); }
                    }
                }
                session.fsync(out);
            }

            Intent done = new Intent(c, InstallReceiver.class);
            PendingIntent pIntent = PendingIntent.getBroadcast(c, id, done,
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            session.commit(pIntent.getIntentSender());
            session.close();
            session = null;
        } catch (Exception e) {
            cb.onError("Aggiornamento non riuscito: " + e.getMessage());
        } finally {
            if (session != null) session.abandon();
            if (con != null) con.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setInstanceFollowRedirects(true);
        con.setConnectTimeout(15000);
        con.setReadTimeout(30000);
        con.setRequestProperty("Cache-Control", "no-cache");
        con.setRequestProperty("User-Agent", "QuantoManca/" + BuildConfig.VERSION_NAME);
        return con;
    }

    private static String readAll(InputStream in) throws Exception {
        try (InputStream s = in; ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = s.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        }
    }
}
