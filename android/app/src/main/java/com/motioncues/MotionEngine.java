package com.motioncues;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class MotionEngine implements SensorEventListener {
    public interface MotionListener {
        void onMotionUpdate(float lateralG, float longitudinalG, boolean inVehicle);
    }

    private final SensorManager sensorManager;
    private MotionListener listener;
    private float lateralG = 0f;
    private float longitudinalG = 0f;
    private boolean inVehicle = false;
    private static final float ALPHA = 0.15f;

    public MotionEngine(SensorManager sm) {
        this.sensorManager = sm;
    }

    public void start(MotionListener listener) {
        this.listener = listener;
        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if (accel != null) {
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI);
        }
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    public void setInVehicle(boolean v) {
        this.inVehicle = v;
    }

    public boolean isInVehicle() {
        return inVehicle;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            lateralG = lateralG + ALPHA * (event.values[0] - lateralG);
            longitudinalG = longitudinalG + ALPHA * (event.values[1] - longitudinalG);
            if (listener != null) {
                listener.onMotionUpdate(lateralG, longitudinalG, inVehicle);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
