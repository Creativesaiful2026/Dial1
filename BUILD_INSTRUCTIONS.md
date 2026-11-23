# Mobile-Only Build & Testing Instructions

## Building via GitHub Actions (Smartphone Only)
1. Upload the repository contents to your GitHub repository.
2. Open the **Actions** tab on GitHub (or GitHub Mobile App) and select **DialMe CI**.
3. Wait for the workflow to complete and download `DialMe-debug-apk` from **Artifacts**.
4. Extract `app-debug.apk` using your phone's Files app and install it.

## Android 13-16 Sideloaded APK Setup
If Android displays *"Restricted setting"* when granting Default Phone App or Call Screening roles:
1. Open **Settings -> Apps -> DialMe**.
2. Tap the **3-dot menu** in the top-right corner and select **Allow restricted settings**.
3. Return to DialMe and tap **Set Default**.
