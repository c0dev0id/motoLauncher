# User guide

Configuration is done by touch. The remote only moves the highlight and launches apps,
so it can never leave you on a screen you cannot get out of with gloves on.

## Fill the home screen

Home has eleven slots and an **All Apps** tile. An empty slot shows a **+**.

1. Tap a **+** tile. The app picker opens.
2. Tap an app to put it in the slot.
   - **Add link** puts a link there instead. Enter a name and a URL. Any link Android
     can open works, including web addresses and app-specific links.
   - **None** leaves the slot empty.

To change a slot, long press it and choose **Remove**, then tap the **+** that takes its
place. A link tile offers **Edit link** to change its name or URL.

Uninstalling an app also clears any slot that held it.

## Ride with the remote

| Key | Home | All Apps |
|---|---|---|
| Dpad | Move the highlight | Move the highlight |
| Enter | Launch the highlighted app | Launch the highlighted app |
| Escape | — | Leave settings mode, then close All Apps |
| Hold Escape | Start the navigation app | Start the navigation app |

A hold is about half a second or longer. Coming back from an app, the highlight is still on
the tile you launched.

The remote cannot open menus, the search field or settings. That is deliberate.

## Set the navigation app

1. Open **All Apps** and tap **Settings**.
2. Tap **Navigation app** and pick the app. **None** clears it.

From then on, holding Escape starts it. With no navigation app set, or after it has
been uninstalled, holding Escape does nothing.

## Find, hide and remove apps

- **Search:** tap the search field in All Apps and type. The list narrows as you type.
- **Hide:** long press an app and choose **Hide**. It leaves All Apps and the pickers.
- **See hidden apps:** in Settings, set **Hidden apps** to **Showing**. Hidden apps
  appear dimmed; long press one and choose **Unhide** to bring it back.
- **App info** and **Uninstall** are in the same long-press menu, on Home and in All
  Apps. Uninstall hands over to Android, which asks for confirmation.

Back or Escape closes a long-press menu without choosing anything.

## Adjust the screen and status bar

All of these are in **All Apps → Settings**. Tap a setting to change it; tap
**Settings** again to return to the app list. The search field filters the settings
too.

| Setting | Options |
|---|---|
| Theme | Dark (default), Light |
| Orientation | Sensor (default), Landscape, Portrait, Reverse landscape, Reverse portrait |
| Navigation bar | Hidden (default), Visible |
| Battery | Icon + text (default), Icon only, Text only |
| Cellular indicator | Off (default), On |
| GPS Speed | Off (default), On |
| Units | km/h (default), mph |

**Orientation** opens a list and applies only the one you pick. **Sensor** follows the
device and rotates on its own, whatever Android's auto-rotate switch says.

**GPS Speed** asks for location permission the first time you turn it on, and
switches on only if you allow it. GPS Speed and Units appear only on devices with GPS.

The status bar shows the time on the left and Wi-Fi, cellular and battery on the right.
GPS speed sits between them in landscape and on its own row in portrait. A Wi-Fi icon
with no bars means Wi-Fi is on but not connected. A lightning bolt in the battery means
a charger is connected.

## Lock the screen at a stop

The park lock covers the screen with a PIN keypad and pins it, so Recents and the
notification shade stay closed. The **Park lock** setting appears on Android 15 or
newer, and only while the device has no screen lock of its own.

1. **Set a PIN:** tap **Park lock** the first time. Enter four digits, then repeat them.
2. **Lock:** tap **Park lock**. The keypad appears at once.
3. **Unlock:** enter the PIN. You return to Home.
4. **Change the PIN:** long press **Park lock**.

**C** clears the entry, **⌫** deletes the last digit. The lock stays on through Home
presses and reboots. The remote cannot operate the keypad.

> **While locked, the PIN is the only way back in.** If you forget it, the launcher has
> to be reinstalled from a computer.

## Update the launcher

The Google Play version is updated by Google Play. The version from the GitHub release
page never checks for updates on its own. With a network connection:

1. In Settings, tap **Check for updates**. The tile shows the installed version.
2. If a newer build exists, choose **Download & install**. The tile shows progress, for
   example `45% · 2.2 MB/s`. A release offers the next release; a development build
   offers the newest development build.
3. Android's installer opens. Confirm the update there.

The downloaded file is removed once the new version is running.

## Read the privacy policy

In **All Apps → Settings**, tap **Privacy policy**. It needs no connection.
