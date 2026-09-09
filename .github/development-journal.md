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
- **Immersive mode app-wide.** System bars are hidden from every activity via
  `WindowInsetsControllerCompat`. The device is a single-purpose launcher on a
  glove-operated screen — nothing on those bars is useful, and reclaiming the pixels
  gives the launcher the full canvas. Note: the row-height layout listener on `homeGrid`
  stays as-is; it exists to bridge the ordering between the first layout pass and
  `adapter.submit` (which runs in `onResume` and can arrive after the first measure), not
  to react to inset changes. Retiring it in favour of a one-shot pre-draw callback set
  the tile height too late and cut the bottom row off.
- **Custom top bar on Home only.** `StatusBarView` is a self-contained widget: it
  registers `ACTION_TIME_TICK`, `ACTION_BATTERY_CHANGED`, a Wi-Fi `NetworkCallback`, and
  (API 31+) `TelephonyCallback.SignalStrengthsListener` in `onAttachedToWindow`, and
  releases them in `onDetachedFromWindow` — no lifecycle wiring in `HomeActivity`.
  Signal-strength icons are `<level-list>` drawables so updates are one `setImageLevel()`.
- **Wi-Fi via `NetworkCallback`, not `WifiManager.connectionInfo`.** Reading RSSI through
  `NetworkCapabilities.transportInfo` avoids `ACCESS_FINE_LOCATION`; only
  `ACCESS_WIFI_STATE` + `ACCESS_NETWORK_STATE` are needed.
- **Back button uses `focusable="false"`, not `focusableInTouchMode`.** The `AppList` and
  `Settings` activities gain a visible back button now that the Android nav bar is gone.
  `focusableInTouchMode` was tried first (to mirror the search box), but on an
  `ImageButton` the first tap only acquired focus — the click needed a second tap.
  `View.performClick` doesn't require focus, so `focusable="false"` is the right knob:
  dpad skips it, touch activates on the first tap, Escape still finishes the activity for
  remote users.

## Core Features

- Fixed 4×3 favorites grid, remote- and glove-operable.
- All-apps browser with touch search.
- Tap-to-launch, long-press for app info.
- Touch configuration of favorite slots.
- User-triggered self-update from GitHub nightly.
