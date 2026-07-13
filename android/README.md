# Animal Crossing Android port

This directory packages the native PC port as an SDL2 Android application. It does **not** contain a ROM, game assets, keys, or extracted Nintendo data.

## Current target

- ABI: `armeabi-v7a` (32-bit ARM)
- Graphics: OpenGL ES 3.0
- Minimum Android version: Android 6.0 (API 23)
- Disc images: USA Rev 0 (`GAFE01_00`) in ISO, GCM, or CISO format

The 32-bit ABI is intentional. The decompilation and the PC translation layer store many live pointers in `u32` fields. A safe ARM64 build requires a separate pointer-token/refactoring effort; simply enabling `arm64-v8a` will corrupt pointers at runtime.

## Build in Android Studio

1. Clone with submodules:

   ```bash
   git clone --recursive https://github.com/flyngmt/ACGC-PC-Port.git
   cd ACGC-PC-Port/android
   ```

2. Open the `android` directory in Android Studio.
3. Install Android SDK 34, NDK, and CMake when prompted.
4. Select the `app` configuration and build or run it on a 32-bit-compatible Android device.

Command-line build:

```bash
cd android
./gradlew assembleDebug
```

The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.

## First launch

The launcher asks the user to select their own disc image through Android's Storage Access Framework. It copies the selected file into the app's private storage, along with the runtime shaders. Save data, settings, and shader caches remain private to the app.

The game activity includes transparent multi-touch controls for the main stick, D-pad, C-stick, A/B/X/Y, L/R/Z, and Start. SDL2 gamepads and physical keyboards continue to work.

## GitHub Actions

The `Build Android APK` workflow checks out SDL2, builds the `armeabi-v7a` APK, and uploads it as a workflow artifact. Run it manually from the repository's Actions tab after pushing this branch to a fork or a repository where you have write access.
