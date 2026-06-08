# 3X-UI Manager — Android app

Native Android client for managing a [3x-ui](https://github.com/MHSanaei/3x-ui) panel over its REST API.

🇷🇺 [Версия на русском](README.ru.md) · 📝 [Changelog](CHANGELOG.md)

## Features

- **Dashboard** — live server status, polled every 3 s: Xray running/stopped (with one-tap restart), CPU, RAM, disk, online clients, network throughput, connection counts, load average, uptime, panel/Xray versions.
- **Inbounds** — list with per-row enable/disable (optimistic), traffic usage vs quota, client counts. Full editor: remark, port, listen, protocol, traffic limit, reset schedule, expiry, plus raw-JSON `settings` / `streamSettings` / `sniffing` for any transport/TLS/Reality setup. Create and delete.
- **Clients** — list with online presence, traffic, expiry, last-seen. Per-client share sheet with the **subscription** QR + link and each individual **connection** QR + link (copy / share). Full editor: email, inbound membership, traffic limit, IP limit, reset, expiry, Telegram ID, group, comment. Create and delete.
- **Nodes** — manage remote panels (multi-panel): online status, CPU/RAM/latency, inbound/client counts. Add / edit / delete with name, address, port, scheme, base path, API token, TLS verify mode.
- **Xray config** — edit the full Xray config (outbounds, routing, DNS) as JSON, the same as the panel's Xray Configuration page.

## Authentication

Two modes, chosen on the Connect screen:

| Mode | What works |
|------|-----------|
| **API token** (Bearer) | Dashboard, Inbounds, Clients, Nodes — everything under `/panel/api/*` |
| **Login + password** (session, optional 2FA) | Everything above **plus** subscription URLs and the Xray config editor |

3x-ui only exposes panel settings and the Xray config to a logged-in session, not to an API token — so the subscription link and outbound/routing editing require login/password. Create an API token under *Settings → Security → API Token* in the panel.

Self-signed TLS is supported via an opt-in toggle per connection. Credentials are stored in `EncryptedSharedPreferences` (AES-256, key in the Android Keystore).

## Tech stack

- Kotlin 2.1 + Jetpack Compose (Material 3)
- Min SDK 24 (Android 7.0), target/compile SDK 35 (Android 15)
- Hilt (DI), Retrofit + OkHttp + kotlinx.serialization (networking), zxing (QR codes)

## Project layout

```
android/
├── app/src/main/java/net/yukh/xui/
│   ├── data/
│   │   ├── api/        # Retrofit interface, response envelope, DTOs
│   │   ├── auth/       # Bearer / cookie / CSRF interceptors, TLS
│   │   ├── prefs/      # EncryptedSharedPreferences connection store
│   │   └── repo/       # PanelRepository — single source of truth
│   ├── di/             # Hilt modules
│   ├── ui/
│   │   ├── screen/     # connect, dashboard, inbounds, clients, nodes, xray, main
│   │   ├── format/     # byte/date/uptime formatters
│   │   ├── qr/         # QR generator
│   │   └── navigation/ # AppNav + bottom-nav routes
│   ├── MainActivity.kt
│   └── XuiApp.kt
├── build.gradle.kts · settings.gradle.kts · gradle/libs.versions.toml
```

## Building

Open the `android/` folder in Android Studio (Hedgehog or newer) — it syncs and downloads everything, including the Gradle wrapper.

CLI:

```bash
cd android
gradle wrapper --gradle-version 8.10.2   # one-time, generates ./gradlew
./gradlew assembleDebug                   # debug APK
./gradlew assembleRelease                 # signed release APK (needs keystore)
./gradlew testDebugUnitTest               # unit tests
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## CI / releases

[`.gitlab-ci.yml`](../.gitlab-ci.yml) (repo root) builds **only** on a version tag (`vX.Y.Z`) or a manual trigger — branch pushes don't build. A tag pipeline builds the debug + signed release APKs, runs tests, uploads both APKs to the project's Generic Package Registry, and auto-creates a GitLab Release linking them. `versionName`/`versionCode` are derived from the tag, so the version only changes at release time.

To cut a release: push a `vX.Y.Z` tag.
