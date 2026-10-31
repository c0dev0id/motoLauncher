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

### An app is a component in a profile
The app list shows every app from every profile; a launcher holds no personal data, and
some riders hand the device to people who only want big buttons. The same component can
be installed in the personal and the work profile, so an app is a `ComponentKey`
(component plus `UserHandle`). Launch, App info and Uninstall go to that profile; the
uninstaller takes it as `Intent.EXTRA_USER` and checks that it is one of ours. An app
stored in a preference keeps its profile as the profile's serial number, because a
`UserHandle` id can be reused by a later profile and a serial number cannot. The own user
is stored as no serial at all, so a favourite set before profiles were tracked reads
unchanged. A serial whose profile no longer exists, for example after a restore on
another device, reads as an empty slot. Hidden apps stay per package, across profiles.

### Every launch checks the app still resolves
A tile can outlive its app: the uninstaller's dialog closes before the package is gone,
so the screen behind it resumes with the old tile, and a stored navigation app outlives
its uninstall. `startMainActivity` throws for an activity that no longer resolves, so
`AppRepository.launch` asks `isActivityEnabled` first and does nothing when it is not.

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

Configuration is touch-only even where Escape could leave it: the tiles of the app
list's settings mode and both pick modes, and Home's empty "+" tiles, cannot take focus.
They are the same views as the launch tiles, so focusability is set on every bind. They
used to be focusable on the reasoning that settings mode is entered by touch. It also
survives a trip to the navigation app, though, and from there the remote reached the
Park lock tile and a set-PIN screen it cannot leave.

### Native focus traversal
Dpad navigation uses Android's View focus engine, not custom key handling. A fixed grid
traverses correctly by visual proximity, and custom handling would have to re-implement
what the framework already does.

### `TouchOnlyRow` hides controls from the dpad
The framework moves focus into a subtree without the child asking in three ways, and
`TouchOnlyRow` refuses all three: it adds nothing in `addFocusables()` (focus search),
returns false from `onRequestFocusInDescendants()` (default focus when the first remote
key leaves touch mode, and the refocus after the focused view is removed), and drops
`focusableViewAvailable()` (outside touch mode the window hands focus to a view that
becomes focusable while nothing has it). A touch focuses the touched view directly and
passes none of them, so the search field still opens the keyboard. The first version
overrode only `addFocusables()`; default focus then put the remote on the app list's back
button and on park keypad key "1". Two alternatives failed:
- `focusableInTouchMode` on the `EditText`: it implies `focusable`, and the dpad path
  ignores the touch-mode flag, so the field stayed reachable.
- `descendantFocusability="blocksDescendants"`: it blocks touch focus too, so the search
  field stopped opening the keyboard.

### Key long-press is blocked, touch long-press is not
`blockKeyLongPress()` routes the confirm keys through `performClick()` on key-up and
never arms the framework's long-press timer. A held remote button therefore cannot open
the tile menu. The long-click listener is untouched, so a touch long press still works.
The keys are the ones `View` itself treats as confirm keys (DPAD_CENTER, Enter, numpad
Enter, Space); `KeyEvent.isConfirmKey` is hidden API, so the list is written out.

The key-up clicks only when it ends a press on this view and is not canceled. An UP
whose DOWN went to another window finds the view unpressed. When focus leaves the window
mid-press (an incoming call, a system dialog), the system ends the press with a canceled
UP that arrives before the window learns it lost focus, so the pressed state alone does
not catch that one.

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
A fixed set of tiles, always visible, never scrolling. With a `RecyclerView` the adapter bound
before the parent's final size was known, and on cold start the first frame showed the
third row cut off; the workaround recomputed row height on every layout pass. Nested
weighted `LinearLayout`s divide the space in the layout pass by construction. Tiles are
inflated when the activity first resumes or the tile size has changed since, and are
rebound on every resume.

