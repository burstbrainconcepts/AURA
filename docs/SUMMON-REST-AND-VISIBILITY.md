# AURA summon, rest and visibility experience

This is the product specification for a user-controlled assistant presence. It is deliberately not a stealth or covert-surveillance feature.

## Activation gate
Do not offer app-hiding or reduced-visibility controls until first-run model downloads, integrity checks and runtime setup have completed successfully. Keep normal launcher access available during setup and recovery.

## Shared interaction model
- **Visible:** Full AURA app and conversation.
- **Resting:** Main interface is closed; no microphone capture is active.
- **Summoned:** User explicitly invokes AURA using a supported platform shortcut, assistant integration or visible control.
- **Dismissed:** Close the assistant surface and stop any active microphone session.
- **Recover:** A permanent, documented route remains available to reopen the full app and disable reduced-visibility settings.

## iOS implementation
- Keep the app discoverable in iOS Settings and App Library.
- Provide App Intents / App Shortcuts for user-configured Siri phrases, such as “Open AURA” and “Start an AURA conversation”, subject to the supported iOS APIs.
- Provide a user guide for Apple's own Hide App / Remove from Home Screen controls where supported. AURA cannot programmatically hide itself from SpringBoard or make itself invisible to the device owner.
- Do not promise a system-wide floating overlay or a custom always-listening wake word. iOS does not grant ordinary third-party apps those capabilities.
- Voice capture starts only after an explicit user action or a platform-authorized invocation, with clear microphone state and permission.

## Android implementation
- Keep the normal launcher activity and app-info visibility available by default.
- Offer an optional, clearly explained reduced-visibility mode only if a supported launcher-alias approach is implemented and tested. Never remove the user's only recovery route.
- Provide a Quick Settings tile and notification action as reliable summon/reopen paths where platform versions permit.
- Explore Android conversation bubbles / floating assistant surfaces only with the appropriate user-visible permissions and platform requirements.
- Any foreground microphone service must show the required persistent system notification and stop immediately when dismissed.
- No covert microphone capture, hidden background recording, deceptive app identity, or bypass of Android permission indicators.

## Privacy and safety requirements
- AURA must never silently enable microphone capture when hidden or resting.
- Show a clear listening indicator and an immediate stop control.
- Explain exactly what activation methods are enabled.
- Make reduced visibility opt-in, reversible, and off by default.
- Do not disguise AURA as another app or prevent the device owner from uninstalling or inspecting it.

## Acceptance tests
1. Initial setup and model download remain fully visible and recoverable.
2. Reduced-visibility controls appear only after successful setup.
3. Every platform has a tested, documented route to reopen AURA.
4. Dismissal stops microphone capture and any temporary foreground service.
5. Siri/App Shortcut invocation works only through supported iOS integrations.
6. Android tile, notification or bubble behaviour is tested across supported Android versions and launchers.
7. Hiding/removing a home-screen icon never deletes user data or disables recovery.
