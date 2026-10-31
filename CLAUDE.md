# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project scope

motoLauncher is an Android **launcher / home app** for a motorcycle navigation device
(the navigation app itself is `com.thorkracing.dmd2launcher`, which this project does NOT
replace — it launches it). The launcher must be operable while wearing gloves and via a
handlebar remote, not just by touch.

## Commands

These run in CI, not here — AGP is unavailable on this platform and the firewall blocks
it, so none of them can be executed locally (see *Build & CI*). They are listed because
they are what a change is judged by:

```sh
# what Check runs on a branch push (one Gradle invocation)
python3 -m unittest discover -s .github/scripts     # release notes script
./gradlew --continue lintGithubDebug testGithubDebugUnitTest assembleGithubDebug assemblePlayDebug

# what Build adds on main
./gradlew lintGithubRelease lintPlayRelease
./gradlew assembleGithubRelease -PappVersionName=dev-<sha> -PappVersionCode=<commit count>

# what Release builds
./gradlew assembleGithubRelease bundlePlayRelease -PappVersionName=1.0.<n> -PappVersionCode=<commit count>

# one test class, or one test
./gradlew testGithubDebugUnitTest --tests "de.codevoid.motolauncher.AppRepositoryTest"
./gradlew testGithubDebugUnitTest --tests "de.codevoid.motolauncher.AppRepositoryTest.sortsCaseInsensitively"
```

Two product flavors: `github` (the release-page APK, with the self-updater) and `play`
(Google Play, no updater, no `INTERNET`). Code that only the updater needs is gated on
`BuildConfig.SELF_UPDATE`; the github-only manifest entries live in
`app/src/github/AndroidManifest.xml`.

## Conventions

The changelog is written for the rider, not the developer, and a version's entries must
not contradict each other: `[Unreleased]` reads as the net description of the coming
release, so when a change supersedes something an earlier unreleased entry says, fold it
into that entry rather than adding a "now X instead of Y" beside it. Until the first
release that makes `[Unreleased]` a plain feature list under `### Added`: a fix or change
to behaviour no user ever had is folded into its feature's entry or dropped, never
listed under Fixed or Changed, and no entry describes a change from a prior state
("now", "no longer"). Entries say what the user sees and does, not how the code does it
— no class names, permission constants, resource qualifiers or CI; that material belongs
in `.github/`.

Read `.github/decisions.md` before proposing structural changes — most of them were
reached after a failed simpler attempt, and it records why. Where `.github/` and this file
disagree, the code wins and the stale one gets fixed in the same task.

## Hard UX constraints (glove + remote usage)

These drive nearly every UI decision — violating them defeats the point of the app:

