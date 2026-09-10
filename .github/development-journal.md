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
  search field, back button, theme / update / cellular buttons in the All Apps header, and every
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
- **`AppTileAdapter` + `TileItem` for the scrollable app list.** The all-apps screen
  composes a `List<TileItem>`; the adapter stays dumb. Home doesn't use it — see the
  next entry.
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
  Cache hygiene is two rules: on process start (`MotoLauncherApp`, background thread)
  only the APK matching the running `VERSION_NAME` is deleted — that file was consumed by
  the install that produced this process, whereas a different version might still be
  open in the installer if the process was restarted to serve it through the
  `FileProvider`; and `download()` removes every other file before writing, so the
  directory holds at most the download in progress.
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
- **Signal meters need a dedicated "unlit" colour, and the platform's rating range is
  inclusive.** Two separate faults made the status bar's Wi-Fi and cellular meters
  unreadable, both found from a screenshot of the running device. First, the unlit bars
  were drawn in `on_surface_muted` — a colour built for secondary *text*, which in the
  dark theme (`#B8C0CC` against `#FFFFFF`) leaves lit and unlit bars about 1.85:1 apart,
  so full signal and no signal looked nearly the same. The unlit segments now use their
  own `signal_track` token, far enough from `on_surface` to read at a glance while still
  showing where the missing bars would be; `on_surface_muted` could not simply be darkened
  because secondary text and the "+" / "None" tile glyphs share it. Second,
  `WifiManager.calculateSignalLevel` returns a rating in `[0, maxSignalLevel]`
  *inclusive* — five values on a typical device, not four. Rescaling with
  `maxSignalLevel - 1` as the divisor therefore reported a 3-of-4 signal as full and made
  the three-bar icon unreachable. `StatusBarView.wifiIconLevel` is now a pure function
  over (rating, platform maximum) so the mapping is unit-tested at both ends. Cellular
  needs no rescale: `SignalStrength.getLevel()` is documented as 0..4 and matches the
  icon's five states directly.
- **No Wi-Fi is shown by absence, not by an empty meter.** The zero state has to mean one
  thing. It used to mean both "connected, signal gone" and "there is no Wi-Fi network at
  all" — and on a device that is mostly offline the second is both the more common case
  and the more useful to know. Visibility now keys off network presence (`onAvailable` /
  `onLost`), never off the signal level, and the meter is emptied while hidden so a
  reconnect cannot flash the previous strength in the gap before the first capabilities
  callback lands. A set of live networks is tracked rather than a boolean because losing
  one of two Wi-Fi networks must not hide an indicator the other still earns; the set is
  cleared on detach because re-registering the callback replays `onAvailable` for networks
  that are already up, and a stale entry would leave the icon hidden for good. The icon
  starts `gone` in the layout: at cold start nothing is known until the first callback.
- **Wi-Fi via `NetworkCallback`, not `WifiManager.connectionInfo`.** Reading RSSI through
  `NetworkCapabilities.transportInfo` avoids `ACCESS_FINE_LOCATION`; only
  `ACCESS_WIFI_STATE` + `ACCESS_NETWORK_STATE` are needed.
- **Key-driven long-press is blocked; touch long-press is not.** Long-press opens app
  info / the slot picker — both touch-only destinations a rider can't back out of
  without touching the screen. `View.blockKeyLongPress()` sets an `OnKeyListener` on
  each home tile and each AppList cell that routes DPAD_CENTER / ENTER through
  `performClick()` on ACTION_UP and never arms the framework's key long-press timer.
  `setOnLongClickListener` is untouched, so tapping and holding a tile still works.
- **Home tile long-press opens a tile-styled menu (`TileActionsDialog`).** Rows in order:
  App info, Uninstall (system uninstaller via `ACTION_DELETE`, no permission needed),
  Reassign app. The app list's long-press shows the same dialog without the Reassign row
  (there is no slot), so one dialog serves both screens. Reassigning a favourite without the menu meant a detour through
  Settings; app info alone was not worth a long-press. The menu is a plain `Dialog` with a custom layout, not an
  `AlertDialog`: the Material3 dialog theme brings its own surface colours and small
  buttons, whereas the custom view reuses `tile_background` and the launcher palette so
  it reads as part of the grid and stays glove-sized; its chrome (background drawable,
  minimum width) is `Theme.MotoLauncher.Dialog` in `themes.xml`, not code. Every dialog
  goes through `Dialog.showImmersive()` because a dialog is a separate window and would
  otherwise bring the system bars back while showing.
