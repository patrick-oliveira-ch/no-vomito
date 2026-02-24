package com.motioncues;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * Push-based OTA updater using long-poll.
 * App connects to /api/wait-update — server holds connection
 * until a new build is pushed, then responds immediately.
 * NO inner classes — d8 bug workaround.
 */
public class UpdateChecker implements Runnable {
    static final String SERVER_URL = "http://192.168.1.153:7777";
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;
    int localVersion = -1;
    volatile boolean updateAvailable = false;

    Context getContext() {
        return context;
    }

    public UpdateChecker(Context ctx) {
        this.context = ctx;
        try {
            PackageInfo pi = ctx.getPackageManager()
                .getPackageInfo(ctx.getPackageName(), 0);
            localVersion = (int) pi.getLongVersionCode();
        } catch (Exception e) {
            localVersion = 0;
        }
    }

    public void startChecking() {
        running = true;
        // Start long-poll connection in background
        startLongPoll();
    }

    public void stopChecking() {
        running = false;
        handler.removeCallbacks(this);
    }

    /** Manual check — fetch /api/version and compare */
    public void checkNow() {
        ManualCheckThread t = new ManualCheckThread(this);
        t.setDaemon(true);
        t.start();
    }

    private void startLongPoll() {
        if (!running) return;
        BackgroundChecker bg = new BackgroundChecker(this);
        bg.setDaemon(true);
        bg.start();
    }

    // Called from BackgroundChecker when server responds with update
    void onUpdateFound() {
        updateAvailable = true;
        handler.post(this);
    }

    // Called from BackgroundChecker when connection times out or fails
    void onConnectionEnded() {
        if (running) {
            // Reconnect after 2 seconds
            handler.postDelayed(new ReconnectTask(this), 2000);
        }
    }

    @Override
    public void run() {
        // Called on main thread to trigger download
        if (updateAvailable) {
            updateAvailable = false;
            running = false;
            doDownload();
        }
    }

    private void doDownload() {
        Toast.makeText(context,
            "Mise à jour dispo ! Téléchargement...",
            Toast.LENGTH_LONG).show();

        DownloadInstallThread t = new DownloadInstallThread(context);
        t.setDaemon(true);
        t.start();
    }
}
