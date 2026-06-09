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
3. **Compile locally before tagging**: `cd android && ./gradlew :app:assembleDebug`.
   Only boot the emulator for visual checks when a change touches layout.
4. Keep **both changelogs** (`android/CHANGELOG.md` + `.ru.md`) and **both READMEs**
   updated. The GitLab Release notes are generated (in Russian) from `CHANGELOG.ru.md`.
5. In the **Russian** UI, do **not** translate the words `inbound` / `outbound`.
6. Copyright: `© 2026 Yuriy Khachaturian (yukh.net)`.
7. Delete old/buggy GitLab releases only after the user confirms a build is stable.

## Quick facts
- GitLab: `yukh/3x-ui`, project id **15**, `git.home.yukh.net`. API token at
  `~/.gl-token` (local). Latest **released**: **v0.3.12** ("первый стабильный" was
  v0.3.11). **v0.3.13 is committed on `android-app` but NOT yet tagged** (online-by-
  server fix) — tag it when the user says "собирай".
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
- Node 3x-ui self-update: `POST /panel/api/nodes/updatePanel` `{ids:[…]}` (Nodes
  screen shows an "Update" button when a node's version ≠ latest).
- Filed upstream bug: MHSanaei/3x-ui **#5100** (resetting an inbound's traffic on
  the master doesn't propagate to slave nodes).

(Detailed rationale for every point above is in `docs/ANDROID-HANDOFF.md`.)
