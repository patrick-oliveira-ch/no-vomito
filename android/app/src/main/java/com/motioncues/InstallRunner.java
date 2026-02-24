package com.motioncues;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;

/**
 * Runnable that opens the APK install prompt.
 * Uses our custom ApkFileProvider to share the file.
 */
public class InstallRunner implements Runnable {
    private final Context context;
    private final File apkFile;

    public InstallRunner(Context ctx, File apkFile) {
        this.context = ctx;
        this.apkFile = apkFile;
    }

    @Override
    public void run() {
        try {
            Uri apkUri = Uri.parse(
                "content://com.motioncues.apkprovider/apk_cache/" + apkFile.getName());

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context,
                "Erreur installation: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }
}
