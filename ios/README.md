# AURA for iOS

This directory contains the native SwiftUI iPhone application target.

## Current state

- Native SwiftUI application shell with Assistant, Memory, Guard and Settings tabs.
- No cloud chat transport is included in the preview UI.
- The shared inference, encrypted-memory and recovery contracts still need native iOS adapters.
- iOS cannot provide Android-equivalent remote device locking or unrestricted background monitoring. Lost-device protection must use Apple's supported services and permissions.

## Build

Open `AURA.xcodeproj` in Xcode on macOS and build the `AURA` scheme for an iOS Simulator. A signed device build requires an Apple development team and provisioning profile.
