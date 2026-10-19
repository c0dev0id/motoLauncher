# Decisions

Key decisions and the reasons behind them. Many of these were reached after a simpler
attempt failed; the failed attempt is recorded where it explains why not to try it again.

## Platform

### Classic Views, not Compose
A launcher has to cold-start fast and be driven by a six-key remote. The View focus
engine already provides dpad traversal, Enter-to-activate and focus highlighting, and
keeps the APK small.

### `LauncherApps`, not `PackageManager`
`LauncherApps` is the launcher-appropriate API for enumerating, launching
(`startMainActivity`) and opening app info (`startAppDetailsActivity`), and it walks
every user profile. Its `Callback` reports changes for exactly the apps it enumerates.

### Scoped package visibility
The manifest declares `MAIN`/`LAUNCHER` `<queries>` instead of `QUERY_ALL_PACKAGES`. A
launcher only needs launchable apps, and the broad permission is discouraged.

### No networking library
The update check is one GET and one download. `HttpURLConnection` and `org.json` cover
both without a dependency.

## Remote and glove input

### The remote may only launch
Everything that needs touch to leave (app info, the search field, the header buttons,
dialogs, the park keypad) is unreachable from the remote. A rider on the road cannot
type or back out of a touch-only screen. Holding Escape is allowed because it only
launches. Configuration is a touch workflow placed where it is used; there is no
separate settings screen. An earlier Configure Favorites activity duplicated the
in-place flows and was removed.

### Native focus traversal
Dpad navigation uses Android's View focus engine, not custom key handling. A fixed grid
traverses correctly by visual proximity, and custom handling would have to re-implement
what the framework already does.

### `TouchOnlyRow` hides controls from the dpad
`TouchOnlyRow.addFocusables()` contributes nothing, so its children are invisible to the
focus finder while touch focus still works (`requestFocus()` never consults
`addFocusables`). Two alternatives failed:
- `focusableInTouchMode` on the `EditText`: it implies `focusable`, and the dpad path
  ignores the touch-mode flag, so the field stayed reachable.
- `descendantFocusability="blocksDescendants"`: it blocks touch focus too, so the search
  field stopped opening the keyboard.

### Key long-press is blocked, touch long-press is not
`blockKeyLongPress()` routes DPAD_CENTER/Enter through `performClick()` on key-up and
never arms the framework's long-press timer. A held remote button therefore cannot open
the tile menu. The long-click listener is untouched, so a touch long press still works.

### Escape: short press is back, hold launches the navigation app
The remote has no spare key and no spare gesture, and Escape was the only key with a
meaning outside a tile. A hold is the one input left that can carry an action without
making anything new dpad-reachable. A short Escape on Home does nothing, since Home is
the root.

The navigation app is an explicit setting (`NavAppStore`). It replaced an implicit rule
that slot 0 was the navigation app. A missing or uninstalled navigation app is a silent
no-op: `launchIfInstalled` checks `isActivityEnabled` because `startMainActivity` throws
on a component that no longer exists.

### The Escape hold is timed from the key's own timestamps
`EscapeKeys` decides short vs. hold on ACTION_UP from `eventTime - downTime` against the
long-press timeout. The first version used the framework's `onKeyLongPress`, which is
delivered off the first key repeat. It worked from a USB keyboard and never fired from
the handlebar remote, which sends a plain down and up with no repeats. Measuring the gap
behaves the same on every device. The framework path was removed rather than kept as a
second route, which would have needed an "already fired" flag against a double launch.

`onKeyDown` still claims the DOWN and calls `startTracking()`, so an UP whose DOWN went
to another window arrives untracked and is ignored. A remote that emits an
instantaneous down/up pair on release cannot produce a hold; `adb shell getevent -lt`
shows the real timestamps if that is ever suspected.

## Home screen

### Weighted `LinearLayout` grid, not `RecyclerView`
Twelve tiles, always visible, never scrolling. With a `RecyclerView` the adapter bound
before the parent's final size was known, and on cold start the first frame showed the
third row cut off; the workaround recomputed row height on every layout pass. Nested
weighted `LinearLayout`s divide the space in the layout pass by construction. Tiles are
inflated once in `onCreate` and rebound in `onResume`.

