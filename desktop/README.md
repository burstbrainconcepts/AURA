# AURA Desktop

Tauri 2 application for Windows, macOS and Linux.

## Implemented

- Assistant interface connected to a local llama.cpp OpenAI-compatible HTTP server through a native Rust bridge.
- Optional speech output through an installed local system voice; no online speech service fallback is used.
- Push-to-talk speech input records through the system microphone and transcribes through a separately configured local whisper.cpp HTTP server. Audio is sent only to the localhost endpoint; the transcript is inserted for review and is never sent automatically.
- Local inference target is restricted to loopback addresses in this build.
- Opt-in encrypted memory vault using AES-256-GCM and PBKDF2-HMAC-SHA256 (310,000 iterations). The user passphrase is required to unlock and is held in application memory only. The vault is stored in the OS application-data directory; on Unix the vault file is restricted to owner read/write.
- Manual memory save, list, delete, lock and full vault deletion. Saved items are included as reference context only while the vault is unlocked.
- Device Guard controller connects to the AURA recovery API, lists enrolled devices, queues explicitly confirmed lock/location commands, clears stored locations and revokes device credentials. The account token is held in application memory only.

## Local inference

Start llama.cpp on the same computer:

```sh
llama-server -m /path/to/model.gguf --host 127.0.0.1 --port 8080 -c 4096
```

Open AURA Settings and configure the local endpoint if it differs from the default `http://127.0.0.1:8080`.

## Offline voice input

AURA includes a helper that initializes the repository's pinned whisper.cpp submodule, builds its HTTP server, downloads the multilingual `base` model if it is missing, and starts it on loopback:

```sh
bash scripts/setup-whisper-server.sh
```

The helper requires Git, CMake, a C++ compiler, and the platform's native build tools. On Windows, run it from Git Bash or WSL with the corresponding C++ toolchain installed. Set `WHISPER_MODEL_NAME=small` for a larger model, `WHISPER_MODEL_PATH` to use an existing GGML model, or `WHISPER_PORT` to change the default port. The initial model download requires internet; transcription after the model is installed runs locally.

AURA captures mono 16-bit PCM and wraps it in a WAV file locally, so the server does not need FFmpeg audio conversion. The multilingual `base` model supports both English and Hausa; use `-l auto` for automatic language detection or `-l ha` to force Hausa. In AURA Settings, set the whisper.cpp URL (default `http://127.0.0.1:8081`). In the Assistant, tap **Mic**, speak, tap **Stop**, review the transcript and then tap **Send**. Audio is sent only to the configured localhost endpoint and recordings are limited to 20 MB. Leaving the Assistant while recording discards the unfinished recording. The operating system may request microphone permission on first use.

## Memory

Open Memory and enter a passphrase of at least 12 characters. The passphrase is never stored by AURA. Use the same passphrase to unlock the vault in future sessions. If the passphrase is lost, the encrypted vault cannot be recovered. Deleting the vault permanently removes its encrypted data.

## Desktop installers

The desktop CI workflow builds three separate native targets:

- **Windows:** Tauri installer bundle (NSIS/MSI where configured by the project).
- **macOS:** Tauri app bundle and DMG where configured by the project.
- **Linux:** Tauri Linux bundles (DEB/RPM/AppImage where configured by the project).

Each platform uploads its own GitHub Actions artifact named `aura-desktop-Windows`, `aura-desktop-macOS`, or `aura-desktop-Linux`. Download the artifact for the operating system you intend to install. These CI artifacts are unsigned development builds unless a signing and notarization workflow is configured; macOS distribution may therefore require additional security approval.

## Build

The GitHub Actions workflow builds Windows, macOS and Linux bundles and uploads artifacts. A successful workflow run is required before distributing the installers.
