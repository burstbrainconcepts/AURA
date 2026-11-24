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
| 6 | Branded, clean interface (white on purple, clear pages, icons) | Home, Chat, Downloads, Notifications, Settings, update and download screens visible, nothing clipped | **PARTIAL** | Splash, welcome and 5-tab navigation exist. The UI is built in code in one 1,434-line `MainActivity.kt`; nav "icons" are Unicode glyphs (⌂ ▢ ⇩ ♧ ⚙) at 9sp, not real icons. Clipping/hidden parts reported by the user are not yet reproduced or fixed. Reference screenshots needed. |
| 7 | Memory | Tell AURA a fact; ask for it in a later session | **PARTIAL** | Encrypted store exists but saving is manual and opt-in ("Remember" button); not automatic recall. |
| 8 | Local-first, no silent cloud | With airplane mode on, chat and voice still work | **BUILT-UNTESTED** | Code avoids cloud speech and downloads the model once from Hugging Face with a SHA-256 check. Untested offline. |
| 9 | Same assistant on Android / iOS / desktop | Core checks above pass on each | **MISSING** | Platforms share design intent, not code. iOS is a simulator-only build; desktop has no wake word. |

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