- **No swipe gestures anywhere on primary flows.** Swiping is impractical with gloves.
- **Large touch targets.** Precise touch is hard with gloves; buttons must be big.
  Glove sizing is stated once in `values/dimens.xml` (phone, sw < 600dp) and
  `values-sw600dp/dimens.xml` (tablet — the original 7" 1920x1080 sizing), never per view.
- **Orientation is a setting, Sensor by default** (`OrientationStore`: `FULL_SENSOR`,
  "Sensor", which auto-rotates through all four, plus the four fixed values). The default
  only reaches an install that never set the option — an explicit choice is already stored.
  The tile opens a picker (`MenuDialog`), never a cycle: tapping through five values
  would apply every one on the way, rotating the screen up to four times per change. Rotation
  under Sensor reshapes the grid only because no activity lists `orientation` in its
  `configChanges` — the system recreates them and `isPortrait` is read afresh. Adding
  `orientation` there would freeze the layout in whatever shape it started in. The device
  is a 7" landscape screen; portrait exists for phones. Every activity calls
  `setRequestedOrientation` in `onCreate` and `onResume`; grid shape
  follows the Tile size setting (`TileSize`) and `Context.isPortrait`
  (`ui/LayoutUtils.kt`): Home is 4×2, 4×3 (default) or 5×3 in landscape and the same
  grid transposed in portrait, so rotating never changes the tile count; the app list
  has 4 / 5 / 6 columns in landscape and 3 / 4 / 5 in portrait; and the status bar has a
  `layout-port` variant.
  The status bar is always hidden (immersive mode); the navigation bar follows
  `NavBarStore` (hidden by default).
- **Home and the app list must be operable by remote** using only the keys the remote
  emits: dpad-left/right/up/down, Enter, Escape. Traversal is Android's native View
  focus engine, not custom key handling — keep it that way.
- **The remote may only ever launch apps.** Everything that needs touch to get back out
  of (app info, the search field, the back/settings buttons, every dialog) and all
  configuration is deliberately unreachable from the remote: touch-only controls sit in a
  `TouchOnlyRow`, the tiles of the app list's settings and pick modes and Home's empty "+"
  tiles cannot take focus, key-driven long-press is blocked on every tile, and a short
  Escape on Home does nothing. Do not
  "fix" any of these by making them dpad-reachable. The one thing a held key may do is
  launch: holding Escape starts the configured navigation app (see below), which adds no
  route into configuration. Configuration itself is a touch workflow: empty "+" tiles
  open the picker for that slot in place, and everything else lives in the All Apps
  screen's settings mode. There is no settings screen — don't add one; put configuration
  where it is used.

## Required features

- Configurable set of **favorite apps** (or **links**) pinned to the home screen.
- **App list** showing all installed apps (with a touch-only search filter), minus apps
  the user has hidden.
- **Short tap / Enter → launch**; **touch long press** → a tile-styled menu:
  App info / Uninstall / Remove on a Home app tile, Edit link / Remove on a Home link
  tile, App info / Uninstall / Hide-or-Unhide in the app list, nothing in pick mode.
- **Hold Escape → launch the navigation app** chosen in settings (`NavAppStore`), from
  Home and from the app list alike. No nav app configured, or uninstalled: nothing happens.
- **In-app update check** (github flavor only — Google Play forbids self-updating): a
  release build compares itself to the latest GitHub release, a `dev-` build to the
  newest file in the home.codevoid.de dist store, and offers the install if they differ. Must be **user-triggered** (the
  device is mostly offline — never auto-poll the network).
- **Privacy policy** shown as text from the settings mode (Play requires it in-app);
  `privacy_policy_text` is the body of `PRIVACY.md`, and `PrivacyPolicyTest` compares them.

## Architecture

Kotlin, Android classic Views (no Compose), AppCompat + Material3, view binding.
Versions and SDK levels are in `.github/stackinfo.md`. Coroutines for the app
enumeration and update I/O, and `StateFlow` for the package generation and the app list,
collected with `repeatOnLifecycle(STARTED)`. No networking library: `HttpURLConnection` +
`org.json`.

Package layout under `de.codevoid.motolauncher`:

- `MotoLauncherApp` (`Application`) — re-applies the saved theme before any activity is
  created, deletes the just-installed update APK on its IO scope (github flavor), and
  owns two pieces of process-wide state. (1) `packageGeneration`, a `StateFlow<Int>`
  advanced by the single `LauncherApps.Callback`, `PackageWatcher`, on every package event;
  a removal also clears the favourite slots holding that package in that profile
  (`clearSlotsForPackage`). Only a real removal clears a slot — an app that merely fails to
  resolve may be on unmounted storage. Screens collect the generation while started, so a
  change shows while they are in front. (2) `getApps()`, the process-level app-list cache: a
  `Deferred<List<AppEntry>>` on an IO scope, reissued only when `packageGeneration` moved.
  Home warms it in `onCreate` so "All Apps" opens with the list already decoded.
- `HomeActivity` — the launcher entry (`category.HOME`, `singleTask`). Fixed grid of 8,
  12 or 15 tiles (`TileSize.homeTileCount`): the leading favourite slots + a pinned "All
  Apps" tile in the last cell. The grid is **not** a RecyclerView:
  `populateGrid(tileSize)` inflates `item_app_tile` once per cell into weighted
  `LinearLayout` rows, and `buildGrid()` rebinds them. `showGrid()` runs both, from
  `onResume` and from every package change while Home is started (`packageGeneration`
  after its current value), and calls `populateGrid` only when the stored size differs
  from `gridTileSize`, the size the tiles were built for: on the first resume, and on
  return from a Tile size change.
  Weighted layout divides space in the layout pass by construction, which is what fixed
  the cold-start "third row cut off" first-frame race. `buildGrid` gets the resolved
  `AppEntry`s from `FavoritesCache.resolve`, a `ViewModel` that empties itself when
  `packageGeneration` moves and otherwise loads only keys it has not seen (remembering
  ones that did not resolve), so returning from the navigation app costs no Binder calls
  or icon decoding, and a new favourite resolves alone.
  It is a `ViewModel` rather than an activity field because a recreate — the theme toggle,
  or a rotation under the Sensor orientation — would otherwise start it empty and redo the
  whole resolve on the main thread in `onResume`. A slot is a
  `SlotEntry.App` (launch / App info / Uninstall / Remove), a `SlotEntry.Link`
  (`ACTION_VIEW` on the URL / Edit link / Remove) or empty ("+" opens the picker for that
  slot). Back is swallowed; a short Escape does nothing and a held Escape runs
  `launchNavApp()` (`EscapeKeys`). A `StatusBarView` sits above the grid. Two consequences
  of reusing the same views: remote focus is seeded on tile 0 only when `populateGrid`
  creates new tiles (re-seeding on every resume would drag the remote back to the first
  tile after every launch), and `bindTile` must null the long-click listener and drop `isLongClickable` for
  a tile that has no long-press, or the framework keeps arming the timer from the tile's
  previous binding. `bindTile` also sets focusability on every bind: an empty "+" tile is
  `touchOnly` and cannot take focus, so the remote skips it (and the tile-0 seed misses
  when slot 0 is empty; the first remote key then lands on the first focusable tile).
- `AppListActivity` — `RecyclerView` + `GridLayoutManager`, whose span count is
  `TileSize.listColumns` and is synced on every render; focus is seeded on the first cell
  once the first list arrives. Only browse tiles can take focus: settings mode and both
  pick modes submit their tiles `touchOnly`. Three modes, the sealed `Mode` parsed once
  from the intent extras: **`Browse`** (tap launches, long-press opens the tile menu),
  **`PickSlot(slot)`** (`pickIntent(context, slot)`: a leading "None" tile clears the slot,
  an "Add link" tile opens `LinkDialog` pre-filled from the slot's current link, any
  app tile is written to that `FavoritesStore` slot), and **`PickNavApp`**
  (`navAppPickerIntent`: writes `NavAppStore`). Pick modes finish on selection; callers
  rebuild in `onResume`, so there is no result contract. `AppListViewModel.apps` is a
  `StateFlow` of the app list, reloaded through `getApps()` on every package generation
  and shared while the activity is started; the activity re-renders when a new list
  instance arrives and on every `onResume`. `renderCurrentMode()` is the one render path:
  it trims the search text once, then shows either the settings tiles filtered by label or
  the pick tiles followed by `appTiles(...)` — a pure top-level function that drops
  packages in `HiddenAppsStore` unless `showHidden` is on (hidden apps then render dimmed)
  and keys each tile by its `ComponentKey` — over `AppRepository.filterApps`.
  `AppListViewModel` (same file) carries `isSettingsMode` so the theme toggle's
  `recreate()` restores it. In the github flavor the activity also collects
  `UpdateViewModel`: its state re-renders the settings tiles, its events become dialogs or
  the installer through `showUpdateEvent`. The header
  `TouchOnlyRow` holds the back button, the search box and a single `settingsButton`
  (hidden in pick mode) that toggles settings mode. A short Escape exits settings mode
  before finishing. Activity transitions are disabled (`noTransition()`) in both
  directions.
- `SettingsTiles` — the settings mode: builds its tiles on every render (theme, nav app,
  update check with live download progress in the github flavor, park lock when
  `ParkLockRules.shouldOfferParkLock` allows it, cellular toggle, GPS speed toggle + units
  when the device has `FEATURE_LOCATION_GPS`, battery display, hidden apps, orientation,
  tile size, nav bar, privacy policy) and owns their stores, the option tables and the
  location permission request. Every change of a setting ends in one `onChanged()`, the
  activity's `renderCurrentMode`; there is no cached list to invalidate. The nav app's
  label comes from the loaded app list, falling back to the package name. The update
  tile shows `UpdateViewModel.state` and taps `check()`, which ignores a tap while busy;
  every state change re-renders like any other change. It is created in `onCreate` because it
  registers the permission request and the picker result listeners. Orientation and
  Tile size open a `MenuDialog` picker.
  The GPS toggle asks for fine and coarse location together through
  `RequestMultiplePermissions()`; the store flips to enabled only when fine location is
  granted, and the tile reads On only while both the setting and the permission hold.
- `ParkActivity` — the park lock: a PIN keypad for short unattended stops, in **its own
  task** (`singleInstance` + `taskAffinity`), which is load-bearing — `HomeActivity` is
  `singleTask` and the root of the home task, so a Home press clears anything stacked above
  it. `onResume` and `onWindowFocusChanged` call `startLockTask()` (screen pinning, no device
  owner) to block Recents and the shade — on Android 15+ an app pinning itself is not
  asked to confirm (measured on 16; AOSP suggests otherwise, see `.github/decisions.md`),
  so parking is one tap. The request is best effort: `startLockTask()` throws when the task
  is not in front, so it is wrapped in `runCatching`, and a device with screen pinning
  switched off does not pin. Which state is in force is deliberately not shown on the
  screen. `stopLockTask()` is called directly; it returns quietly when nothing is pinned.
  `settleCheck` runs after a delay because `lockTaskModeState` is updated
  asynchronously and reads as `NONE` straight after a successful request; it counts the
  requests in a row that have not taken, and after three the guard stops asking until
  focus returns. The system's unpin gesture (hold Back + Recents) cannot be
  blocked, so it is undone: `lockTaskGuard` polls `lockTaskModeState` every 300 ms while
  parked and re-pins, and `onWindowFocusChanged` re-asserts too.
  `ParkLockRules.shouldRequestLockTask` is the pure, unit-tested rule behind both —
  parked, not in set mode, not finishing, not already pinned, and past the settle window
  that covers the asynchronous state update.
  Order on unlock is load-bearing: disarm the guard and clear `isParked` *before*
  `stopLockTask()`, or an in-flight guard run re-pins the screen the correct PIN just
  released. The guard is armed in `onResume` and disarmed in `onPause`, so a parked device
  with the screen off polls nothing. **It also means the PIN is the only way out** short of
  a computer — a forgotten PIN strands the device until `adb shell pm clear` or a
  reinstall, which works because the park state is never backed up (see `ParkStore`). `setMode` is read from the
  *current* intent, never cached, because a reused `singleInstance` gets later intents
  through `onNewIntent`. The restore path is
  `HomeActivity.onResume`: while `ParkStore.isParked` is set it re-launches this screen,
  covering both a reboot (pinning does not survive one) and a Home press where pinning was
  refused. `ParkActivity` sets `isParked` itself in `onResume`, so no entry point carries a
  protocol, and the Park lock tile finishes the app list as it launches — the home task is
  already back at `HomeActivity`, so unlocking returns to Home and nothing needs a
  finish-if-parked guard. The keypad sits in a `TouchOnlyRow`, so the whole subtree is
  invisible to dpad traversal — the remote must never drive it. Entry point is the Park
  lock tile in the app list's settings mode (tap to lock or set a PIN, long-press to
  change it). That tile
  is the only way in, and it appears only when `ParkLockRules.shouldOfferParkLock` — the
  second pure, unit-tested rule — says the lock is worth reaching: Android 15+ (below it the
  system confirms every `startLockTask()`, so the guard raises a dialog instead of
  re-pinning) and no secure lock screen (`KeyguardManager.isDeviceSecure`, whose power
  button is the better park lock). Hiding the tile is enough because nothing else starts
  this activity: the restore path only fires on an `isParked` the tile set.
