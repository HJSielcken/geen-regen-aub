# Android app

Kotlin, Jetpack Compose with Material 3 (dynamic colour), a Glance home-screen widget and a
15-minute WorkManager job. Dutch UI. minSdk 26, targets API 37. Built with AGP 9 (built-in Kotlin), Gradle 9 and JDK 21.

## Modules

| Module | Contents |
| --- | --- |
| `:app` | Activity, application class, schedules the background job |
| `:core:domain` | Pure Kotlin: forecast model, intensity bands, Dutch verdict text (unit tested) |
| `:core:data` | Relay client, DataStore forecast cache, coarse location, place search (PDOK); entry point `GeenRegenData` |
| `:core:ui` | Theme and colours |
| `:feature:home` | Home screen: verdict, 2-hour rain graph, place picker |
| `:feature:alerts` | Rain alert settings screen, notification, settings storage |
| `:feature:forecast` | Weather forecast screen (now, 24 hours, 10 days), the home screen weather card, Meteocons icons |
| `:widget` | Glance widgets (rain: verdict + 2-hour graph; weather: now, hours, days, clock; air quality, sun, moon, wind) and the 15-minute worker that refreshes, redraws and runs the alert check |
| `:feature:map` | Animated radar map (MapLibre, PDOK base map, frames from the relay's `/v1/radar`) |


## Widget and rain alerts

The widget reads the forecast cache from `:core:data` only; the worker refreshes it every ~15 minutes (and right
away when the first widget is placed or a setting changes). At 2 rows or more it shows the bar
graph, smaller sizes show just the verdict.

The weather widget ("Weerbericht") grows with its size: just the weather now at 1 row, the next
hours from 2 rows, and the coming days below that. The weather forecast comes straight from
Open-Meteo and follows the same place as the rain forecast; the worker only fetches it when the
cached one is older than 30 minutes.

Four more widgets sit beside them, each scaling with its size: **Luchtkwaliteit** (European AQI
with its band, the scale, and the next 24 hours as bars; Copernicus CAMS via Open-Meteo, fetched
together with the weather), **Zon** (sunrise, sunset and the sun's path with the sun where it is
now), **Maan** (phase, how much is lit, next new and full moon; computed on the phone) and
**Wind** (speed, direction, Beaufort and gusts, a compass, and the coming hours). "Weer nu"
leaves the wind and the sun to these.

Rain alerts are off by default. Turn them on in **Instellingen**: the gear on the home screen, a
long-press on the app icon, or the gear on Android's app info page.
Rules (in `:core:domain`, `RainAlertPolicy`): rain at or above the chosen intensity starting within
the lead time (15–60 min) notifies once per shower, nothing during quiet hours (23:00–07:00) or
from a forecast older than 30 minutes, and never from sample data (builds without a relay key show
sample data labelled "Voorbeeldgegevens"). Because WorkManager runs about every 15 minutes, a warning
can arrive up to ~15 minutes later than the lead time.

## Get the APK

Every push that touches `android/` runs the **Android** workflow (unit tests, lint, APK build).
On a pull request the workflow posts a comment with a direct link to the APK. Otherwise open
the run under the repo's Actions tab and download the `geen-regen-aub-apk` artifact. Unzip it
and install the APK on your phone (allow installs from your browser or file manager).

## Releases

Push a tag such as `v1.0.0` and the **Release** workflow builds a signed, minified release APK
and publishes it as a GitHub Release (`geen-regen-aub-1.0.0.apk`). The version name comes from
the tag. Running the workflow by hand from the Actions tab builds the same APK as an artifact
without publishing anything, which is a quick way to check the R8 build.

Every CI build, debug or release, gets a version code of minutes since 2026-01-01, so whichever
APK is newer installs over the older one.

## Size

Release builds run R8 (code shrinking, obfuscation, optimisation) and resource shrinking, with
keep rules in `app/proguard-rules.pro`. MapLibre's native library is most of the APK, so only
the `arm64-v8a` copy is packed (all phones of the last years). For an x86 emulator or an old
32-bit phone, build with `-Pgeenregen.allAbis=true`. That library is packed compressed, which
roughly halves the download; Android unpacks it on install. Only Dutch and English resources are
kept. Every build log ends with a size breakdown (`reportApkSize`).

## Crash reports (Sentry)

Crashes go to the Sentry project `theharmen/geen-regen-aub` (EU region). The app only starts
Sentry when the build has a DSN, and sends no personal data. Repository secrets:

| Secret | Value |
| --- | --- |
| `SENTRY_DSN` | The project's DSN (Sentry → Project settings → Client Keys) |
| `SENTRY_AUTH_TOKEN` | An organization auth token, so release builds upload the R8 mapping and stack traces stay readable |

Locally, use `geenregen.sentryDsn` and `geenregen.sentryAuthToken` in `local.properties`.

## Signing

Every build is signed with the same key, so a new APK installs over the old one. CI reads the key
from these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | The keystore file, base64-encoded (`base64 -w0 geenregen.jks`) |
| `SIGNING_KEYSTORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias |
| `SIGNING_KEY_PASSWORD` | Key password (the same as the keystore password for PKCS12) |

Without them, CI falls back to a throwaway debug key and the workflow shows a warning. Switching
keys (for example to a key you create yourself later) means uninstalling the app once.

To create your own key:

```sh
keytool -genkeypair -keystore geenregen.jks -storetype PKCS12 -alias geenregen \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Geen regen aub, C=NL"
```

Keep the keystore and passwords somewhere safe: without them you cannot update the app in place.

## Build locally

Needs Android Studio (or JDK 21 plus the Android SDK). Create `android/local.properties`
(git-ignored) with:

```properties
sdk.dir=/path/to/Android/sdk
geenregen.relaySecret=the same value as RELAY_SHARED_SECRET on the relay
# optional, defaults to https://geenregenaub.theharmen.org
# geenregen.relayUrl=http://192.168.1.10:8093
# optional: sign local builds with the shared key
# geenregen.signing.storeFile=/path/to/geenregen.jks
# geenregen.signing.storePassword=...
# geenregen.signing.keyAlias=geenregen
# geenregen.signing.keyPassword=...
```

Then:

```sh
./gradlew :core:domain:test testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For CI builds that should reach the relay, add the repository secret `GEENREGEN_RELAY_SECRET`.
