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
import android.os.IBinder;
import android.view.WindowManager;

public class OverlayService extends Service
    implements MotionEngine.MotionListener, VehicleDetector.VehicleListener {

    private static final String CHANNEL_ID = "motion_cues_channel";
    private static final int NOTIF_ID = 1;
    public static final String ACTION_STOP = "STOP";
    public static final String EXTRA_COLOR = "dot_color";

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
        int color = prefs.getInt("dot_color", 0xFF333333);
        overlayView.setDotColor(color);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null && intent.hasExtra(EXTRA_COLOR)) {
            int color = intent.getIntExtra(EXTRA_COLOR, 0xFF333333);
            overlayView.setDotColor(color);
        }

        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        motionEngine.start(this);
        vehicleDetector.start(this);

        return START_STICKY;
    }

    @Override
    public void onMotionUpdate(float lateralG, float longitudinalG, boolean inVehicle) {
        if (inVehicle && isOverlayVisible) {
            overlayView.updateMotion(lateralG, longitudinalG);
        }
    }

    @Override
    public void onVehicleStateChanged(boolean inVehicle, float speedKmh) {
        motionEngine.setInVehicle(inVehicle);
        if (inVehicle && !isOverlayVisible) {
            showOverlay();
        } else if (!inVehicle && isOverlayVisible) {
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
            CHANNEL_ID, "Motion Cues", NotificationManager.IMPORTANCE_LOW);
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
            .setContentTitle("Motion Cues actif")
            .setContentText("Détection de mouvement en cours")
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
