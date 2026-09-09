# motoLauncher

A home screen for Android-based motorcycle navigation devices, designed to be
operated with gloves and a handlebar remote while riding.

## Purpose

Riding gloves make precise touch and swiping impractical, and much of the
interaction happens through the bike's remote rather than the screen.
motoLauncher replaces the device's home screen with a simple, large-tiled
layout built for exactly those conditions.

## Features

- **Favorites grid.** A fixed grid of large tiles for the apps you use most.
  Tap a tile to launch its app. Touch long-press opens a large action menu with
  App info, Uninstall, and Reassign app.
- **All apps.** A dedicated tile opens the full list of installed apps, with a
  touch-only search field plus touch-only theme, update, and cellular-indicator
  controls in the header.
- **Remote friendly.** The home screen and app list can be navigated entirely
  with the remote's direction pad, Enter, and Escape. Only app launching is
  remote-accessible; touch-only controls are intentionally excluded from dpad
  focus. There are no swipe gestures anywhere in normal use.
- **Configuration.** Assign, change, or clear the apps in your favorite tiles
  in place. Empty "+" tiles and the Reassign action open the picker directly for
  that slot; there is no separate settings screen.
- **Updates.** Check for and install a newer version on demand. Because the
  device is usually offline, updates are only ever checked when you ask.
- **Status bar.** The home screen includes a launcher-owned top bar with time,
  Wi-Fi, cellular, and battery indicators.

## Layout

motoLauncher is built for landscape orientation on a 1920x1080, 7-inch screen.
It runs in immersive mode so the launcher owns the full screen.

## Screenshots

### Home

![Home screen](screenshots/main.png)

### Home action menu

![Home tile action menu](screenshots/main-popup.png)

### All apps

![All apps screen](screenshots/all-apps.png)

### Light theme

![Home screen in light theme](screenshots/main-light.png)
