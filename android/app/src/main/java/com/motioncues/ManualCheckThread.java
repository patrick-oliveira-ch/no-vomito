package com.motioncues;

import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Background thread for manual update check.
 * Separate class (not inner) to avoid d8 issues.
 */
public class ManualCheckThread extends Thread {
    private final UpdateChecker checker;

    public ManualCheckThread(UpdateChecker checker) {
        this.checker = checker;
    }

    @Override
    public void run() {
        try {
            URL url = new URL(UpdateChecker.SERVER_URL + "/api/version");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            conn.disconnect();

            // Parse versionCode from JSON {"versionCode":7,...}
            String json = sb.toString();
            int idx = json.indexOf("\"versionCode\"");
            if (idx < 0) {
                postToast("Réponse serveur invalide");
                return;
            }
            int colon = json.indexOf(":", idx);
            int comma = json.indexOf(",", colon);
            if (comma < 0) comma = json.indexOf("}", colon);
            int remoteVersion = Integer.parseInt(
                json.substring(colon + 1, comma).trim());

            if (remoteVersion > checker.localVersion) {
                checker.onUpdateFound();
            } else {
                postToast("Déjà à jour (v" + checker.localVersion + ")");
            }
        } catch (Exception e) {
            postToast("Serveur injoignable");
        }
    }

    private void postToast(String msg) {
        new Handler(Looper.getMainLooper()).post(new ToastRunner(checker, msg));
    }
}
