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
- **Touch-only controls are hidden from dpad via a `TouchOnlyRow` container.** The
  search field, back button, theme and update buttons in the settings header, and every
  keyboard-only affordance in general must not be dpad-reachable — a rider on the road
  can't type or navigate away from a screen without a keyboard. Earlier attempts using
  `focusableInTouchMode` on the `EditText` did not achieve this: an EditText with
  `focusableInTouchMode=true` is also `focusable=true`, and the FocusFinder's dpad path
  ignores the touch-mode flag, so the search field remained a valid dpad target.
  `descendantFocusability="blocksDescendants"` on the container blocks touch focus as
  well (View.requestFocus checks the ancestor chain for that flag) — so the EditText
  would stop opening the IME on tap. `TouchOnlyRow` overrides `addFocusables()` to
  contribute nothing to the focus finder; touch focus keeps working because
  `View.requestFocus()` never consults `addFocusables`.
- **Shared `AppTileAdapter` + `TileItem` for scrollable lists.** The all-apps and settings
  screens compose a `List<TileItem>` (apps, "All Apps", empty slots); the adapter stays
  dumb. Home doesn't use it — see the next entry.
- **Home grid: weighted `LinearLayout`, not `RecyclerView`.** 12 tiles, always visible,
  never scrolling — the RecyclerView lifecycle is the wrong shape. The adapter binds
  before the parent's final size is known, so on cold start the first frame lays tiles
  out at the XML default height and the third row is briefly cut off; the earlier
  workaround was an `addOnLayoutChangeListener` that recomputed row height on every pass.
  Nested weighted `LinearLayout`s (3 rows × 4 tiles, `weight=1` throughout) divide the
  available space during the layout pass by construction — no first-frame race, no
  per-pass recompute, no adapter needed. Tiles are inflated once in `onCreate` and
  rebound in `onResume`. Focus traversal is Android's default (visual proximity), which
  handles a fixed 4×3 grid correctly.
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
  gives the launcher the full canvas.
- **Custom top bar on Home only.** `StatusBarView` is a self-contained widget: it
  registers `ACTION_TIME_TICK`, `ACTION_BATTERY_CHANGED`, a Wi-Fi `NetworkCallback`, and
  (API 31+) `TelephonyCallback.SignalStrengthsListener` in `onAttachedToWindow`, and
  releases them in `onDetachedFromWindow` — no lifecycle wiring in `HomeActivity`.
  Signal-strength and battery icons are `<level-list>` drawables so updates are one
  `setImageLevel()`. The battery level-list encodes charging state in the level number
  itself (0–4 = idle, 5–9 = plugged, five fill buckets each), so plugging in and level
  changes both flow through the same one-call update path.
- **Wi-Fi via `NetworkCallback`, not `WifiManager.connectionInfo`.** Reading RSSI through
  `NetworkCapabilities.transportInfo` avoids `ACCESS_FINE_LOCATION`; only
  `ACCESS_WIFI_STATE` + `ACCESS_NETWORK_STATE` are needed.
- **Back button uses `focusable="false"`.** The `AppList` and `Settings` activities gain
  a visible back button now that the Android nav bar is gone. `focusableInTouchMode` was
  tried first, but on an `ImageButton` the first tap only acquired focus — the click
  needed a second tap. `View.performClick` doesn't require focus, so `focusable="false"`
  is the right knob: touch activates on the first tap, Escape still finishes the
  activity for remote users (the surrounding `TouchOnlyRow` already keeps dpad away).
- **Key-driven long-press is blocked; touch long-press is not.** Long-press opens app
  info / the settings picker — both touch-only destinations a rider can't back out of
  without touching the screen. `View.blockKeyLongPress()` sets an `OnKeyListener` on
  each home tile and each AppList cell that routes DPAD_CENTER / ENTER through
  `performClick()` on ACTION_UP and never arms the framework's key long-press timer.
  `setOnLongClickListener` is untouched, so tapping and holding a tile still works.
- **Escape on Home does nothing; Settings is reached via empty slots.** Home is the
  launcher root — there is nothing for "back" to go to. Once every favourite slot is
  filled a remote-only user has no route into Settings, but that is intentional: the
  device is on a motorbike, configuration is a touch-only workflow. On first-time setup
  the empty "+" tiles route to Settings, which is enough to bootstrap.

## Core Features

- Fixed 4×3 favorites grid, remote- and glove-operable.
- All-apps browser with touch search.
- Tap-to-launch, long-press for app info.
- Touch configuration of favorite slots.
- User-triggered self-update from GitHub nightly.