### Consequences of reusing the twelve tile views
Remote focus is seeded on the first tile once, in `onCreate`. Re-seeding in `onResume`
would pull the remote back to the first tile after every launch. `bindTile` clears the
long-click listener and `isLongClickable` for a tile without a long press, or the
framework keeps arming the timer from the tile's previous binding.

### Resolved favourites are cached in a `ViewModel`
`FavoritesCache` holds the resolved `AppEntry`s keyed on (`packageGeneration`, slot
list), so returning from the navigation app costs no Binder calls or icon decoding. It
was an activity field until the Sensor orientation made rotation common: every recreate
then redid the whole resolve on the main thread in `onResume`.

### Favourites are slots in `SharedPreferences`
Eleven slots plus the fixed All Apps tile fill the 4×3 grid. A slot holds a flattened
`ComponentName`, or the `link:` marker with the label and URL in two sibling keys. Each
setter removes the other representation's keys.

### Remove instead of Reassign
The tile menu once offered "Reassign app". Remove followed by tapping the empty "+" tile
is simpler and avoids carrying the current occupant and the pick target across the
picker round trip.

### The picker writes the slot itself
`AppListActivity.pickIntent(context, slot)` stores the choice in `FavoritesStore` and
finishes; Home rebuilds in `onResume`. No result contract, no result extra, and no
"which slot was I picking for" state in the caller.

## App list and settings

### Process-level app-list cache
Enumerating and rasterising every installed app is the most expensive thing the app
does. `MotoLauncherApp.getApps()` returns a `Deferred` that is reissued only when
`packageGeneration` moved. Callers detect a stale list by comparing the returned
instance. Home warms it in `onCreate`, so All Apps opens with the list decoded, and
`AppListViewModel` keeps the reference across a theme or rotation recreate.

### Package changes come from one process-wide callback
A single `LauncherApps.Callback`, registered in `MotoLauncherApp`, bumps
`packageGeneration` on every package event and clears favourite slots on removal. It is
process-wide because an activity paused during an uninstall would miss the event that
concerns it most. Only `onPackageRemoved` clears a slot: an app on unmounted storage is
unavailable, not uninstalled, and clearing it would lose the configuration. Known gap:
a screen already in front when a package changes waits for its next resume.

### Uninstall needs two manifest declarations
The `ACTION_DELETE` hand-off fails silently without either:
`REQUEST_DELETE_PACKAGES` (required since target SDK 28, granted at install), and a
`<queries>` entry for `ACTION_DELETE` with the `package` scheme, because package
visibility filters intent resolution and the system uninstaller has no launcher entry.
Neither is reachable from a unit test.

### Settings live in a mode of the app list
Settings were once header buttons beside the search field; more settings would have
crowded the field and shrunk the buttons below glove size. A Settings toggle now swaps
the grid's contents for text-only settings tiles in the same `RecyclerView`. The search
field stays and filters the settings tiles. `AppListViewModel` carries the mode, the
update-check state and the download subtitle, so the theme toggle's `recreate()`
returns to settings mode. A short Escape leaves settings mode before it leaves the
screen.

### The first three settings tiles are fixed
`UPDATE_TILE_INDEX` is 2 (theme, navigation app, update) so download progress can patch
one tile in place instead of re-rendering the list on every progress callback.
Conditional tiles go after it.

### Hidden apps are stored per package
`HiddenAppsStore` keeps a `Set<String>` of package names, read once per render.
`getStringSet` returns the live internal set, so the store always returns a copy.

### Permission-gated toggles flip only on grant
The cellular and GPS toggles request their permission through
`registerForActivityResult(RequestPermission())`. The store is set to enabled only when
the grant arrives, so a setting never reads On without the access behind it.