### Consequences of reusing the tile views
Remote focus is seeded on the first tile only when new tiles are inflated. Re-seeding on
every resume would pull the remote back to the first tile after every launch. `bindTile` clears the
long-click listener and `isLongClickable` for a tile without a long press, or the
framework keeps arming the timer from the tile's previous binding.

### Resolved favourites are cached in a `ViewModel`
`FavoritesCache` holds the resolved `AppEntry`s of the current `packageGeneration`, so
returning from the navigation app costs no Binder calls or icon decoding. A favourite it
has not seen yet is resolved alone; it used to re-resolve every slot whenever one
changed. An app that does not resolve is remembered as such until the next generation,
so an empty-looking slot is not asked again on every resume. It was an activity field
until the Sensor orientation made rotation common: every recreate then redid the whole
resolve on the main thread in `onResume`.

### Favourites are slots in `SharedPreferences`
Fourteen slots plus the fixed All Apps tile fill the largest grid, 5×3. A smaller grid
shows the leading slots; the others keep their contents and reappear when the grid grows
again, so changing the tile size never loses configuration. A slot holds a flattened
`ComponentName` with its profile's serial in a sibling key, or the `link:` marker with
the label and URL in two sibling keys. Every write removes all of a slot's keys first.

### Remove instead of Reassign
The tile menu once offered "Reassign app". Remove followed by tapping the empty "+" tile
is simpler and avoids carrying the current occupant and the pick target across the
picker round trip.

### Tile size is a tile count, the same in both orientations
Tile size (Large, Medium, Small) sets Home to 8, 12 or 15 tiles and the app list to 4, 5
or 6 columns in landscape, 3, 4 or 5 in portrait. It exists so the physical tile size
can follow the screen: tile area scales with the square of the diagonal, so a 6" screen
needs about 9 tiles and an 8" about 16 to keep the 7" device's tile size.

The first design offered separate −1 / +1 offsets for Home's columns and rows. It was
dropped before it was built, for two reasons. Offsets applied per screen axis give
different tile counts in landscape and portrait whenever the two differ, so a rotation
under Sensor would have hidden or shown a slot and moved All Apps. And a fourth
landscape row leaves a tile shorter than its icon plus a two-line label on the 7"
device, which would have needed the icon to shrink to fit. The portrait grid is now the
landscape grid transposed. In landscape no shape (4×2, 4×3, 5×3) has more than the
default three rows; the one extra row, Small in portrait, runs along the screen's long
side. The tile layout is unchanged.

The app list follows the same setting rather than its own, so the choice reads as one
decision about the screen. It is a picker, not a cycling tile: each step reflows the
settings grid, which moves the Tile size tile out from under the finger, and the picker
shows each size's tile count before it is applied.

### The picker writes the slot itself
`AppListActivity.pickIntent(context, slot)` stores the choice in `FavoritesStore` and
finishes; Home rebuilds in `onResume`. No result contract, no result extra, and no
"which slot was I picking for" state in the caller.

## App list and settings

### Process-level app-list cache
Enumerating and rasterising every installed app is the most expensive thing the app
does. `MotoLauncherApp.getApps()` returns a `Deferred` that is reissued only when
`packageGeneration` moved. Home warms it in `onCreate`, so All Apps opens with the list
decoded. `AppListViewModel` maps each generation to `getApps().await()` and holds the
result in a `StateFlow` that is shared only while the screen is started; a recreate
reuses it. `map` handles one generation at a time, so an older load cannot replace a
newer one.

The load enumerates on `Dispatchers.IO` and decodes each icon in its own coroutine on
`Dispatchers.Default`. Decoding is CPU work; on the IO dispatcher one coroutine per app
started up to 64 threads for it, where the default dispatcher uses one per core.

### Package changes come from one process-wide callback
A single `LauncherApps.Callback`, `PackageWatcher`, registered in `MotoLauncherApp`,
advances `packageGeneration` (a `StateFlow`) on every package event and clears favourite
slots on removal. It is process-wide because an activity paused during an uninstall
would miss the event that concerns it most. Only `onPackageRemoved` clears a slot: an
app on unmounted storage is unavailable, not uninstalled, and clearing it would lose the
configuration.

