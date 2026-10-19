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
./gradlew testGithubDebugUnitTest --tests "de.codevoid.motolauncher.apps.AppRepositoryTest"
./gradlew testGithubDebugUnitTest --tests "de.codevoid.motolauncher.apps.AppRepositoryTest.sortsCaseInsensitively"
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
  The tile opens a picker (`showOptionsDialog`), never a cycle: tapping through five values
  would apply every one on the way, rotating the screen up to four times per change. Rotation
  under Sensor reshapes the grid only because no activity lists `orientation` in its
  `configChanges` — the system recreates them and `isPortrait` is read afresh. Adding
  `orientation` there would freeze the layout in whatever shape it started in. The device
  is a 7" landscape screen; portrait exists for phones. Every activity calls
  `setRequestedOrientation` in `onCreate` and `onResume`; grid shape
  follows `Context.isPortrait` (`ui/Portrait.kt`): Home is 4×3 landscape / 3×4
  portrait, the app list 5 / 4 columns, and the status bar has a `layout-port` variant.
  The status bar is always hidden (immersive mode); the navigation bar follows
  `NavBarStore` (hidden by default).
- **Home and the app list must be operable by remote** using only the keys the remote
  emits: dpad-left/right/up/down, Enter, Escape. Traversal is Android's native View
  focus engine, not custom key handling — keep it that way.
- **The remote may only ever launch apps.** Everything that needs touch to get back out
  of (app info, the search field, the back/settings buttons, every dialog) is deliberately
  unreachable from the remote: touch-only controls sit in a `TouchOnlyRow`, key-driven
  long-press is blocked on every tile, and a short Escape on Home does nothing. Do not
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
  release build compares itself to the latest GitHub release, a `dev-` build to the `dev`
  pre-release, and offers the install if they differ. Must be **user-triggered** (the
  device is mostly offline — never auto-poll the network).
- **Privacy policy** shown as text from the settings mode (Play requires it in-app);
  `privacy_policy_text` is the body of `PRIVACY.md`, and `PrivacyPolicyTest` compares them.

## Architecture

Kotlin, Android classic Views (no Compose), AppCompat + Material3, view binding.
Coroutines for the app enumeration and update I/O only. No networking library:
`HttpURLConnection` + `org.json`. Versions and SDK levels are in `.github/stackinfo.md`,
the component and layer diagrams in `.github/architecture.md`, and the reasons behind
everything below in `.github/decisions.md`.

Sources are organised by feature under `de.codevoid.motolauncher`. New code goes into
the package of the feature it serves; `apps/` and `ui/` hold only what several features
share. Tests mirror the packages.

- **Root** — `MotoLauncherApp` re-applies the theme, deletes the installed update APK
  (github flavor), and owns the single `LauncherApps.Callback`: a removal clears the
  favourite slots holding the package (`clearSlotsForPackage`), every event bumps
  `packageGeneration`. `getApps()` is the process-level app-list `Deferred`, reissued only
  when `packageGeneration` moved; callers compare instances. `HomeActivity`
  (`category.HOME`, `singleTask`) stays in the root package because Android holds it as the
  default home app — do not move or rename it.
- **`home/`** — Home's 12 tiles (11 slots + All Apps) are inflated once by `populateGrid()`
  into weighted `LinearLayout` rows, not a RecyclerView, and rebound by `buildGrid()` in
  `onResume`. Remote focus is seeded once, in `onCreate`. `bindTile` must clear the
  long-click listener and `isLongClickable` on a tile without a long press. `FavoritesCache`
  (a `ViewModel`) resolves only the app slots and reloads when the slots or
  `packageGeneration` change. `FavoritesStore` (file `favorites`, `SLOT_COUNT` 11):
  `slot_<i>` is a flattened `ComponentName` or the `link:` marker with `slot_<i>_label` /
  `slot_<i>_url`; each setter removes the other representation's keys. While
  `ParkStore.isParked` is set, `HomeActivity.onResume` re-launches the park screen.
