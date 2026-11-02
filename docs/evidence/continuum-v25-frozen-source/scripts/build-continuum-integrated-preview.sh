#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

for program in git node npm java gradle sha256sum; do
  if ! command -v "$program" >/dev/null 2>&1; then
    printf 'Missing required build command: %s\n' "$program" >&2
    exit 2
  fi
done

if [[ -z "${ANDROID_HOME:-}" || ! -d "${ANDROID_HOME}/platforms/android-36" ]]; then
  echo "Android SDK 36 is required at ANDROID_HOME; no verified APK can be built here." >&2
  exit 2
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Refusing to label an APK reproducible from a dirty checkout." >&2
  exit 2
fi

SHA="$(git rev-parse HEAD)"
echo "Building verified Continuum preview from: $SHA"

npm ci --prefix backend --ignore-scripts --no-audit --no-fund
npm --prefix backend run check
npm --prefix backend test
node --test .github/continuum/*.test.mjs

gradle :app:testDebugUnitTest --stacktrace
gradle :app:lintDebug --stacktrace
gradle :app:assembleDebug -PdeviceAiPreview=true --stacktrace

APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -s "$APK" ]]; then
  echo "No Android preview APK was emitted by Gradle." >&2
  exit 3
fi

TOOLS="${ANDROID_HOME}/build-tools/35.0.0"
"$TOOLS/apksigner" verify --verbose "$APK"
"$TOOLS/aapt" dump badging "$APK" | grep -F "package: name='at.heinz.deviceai.preview'"

DEST="$ROOT/build-evidence/continuum-integrated-preview"
mkdir -p "$DEST"
cp "$APK" "$DEST/DeviceAI-Continuum-Preview.apk"
printf '%s\n' "$SHA" > "$DEST/COMMIT.txt"
(cd "$DEST" && sha256sum DeviceAI-Continuum-Preview.apk > SHA256SUMS.txt)
printf 'APK created and signature verified.\nEvidence: %s\n' "$DEST"
