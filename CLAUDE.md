# CLAUDE.md — project context for AI assistants (apple branch)

This repository (`yukh/3X-UI-Manager`) holds the native mobile **management apps**
for a [3x-ui](https://github.com/MHSanaei/3x-ui) panel — manager only, **no** panel
Go source. It is split by branch:
- **`main`** — the native **Android** app (Kotlin + Jetpack Compose), at the repo root.
- **`apple`** (this branch) — the **Apple** app: a Kotlin-Multiplatform /
  Compose-MP project at the **repo root**, producing **iOS** (via Xcode) and a
  **macOS desktop** app. Modules: `:shared` (logic) + `:composeApp` (UI), with the
  Xcode host in `iosApp/` and the desktop packager in `scripts/package-macos.sh`.
- **`manual`** — the 3X-UI panel user manual (RU canonical + EN), panel v3.3.0.

The upstream panel is mirrored read-only in the separate repo **`yukh/3x-ui`**
(project id 15) — a *pure* mirror of MHSanaei/3x-ui, kept only to diff what changed
on a panel upgrade. Read upstream sources from there; never develop in it.

👉 Shared background (API facts, hard-won gotchas) lives in
[`docs/ANDROID-HANDOFF.md`](docs/ANDROID-HANDOFF.md) and
[`docs/RESUME-NEXT-SESSION.md`](docs/RESUME-NEXT-SESSION.md) — written from the
Android side but the API/behaviour facts apply to the Apple app too.

## Standing rules (do these without being re-asked)
1. Work on branch **`apple`**. Android work goes on **`main`**. After each
   meaningful change: focused commit (`feat(apple): …` / `fix(apple): …`) + push to
   `origin`. Don't push `upstream`.
2. **Android is the source of truth** for features and versioning; this branch
   tracks it. On a release version bump (driven from `main`), also update
   `composeApp/src/desktopMain/.../Platform.desktop.kt` (`appVersionName`) and
   `iosApp/iosApp/Info.plist` (`CFBundleShortVersionString` / `CFBundleVersion`).
3. **No GitLab CI on this branch** — a Linux runner can't build Apple targets.
   - **iOS:** open `iosApp/iosApp.xcodeproj` in Xcode (needs macOS + Xcode).
   - **macOS desktop:** `JAVA_HOME=<jdk> ./scripts/package-macos.sh "<Apple Silicon|Intel>"`
     (needs `brew install create-dmg`); arch of the result = arch of the JDK
     running Gradle. Quick run: `./gradlew :composeApp:run`.
   - **Don't build on this Intel Mac** unless asked (slow/unstable); the user
     verifies iOS/desktop on an Apple-Silicon machine.
4. Keep **both READMEs** (`README.md` + `README.ru.md`) updated.
5. In the **Russian** UI, do **not** translate the words `inbound` / `outbound`.
6. Copyright: `© 2026 Yuriy Khachaturian (yukh.net)`.

## Quick facts
- GitLab: `yukh/3X-UI-Manager`, project id **19**, `git.home.yukh.net`. API token
  at `~/.gl-token` (local). The upstream mirror is `yukh/3x-ui`, project id **15**.
  Released builds are cut from `main` (Android); latest **v0.3.23**.
- **Auth = API token only** (Bearer), requires panel **v3.3.0+**. A token is full
  admin (no read-only/scoped tokens) and covers everything the app does — dashboard,
  inbounds, clients, nodes, Xray config, settings, subscription links, backup/restore.
- **App-lock rule:** the passcode/biometric lock guards the **signed-in UI only** —
  armed when a saved session is auto-restored at launch or when backgrounded while
  connected; never shown signed-out or right after a fresh manual sign-in. The gate
  is in `composeApp/.../app/App.kt` (`if (locked && connected)`).
- **Desktop packaging gotcha:** jpackage refuses a `0.x` major, so Gradle's
  `packageVersion` stays `1.0.0`; the script patches the real version into
  `CFBundleShortVersionString` post-build and re-signs. It also wipes
  `composeApp/build/compose` first (a cached jlink runtime would silently mix
  arches) and verifies launcher/JVM arch match. 3X app icon: `composeApp/icons/`.
- **Targets:** `:composeApp` = `iosX64` (Intel-sim) / `iosArm64` (device) /
  `iosSimulatorArm64` / `jvm("desktop")`. **No Android target** here (that's `main`).
- The Gradle **wrapper jar is committed** on this branch, so `./gradlew` works out
  of the box (unlike `main`, where it's regenerated).
- **3X-UI panel manual (RU + EN)** lives on the **`manual`** branch.

(Most API/behaviour rationale is shared with the Android app — see `docs/`.)
