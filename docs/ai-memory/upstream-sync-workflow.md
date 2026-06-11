---
name: upstream-sync-workflow
description: How to update the 3x-ui fork to a new upstream release across all branches
metadata: 
  node_type: memory
  type: project
  originSessionId: 0568383f-1229-43b8-902c-fb643458f5dd
---

The fork's `main` tracks upstream **MHSanaei/3x-ui** (`upstream` remote = https://github.com/MHSanaei/3x-ui.git, pull-only; on a fresh machine the remote isn't configured — add it). `main` is a strict upstream mirror with **no fork-only commits**, so when the user says "панель обновилась до vX, обнови во всех ветках":

1. `main`: fast-forward to the upstream tag — `git merge --ff-only vX`.
2. `android-app` (= `main` + the Android app in `android/`): `git merge main`.
3. `ios-app` (= `android-app` + `kmp/`): `git merge android-app`.

Merges are near-clean — app code (`android/`, `kmp/`, `docs/`, `CLAUDE.md`) and panel code (`web/`, `frontend/`, `database/`…) are disjoint; only `.gitignore` and root `README.md` ever overlap and auto-resolve. Push each branch to `origin`.

**Watch for breaking API changes** that relocate endpoints the app calls. v3.3.0 (upstream commit `c6f15cd5`) moved `/panel/setting/*` + `/panel/xray/*` under `/panel/api/*` (now Bearer-or-session, not session-only) — needed updating `android/.../data/api/XuiApi.kt` (`panel/api/setting/all`, `panel/api/xray/`, `panel/api/xray/update`). `kmp/.../PanelApi.kt` only uses `/panel/api/*` already, so it was unaffected.

Last synced: **upstream v3.3.0 on 2026-06-09**. See [[local-build-toolchain]].
