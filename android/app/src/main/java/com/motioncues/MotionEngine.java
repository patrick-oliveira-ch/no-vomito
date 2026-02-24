package com.motioncues;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;

/**
 * Orientation-independent motion engine.
 *
 * Uses rotation vector sensor to transform accelerometer from phone frame
 * to world frame (North/East/Up), then uses GPS bearing to rotate into
 * vehicle frame (Forward/Right). Works regardless of how the phone is held.
 */
public class MotionEngine implements SensorEventListener, Runnable {
    public interface MotionListener {
        void onMotionUpdate(float motionX, float motionY);
    }

    private final SensorManager sensorManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MotionListener listener;
    private boolean running = false;

    // Raw accelerometer in device frame (smoothed)
    private float rawAccelX = 0f;
    private float rawAccelY = 0f;
    private float rawAccelZ = 0f;

    // Rotation matrix from rotation vector sensor
    private final float[] rotationMatrix = new float[9];
    private boolean hasRotation = false;

    // GPS data
    private float gpsSpeedKmh = 0f;
    private float gpsBearingRad = 0f;
    private boolean hasBearing = false;

    // Vehicle-frame acceleration (computed each frame)
    private float vehicleForward = 0f;  // positive = accelerating
    private float vehicleRight = 0f;    // positive = turning right

    // Low-pass filter
    private static final float ACCEL_ALPHA = 0.15f;

    // Output (smoothed for display)
    private float outX = 0f;
    private float outY = 0f;
    private static final float SMOOTH = 0.08f;
    private static final float DECAY = 0.96f;

    public MotionEngine(SensorManager sm) {
        this.sensorManager = sm;
    }

    public void start(MotionListener listener) {
        this.listener = listener;
        this.running = true;

        // Accelerometer (without gravity)
        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if (accel != null) {
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
        }

        // Rotation vector — gives phone orientation in world frame
        Sensor rot = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rot != null) {
            sensorManager.registerListener(this, rot, SensorManager.SENSOR_DELAY_GAME);
        }

        // 60fps display refresh
        mainHandler.postDelayed(this, 16);
    }

    public void stop() {
        running = false;
        sensorManager.unregisterListener(this);
        mainHandler.removeCallbacks(this);
    }

    public void onGpsUpdate(Location loc) {
        if (loc.hasSpeed()) {
            gpsSpeedKmh = loc.getSpeed() * 3.6f;
        }
        if (loc.hasBearing()) {
            gpsBearingRad = (float) Math.toRadians(loc.getBearing());
            hasBearing = true;
        }
    }

    public float getGpsSpeedKmh() {
        return gpsSpeedKmh;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            rawAccelX += (event.values[0] - rawAccelX) * ACCEL_ALPHA;
            rawAccelY += (event.values[1] - rawAccelY) * ACCEL_ALPHA;
            rawAccelZ += (event.values[2] - rawAccelZ) * ACCEL_ALPHA;
        } else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            hasRotation = true;
        }
    }

    @Override
    public void run() {
        if (!running) return;

        if (hasRotation && hasBearing) {
            // === Transform accel from phone frame to world frame ===
            // rotationMatrix maps device coords to world coords (East, North, Up)
            // Row 0 = East, Row 1 = North, Row 2 = Up
            float worldEast  = rotationMatrix[0] * rawAccelX
                             + rotationMatrix[1] * rawAccelY
                             + rotationMatrix[2] * rawAccelZ;
            float worldNorth = rotationMatrix[3] * rawAccelX
                             + rotationMatrix[4] * rawAccelY
                             + rotationMatrix[5] * rawAccelZ;

            // === Rotate world frame to vehicle frame using GPS bearing ===
            // bearing: 0=North, 90=East (clockwise from North)
            float cosB = (float) Math.cos(gpsBearingRad);
            float sinB = (float) Math.sin(gpsBearingRad);

            // Forward = projection onto bearing direction
            // Right = perpendicular to bearing (positive = right)
            vehicleForward = worldNorth * cosB + worldEast * sinB;
            vehicleRight   = -worldNorth * sinB + worldEast * cosB;
        }

        // === Accel-based motion (vehicle frame → screen) ===
        float speedFactor = gpsSpeedKmh / 60f;
        float accelX = vehicleRight * 0.06f * (1f + speedFactor * 2f);
        float accelY = -vehicleForward * 0.06f;

        // === Speed-based continuous scroll ===
        // Project travel direction onto phone screen axes
        // Travel direction in world: East=sin(bearing), North=cos(bearing)
        // R maps device→world, so R^T maps world→device
        // deviceX = R[0]*East + R[3]*North  (screen right)
        // deviceY = R[1]*East + R[4]*North  (screen up)
        float speedScroll = gpsSpeedKmh * 0.008f;
        float speedScrollX = 0f;
        float speedScrollY = 0f;

        if (hasRotation && hasBearing) {
            float travelEast = (float) Math.sin(gpsBearingRad);
            float travelNorth = (float) Math.cos(gpsBearingRad);

            // Project onto phone screen axes
            float screenRight = rotationMatrix[0] * travelEast + rotationMatrix[3] * travelNorth;
            float screenUp = rotationMatrix[1] * travelEast + rotationMatrix[4] * travelNorth;

            // Dots should flow opposite to travel direction on screen
            // screenRight > 0 = travel goes right on screen → dots go right (positive X)
            // screenUp > 0 = travel goes up on screen → dots go down (positive Y)
            speedScrollX = -speedScroll * screenRight;
            speedScrollY = speedScroll * screenUp;
        } else {
            // Fallback: assume forward = screen down
            speedScrollY = speedScroll;
        }

        float targetX = accelX + speedScrollX;
        float targetY = accelY + speedScrollY;

        // Smooth towards target
        outX += (targetX - outX) * SMOOTH;
        outY += (targetY - outY) * SMOOTH;

        // Gentle decay on accel components, speed scroll keeps going
        outX *= DECAY;
        outY *= DECAY;

        if (listener != null) {
            listener.onMotionUpdate(outX, outY);
        }

        mainHandler.postDelayed(this, 16);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
