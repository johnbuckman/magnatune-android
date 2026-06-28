# Magnatune for Android

A native Android client for [magnatune.com](https://magnatune.com) — Kotlin / Jetpack Compose /
Media3. It's a port of the SwiftUI iOS app: browse the whole Magnatune catalog, stream AAC, manage
favorites and playlists, and (for members) stream the no-announcement / lossless tiers and download
albums.

![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)

## Features

- **Browse** the full catalog: Popular (per-genre rows), Artists, Albums, Genres (with icons), Tags,
  Featured (curated playlists), and Search.
- **Playback** via Media3/ExoPlayer with background audio, lock-screen / bluetooth controls, a
  persistent mini-player and a Now Playing screen, queue, shuffle, and **crossfade** between tracks
  (dual-player engine, toggle in Settings).
- **Favorites & dislikes**, user **playlists**, add/remove-to-playlist.
- **Membership**: sign in to stream the no-announcement and lossless tiers and download albums/songs.
- **Auto-download favorites** for offline playback.
- **Casting**: Google Cast and (discovery for) Apple AirPlay — two icons that appear only when a
  device of that type is on the network.
- **LAN peer sync**: discover and remote-control other Magnatune instances on the same Wi-Fi.

## Requirements

- Android 8.0 (API 26) or newer.
- All Magnatune media is served over HTTP, so the app ships a network-security config that permits
  cleartext only to `*.magnatune.com`.

## Build

Prerequisites:

- **JDK 21** (the build is tested with Android Studio's bundled JBR).
- **Android SDK 35** with build-tools 35.x. Point the build at it via `local.properties`
  (`sdk.dir=/path/to/Android/sdk`) or the `ANDROID_HOME` env var.
- No NDK required — the app is pure Kotlin.

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # or any JDK 21
export ANDROID_HOME="$HOME/Library/Android/sdk"

# Debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleDebug

# Install + launch on a connected device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.magnatune.player/.MainActivity
```

Or open the project in **Android Studio** (Giraffe+), let it sync, and Run.

### Release build (signed)

Create `local.keystore.properties` in the project root (it is git-ignored):

```properties
storeFile=/absolute/path/to/your-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Then:

```sh
./gradlew :app:assembleRelease       # -> app/build/outputs/apk/release/app-release.apk
```

Without that file the release build is produced unsigned.

## Architecture

- **UI** — Jetpack Compose + Material 3 (the iOS "Catalyst" light look). Adaptive sidebar layout.
- **Audio** — `CrossfadePlayer` (a Media3 `SimpleBasePlayer` wrapping two ExoPlayers) inside a
  foreground `MediaSessionService`.
- **Data** — read-only catalog SQLite downloaded from he3 (replaced wholesale on refresh) + a
  read-write Room user DB (favorites / dislikes / playlists / history / downloads).
- **Images** Coil · **Credentials** EncryptedSharedPreferences · **Cast** Media3 CastPlayer ·
  **AirPlay/peer discovery** Android NSD.

## License

This project is licensed under the **GNU General Public License v3.0** — see [LICENSE](LICENSE).

Magnatune content (music, catalog, artwork) belongs to Magnatune and its artists and is used under
Magnatune's terms; the GPL applies to this app's source code only.