Screens collect the generation while started, so a change that arrives while a screen is
in front shows at once. That case is the common one: the uninstaller's dialog closes
before the package is gone, and the screen behind it has already resumed. Home skips the
generation current at start (`drop(1)`), because `onResume` rebinds anyway; a change while
it was stopped is caught there.

### App list tiles have stable IDs
The app list re-renders on every resume and every package change through
`notifyDataSetChanged`. Without stable IDs that recycles every holder, the focused one
included, and the remote's position is lost. `AppTileAdapter` has stable IDs, one per
`TileItem.key` (the label for settings tiles, the `ComponentKey` for apps), and
`RecyclerView` restores focus to the holder with the same ID after the layout. Diffing
with `ListAdapter` was the alternative; it would skip rebinding unchanged tiles, but
`submit` also changes every tile's focusability, so each tile is rebound anyway.

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

### Settings tiles are rebuilt on every render
`SettingsTiles` builds the list from the stores each time it is shown, and every change
of a setting ends in one re-render callback. The list used to be cached, and ten tiles
each had to invalidate the cache before re-rendering; a missed invalidation showed a
stale value. The expensive part of the old rebuild was resolving the navigation app's
label with an icon decode on the main thread; the label now comes from the loaded app
list, so a rebuild is a dozen `TileItem`s and a keyguard query.

Download progress re-renders like any other change. It used to patch the update tile in
place, found by position and then by label, because a search filter or leaving settings
mode changed what the adapter held; a full render reads the current mode and filter by
construction. Stable IDs keep the remote's focus through it.

### Hidden apps are stored per package
`HiddenAppsStore` keeps a `Set<String>` of package names, read once per render.
`getStringSet` returns the live internal set, so the store always returns a copy.

### The GPS toggle flips only on grant
The GPS toggle requests `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` together
through `RequestMultiplePermissions`: from Android 12 a request for fine location alone
is refused without a dialog on some releases, so the first version's tile never turned
on there. The store is set to enabled only when precise location is granted; speed
needs it, and a rider who picks "Approximate" keeps the tile Off. The tile reads the
setting and the permission together, so it never reads On without the access behind
it, also after the permission is revoked in system settings.

### A picker for multi-valued settings whose intermediate values are disruptive
Settings tiles cycle on tap, which suits two or three harmless values (theme, units,
battery display). Orientation has five values, and cycling would rotate the screen
through each one. `showOptionsDialog` lists the values on the launcher's dialog theme
and applies only the one picked. Its rows are inflated from `item_dialog_option`: a
style applied through a `TextView` constructor does not carry its `layout_*`
attributes. The current value's row is selected, drawn as a solid `tile_focused` fill
with `on_tile_focused` text, because the tile showing it is hidden behind the dialog.
Accent-coloured text on `tile_default` was the first marker; it is 3.18:1 in the dark
theme.

## Orientation

### Sensor uses `FULL_SENSOR` and works by not handling the configuration change
`FULL_SENSOR` is the exact superset of the four fixed options. `SENSOR` omits reverse
portrait on most devices, and `USER` defers to Android's auto-rotate switch, whereas
picking Sensor in the launcher is meant to be the whole decision. Rotation reshapes the
UI only because no activity lists `orientation` in `configChanges`: the system
recreates them, so the grid, the column count and the status bar's `layout-port`
variant are chosen afresh. Adding `orientation` there would freeze the layout in its
starting shape. Sensor is the default for installs that never set the option.

### The large-screen orientation rule is opted out of, for now
With targetSdk 36, Android 16 ignores requested orientations on displays of 600dp
smallest width and up, which includes the 7" device. The manifest's
`PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` keeps the setting working. Google ends
the opt-out at targetSdk 37, which Play is likely to require about a year after API 36.
From then on the setting has no effect on large screens running Android 16 or newer,
unless the rider sets the launcher to its app default in Android's aspect ratio
settings.

