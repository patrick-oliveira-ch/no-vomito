package com.motioncues;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {
    private static final String SERVER_URL = "http://192.168.1.153:7777";
    private final Context context;

    public UpdateChecker(Context ctx) {
        this.context = ctx;
    }

    public void checkForUpdate() {
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL + "/api/version");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                int remoteVersion = json.getInt("versionCode");

                PackageInfo pi = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
                int localVersion = (int) pi.getLongVersionCode();

                if (remoteVersion > localVersion) {
                    downloadAndInstall(json.getString("filename"));
                }
            } catch (Exception e) {
                // Server unreachable — silently ignore
            }
        }).start();
    }

    private void downloadAndInstall(String filename) {
        DownloadManager dm = (DownloadManager) context.getSystemService(
            Context.DOWNLOAD_SERVICE);
        Uri uri = Uri.parse(SERVER_URL + "/api/download");

        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setTitle("Motion Cues Update");
        request.setDescription("Downloading update...");
        request.setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS, "motion-cues-update.apk");
        request.setNotificationVisibility(
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setMimeType("application/vnd.android.package-archive");

        dm.enqueue(request);

        // The user taps the notification to install
        Toast.makeText(context, "Mise à jour en téléchargement...",
            Toast.LENGTH_LONG).show();
    }
}
