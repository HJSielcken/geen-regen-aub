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
| `:widget` | Glance widget and the refresh worker |

Map, places and settings modules come with their milestones.

## Get the APK

Every push that touches `android/` runs the **Android** workflow (unit tests, lint, APK build).
On a pull request the workflow posts a comment with a direct link to the APK. Otherwise open
the run under the repo's Actions tab and download the `geen-regen-aub-apk` artifact. Unzip it
and install the APK on your phone (allow installs from your browser or file manager).

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
