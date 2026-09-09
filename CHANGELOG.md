# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Glove- and remote-friendly home launcher for the DMD2 navigation device.
- Home screen with a fixed 4×3 grid of large tiles: 11 configurable favorite slots
  plus a dedicated "All Apps" tile. Fully operable by the handlebar remote
  (dpad + Enter + Escape) via the native focus system — no swiping.
- All-apps screen: scrollable, remote-navigable grid of every installed app, with a
  touch search field to filter long lists.
- Short tap / Enter launches an app; long press opens its system app-info screen.
- Settings screen to assign favorites: tap a slot to pick or change its app, long-press
  to clear it. Reached from home via Escape (remote) or a long-press on the "All Apps"
  tile (touch), so it stays accessible even when every slot is filled.
- Dark/Light theme toggle on the settings screen (defaults to dark); the choice is saved
  and restored on restart.
- User-triggered update check against the repository's `dev` nightly pre-release, with
  download and hand-off to the system installer (no automatic polling — the device is
  mostly offline).
- GitHub Actions build: parallel lint and unit tests, signed release APK, and a
  self-replacing `dev` pre-release.
- Custom home status bar showing the current time on the left and Wi-Fi, cellular, and
  battery indicators on the right, aligned with the grid margins.
- Visible back button on the app list and settings screens for touch users.

### Changed
- The theme toggle and "Check for updates" button now live in the All Apps header, to
  the right of a shorter search field, and are hidden while picking an app for a slot.
  Update results (latest build, update available, failure) appear as dialogs instead of
  a status line. The Configure Favorites screen keeps only the slot grid and the
  cellular-indicator permission button.
- Long-pressing an assigned favourite on the home screen now opens a tile-styled menu
  with two large buttons instead of jumping straight to the system app-info screen:
  **Reassign app** opens the app picker for that slot and stores the choice, **App info**
  opens the system screen as before. Back or Escape closes the menu.
- Tapping an empty "+" tile on the home screen now opens the app picker for that slot
  directly instead of the Configure Favorites screen. That screen is still reachable by
  long-pressing "All Apps" for the theme, update, and cellular-indicator controls.
- The Android status bar and navigation bar are now hidden app-wide so the full screen
  is available for the launcher's own UI.
- The custom home status bar now uses the Michroma display font, sits at larger sizes
  (time 28sp, battery 24sp), and aligns its text with the favourites grid columns.
  Michroma is bundled under the SIL Open Font License 1.1 — see `MICHROMA-LICENSE.txt`.

### Fixed
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
