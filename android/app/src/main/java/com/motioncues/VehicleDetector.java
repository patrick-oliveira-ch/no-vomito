package com.motioncues;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

public class VehicleDetector implements LocationListener {
    public interface VehicleListener {
        void onVehicleStateChanged(boolean inVehicle, float speedKmh);
        void onGpsLocation(Location loc);
    }

    private final LocationManager locationManager;
    private VehicleListener listener;
    private static final float VEHICLE_THRESHOLD_KMH = 10f;
    private static final float EXIT_THRESHOLD_KMH = 5f;
    private boolean currentlyInVehicle = false;

    public VehicleDetector(Context ctx) {
        this.locationManager = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
    }

    @SuppressLint("MissingPermission")
    public void start(VehicleListener listener) {
        this.listener = listener;
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, 300, 0, this);
    }

    public void stop() {
        locationManager.removeUpdates(this);
    }

    @Override
    public void onLocationChanged(Location loc) {
        float speedKmh = loc.getSpeed() * 3.6f;
        boolean wasInVehicle = currentlyInVehicle;

        if (!currentlyInVehicle && speedKmh > VEHICLE_THRESHOLD_KMH) {
            currentlyInVehicle = true;
        } else if (currentlyInVehicle && speedKmh < EXIT_THRESHOLD_KMH) {
            currentlyInVehicle = false;
        }

        if (listener != null) {
            // Always send GPS data for motion calculation
            listener.onGpsLocation(loc);
            if (wasInVehicle != currentlyInVehicle) {
                listener.onVehicleStateChanged(currentlyInVehicle, speedKmh);
            }
        }
    }

    @Override public void onStatusChanged(String p, int s, Bundle e) {}
    @Override public void onProviderEnabled(String p) {}
    @Override public void onProviderDisabled(String p) {}
}