### A picker for multi-valued settings whose intermediate values are disruptive
Settings tiles cycle on tap, which suits two or three harmless values (theme, units,
battery display). Orientation has five values, and cycling would rotate the screen
through each one. `showOptionsDialog` lists the values on the launcher's dialog theme
and applies only the one picked. Its rows are inflated from `item_dialog_option`: a
style applied through a `TextView` constructor does not carry its `layout_*`
attributes. The current value is drawn in `tile_focused` because the tile showing it is
hidden behind the dialog.

## Orientation

### Sensor uses `FULL_SENSOR` and works by not handling the configuration change
`FULL_SENSOR` is the exact superset of the four fixed options. `SENSOR` omits reverse
portrait on most devices, and `USER` defers to Android's auto-rotate switch, whereas
picking Sensor in the launcher is meant to be the whole decision. Rotation reshapes the
UI only because no activity lists `orientation` in `configChanges`: the system
recreates them, so the grid, the column count and the status bar's `layout-port`
variant are chosen afresh. Adding `orientation` there would freeze the layout in its
starting shape. Sensor is the default for installs that never set the option.

## Dialogs, theme and layout

### Tile menus are plain `Dialog`s on the launcher theme
The tile and link menus use a custom layout on `Theme.MotoLauncher.Dialog`, not an
`AlertDialog`, whose Material3 look brings its own surface colours and small buttons.
The custom view reuses `tile_background`, so the menu reads as part of the grid and
stays glove-sized. The update flow and the link dialog use `AlertDialog`; they are
off-bike tasks. Their look comes from `alertDialogTheme`, so no call site names a theme.

### Every dialog goes through `showImmersive()`
A dialog is its own window and would bring the system bars back while it shows.
`showImmersive()` re-applies immersive mode to the dialog's window and drops the dialog
when its host activity is finishing or destroyed. An update result that arrived after a
recreate used to crash on a dead window token.

### Immersive mode everywhere
The status bar is always hidden; the navigation bar follows a setting, hidden by
default. Nothing on the system bars is useful on a single-purpose, glove-operated
screen. `setDecorFitsSystemWindows` mirrors the navigation-bar setting so a visible bar
gets its space without manual insets. Activities re-apply on window focus because
permission dialogs and the installer restore the bars.

### Theme by resource qualifier only
Layouts reference colour tokens; `values-night/colors.xml` overrides them. The theme
toggle needs no colour logic in code.

### Signal meters have their own "unlit" colour
Unlit bars were once drawn in `on_surface_muted`, a secondary-text colour that sits
about 1.85:1 from `on_surface` in the dark theme, so full and empty signal looked
alike. `signal_track` exists for unlit segments. `on_surface_muted` could not simply be
darkened because secondary text and the "+" / "None" glyphs share it.

### Dotted root style names set `parent=""`
AAPT reads `Widget.MotoLauncher.DialogAction` as inheriting from a non-existent
`Widget.MotoLauncher` and fails resource linking. The failure only shows in CI.

### Glove sizing is stated once
Every size is a `@dimen` with a phone default and a `sw600dp` override sized for the
7" 1920×1080 device, never set per view.

## Status bar

### A self-contained view
`StatusBarView` registers its receivers and callbacks in `onAttachedToWindow` and
releases them in `onDetachedFromWindow`, so `HomeActivity` does no lifecycle wiring.
Settings arrive through the `settings` `OnSharedPreferenceChangeListener`, keyed by
each store's public `KEY_*` constant. Icons are `<level-list>` drawables updated with
one `setImageLevel()`. The battery encodes charging in the level number (0–4 idle,
5–9 plugged), so plugging in and a level change take the same path.

### Wi-Fi reads RSSI from `NetworkCapabilities`
RSSI comes from `NetworkCapabilities.transportInfo` in a `NetworkCallback`, which needs
only `ACCESS_WIFI_STATE` and `ACCESS_NETWORK_STATE`. `WifiManager.connectionInfo` would
need location permission.

