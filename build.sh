#!/bin/bash
# Build Motion Cues APK on ARM64 without Gradle
# Uses Debian's ARM64-native aapt2/d8/apksigner + Google SDK's android.jar

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
ANDROID_DIR="$PROJECT_DIR/android/app/src/main"
SRC_DIR="$ANDROID_DIR/java"
RES_DIR="$ANDROID_DIR/res"
MANIFEST="$ANDROID_DIR/AndroidManifest.xml"

# Tools
AAPT2=/usr/bin/aapt2
JAVAC=/usr/bin/javac
D8=$HOME/android-sdk/build-tools/34.0.0/d8
ZIPALIGN=/usr/bin/zipalign
APKSIGNER=/usr/bin/apksigner

# Android platform
ANDROID_JAR=$HOME/android-sdk/platforms/android-34/android.jar

# Output dirs
BUILD_DIR="$PROJECT_DIR/build"
GEN_DIR="$BUILD_DIR/gen"
OBJ_DIR="$BUILD_DIR/obj"
CLASSES_DIR="$BUILD_DIR/classes"
APK_DIR="$BUILD_DIR/apk"

export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64

echo "=== Motion Cues APK Build ==="
echo ""

# Clean
rm -rf "$BUILD_DIR"
mkdir -p "$GEN_DIR" "$OBJ_DIR" "$CLASSES_DIR" "$APK_DIR"

# Step 1: Compile resources with aapt2
echo "[1/6] Compiling resources..."
FLAT_FILES=""
for res_type_dir in "$RES_DIR"/*/; do
    if [ -d "$res_type_dir" ]; then
        for res_file in "$res_type_dir"*; do
            if [ -f "$res_file" ]; then
                $AAPT2 compile "$res_file" -o "$OBJ_DIR/"
            fi
        done
    fi
done

# Step 2: Link resources + generate R.java
echo "[2/6] Linking resources..."
FLAT_LIST=$(find "$OBJ_DIR" -name "*.flat" -type f)
$AAPT2 link \
    -I "$ANDROID_JAR" \
    --manifest "$MANIFEST" \
    --java "$GEN_DIR" \
    -o "$APK_DIR/app-unaligned.apk" \
    --auto-add-overlay \
    $FLAT_LIST

# Step 3: Compile Java sources
echo "[3/6] Compiling Java sources..."
JAVA_FILES=$(find "$SRC_DIR" -name "*.java" -type f)
GEN_FILES=$(find "$GEN_DIR" -name "*.java" -type f 2>/dev/null)
$JAVAC \
    --release 11 \
    -classpath "$ANDROID_JAR" \
    -d "$CLASSES_DIR" \
    $JAVA_FILES $GEN_FILES \
    2>&1 || {
        echo "ERROR: Java compilation failed"
        exit 1
    }

# Step 4: Convert to DEX
echo "[4/6] Converting to DEX..."
mkdir -p "$BUILD_DIR/dex"
CLASS_FILES=$(find "$CLASSES_DIR" -name "*.class" -type f)
$D8 \
    --min-api 29 \
    --lib "$ANDROID_JAR" \
    --output "$BUILD_DIR/dex/" \
    $CLASS_FILES

# Step 5: Add DEX to APK
echo "[5/6] Packaging APK..."
cd "$BUILD_DIR/dex"
zip -j "$APK_DIR/app-unaligned.apk" classes.dex
cd "$PROJECT_DIR"

# Align
$ZIPALIGN -f 4 "$APK_DIR/app-unaligned.apk" "$APK_DIR/app-aligned.apk"

# Step 6: Sign APK
echo "[6/6] Signing APK..."
KEYSTORE="$PROJECT_DIR/motion-cues.keystore"
if [ ! -f "$KEYSTORE" ]; then
    echo "  Generating debug keystore..."
    keytool -genkeypair \
        -keystore "$KEYSTORE" \
        -alias motioncues \
        -keyalg RSA -keysize 2048 \
        -validity 10000 \
        -storepass android \
        -keypass android \
        -dname "CN=Motion Cues, O=Dev, C=FR"
fi

$APKSIGNER sign \
    --ks "$KEYSTORE" \
    --ks-key-alias motioncues \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$APK_DIR/motion-cues.apk" \
    "$APK_DIR/app-aligned.apk"

# Copy to server releases
mkdir -p "$PROJECT_DIR/server/releases"
cp "$APK_DIR/motion-cues.apk" "$PROJECT_DIR/server/releases/"

# Update version manifest
VERSION_CODE=$(grep -oP 'android:versionCode="\K[^"]+' "$MANIFEST" 2>/dev/null || echo "1")
VERSION_NAME=$(grep -oP 'android:versionName="\K[^"]+' "$MANIFEST" 2>/dev/null || echo "1.0.0")
cat > "$PROJECT_DIR/server/releases/latest.json" << EOFJ
{"versionCode": $VERSION_CODE, "versionName": "$VERSION_NAME", "filename": "motion-cues.apk"}
EOFJ

APK_SIZE=$(du -h "$APK_DIR/motion-cues.apk" | cut -f1)
echo ""
echo "=== BUILD SUCCESS ==="
echo "APK: $APK_DIR/motion-cues.apk ($APK_SIZE)"
echo "Also copied to: server/releases/motion-cues.apk"
