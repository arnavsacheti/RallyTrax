# RallyTrax

**Drive. Record. Replay. Co-Drive.**

RallyTrax is a native Android app that turns any road into a rally stage. Record your drives with high-precision GPS, let the app generate rally-style pace notes from your track geometry, then replay your routes with a synthetic co-driver calling out turns, crests, and hazards ahead of time — just like a professional rally navigator.

[![CI](https://github.com/arnavsacheti/RallyTrax/actions/workflows/ci.yml/badge.svg)](https://github.com/arnavsacheti/RallyTrax/actions/workflows/ci.yml)

---

## Who It's For

- **Driving enthusiasts** who want to revisit and share favourite routes
- **Amateur motorsport participants** wanting pace-note practice on public roads
- **Travel & road-trip users** looking for audio-guided replays of scenic drives

## Features

### Recording
- Real-time GPS track recording with live map, speed, distance, and elevation
- Foreground service keeps recording when the screen is off
- Pause/resume support with multi-segment tracks
- Auto-generates pace notes when recording finishes

### Pace Note Engine
- Analyses track geometry to produce rally-style notes (turns, severity 1–6, crests, dips, hairpins, straights)
- Configurable sensitivity (Low / Medium / High)
- Detects modifiers like *tightens*, *opens*, *long*, *don't cut*, *keep in*
- Computes speed-adaptive call distances (80 m–300 m)

### Replay & Co-Driver
- Immersive dark-mode HUD with Google Maps, live speed, and progress bar
- TTS co-driver with configurable pitch and speech rate
- Audio ducking so pace-note calls blend over music
- Chime earcon before each call for attention
- Off-route warning and finish detection

### Library & Data
- Search, sort, and tag your track library
- Swipe-to-delete with undo, multi-select batch delete
- GPX import/export with custom pace-note extensions
- "Open with" support — tap a `.gpx` file to import it directly

### Settings & Personalisation
- Theme: System / Light / Dark (Material You dynamic colours on Android 12+)
- Units: Metric (km, km/h, m) or Imperial (mi, mph, ft)
- GPS accuracy mode: High Accuracy or Battery Saver
- Co-driver voice: adjustable speech rate and pitch
- In-app update checker via GitHub Releases

### Home Dashboard
- Weekly distance and monthly recording count at a glance
- Quick access to the 5 most recent tracks
- One-tap record button

## Screenshots

*Coming soon*

## Requirements

| | Minimum | Target |
|---|---|---|
| **Android** | 10 (API 29) | 15 (API 35) |
| **Google Play Services** | Required (Maps + Location) |

A Google Maps API key is needed for map features. Set it in `local.properties`:

```properties
MAPS_API_KEY=your_key_here
```

Or provide it as an environment variable (`MAPS_API_KEY`).

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.1 |
| UI | Jetpack Compose + Material 3 |
| Navigation | Compose Navigation (type-safe routes with kotlinx.serialization) |
| DI | Hilt |
| Database | Room (SQLite) |
| Preferences | DataStore |
| Maps | Google Maps SDK for Compose |
| Location | FusedLocationProvider (Play Services) |
| Audio | Android TextToSpeech + ToneGenerator + AudioFocusRequest |
| Build | Gradle 8.9 with version catalog (`libs.versions.toml`) |

## Architecture

MVVM with a clear separation between data, domain, and presentation layers.

```
app/src/main/java/com/rallytrax/app/
├── data/           # Room entities, DAOs, DataStore preferences, GPX parser/exporter
├── di/             # Hilt modules (Database, Preferences)
├── navigation/     # Screen routes & NavHost setup
├── pacenotes/      # Pace note generation pipeline
├── recording/      # TrackingService, foreground notification, GPS capture
├── replay/         # ReplayEngine, ReplayAudioManager, ReplayViewModel
├── ui/             # Compose screens (Home, Library, Recording, Replay, Settings, Onboarding, TrackDetail)
├── update/         # In-app update checker (GitHub Releases API)
└── util/           # FormatUtils (unit-aware formatting)
```

## Building

```bash
# Clone
git clone https://github.com/arnavsacheti/RallyTrax.git
cd RallyTrax

# Add your Maps API key
echo "MAPS_API_KEY=your_key" >> local.properties

# Build debug APK
./gradlew assembleDebug

# Run lint + unit tests
./gradlew lint test
```

> **Note:** Requires JDK 17+ and the Android SDK (set `ANDROID_HOME` or use Android Studio).

## CI / CD

GitHub Actions runs on every push to `main` and on pull requests:

1. **Lint** and **unit tests**
2. **Auto-increment** patch version (on `main` pushes without a manual bump)
3. **Tag** the new version (`v{major}.{minor}.{patch}`)
4. **Build** debug + release APKs
5. **Create a GitHub Release** with APK artifacts and auto-generated changelog

Download the latest APK from the [Releases](https://github.com/arnavsacheti/RallyTrax/releases) page.

## License

*To be determined.*
