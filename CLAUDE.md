# CLAUDE.md — project context for AI assistants

This is a self-hosted fork of **3x-ui**. The **active work** here is a native
**Android** management app in [`android/`](android/) (Kotlin + Jetpack Compose),
plus iOS groundwork on the `ios-app` branch.

👉 **Read [`docs/ANDROID-HANDOFF.md`](docs/ANDROID-HANDOFF.md) first** — full
state, architecture, hard-won API facts, build/signing, and what's next.

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
  `~/.gl-token` (local). Latest release: **v0.3.9**.
- Release signing keystore: `~/.config/3x-ui-android-keystore/` (local) **and**
  GitLab CI vars (`RELEASE_KEYSTORE_B64` …) — so CI signs releases from any machine.
- Dashboard Xray control = **Restart only** (stopping Xray kills a proxied panel).
  Online list is grouped **by server** (main panel + each node queried directly),
  because the central API can't map online clients to inbounds.
- Editors are full-screen overlays in `MainScreen` (NOT `Dialog` — Dialogs don't
  get insets); Delete lives in a pinned `bottomBar`.

(Detailed rationale for every point above is in `docs/ANDROID-HANDOFF.md`.)
