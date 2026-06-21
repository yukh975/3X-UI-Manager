# 3X-UI Manager — Apple

Native Apple clients for managing a [3x-ui](https://github.com/MHSanaei/3x-ui) panel over its REST API — dashboard, inbounds, clients (with QR sharing), nodes, and the Xray config.

This branch (`apple`) is a **Kotlin Multiplatform + Compose Multiplatform** project, built straight from the repository root. It produces an **iOS** app (via Xcode) and a **macOS desktop** app, sharing business logic with the Android app.

🇷🇺 [Версия на русском](README.ru.md)

---

## Repository layout

| Branch | What's there |
| --- | --- |
| **`main`** | The **Android** app (Kotlin + Jetpack Compose). |
| **`apple`** | The **Apple** app (this branch) — Kotlin Multiplatform / Compose-MP: **iOS** + **macOS desktop**. |

The 3X-UI panel **user manual** (RU canonical + EN) lives in its own repository, **3X-UI-Manual** — it's unrelated to the manager apps.

The upstream project lives at [MHSanaei/3x-ui](https://github.com/MHSanaei/3x-ui); a private **pure mirror** of it is kept separately as a read-only reference for diffing what changed on a panel upgrade. This repository is the management app only — it carries none of the panel's Go source.

**Android is the source of truth** for features and versioning; this branch tracks it.

---

## Modules

| Module | Role |
| --- | --- |
| **`:shared`** | Cross-platform business logic (API client, models, repository) — iOS targets + JVM desktop. |
| **`:composeApp`** | The Compose Multiplatform UI and app shell — iOS targets + `jvm("desktop")`. |
| **`iosApp/`** | The Xcode project that hosts `:composeApp` as an iOS app (Swift entry point + asset catalog). |

## Features

Feature parity with the [Android app](https://github.com/MHSanaei/3x-ui) (see the `main` branch README for the full list): **API-token** connect, live **dashboard**, **inbounds** (toggle/edit), **clients** (search, edit, QR/subscription sharing), **nodes**, the structured **Xray config** editor, **backup/restore**, **panel admin**, and an optional **app lock** (passcode + biometric) that guards the signed-in UI only.

## Authentication

**API token** (Bearer) only — requires panel **v3.3.0 or newer**. A 3x-ui token is full admin (no read-only/scoped tokens), so guard it like the password. Create one under **Settings → Security → API Token**.

## Building

The Gradle **wrapper jar is committed**, so `./gradlew` works out of the box. JDK 17+ is required.

### iOS

Open `iosApp/iosApp.xcodeproj` in **Xcode** and run on a simulator or device. Xcode invokes Gradle to build and embed the shared Kotlin framework; on an Apple-Silicon Mac this is the fastest loop. (The iOS build needs macOS + Xcode — a Linux CI can't produce it, which is why this branch has no GitLab pipeline.)

### macOS desktop

```bash
# Apple Silicon:
JAVA_HOME=<arm64 jdk> ./scripts/package-macos.sh "Apple Silicon"
# Intel:
JAVA_HOME=<x64 jdk> arch -x86_64 ./scripts/package-macos.sh "Intel"
```

`scripts/package-macos.sh` builds the distributable, patches the real app version into `CFBundleShortVersionString` (jpackage refuses a `0.x` major, so Gradle's `packageVersion` stays `1.0.0`), signs ad-hoc, verifies launcher/JVM arch match, and wraps a `create-dmg` image (`brew install create-dmg`). The arch of the result follows the arch of the JDK that runs Gradle. App version is read from `composeApp/.../Platform.desktop.kt`.

For a quick run without packaging:

```bash
./gradlew :composeApp:run            # desktop app
```

## Releases

Apple artifacts (the iOS `.ipa` and macOS `.dmg`) are built **locally** on macOS — there is no GitLab CI on this branch (a Linux runner can't build Apple targets). The Android app's release pipeline lives on the `main` branch.

**Version sync:** when the Android version is bumped on `main`, also update `composeApp/.../Platform.desktop.kt` (`appVersionName`) and `iosApp/iosApp/Info.plist` (`CFBundleShortVersionString` / `CFBundleVersion`).

---

© 2026 Yuriy Khachaturian ([yukh.net](https://yukh.net))
