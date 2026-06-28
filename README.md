# Magnatune for Android

Native Android client for [magnatune.com](https://magnatune.com) — a Kotlin / Jetpack Compose /
Media3 port of the SwiftUI iOS app. Browse the full Magnatune catalog, stream AAC, manage
favorites and playlists, and (for members) stream the no-announcement and lossless tiers.

## Build

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # JDK 21
export ANDROID_HOME=~/Library/Android/sdk
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease        # release APK (configure signing first)
```

Requires Android SDK 35, minSdk 26. Pure Kotlin — no NDK.

## Architecture

- **UI** — Jetpack Compose + Material3 (Catalyst light look).
- **Audio** — Media3 ExoPlayer in a foreground `MediaSessionService` (background audio, lock-screen
  controls), dual-player crossfade.
- **Data** — read-only catalog SQLite (downloaded from he3, replaced wholesale on refresh) +
  read-write Room user DB (favorites / dislikes / playlists / history).
- **Images** — Coil. **Login** — EncryptedSharedPreferences.
- **Cast** + **LAN peer sync** (NSD) for multi-device control.

All Magnatune media is HTTP-only, so a `network_security_config` permits cleartext to
`*.magnatune.com` only.
