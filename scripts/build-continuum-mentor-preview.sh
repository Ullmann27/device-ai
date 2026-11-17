#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

for cmd in git npm node java gradle sha256sum; do
  command -v "$cmd" >/dev/null 2>&1 || {
    printf 'Missing required command: %s\n' "$cmd" >&2
    exit 2
  }
done

if [[ -z "${ANDROID_HOME:-}" || ! -d "${ANDROID_HOME}/platforms/android-36" ]]; then
  echo "Android SDK 36 is missing. Set ANDROID_HOME to a fully installed Android SDK." >&2
  exit 2
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "The Git checkout contains uncommitted changes; refusing an untraceable release." >&2
  exit 2
fi

SHA="$(git rev-parse HEAD)"
printf 'Building Device AI Mentor alongside installed Sol preview from %s\n' "$SHA"

npm ci --prefix backend --ignore-scripts --no-audit --no-fund
npm --prefix backend run check
npm --prefix backend test
node --test .github/continuum/*.test.mjs

gradle :app:testDebugUnitTest --stacktrace
gradle :app:lintDebug --stacktrace
gradle :app:assembleDebug -PdeviceAiMentorPreview=true --stacktrace

APK="app/build/outputs/apk/debug/app-debug.apk"
[[ -s "$APK" ]] || { echo "Gradle produced no APK." >&2; exit 3; }

BUILD_TOOLS="${ANDROID_HOME}/build-tools/35.0.0"
[[ -x "$BUILD_TOOLS/apksigner" && -x "$BUILD_TOOLS/aapt" ]] || {
  echo "Android build-tools 35.0.0 are required for signature and package verification." >&2
  exit 3
}

"$BUILD_TOOLS/apksigner" verify --verbose "$APK"
"$BUILD_TOOLS/aapt" dump badging "$APK" |
  grep -F "package: name='at.heinz.deviceai.mentorpreview'"

DEST="$ROOT/build-evidence/continuum-mentor-preview"
mkdir -p "$DEST"
cp "$APK" "$DEST/DeviceAI-Continuum-Mentor-Preview.apk"
printf '%s\n' "$SHA" > "$DEST/COMMIT.txt"
(
  cd "$DEST"
  sha256sum DeviceAI-Continuum-Mentor-Preview.apk > SHA256SUMS.txt
)
printf 'Verified parallel APK generated: %s\n' "$DEST/DeviceAI-Continuum-Mentor-Preview.apk"
