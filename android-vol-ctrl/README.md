# Vol Ctrl

An Android volume control app: every audio stream on one screen, a ringer mode
switch, and named presets you can save and re-apply.

## What it does

- **Per-stream sliders** for Media, Ring, Notification, Alarm, System and Call,
  each with a live `current / max` readout and a one-tap mute toggle that
  remembers the level to restore.
- **Ringer mode switching** between Normal, Vibrate and Silent, with the active
  mode highlighted.
- **Presets** — save the current volumes plus ringer mode under a name, then
  re-apply the whole set with one tap. Tap a preset to apply it, long press to
  delete it. Presets are stored as JSON in `SharedPreferences`.
- **Stays in sync** with changes made elsewhere. A `ContentObserver` on
  `Settings.System` picks up the hardware volume keys and other apps, and a
  receiver for `RINGER_MODE_CHANGED_ACTION` tracks the ringer.

## Do Not Disturb access

From API 23 on, silencing the ring or notification stream — and any ringer mode
change into or out of silent — throws a `SecurityException` unless the user has
granted Do Not Disturb access. The app never assumes it succeeded: every
mutating call in `VolumeController` reports a boolean, a banner on the main
screen offers to open the grant screen, and rejected changes roll the sliders
back to the system's real values.

Grant it under **Settings → Apps → Special app access → Do Not Disturb access**,
or through the in-app banner and the "Do Not Disturb access" menu item.

## Building

The project builds a signed debug APK directly from `aapt2`, `kotlinc` and `dx`
— no Gradle and no Android Gradle Plugin, so it needs nothing from a Maven
repository at build time. The app uses only framework classes, which leaves the
Kotlin standard library as its single dependency.

```sh
./build.sh
```

The APK lands in `build_out/volctrl_debug.apk`. On Debian or Ubuntu the tools
come from:

```sh
apt-get install -y android-sdk-platform-23 aapt android-sdk-build-tools \
                   dalvik-exchange kotlin
```

`build.sh` finds `kotlin-stdlib.jar` on its own — a copy in `libs/` wins,
otherwise it takes the one shipped next to `kotlinc`, so a fresh checkout builds
with nothing vendored. Point `KOTLIN_STDLIB` at a specific jar to override that;
whichever you use has to match the compiler's version. Every tool path is
likewise overridable by an environment variable of the same name (`ANDROID_JAR`,
`AAPT2`, `KOTLINC`, `DX`, `ZIPALIGN`, `APKSIGNER_JAR`, `BUILD_TOOLS`).

Step 7 generates `debug.keystore` on first run if it is absent — a throwaway
debug key with the standard `android` password. Keep that file if you want later
builds to sign with the same certificate so a new APK installs over an older
one. It is not a release key: do not publish anything signed with it.

## Layout

```
app/src/main/java/com/volctrl/
  audio/StreamType.kt        the six streams, with label and icon
  audio/VolumeController.kt  AudioManager wrapper; all SecurityException handling
  preset/Preset.kt           a named snapshot of volumes + ringer mode
  preset/PresetStore.kt      JSON persistence in SharedPreferences
  ui/MainActivity.kt         sliders, mute toggles, ringer buttons
  ui/PresetsActivity.kt      preset list: tap to apply, long press to delete
```

Min SDK 26, target SDK 26, compiled against the API 23 platform JAR. The UI is
built from framework widgets against the built-in Material theme, with a
`values-night` palette for dark mode, so there is no support library at runtime.
