# AURA Presence Mode

AURA's Presence Mode keeps the full assistant interface out of the way until the user explicitly summons it.

## Android

- Add the **Open AURA** Quick Settings tile from the tile editor for a system-level shortcut.
- Optionally enable **Floating AURA bubble** inside AURA.
- Android will ask for the special “Display over other apps” permission. The user can decline and continue using the Quick Settings tile.
- The floating bubble contains only an **AURA** launch action and a **×** dismiss action. It does not capture audio, inspect other app content, or start a microphone session.
- To remove it, tap **×** or return to AURA and disable it.

The overlay is an optional launcher surface, not a persistent voice assistant daemon. Android may stop ordinary background services under memory or battery pressure; the Quick Settings tile remains the reliable summon path.

## iPhone and iPad

- Use the Siri/App Shortcut phrases **Open AURA** or **Start AURA**.
- AURA opens as a normal app and can be dismissed normally.
- iOS does not permit a third-party app to create a persistent system-wide overlay above other apps or hide itself from the App Library programmatically.
- Voice input begins only after the user taps the voice control. AURA does not use hidden always-on listening.

## First-run setup

Presence controls are available after installation. The Android model download remains a separate, user-confirmed setup step. Presence Mode does not require the model to be downloaded just to launch AURA; AI conversation requires a ready inference model or configured inference endpoint.

## iOS app icon

The iOS asset catalog uses a 1024px AppIcon generated at build time by `ios/scripts/generate_app_icon.swift`. The generator is native Swift/AppKit, so the CI build does not need a third-party image package.
