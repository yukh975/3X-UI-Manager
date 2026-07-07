# Release runbook

How a 3X-UI Manager release is built, published and propagated. Self-contained —
clone the repo anywhere and follow it. No secrets live here, only *paths* to
local token files (chmod 600, never committed).

## Topology

```
[ dev machine ] ──git push──> [ home GitLab (canonical) ]
                                        │
                          push-mirror (automatic, every push)
                                        ▼
                              [ github.com/yukh975/3X-UI-Manager ]
                              (public buffer: F-Droid source + Binaries)
```

- **Canonical:** home GitLab, project `yukh/3X-UI-Manager`. Everything is pushed
  ONLY here (`origin`). The in-app updater checks this host's releases, so every
  release must exist here with its assets.
- **GitHub** is a read-only public mirror kept ONLY for F-Droid (its metadata's
  `Repo:`/`SourceCode:`/`Binaries:` point there) — an always-online buffer so
  F-Droid never depends on the home server's uptime. The push-mirror (home GitLab
  → Settings → Repository → Mirroring) propagates all branches + tags on every
  push; never `git push github` by hand. GitHub has no Actions, so mirrored tags
  trigger nothing there.
- **gitlab.com** hosts NOTHING of ours except the F-Droid catalog fork
  (`yukh975/fdroiddata`) — see `fdroid-mr-runbook.md`. An app mirror there was
  tried and dropped. Lesson learned: a fresh GitLab mirror target must have CI/CD
  disabled (`builds_access_level: disabled`) BEFORE the first tag-carrying sync,
  or its default CI tries to build every historical tag and floods failure mail.

## What is automatic on a `v*` tag

The home CI (`.gitlab-ci.yml`) runs:
1. `build:release` — signed `standard`-flavor APK → home generic package
   `xui-android/<ver>`.
2. `release:publish` — home GitLab Release with changelog section + APK link.
3. `publish:github` — get-or-creates the GitHub release for the same tag and
   uploads the signed standard APK (coexists with the hand-published
   `fdroid.apk`; never overwrites an existing release body). Token = masked +
   protected CI variable `GITHUB_TOKEN`; `v*` tags are protected so it reaches
   the pipeline. `allow_failure: true` — a mirror hiccup must not fail the
   canonical release.

## What stays manual (and why)

- **`fdroid.apk`** — the `fdroid`-flavor APK, built LOCALLY and uploaded to the
  GitHub release. Deliberately not in CI: F-Droid byte-compares this exact file
  against its own from-source build (reproducible builds), and the local
  toolchain is the verified-reproducible one. Skipping this step means that
  version never ships on F-Droid.
- **`.ipa`** — no macOS CI runner exists; built locally per
  `ios-release-packaging` (memory) / the steps below.

## Per-release checklist

Versioning policy: features bump the minor octet and set patch to 1
(0.5.7 → 0.6.1); bugfix-only releases bump the patch. versionCode scheme:
`major*1_000_000 + minor*10_000 + patch*100`.

1. **Bump versions (literals!):**
   - `main`: `app/build.gradle.kts` → `versionCode` + `versionName`
     (F-Droid's scanner reads the literals; never derive them from CI/git).
   - `apple` branch: `iosApp/iosApp/Info.plist`
     (CFBundleShortVersionString/CFBundleVersion) +
     `composeApp/src/desktopMain/.../Platform.desktop.kt`.
2. **Changelogs:** `CHANGELOG.md` + `CHANGELOG.ru.md` section `## [<ver>]`
   (single-line bullets — the in-app dialog reflows), plus
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
   (this is the "what's new" text F-Droid shows).
3. **Push:** commit → `git push origin main` (and `origin apple`). The mirror
   fans out to GitHub by itself.
4. **Tag:** `git tag v<ver> && git push origin v<ver>` → home CI does everything
   in "What is automatic" above.
5. **fdroid.apk (mandatory for F-Droid):**
   `./gradlew :app:assembleFdroidRelease` with the release keystore
   (`~/.config/3x-ui-android-keystore/`), verify the cert
   (`apksigner verify --print-certs` → SHA-256 `668cdb5c…`), upload the APK as
   asset **`fdroid.apk`** to the GitHub release `v<ver>`
   (`gh release upload` or the uploads API with `~/.gh-token`).
6. **While the fdroiddata MR is still open:** update its metadata for the new
   version (see `fdroid-mr-runbook.md`). After the merge this step disappears —
   F-Droid picks up tags automatically.
7. **iOS (.ipa):** on the `apple` worktree —
   `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration
   Release -sdk iphoneos -destination 'generic/platform=iOS' -derivedDataPath
   build/ipa CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO
   CODE_SIGN_IDENTITY= CODE_SIGN_ENTITLEMENTS= build`, wrap `Payload/` into
   `3x-ui-manager-<ver>.ipa`, upload to the home generic package
   `xui-ios/<ver>`, add the release asset link + a `### Download` line to the
   home release, verify the download sha256 round-trip, then
   `git tag ios-v<ver> && git push origin ios-v<ver>`.
8. **Verify:** home release has APK + .ipa; GitHub release has the standard APK
   + `fdroid.apk`; `git ls-remote github` shows the new tags.

## Tokens (local files only, chmod 600 — never in the repo)

| File | What | Used for |
|---|---|---|
| `~/.gl-token` | home GitLab PAT (admin) | releases/packages API, CI admin |
| `~/.gh-token` | GitHub fine-grained PAT (Contents RW on the repo) | `fdroid.apk` upload; also stored server-side in the push-mirror + `GITHUB_TOKEN` CI var |
| `~/.gl-com-token` | gitlab.com PAT (scope `api`) | fdroiddata MR management only |
| `~/.gl-notify-token` | home GitLab project-bot token (`yukh/notifications`) | MR-monitor notification issues |

## Flavors

- `standard` — home-GitLab build; in-app self-updater ON (checks home releases,
  downloads + installs the APK).
- `fdroid` — updater OFF (`IN_APP_UPDATER=false`) + `REQUEST_INSTALL_PACKAGES`
  stripped; F-Droid manages updates. Both flavors are signed by the same release
  key, so users can switch channels by installing one build over the other —
  no reinstall, no data loss.
- Reproducibility relies on two AGP opt-outs in `app/build.gradle.kts`:
  `vcsInfo { include = false }` (release buildType) and
  `dependenciesInfo { includeInApk = false; includeInBundle = false }` — do not
  remove them, F-Droid's byte-match and `check apk` both fail otherwise.
