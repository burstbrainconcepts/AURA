# AURA for iOS

Native SwiftUI application target for iPhone and iPad.

## Product approach

AURA does not hard-code a permanent list of operating-system restrictions into its product architecture. Platform capabilities are discovered and evaluated at runtime through native adapters. The implementation and release process will verify current OS versions, device classes, account types, developer-program terms, entitlements, managed-device options, contractual agreements and approvals that apply at that time.

Where a capability requires an entitlement, agreement, managed deployment, partner arrangement or platform-team approval, track it as an integration dependency and pursue the applicable process. Do not silently remove the product capability from the roadmap. If a platform does not grant it in a particular deployment context, report that state accurately and provide the best supported alternative.

## Current state

- Native SwiftUI application shell with Assistant, Memory, Guard and Settings tabs.
- Preview UI does not yet connect to the shared inference, encrypted-memory or recovery services.
- Native adapters and a capability registry are planned for model runtime, secure storage, voice, background execution, device management and recovery.
- Capability states should distinguish available, approval-required, configured, unavailable and temporarily unavailable.

## Build

Open `AURA.xcodeproj` in Xcode on macOS and build the `AURA` scheme for an iOS Simulator. A signed device build requires the applicable Apple developer account, signing configuration and provisioning profile.