- `data/ComponentKey` — an app is a component in a user profile (`ComponentKey`), because
  the list shows every profile and the same component can exist in several.
  `getComponentKey` / `putComponentKey` / `removeComponentKey` persist one: the flattened
  component under the key, the profile's serial number under `<key>_user`, which is left
  out for the own user. A serial whose profile is gone reads as null.
- `data/AppRepository` — thin wrapper over `LauncherApps` (not `PackageManager`),
  iterating all `UserManager` profiles, and taking `ComponentKey`s. `launch` →
  `startMainActivity`, but only when `isActivityEnabled` says the app still resolves,
  because `startMainActivity` throws otherwise and both tiles and the stored nav app can be
  stale; `openInfo` → `startAppDetailsActivity`, `requestUninstall` → `ACTION_DELETE`
  hand-off with `EXTRA_USER` (needs both `REQUEST_DELETE_PACKAGES` and the `ACTION_DELETE`
  `<queries>` entry, or it silently does nothing). `loadApps` walks every profile and
  skips the launcher's own package; `loadByKeys` resolves only the requested apps so Home
  never enumerates or rasterizes every installed app. `sortApps` / `filterApps` are pure
  companion functions, deliberately free of `Context`.
- `data/*Store` — one small class per `SharedPreferences` concern, all synchronous
  getters/setters. `FavoritesStore` (file `favorites`, `SLOT_COUNT` = 14, enough for the
  largest Home grid; a smaller grid shows the leading slots and keeps the rest): `slot_<i>`
  is an app (`ComponentKey`, with `slot_<i>_user`) or the literal `link:` marker, in which
  case `slot_<i>_label` / `slot_<i>_url` hold the link; `getSlotEntry` returns the sealed
  `SlotEntry`, `getSlot` only the app case, and every write first removes all of the
  slot's keys. `clearSlotsForPackage` clears a package in one profile only. `NavAppStore`
  keeps a `ComponentKey` the same way. `HiddenAppsStore` (file `hidden_apps`) keeps
  a package-name `Set` plus `showHidden`, and always returns a copy because
  `getStringSet` hands out its live internal set. Everything else shares the `settings`
  file (`SETTINGS_PREFS`): `ThemeStore` (default dark, drives `AppCompatDelegate.setDefaultNightMode`),
  `NavAppStore`, `OrientationStore`, `NavBarStore`, `CellularStore`, `SpeedStore`
  (enabled + metric), `BatteryStore` (`BatteryDisplay` enum), `TileSizeStore` (`TileSize`
  enum: Home grid per orientation and app-list columns; default `MEDIUM`). `StatusBarView`
  listens to that file by key, so a new status-bar setting is a new public `KEY_*`
  constant plus a branch in its `prefsListener`, not new lifecycle wiring. `ParkStore`
  (file `park`: parked flag + salted-hash park PIN; the hash is hygiene, not security) has
  its own file because it is the one store left out of backups:
  `res/xml/data_extraction_rules.xml` (Android 12+) and `res/xml/backup_rules.xml`
  (Android 11 and lower) exclude `park.xml`, and the rules can only match whole files. A
  new store goes into `settings` unless it must stay out of backups too.
