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

Build whisper.cpp with its HTTP server and FFmpeg audio conversion enabled, then start it with a downloaded local speech model, for example:

```sh
./build/bin/whisper-server -m ./models/ggml-base.en.bin --host 127.0.0.1 --port 8081
```

If the server is built without FFmpeg conversion, browser-recorded WebM/OGG audio may not be accepted. Enable FFmpeg support in the whisper.cpp build or use a WAV recording format. In AURA Settings, set the whisper.cpp URL (default `http://127.0.0.1:8081`). In the Assistant, tap **Mic**, speak, tap **Stop**, review the transcript and then tap **Send**. The native bridge accepts only localhost endpoints and limits recordings to 20 MB. The operating system may request microphone permission on first use.

## Memory

Open Memory and enter a passphrase of at least 12 characters. The passphrase is never stored by AURA. Use the same passphrase to unlock the vault in future sessions. If the passphrase is lost, the encrypted vault cannot be recovered. Deleting the vault permanently removes its encrypted data.

## Build

The GitHub Actions workflow builds Windows, macOS and Linux bundles and uploads artifacts. A successful workflow run is required before distributing the installers.
