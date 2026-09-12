# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- Wi-Fi and cellular icons now show zero bars instead of disappearing when signal is weak or coverage is lost. The icon is hidden only when the radio is off (Wi-Fi) or the feature is disabled in app settings (cellular) — not when signal drops to zero.
- Activity transitions (Home ↔ All Apps, pick-mode open/close) are now instant — no slide animation.
- App list is now cached at the process level. Navigating back and forth between Home and All Apps no longer re-decodes all app icons on every visit — the list loads once and stays loaded until an app is installed or removed.
- Home screen favorite icons are now cached across resumes. Returning from the navigation app no longer triggers Binder IPC and icon decoding on the main thread for each favorite slot — the resolved entries are reused until a package change or slot reassignment invalidates them.
- GPS speed listener is now paused while the home screen window is hidden (navigation app in the foreground) and resumed when the home screen comes back. Previously the 1 Hz location poll kept running in the background, consuming CPU that the navigation app needed.
- GPS speed listener is also paused when the screen turns off and resumed when it comes back on.

### Added
- **Download progress in settings tile.** While an update is downloading, the "Check for updates" tile subtitle shows percentage and speed (e.g. `45% · 2.2 MB/s`), updating every 500 ms.
- **Navigation bar setting.** Settings tile toggles between Hidden (default) and Visible. The home grid and app list automatically make space for the bar when shown.
- **Battery display setting.** Settings tile cycles between Icon + text (default),
  Icon only, and Text only. The status bar updates immediately on change.

- **Phone / small-screen support.** All text sizes, icon sizes, padding, and tile heights
  now scale down on phones (smallest-width < 600dp) via `values/dimens.xml` qualifiers,
  while tablets (≥ 600dp) keep the original sizing unchanged.

- **Hide apps from the All Apps list.** Long-pressing any app now shows a "Hide" option
  in the tile-actions menu; long-pressing a hidden app shows "Unhide" instead.
  A "Hidden apps" settings tile (in the All Apps settings mode) toggles between
  Hidden (default) and Showing (dimmed), letting you review and recover hidden apps
  without cluttering the list during normal use.


- Glove- and remote-friendly home launcher for the DMD2 navigation device.
- Home screen with a fixed 4×3 grid of large tiles: 11 configurable favorite slots
  plus a dedicated "All Apps" tile. Fully operable by the handlebar remote
  (dpad + Enter + Escape) via the native focus system — no swiping.
- All-apps screen: scrollable, remote-navigable grid of every installed app, with a
  touch search field to filter long lists.
- Short tap / Enter launches an app; long press opens its system app-info screen.
- Favorites are configured in place: an empty "+" tile, or "Reassign app" from a
  favourite's long-press menu, opens the app picker for that slot.
- Dark/Light theme toggle accessible from the All Apps settings mode (defaults to dark);
  the choice is saved and restored on restart.
- User-triggered update check from the All Apps settings mode, with download and hand-off
  to the system installer (no automatic polling — the device is mostly offline).
- **Settings mode** in the All Apps screen: tapping the "Settings" toggle switches the
  RecyclerView from the app grid to a settings tile grid (theme, update check, cellular
  permission), hiding the search field and showing settings tiles instead. Tapping
  "Apps" or pressing Escape returns to app browse mode. Settings mode state survives the
  activity recreation triggered by the theme tile.
- GitHub Actions build: parallel lint and unit tests, signed release APK, and a
  self-replacing `dev` pre-release.
- **GPS Speed display** in the home screen status bar, center-aligned between the clock
  and the status icons. Defaults to disabled. Enabled from the settings mode in the
  All Apps screen: tapping the tile requests `ACCESS_FINE_LOCATION` on first enable;
  if granted the tile flips to On immediately.
- **Cellular indicator** in the home screen status bar is now a toggle (defaults to
  disabled). Enabling it requests `READ_PHONE_STATE` on the spot; the tile reflects
  the outcome without requiring a separate page load.
- **Units setting** (Metric / Imperial) in the All Apps settings mode, shown as the
  current unit (`km/h` or `mph`); controls the unit displayed by the GPS speed widget.
- Settings tiles now update their subtitle immediately when tapped.
- Custom home status bar showing the current time on the left and Wi-Fi, cellular, and
  battery indicators on the right, aligned with the grid margins.
- Visible back button on the app list screen for touch users.
- Holding Escape on the remote launches the app in the first favourite slot (the top-left
  home tile), from the home screen and from the app list. An empty slot, or one whose app
  has been uninstalled, does nothing.

