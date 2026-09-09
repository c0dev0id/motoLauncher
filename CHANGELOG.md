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
- The Android status bar and navigation bar are now hidden app-wide so the full screen
  is available for the launcher's own UI.
