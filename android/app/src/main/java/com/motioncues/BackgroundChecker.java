package com.motioncues;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Long-poll background thread.
 * Connects to /api/wait-update and blocks until server pushes an update.
 * Top-level class to avoid d8 inner class bug.
 */
public class BackgroundChecker extends Thread {
    private final UpdateChecker checker;

    public BackgroundChecker(UpdateChecker checker) {
        this.checker = checker;
    }

    @Override
    public void run() {
        try {
            String endpoint = UpdateChecker.SERVER_URL
                + "/api/wait-update?v=" + checker.localVersion;
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            // Long timeout — server holds connection up to 5 minutes
            conn.setReadTimeout(6 * 60 * 1000);

            int code = conn.getResponseCode();
            if (code == 200) {
                // Server responded with update info
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                if (sb.length() > 0) {
                    checker.onUpdateFound();
                    conn.disconnect();
                    return;
                }
            }
            // 204 = timeout, no update yet
            conn.disconnect();
            checker.onConnectionEnded();
        } catch (Exception e) {
            // Connection failed — will reconnect
            checker.onConnectionEnded();
        }
    }
}
