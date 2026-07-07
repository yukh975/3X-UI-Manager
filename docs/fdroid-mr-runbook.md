# F-Droid submission runbook

Operational notes for shepherding **3X-UI Manager** (`net.yukh.xui`) into the
F-Droid catalog. Self-contained so it can be picked up from any machine (this
file lives in the repo — clone it anywhere). No secrets are stored here.

## Status

- **MR:** <https://gitlab.com/fdroid/fdroiddata/-/merge_requests/42307> — CI fully green (9/9, incl. reproducible `fdroid build` that byte-matches the published APK, and `check apk`); F-Droid will ship the developer-signed APK; awaiting maintainer merge.
- **Current release:** v0.8.8 (commit `497d7632f16f714b9124d228035e250b5a5117d2`).
- **Target project** `fdroid/fdroiddata` id **36528**.
- **Fork** `yukh975/fdroiddata` id **84185961**, branch **`add-net-yukh-xui`**.
- **App source mirror:** <https://github.com/yukh975/3X-UI-Manager> (default branch `main`, tags `v*`).
- **App id / version:** `net.yukh.xui`, 0.8.8 / versionCode 80800.
- **Reproducible builds:** F-Droid ships the developer-signed APK (signing cert SHA-256 `668cdb5cd9deb1798e1b5a0ac126dba3b1b36b0da2062c64309aeb53cf8f964a`). Each release must publish the `fdroid`-flavor APK as `fdroid.apk` on the matching GitHub release (the `Binaries:` URL).
- gitlab.com account **yukh975** (validated → CI allowed).

## Why F-Droid

Google Play is blocked (identity verification fails: the Play account country is
Serbia and can't be changed, but only RF documents are available; plus Google's
Russia sanctions). RuStore was ruled out (VK-owned). The app was open-sourced
under MIT with a dedicated `fdroid` build flavor (no in-app updater, no
`REQUEST_INSTALL_PACKAGES`) so F-Droid can build it from source.

## Prerequisite on any machine

A gitlab.com personal access token with scope `api`, saved to `~/.gl-com-token`
(chmod 600). Create one at
<https://gitlab.com/-/user_settings/personal_access_tokens>.

## Check the MR status

```bash
TOK=$(tr -d '[:space:]' < ~/.gl-com-token) python3 - <<'PY'
import os, json, urllib.request
TOK = os.environ['TOK']; GL = "https://gitlab.com/api/v4"
def api(p):
    return json.load(urllib.request.urlopen(
        urllib.request.Request(GL + p, headers={'PRIVATE-TOKEN': TOK})))
mr = api("/projects/36528/merge_requests/42307")
print("state:", mr["state"], "| merge:", mr.get("detailed_merge_status"), "| labels:", mr.get("labels"))
notes = [n for n in api("/projects/36528/merge_requests/42307/notes?per_page=100") if not n.get("system")]
print("human comments:", len(notes))
for n in notes[-5:]:
    print(" -", n["author"]["username"] + ":", n["body"][:200].replace("\n", " "))
PY
```

## Apply a maintainer-requested metadata change

Edit `metadata/net.yukh.xui.yml` (content below), validate with `fdroid lint`,
then PUT it to the fork branch — the open MR updates automatically:

```bash
TOK=$(tr -d '[:space:]' < ~/.gl-com-token) python3 - <<'PY'
import os, json, urllib.request
TOK = os.environ['TOK']; GL = "https://gitlab.com/api/v4"; FORK = 84185961; BR = "add-net-yukh-xui"
content = open("net.yukh.xui.yml").read()  # the edited metadata file
req = urllib.request.Request(
    f"{GL}/projects/{FORK}/repository/files/metadata%2Fnet.yukh.xui.yml",
    method="PUT", headers={'PRIVATE-TOKEN': TOK, 'Content-Type': 'application/json'},
    data=json.dumps({"branch": BR, "content": content,
                     "commit_message": "net.yukh.xui: address review"}).encode())
print(urllib.request.urlopen(req).status)
PY
```

