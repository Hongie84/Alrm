#!/usr/bin/env bash
# Direct Android APK build script — no Android Gradle Plugin required.
# Requires: kotlinc, aapt2, d8, apksigner, zipalign, android.jar (API 23+)

set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/app/src/main"
LIBS="$ROOT/libs"
BUILD="$ROOT/build_out"
ANDROID_JAR="/usr/lib/android-sdk/platforms/android-23/android.jar"
AAPT2="/usr/bin/aapt2"
APKSIGNER="java -jar /usr/lib/android-sdk/build-tools/debian/apksigner.jar"
ZIPALIGN="/usr/lib/android-sdk/build-tools/debian/zipalign"
DX="dalvik-exchange"
KOTLINC="/opt/kotlinc/bin/kotlinc"

echo "=== Step 1: Compile resources with aapt2 ==="
mkdir -p "$BUILD/res_compiled"
find "$APP/res" -name "*.xml" -o -name "*.png" | while read f; do
    $AAPT2 compile "$f" -o "$BUILD/res_compiled/" 2>/dev/null || true
done

echo "=== Step 2: Link resources ==="
mkdir -p "$BUILD/linked"
$AAPT2 link \
    -o "$BUILD/linked/resources.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$APP/AndroidManifest.xml" \
    --java "$BUILD/linked" \
    --auto-add-overlay \
    --min-sdk-version 26 \
    --target-sdk-version 26 \
    $(find "$BUILD/res_compiled" -name "*.flat" | sed 's/^/-R /') \
    2>&1 || { echo "aapt2 link failed"; exit 1; }

echo "=== Step 3: Compile Kotlin sources ==="
mkdir -p "$BUILD/kotlin_classes"
KOTLIN_SOURCES=$(find "$APP/java" -name "*.kt" | tr '\n' ' ')
JAVA_SOURCES=$(find "$BUILD/linked" -name "*.java" | tr '\n' ' ')
CLASSPATH="$ANDROID_JAR:$LIBS/kotlin-stdlib.jar:$LIBS/kotlinx-coroutines-core.jar:$LIBS/kotlinx-coroutines-android.jar"

KOTLINC_OUT=$($KOTLINC $KOTLIN_SOURCES $JAVA_SOURCES \
    -classpath "$CLASSPATH" \
    -d "$BUILD/kotlin_classes" \
    -jvm-target 1.8 \
    -api-version 1.9 \
    2>&1)
KOTLINC_STATUS=$?
echo "$KOTLINC_OUT" | grep -v "^w:" | grep -v "^Picked up" || true
if [ $KOTLINC_STATUS -ne 0 ]; then
    echo "Kotlin compilation failed"; exit 1
fi

echo "=== Step 4: Convert to DEX with dx ==="
mkdir -p "$BUILD/dex"
mkdir -p "$BUILD/jars_clean"
# Strip Java 9+ META-INF/versions from JARs so old dx doesn't choke on them
for jar in "$LIBS/kotlin-stdlib.jar" "$LIBS/kotlinx-coroutines-core.jar" "$LIBS/kotlinx-coroutines-android.jar"; do
    jarname=$(basename "$jar")
    cp "$jar" "$BUILD/jars_clean/$jarname"
    zip -d "$BUILD/jars_clean/$jarname" "META-INF/versions/*" >/dev/null 2>&1 || true
done
CLEAN_JARS="$BUILD/jars_clean/kotlin-stdlib.jar $BUILD/jars_clean/kotlinx-coroutines-core.jar $BUILD/jars_clean/kotlinx-coroutines-android.jar"
$DX --dex \
    --output="$BUILD/dex/classes.dex" \
    --min-sdk-version=26 \
    --core-library \
    $CLEAN_JARS \
    "$BUILD/kotlin_classes" \
    2>&1 | grep -v "^Note:" || true

echo "=== Step 5: Package APK ==="
cd "$BUILD/linked"
rm -rf apk_content
unzip -q resources.apk -d apk_content
cp "$BUILD/dex/classes.dex" apk_content/
cd apk_content
zip -r "$BUILD/alrm_unsigned.apk" . > /dev/null

echo "=== Step 6: Align APK ==="
$ZIPALIGN -f 4 "$BUILD/alrm_unsigned.apk" "$BUILD/alrm_aligned.apk"

echo "=== Step 7: Generate debug keystore if missing ==="
if [ ! -f "$ROOT/debug.keystore" ]; then
    keytool -genkey -v -keystore "$ROOT/debug.keystore" \
        -storepass android -alias androiddebugkey -keypass android \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" 2>/dev/null
fi

echo "=== Step 8: Sign APK ==="
$APKSIGNER sign \
    --ks "$ROOT/debug.keystore" \
    --ks-pass pass:android \
    --ks-key-alias androiddebugkey \
    --key-pass pass:android \
    --out "$BUILD/alrm_debug.apk" \
    "$BUILD/alrm_aligned.apk"

echo ""
echo "=== BUILD SUCCESS ==="
echo "APK: $BUILD/alrm_debug.apk"
ls -lh "$BUILD/alrm_debug.apk"
