#!/usr/bin/env bash
# Build the macOS desktop .app + DMG with the real app version, ad-hoc
# signature, install README and a Finder layout where everything is visible.
#
# The CPU arch of the result = the arch of the JDK that runs Gradle:
#   Apple Silicon:  JAVA_HOME=<arm64 jdk> ./scripts/package-macos.sh "Apple Silicon"
#   Intel:          JAVA_HOME=<x64 jdk>  arch -x86_64 ./scripts/package-macos.sh "Intel"
#
# Why the post-build plist patch: jpackage refuses a CFBundleShortVersionString
# with MAJOR=0 (so Gradle's packageVersion is pinned at 1.0.0), but Finder's
# Get Info reads exactly that key — patch it to the real app version after the
# build, then re-sign.
#
# Requires: create-dmg (brew install create-dmg).
set -euo pipefail
cd "$(dirname "$0")/.."

ARCH_LABEL="${1:-$( [ "$(uname -m)" = arm64 ] && echo "Apple Silicon" || echo "Intel" )}"

VERSION=$(sed -n 's/.*appVersionName(): String = "\(.*\)".*/\1/p' \
  composeApp/src/desktopMain/kotlin/net/yukh/xui/app/Platform.desktop.kt)
[ -n "$VERSION" ] || { echo "cannot read appVersionName from Platform.desktop.kt" >&2; exit 1; }

echo "==> building $ARCH_LABEL package, app version $VERSION (JDK: $(java -version 2>&1 | head -1))"
# Wipe ALL of build/compose: tmp/main/runtime caches the jlink runtime image,
# and a stale one from the other arch gets bundled silently (arm64 JVM inside
# an x86_64 launcher → won't start on Intel).
rm -rf composeApp/build/compose
./gradlew :composeApp:createDistributable

APP="composeApp/build/compose/binaries/main/app/3X-UI Manager.app"

# Sanity: the bundled JVM must match the launcher arch.
LAUNCHER_ARCH=$(file "$APP/Contents/MacOS/3X-UI Manager" | grep -oE 'x86_64|arm64' | head -1)
JVM_ARCH=$(file "$APP/Contents/runtime/Contents/Home/lib/server/libjvm.dylib" | grep -oE 'x86_64|arm64' | head -1)
if [ "$LAUNCHER_ARCH" != "$JVM_ARCH" ]; then
  echo "ARCH MISMATCH: launcher=$LAUNCHER_ARCH jvm=$JVM_ARCH" >&2; exit 1
fi
echo "==> arch check ok: $LAUNCHER_ARCH"
/usr/libexec/PlistBuddy -c "Set :CFBundleShortVersionString $VERSION" "$APP/Contents/Info.plist"
codesign --force --deep --sign - "$APP"

STAGE=$(mktemp -d)
cp -R "$APP" "$STAGE/"
cp composeApp/packaging/KAK-USTANOVIT.ru.txt "$STAGE/КАК УСТАНОВИТЬ.txt"

OUT="composeApp/build/compose/binaries/main/dmg/3X-UI Manager ($ARCH_LABEL).dmg"
mkdir -p "$(dirname "$OUT")"
rm -f "$OUT"
create-dmg \
  --volname "3X-UI Manager" \
  --window-pos 220 120 \
  --window-size 680 480 \
  --icon-size 100 \
  --icon "3X-UI Manager.app" 175 195 \
  --app-drop-link 505 195 \
  --icon "КАК УСТАНОВИТЬ.txt" 340 380 \
  --no-internet-enable \
  "$OUT" "$STAGE"
rm -rf "$STAGE"
echo "==> done: $OUT"
