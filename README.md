# Crunch ⚡

A modern, high-performance media and video stream downloader for Android, built with **Jetpack Compose**, **Material 3**, and powered by native **yt-dlp**, **FFmpeg**, and **aria2c**.

---

## ✨ Features

- **🚀 Extreme Speed & Multithreading**:
  - Multi-connection segmented downloading via native **aria2c** (4x, 8x, 16x, 32x parallel socket threads).
  - Concurrent DASH and HLS segment/fragment splitting for live streams and segmented video downloads.
- **🔄 In-App Automatic Updater**:
  - Over-the-air (OTA) dynamic updating for the **yt-dlp** engine.
  - Background periodic update checks via **WorkManager** (with Wi-Fi-only preservation).
  - Manual "Check for Updates" in Settings with real-time feedback.
- **🎬 Remux & MKV Optimization**:
  - Automatic remuxing and container conversion into `.mkv` (or `.mp4`) after downloading for maximum compatibility and subtitle preservation.
- **📂 Public Downloads Folder**:
  - Finished files are published to the public `Downloads/Crunch` folder (MediaStore on Android 10+), so they show up in the Files app and gallery.
- **📃 Real Playlist Support**:
  - Playlist links are expanded into individual downloads, all grouped under one playlist id.
- **📲 Seamless System Share Sheet Support**:
  - Tap "Share" from YouTube, Instagram, Facebook, Reddit, TikTok, or browser and select Crunch to instantly parse and fetch video metadata.
- **🔔 Foreground & Completed Notifications**:
  - Live downloading progress bar with download speed and ETA.
  - Completed notifications with direct "Play" and "Share" media actions.
- **🎨 Material 3 & Dynamic Theming**:
  - Dynamic Material You theming that adapts to your device wallpaper.
  - Custom adaptive branding icon and monochrome launcher support.

---

## 🛠️ Tech Stack & Engine Components

- **UI**: Jetpack Compose, Material 3, Compose Navigation 3
- **Core Downloader**: [`youtubedl-android`](https://github.com/junkfood02/youtubedl-android)
  - `yt-dlp` (OTA Dynamic Updatable)
  - `FFmpeg 7.1.1` (Native NDK)
  - `aria2c 1.37.0` (Native NDK)
- **Concurrency & Backgrounding**: Kotlin Coroutines, Flow, AndroidX WorkManager, Foreground Services
- **Database**: AndroidX Room (SQLite)
- **Image Loading**: Coil Compose

---

## 🏗️ Building from Source

```bash
# Clone the repository
git clone https://github.com/cyberlog69/Crunch.git
cd Crunch

# Assemble debug APK (universal, all ABIs)
./gradlew assembleDebug

# Assemble release APKs (minified + per-ABI splits + universal)
./gradlew assembleRelease
```

The debug APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Release builds

Release builds run R8 (`isMinifyEnabled` + `isShrinkResources`), so the app is
shrunk and obfuscated. ABI splits produce one APK per architecture plus a
universal fallback:

```
app/build/outputs/apk/release/app-arm64-v8a-release.apk
app/build/outputs/apk/release/app-armeabi-v7a-release.apk
app/build/outputs/apk/release/app-x86-release.apk
app/build/outputs/apk/release/app-x86_64-release.apk
app/build/outputs/apk/release/app-universal-release.apk
```

Unsigned builds get an `-unsigned` suffix in the file name instead.

Most devices only need `app-arm64-v8a-release.apk`; the universal APK is the
safe fallback for sideloading on unknown hardware.

Signing is optional for local builds. To sign a release, either copy
`keystore.properties.example` to `keystore.properties` (gitignored) and point
`storeFile` at your keystore, or export the `CRUNCH_*` environment variables
listed in that example:

```bash
export CRUNCH_KEYSTORE_FILE=/path/to/release.jks
export CRUNCH_KEYSTORE_PASSWORD=...
export CRUNCH_KEY_ALIAS=crunch
export CRUNCH_KEY_PASSWORD=...
./gradlew assembleRelease
```

Without signing material the release APKs are still produced, but unsigned.

---

## 📄 License
This project is licensed under the Apache 2.0 License.
