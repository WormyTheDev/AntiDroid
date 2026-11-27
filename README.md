# AntiDroid

An unofficial, standalone Android APK wrapper for the [Google Antigravity](https://antigravity.google.com) Remote Control web PWA.

> **Disclaimer:** AntiDroid is an independent, unofficial community wrapper and is not affiliated with, endorsed by, or sponsored by Google.

## Features

- **Full-Screen Standalone UI:** Launches `https://antigravity.google.com/` edge-to-edge with a `#070707` status bar matching the dark UI and an immersive transparent navigation bar.
- **In-App Google Sign-In:** Strips WebView markers from the User-Agent and supports persistent cookies and OAuth popups so sign-in works natively inside the app.
- **Deep Link Support:** Opens `https://antigravity.google.com/r/...` Remote Control handoff links directly in AntiDroid while routing external links to your default browser.
- **Web Capabilities:** File uploads, `DownloadManager` downloads, microphone permission forwarding (`RECORD_AUDIO`), hardware back navigation, keyboard inset handling, and an offline retry overlay.

## Building from Source

### Prerequisites
- JDK 17
- Android SDK (API 34 + Build Tools 34.0.0)
- Gradle 8.7+

### 1. Configure SDK & Signing
Create `local.properties` in the project root:
```properties
sdk.dir=/path/to/Android/Sdk
```

Generate a signing keystore (or build an unsigned/debug APK with `gradle assembleDebug`):
```bash
keytool -genkeypair -v -keystore release.jks -alias antidroid \
  -keyalg RSA -keysize 2048 -validity 10000
```

Create `keystore.properties` (gitignored):
```properties
storeFile=release.jks
storePassword=your-store-password
keyAlias=antidroid
keyPassword=your-key-password
```

### 2. Build & Install
```bash
gradle assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```
