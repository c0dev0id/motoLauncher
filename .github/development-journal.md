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
  starts `gone` in the layout: at cold start nothing is known until the first callback, and
  `onAttachedToWindow` hides it again before registering, because re-attaching does not
  re-run the layout's starting visibility and a network that is already gone sends no
  `onLost`.

  The cellular meter follows the same rule, with one addition that decides whether it works
  at all: its request asks for `NET_CAPABILITY_INTERNET`, not merely `TRANSPORT_CELLULAR`.
  Many devices keep an IMS connection up for VoLTE while mobile data is switched off, and
  that is a cellular network too — matching on transport alone would leave the icon on
  screen in exactly the case this was built for. Its existing gate is unchanged and comes
  first: without telephony, API 31+ and `READ_PHONE_STATE` there is no level to draw, so
  nothing is watched and the meter never appears. This also settles what the meter means —
  mobile data and its strength, rather than radio signal strength, which is the useful
  reading on a device that uses cellular for data alone.
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
  App info, Uninstall (system uninstaller via `ACTION_DELETE`; see the uninstall entry
  below for the two manifest declarations it needs),
  Remove (clears the slot, making it an empty "+" tile again). The app list's long-press
  shows the same dialog without the Remove row (there is no slot to clear), so one dialog
  serves both screens. The original third row was "Reassign app" — it was replaced by
  "Remove" because the flow of remove-then-tap-"+" is simpler than an in-place reassign,
  and avoids holding two states (current occupant and pick target) across the picker round-trip. The menu is a plain `Dialog` with a custom layout, not an
  `AlertDialog`: the Material3 dialog theme brings its own surface colours and small
  buttons, whereas the custom view reuses `tile_background` and the launcher palette so
  it reads as part of the grid and stays glove-sized; its chrome (background drawable,
  minimum width) is `Theme.MotoLauncher.Dialog` in `themes.xml`, not code. Every dialog
  goes through `Dialog.showImmersive()` because a dialog is a separate window and would
  otherwise bring the system bars back while showing.
- **Uninstall needs two manifest declarations, and fails silently without either.** The
  tile menu's Uninstall row did nothing on the device. `ACTION_DELETE` with a `package:`
  URI is still the right hand-off — the system uninstaller owns the confirmation, so the
  launcher needs no UI, no permission prompt and no result contract of its own — but two
  declarations were missing, and the failure mode for both is silence rather than an
  error. `REQUEST_DELETE_PACKAGES` is mandatory for apps targeting API 28 or later that
  ask for a package to be deleted; its protection level is normal, so it is granted at
  install with no runtime ask. And package visibility (API 30+) filters intent
  *resolution*, not just explicit queries: the system uninstaller has no MAIN/LAUNCHER
  entry, so the `<queries>` filter the launcher already had never made it visible — a
  second entry for `ACTION_DELETE` with the `package` scheme is what gives the hand-off
  something to resolve against. Neither is reachable from a unit test: both are manifest
  facts enforced by the framework at runtime, so this one is confirmed on the device.
- **Package changes come from one process-wide `LauncherApps.Callback`.** Uninstalling an
  app left two kinds of stale state: the All Apps list kept showing it (the enumeration is
  cached in `AppListViewModel` for the screen's lifetime, so nothing re-ran on the way back
  from the uninstaller), and the favourite slot kept a component that could never resolve
  again — drawn as an empty "+" that was not actually empty. `LauncherApps.Callback` is the
  right feed: it reports adds, removals and profile availability for exactly the apps
  `AppRepository` enumerates, so the change events come from the same place as the data,
  and no broadcast receiver or extra permission is involved. It is registered once in
  `MotoLauncherApp` rather than per activity, because the favourites it repairs are
  process-wide state and an activity paused during an uninstall would miss the very event
  that concerns it. Screens are not pushed to: the callback bumps a `packageGeneration`
  counter, Home already rebuilds its grid in `onResume`, and the app list compares the
  counter there and reloads only when it moved — the enumeration is the most expensive
  thing the app does, so "reload on every resume" was not an option. Only a real
  `onPackageRemoved` clears a slot; "failed to resolve" must not, since an app on unmounted
  external storage is unavailable rather than uninstalled and clearing then would lose the
  configuration for good. The known gap: a screen already in the foreground when a package
  changes still waits for the next resume.
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
  a touch-only workflow. Empty "+" tiles open the picker for that slot directly; long-pressing
  an occupied tile shows the Remove option to clear it first. Theme toggle, update check, and the cellular-permission
  ask sit in the All Apps header (hidden in pick mode). There is no separate settings
  screen any more — the earlier Configure Favorites activity duplicated all of this and
  was removed.
