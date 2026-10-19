# Architecture

Blue nodes are primary components; the rest are secondary.

## High-level architecture

```mermaid
flowchart LR
    rider(["Rider<br/>gloves + handlebar remote"])

    subgraph launcher["motoLauncher"]
        direction TB
        subgraph screens["Screens"]
            home["HomeActivity<br/>favourites grid + StatusBarView"]
            list["AppListActivity<br/>app list, pickers, settings mode"]
            park["ParkActivity<br/>PIN keypad, own task"]
        end
        subgraph core["Process and data"]
            app["MotoLauncherApp<br/>app-list cache, package watch"]
            repo["AppRepository"]
            stores[("Preference stores")]
            update["UpdateChecker<br/>github flavor"]
        end
        screens --> core
    end

    subgraph android["Android"]
        la["LauncherApps"]
        sys["Connectivity, telephony,<br/>location, battery"]
        installer["Installer / uninstaller"]
        locktask["Lock task mode"]
    end

    nav["Navigation app"]
    gh["GitHub releases<br/>and dev pre-release"]
    play["Google Play"]
    ci["GitHub Actions"]

    rider --> screens
    home -. hold Escape .-> nav
    list -. hold Escape .-> nav
    home -- status bar --> sys
    park --> locktask
    repo <--> la
    repo -- uninstall --> installer
    update -- install --> installer
    update -- download --> gh
    ci -- publishes APK --> gh
    ci -- uploads bundle --> play
    play -- installs and updates --> launcher

    classDef primary fill:#2F6FED,stroke:#1E4FB0,color:#FFFFFF
    classDef external fill:#EEF1F5,stroke:#9AA5B4,color:#14181D
    class home,list,park,app primary
    class nav,gh,play,ci,rider external
```

## Layers

Each feature package holds its screens, views and stores. `MotoLauncherApp` and
`HomeActivity` stay in the root package.

```mermaid
flowchart TB
    subgraph features["Features"]
        direction LR
        home["home<br/>HomeActivity (root package), FavoritesStore,<br/>FavoritesCache, LinkDialog"] ~~~ applist["applist<br/>AppListActivity, AppTileAdapter,<br/>TileItem, HiddenAppsStore"] ~~~ settings["settings<br/>SettingsTiles, OptionsDialog,<br/>Theme, Orientation, NavBar stores"] ~~~ park["park<br/>ParkActivity, ParkStore"]
        statusbar["statusbar<br/>StatusBarView,<br/>Speed, Cellular, Battery stores"] ~~~ update["update<br/>UpdateChecker, UpdateFlow,<br/>DownloadProgress"] ~~~ navapp["navapp<br/>NavAppStore, launchNavApp"]
    end
    subgraph shared["Shared"]
        direction LR
        appc["MotoLauncherApp<br/>app-list cache, package watch"] ~~~ apps["apps<br/>AppRepository, AppEntry"] ~~~ ui["ui<br/>KeyInput, TouchOnlyRow, Immersive,<br/>TileActionsDialog, Portrait"]
    end
    subgraph platform["Android platform"]
        direction LR
        p1["LauncherApps<br/>UserManager"] ~~~ p2["SharedPreferences"] ~~~ p3["Connectivity, telephony,<br/>location, battery"] ~~~ p4["HttpURLConnection<br/>FileProvider<br/>github flavor"] ~~~ p5["Lock task mode"]
    end

    features --> shared --> platform
    features --> platform

    classDef primary fill:#2F6FED,stroke:#1E4FB0,color:#FFFFFF
    class home,applist,settings,park,statusbar,update,navapp primary
```

Features also use the platform directly: the status bar reads the system services, and
the park lock calls lock task mode.

## Persistent state

There is no database. State lives in three `SharedPreferences` files, shown here as
entities.

```mermaid
erDiagram
    favorites {
        string slot_N "N = 0..10: flattened ComponentName, or the marker link:"
        string slot_N_label "link slots only"
        string slot_N_url "link slots only"
    }
    hidden_apps {
        stringset hidden "package names"
        boolean show_hidden "default false"
    }
    settings {
        boolean dark_theme "default true"
        string nav_app "flattened ComponentName"
        int orientation "default FULL_SENSOR"
        boolean show_nav_bar "default false"
        boolean cellular_enabled "default false"
        boolean speed_enabled "default false"
        boolean speed_metric "default true"
        string battery_display "BOTH, ICON or TEXT"
        boolean parked "default false"
        string park_pin_salt
        string park_pin_hash
    }
    launcher_activity {
        string component "package/class, resolved through LauncherApps"
    }
    favorites }o--o{ launcher_activity : "app slots launch"
    settings }o--o| launcher_activity : "nav_app launches"
    hidden_apps }o--o{ launcher_activity : "hides by package"
```
