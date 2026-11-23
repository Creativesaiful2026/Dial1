# DialMe Engineering Handoff

## Architecture Overview
- **UI Layer**: Jetpack Compose + Material 3 + Type-Safe Navigation Compose (`DialMeRoute`).
- **State Layer**: `DialMeMainViewModel` + `DialMeAppContainer` constructor injection.
- **Data Layer**: `ContactsRepository`, `CallHistoryRepository`, `DialMeLocalRepository`, `PreferencesRepository`.
- **Persistence**: Room (`dialme.db`, v1) + Preferences DataStore (`dialme_prefs`).
- **Telecom**: `DialMeInCallService`, `DialMeCallScreeningService`, `TelecomCallRepository`, `TelecomCallPlacer`, `InCallActivity`, `CallNotificationManager`.

## Pending Real-Device Verification
- Test `ROLE_DIALER` and `ROLE_CALL_SCREENING` prompts on physical Android 10-16 hardware.
- Verify Dual SIM voice routing, Bluetooth `CallEndpoint`, conference merge, and lock-screen incoming calls on a physical SIM device.