- **`applist/`** — `AppListActivity` browses, picks a slot (`pickIntent`: None and Add link
  tiles first) or picks the navigation app (`navAppPickerIntent`: None first). Pickers
  write the store and finish; callers rebuild in `onResume`. `AppListViewModel` holds
  `isSettingsMode` and the app-list `Deferred` (`reloadIfStale`). `AppTileAdapter` resets
  icon and subtitle visibility and `isLongClickable` on every bind; `updateItem` patches
  the first matching tile. `HiddenAppsStore` (file `hidden_apps`) returns a copy of its set.
- **`settings/`** — `SettingsTiles` builds settings mode as a list of named tiles, rebuilt
  on every render. Construct it in `onCreate`: it registers the permission requests, and
  a permission toggle turns on only on grant. `UpdateState` keeps the update check across
  the theme toggle's recreate; download progress patches the update tile by its label, only
  while settings mode shows. A multi-valued setting whose intermediate values are
  disruptive opens `showOptionsDialog` instead of cycling. `ThemeStore`, `OrientationStore`
  and `NavBarStore` live here.
- **`park/`** — `ParkActivity` runs in its own task (`singleInstance` + `taskAffinity`),
  sets `isParked` itself in `onResume`, pins with `startLockTask()` and re-pins every 300 ms
  while parked. On a correct PIN, disarm the guard and clear `isParked` *before*
  `stopLockTask()`. `isSettingPin` is read from the current intent, never cached. The pure
  rules `shouldRequestLockTask` and `shouldOfferParkLock` (Android 15+, no secure lock
  screen) are unit-tested. The keypad sits in a `TouchOnlyRow`. While parked, the PIN is
  the only way out. `ParkStore` holds the flag and the salted PIN hash.
- **`statusbar/`** — `StatusBarView` subscribes in `onAttachedToWindow` and releases in
  `onDetachedFromWindow`; Home wires nothing. A new status-bar setting is a public `KEY_*`
  constant plus a branch in `prefsListener`. Wi-Fi visibility follows the radio and the
  bars follow RSSI through the pure `wifiIconLevel` (the rating range is inclusive).
  Cellular needs the setting, API 31+ and `READ_PHONE_STATE`. GPS speed is the one
  subscription paused while Home is hidden or the screen is off. `refreshOnResume`
  re-reads the clock and the battery from both resume edges; do not add the time-change
  broadcasts. `SpeedStore`, `CellularStore` and `BatteryStore` live here.
- **`update/`** (github flavor, behind `BuildConfig.SELF_UPDATE`) — `UpdateChecker`:
  `releaseApiUrl` picks the `dev` tag or `releases/latest` by the installed version name,
  `parseRelease` takes the first APK and its version from the file name, `isNewer` is plain
  inequality; the APK goes to `cacheDir/updates/` and to the installer through
  `FileProvider`. `runUpdateFlow` shows each outcome as a dialog and rethrows
  `CancellationException`. `DownloadProgress` builds "45% · 2.2 MB/s" by concatenation —
  a `%` in a format template crashed once.
- **`navapp/`** — `NavAppStore` and `launchNavApp()`, which goes through
  `AppRepository.launchIfInstalled`.
- **`apps/`** — `AppRepository` wraps `LauncherApps` across all profiles: `loadApps`,
  `loadByComponents` (Home never enumerates every app), `launch`, `launchIfInstalled`,
  `openInfo`, and `requestUninstall` (`ACTION_DELETE`, which needs both
  `REQUEST_DELETE_PACKAGES` and its `<queries>` entry). `sortApps` / `filterApps` are pure.
- **`ui/`** — `KeyInput.kt`: `blockKeyLongPress()` and `EscapeKeys`, which decides short
  vs. held on ACTION_UP from the event's own timestamps — never reintroduce
  `onKeyLongPress`; wire both `onKeyDown` and `onKeyUp`. `TouchOnlyRow` hides its children
  from dpad traversal. `TileActionsDialog.kt`: the tile and link menus, plain `Dialog`s on
  `Theme.MotoLauncher.Dialog`. `Immersive.kt`: `enableImmersiveMode` (re-applied on focus
  gain), `fitContentToSystemBars` (once per activity), `Dialog.showImmersive()` (mandatory
  for every dialog) and `noTransition()`. `Portrait.kt`: `isPortrait`.

