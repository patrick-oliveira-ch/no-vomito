# Motion Cues Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build an Android overlay app that displays animated dots on screen edges responding to vehicle motion, with OTA updates from a self-hosted server.

**Architecture:** Native Android app (Java) with a foreground service that draws an overlay Canvas. Accelerometer + GPS provide motion data. A lightweight Express server on the Pi serves OTA config/assets.

**Tech Stack:** Java, Android SDK CLI, Gradle, Canvas API, SensorManager, LocationManager, Node.js (update server)

---

### Task 1: Setup Android Build Environment on Pi

**Step 1: Install JDK 17**
```bash
sudo apt-get install -y openjdk-17-jdk-headless
java -version
```
Expected: `openjdk version "17.x.x"`

**Step 2: Download Android SDK command-line tools**
```bash
mkdir -p ~/android-sdk/cmdline-tools
cd /tmp
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-*.zip
mv cmdline-tools ~/android-sdk/cmdline-tools/latest
```

**Step 3: Set environment variables**
Add to `~/.bashrc`:
```bash
export ANDROID_HOME=$HOME/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
```

**Step 4: Install SDK components**
```bash
source ~/.bashrc
yes | sdkmanager --licenses
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

**Step 5: Verify build works with a minimal project**
Create a bare-bones Android project, run `./gradlew assembleDebug`, confirm APK is produced.

**Step 6: Commit**
```bash
git add -A && git commit -m "chore: setup Android build environment"
```

---

### Task 2: Create Android Project Skeleton

**Files:**
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/java/com/motioncues/MainActivity.java`
- Create: `android/app/build.gradle`
- Create: `android/build.gradle`
- Create: `android/settings.gradle`
- Create: `android/gradle.properties`
- Create: `android/gradle/wrapper/gradle-wrapper.properties`

**Step 1: Create project structure**

`android/build.gradle` (root):
```groovy
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.2.0'
    }
}
allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
```

`android/settings.gradle`:
```groovy
include ':app'
```

`android/gradle.properties`:
```
android.useAndroidX=true
org.gradle.jvmargs=-Xmx1536m
```

`android/app/build.gradle`:
```groovy
plugins {
    id 'com.android.application'
}
android {
    namespace 'com.motioncues'
    compileSdk 34
    defaultConfig {
        applicationId "com.motioncues"
        minSdk 29
        targetSdk 34
        versionCode 1
        versionName "1.0.0"
    }
    buildTypes {
        release {
            minifyEnabled false
        }
        debug {
            applicationIdSuffix ".debug"
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}
dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.gms:play-services-location:21.0.1'
}
```

`android/app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION"/>
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
    <uses-permission android:name="android.permission.HIGH_SAMPLING_RATE_SENSORS"/>
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>

    <application
        android:allowBackup="false"
        android:label="Motion Cues"
        android:theme="@style/Theme.AppCompat.DayNight.NoActionBar"
        android:icon="@mipmap/ic_launcher">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>

        <service
            android:name=".OverlayService"
            android:foregroundServiceType="location"
            android:exported="false"/>
    </application>
</manifest>
```

`android/app/src/main/java/com/motioncues/MainActivity.java`:
```java
package com.motioncues;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("Motion Cues - skeleton");
        setContentView(tv);
    }
}
```

**Step 2: Generate Gradle wrapper**
```bash
cd android
gradle wrapper --gradle-version 8.5
```

**Step 3: Build and verify APK**
```bash
./gradlew assembleDebug
ls -la app/build/outputs/apk/debug/
```
Expected: `app-debug.apk` exists

**Step 4: Commit**
```bash
git add -A && git commit -m "feat: create Android project skeleton"
```

---

### Task 3: Implement Motion Engine (Sensor Data Processing)

**Files:**
- Create: `android/app/src/main/java/com/motioncues/MotionEngine.java`

**Step 1: Write MotionEngine**

```java
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
    private float lateralG = 0f;      // left-right (virage)
    private float longitudinalG = 0f;  // front-back (accel/frein)
    private boolean inVehicle = false;
    private static final float ALPHA = 0.15f; // low-pass filter

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

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            // X = lateral (left/right), Y = longitudinal (forward/back)
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
```

