package com.motioncues;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.IBinder;
import android.view.WindowManager;

public class OverlayService extends Service
    implements MotionEngine.MotionListener, VehicleDetector.VehicleListener {

    private static final String CHANNEL_ID = "motion_cues_channel";
    private static final int NOTIF_ID = 1;
    public static final String ACTION_STOP = "STOP";
    public static final String EXTRA_COLOR = "dot_color";
    // EXTRA_FULLSCREEN removed — always fullscreen grid now
    public static final String EXTRA_SENSITIVITY = "sensitivity";
    public static final String EXTRA_SCROLL_SPEED = "scroll_speed";
    public static final String EXTRA_ALPHA = "dot_alpha";
    public static final String EXTRA_GRID_DENSITY = "grid_density";
    public static final String EXTRA_MIN_SPEED = "min_speed";

    private float minSpeedKmh = 0f;

    private WindowManager windowManager;
    private DotsOverlayView overlayView;
    private MotionEngine motionEngine;
    private VehicleDetector vehicleDetector;
    private boolean isOverlayVisible = false;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = new DotsOverlayView(this);
        motionEngine = new MotionEngine(
            (SensorManager) getSystemService(SENSOR_SERVICE));
        vehicleDetector = new VehicleDetector(this);

        SharedPreferences prefs = getSharedPreferences("motion_cues", MODE_PRIVATE);
        int color = prefs.getInt("dot_color", 0xFF000000);
        float sens = prefs.getFloat("sensitivity", 0.5f);
        float scrollSpeed = prefs.getFloat("scroll_speed", 0.3f);
        float alpha = prefs.getFloat("dot_alpha", 0.45f);
        overlayView.setDotColor(color);
        overlayView.setSensitivity(sens);
        overlayView.setScrollSpeed(scrollSpeed);
        overlayView.setDotAlpha(alpha);
        overlayView.setGridDensity(prefs.getInt("grid_density", 5));
        minSpeedKmh = prefs.getFloat("min_speed", 0f);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null) {
            if (intent.hasExtra(EXTRA_COLOR)) {
                int color = intent.getIntExtra(EXTRA_COLOR, 0xFF333333);
                overlayView.setDotColor(color);
            }
if (intent.hasExtra(EXTRA_SENSITIVITY)) {
                float sens = intent.getFloatExtra(EXTRA_SENSITIVITY, 0.5f);
                overlayView.setSensitivity(sens);
            }
            if (intent.hasExtra(EXTRA_SCROLL_SPEED)) {
                float ss = intent.getFloatExtra(EXTRA_SCROLL_SPEED, 0.3f);
                overlayView.setScrollSpeed(ss);
            }
            if (intent.hasExtra(EXTRA_ALPHA)) {
                float a = intent.getFloatExtra(EXTRA_ALPHA, 0.45f);
                overlayView.setDotAlpha(a);
            }
            if (intent.hasExtra(EXTRA_GRID_DENSITY)) {
                int d = intent.getIntExtra(EXTRA_GRID_DENSITY, 5);
                overlayView.setGridDensity(d);
            }
            if (intent.hasExtra(EXTRA_MIN_SPEED)) {
                minSpeedKmh = intent.getFloatExtra(EXTRA_MIN_SPEED, 0f);
            }
        }

        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        motionEngine.start(this);
        vehicleDetector.start(this);

        // Show overlay immediately
        showOverlay();

        return START_STICKY;
    }

    @Override
    public void onMotionUpdate(float motionX, float motionY) {
        if (isOverlayVisible) {
            overlayView.updateMotion(motionX, motionY);
        }
    }

    @Override
    public void onVehicleStateChanged(boolean inVehicle, float speedKmh) {
        // Handled in onGpsLocation with min speed threshold
    }

    @Override
    public void onGpsLocation(Location loc) {
        motionEngine.onGpsUpdate(loc);

        // Show/hide based on min speed setting
        float speedKmh = loc.hasSpeed() ? loc.getSpeed() * 3.6f : 0f;
        if (minSpeedKmh <= 0f) {
            // No threshold — always visible
            if (!isOverlayVisible) showOverlay();
        } else if (speedKmh >= minSpeedKmh && !isOverlayVisible) {
            showOverlay();
        } else if (speedKmh < minSpeedKmh - 2f && isOverlayVisible) {
            // Small hysteresis (-2 km/h) to avoid flickering
            hideOverlay();
        }
    }

    private void showOverlay() {
        if (isOverlayVisible) return;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        windowManager.addView(overlayView, params);
        isOverlayVisible = true;
    }

    private void hideOverlay() {
        if (!isOverlayVisible) return;
        windowManager.removeView(overlayView);
        isOverlayVisible = false;
    }

    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
            CHANNEL_ID, "No Vomito", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Active pendant la conduite");
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, OverlayService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("No Vomito actif")
            .setContentText("Overlay anti mal des transports")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openPi)
            .addAction(new Notification.Action.Builder(
                null, "Arrêter", stopPi).build())
            .setOngoing(true)
            .build();
    }

    @Override
    public void onDestroy() {
        motionEngine.stop();
        vehicleDetector.stop();
        hideOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
