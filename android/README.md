# AURA Android

AURA is a native Kotlin Android app with a CPU-first llama.cpp JNI runtime.

## Current Android implementation

- The GitHub Actions workflow fetches the pinned llama.cpp revision and compiles it through the Android NDK/CMake toolchain.
- The app offers an explicit first-run download of the Qwen2.5 0.5B Instruct Q4_K_M GGUF starter model (about 398 MB).
- The model download is verified against its published SHA-256 before native loading.
- Chat inference runs in-process through JNI. Conversation text is not sent to a remote inference API.
- The model uses a 2,048-token context and a conservative CPU thread limit.
- Optional speech input uses Android's on-device speech recognizer only; AURA does not fall back to a cloud recognizer.
- Speech output uses an installed voice only when Android reports that voice does not require a network connection.
- Memory is off by default. The user can explicitly enable it, save an individual exchange, review saved items, clear them, or disable memory. The Android memory store uses AES-GCM with a key held by Android Keystore.

## Known runtime boundaries

- The starter model is deliberately small; response quality and speed are lower than larger hosted models.
- On-device speech recognition and offline TTS depend on compatible language services/voices already installed on the phone.
- A device with insufficient free RAM may fail to load the model.
- Device protection, background recovery delivery, and privileged device-admin actions are separate features and are not enabled by this assistant shell.
- The debug APK is not a production-signed release.

## Cloud build

The **Android cloud build** GitHub Actions workflow fetches the exact pinned llama.cpp commit, installs Android NDK/CMake, runs `gradle :app:assembleDebug`, and uploads `aura-android-debug` as a workflow artifact.
