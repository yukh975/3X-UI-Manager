# 3X-UI Manager — Android App

Native Android client for managing a [3x-ui](../README.md) panel via its REST API.

## Stack

- Kotlin 2.1 + Jetpack Compose (Material 3)
- Min SDK 24 (Android 7.0), Target/Compile SDK 35 (Android 15)
- Hilt for DI, Retrofit + OkHttp for networking, kotlinx.serialization for JSON
- EncryptedSharedPreferences for storing panel URL + API token

## Project layout

```
android/
├── app/                          # :app module — UI, ViewModels, DI
│   └── src/main/
│       ├── java/net/yukh/xui/
│       │   ├── data/             # API client, DTOs, repository, prefs
│       │   ├── di/               # Hilt modules
│       │   ├── ui/               # Compose screens + theme + navigation
│       │   ├── XuiApp.kt         # Application (@HiltAndroidApp)
│       │   └── MainActivity.kt
│       └── res/
├── build.gradle.kts              # Root build script (plugin DSL)
├── settings.gradle.kts           # Modules + repositories
├── gradle.properties             # JVM args, AndroidX flags
└── gradle/libs.versions.toml     # Version catalog (single source of truth)
```

## Local development

**Recommended:** open the `android/` folder in Android Studio (Hedgehog or newer). The IDE will sync, download dependencies, and generate the Gradle wrapper automatically.

**CLI build:**

```bash
cd android
gradle wrapper --gradle-version 8.10.2     # generate ./gradlew (one-time)
./gradlew assembleDebug                    # build debug APK
./gradlew testDebugUnitTest                # run unit tests
./gradlew installDebug                     # install on a connected device/emulator
```

Built APK lands at `android/app/build/outputs/apk/debug/app-debug.apk`.

## CI

[`.gitlab-ci.yml`](../.gitlab-ci.yml) at the repo root runs on every push that touches `android/**` or the CI file itself:

- `build:debug` → produces a downloadable debug APK artifact (2-week retention)
- `test:unit` → runs `testDebugUnitTest`, publishes JUnit reports

Pipelines are restricted by `workflow.rules` so changes outside `android/` (e.g. Go panel source) don't trigger Android builds.

## Connecting to a panel

The app authenticates against the panel using a **Bearer API token** created in *Settings → Security → API Token* (see the panel's own [OpenAPI spec](../frontend/public/openapi.json)). The token is stored locally in EncryptedSharedPreferences.

Cookie-based session login is intentionally not implemented — Bearer tokens are simpler, work everywhere `/panel/api/*` is reachable, and don't require CSRF.

## Status

Scaffold only. Planned MVP screens:

- [ ] Connection profile (URL + token, self-signed TLS toggle)
- [ ] Dashboard (CPU/RAM/online, xray status — polling `/panel/api/server/status`)
- [ ] Inbounds list with enable/disable toggle
- [ ] Clients list, add/delete, share subscription link + QR code