- **Holding Escape launches the configured navigation app (`ui/QuickLaunch.kt`, `NavAppStore`).** The
  remote has one spare key and no spare gesture; ESC was already the only key that meant
  anything outside a tile. A hold is the one input left that can be given a meaning
  without making anything new dpad-reachable, and the meaning stays inside the rule that
  the remote may only ever launch apps: it starts the user-selected nav app on both Home
  and the app list, so the gesture means one thing wherever the remote is. The nav app is
  configured via a settings tile that opens the app picker in nav-app-pick mode; selecting
  an app writes its `ComponentName` to `NavAppStore` (shared `"settings"` prefs, key
  `nav_app`). Slot 0 is now a plain favourite with no side-effects. Detection is
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
- **Settings mode: modal RecyclerView, ViewModel-persisted state.** Theme toggle, update
  check, and the cellular-permission ask were header buttons in the All Apps screen. As the
  number of settings grew, more buttons would have eaten into the search field and become
  unglove-sized. The solution reuses the existing RecyclerView: a "Settings" toggle switches
  `isSettingsMode` in `AppListViewModel`, hides the search box, and replaces the app tiles
  with text-only settings tiles; "Apps" or a short Escape flips back. Because `AppListViewModel`
  outlives `recreate()`, the theme tile's immediate recreation (via
  `AppCompatDelegate.setDefaultNightMode`) restores settings mode instead of dropping
  the user back to the app grid. The short Escape exit means: first press → apps mode, second
  press → finish the activity, matching the intuition that Escape walks up the modal stack.
  `TileItem.icon` is nullable so settings tiles can omit the icon view (`View.GONE`); the
  adapter restores `VISIBLE` on rebind to prevent ViewHolder reuse from carrying the stale
  `GONE` into the next app tile. The header row retains a single `settingsButton` (touch-only,
  hidden in pick mode); `Widget.MotoLauncher.HeaderButton` still governs its sizing.
- **Dotted style names that are roots must set `parent=""`.** Android treats
  `Widget.MotoLauncher.Foo` as inheriting from `Widget.MotoLauncher` unless an explicit
  parent is given. `Widget.MotoLauncher.DialogAction` is intentionally a root style for
  the tile-action rows, so it must opt out with `parent=""` or AAPT fails resource
  linking looking for a non-existent `Widget.MotoLauncher` base style.

- **GPS speed widget follows the same two-gate pattern as the cellular meter.** Both
  `speedEnabled` (a `SpeedStore` preference in the shared `"settings"` SharedPreferences
  file) and `ACCESS_FINE_LOCATION` must be true, or nothing is registered and the widget
  stays `GONE`. `StatusBarView` watches the `"settings"` SharedPreferences for changes via
  `OnSharedPreferenceChangeListener` — registered in `onAttachedToWindow`, unregistered in
  `onDetachedFromWindow` — so toggling GPS Speed or the unit in AppListActivity is
  immediately reflected in the running status bar without any HomeActivity lifecycle
  wiring. When speed is enabled and permission granted, `LocationManager.requestLocationUpdates`
  is called with `GPS_PROVIDER`, 1 second minimum interval, on the main Looper. The speed
  widget shows "-- km/h" (or mph) until the first fix arrives. Unit changes reset the
  placeholder immediately; the real value updates with the next GPS fix (~1 s). The
  permission-grant path is the same as cellular: granting permission mid-session causes the
  GPS speed to activate on the next `StatusBarView` reattach (launcher restart), not
  instantly — consistent with cellular.
- **`layout_weight` center position.** `speedText` sits between `timeText` and the status
  icons in the `view_status_bar.xml` `<merge>`. Both text views have `layout_weight="1"`;
  the icons are `wrap_content`. When speed is `GONE`, its weight is dropped and `timeText`
  reclaims all remaining space — current behavior is preserved. When speed is `VISIBLE`,
  the two texts split remaining space equally, placing the speed roughly in the center of
  the bar (slightly left of true center because the icons occupy fixed space on the right).

- **Hidden apps stored per-package, not per-component.** `HiddenAppsStore` persists a
  `Set<String>` of package names. The hidden set is read once per `render()` call
  (one prefs read) rather than per-app (N reads), and a mutable copy is taken before
  any write since `getStringSet()` returns the live internal reference. Visibility
  (show/hide) is a second boolean pref in the same file. Both live under `hidden_apps`.

## Core Features

- Fixed 4×3 favorites grid, remote- and glove-operable.
- All-apps browser with touch search; settings mode (same screen) for theme toggle, update check, cellular permission, GPS speed toggle, and unit selection.
- Tap-to-launch; long-press on a favourite for an App info / Uninstall / Remove menu.
- Remote quick launch: holding Escape starts the configured navigation app from anywhere in the app.
- Touch configuration of favorite slots.
- User-triggered self-update from GitHub nightly.
- GPS speed widget in the home screen status bar (optional, defaults off), with metric/imperial selection.
