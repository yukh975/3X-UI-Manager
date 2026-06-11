---
name: local-build-toolchain
description: Local Android/iOS/macOS-desktop build toolchain state on this Apple-Silicon Mac
metadata:
  node_type: memory
  type: project
  originSessionId: 0568383f-1229-43b8-902c-fb643458f5dd
---

This Mac is **Apple Silicon** (supersedes the stale "this Mac is Intel, no iOS runtime" note in `docs/RESUME-NEXT-SESSION.md`). Set up 2026-06-09, extended 2026-06-11:

- **JDK 17 (arm64)** at `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` (Homebrew, keg-only). **JDK 17 (x86_64)** at `~/jdks/jdk-17.0.19+10/Contents/Home` (Temurin tarball; run via `arch -x86_64`) — for the Intel desktop build. Rosetta 2 installed.
- **Android SDK** at `/opt/homebrew/share/android-commandlinetools`: platform-35, build-tools 35.0.0, platform-tools, emulator. `adb` is NOT on PATH — use `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`.
- **Gradle env**: builds fail with "Unable to locate a Java Runtime" unless `JAVA_HOME` is exported (the `/tmp/xui_build_env.sh` helper is volatile — just export `JAVA_HOME` + `ANDROID_HOME`). Build: `cd android && ./gradlew :app:assembleDebug`.
- **gradle** 9.5.1 (Homebrew) only to regenerate the **8.10.2 wrapper** into `android/` (gitignored there); `kmp/` ships its committed wrapper.
- **Emulator AVD** `xui_pixel7` (Pixel 7, android-35 arm64), usually running headless (`-no-window`, swiftshader). Drive with `adb shell input` + `uiautomator dump`; field coordinates shift when the soft keyboard opens — re-dump before tapping. Dashboard cards need a live panel (URL+token given per session; never in repo).
- **KMP worktree**: branch `ios-app` is checked out as a git worktree at `/tmp/3xui-ios` — **volatile** (/tmp). If missing after a reboot: `git worktree prune && git worktree add /tmp/3xui-ios ios-app`.
- **macOS desktop packaging**: `kmp/scripts/package-macos.sh "<Apple Silicon|Intel>"` (on ios-app; details in the script + `docs/RESUME-NEXT-SESSION.md`). Needs `create-dmg` (brew-installed). Intel build = `arch -x86_64` + the x64 JDK above. Deliverables go to `~/Desktop` ("3X-UI Manager.app", "3X-UI Manager (Apple Silicon).dmg", "3X-UI Manager (Intel).dmg").
- **iOS — fully working**: Xcode 26.5, iOS 26.5 Simulator runtime, license accepted. Build: `cd kmp && xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath /tmp/xui-ios-dd CODE_SIGNING_ALLOWED=NO build` (needs JAVA_HOME+ANDROID_HOME). Simulator device `xui-ios`; bundle id `net.yukh.xui.ios`; auto-login via `defaults write net.yukh.xui.ios xui.baseUrl/xui.token`. Gotcha: `simctl install` may silently keep the old app — `simctl uninstall` first if the version looks stale. `CADisableMinimumFrameDurationOnPhone` already in Info.plist (Compose MP aborts without it).
- **Gotcha**: installing Xcode switched the active toolchain, breaking `go run` cgo; `backend` launch.json pins `DEVELOPER_DIR=/Library/Developer/CommandLineTools`.
- **Passwordless sudo** at `/etc/sudoers.d/claude-nopasswd` (user keeps it intentionally).
- Finder caches DMG volume window geometry per volume name — a rebuilt DMG may open at a stale window size locally (`defaults delete com.apple.finder FXDesktopVolumePositions; killall Finder` resets); fresh machines are unaffected.

See [[upstream-sync-workflow]].
