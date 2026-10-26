#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="$ROOT/app/src/main/java/at/heinz/deviceai/LocalFirstPolicy.kt"
RUNTIME="$ROOT/app/src/main/java/at/heinz/deviceai/localmodel/LiteRtLmLocalModelRuntime.kt"
SMOKE="$ROOT/scripts/verification/ContinuumDialogueRoutingSmoke.kt"

command -v kotlinc >/dev/null || { printf '%s\n' "UNAVAILABLE: kotlinc" >&2; exit 2; }
command -v java >/dev/null || { printf '%s\n' "UNAVAILABLE: java" >&2; exit 2; }
test -f "$SOURCE" && test -f "$RUNTIME" && test -f "$SMOKE" ||
    { printf '%s\n' "UNAVAILABLE: missing exact app Kotlin sources" >&2; exit 2; }

STATE_LINE="$(grep -m 1 '^enum class LocalModelState ' "$RUNTIME" || true)"
if [[ "$STATE_LINE" != 'enum class LocalModelState { EMPTY, LOADING, READY, GENERATING, UNLOADING, FAILED }' ]]; then
    printf '%s\n' "UNAVAILABLE: real LocalModelState declaration changed; review harness" >&2
    exit 2
fi

TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT
printf 'package at.heinz.deviceai.localmodel\n%s\n' "$STATE_LINE" > "$TEMP/LocalModelState.kt"

printf 'SOURCE_SHA256='
sha256sum "$SOURCE" | cut -d' ' -f1
kotlinc -jvm-target 17 "$SOURCE" "$TEMP/LocalModelState.kt" "$SMOKE" \
    -include-runtime -d "$TEMP/routing-smoke.jar"
java -jar "$TEMP/routing-smoke.jar"
printf '%s\n' "LIMIT: host-only route test, NOT Android Gradle / Fold7 / model inference"
