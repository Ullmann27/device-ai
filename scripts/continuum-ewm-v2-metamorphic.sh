#!/usr/bin/env bash
set -Eeuo pipefail

# Safe, local and reproducible: no Android SDK, remote runner, or network calls.
ROOT="$(cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT"

if ! command -v git >/dev/null 2>&1 ||
   ! command -v java >/dev/null 2>&1 ||
   ! command -v kotlinc >/dev/null 2>&1; then
  printf 'M6_METAMORPHIC_NOT_RUN: missing git, java or kotlinc\n' >&2
  exit 3
fi

CANDIDATE_COMMIT=3ba1ba0bba28e441d2b85ae098371901d3db7407
if ! git merge-base --is-ancestor "$CANDIDATE_COMMIT" HEAD; then
  printf 'M6_METAMORPHIC_NOT_RUN: frozen commit not in branch ancestry\n' >&2
  exit 3
fi

LEXICON=app/src/main/java/at/heinz/deviceai/intelligence/ewm/Ewm1Lexicon.kt
MEMORY=app/src/main/java/at/heinz/deviceai/intelligence/ewm/Ewm1EvidenceWorkspace.kt
PROBE=app/src/test/java/at/heinz/deviceai/intelligence/ewm/M6MetamorphicInvariantProbe.kt

verify_blob () {
  local file="$1" expected="$2" observed
  [[ -f "$file" ]] || { printf 'M6_METAMORPHIC_MISSING: %s\n' "$file" >&2; exit 3; }
  observed="$(git hash-object -- "$file")"
  if [[ "$observed" != "$expected" ]]; then
    printf 'M6_METAMORPHIC_FROZEN_SOURCE_CHANGED: %s\n' "$file" >&2
    exit 3
  fi
}
verify_blob "$LEXICON" 8c1e6acd4a7ee2cf632b115550eb7c7d5c801df6
verify_blob "$MEMORY" cb19581af34ad177add22a89638988a74a9c7aad

tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
kotlinc "$LEXICON" "$MEMORY" "$PROBE" -include-runtime -d "$tmp/m6.jar"
java -Xmx384m -cp "$tmp/m6.jar" \
  at.heinz.deviceai.intelligence.ewm.M6MetamorphicInvariantProbe