- `update/UpdateChecker` — github flavor only. One-shot GET: a `dev-` build asks the
  dist store's `latest` address (`DIST_LATEST`, JSON with `name`, `version` and a
  relative `url`, read by `parseDistFile`), any other build GitHub's `releases/latest`.
  `parseRelease` takes the first `.apk` asset and derives the version from its filename
  (`motoLauncher-<versionName>.apk`); `isNewer` is a plain string inequality against
  `BuildConfig.VERSION_NAME`, so any differing published build counts as an update.
  Install is a hand-off: download to `cacheDir/updates/` (with a bytes-written progress
  callback), then `FileProvider` + `ACTION_VIEW` to the system installer. `download()`
  clears other files first; `deleteInstalledUpdate()` removes only the running build's APK.
- `ui/AppTileAdapter` + `ui/TileItem` — the `RecyclerView` adapter used by the app list
  (not by Home). Callers compose a `List<TileItem>` (label, optional subtitle, optional
  icon, `dimmed`, and a `key` that defaults to the label; app tiles use their
  `ComponentKey`); the adapter stays dumb, calls `blockKeyLongPress()` once per view
  holder, and resets icon/subtitle visibility, the dimmed icon alpha and label colour,
  `isLongClickable` and focusability (from `submit(items, touchOnly)`) on every bind
  because recycled holders carry the previous item's state. It has stable IDs, one per key, so the remote's focus survives a
  `submit` of the same tiles.
- `ui/UpdateFlow.kt` — `UpdateViewModel`, the whole check / download flow in
  `viewModelScope`, so a recreate keeps it. `state` is a `StateFlow<UpdateState>` (`Idle`,
  `Checking`, `Downloading(percent, bytesPerSecond)`, throttled to 500 ms); `check()` and
  `download(release)` start only while idle. Outcomes are one-shot `UpdateEvent`s on a
  `Channel` (`UpToDate`, `Available`, `Install`, `Failed`), shown by
  `showUpdateEvent` as `AlertDialog`s or the installer. The ViewModel takes the release
  lookup and the download as functions (the public constructor wires `UpdateChecker` on
  the application context), so tests drive it without the network. `progressText()`
  gives `"45% · 2.2 MB/s"`: the percentage is concatenated, never passed through
  `String.format` — a `%` in a format template crashed once. `updateErrorText` names a
  failure's cause. `CancellationException` is rethrown, not swallowed.
  `AlertDialog.Builder(context)` gets the launcher look from `alertDialogTheme` — never
  pass a theme id at a call site.
- `ui/KeyInput.kt` — `View.blockKeyLongPress()` routes the confirm keys (DPAD_CENTER,
  Enter, numpad Enter, Space) through `performClick()` on a key-up that ends an
  uncanceled press on that view, without arming the framework's long-press timer (touch
  long-press still works). `EscapeKeys` is the whole Escape contract — short press to
  `onShortPress` (AppList: leave settings mode, else `finish()`, since Android doesn't
  route Escape to the back dispatcher; Home: nothing), hold to `onLongPress` (both:
  `launchNavApp()`). **Short vs. hold is decided on ACTION_UP from the event's own
  timestamps** (`eventTime - downTime` against `ViewConfiguration.getLongPressTimeout()`),
  never from the framework's `onKeyLongPress` — that callback is delivered off the first
  key *repeat*, so it only ever arrives from a device that auto-repeats while held, and
  the handlebar remote does not. Do not reintroduce it: the whole point is that the action
  cannot depend on a capability of whatever is plugged in. Two methods are wired from the
  activity, `onKeyDown` and `onKeyUp`; the DOWN is claimed and `startTracking()` called so
  the UP arrives with `isTracking` set, which is how an UP whose DOWN went to another
  window is told apart from a real press. A remote that emits an instantaneous down/up
  pair on release still cannot produce a hold — there is no elapsed time to measure. The
  file stays pure key plumbing — the action lives in `ui/QuickLaunch.kt`
  (`Context.launchNavApp()`: `NavAppStore` → `AppRepository.launch`).