## Dialogs, theme and layout

### Tile menus are plain `Dialog`s on the launcher theme
The tile and link menus use a custom layout on `Theme.MotoLauncher.Dialog`, not an
`AlertDialog`, whose Material3 look brings its own surface colours and small buttons.
The custom view reuses `tile_background`, so the menu reads as part of the grid and
stays glove-sized. The update flow and the link dialog use `AlertDialog`; they are
off-bike tasks. Their look comes from `alertDialogTheme`, so no call site names a theme.

### Dialog controls are touch-only
A touch opens every dialog, but the first remote key afterwards used to focus its first
row or button, and Enter then ran App info, Uninstall or the update download, which
hand off to system screens the remote may not be able to leave. Every dialog row
(`Widget.MotoLauncher.DialogAction`) and every `AlertDialog` button
(`Widget.MotoLauncher.DialogButton`) is therefore unfocusable. One style item covers all
the buttons because AppCompat resolves the positive, negative and neutral button styles
through `buttonBarButtonStyle`, the only one the Material3 alert overlay sets. Escape
still cancels a dialog with nothing focused. The link dialog's fields sit in a
`TouchOnlyRow`; focus search then no longer finds the URL field from the label, so the
label names it in `nextFocusForward` to keep the keyboard's Next working.

### Every dialog goes through `showImmersive()`
A dialog is its own window and would bring the system bars back while it shows.
`showImmersive()` re-applies immersive mode to the dialog's window and drops the dialog
when its host activity is finishing or destroyed. An update result that arrived after a
recreate used to crash on a dead window token.

### Immersive mode everywhere
The status bar is always hidden; the navigation bar follows a setting, hidden by
default. Nothing on the system bars is useful on a single-purpose, glove-operated
screen. Activities re-apply on window focus because permission dialogs and the installer
restore the bars.

### Content is padded by insets, not fitted by the decor
From targetSdk 35, Android 15 draws every activity edge to edge and disables
`setDecorFitsSystemWindows`, which used to give a visible navigation bar its space.
`fitContentToSystemBars()` lays each activity out edge to edge on every API level and
pads `android.R.id.content` by the visible system bars and the display cutout, so one
mechanism covers Android 9 to 16. Hidden bars report no insets, so the padding follows
the navigation-bar setting by itself. Below Android 11 that depends on androidx.core
1.16 or newer: earlier versions passed through the legacy layout-stable inset, the
height of a hidden navigation bar, and left an empty strip in its place. Dialog windows
are floating and keep the platform default.

### Theme by resource qualifier only
Layouts reference colour tokens; `values-night/colors.xml` overrides them. The theme
toggle needs no colour logic in code.

### Text holds 4.5:1 on every fill, in both themes
The pressed fill is a per-theme value: a light accent tint in the light theme, a deep
accent in the dark. A single deep blue left the light theme's dark labels at 2.38:1
while a finger was down. Turning labels white while pressed was rejected because every
text and glyph colour on a tile would then need a state list; with a per-theme fill,
`on_surface` and `on_surface_muted` read on it unchanged.

Text on a `tile_focused` fill is `on_tile_focused`; `on_surface` there was 3.92:1 in the
light theme. A colour that depends on a view's state is a colour state list in
`res/color/`, and code only sets the state (`isActivated` on the Settings button), so the
theme toggle still needs no colour logic in code.

