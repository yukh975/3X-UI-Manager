# CLAUDE.md — project context for AI assistants

This is a self-hosted fork of **3x-ui**. The **active work** here is a native
**Android** management app in [`android/`](android/) (Kotlin + Jetpack Compose),
plus a **working Kotlin-Multiplatform / Compose-MP iOS foundation** in
[`kmp/`](kmp/) on the **`ios-app`** branch (builds/links for iOS; see
[`ios/README.md`](ios/README.md)).

👉 **Read [`docs/ANDROID-HANDOFF.md`](docs/ANDROID-HANDOFF.md) first** — full
state, architecture, hard-won API facts, build/signing, and what's next.
👉 Resuming on a new machine? See
[`docs/RESUME-NEXT-SESSION.md`](docs/RESUME-NEXT-SESSION.md) (step-by-step, RU) —
its "Текущее состояние" section is the latest snapshot.
👉 Backlog of agreed-but-not-done features: [`docs/TODO.md`](docs/TODO.md).

## Standing rules (do these without being re-asked)
1. Work on branch **`android-app`**. After each meaningful change: focused commit
   (`feat(android): …` / `fix(android): …`) + push to `origin`. Don't push `upstream`.
2. **Build/release only on a `vX.Y.Z` tag** (branch pushes don't build). Don't tag
   per change — batch under one tag when the user says they're ready ("собирай").
3. **Dev loop for every change:** write code → compile locally
   (`cd android && ./gradlew :app:assembleDebug`) → **install on the local
   emulator and verify the functionality actually works** → only when fully
   debugged, tag `vX.Y.Z` to trigger the GitLab build + release. Don't tag until
   it's emulator-verified.
4. Keep **both changelogs** (`android/CHANGELOG.md` + `.ru.md`) and **both READMEs**
   updated. The GitLab Release notes are generated (in Russian) from `CHANGELOG.ru.md`.
5. In the **Russian** UI, do **not** translate the words `inbound` / `outbound`.
6. Copyright: `© 2026 Yuriy Khachaturian (yukh.net)`.
7. Delete old/buggy GitLab releases only after the user confirms a build is stable.

## Quick facts
- GitLab: `yukh/3x-ui`, project id **15**, `git.home.yukh.net`. API token at
  `~/.gl-token` (local). Latest **released**: **v0.3.23** ("первый стабильный" was
  v0.3.11). Recent: v0.3.23 = no passcode prompt right after a manual sign-in;
  v0.3.22 = app-lock only gates the signed-in UI (not the Connect screen);
  **v0.3.21 = token-only auth** (login/password/2FA removed; requires panel
  **v3.3.0+**; Connect screen has a where-to-get-the-token footnote); v0.3.20 =
  401 → back to Connect (X-Requested-With header); v0.3.19 = Panel admin (admin
  creds, API tokens, restart panel); v0.3.18 = backup/restore (engine-agnostic
  getDb/importDB); v0.3.17 = structured Xray-config editor + `vless://` import.
- **App-lock rule (both apps):** the passcode/biometric lock guards the
  signed-in panel UI only — armed when a saved session is auto-restored at
  launch or on ON_STOP while connected. Never shown signed-out or right after a
  fresh manual sign-in. Android: `security/LockState.kt` + `MainActivity`;
  KMP: `App.kt` (locked && connected gate).
- **Desktop (macOS) build exists** on the `ios-app` branch: `kmp/composeApp` has
  a `jvm("desktop")` target (window 1290×880). Package with
  `kmp/scripts/package-macos.sh "<Apple Silicon|Intel>"` — it builds the
  distributable, patches the real app version into CFBundleShortVersionString
  (jpackage refuses MAJOR=0, Gradle packageVersion stays 1.0.0), signs ad-hoc,
  and wraps a create-dmg image (brew install create-dmg) with a per-arch
  README.txt (template in `composeApp/packaging/`). The JDK arch picks the
  target: arm64 JDK → Apple Silicon; `arch -x86_64` + an x64 JDK → Intel. The
  script wipes build/compose (cached jlink runtime would silently mix arches)
  and verifies launcher/JVM arch match. 3X app icon: `composeApp/icons/`.
- **Version sync rule:** Android is the source of truth. On a release bump also
  update `kmp/composeApp/.../Platform.desktop.kt` (appVersionName) and
  `kmp/iosApp/iosApp/Info.plist` (CFBundleShortVersionString/CFBundleVersion).
- Release APK filename now carries the version: `3x-ui-manager-<version>.apk`.
  CI has no `test:unit` job (removed — no tests, didn't gate the release).
- Release signing keystore: `~/.config/3x-ui-android-keystore/` (local) **and**
  GitLab CI vars (`RELEASE_KEYSTORE_B64` …) — so CI signs releases from any machine.
- Dashboard Xray control = **Start / Stop / Restart** for all auth types (Stop only
  breaks the link when the panel is reached *through* Xray; direct connection is
  fine). Optimistic state is pinned ~6 s so a lagging poll doesn't flicker it back.
- Online list is grouped **by server**: main = online ∩ clients of main-panel
  inbounds (`nodeId` 0); each node = its own live `/clients/onlines` (POST), with a
  fallback to inbound-membership when a node isn't directly reachable. `nodeId` on
  the inbound distinguishes main (null/0) from node inbounds.
- Editors are full-screen overlays in `MainScreen` (NOT `Dialog` — Dialogs don't
  get insets); Delete lives in a pinned `bottomBar`.
- **Xray editor (v0.3.17)** lives in `ui/screen/xrayedit/` (+ `ui/screen/outbounds/`);
  all sections round-trip the one config JsonObject via `XrayConfigIO.kt`. GOTCHA:
  3x-ui stores outbound settings **flat** (`settings.address/port/id/flow/encryption`,
  `streamSettings` a sibling), NOT raw-Xray `vnext[]`/`servers[]`. Field specs come
  from the panel's **Vue source** (frontend not in tree — `git show
  bc00d37a:frontend/src/pages/xray/*.vue` + `models/outbound.js`). Xray config works
  with an API token on v3.3.0 (the "session only" gate is a fallback). See
  `docs/RESUME-NEXT-SESSION.md` for what's still raw-JSON (Observatory, xHTTP-advanced,
  hysteria, basic block/direct helpers).
- Node 3x-ui self-update: `POST /panel/api/nodes/updatePanel` `{ids:[…]}` (Nodes
  screen shows an "Update" button when a node's version ≠ latest).
- Filed upstream bug: MHSanaei/3x-ui **#5100** (resetting an inbound's traffic on
  the master doesn't propagate to slave nodes) — **fixed upstream in v3.3.0 (#5103)**.
- **3X-UI panel manual (RU + EN)** lives on branch **`docs/manual`** (NOT
  android-app), MR **!2** → `main`: `docs/3X-UI-MANUAL.ru.md` (canonical) +
  `docs/3X-UI-MANUAL.md` (English). Targets panel **v3.3.0**; 16 sections / 142
  subsections. Edit RU first, then sync EN.

(Detailed rationale for every point above is in `docs/ANDROID-HANDOFF.md`.)
