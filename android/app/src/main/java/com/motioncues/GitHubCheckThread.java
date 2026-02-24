package com.motioncues;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Checks GitHub releases API for a newer version.
 * Parses tag_name as version, finds .apk asset download URL.
 */
public class GitHubCheckThread extends Thread {
    private final UpdateChecker checker;
    private final boolean showToast;

    public GitHubCheckThread(UpdateChecker checker, boolean showToast) {
        this.checker = checker;
        this.showToast = showToast;
    }

    @Override
    public void run() {
        try {
            URL url = new URL(UpdateChecker.GITHUB_API);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Accept", "application/vnd.github+json");

            if (conn.getResponseCode() != 200) {
                if (showToast) postToast("Erreur GitHub (" + conn.getResponseCode() + ")");
                conn.disconnect();
                return;
            }

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            conn.disconnect();

            String json = sb.toString();

            // Parse tag_name (e.g. "v1.0", "v1.1")
            String tagName = parseJsonString(json, "tag_name");
            if (tagName == null) {
                if (showToast) postToast("Pas de release trouvée");
                return;
            }

            // Compare version strings: strip 'v' prefix, compare
            String remoteVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
            if (!isNewer(remoteVersion, checker.localVersionName)) {
                if (showToast) postToast("Déjà à jour (v" + checker.localVersionName + ")");
                return;
            }

            // Find APK download URL in assets
            // Look for "browser_download_url" ending in .apk
            String apkUrl = findApkUrl(json);
            if (apkUrl == null) {
                if (showToast) postToast("Pas d'APK dans la release");
                return;
            }

            checker.onUpdateFound(apkUrl);

        } catch (Exception e) {
            if (showToast) postToast("Erreur connexion GitHub");
        }
    }

    /** Simple semver comparison: "1.1" > "1.0.1" */
    private boolean isNewer(String remote, String local) {
        int[] r = parseVersion(remote);
        int[] l = parseVersion(local);
        for (int i = 0; i < 3; i++) {
            if (r[i] > l[i]) return true;
            if (r[i] < l[i]) return false;
        }
        return false;
    }

    private int[] parseVersion(String v) {
        int[] parts = {0, 0, 0};
        String[] s = v.split("\\.");
        for (int i = 0; i < Math.min(s.length, 3); i++) {
            try { parts[i] = Integer.parseInt(s[i]); } catch (Exception e) {}
        }
        return parts;
    }

    /** Naive JSON string value parser */
    private String parseJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(":", idx);
        int quote1 = json.indexOf("\"", colon + 1);
        int quote2 = json.indexOf("\"", quote1 + 1);
        if (quote1 < 0 || quote2 < 0) return null;
        return json.substring(quote1 + 1, quote2);
    }

    /** Find first browser_download_url ending in .apk */
    private String findApkUrl(String json) {
        String key = "browser_download_url";
        int searchFrom = 0;
        while (true) {
            int idx = json.indexOf(key, searchFrom);
            if (idx < 0) return null;
            String val = parseJsonStringAt(json, idx);
            if (val != null && val.endsWith(".apk")) return val;
            searchFrom = idx + key.length();
        }
    }

    private String parseJsonStringAt(String json, int keyIdx) {
        int colon = json.indexOf(":", keyIdx);
        int quote1 = json.indexOf("\"", colon + 1);
        int quote2 = json.indexOf("\"", quote1 + 1);
        if (quote1 < 0 || quote2 < 0) return null;
        return json.substring(quote1 + 1, quote2);
    }

    private void postToast(String msg) {
        new Handler(Looper.getMainLooper()).post(new ToastRunner(checker, msg));
    }
}