`AlertDialog` buttons are `on_surface`, through `buttonBarButtonStyle` in the alert
overlay, the attribute Material3's own overlay sets. Material's default, `colorPrimary`,
is about 4:1 on the dialog background in both themes.

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
only `ACCESS_WIFI_STATE` and `ACCESS_NETWORK_STATE`. `getTransportInfo()` exists from
Android 10, but `WifiInfo` only implements `TransportInfo` from Android 12, so the cast
is gated on API 31 and Android 9 to 11 read `WifiManager.connectionInfo` instead. The
first version gated on API 29, and Android 10 and 11 showed zero bars. Without location
permission `connectionInfo` hides only the SSID, BSSID and MAC address (Android 9's
`WifiServiceImpl.getConnectionInfo`); the RSSI comes through.

The callback runs on the connectivity thread. Its set of networks lives in the callback
object, one per registration, and nothing else touches it; every level is posted to the
main thread, which drops repeats. The first version shared the set with the main thread
and compared against the last level on the callback thread, where a quick 2-3-2 change
could drop the second 2 and leave three bars on screen.

### The Wi-Fi icon follows the radio; no connection is zero bars
Visibility keys off `WIFI_STATE_CHANGED_ACTION`, seeded from `isWifiEnabled`. Losing the
last network draws zero bars instead of hiding the icon. An earlier version hid the icon
whenever no network was connected, which made "radio off" and "radio on, nothing in
range" look the same.

### The Wi-Fi rating range is inclusive
`WifiManager.calculateSignalLevel` returns `[0, maxSignalLevel]` inclusive, five values
on a typical device. Dividing by `maxSignalLevel - 1` reported three of four bars as full
and made the three-bar icon unreachable. `wifiIconLevel` is a pure function so the
mapping is unit-tested at both ends. Before Android 11 the device's scale does not exist,
and the static `calculateSignalLevel(rssi, 5)` rates on the icon's own five steps.

### Cellular needs no permission and takes the level directly
The meter appears while the setting is on. It subscribes with `TelephonyCallback` on
Android 12 and later and with `PhoneStateListener` before, which delivers the same
`SignalStrength`. Each subscription hands back the call that ends it; on Android 9 the
platform holds a `PhoneStateListener` only weakly, so that stored call is also the
reference that keeps the listener alive. Neither needs a permission: `TelephonyRegistry`
asks for `READ_PHONE_STATE` only for call forwarding, voicemail and emergency-number
events, plus call state, data subscription, cell info and display info from Android 12,
from Android 9 through current AOSP. The meter used to stop at Android 12 and ask for
that permission, which bought nothing. `SignalStrength.level` is documented as 0–4 and
matches the icon's five states. A separate network watch was dropped: the telephony
callback already reports level 0 for no coverage.

### Android 9 is the floor
`minSdk` is 28. Everything outside the status bar is API 28 or older, goes through
androidx, or is version-gated already. In the status bar, Wi-Fi and cellular have the
pre-29 and pre-31 paths above, and the GPS listener is a `LocationListenerCompat`:
before Android 11 `LocationListener`'s provider and status callbacks are abstract, so a
lambda implementing only `onLocationChanged` threw `AbstractMethodError` the first time
GPS was switched on or off. Robolectric runs those paths on SDK 28. What it cannot show
is how a real Android 9 device lays out the hidden-bar insets.

### GPS speed is paused whenever it cannot be seen
Speed polls `GPS_PROVIDER` at 1 Hz, which costs CPU the navigation app needs. It is the
one subscription stopped while the window is hidden or the screen is off. The other
callbacks are event-driven and cheap enough to leave running.

`registerGps` checks the condition itself (window visible, `PowerManager.isInteractive`),
so every path that starts GPS obeys it: the window becoming visible, the screen coming
on and the settings toggle. The toggle used to start GPS unchecked, and switching speed
on in the app list and going straight on to the navigation app left 1 Hz GPS running
behind it until the screen went off. Attaching starts nothing itself: `View` sends
`onWindowVisibilityChanged` right after `onAttachedToWindow` unless the window is gone,
and that call refreshes the clock and battery and starts GPS.

A fix without speed, and location being switched off, show dashes. The last speed used
to stay on screen, where it reads as current.

