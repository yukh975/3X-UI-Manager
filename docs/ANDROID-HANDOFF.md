# 3X-UI Manager (Android) — Project Handoff

> Self-contained context so work can continue from any machine / new chat
> session. If you're an AI assistant picking this up: read this fully first.
> Last updated: 2026-06-08 (after release **v0.3.9**).
>
> ⚠️ **Partially stale — written at v0.3.9.** The current snapshot lives in
> [RESUME-NEXT-SESSION.md](RESUME-NEXT-SESSION.md) («Текущее состояние») and
> the Quick facts of [../CLAUDE.md](../CLAUDE.md). Biggest deltas since: the
> app is **token-only** since v0.3.21 (login/password/2FA removed; requires
> panel **v3.3.0+**, where the whole API incl. settings/Xray-config moved under
> `/panel/api/*` — the "session-only" claims below no longer apply); released
> through **v0.3.23**; the `ios-app` branch has full-parity **iOS + macOS
> desktop** apps (`kmp/`).

## What this is
A **native Android** app (Kotlin + Jetpack Compose) that manages a
[3x-ui](https://github.com/MHSanaei/3x-ui) VPN panel over its REST API —
dashboard, inbounds, clients (QR/sub), nodes, Xray config. Lives in the
[`android/`](../android) folder of this 3x-ui fork. There's also an **`ios-app`**
branch holding iOS groundwork ([`ios/README.md`](../ios/README.md): KMP + Compose
Multiplatform migration plan; built locally on a Mac, never in CI).

## Repo / infra
- Self-hosted GitLab fork: **`yukh/3x-ui`, project id `15`**, web
  `https://git.home.yukh.net/yukh/3x-ui`. Default branch `main`; **active dev
  branch `android-app`**.
- Remotes in the working copy: `origin` →
  `ssh://git@git.home.yukh.net:20222/yukh/3x-ui.git` (push here); `upstream` →
  `https://github.com/MHSanaei/3x-ui.git` (pull-only).
- **GitLab API token**: local file `~/.gl-token` (per-machine — not in repo).
  Header `PRIVATE-TOKEN`. API base `https://git.home.yukh.net/api/v4`.
- SSH: host alias in `~/.ssh/config`, port 20222, key `~/.ssh/gitlab.key`.

## Standing working rules (the user's preferences — follow these)
1. **Commit + push after each meaningful change** to `origin android-app`
   (focused conventional-commit messages: `feat(android): …`, `fix(android): …`).
   Don't batch a whole session into one commit; don't push to `upstream`.
2. **Build/release ONLY on a version tag** `vX.Y.Z`. Branch pushes do NOT build
   (`.gitlab-ci.yml` `workflow.rules`). Don't tag per change — batch commits under
   one release tag, created when the user says "собирай"/ready. versionName/Code
   derive from the tag.
3. **Superseded pipelines auto-cancel** (`interruptible: true` + project
   `auto_cancel_pending_pipelines`). Let GitLab handle it.
4. **Compile locally before tagging** (reliability): `./gradlew
   :app:assembleDebug`. But **don't routinely boot the emulator** — only do the
   emulator UI check when a change touches layout and could glitch visually.
5. **Releases page is Russian.** CI generates the GitLab Release description from
   the matching version section of `CHANGELOG.ru.md`.
6. **Maintain both changelogs** (`CHANGELOG.md` + `.ru.md`) and READMEs
   (`README.md` + `.ru.md`). Copyright: `© 2026 Yuriy Khachaturian (yukh.net)`.
7. **Clean up old releases**: once a release is confirmed stable, delete earlier
   buggy ones (`DELETE /projects/15/releases/<tag>`) — but only on the user's
   go-ahead. v0.1.0 already deleted.
8. **i18n**: keep the words **`inbound`/`outbound` untranslated** in Russian.

## Release state (2026-06-08)
- Latest: **v0.3.9**. Releases present: v0.2.0, v0.2.2, v0.3.0–v0.3.9 (all RU
  notes). v0.1.0 deleted.
- Pending cleanup: delete v0.2.x–v0.3.8 once the user confirms a stable build
  (awaiting their word).
- Distribution: signed `3x-ui-manager-release.apk` in the GitLab **Generic
  Package Registry** (`/packages/generic/xui-android/<version>/`) + GitLab
  Release. Permanent links survive artifact expiry.
- **Future**: at full release, publish to **Google Play** + **F-Droid** (removes
  the Play Protect "unverified developer" warning). F-Droid needs an OSS license
  in the repo + a public git mirror; no Google Play Services (we have none).

## Build & signing
- Stack: Kotlin 2.1, AGP 8.7.3, Gradle 8.10.2, JDK 17, compileSdk/targetSdk 35,
  minSdk 24. package/applicationId `net.yukh.xui`, archivesName `3x-ui-manager`
  → output `3x-ui-manager-release.apk`. Hilt, Retrofit+OkHttp+kotlinx.serialization,
  Compose BOM 2024.12.01 (Material3 1.3.1), zxing, androidx.security-crypto,
  androidx.biometric.
- CI: [`.gitlab-ci.yml`](../.gitlab-ci.yml) — on a `v*.*.*` tag: `build:release`
  (signed APK → package registry), `test:unit`, `release:publish` (release-cli,
  RU notes from changelog). Debug APK build was removed.
- **Release keystore** (NOT in repo): `~/.config/3x-ui-android-keystore/`
  (`release.p12`, `release.properties`). Also stored as GitLab CI vars
  `RELEASE_KEYSTORE_B64` / `RELEASE_KEYSTORE_PASSWORD` / `RELEASE_KEY_ALIAS`
  (`xui-release`) / `RELEASE_KEY_PASSWORD` → so **CI can sign from any machine**
  (just push a tag). Cert SHA-256:
  `66:8C:DB:5C:D9:DE:B1:79:8E:1B:5A:0A:C1:26:DB:A3:B1:B3:6B:0D:A2:06:2C:64:30:9A:EB:53:CF:8F:96:4A`.
  ⚠️ Losing this key breaks all future app upgrades — it's recoverable from the
  CI var `RELEASE_KEYSTORE_B64` (base64 of release.p12). `build.gradle.kts` reads
  `RELEASE_KEYSTORE_FILE` env or the local keystore; unsigned if neither present.

### Local build on a fresh Mac
JDK 17 + Android SDK (platform 35, build-tools 35.0.0, platform-tools) required.
The Gradle **wrapper is gitignored** (`gradlew`, `gradle/wrapper/*.jar`);
regenerate: system gradle is 9.x (too new for AGP 8.7.3), so generate the 8.10.2
wrapper in a temp dir containing an empty `settings.gradle.kts`, then copy
`gradlew` + `gradle-wrapper.jar` into `android/`. Then
`./gradlew :app:assembleDebug` (set `JAVA_HOME` to 17, `ANDROID_HOME`).
For visual checks: an AVD + `adb install` + `adb exec-out screencap`. Caveat:
`adb input text` drops chars on long strings — type long fields char-by-char.

## Architecture & hard-won API facts (don't relearn these)
- **Auth boundary**: `/panel/api/*` accepts **Bearer token OR session**.
  `/panel/setting/*` and `/panel/xray/*` are **session-only** (login/password) —
  so with a token, the Xray-config editor and panel-derived subscription base are
  unavailable. Subscription link with a token uses a manual "Subscription base
  URL" field on the connect screen. Response envelope `{success,msg,obj}`.
- **JSON**: lenient (`ignoreUnknownKeys, coerceInputValues, explicitNulls=false,
  isLenient, encodeDefaults`). DTOs default every field.
- **ServerStatus**: `xray{state,version,errorMsg}`; `uptime` = server uptime
  (s); `appStats.uptime` = **Xray core uptime** (s, resets on restart) — shown on
  the dashboard. Online tile = `/panel/api/clients/onlines` (emails).
- **Online → which server (key finding)**: xray keys traffic/online **by email
  globally**, attributed to ONE canonical inbound; 3x-ui copies that identical
  `ClientStat` (same `id`/`inboundId`/`up`/`down`/`lastOnline`) into **every
  inbound the email is a member of**. So the central API **cannot** tell which
  inbound a client is live on. → The online dialog instead groups **by server**:
  main panel's `/onlines`, then **each node queried directly** at its own
  `scheme://address:port/basePath/panel/api/clients/onlines` with the node's
  `apiToken` (from `GET /panel/api/nodes/list`). This reveals a client connected
  to several nodes at once (e.g. `home` on se-sto + fr-par). See
  `PanelRepository.listNodeOnlines` + `DashboardViewModel.openOnlineList`.
- **Nodes**: this fork has central multi-node. `GET /panel/api/nodes/list`
  returns nodes with `address/port/basePath/apiToken` + heartbeat status.
- **Xray controls**: dashboard offers **only Restart** (no Start/Stop). Stopping
  Xray cuts off the panel/API when the panel is reverse-proxied through Xray —
  confirmed with **both token AND login/password** — and the app can't recover
  it (needs a host-level panel restart). Restart is safe.
- **Editors are full-screen overlays in `MainScreen`, NOT `Dialog`s.** Compose
  Dialog windows don't reliably get system-bar/IME insets, which hid the Delete
  button under the nav bar and the Comment field under the keyboard. MainScreen
  shares the tab ViewModels and renders editors over the Scaffold; Delete sits in
  a pinned Scaffold `bottomBar` (navigationBarsPadding); Scaffold uses
  `imePadding`; `windowSoftInputMode=adjustResize`. Text+control rows give the
  label `weight(1f)` so long RU text doesn't run under switches.
- **i18n**: `tr("English")` (`@Composable`, reads `LocalAppLanguage`) + RU
  dictionary `RuStrings.kt` (English string is the key). Language switch in
  Settings, no restart. Formatters take a `lang` param for relative-time words.

## Test panel (user's, IP-whitelisted)
`https://netadm.pro/cceGhWCWxd58CmdnAv` + an API token the user provides. Token
mode. Panel reachable only from whitelisted IPs (the Mac's). The panel returns
**404 for a missing/wrong token** (not 401). Has 3 nodes (fi-hel/se-sto/fr-par).
Don't store the token in the repo; don't stop Xray on it (disrupts service).

## Likely next steps
- Await user's "stable" confirmation → delete old releases (rule 7).
- iOS (`ios-app` branch): scaffold the KMP + Compose MP project per
  `ios/README.md` (Retrofit→Ktor, Hilt→Koin, Keystore→Keychain,
  BiometricPrompt→LocalAuthentication). Built locally on the Mac (Xcode set up).
- Store publishing (Play + F-Droid) when the user calls a full release.