## Resources & styling

- The palette lives in `values/colors.xml` (light) with dark overrides in
  `values-night/colors.xml`; a colour that is the same in both modes is defined once, in
  the light file. Layouts reference tokens (`background`, `surface`, `tile_default`,
  `tile_focused`, `tile_pressed`, `on_surface`, `on_surface_muted`, `signal_track`), never
  literals — the theme toggle works purely by resource qualifier, with no colour logic in
  code. Same for system-bar icon polarity: `@bool/light_system_bars`.
- **A dotted style name that is meant to be a root must set `parent=""`.** AAPT reads
  `Widget.MotoLauncher.DialogAction` as inheriting from a `Widget.MotoLauncher` style that
  doesn't exist and fails resource linking — a CI-only failure, so it costs a full round
  trip. `Widget.MotoLauncher.HeaderButton` (parent `Widget.Material3.Button`) and
  `Widget.MotoLauncher.DialogAction` (`parent=""`) are the two shapes in use.
- Every size is a `@dimen` with a phone default and a `sw600dp` override; add new sizes
  to both files.
- The unlit bars of the signal meters use `signal_track`, not `on_surface_muted` — that
  token is secondary *text* and is far too close to `on_surface` to separate a lit bar
  from an unlit one. Keep the two roles apart.

The manifest scopes package visibility to `MAIN`/`LAUNCHER` `<queries>` (plus the
`ACTION_DELETE` entry) rather than requesting `QUERY_ALL_PACKAGES` — the
launcher-appropriate approach. Keep it that way. Permissions in use: `INTERNET` and
`REQUEST_INSTALL_PACKAGES` (github flavor only), `REQUEST_DELETE_PACKAGES`,
`ACCESS_WIFI_STATE`,
`ACCESS_NETWORK_STATE`, and the runtime `READ_PHONE_STATE` (cellular bars) and
`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` (GPS speed), both optional and asked
for from their settings tile.

## Build & CI

**Do not attempt to build locally.** Android Studio / AGP are unavailable on this
platform and the firewall blocks AGP — do not work around this. All builds run in CI.
Correctness depends on careful API use and reading before writing.

Three workflows. **`Check`** (`.github/workflows/check.yml`) is the pre-merge gate: every
push to a branch other than `main`, plus `workflow_dispatch`. It is a
single job: the release script's unit tests, then one Gradle invocation (see *Commands*)
that lints and tests `githubDebug` — both flavors share every source and test — and
assembles the debug variant of both flavors, sharing compilation and one daemon start-up. The debug builds are unminified, unshrunk and debug-signed, and are
uploaded as the `app-debug` artifact — with a `.debug` `applicationIdSuffix`, so they
install alongside the release build and a branch can be tested on the device without
disturbing the launcher in daily use. `--continue` means one run reports lint, test and compile failures together.
There is deliberately **no `pull_request` trigger**: a check run attaches to the head
commit, so the push run's result already shows on a PR, and the event only added a second
run when a PR was opened onto an already-built branch. The consequence is that opening a
PR triggers nothing — the newest result is whatever the last push produced — and that pull
requests from forks would go unchecked. The concurrency group (`check-<ref_name>`,
`cancel-in-progress`) now only supersedes an older run when you push twice quickly.

**`Build`** (`.github/workflows/build.yml`) runs **only on push to `main`** and
republishes the `dev` pre-release. Three parallel jobs plus a follow-up release step:

- `./gradlew lintGithubRelease lintPlayRelease` — the variants that ship; plain `lint`
  covers only the default debug variant, which `Check` already lints.
