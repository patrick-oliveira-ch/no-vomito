package com.motioncues;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int OVERLAY_REQUEST_CODE = 101;

    private Button toggleButton;
    private TextView statusText;
    private SharedPreferences prefs;
    private boolean serviceRunning = false;

    private final int[] colorIds = {
        R.id.colorGray, R.id.colorWhite, R.id.colorBlue,
        R.id.colorGreen, R.id.colorRed, R.id.colorOrange
    };
    private final int[] colorValues = {
        0xFF666666, 0xFFFFFFFF, 0xFF2196F3,
        0xFF4CAF50, 0xFFF44336, 0xFFFF9800
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("motion_cues", MODE_PRIVATE);
        toggleButton = findViewById(R.id.toggleButton);
        statusText = findViewById(R.id.statusText);

        // Version
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            ((TextView) findViewById(R.id.versionText)).setText("v" + pi.versionName);
        } catch (PackageManager.NameNotFoundException e) {}

        // Toggle
        toggleButton.setOnClickListener(v -> {
            if (serviceRunning) {
                stopOverlayService();
            } else {
                startOverlayService();
            }
        });

        // Color picker
        for (int i = 0; i < colorIds.length; i++) {
            final int color = colorValues[i];
            findViewById(colorIds[i]).setOnClickListener(v -> {
                prefs.edit().putInt("dot_color", color).apply();
                if (serviceRunning) {
                    Intent intent = new Intent(this, OverlayService.class);
                    intent.putExtra(OverlayService.EXTRA_COLOR, color);
                    startService(intent);
                }
                Toast.makeText(this, "Couleur mise à jour", Toast.LENGTH_SHORT).show();
            });
        }

        updateUI();

        // Check for updates
        new UpdateChecker(this).checkForUpdate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        serviceRunning = isServiceRunning();
        updateUI();
    }

    private void startOverlayService() {
        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_REQUEST_CODE);
            return;
        }

        // Check location permission
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            }, PERMISSION_REQUEST_CODE);
            return;
        }

        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                Manifest.permission.POST_NOTIFICATIONS
            }, PERMISSION_REQUEST_CODE);
            return;
        }

        Intent intent = new Intent(this, OverlayService.class);
        int color = prefs.getInt("dot_color", 0xFF333333);
        intent.putExtra(OverlayService.EXTRA_COLOR, color);
        startForegroundService(intent);
        serviceRunning = true;
        updateUI();
    }

    private void stopOverlayService() {
        Intent intent = new Intent(this, OverlayService.class);
        intent.setAction(OverlayService.ACTION_STOP);
        startService(intent);
        serviceRunning = false;
        updateUI();
    }

    private void updateUI() {
        if (serviceRunning) {
            toggleButton.setText("Arrêter");
            toggleButton.setBackgroundColor(0xFFF44336);
            statusText.setText("Actif — en attente de mouvement véhicule");
        } else {
            toggleButton.setText("Démarrer");
            toggleButton.setBackgroundColor(0xFF4CAF50);
            statusText.setText("Inactif");
        }
    }

    private boolean isServiceRunning() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo si : am.getRunningServices(Integer.MAX_VALUE)) {
            if (OverlayService.class.getName().equals(si.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int reqCode, String[] perms, int[] results) {
        if (reqCode == PERMISSION_REQUEST_CODE) {
            startOverlayService();
        }
    }

    @Override
    protected void onActivityResult(int reqCode, int resultCode, Intent data) {
        if (reqCode == OVERLAY_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                startOverlayService();
            } else {
                Toast.makeText(this,
                    "Permission overlay requise", Toast.LENGTH_LONG).show();
            }
        }
    }
}
