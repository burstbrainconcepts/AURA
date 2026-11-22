# AURA Android

The Android project is a native Kotlin application shell (minSdk 24, targetSdk 35) with a basic chat interface for a local llama.cpp server exposing the OpenAI-compatible `/v1/chat/completions` endpoint at `http://localhost:8080`.

The app permits cleartext traffic only for the localhost inference endpoint. It does not upload conversation text to a cloud service. It currently expects the local inference engine to be supplied separately; llama.cpp is not yet compiled into the APK, and no model weights are bundled. Therefore the APK can be built in the cloud, but chat generation requires a compatible local engine to be running.

Builds are configured in GitHub Actions. The debug APK artifact is produced at `android/app/build/outputs/apk/debug/`.