### Clock and battery are re-read on resume
`ACTION_TIME_TICK` stops while the screen is off, so a device picked up after a night
showed the minute it went dark until the next tick. `refreshOnResume` is called from
two edges, `ACTION_SCREEN_ON` and `onWindowVisibilityChanged(VISIBLE)`; each covers a
case the other misses, and one method keeps them from drifting apart. The battery rides
along because a plug or unplug while asleep can miss its broadcast, and the read is
free: `ACTION_BATTERY_CHANGED` is sticky and unchanged values touch no views. The radios
re-deliver through their own callbacks and are left out. A polling clock was rejected
as wasted CPU.

The formatter is built on every update, because a `SimpleDateFormat` keeps the time
zone it was created in. The first version built it once, and a rider crossing into
another zone saw the old zone's time until Home was recreated, which for a `singleTask`
launcher can take days. One formatter a minute costs nothing. With that, a manual time
or time zone change corrects at the next tick. Adding `ACTION_TIME_CHANGED` and
`ACTION_TIMEZONE_CHANGED` was considered and declined as too rare to justify. Do not add
them without being asked. The pattern follows the system 12/24-hour setting (`HH:mm` or
`h:mm`, ASCII digits through `Locale.ROOT`).

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
which would destroy a park screen stacked there. The affinity is
`${applicationId}.park`: unlike `android:name`, `taskAffinity` does not expand a leading
dot, so `.park` was one literal string shared by the release and the debug build, which
install side by side.

The separate task is restored explicitly: while `ParkStore.isParked` is set,
`HomeActivity.onResume` re-launches the park screen. That one path covers a reboot,
which pinning does not survive, and a Home press on a device that refused pinning. The
park screen sets `isParked` itself in `onResume`, so no entry point carries a protocol,
and the Park lock tile finishes the app list as it launches so unlocking returns to Home.
Pinning is requested from `onResume` too, not `onCreate`: `startLockTask()` needs a
resumed activity, and the restore path enters there as well.

Being `singleInstance`, the screen receives later intents through `onNewIntent`, so set
mode is read from the current intent rather than cached; an abandoned set-PIN screen
would otherwise come back as set-PIN when the rider parks. Both system bars stay hidden
on the park screen whatever the navigation bar setting: a visible navigation bar beside
a lock screen is an invitation.

### Measured on the device
On an Android 16 device with gesture navigation, an app pinning itself is not asked to
confirm, so parking is one tap. The shade is blank and Home, Back and Recents are
inert. Android 14 confirmed every pin. AOSP routes an app's own `startLockTask()`
through the screen-pinning request on 14, 15 and 16 alike, so the silent pin on 16 may be
that device's own behaviour; the measurement is what the Android 15 gate rests on. The
7" device runs Android 14, so the park lock is not offered there until it receives
Android 16.

`lockTaskModeState` updates asynchronously and reads `NONE` straight after a successful
request; the settle window in `ParkLockRules.shouldRequestLockTask` and the delayed
`settleCheck` exist for that, and stop a redundant second request and toast.

### The unpin gesture is undone, not blocked
The system unpin gesture (hold Back + Recents) cannot be blocked, and an ordinary app
gets no unpin callback. While parked and resumed, a guard polls `lockTaskModeState`
every 300 ms and re-pins. On a correct PIN the guard is disarmed and `isParked` cleared
before `stopLockTask()`, or an in-flight run would re-pin what the PIN released. The
guard is disarmed in `onPause`, so a parked device with the screen off polls nothing.

The consequence was accepted deliberately: while parked, the PIN is the only way out
short of a computer. A forgotten PIN strands the device until its data is cleared
(`adb shell pm clear de.codevoid.motolauncher`) or the launcher is reinstalled.