### Changed
- The home status bar hides the Wi-Fi indicator entirely when no Wi-Fi network is
  connected. An empty meter previously stood for both "no Wi-Fi at all" and "connected,
  signal gone".
- The cellular indicator follows the same rule: it is hidden unless a mobile-data network
  exists, so switching mobile data off (or flight mode, no SIM, or no coverage) removes it
  from the bar instead of leaving an empty meter behind.
- A short Escape closes the app list on key release rather than on key press, so holding
  the key can be told apart from tapping it.
- The "All Apps / Config" home tile is now labelled "All Apps"; it no longer leads to
  a configuration screen.
- Theme toggle, update check, and the cellular-permission ask moved from dedicated header
  buttons to tile-sized controls in the All Apps settings mode. The separate Configure
  Favorites screen is gone, along with the "All Apps" long-press that opened it.
- Long-pressing an assigned favourite on the home screen now opens a tile-styled menu
  with three large buttons instead of jumping straight to the system app-info screen:
  **App info** opens the system screen as before, **Uninstall** hands the app to the
  system uninstaller, and **Reassign app** opens the app picker for that slot and
  stores the choice. Back or Escape closes the menu.
- The app picker, when opened for a slot, starts with a **None** tile that clears the
  slot.
- Long-pressing an app in the All Apps list opens the same menu with **App info** and
  **Uninstall** only.
- Tapping an empty "+" tile on the home screen now opens the app picker for that slot
  directly instead of the Configure Favorites screen. That screen is still reachable by
  long-pressing "All Apps" for the theme, update, and cellular-indicator controls.
- The Android status bar and navigation bar are now hidden app-wide so the full screen
  is available for the launcher's own UI.
- The custom home status bar now uses the Michroma display font, sits at larger sizes
  (time 28sp, battery 24sp), and aligns its text with the favourites grid columns.
  Michroma is bundled under the SIL Open Font License 1.1 — see `MICHROMA-LICENSE.txt`.

### Fixed
- Uninstalling an app now updates the launcher straight away: it is gone from the All Apps
  list when that screen comes back, and any home slot holding it is cleared instead of
  keeping a component that can never resolve again.
- The tile menu's "Uninstall" now actually starts the system uninstaller. The launcher was
  missing both the `REQUEST_DELETE_PACKAGES` permission and the package-visibility entry
  the hand-off needs, and the request was being dropped without any error.
- The Wi-Fi indicator no longer overstates the signal. The platform rates a link from 0 to
  its reported maximum *inclusive*, and the launcher treated the top rating as out of
  range: a 3-of-4 signal drew full bars and the three-bar state was never shown at all.
- Unlit bars in the Wi-Fi and cellular indicators are now clearly darker than lit ones.
  They were so close in the dark theme that full signal and no signal looked almost
  identical at a glance.
- GitHub Actions `lint`, `test`, and release builds no longer fail during resource
  linking because the tile-actions dialog row style now opts out of Android's implicit
  dotted-name parent lookup instead of inheriting from a non-existent
  `Widget.MotoLauncher` base style.
- The installer file of an update is deleted when the launcher starts after installing
  it, and any older download is removed when a new one begins, so update APKs no longer
  accumulate in the app cache.
- Returning to the home screen no longer moves the remote's focus back to the top-left
  tile; it stays on the tile the app was launched from.
- Leaving the All Apps screen while an update check or download was in flight (theme
  toggle, Home button, low memory) could crash the launcher when the interrupted flow
  tried to show its result dialog on a screen that no longer existed.
- Home grid no longer shows the bottom row cut off for the first frame on cold start.
- The home status bar's battery icon now shows a fill level that tracks the percentage
  (five buckets) and gains a lightning-bolt overlay while a charger is connected.
- The dpad can no longer land on the app-list search field or the back / theme / update
  buttons in the header rows — those controls are touch-only. Touch focus and text entry
  in the search field still work as before.
- Holding Enter (or the handlebar remote's centre button) on a home tile or app-list
  cell no longer triggers a long-press, so keyboard users can't accidentally reach the
  app-info screen or the settings picker from which the remote has no way back.
  Long-pressing a tile with a finger still works.
- Escape on the home screen no longer opens Settings. Configuration is a touch-only
  workflow reached by tapping the empty "+" tiles on first-time setup, or by
  long-pressing "All Apps" once every slot is filled. Escape from All Apps and Settings
  still returns to the home screen.
