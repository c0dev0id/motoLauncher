# Features

- **Home grid.** 4×3 in landscape, 3×4 in portrait: eleven favourite slots plus a fixed
  All Apps tile. A slot holds an app, a link (label + URL, opened with `ACTION_VIEW`),
  or nothing; an empty "+" tile opens the picker for that slot.
- **Remote operation.** Dpad, Enter and Escape drive Home and the app list through the
  native focus engine. The remote can only launch: every configuration path is
  touch-only.
- **Tile menu.** A touch long press opens a tile-styled menu: App info / Uninstall /
  Remove on a Home app tile, Edit link / Remove on a link tile, App info / Uninstall /
  Hide or Unhide in the app list. Pick mode has none.
- **Hold Escape.** Launches the configured navigation app from Home and from the app
  list, timed from the key's own timestamps so it works on remotes that do not
  auto-repeat.
- **App list.** Every launchable app across all profiles, with a touch-only search
  filter. Hidden apps are left out, or shown dimmed when "Hidden apps" is set to
  Showing. The same screen serves as the slot picker (with None and Add link tiles) and
  the navigation-app picker (with None).
- **Settings mode.** A toggle in the app list header swaps the grid for settings tiles:
  theme, navigation app, update check (github flavor), park lock, cellular indicator,
  GPS speed and units, battery display, hidden apps, orientation, navigation bar,
  privacy policy. The search field filters them.
- **Orientation.** Landscape, portrait, their reverses, or Sensor (default), chosen
  from a picker. The grid and status bar reshape on rotation.
- **Status bar.** Time, optional GPS speed in km/h or mph, Wi-Fi, optional cellular,
  and battery as icon, text or both. Clock and battery refresh when the screen wakes or
  Home returns to view.
- **Park lock.** A four-digit PIN keypad in its own task that pins the screen, re-pins
  itself if unpinned, and survives a reboot. Offered on Android 15+ without a secure
  lock screen.
- **Self-update (github flavor).** A user-triggered check against the latest release
  for a release build, or the `dev` pre-release for a dev build, with download progress
  on the tile and a hand-off to the system installer.
- **Google Play build.** The play flavor has no updater and no network access; Play
  updates it.
- **Privacy policy.** Shown as text from the settings mode, readable offline.
- **Themes and sizing.** Dark (default) and light themes; glove sizing for the 7"
  device with a scaled-down phone variant.