### The park state is never backed up
Auto Backup and device transfer used to copy the whole `settings` file, the parked flag
and the PIN hash included, and Android restores a backup on every install. A backup
taken while parked (overnight, charging, on Wi-Fi) turned the recovery, a reinstall, into
a device that came back up parked behind the forgotten PIN, and a new device set up from
that backup started parked as well. The park state now has its own `park` file,
excluded from cloud backup and device transfer (`data_extraction_rules`, Android 12+) and
from full backup (`backup_rules`, Android 11 and lower). The rules can only match whole
files, hence the separate file. A restored device has no PIN and is not parked. The
park keys left in an older `settings` file are not cleaned up: nothing reads them, and
the project carries no migration code before 1.0.

### Pinning state is not shown
A status line under the keypad reported whether pinning took while the mechanism was
being proven. It was removed once pinning was confirmed: the screen is the same lock to
its user either way, and the line offered no action. The state is still read to stop
retrying on a device with screen pinning switched off.

### The guard gives up after three unanswered requests
Where screen pinning is switched off every request fails, and repeating one several
times a second buys nothing. The guard stops once three requests in a row have not taken
by their settle check, about two seconds; a success resets the count, and focus
returning to the screen starts it over. The first version stopped after a single failed
check, unless pinning had worked earlier on the same screen instance. A slow system
server at boot could cause that one failure on a device where pinning works, which left
the unpin gesture unanswered until the next focus change. Three trades that grace
against repeating a refused request.

### The keypad is touch-only
The keypad sits in a `TouchOnlyRow`, so the remote can never drive it.

### The PIN hash is hygiene, not security
The PIN is salted and hashed so it is not in a prefs file in plain text. Four digits
fall to a trivial search, and anyone who can read the file has adb, which defeats the
park lock outright.

### Offered only on Android 15+ and without a secure lock screen
Below Android 15 the system confirms every `startLockTask()`, so the guard would answer
an unpin with a dialog instead of a silent re-pin. A device with a secure lock screen
already has a better park lock one power-button press away. `ParkLockRules.shouldOfferParkLock` gates
the settings tile only; that is enough because the tile is the sole entry point and the
restore path fires only on a flag the park screen sets. The Android 15 boundary comes
from testing on 14 (confirmed every pin) and 16 (silent); 15 itself is untested and
assumed to behave like 16.

## Updates

### Releases and the `dev` pre-release are two channels
A release build (`1.0.x`) queries the latest release; a dev build (`dev-<sha>`) queries
the `dev` pre-release. The installed version name picks the channel, so there is no
setting. GitHub's latest release never resolves to a pre-release. CI deletes and
recreates `dev` on every push to `main`, so that channel is one stable URL and "keep only
the latest" needs no cleanup. The version comes from the APK filename
(`motoLauncher-<versionName>.apk`). Any published build that differs from the installed
one counts as an update. The check is user-triggered only: the device is mostly
offline.

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

A failure names its cause instead of showing the exception text: a 404 means nothing is
published right now, which happens for a few minutes while CI recreates `dev`; other
statuses show their code; no connection and no answer in time are named as such.

### The update flow runs in a ViewModel
`UpdateViewModel` runs the check and the download in `viewModelScope`, holds what it is
doing as a `StateFlow` (idle, checking, downloading with progress) and delivers each
outcome (current, available, install, failed) once, through a `Channel` the app list
collects while started. It used to run in the activity's `lifecycleScope`: a recreate
(theme toggle, rotation) cancelled it mid-flight and lost the result dialog or the
installer hand-off, while the download loop kept running and held the destroyed activity.
Now a recreate keeps the flow, and an outcome that arrives while the screen is stopped
waits for it. A step starts only while the flow is idle, so a tap during a check or a
download is ignored; the tile's own guard used to miss the download. Leaving the app list
for good clears the ViewModel and cancels the download, which checks for cancellation
after every read. The ViewModel takes the release lookup and the download as functions,
so a test can drive it without the network. The install prompt is still a plain dialog,
lost on a recreate.

## Distribution