- `./gradlew testGithubDebugUnitTest` — JUnit4 + Robolectric JVM
  unit tests in `app/src/test/kotlin` (`isIncludeAndroidResources = true`, so real resources and view
  inflation work; see *Commands* for running a single class or test).
  Robolectric runs them against SDK 36, which needs JDK 21 — hence JDK 21 in CI.
  Fifteen classes, and the shape they set: `AppRepositoryTest` (the pure `sortApps` /
  `filterApps`), `FavoritesCacheTest` (one load per slot list and package generation),
  `FavoritesStoreTest` (app and link slot round-trips against real
  `SharedPreferences`, the two representations cleaning each other up, and
  `clearSlotsForPackage` matching whole package names and leaving link slots alone),
  `UpdateCheckerTest` (`parseRelease` / `isNewer` / `releaseApiUrl` /
  `deleteInstalledUpdate` over JSON fixtures and temp files), `ImmersiveTest` (content
  padding follows dispatched bar and cutout insets), `PrivacyPolicyTest` (the in-app text is
  the body of `PRIVACY.md`, read from the module's parent directory), `AppTileAdapterTest`
  (`updateItem` patches only the matching tile and ignores a missing one),
  `TileActionsDialogTest` (inflates the dialog through the
  handle `showTileActionsDialog` returns: row order, optional rows hidden, one callback per
  row, dismissal), `EscapeKeysTest` (short vs. held Escape, dispatched through a real
  `KeyEvent.DispatcherState` so the framework's tracking rules are exercised rather than
  assumed), `StatusBarViewTest` (`wifiIconLevel` across platform rating ranges, and that
  `refreshOnResume` re-reads the clock and the battery when the bar returns to view but
  touches neither when it leaves), `ParkStoreTest`
  (PIN round-trips against real `SharedPreferences`, the parked flag read back through a
  second instance — the path a reboot takes — and that the PIN is never written in the
  clear), `ParkLockTaskGuardTest` (the pure `shouldRequestLockTask` rule, including the
  settle window that stops a redundant second pin request), `ParkLockAvailabilityTest`
  (the pure `shouldOfferParkLock` rule, with the API boundary written as numbers so it is
  pinned independently of the constant), `OptionsDialogTest` (rows
  in caller order, one index reported per pick, dismissal, and that the selected row is
  drawn differently) and `DownloadProgressTest` (the throttle, the rate since the last
  report, and the text without a known size, against a fake clock).
  Write new behaviour so it lands in that surface — a pure function, a store, or
  something a Robolectric activity can reach. No device is ever available to check it.
  The job caches `~/.m2/repository/org/robolectric` separately: Robolectric fetches its
  ~100 MB `android-all` jar at test runtime with its own resolver, outside the Gradle
  cache `setup-gradle` restores. The cache key hashes `app/build.gradle.kts`, so any
  edit to that file costs one re-download.
- `./gradlew assembleGithubRelease -PappVersionName=dev-<sha>
  -PappVersionCode=<commit count>` — minified + shrunk, signed via the `SIGNING_KEYSTORE_*` / `SIGNING_KEY_*` env vars
  (already configured on the repo; do not add or commit signing material). Without
  those properties the version is `dev-local` / `1`. The checkout is full-depth because
  the versionCode is `git rev-list --count HEAD`, shared with `Release`.
- `draft-release` deletes and recreates the `dev` pre-release with the fresh APK, so
  the update endpoint stays a single stable URL and "keep only the latest" needs no
  cleanup.

**`Release`** (`.github/workflows/release.yml`) is manual, from `main` only. It picks the
next `1.0.x` after the newest `v*` tag (1.0.0 first), runs `.github/scripts/changelog.py
release` to move `[Unreleased]` under that version, commits, builds the github APK and
the play bundle, and only then pushes commit and tag atomically. It publishes the APK as
the GitHub release `v1.0.x` with the changelog section as text, keeps the bundle and
mapping as an artifact, and — when the `PLAY_SERVICE_ACCOUNT_JSON` secret exists —
uploads the bundle to the chosen Play track with `changelog.py play-notes` as "What's
new" (plain text, whole entries within 500 characters, plus the release link). So
`[Unreleased]` must hold the release's entries before it runs, or it refuses.

No workflow can be run locally, and a green `Check` is the first real signal a branch
gets — it does not cover the release builds, so a minification or shrinking failure
surfaces only in `Build` (github) or `Release` (play).

Read CI results through the GitHub MCP tools (`actions_list`, `actions_get`,
`get_job_logs`), fetching only the failed steps' logs — the full logs are large. There
is no `.gh_token` in the repo.
