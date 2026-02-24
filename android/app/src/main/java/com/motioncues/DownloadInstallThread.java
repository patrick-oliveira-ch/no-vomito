package com.motioncues;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Downloads APK from a URL then triggers install intent.
 * Follows redirects (GitHub uses them for asset downloads).
 */
public class DownloadInstallThread extends Thread {
    private final Context context;
    private final String downloadUrl;

    public DownloadInstallThread(Context ctx, String url) {
        this.context = ctx;
        this.downloadUrl = url;
    }

    @Override
    public void run() {
        try {
            File apkFile = new File(context.getExternalCacheDir(), "no-vomito-update.apk");
            if (apkFile.exists()) apkFile.delete();

            HttpURLConnection conn = (HttpURLConnection) new URL(downloadUrl).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);

            InputStream in = conn.getInputStream();
            FileOutputStream out = new FileOutputStream(apkFile);
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.close();
            in.close();
            conn.disconnect();

            new Handler(Looper.getMainLooper()).post(new InstallRunner(context, apkFile));

        } catch (Exception e) {
            new Handler(Looper.getMainLooper()).post(
                new ToastRunner2(context, "Erreur téléchargement: " + e.getMessage()));
        }
    }
}