- `ui/TouchOnlyRow` — a `LinearLayout` that refuses every way the framework moves focus
  into it without the child asking: focus search (`addFocusables`), default focus
  (`onRequestFocusInDescendants`) and a child becoming focusable
  (`focusableViewAvailable`). Its children still take touch focus (an `EditText` inside
  it still opens the IME). `focusableInTouchMode` and
  `descendantFocusability="blocksDescendants"` were both tried and rejected; see
  `.github/decisions.md`.
- `ui/MenuDialog.kt` — `MenuDialog`, a `DialogFragment` and the one row menu: the tile
  menu (App info / Uninstall / Remove on Home, Hide or Unhide in the list), the link menu
  (Edit link / Remove) and the pickers for Orientation and Tile size. `MenuDialog.show(fm,
  requestKey, title, rows, selected, iconRes, app, extras)` takes rows as (id, label)
  pairs; `menuRows(...)` builds them from string resources, which then serve as the ids,
  and a picker uses the value it applies as the id. The header shows `iconRes` or the
  app's icon, looked up from its `ComponentKey`. A tap sets a fragment result under the
  request key with `ROW` = the id plus the caller's extras, and dismisses; Back or Escape
  dismisses with no result. A second `show` for a request key that is up does nothing.
  Rows are *inflated* from `item_dialog_option` into `dialog_options`, so the style's
  `layout_*` attributes are resolved by the parent; a style applied through a `TextView`
  constructor would not carry them. The selected row is `isSelected`, which
  `tile_background` and `dialog_action_text` draw as a solid `tile_focused` fill with
  `on_tile_focused` text, because the tile that shows the value is hidden behind the
  dialog. It runs on `Theme.MotoLauncher.Dialog` (not `AlertDialog`, whose Material3 look
  would override the palette). Reach for a picker over a cycling tile whenever applying an
  intermediate value is itself disruptive.
- `ui/MessageDialog.kt` — `MessageDialog`, an `AlertDialog` fragment: `tell(fm, message,
  title)` with OK (the privacy policy, update notices), or `ask(fm, requestKey, question,
  positive, extras)` with a positive button that sets the extras as the result, and
  Cancel (the install prompt).
- `ui/LinkDialog.kt` — `LinkDialog`, the one `AlertDialog` fragment with text input
  (`dialog_add_link`): `show(fm, requestKey, label, url, editing, extras)`, titled "Edit
  link" when `editing`. OK stays disabled until the name is non-blank and
  `normalizeLink(url)` accepts the address; the result carries `LABEL`, `URL` (normalized)
  and the extras. `normalizeLink` trims, adds `https://` when there is no scheme, and
  rejects an empty address or a web address without a host; other schemes pass. The
  fields sit in a `TouchOnlyRow`; the label's `nextFocusForward` carries the keyboard's
  Next to the URL, which focus search no longer finds.
- Dialogs in general: every dialog is a `DialogFragment` whose arguments hold everything
  it shows, so it survives a recreate; results go through `setFragmentResult`, and each
  activity (and `SettingsTiles`, for its pickers) registers its listeners in `onCreate`.
  Dialog chrome lives in `Theme.MotoLauncher.Dialog` and the
  `ThemeOverlay.MotoLauncher.Dialog.Alert` sibling that `alertDialogTheme` points at.
  Every dialog control is touch-only: `Widget.MotoLauncher.DialogAction` rows and
  `Widget.MotoLauncher.DialogButton` `AlertDialog` buttons set `focusable=false`, so the
  remote can only cancel a dialog with Escape. Every dialog calls `hideSystemBars()` on
  its dialog before it is shown.
- `ui/Immersive.kt` — `Window.enableImmersiveMode(showNavBar)`: status bar always
  hidden, nav bar per the flag. Called in `onCreate` and again on
  `onWindowFocusChanged(true)` because permission dialogs and the installer restore the
  bars. `Window.fitContentToSystemBars()`, called once per activity in `onCreate`, lays the
  window out edge to edge and pads `android.R.id.content` by the visible bars and the
  display cutout — `setDecorFitsSystemWindows` is disabled from targetSdk 35 on Android 15+. `Dialog.hideSystemBars()` applies the bars to a dialog's own window; every
  dialog fragment calls it before the dialog is shown. `Activity.noTransition()` also
  lives here.