To validate locally: `pip install fdroidserver` in a venv, seed a `config.yml`
whose `categories:` list comes from fdroiddata's `config/categories.yml`, then
`fdroid lint net.yukh.xui`.

## Re-trigger the fork CI

Reopening the MR does **not** start a pipeline — only a **push to the branch**
does (a metadata edit is such a push). fdroiddata's verification jobs
(`app_verification_rules`: lint/build) run on fork branches and MRs; they are not
gated to the canonical project. Inspect:
`/projects/84185961/pipelines?ref=add-net-yukh-xui` → `/pipelines/<id>/jobs`.

## Future releases

New `v*` tags on the mirror are auto-picked up by F-Droid once the app is in the
catalog (`UpdateCheckMode: Tags`, `AutoUpdateMode: Version`). Keep the GitHub
mirror in sync: `git push github main --tags`.

## What F-Droid requires (learned in review)

1. **`versionCode`/`versionName` must be plain literals** in `app/build.gradle.kts`
   `defaultConfig` (a CI/git-derived value makes `fdroid build` produce the wrong
   code and `checkupdates` find nothing). Bump both on every release. A GitLab tag
   pipeline may still override them on lines that don't match F-Droid's scanner.
2. **Store title/summary/description live in fastlane**
   (`fastlane/metadata/android/en-US/…`), not the `.yml`.
3. **`Builds.commit` is the full 40-char hash**, not a tag/branch.
4. **Keep `AutoName`** — `checkupdates` regenerates it and fails on the diff if absent.
5. **`AutoUpdateMode: Version`** (not `Version v%v`).
6. Validate: `fdroid rewritemeta net.yukh.xui` + `fdroid lint net.yukh.xui`.

### Reproducible builds (so F-Droid ships the dev-signed APK)

The `Binaries:` + `AllowedAPKSigningKeys:` fields make F-Droid build from
source and byte-compare against the developer's published APK; if they match it
ships *your* signed APK instead of re-signing. Two AGP defaults break the
byte-match and must be disabled **on the release build type / android block** in
`app/build.gradle.kts` (both already in place):

7. **`vcsInfo { include = false }`** (release buildType) — drops
   `META-INF/version-control-info.textproto`, which embeds the git commit hash
   and varies per checkout. (The Gradle property `android.enableVcsInfo=false`
   does **not** work; use the DSL block.)
8. **`dependenciesInfo { includeInApk = false; includeInBundle = false }`**
   (android block) — drops the Play-oriented "dependency metadata" block that
   AGP adds *inside the APK signing block*. F-Droid's `check apk` scanner rejects
   it ("Found extra signing block 'Dependency metadata'").

Per release: build `:app:assembleFdroidRelease` with the release keystore,
publish the APK as **`fdroid.apk`** on the GitHub release for the tag, and keep
`Binaries:` pointing at `…/releases/download/v%v/fdroid.apk`. The
`AllowedAPKSigningKeys` value is the signing cert's SHA-256
(`apksigner verify --print-certs`). The `Binaries` line must stay ≤ ~90 chars or
older `fdroid rewritemeta` wraps it and the CI check fails — hence the short
`fdroid.apk` asset name.

## Current metadata (`metadata/net.yukh.xui.yml`)

```yaml
Categories:
  - Remote Access
  - VPN & Proxy
License: MIT
AuthorName: Yuriy Khachaturian
SourceCode: https://github.com/yukh975/3X-UI-Manager
IssueTracker: https://github.com/yukh975/3X-UI-Manager/issues

AutoName: 3X-UI Manager

RepoType: git
Repo: https://github.com/yukh975/3X-UI-Manager.git
Binaries: https://github.com/yukh975/3X-UI-Manager/releases/download/v%v/fdroid.apk

Builds:
  - versionName: 0.8.8
    versionCode: 80800
    commit: 497d7632f16f714b9124d228035e250b5a5117d2
    subdir: app
    gradle:
      - fdroid

AllowedAPKSigningKeys: 668cdb5cd9deb1798e1b5a0ac126dba3b1b36b0da2062c64309aeb53cf8f964a

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9]
CurrentVersion: 0.8.8
CurrentVersionCode: 80800
```
