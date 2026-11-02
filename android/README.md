# Android app

Kotlin, Jetpack Compose with Material 3 (dynamic colour), a Glance home-screen widget and a
15-minute WorkManager job. Dutch UI. minSdk 26, targets Android 16 (API 36).

## Modules

| Module | Contents |
| --- | --- |
| `:app` | Activity, application class, schedules the background job |
| `:core:domain` | Pure Kotlin: forecast model, intensity bands, Dutch verdict text (unit tested) |
| `:core:data` | Relay settings; API client and cache arrive in milestones 2 and 3 |
| `:core:ui` | Theme and colours |
| `:feature:home` | Home screen |
| `:widget` | Glance widget and the refresh worker |

Map, places and settings modules come with their milestones.

## Get the APK

Every push that touches `android/` runs the **Android** workflow (unit tests, lint, debug build).
Open the run under the repo's Actions tab and download the `geen-regen-aub-debug-apk` artifact,
unzip it and install the APK on your phone (allow installs from your browser or file manager).

Each CI run currently signs with a fresh debug key, so Android may ask you to uninstall the
previous build first. A fixed signing key arrives with the release build in milestone 6.

## Build locally

Needs Android Studio (or JDK 17 plus the Android SDK). Create `android/local.properties`
(git-ignored) with:

```properties
sdk.dir=/path/to/Android/sdk
geenregen.relaySecret=the same value as RELAY_SHARED_SECRET on the relay
# optional, defaults to https://geenregenaub.theharmen.org
# geenregen.relayUrl=http://192.168.1.10:8080
```

Then:

```sh
./gradlew :core:domain:test testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For CI builds that should reach the relay, add the repository secret `GEENREGEN_RELAY_SECRET`.