- `ui/StatusBarView` — self-contained Home top bar (time, optional GPS speed, Wi-Fi,
  cellular, battery). Registers receivers/callbacks in `onAttachedToWindow`, releases
  them in `onDetachedFromWindow`; `HomeActivity` does no lifecycle wiring. Icons are
  `<level-list>` drawables updated by `setImageLevel()`; the battery level encodes
  charging in the number (0–4 idle, 5–9 plugged), and `BatteryStore.display` chooses
  icon / text / both. **Wi-Fi visibility keys off the radio** (`WIFI_STATE_CHANGED_ACTION`,
  seeded from `isWifiEnabled`), bars off RSSI from `NetworkCapabilities.transportInfo`
  (`WifiManager.connectionInfo` below API 31, because `WifiInfo` only implements
  `TransportInfo` from Android 12; no location permission needed either way);
  losing the last network draws zero bars, it does not hide the icon. The callback is one
  `WifiCallback` per registration on the connectivity thread; its levels are posted and
  de-duplicated on the main thread.
  `calculateSignalLevel`'s rating spans `[0, maxSignalLevel]` **inclusive** (below API 30
  the static five-step rating) and is mapped onto the five icon states by the pure
  `wifiIconLevel`. **Cellular** shows while `CellularStore.enabled` is set and takes
  `SignalStrength.level` (0..4) through, from `TelephonyCallback` on API 31+ and
  `PhoneStateListener` below; neither needs a permission. **GPS speed** is gated by
  `SpeedStore.enabled`, `FEATURE_LOCATION_GPS` and `ACCESS_FINE_LOCATION`, listens through
  a `LocationListenerCompat` (a plain `LocationListener` lambda throws
  `AbstractMethodError` below API 30), polls `GPS_PROVIDER` at 1 Hz, and is the one
  subscription that is paused while the window is hidden or the screen is off, because it
  costs CPU the navigation app needs. `registerGps` checks window visibility and
  `PowerManager.isInteractive` itself, so every path that starts GPS obeys that, the
  settings toggle included. `onAttachedToWindow` only registers; the attach-time
  `onWindowVisibilityChanged` does the first clock, battery and GPS work. **`refreshOnResume` is the one resume refresh**,
  reached from both
  edges — `ACTION_SCREEN_ON` on `screenReceiver`, and `onWindowVisibilityChanged(VISIBLE)`
  — so the two cannot drift apart. It re-reads the clock, which needs it because
  `ACTION_TIME_TICK` stops arriving while the screen is off and a device picked up in the
  morning would otherwise show the minute it went dark until the next tick; and the
  battery, which only goes stale when a change broadcast is missed while the device sleeps,
  the visible case being plugged in or unplugged with the screen off. The battery read is
  free: `ACTION_BATTERY_CHANGED` is sticky, so a null receiver returns the current state
  synchronously without registering anything, and `applyBatteryIntent` already drops a
  no-change refresh. The radios are deliberately left out — Wi-Fi and cellular re-deliver
  through their own callbacks once the network settles after a wake. The clock builds its
  formatter on every update (a `SimpleDateFormat` keeps the zone it was created in) and
  follows the system 12/24-hour setting. Setting changes arrive
  through the `settings` `OnSharedPreferenceChangeListener`. Uses the bundled Michroma font
  (`res/font/michroma.ttf`, OFL — see `MICHROMA-LICENSE.txt`).

## Resources & styling

- The palette lives in `values/colors.xml` (light) with dark overrides in
  `values-night/colors.xml`; a colour that is the same in both modes is defined once, in
  the light file. Layouts reference tokens (`background`, `surface`, `tile_default`,
  `tile_focused`, `tile_pressed`, `on_surface`, `on_surface_muted`, `on_tile_focused`,
  `signal_track`), never literals — the theme toggle works purely by resource qualifier,
  with no colour logic in code. Same for system-bar icon polarity: `@bool/light_system_bars`.
  A colour that follows a view state is a colour state list in `res/color/` and code sets
  only the state: the Settings button is `isActivated` in settings mode, and
  `header_button_background` / `header_button_text` draw it.
- **A dotted style name that is meant to be a root must set `parent=""`.** AAPT reads
  `Widget.MotoLauncher.DialogAction` as inheriting from a `Widget.MotoLauncher` style that
  doesn't exist and fails resource linking — a CI-only failure, so it costs a full round
  trip. `Widget.MotoLauncher.HeaderButton` (parent `Widget.Material3.Button`),
  `Widget.MotoLauncher.DialogButton` (parent `Widget.Material3.Button.TextButton.Dialog.Flush`,
  the `AlertDialog` buttons in `on_surface`) and `Widget.MotoLauncher.DialogAction`
  (`parent=""`) are the two shapes in use: an explicit parent, or an empty one.
- Every size is a `@dimen` with a phone default and a `sw600dp` override; add new sizes
  to both files.
- Text holds 4.5:1 on every fill it is drawn on, in both themes, and marks that carry
  meaning (the focus ring, the charging bolt in `battery_bolt`) 3:1. `ColorContrastTest`
  lists the pairs; a new text colour, mark or fill adds its pairs there. `signal_track`
  is the one deliberate exception: unlit bars recede. Never fade a whole tile with
  `alpha`: a dimmed (hidden) app fades its icon and uses `on_surface_muted` for the
  label, so the label and the focus ring keep their contrast.
- The unlit bars of the signal meters use `signal_track`, not `on_surface_muted` — that
  token is secondary *text* and is far too close to `on_surface` to separate a lit bar
  from an unlit one. Keep the two roles apart.

The manifest scopes package visibility to `MAIN`/`LAUNCHER` `<queries>` (plus the
`ACTION_DELETE` entry) rather than requesting `QUERY_ALL_PACKAGES` — the
launcher-appropriate approach. Keep it that way. Permissions in use: `INTERNET` and
`REQUEST_INSTALL_PACKAGES` (github flavor only), `REQUEST_DELETE_PACKAGES`,
`ACCESS_WIFI_STATE`,
`ACCESS_NETWORK_STATE`, and the runtime `ACCESS_FINE_LOCATION` /
`ACCESS_COARSE_LOCATION` (GPS speed), optional and asked for from its settings tile.
Cellular bars need no permission.

## Build & CI

**Do not attempt to build locally.** Android Studio / AGP are unavailable on this
platform and the firewall blocks AGP — do not work around this. All builds run in CI.
Correctness depends on careful API use and reading before writing.

