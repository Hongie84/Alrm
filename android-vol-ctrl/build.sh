#!/usr/bin/env bash
# Builds a signed debug APK straight from aapt2 + kotlinc + dx, with no Gradle and
# no Android Gradle Plugin. The app deliberately uses only framework classes, so
# the Kotlin standard library is the single dependency.
#
# Needs: aapt2, kotlinc, dx (or dalvik-exchange), zipalign, apksigner, android.jar.
# Every path below can be overridden with an environment variable of the same name.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/app/src/main"
LIBS="$ROOT/libs"
BUILD="$ROOT/build_out"

ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
BUILD_TOOLS="${BUILD_TOOLS:-/usr/lib/android-sdk/build-tools/debian}"
AAPT2="${AAPT2:-$(command -v aapt2 || echo "$BUILD_TOOLS/aapt2")}"
ZIPALIGN="${ZIPALIGN:-$(command -v zipalign || echo "$BUILD_TOOLS/zipalign")}"
APKSIGNER_JAR="${APKSIGNER_JAR:-$BUILD_TOOLS/apksigner.jar}"
DX="${DX:-$(command -v dalvik-exchange || command -v dx || echo "$BUILD_TOOLS/dx")}"
KOTLINC="${KOTLINC:-$(command -v kotlinc)}"

MIN_SDK=26
TARGET_SDK=26
APK_NAME=volctrl

for tool in "$ANDROID_JAR" "$AAPT2" "$ZIPALIGN" "$APKSIGNER_JAR" "$DX" "$KOTLINC"; do
    [ -e "$tool" ] || { echo "Missing build tool: $tool" >&2; exit 1; }
done

# Prefer a stdlib vendored in libs/, else the one shipped alongside kotlinc, so a
# checkout without the jar still builds. Must match the compiler's own version.
if [ -z "${KOTLIN_STDLIB:-}" ]; then
    for candidate in \
        "$LIBS/kotlin-stdlib.jar" \
        "$(dirname "$(readlink -f "$KOTLINC")")/../lib/kotlin-stdlib.jar" \
        /usr/share/java/kotlin-stdlib.jar
    do
        [ -f "$candidate" ] && { KOTLIN_STDLIB="$candidate"; break; }
    done
fi
[ -n "${KOTLIN_STDLIB:-}" ] || {
    echo "kotlin-stdlib.jar not found; set KOTLIN_STDLIB or drop it in libs/" >&2
    exit 1
}
echo "Using kotlin-stdlib: $KOTLIN_STDLIB"

rm -rf "$BUILD"
mkdir -p "$BUILD"/{res_compiled,linked,kotlin_classes,dex,jars_clean}

echo "=== 1/8 Compile resources ==="
find "$APP/res" \( -name "*.xml" -o -name "*.png" \) -print0 |
    xargs -0 -I{} "$AAPT2" compile {} -o "$BUILD/res_compiled/"

echo "=== 2/8 Link resources ==="
# --java emits R.java; its constants are inlined by kotlinc, so R never reaches the dex.
FLAT_ARGS=()
while IFS= read -r flat; do
    FLAT_ARGS+=(-R "$flat")
done < <(find "$BUILD/res_compiled" -name "*.flat")
"$AAPT2" link \
    -o "$BUILD/linked/resources.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$APP/AndroidManifest.xml" \
    --java "$BUILD/linked" \
    --auto-add-overlay \
    --min-sdk-version "$MIN_SDK" \
    --target-sdk-version "$TARGET_SDK" \
    "${FLAT_ARGS[@]}"

echo "=== 3/8 Compile Kotlin ==="
# Kotlin 1.5+ lowers lambdas with invokedynamic, which Android's older runtimes
# cannot link (NoSuchMethodError on LambdaMetafactory). Force class-based lambdas
# where the compiler is new enough to know the flags.
KOTLIN_EXTRA_FLAGS=()
if "$KOTLINC" -X 2>&1 | grep -q -- "-Xlambdas"; then
    KOTLIN_EXTRA_FLAGS+=(-Xlambdas=class -Xsam-conversions=class)
fi

mapfile -t KOTLIN_SOURCES < <(find "$APP/java" -name "*.kt")
mapfile -t JAVA_SOURCES < <(find "$BUILD/linked" -name "*.java")

"$KOTLINC" \
    "${KOTLIN_SOURCES[@]}" \
    "${JAVA_SOURCES[@]}" \
    -classpath "$ANDROID_JAR:$KOTLIN_STDLIB" \
    -d "$BUILD/kotlin_classes" \
    -jvm-target 1.8 \
    -nowarn \
    "${KOTLIN_EXTRA_FLAGS[@]}"

echo "=== 4/8 Dex ==="
# dx chokes on the multi-release entries modern JARs carry, so drop them first.
cp "$KOTLIN_STDLIB" "$BUILD/jars_clean/kotlin-stdlib.jar"
zip -d "$BUILD/jars_clean/kotlin-stdlib.jar" "META-INF/versions/*" >/dev/null 2>&1 || true

"$DX" --dex \
    --output="$BUILD/dex/classes.dex" \
    --min-sdk-version="$MIN_SDK" \
    --core-library \
    "$BUILD/jars_clean/kotlin-stdlib.jar" \
    "$BUILD/kotlin_classes"

echo "=== 5/8 Package ==="
rm -rf "$BUILD/apk_content"
unzip -q "$BUILD/linked/resources.apk" -d "$BUILD/apk_content"
cp "$BUILD/dex/classes.dex" "$BUILD/apk_content/"
(cd "$BUILD/apk_content" && zip -qr "$BUILD/${APK_NAME}_unsigned.apk" .)

echo "=== 6/8 Align ==="
"$ZIPALIGN" -f 4 "$BUILD/${APK_NAME}_unsigned.apk" "$BUILD/${APK_NAME}_aligned.apk"

echo "=== 7/8 Debug keystore ==="
if [ ! -f "$ROOT/debug.keystore" ]; then
    keytool -genkeypair -v -keystore "$ROOT/debug.keystore" \
        -storepass android -alias androiddebugkey -keypass android \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" >/dev/null
fi

echo "=== 8/8 Sign ==="
java -jar "$APKSIGNER_JAR" sign \
    --ks "$ROOT/debug.keystore" \
    --ks-pass pass:android \
    --ks-key-alias androiddebugkey \
    --key-pass pass:android \
    --out "$BUILD/${APK_NAME}_debug.apk" \
    "$BUILD/${APK_NAME}_aligned.apk"

echo
echo "=== BUILD SUCCESS ==="
ls -lh "$BUILD/${APK_NAME}_debug.apk"
