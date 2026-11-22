# AURA Android

The Android application is a native Kotlin launcher scaffold (minSdk 24, targetSdk 35). It intentionally does not claim device-admin, location, microphone, or background privileges until the corresponding user-facing feature and permission flow are implemented.

Build on GitHub Actions with the **Android cloud build** workflow. A local build, if desired, is `gradle -p android :app:assembleDebug`. The debug APK is written to `android/app/build/outputs/apk/debug/`.