Three workflows. **`Check`** (`.github/workflows/check.yml`) is the pre-merge gate: every
push to a branch other than `main`, plus `workflow_dispatch`. It is a
single job: the release script's unit tests, then one Gradle invocation (see *Commands*)
that lints and tests `githubDebug` — both flavors share every source and test — and
assembles the debug variant of both flavors, sharing compilation and one daemon start-up. The debug builds are unminified, unshrunk and signed with the committed
`app/debug.keystore`, so APKs from different runs install over each other, and are
uploaded as the `app-debug` artifact — with a `.debug` `applicationIdSuffix`, so they
install alongside the release build and a branch can be tested on the device without
disturbing the launcher in daily use. The debug build type sets `SELF_UPDATE` to
false: the dev channel serves the release-ID build, which would install beside it. `--continue` means one run reports lint, test and compile failures together.
There is deliberately **no `pull_request` trigger**: a check run attaches to the head
commit, so the push run's result already shows on a PR, and the event only added a second
run when a PR was opened onto an already-built branch. The consequence is that opening a
PR triggers nothing — the newest result is whatever the last push produced — and that pull
requests from forks would go unchecked. The concurrency group (`check-<ref_name>`,
`cancel-in-progress`) now only supersedes an older run when you push twice quickly.

**`Build`** (`.github/workflows/build.yml`) runs **only on push to `main`**,
republishes the `dev` pre-release and pushes the dev APK to the dist store's nightly channel; a newer push cancels an older run (concurrency group
`build`), so an older run never publishes last. Three parallel jobs plus two follow-up
publish jobs:

- `./gradlew lintGithubRelease lintPlayRelease` — the variants that ship; plain `lint`
  covers only the default debug variant, which `Check` already lints.