### The Wi-Fi icon follows the radio; no connection is zero bars
Visibility keys off `WIFI_STATE_CHANGED_ACTION`, seeded from `isWifiEnabled`. Losing the
last network draws zero bars instead of hiding the icon. An earlier version hid the icon
whenever no network was connected, which made "radio off" and "radio on, nothing in
range" look the same.

### The Wi-Fi rating range is inclusive
`WifiManager.calculateSignalLevel` returns `[0, maxSignalLevel]` inclusive, five values
on a typical device. Dividing by `maxSignalLevel - 1` reported three of four bars as
full and made the three-bar icon unreachable. `wifiIconLevel` is a pure function so the
mapping is unit-tested at both ends.

### Cellular shows behind three gates and takes the level directly
The meter appears only when the setting is on, telephony is available on API 31+ and
`READ_PHONE_STATE` is granted. `SignalStrength.level` is documented as 0–4 and matches
the icon's five states. A separate network watch was dropped: the telephony callback
already reports level 0 for no coverage.

### GPS speed is paused whenever it cannot be seen
Speed polls `GPS_PROVIDER` at 1 Hz, which costs CPU the navigation app needs. It is the
one subscription stopped while the window is hidden or the screen is off. The other
callbacks are event-driven and cheap enough to leave running.

### Clock and battery are re-read on resume
`ACTION_TIME_TICK` stops while the screen is off, so a device picked up after a night
showed the minute it went dark until the next tick. `refreshOnResume` is called from
two edges, `ACTION_SCREEN_ON` and `onWindowVisibilityChanged(VISIBLE)`; each covers a
case the other misses, and one method keeps them from drifting apart. The battery rides
along because a plug or unplug while asleep can miss its broadcast, and the read is
free: `ACTION_BATTERY_CHANGED` is sticky and unchanged values touch no views. The radios
re-deliver through their own callbacks and are left out. A polling clock was rejected
as wasted CPU.

A manual time or timezone change still corrects at the next tick. Adding
`ACTION_TIME_CHANGED` and `ACTION_TIMEZONE_CHANGED` was considered and declined as too
rare to justify. Do not add them without being asked.

## Park lock

### Lock task mode, without device owner
The device runs without a lock screen on purpose: a credential at a red light or after
a roadside reboot is worse than the risk it covers. A parked bike wants the opposite,
but Android cannot suppress an existing credential on demand for an ordinary app:
`setKeyguardDisabled` needs device owner, `disableKeyguard` only works on a non-secure
keyguard, and trust agents need a signature permission. Toggling the credential itself
needs device owner and therefore a factory reset, and would add a credential the rider
could be locked out by. Lock task mode (`startLockTask()`) blocks Recents and the
notification shade without touching any credential. The Home gesture cannot be
swallowed otherwise: gesture exclusion covers only the Back edges, and
`ACTION_CLOSE_SYSTEM_DIALOGS` has been closed to third-party apps since Android 12.

### The park screen lives in its own task
`ParkActivity` is `singleInstance` with its own `taskAffinity`. `HomeActivity` is
`singleTask` and the root of the home task, so a Home press clears everything above it,
which would destroy a park screen stacked there.

The separate task is restored explicitly: while `ParkStore.isParked` is set,
`HomeActivity.onResume` re-launches the park screen. That one path covers a reboot,
which pinning does not survive, and a Home press on a device that refused pinning. The
park screen sets `isParked` itself, so no entry point carries a protocol, and the Park
lock tile finishes the app list as it launches so unlocking returns to Home.

### Measured on the device
On the 7" device with gesture navigation, an app pinning itself is not asked to
confirm, so parking is one tap. The shade is blank and Home, Back and Recents are
inert. `lockTaskModeState` updates asynchronously and reads `NONE` straight after a
successful request; the settle window in `shouldRequestLockTask` and the delayed
`settleCheck` exist for that, and stop a redundant second request and toast.

### The unpin gesture is undone, not blocked
The system unpin gesture (hold Back + Recents) cannot be blocked, and an ordinary app
gets no unpin callback. While parked and resumed, a guard polls `lockTaskModeState`
every 300 ms and re-pins. On a correct PIN the guard is disarmed and `isParked` cleared
before `stopLockTask()`, or an in-flight run would re-pin what the PIN released. The
guard is disarmed in `onPause`, so a parked device with the screen off polls nothing.

