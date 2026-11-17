#!/usr/bin/env bash
set -euo pipefail
repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
command -v kotlinc >/dev/null || { echo 'KOTLINC_UNAVAILABLE' >&2; exit 2; }
command -v java >/dev/null || { echo 'JAVA_UNAVAILABLE' >&2; exit 2; }
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
root="$repo/app/src/main/java/at/heinz/deviceai/cognitive"
kotlinc "$root/NativeAppLookup.kt" "$root/JarvisVoiceProfile.kt" \
    "$root/OwnerAppLookupIntent.kt" "$repo/tools/dialog-vi-host/ContractChecks.kt" \
    -include-runtime -d "$tmp/contract-checks.jar"
java -jar "$tmp/contract-checks.jar"
echo 'HOST_PURE_KOTLIN_PASS; ANDROID_GRADLE_NOT_RUN; FOLD7_DEVICE_NOT_RUN'
