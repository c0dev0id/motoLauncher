# Development Journal

## Software Stack

- **Language:** Kotlin (JVM target 17)
- **UI:** Android classic View system (no Jetpack Compose) — AppCompat + Material 3
  theme, `RecyclerView` + `GridLayoutManager`, view binding.
- **Build:** Gradle 8.9 (Kotlin DSL), AGP 8.7.3, Kotlin 2.0.21.
- **SDK:** compileSdk 35, targetSdk 34, minSdk 30 (device is Android 14, later 16).
- **Async:** kotlinx-coroutines; `lifecycleScope` for update I/O.
- **Networking:** `HttpURLConnection` + `org.json` (no extra dependency) for the update check.
- **Tests:** JUnit4 + Robolectric (JVM unit tests).
- **CI/CD:** GitHub Actions — cannot build locally (no AGP on OpenBSD).

## Key Decisions

- **Views over Compose.** A launcher must cold-start fast and be driven by a 6-key remote.
  The native View focus engine already provides dpad traversal, Enter-to-activate, and
  focus highlighting, and keeps the APK small — a better fit than Compose here.
- **`LauncherApps` (not `PackageManager`).** The launcher-appropriate API for enumerating,
  launching (`startMainActivity`), and opening app info (`startAppDetailsActivity`).
- **Search is touch-only by design.** The `EditText` is `focusableInTouchMode`, so the
  remote's dpad skips it — satisfying "app list is remote-navigable, search is not" with
  no custom key handling.
- **One generic `AppTileAdapter` + `TileItem`.** Home, all-apps, and settings screens each
  compose a `List<TileItem>` (apps, "All Apps", empty slots); the adapter stays dumb.
- **Home tile height computed at runtime.** The 4×3 grid fills exactly 3 rows regardless of
  screen density; the scrollable lists keep the layout's default tile height.
- **Update model: hand-off install against a fixed `dev` tag.** CI deletes and recreates the
  `dev` pre-release each push, so "keep only the latest" needs no cleanup and the app always
  queries one stable endpoint. The APK filename (`motoLauncher-<versionName>.apk`) carries
  the version, compared against `BuildConfig.VERSION_NAME`. Install is user-triggered and
  hands the APK to the system installer via `FileProvider` (`REQUEST_INSTALL_PACKAGES`).
- **Favorites in `SharedPreferences`** as slot→flattened `ComponentName`. 11 slots; the 12th
  grid cell is the fixed "All Apps" tile.

## Core Features

- Fixed 4×3 favorites grid, remote- and glove-operable.
- All-apps browser with touch search.
- Tap-to-launch, long-press for app info.
- Touch configuration of favorite slots.
- User-triggered self-update from GitHub nightly.