**Step 2: Build and verify**
```bash
cd android && ./gradlew assembleDebug
```

**Step 3: Commit**
```bash
git add -A && git commit -m "feat: implement motion engine with accelerometer"
```

---

### Task 4: Implement Vehicle Detection (GPS)

**Files:**
- Create: `android/app/src/main/java/com/motioncues/VehicleDetector.java`

**Step 1: Write VehicleDetector**

```java
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
            LocationManager.GPS_PROVIDER, 1000, 0, this);
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

        if (listener != null && wasInVehicle != currentlyInVehicle) {
            listener.onVehicleStateChanged(currentlyInVehicle, speedKmh);
        }
    }

    @Override public void onStatusChanged(String p, int s, Bundle e) {}
    @Override public void onProviderEnabled(String p) {}
    @Override public void onProviderDisabled(String p) {}
}
```

**Step 2: Build and verify**
```bash
cd android && ./gradlew assembleDebug
```

**Step 3: Commit**
```bash
git add -A && git commit -m "feat: implement GPS vehicle detection"
```

---

### Task 5: Implement Dots Renderer (Canvas Overlay View)

**Files:**
- Create: `android/app/src/main/java/com/motioncues/DotsOverlayView.java`

**Step 1: Write DotsOverlayView**

This is the core visual component. Draws 8-12 dots on screen edges that shift based on motion data.

```java
package com.motioncues;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;

public class DotsOverlayView extends View {
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float lateralOffset = 0f;    // pixels, from accelerometer
    private float longitudinalOffset = 0f;
    private int dotColor = 0xFF333333;   // default dark gray
    private float dotRadius = 8f;
    private float dotAlpha = 0.7f;
    private int screenW, screenH;
    private static final int DOTS_PER_SIDE = 5;
    private static final float MAX_OFFSET = 30f;  // max dot displacement in px
    private static final float SENSITIVITY = 12f;  // px per m/s²

    public DotsOverlayView(Context ctx) {
        super(ctx);
        dotPaint.setStyle(Paint.Style.FILL);
        DisplayMetrics dm = new DisplayMetrics();
        ((WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE))
            .getDefaultDisplay().getMetrics(dm);
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        dotRadius = dm.density * 4f;
    }

    public void updateMotion(float lateralG, float longitudinalG) {
        lateralOffset = clamp(lateralG * SENSITIVITY, -MAX_OFFSET, MAX_OFFSET);
        longitudinalOffset = clamp(longitudinalG * SENSITIVITY, -MAX_OFFSET, MAX_OFFSET);
        invalidate();
    }

    public void setDotColor(int color) {
        this.dotColor = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        dotPaint.setColor(dotColor);
        dotPaint.setAlpha((int)(dotAlpha * 255));

        float margin = dotRadius * 2;
        float spacing = (screenH - margin * 2) / (DOTS_PER_SIDE + 1);

        for (int i = 1; i <= DOTS_PER_SIDE; i++) {
            float baseY = margin + spacing * i;
            float y = baseY + longitudinalOffset;

            // Left edge dots
            float lx = margin + lateralOffset;
            canvas.drawCircle(lx, y, dotRadius, dotPaint);

            // Right edge dots
            float rx = screenW - margin - lateralOffset;
            canvas.drawCircle(rx, y, dotRadius, dotPaint);
        }

        // Top edge dots (2 dots)
        float topSpacing = screenW / 3f;
        for (int i = 1; i <= 2; i++) {
            float x = topSpacing * i + lateralOffset;
            float ty = margin + longitudinalOffset;
            canvas.drawCircle(x, ty, dotRadius, dotPaint);
        }

        // Bottom edge dots (2 dots)
        for (int i = 1; i <= 2; i++) {
            float x = topSpacing * i + lateralOffset;
            float by = screenH - margin - longitudinalOffset;
            canvas.drawCircle(x, by, dotRadius, dotPaint);
        }
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
```

**Step 2: Build and verify**
```bash
cd android && ./gradlew assembleDebug
```

**Step 3: Commit**
```bash
git add -A && git commit -m "feat: implement dots overlay renderer"
```

---

### Task 6: Implement Overlay Foreground Service