The consequence was accepted deliberately: while parked, the PIN is the only way out
short of adb or reinstalling. A forgotten PIN strands the device.

### Pinning state is not shown
A status line under the keypad reported whether pinning took while the mechanism was
being proven. It was removed once pinning was confirmed: the screen is the same lock to
its user either way, and the line offered no action. The state is still read to stop
retrying on a device with screen pinning switched off.

### The keypad is touch-only
The keypad sits in a `TouchOnlyRow`, so the remote can never drive it.

### The PIN hash is hygiene, not security
The PIN is salted and hashed so it is not in a prefs file in plain text. Four digits
fall to a trivial search, and anyone who can read the file has adb, which defeats the
park lock outright.

### Offered only on Android 15+ and without a secure lock screen
Below Android 15 the system confirms every `startLockTask()`, so the guard would answer
an unpin with a dialog instead of a silent re-pin. A device with a secure lock screen
already has a better park lock one power-button press away. `shouldOfferParkLock` gates
the settings tile only; that is enough because the tile is the sole entry point and the
restore path fires only on a flag the park screen sets. The Android 15 boundary comes
from testing on 14 (confirmed every pin) and 16 (silent); 15 itself is untested and
assumed to behave like 16.

## Updates

### A fixed `dev` pre-release tag
CI deletes and recreates the `dev` pre-release on every push to `main`, so the app
queries one stable URL and "keep only the latest" needs no cleanup. The version comes
from the APK filename (`motoLauncher-<versionName>.apk`). Any published build that
differs from the installed one counts as an update. The check is user-triggered only:
the device is mostly offline.

### Install is a hand-off; the cache holds at most one APK
The APK is downloaded to `cacheDir/updates/` and handed to the system installer through
`FileProvider`. `download()` removes every other file first. On process start only the
APK of the running version is deleted: a different version might still be open in the
installer if the process was restarted to serve it.

### Progress on the tile, outcomes in dialogs
There is no room for a status line, and a transient one is easy to miss on a
handlebar-mounted screen, so progress rides on the update tile's subtitle, throttled to
500 ms. The percentage is concatenated, never passed through `String.format`: a `%` in
a format template once crashed the main thread.

## Build and CI

### Two workflows
`Check` runs on every push to a branch other than `main`. `Build` runs on push to
`main` and is the only producer of a release. For a long time `Build` was the whole
pipeline, so a branch got no signal before merge on a project that cannot be built
locally.

### `Check` is one job with one Gradle invocation
`./gradlew --continue lintDebug testDebugUnitTest assembleDebug` shares
`compileDebugKotlin` and one daemon start-up; parallel jobs would each pay checkout, JDK
setup and compilation. `lintDebug` analyses one variant where `lint` does two.
`--continue` reports lint, test and compile failures from the same run. The release
variant is left to `Build` because it needs the signing secrets and roughly doubles the
build, so a green `Check` does not prove `assembleRelease`.

### No `pull_request` trigger
A check run attaches to the head commit, so the push run already shows on a PR. The
event only added a second run when a PR was opened on an already-built branch. Opening
a PR therefore triggers nothing, and PRs from forks go unchecked.

### The debug build installs beside the release build
The debug variant has a `.debug` application ID suffix and its own app name, so a branch
can be tested on the device without replacing the launcher in daily use.

### Robolectric's SDK jar is cached separately
Robolectric downloads its ~100 MB `android-all` jar at test runtime into `~/.m2`,
outside the Gradle cache. The cache key hashes `app/build.gradle.kts`.

## Documentation

### The changelog is for the rider
Entries describe what the user sees and does, with no class names, permissions,
resource qualifiers or CI. Until the first release `[Unreleased]` is a plain feature
list under `### Added`: a fix or change to behaviour no user ever had is folded into
its feature's entry or dropped.