- `./gradlew testGithubDebugUnitTest` — JUnit4 + Robolectric JVM
  unit tests in `app/src/test/kotlin` (`isIncludeAndroidResources = true`, so real resources and view
  inflation work; see *Commands* for running a single class or test).
  Robolectric runs them against SDK 36, which needs JDK 21 — hence JDK 21 in CI.
  Twenty-eight classes, and the shape they set: `AppRepositoryTest` (the pure `sortApps` /
  `filterApps`), `FavoritesStoreTest` (app and link slot round-trips against real
  `SharedPreferences`, a work-profile app keeping its profile through a profile added to
  Robolectric's `UserManager`, a gone profile reading empty, every write leaving no stale
  key, and `clearSlotsForPackage` matching whole package names in one profile and leaving
  link slots alone), `NavAppStoreTest` (the nav app keeps its profile; clearing forgets
  it), `PackageWatcherTest` (a removal clears the app only in its profile, every other
  event only advances the generation), `MotoLauncherAppTest` (driven through Robolectric's
  `LauncherApps` shadow: the app list is reused until a package event, and an uninstall
  clears its favourite), `FavoritesCacheTest` (only unseen keys are loaded, an unresolved
  one is not asked again, a new generation reloads everything), `AppTilesTest` (the pure
  `appTiles`: hidden apps left out or dimmed, tiles keyed by `ComponentKey`, tap and long
  press reaching their app), `AppListActivityTest` (a real `AppListActivity`, reading tiles
  through the adapter: settings mode is touch-only and a short Escape leaves it before the
  screen, a setting change shows at once, the nav app falls back to its package name, the
  slot picker leads with None and Add link without a settings button, the nav-app picker
  with None only),
  `UpdateCheckerTest` (`parseRelease` / `parseDistFile` / `isNewer` /
  `deleteInstalledUpdate` over JSON fixtures and temp files; an asset name with a path is
  skipped; a dist file's address resolves against the `latest` URL, and one without a
  version or address, or not named as a bare APK, is rejected), `UpdateViewModelTest` (the flow driven by fake lookup and download functions:
  both check outcomes, a tap while busy starting nothing, a failure reported once and the
  flow idle again, download progress then the installer, and the progress text),
  `UpdateErrorTextTest` (a 404, another status, no connection, a timeout and anything
  else, each in words), `ImmersiveTest` (content
  padding follows dispatched bar and cutout insets), `PrivacyPolicyTest` (the in-app text is
  the body of `PRIVACY.md`, read from the module's parent directory), `AppTileAdapterTest`
  (subtitles shown as given and hidden when absent on a recycled holder; a touch-only
  submit leaves a recycled holder unfocusable and the next browse submit restores it; out
  of touch mode, focus stays on the same tile when the list is submitted again; a dimmed
  tile fades only its icon and mutes its label),
  `HomeGridTest` (a real `HomeActivity`: empty "+" tiles cannot take focus while All Apps
  can, a reused tile follows its slot's content across resumes, and a package event while
  Home is in front rebinds the grid without a resume),
  `MenuDialogTest` (hosted by a real `AppCompatActivity`: rows in order, a tap reporting
  its id and the extras once and closing, only the selected row marked and drawn
  differently, no row focusable, Escape closing without a result, the menu surviving a
  recreate and still reporting, the header icon only when given, a second show ignored),
  `MessageDialogTest` (a message with an unfocusable OK, a question reporting its extras
  on accept and nothing on cancel, and surviving a recreate), `LinkDialogTest` (out of
  touch mode, default focus finds neither field and no button is focusable, while the
  keyboard's Next still moves from the label to the URL; OK waits for a name and an
  address that opens, reports the normalized link with the extras, the title says add
  or edit, and `normalizeLink` cases), `EscapeKeysTest` (short vs. held Escape, dispatched through a real
  `KeyEvent.DispatcherState` so the framework's tracking rules are exercised rather than
  assumed), `TouchOnlyRowTest` (out of touch mode, focus search, default focus, the
  refocus after a removal and a child becoming focusable all skip the row, while a child's
  own request still focuses it), `BlockKeyLongPressTest` (Enter and Space click on
  release; a release without a press or a canceled one does not), `StatusBarViewTest`
  (`wifiIconLevel` across platform rating ranges, the pure `batteryIconLevel` encoding, that
  `refreshOnResume` re-reads the clock and the battery when the bar returns to view but
  touches neither when it leaves, the clock following a time zone change and the 12-hour
  setting, GPS waiting for the bar to be seen when switched on while hidden, detaching
  releasing every subscription, that cellular bars need no permission, Wi-Fi bars on SDK
  29, and, on SDK 28, the GPS listener surviving a provider toggle, the five-step Wi-Fi
  rating and the cellular subscription following its toggle; it sets
  `robolectric.areWindowsMarkedVisible`, without which Robolectric reports every window as
  gone and the bar never starts), `ParkStoreTest`
  (PIN round-trips against real `SharedPreferences`, the parked flag read back through a
  second instance — the path a reboot takes — that the PIN is never written in the
  clear, and that the store's file is the one both backup rule files exclude),
  `ParkActivityTest` (the park flows against Robolectric's lock task shadow: parking pins,
  the correct PIN unparks, unpins and closes, a wrong one keeps the lock, the guard re-pins
  after an unpin, setup needs the same PIN twice and never pins, parking after an
  abandoned setup shows the lock, and Home restores the lock only while parked),
  `ParkLockTaskGuardTest` (the pure `ParkLockRules.shouldRequestLockTask`,
  including the settle window that stops a redundant second pin request; plain JUnit),
  `ParkLockAvailabilityTest` (the pure `ParkLockRules.shouldOfferParkLock`, with the API
  boundary written as numbers so it is pinned independently of the constant; plain JUnit), `TileSizeStoreTest` (default and round trip
  through a second instance, an unknown stored name falling back to Medium, the 8 / 12 /
  15 Home counts, portrait as the landscape grid transposed, the app-list columns, and
  `SLOT_COUNT` filling every grid) and `ColorContrastTest` (every text colour on
  every fill it is drawn on at 4.5:1 and the focus ring and the charging bolt at 3:1, in
  both themes, read through an explicit `uiMode`).
  Write new behaviour so it lands in that surface — a pure function, a store, or
  something a Robolectric activity can reach. No device is ever available to check it.
  The job caches `~/.m2/repository/org/robolectric` separately: Robolectric fetches its
  ~100 MB `android-all` jar at test runtime with its own resolver, outside the Gradle
  cache `setup-gradle` restores. The cache key hashes `app/build.gradle.kts`, so any
  edit to that file costs one re-download. A test that adds a new SDK through `@Config`
  must come with an edit to that file: an unchanged key never saves the new jar, and every
  run downloads it again.
- `./gradlew assembleGithubRelease -PrequireSigning -PappVersionName=dev-<sha>
  -PappVersionCode=<commit count>` — minified + shrunk, signed via the `SIGNING_KEYSTORE_*` / `SIGNING_KEY_*` env vars
  (already configured on the repo; do not add or commit release signing material;
  `app/debug.keystore` is debug-only and committed on purpose). `-PrequireSigning` makes
  Gradle fail when one of them is empty instead of building unsigned. Without the version
  properties the version is `dev-local` / `1`. The checkout is full-depth because
  the versionCode is `git rev-list --count HEAD`, shared with `Release`.
- `dev-release` updates the `dev` pre-release in place with `gh`: it moves the `dev`
  tag to the built commit, uploads the new APK, then deletes the other assets. The
  download page stays one stable URL, and "keep only the latest" needs no cleanup. The
  updater no longer reads it.
- `dist` pushes the same APK to the home.codevoid.de dist store's nightly channel:
  `PUT /dist/nightly/motoLauncher-dev-<sha>.apk?version=dev-<sha>`. It has no
  `GITHUB_TOKEN` permissions (same-run artifacts download with the runner's own token);
  the `DIST_TOKEN` secret, which selects the dist repository, reaches only the push step.

**`Release`** (`.github/workflows/release.yml`) is manual, from `main` only, and has three
jobs. `build` (read-only, holds the signing secrets) picks the next patch version after
the newest `v*` tag (1.0.0 first), runs `.github/scripts/changelog.py release` to move
`[Unreleased]` under that version, commits and tags locally, builds the github APK and
the play bundle, and keeps the APK, the bundle, both mapping files, the notes and the
release commit as a git bundle in the `release-<version>` artifact. `publish` (contents
write, no Gradle) pushes commit and tag atomically, creates the GitHub release
`v<version>` with `gh` and the changelog section as text, and — when the
`PLAY_SERVICE_ACCOUNT_JSON` secret exists (job-level `HAS_PLAY`; the secret itself
reaches only the upload step) — uploads the bundle to the chosen Play track with
`changelog.py play-notes` as "What's new" (plain text, whole entries within 500
characters, plus the release link). Push and release creation are skipped when already
done, so `publish` can be re-run alone. `dist` waits for `publish` and pushes the APK
from the artifact to the release channel (`PUT /dist/release/motoLauncher-<version>.apk
?version=<version>`), with no `GITHUB_TOKEN` permissions and `DIST_TOKEN` only in that
step. `[Unreleased]` must hold at least one entry before it runs, or it refuses.

The actions run on their Node 24 majors. `actions/*` are referenced by major tag; the
third-party `gradle/actions/setup-gradle` and `r0adkll/upload-google-play` are pinned to
a commit SHA with the version as a comment. `setup-gradle` runs with
`cache-provider: basic`, the open-source cache, not its default commercial one.

No workflow can be run locally, and a green `Check` is the first real signal a branch
gets — it does not cover the release builds, so a minification or shrinking failure
surfaces only in `Build` (github) or `Release` (play).

Read CI results through the GitHub MCP tools (`actions_list`, `actions_get`,
`get_job_logs`), fetching only the failed steps' logs — the full logs are large. There
is no `.gh_token` in the repo.
