# ListenLog - Native Android Folder-Based Music Player & Listening Tracker

ListenLog is a 100% local, folder-based native Android music player with Spotify-style playlists and an advanced listening statistics engine. All data stays strictly on your device—no accounts, no internet, no backend.

---

## Key Features

1. **Folder-Based Library Player**:
   - Automatic navigation starting at the deepest common root directory containing audio files.
   - Pinned path bar displaying current directory location.
   - **Folder Play**: Tap the circle play button on any folder row to play all songs inside it (recursively including subfolders).
   - **Locate Button (🎯)**: Instantly jump to the folder and exact row of the currently playing track.
   - **Sorting**: Toggle between File Name, Song Title, and Artist.
   - **Sleep Timer**: Auto-pause playback after 15, 30, or 60 minutes.
   - **Inline Track Player**: Tap playing track row to expand inline seek bar, 10s skip, and track controls.

2. **Spotify-Style Playlists & Local Folder Exporter**:
   - Pinned `＋ New` playlist creation button pinned outside scrolling lists.
   - Built-in Auto Playlists: **Liked Songs**, **Recently Played**, and **Most Played**.
   - Custom Playlists: Reorder tracks (move up/down) or remove items.
   - **Copy files to folder**: Export a playlist's actual audio files directly into `Music/ListenLog Mixes/<playlist name>/` via MediaStore scoped storage, prefixed as `01 `, `02 ` to preserve your custom mix order.

3. **Listening Statistics Engine**:
   - **Range Chips**: Filter metrics by Today, 7d, 30d, 90d, Year, or All.
   - **Overview Tab**: Listening time, total plays, skips & skip %, average plays/time per active day, current & longest daily streak, and comparison against previous equivalent period.
   - **Custom Bar Charts**: Plays by hour of day (0–23), by weekday (Sun–Sat), and over recent active days.
   - **Top 50 Tabs**: Top Artists (with proportion bars & top songs modal), Top Songs, Top Albums, Most Skipped Songs, and Last 200 Listens History.
   - **CSV Export**: Export listening history to `Downloads/listenlog_history.csv` via MediaStore.

4. **Tag Editing Modal**:
   - Non-destructive tag overrides (Title, Artist, Album) stored in Room database.
   - Automatically retags past listening history retroactively.

5. **Background Playback & Notification**:
   - Built on **AndroidX Media3** (`MediaSessionService` + `ExoPlayer`).
   - System media notification with cover art and playback controls.

---

## Tech Stack & Dependencies

- **Language**: Kotlin 2.0.20
- **UI Framework**: Jetpack Compose (BOM 2024.09.00) with Material 3 Dark Theme
- **Playback Engine**: AndroidX Media3 1.4.1 (`exoplayer`, `session`)
- **Database**: Room Database 2.6.1 with KSP 2.0.20-1.0.25
- **Build System**: Gradle Kotlin DSL with AGP 8.5.2 & JDK 17
- **Target SDK**: minSdk 29, targetSdk 34, compileSdk 34

---

## Build Instructions

### Local Build
To assemble the debug APK locally:
```bash
# Set JAVA_HOME to JDK 17
gradlew assembleDebug
```
The resulting APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

### GitHub Actions Automated CI/CD
A GitHub Actions workflow is provided in `.github/workflows/build.yml`. Every push or pull request to `main` triggers an automated JDK 17 build using `gradle/actions/setup-gradle@v3` and uploads `app-debug.apk` as a build artifact.
