#!/usr/bin/env bash
set -euo pipefail
COCKPIT_TOOL_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
COCKPIT_SOURCE="$(cd -- "$COCKPIT_TOOL_DIR/../.." && pwd)"
COCKPIT_OUTPUT="${1:?Output directory required}"
shift
exec python3 "$COCKPIT_TOOL_DIR/preview.py" \
  --repo-root "$COCKPIT_SOURCE" --output "$COCKPIT_OUTPUT" \
  --kotlin-lib-dir "${KOTLIN_LIB_DIR:-${SCENE_KOTLIN_LIB:-}}" \
  --json-jar "${JSON_JAR:-${SCENE_JSON_JAR:-}}" "$@"
