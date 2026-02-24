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
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements SeekBar.OnSeekBarChangeListener {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int OVERLAY_REQUEST_CODE = 101;

    private Button toggleButton;
    private TextView statusText;
    private TextView sensitivityLabel;
    private TextView scrollSpeedLabel;
    private TextView alphaLabel;
    private TextView gridDensityLabel;
    private TextView minSpeedLabel;
    private SeekBar sensitivitySeek;
    private SeekBar scrollSpeedSeek;
    private SeekBar alphaSeek;
    private SeekBar gridDensitySeek;
    private SeekBar minSpeedSeek;
    private SharedPreferences prefs;
    private boolean serviceRunning = false;
    private UpdateChecker updateChecker;

    private final int[] colorIds = {
        R.id.colorBlack, R.id.colorGray, R.id.colorWhite, R.id.colorBlue,
        R.id.colorGreen, R.id.colorRed, R.id.colorOrange
    };
    private final int[] colorValues = {
        0xFF000000, 0xFF666666, 0xFFFFFFFF, 0xFF2196F3,
        0xFF4CAF50, 0xFFF44336, 0xFFFF9800
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("motion_cues", MODE_PRIVATE);
        toggleButton = findViewById(R.id.toggleButton);
        statusText = findViewById(R.id.statusText);
        sensitivityLabel = findViewById(R.id.sensitivityLabel);
        sensitivitySeek = findViewById(R.id.sensitivitySeek);
        scrollSpeedLabel = findViewById(R.id.scrollSpeedLabel);
        scrollSpeedSeek = findViewById(R.id.scrollSpeedSeek);
        alphaLabel = findViewById(R.id.alphaLabel);
        alphaSeek = findViewById(R.id.alphaSeek);
        gridDensityLabel = findViewById(R.id.gridDensityLabel);
        gridDensitySeek = findViewById(R.id.gridDensitySeek);
        minSpeedLabel = findViewById(R.id.minSpeedLabel);
        minSpeedSeek = findViewById(R.id.minSpeedSeek);

        // Version
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            ((TextView) findViewById(R.id.versionText)).setText("v" + pi.versionName);
        } catch (PackageManager.NameNotFoundException e) {}

        // Toggle start/stop
        toggleButton.setOnClickListener(v -> {
            if (serviceRunning) {
                stopOverlayService();
            } else {
                startOverlayService();
            }
        });

        // Sensitivity slider
        int savedSens = (int)(prefs.getFloat("sensitivity", 0.5f) * 100);
        sensitivitySeek.setProgress(savedSens);
        sensitivityLabel.setText(savedSens + "%");
        sensitivitySeek.setOnSeekBarChangeListener(this);

        // Scroll speed slider
        int savedSpeed = (int)(prefs.getFloat("scroll_speed", 0.3f) * 100);
        scrollSpeedSeek.setProgress(savedSpeed);
        scrollSpeedLabel.setText(savedSpeed + "%");
        scrollSpeedSeek.setOnSeekBarChangeListener(this);

        // Grid density slider
        int savedDensity = prefs.getInt("grid_density", 5);
        gridDensitySeek.setProgress(savedDensity);
        int totalDots = (3 + savedDensity / 2) * (6 + savedDensity);
        gridDensityLabel.setText(totalDots + " points");
        gridDensitySeek.setOnSeekBarChangeListener(this);

        // Min speed slider
        int savedMinSpeed = (int) prefs.getFloat("min_speed", 0f);
        minSpeedSeek.setProgress(savedMinSpeed);
        minSpeedLabel.setText(savedMinSpeed == 0 ? "Désactivé" : savedMinSpeed + " km/h");
        minSpeedSeek.setOnSeekBarChangeListener(this);

        // Opacity slider
        int savedAlpha = (int)(prefs.getFloat("dot_alpha", 0.45f) * 100);
        alphaSeek.setProgress(savedAlpha);
        alphaLabel.setText(savedAlpha + "%");
        alphaSeek.setOnSeekBarChangeListener(this);

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

        // Update checker
        updateChecker = new UpdateChecker(this);
        updateChecker.startChecking();

        findViewById(R.id.updateButton).setOnClickListener(v -> {
            Toast.makeText(this, "Vérification...", Toast.LENGTH_SHORT).show();
            updateChecker.checkNow();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        serviceRunning = isServiceRunning();
        updateUI();
    }

    private void startOverlayService() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_REQUEST_CODE);
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            }, PERMISSION_REQUEST_CODE);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                Manifest.permission.POST_NOTIFICATIONS
            }, PERMISSION_REQUEST_CODE);
            return;
        }

        Intent intent = new Intent(this, OverlayService.class);
        intent.putExtra(OverlayService.EXTRA_COLOR,
            prefs.getInt("dot_color", 0xFF000000));
        intent.putExtra(OverlayService.EXTRA_SENSITIVITY,
            prefs.getFloat("sensitivity", 0.5f));
        intent.putExtra(OverlayService.EXTRA_SCROLL_SPEED,
            prefs.getFloat("scroll_speed", 0.3f));
        intent.putExtra(OverlayService.EXTRA_ALPHA,
            prefs.getFloat("dot_alpha", 0.45f));
        intent.putExtra(OverlayService.EXTRA_GRID_DENSITY,
            prefs.getInt("grid_density", 5));
        intent.putExtra(OverlayService.EXTRA_MIN_SPEED,
            prefs.getFloat("min_speed", 0f));
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
            statusText.setText("Actif");
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
                Toast.makeText(this, "Permission overlay requise", Toast.LENGTH_LONG).show();
            }
        }
    }

    // SeekBar.OnSeekBarChangeListener
    @Override
    public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
        if (sb.getId() == R.id.sensitivitySeek) {
            sensitivityLabel.setText(progress + "%");
            float val = progress / 100f;
            prefs.edit().putFloat("sensitivity", val).apply();
            if (serviceRunning) {
                Intent intent = new Intent(this, OverlayService.class);
                intent.putExtra(OverlayService.EXTRA_SENSITIVITY, val);
                startService(intent);
            }
        } else if (sb.getId() == R.id.scrollSpeedSeek) {
            scrollSpeedLabel.setText(progress + "%");
            float val = progress / 100f;
            prefs.edit().putFloat("scroll_speed", val).apply();
            if (serviceRunning) {
                Intent intent = new Intent(this, OverlayService.class);
                intent.putExtra(OverlayService.EXTRA_SCROLL_SPEED, val);
                startService(intent);
            }
        } else if (sb.getId() == R.id.gridDensitySeek) {
            int cols = 3 + progress / 2;
            int rows = 6 + progress;
            gridDensityLabel.setText((cols * rows) + " points");
            prefs.edit().putInt("grid_density", progress).apply();
            if (serviceRunning) {
                Intent intent = new Intent(this, OverlayService.class);
                intent.putExtra(OverlayService.EXTRA_GRID_DENSITY, progress);
                startService(intent);
            }
        } else if (sb.getId() == R.id.minSpeedSeek) {
            minSpeedLabel.setText(progress == 0 ? "Désactivé" : progress + " km/h");
            float val = (float) progress;
            prefs.edit().putFloat("min_speed", val).apply();
            if (serviceRunning) {
                Intent intent = new Intent(this, OverlayService.class);
                intent.putExtra(OverlayService.EXTRA_MIN_SPEED, val);
                startService(intent);
            }
        } else if (sb.getId() == R.id.alphaSeek) {
            alphaLabel.setText(progress + "%");
            float val = progress / 100f;
            prefs.edit().putFloat("dot_alpha", val).apply();
            if (serviceRunning) {
                Intent intent = new Intent(this, OverlayService.class);
                intent.putExtra(OverlayService.EXTRA_ALPHA, val);
                startService(intent);
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar sb) {}

    @Override
    public void onStopTrackingTouch(SeekBar sb) {}
}