- **The app picker writes the favourite slot itself.** Pick mode is
  `AppListActivity.pickIntent(context, slot)`: on selection the activity stores the
  component in `FavoritesStore` and finishes. A "None" tile leads the grid in pick mode
  regardless of the search text; choosing it clears the slot — the only way to empty a
  favourite, since there is no settings screen. Home just `startActivity`s and rebuilds
  its grid in `onResume` — no `StartActivityForResult`, no result extra, no "which slot
  was I picking for" state in the caller. The earlier version carried that launcher and
  sentinel in both Home and the since-removed Settings screen.
- **A short Escape on Home does nothing; slots are configured in place.** Home is the
  launcher root — there is nothing for "back" to go to. A remote-only user has no route
  into any configuration, and that is intentional: the device is on a motorbike, configuration is
  a touch-only workflow. Empty "+" tiles and the tile menu's "Reassign app" open the
  picker for that slot directly. Theme toggle, update check, and the cellular-permission
  ask sit in the All Apps header (hidden in pick mode). There is no separate settings
  screen any more — the earlier Configure Favorites activity duplicated all of this and
  was removed.
- **Holding Escape launches the first favourite (`ui/KeyInput.kt`, `EscapeKeys`).** The
  remote has one spare key and no spare gesture; ESC was already the only key that meant
  anything outside a tile. A hold is the one input left that can be given a meaning
  without making anything new dpad-reachable, and the meaning stays inside the rule that
  the remote may only ever launch apps: it starts slot 0, the top-left tile, on both Home
  and the app list, so the gesture means one thing wherever the remote is. Detection is
  the framework's own tracking, not a timer of ours: `onKeyDown` claims the DOWN and calls
  `event.startTracking()`, which is what makes Android deliver `onKeyLongPress` on the
  first key repeat (~500 ms); returning true there marks the press consumed, so the
  following UP arrives canceled. The cost is that the short press had to move from
  `onKeyDown` to `onKeyUp` — at DOWN it isn't yet known whether the press will become a
  long one — so the app list now closes on key release. An empty slot or an uninstalled
  app is a silent no-op (`AppRepository.launchIfInstalled` gates on `isActivityEnabled`,
  because `startMainActivity` throws on a component that no longer exists): there is
  nothing worth showing a rider wearing gloves. This depends on the remote reporting a
  held key at all; a button that emits an instantaneous down/up pair can't produce a long
  press, and then only the short press works.

- **Update UI: button label for progress, dialogs for outcomes (`ui/UpdateFlow.kt`).**
  The header row of the app list has no room for a status line, and a transient line is
  easy to miss on a handlebar-mounted screen. `runUpdateFlow(button)` shows "Checking…" /
  "Downloading…" on the button while disabled, and each result (latest build, update
  available with install prompt, error) is an `AlertDialog`. Stock alert buttons are
  acceptable here, unlike the tile menu, because updating is an off-bike, touch-only
  task. `AlertDialog.Builder(context)` picks the launcher look up from
  `alertDialogTheme` (`ThemeOverlay.MotoLauncher.Dialog.Alert`); no call site names a
  theme.
- **App list survives the theme toggle's recreate via a ViewModel.** Enumerating and
  rasterising every installed app is the most expensive thing the app does. With the
  theme toggle on the same screen, `AppCompatDelegate.setDefaultNightMode` recreates the
  activity; `AppListViewModel` holds the load as a `Deferred`, so the recreated activity
  awaits the same result instead of re-running it. The first render after a recreate
  applies the restored search text.
- **Header controls: one container, one style.** The All Apps header's config buttons
  sit in a `headerConfig` group toggled once for pick mode, and share
  `Widget.MotoLauncher.HeaderButton` so the 56dp glove target is stated once.
- **Dotted style names that are roots must set `parent=""`.** Android treats
  `Widget.MotoLauncher.Foo` as inheriting from `Widget.MotoLauncher` unless an explicit
  parent is given. `Widget.MotoLauncher.DialogAction` is intentionally a root style for
  the tile-action rows, so it must opt out with `parent=""` or AAPT fails resource
  linking looking for a non-existent `Widget.MotoLauncher` base style.

## Core Features

- Fixed 4×3 favorites grid, remote- and glove-operable.
- All-apps browser with touch search, theme toggle, and update check in its header.
- Tap-to-launch; long-press on a favourite for a Reassign / App info menu.
- Remote quick launch: holding Escape starts the first favourite from anywhere in the app.
- Touch configuration of favorite slots.
- User-triggered self-update from GitHub nightly.
