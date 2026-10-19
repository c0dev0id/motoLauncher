# Overview

## Problem

Motorcycle navigation devices are Android tablets mounted on the handlebar. The rider
operates them with gloves on, or with a handlebar remote that sends only dpad, Enter and
Escape. Stock Android launchers assume a bare finger: they rely on swipes, use small
targets, and expose settings and menus that a remote can enter but not leave. A wrong
press on the road can strand the rider on a screen that needs touch to get out of.

## Solution

motoLauncher is a home app built around those two inputs.

- **Big, fixed tiles.** Home is a grid of twelve large tiles that never scrolls. No
  primary flow needs a swipe.
- **A remote that can only launch.** The remote moves a focus highlight with the
  native View focus engine and launches with Enter. Everything that needs touch to leave
  is unreachable from it. Holding Escape starts the navigation app from anywhere in the
  launcher.
- **Configuration by touch, where it is used.** Empty tiles open a picker in place;
  device settings live in a mode of the app list. There is no settings screen.
- **Riding information at a glance.** A status bar shows time, GPS speed and signal and
  battery state, refreshed the moment the device wakes.
- **Short stops.** A park lock pins the screen behind a PIN without involving the
  system lock screen, which the device deliberately runs without.
- **Offline by default.** The device is mostly offline. The GitHub build checks for
  updates only on request; the Google Play build has no network access at all and is
  updated by Play.

The navigation app itself is a separate app. The launcher starts it; it does not
replace it.
