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

```mermaid
flowchart TB
    subgraph screens["Screens"]
        direction LR
        home["HomeActivity"] ~~~ list["AppListActivity"] ~~~ park["ParkActivity"]
    end
    subgraph widgets["UI components"]
        direction LR
        statusbar["StatusBarView"] ~~~ settings["SettingsTiles"] ~~~ adapter["AppTileAdapter<br/>TileItem"] ~~~ dialogs["TileActionsDialog<br/>OptionsDialog<br/>LinkDialog"] ~~~ updateflow["UpdateFlow"]
    end
    subgraph input["Input and window"]
        direction LR
        keys["KeyInput<br/>blockKeyLongPress<br/>EscapeKeys"] ~~~ touch["TouchOnlyRow"] ~~~ quick["QuickLaunch"] ~~~ immersive["Immersive<br/>LayoutUtils"]
    end
    subgraph data["Data"]
        direction LR
        appc["MotoLauncherApp<br/>app-list cache"] ~~~ repo["AppRepository"] ~~~ stores["Preference stores"] ~~~ updater["UpdateChecker"]
    end
    subgraph platform["Android platform"]
        direction LR
        p1["LauncherApps<br/>UserManager"] ~~~ p2["SharedPreferences"] ~~~ p3["Connectivity, telephony,<br/>location, battery"] ~~~ p4["HttpURLConnection<br/>FileProvider<br/>github flavor"] ~~~ p5["Lock task mode"]
    end

    screens --> widgets --> input --> data --> platform
    screens --> data

    classDef primary fill:#2F6FED,stroke:#1E4FB0,color:#FFFFFF
    class home,list,park,appc,repo,stores,updater primary
```

Two components also reach the platform directly: `StatusBarView` reads the system
services, and `ParkActivity` calls lock task mode.

## Persistent state

There is no database. State lives in four `SharedPreferences` files, shown here as
entities. All but `park` are included in Android backup.

```mermaid
erDiagram
    favorites {
        string slot_N "N = 0..13: flattened ComponentName, or the marker link:"
        long slot_N_user "app slots in another profile: its serial number"
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
        long nav_app_user "nav app in another profile: its serial number"
        int orientation "default FULL_SENSOR"
        string tile_size "LARGE, MEDIUM or SMALL"
        boolean show_nav_bar "default false"
        boolean cellular_enabled "default false"
        boolean speed_enabled "default false"
        boolean speed_metric "default true"
        string battery_display "BOTH, ICON or TEXT"
    }
    park {
        boolean parked "default false"
        string park_pin_salt
        string park_pin_hash
    }
    launcher_activity {
        string component "package/class, resolved through LauncherApps"
        string user "the profile it is installed in"
    }
    favorites }o--o{ launcher_activity : "app slots launch"
    settings }o--o| launcher_activity : "nav_app launches"
    hidden_apps }o--o{ launcher_activity : "hides by package"
```