### Two flavors: github and play
Google Play forbids an app updating itself outside Play, and `REQUEST_INSTALL_PACKAGES`
is a restricted permission there. The play flavor leaves out the update tile and the
cleanup of installed update APKs (`BuildConfig.SELF_UPDATE`). The github flavor's own
manifest adds `INTERNET`, `REQUEST_INSTALL_PACKAGES` and the `FileProvider`. Without
the updater the play build has no network access, so nothing can leave the device. The
update code stays in `main` behind the flag rather than in a github source set: it has
two call sites, and R8 drops the unreachable code from the play release. Both flavors
share the application ID. Whether a Play install and a GitHub install can update each
other depends on Play App Signing using the existing release key.

### Target API 36 on AGP 8.13
Play has required targetSdk 36 for new apps and updates since 31 August 2026. AGP 8.13
is the newest 8.x and compiles against API 36; AGP 9 would also force the built-in
Kotlin migration. CI runs JDK 21 because Robolectric needs it to run tests against SDK
36. The bytecode target stays 17.

### One version counter for both channels
`versionCode` is the number of commits reachable from `main`, for dev builds and
releases alike, so the newer build installs over the older whichever channel it came
from. This assumes `main`'s history is never rewritten. The Build run number it
replaced counted one workflow only; at the switch it was 125 against 185 commits, so
installed dev builds stayed upgradable.

### Releases are cut by hand
`Release` runs from `main` on demand and takes the next patch after the newest `v*` tag,
starting at 1.0.0. It builds both artifacts before pushing anything, then pushes the
release commit and tag in one atomic push, so a failed build or a `main` that moved
meanwhile leaves no tag behind. Re-running after a later step failed stops at the empty
`[Unreleased]` section instead of publishing an empty release; the Play bundle is kept
as an artifact for a manual upload. The release commit is pushed with `GITHUB_TOKEN`,
which triggers no workflow, so `Build` does not republish `dev` for it.

### Release notes come from the changelog
The `[Unreleased]` section becomes the version's section, the GitHub release text and
Play's "What's new". Play allows 500 characters of plain text, so the notes lose their
markdown and keep the leading whole entries that fit, followed by a link to the GitHub
release. The first entry that does not fit ends the list, so the notes never skip ahead.

### The Play upload needs a published app and a secret
The Play Developer API cannot create an app, so the first bundle is uploaded by hand from
the workflow artifact. The upload step runs only when `PLAY_SERVICE_ACCOUNT_JSON` is set.
Until the app has had a release published from the Console, the API accepts only `draft`
releases, hence the status input.

### The privacy policy is shown, not linked
Play requires a privacy policy linked in the Console and shown or linked inside the app.
The device is mostly offline, so the settings tile shows the text itself. `PRIVACY.md`
is the copy the Console links to; `PrivacyPolicyTest` fails when the in-app text is not
its body.

## Build and CI

### Three workflows
`Check` runs on every push to a branch other than `main`. `Build` runs on push to `main`
and republishes the `dev` pre-release. `Release` runs on demand and is the only producer
of a versioned release. For a long time `Build` was the whole pipeline, so a branch got
no signal before merge on a project that cannot be built locally.

### `Check` is one job with one Gradle invocation
Lint, unit tests and the debug build run in one `./gradlew --continue`, sharing
compilation and one daemon start-up; parallel jobs would each pay checkout, JDK setup and
compilation. Lint and tests run for `githubDebug` only: both flavors share every source
and test, so the play runs repeated them for about 45 seconds. `assemblePlayDebug` still
compiles `main` against play's `BuildConfig` and links its manifest. `--continue` reports
lint, test and compile failures from the same run. Release variants are left to `Build`
(github) and `Release` (both) because they need the signing secrets and roughly double
the build, so a green `Check` does not prove a minified build.

### `Build` lints the release variants
Plain `./gradlew lint` analyses only the default variant, which is the debug variant
`Check` has already linted on the same commit. `Build` runs `lintGithubRelease` and
`lintPlayRelease` instead, the variants that ship.

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
