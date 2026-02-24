package com.motioncues;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * Update checker using GitHub Releases API.
 * Checks periodically and on manual request.
 * NO inner classes — d8 bug workaround.
 */
public class UpdateChecker implements Runnable {
    static final String GITHUB_API =
        "https://api.github.com/repos/patrick-oliveira-ch/no-vomito/releases/latest";

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;
    int localVersionCode = -1;
    String localVersionName = "";
    volatile boolean updateAvailable = false;
    volatile String downloadUrl = null;

    // Check every 5 minutes
    private static final long CHECK_INTERVAL = 5 * 60 * 1000;

    Context getContext() {
        return context;
    }

    public UpdateChecker(Context ctx) {
        this.context = ctx;
        try {
            PackageInfo pi = ctx.getPackageManager()
                .getPackageInfo(ctx.getPackageName(), 0);
            localVersionCode = (int) pi.getLongVersionCode();
            localVersionName = pi.versionName;
        } catch (Exception e) {
            localVersionCode = 0;
            localVersionName = "0.0.0";
        }
    }

    public void startChecking() {
        running = true;
        // First check after 30 seconds, then every 5 minutes
        handler.postDelayed(this, 30000);
    }

    public void stopChecking() {
        running = false;
        handler.removeCallbacks(this);
    }

    /** Manual check from button */
    public void checkNow() {
        GitHubCheckThread t = new GitHubCheckThread(this, true);
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void run() {
        // Periodic silent check
        if (!running) return;
        GitHubCheckThread t = new GitHubCheckThread(this, false);
        t.setDaemon(true);
        t.start();
        // Schedule next check
        handler.postDelayed(this, CHECK_INTERVAL);
    }

    // Called from GitHubCheckThread when update found
    void onUpdateFound(String apkUrl) {
        downloadUrl = apkUrl;
        updateAvailable = true;
        handler.post(new DownloadStarter(this));
    }

    void doDownload() {
        if (!updateAvailable || downloadUrl == null) return;
        updateAvailable = false;

        Toast.makeText(context,
            "Mise à jour dispo ! Téléchargement...",
            Toast.LENGTH_LONG).show();

        DownloadInstallThread t = new DownloadInstallThread(context, downloadUrl);
        t.setDaemon(true);
        t.start();
    }
}
