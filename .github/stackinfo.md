# Stack

## Target devices

| Device | Screen | Android |
|---|---|---|
| Motorcycle navigation device (primary) | 7", 1920×1080, landscape-mounted | 16 (earlier 14) |
| Phones (secondary) | smallest width < 600dp | 9 or newer |

The navigation app the launcher starts is a separate app
(`com.thorkracing.dmd2launcher` on the primary device). It is configured, not bundled.

## Android targets

| Setting | Value |
|---|---|
| `minSdk` | 28 (Android 9) |
| `targetSdk` | 36 (Android 16) |
| `compileSdk` | 36 |

Feature floor above `minSdk`: the park lock is offered from API 35.

## Language and build

| Component | Version |
|---|---|
| Kotlin | 2.0.21 (JVM target 17) |
| Android Gradle Plugin | 8.13.2 |
| Gradle | 8.14.6 (Kotlin DSL; wrapper jar and distribution checksum-pinned) |
| JDK | 21 (Temurin in CI) |
| Python | 3, as on `ubuntu-latest` (release notes script) |

## Flavors

| Flavor | Distribution | Self-update | Network access |
|---|---|---|---|
| `github` | GitHub release page and `dev` pre-release | Yes | `INTERNET` |
| `play` | Google Play | No | None |

## Libraries

| Library | Version | Use |
|---|---|---|
| `androidx.appcompat:appcompat` | 1.7.0 | Activities, night mode, `AlertDialog` |
| `com.google.android.material:material` | 1.12.0 | Material3 base theme |
| `androidx.recyclerview:recyclerview` | 1.3.2 | App list grid |
| `androidx.activity:activity-ktx` | 1.9.3 | `viewModels`, result APIs, back dispatcher |
| `androidx.core:core-ktx` | 1.17.0 | `FileProvider` (github), window insets, compat helpers |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.6 | `lifecycleScope` |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | 2.8.6 | `ViewModel`, `viewModelScope` |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.8.1 | App enumeration and update I/O |

UI is classic Views with view binding; no Compose. Networking is `HttpURLConnection`
with `org.json`, both from the platform.

## Tests

| Library | Version |
|---|---|
| JUnit | 4.13.2 |
| Robolectric | 4.16.1 |
| `androidx.test:core` | 1.7.0 |
| `kotlinx-coroutines-test` | 1.8.1 |

JVM unit tests in `app/src/test/kotlin`, with Android resources included.

## Assets

- Michroma font (`res/font/michroma.ttf`), SIL Open Font License 1.1, see
  `MICHROMA-LICENSE.txt`.

## CI/CD

GitHub Actions on `ubuntu-latest`, Node 24 actions: `actions/checkout@v7`,
`actions/setup-java@v6`, `actions/cache@v6`, `actions/upload-artifact@v7`,
`actions/download-artifact@v8`, `gradle/actions/setup-gradle` v6.4.1 and
`r0adkll/upload-google-play` v1.1.5 (both pinned to a commit), and the `gh` CLI.

| Workflow | Trigger | Runs | Produces |
|---|---|---|---|
| `Check` | push to any branch except `main`, manual | release script tests; lint and unit tests for `githubDebug`; `assemble` for `githubDebug` and `playDebug` | `app-debug` artifact |
| `Build` | push to `main` | `lintGithubRelease`, `lintPlayRelease`, unit tests, signed `assembleGithubRelease` | `dev` pre-release |
| `Release` | manual, from `main` | `build`: signed `assembleGithubRelease` and `bundlePlayRelease`; `publish`: push, GitHub release, Play upload | `release-<version>` artifact, `v<version>` tag and GitHub release, Play upload |

The project is not built locally.