**Files:**
- Create: `android/app/src/main/java/com/motioncues/OverlayService.java`

**Step 1: Write OverlayService**

Ties everything together: starts sensors, GPS, draws overlay.

```java
package com.motioncues;

import android.app.*;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

public class OverlayService extends Service
    implements MotionEngine.MotionListener, VehicleDetector.VehicleListener {

    private static final String CHANNEL_ID = "motion_cues_channel";
    private static final int NOTIF_ID = 1;

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
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
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
        stopIntent.setAction("STOP");
        PendingIntent stopPi = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Motion Cues actif")
            .setContentText("Détection de mouvement en cours")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .addAction(android.R.drawable.ic_media_pause, "Arrêter", stopPi)
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
```

**Step 2: Build and verify**
```bash
cd android && ./gradlew assembleDebug
```

**Step 3: Commit**
```bash
git add -A && git commit -m "feat: implement overlay foreground service"
```

---

### Task 7: Implement MainActivity (Permissions + Controls + Settings)

**Files:**
- Modify: `android/app/src/main/java/com/motioncues/MainActivity.java`
- Create: `android/app/src/main/res/layout/activity_main.xml`
- Create: `android/app/src/main/res/values/strings.xml`
- Create: `android/app/src/main/res/values/colors.xml`
- Create: `android/app/src/main/res/mipmap-hdpi/ic_launcher.png` (placeholder)

**Step 1: Write layout XML**

`activity_main.xml`: Simple UI with start/stop toggle, color picker, status text.

**Step 2: Write MainActivity with permissions flow**

Handle SYSTEM_ALERT_WINDOW permission, location permission, start/stop service, color selection (6 colors + gray).

**Step 3: Build, install on device, test**
```bash
cd android && ./gradlew assembleDebug
```

**Step 4: Commit**
```bash
git add -A && git commit -m "feat: implement main activity with permissions and settings"
```

---

### Task 8: Implement OTA Update System

**Files:**
- Create: `android/app/src/main/java/com/motioncues/UpdateChecker.java`
- Create: `server/update-server.js`
- Create: `server/package.json`

**Step 1: Write update server (Node.js on Pi)**

`server/update-server.js`:
```javascript
const express = require('express');
const path = require('path');
const fs = require('fs');
const app = express();

const APK_DIR = path.join(__dirname, 'releases');

app.get('/api/version', (req, res) => {
    const manifest = JSON.parse(
        fs.readFileSync(path.join(APK_DIR, 'latest.json'), 'utf8'));
    res.json(manifest);
});

app.get('/api/download', (req, res) => {
    const manifest = JSON.parse(
        fs.readFileSync(path.join(APK_DIR, 'latest.json'), 'utf8'));
    res.download(path.join(APK_DIR, manifest.filename));
});

app.listen(7777, '0.0.0.0', () => {
    console.log('Update server on :7777');
});
```

`server/releases/latest.json`:
```json
{"versionCode": 1, "versionName": "1.0.0", "filename": "motion-cues.apk"}
```

**Step 2: Write UpdateChecker (Android)**

Downloads APK from server and triggers install intent.

**Step 3: Commit**
```bash
git add -A && git commit -m "feat: implement OTA update system"
```

---

### Task 9: Build Script + Deploy

**Files:**
- Create: `build.sh` (builds APK, copies to server/releases, updates latest.json)

**Step 1: Write build script**

```bash
#!/bin/bash
cd android && ./gradlew assembleRelease
VERSION=$(grep versionName app/build.gradle | grep -oP '\d+\.\d+\.\d+')
cp app/build/outputs/apk/release/app-release-unsigned.apk \
   ../server/releases/motion-cues-${VERSION}.apk
echo "{\"versionCode\": $(grep versionCode app/build.gradle | grep -oP '\d+'), \"versionName\": \"$VERSION\", \"filename\": \"motion-cues-${VERSION}.apk\"}" > ../server/releases/latest.json
echo "Built v$VERSION"
```

**Step 2: Test full flow**
1. Build APK
2. Start update server
3. Install APK on phone
4. Bump version, rebuild
5. Verify app detects update

**Step 3: Commit**
```bash
git add -A && git commit -m "feat: add build script and deploy pipeline"
```
