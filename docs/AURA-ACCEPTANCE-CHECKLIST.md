# AURA acceptance checklist

The single definition of "done" for AURA. A feature counts only when it is
**Works** (tested on a real device or by an automated test) — never because a
build passed or the code exists.

Status key: **WORKS** (tested) · **BUILT-UNTESTED** (code exists, nobody has run it on a device) · **PARTIAL** · **MISSING**

Last audited: 2026-11-24, Android only, by reading source (nothing run on a phone).

## What AURA must be

A personal AI assistant, closer to Siri/Google Assistant but with its own AI brain, memory and voice, local-first, on Android, iPhone and desktop.

## Android audit

| # | Requirement | Acceptance test (on a real phone) | Status | Finding from code |
|---|---|---|---|---|
| 1 | Real AI assistant | Ask for an email draft, a code snippet, a summary and a translation; answers are useful | **PARTIAL** | Embedded llama.cpp JNI is present, but the only model is Qwen2.5-0.5B (~400 MB) with replies capped at 120 tokens and the last 4 turns of context. Expect weak writing/coding/research. A larger model option is needed. |
| 2 | Wake word "Hey AURA" | With the app closed and screen off, say "Hey AURA"; AURA responds within ~2 s | **BUILT-UNTESTED / at risk** | Not a wake-word engine. `AuraWakeWordService` loops Android's `SpeechRecognizer` and string-matches the result. Needs Android 12+ (API 31) with on-device recognition (app minSdk is 24, so older phones get nothing). It then calls `startActivity` from a background service, which Android 10+ usually blocks; code falls back to a notification. Continuous recognizer loops also drain battery. openWakeWord is approved in the manifest but not integrated. |
| 3 | Hands-free conversation | After a reply is spoken, AURA listens again without a tap | **MISSING** | `sendMessage` speaks the reply, then only re-arms the wake service. No listen-after-speech loop and no TTS-completion callback. |
| 4 | Silence detection | Stop talking; AURA stops listening on its own and answers | **PARTIAL** | Relies on the recognizer's own 5 s silence timeouts. Works only if the recognizer behaves; untested. |
| 5 | Sleep / wake commands | Say "go to sleep", "be quiet", "continue listening", "end conversation" | **BUILT-UNTESTED** | Phrases are handled in `MainActivity.kt` (~line 91–100). Untested. |
| 6 | Branded, clean interface (white on purple, clear pages, icons) | Onboarding (Welcome, Setup, Download, Complete) then Home, Chat, Downloads, Notifications, Settings visible, nothing clipped | **BUILT-UNTESTED** | Home, Chat, Downloads (tabs, model cards), Notifications and Settings pages already existed in source. Added this session, not yet compiled or seen on a phone: real vector icons in the bottom bar (labels now Home/Chat/Downloads/Notifs/Settings) and a full-screen first-launch flow (Welcome, Setup permissions, Download AI Model with live progress and Pause/Resume, Download Complete). The cloud-run button in the reference is intentionally replaced by "Not now — explore first" (AURA is local-first). Still open: Downloads/Notifications tabs are not yet interactive; Voice screen orb is a text glyph; Settings is still a list of buttons. Phone screenshots from the user showed an older APK build, not this code. |
| 7 | Memory | Tell AURA a fact; ask for it in a later session | **PARTIAL** | Encrypted store exists but saving is manual and opt-in ("Remember" button); not automatic recall. |
| 8 | Local-first, no silent cloud | With airplane mode on, chat and voice still work | **BUILT-UNTESTED** | Code avoids cloud speech and downloads the model once from Hugging Face with a SHA-256 check. Untested offline. |
| 9 | Same assistant on Android / iOS / desktop | Core checks above pass on each | **MISSING** | Platforms share design intent, not code. iOS is a simulator-only build; desktop has no wake word. |


## Product direction (clarified by the user, 24 Nov)

AURA is **not primarily a chat app**. It should (1) record and understand the user's voice, (2) intelligently store it (what was said, how it sounded, and who is speaking), and (3) live as a floating bubble: the user closes the app, taps the bubble from any app, and AURA answers, by voice when voice replies are on.

| Stage | Capability | Status |
|---|---|---|
| 1 | Floating bubble that listens on tap, answers with the on-device model, speaks the reply, then listens again; shared model between app and bubble | **BUILT-UNTESTED** (compiles via CI; Android 14 background-microphone rules may block it; needs phone test) |
| 2 | Record on tap (raw audio), transcribe on device (whisper.cpp), summarise and tag with the model, encrypted searchable store, "what did I say about X?" by voice | **MISSING** |
| 3 | Vocal profile from the audio: pitch, loudness, pace, pauses. Honest label: acoustic descriptors, not emotion claims | **MISSING** |
| 4 | Speaker recognition (is this my voice?) via a speaker-embedding model. Convenience only, never authorisation | **MISSING** |

## Known platform limits (state honestly, do not hide)

- **iOS** does not allow third-party apps unrestricted always-on background listening. Best achievable: foreground voice plus Siri/App Shortcuts.
- **Android** wake listening must run as a visible foreground service with a notification.
- **Desktop** can run a background listener, but it is not yet built.

## Work order

1. Fix the Android interface against the user's reference screens (needs the screenshots).
2. Make the Android voice loop reliable: listen → answer → speak → listen again, sleep and wake.
3. Replace the SpeechRecognizer wake loop with a real wake-word engine (openWakeWord) and fix launching from the background.
4. Add a larger, selectable model so answers are actually useful.
5. Automatic memory recall.
6. Bring iOS and desktop to the same checklist.

After every step: update the Status column above and state what was tested and what was not.
