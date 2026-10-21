#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
gradle=app/build.gradle.kts
code=$(sed -n 's/^ *versionCode = \([0-9]*\)$/\1/p' "$gradle")
# The build counter continues the 76 v0.x releases cut before it existed.
if [ "$code" -ge 1000000 ]; then build=$((code - 1000000 + 1)); else build=77; fi
date=$(date '+%y %m %d' | awk '{ printf "%d.%d.%d", $1, $2, $3 }')
name="$date build $build"
code=$((1000000 + build))
perl -pi -e "s/^( *versionCode = )\d+\$/\${1}$code/; s/^( *versionName = )\"[^\"]*\"\$/\${1}\"$name\"/" "$gradle"
echo "versionName $name"
echo "versionCode $code  (fastlane changelog: fastlane/metadata/android/en-US/changelogs/$code.txt)"
echo "tag         v${date}b$build"
