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
 * Downloads APK then triggers install intent via FileProvider.
 * Separate class to avoid d8 inner class issues.
 */
public class DownloadInstallThread extends Thread {
    private final Context context;

    public DownloadInstallThread(Context ctx) {
        this.context = ctx;
    }

    @Override
    public void run() {
        try {
            // Download to app-private cache dir (no storage permission needed)
            File apkFile = new File(context.getExternalCacheDir(), "no-vomito-update.apk");
            if (apkFile.exists()) apkFile.delete();

            URL url = new URL(UpdateChecker.SERVER_URL + "/api/download");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);

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

            // Launch install intent on main thread
            new Handler(Looper.getMainLooper()).post(new InstallRunner(context, apkFile));

        } catch (Exception e) {
            new Handler(Looper.getMainLooper()).post(
                new ToastRunner2(context, "Erreur téléchargement: " + e.getMessage()));
        }
    }
}
