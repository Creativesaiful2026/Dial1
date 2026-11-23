# Changelog

## v1.0.0-alpha
- Initial complete DialMe Android application implementation in Kotlin + Jetpack Compose + Material 3.
- Implemented `DialMeInCallService`, `DialMeCallScreeningService`, `InCallActivity`, Dual SIM `TelecomCallPlacer`, and `CallNotificationManager`.
- Implemented Room database (`dialme.db`), DataStore preferences (`dialme_prefs`), Contacts & CallLog reactive observers, T9 search, and `libphonenumber` formatting.
- Added English & Bengali (`values-bn`) translations, custom adaptive launcher icon, and GitHub Actions CI/Release workflows.
