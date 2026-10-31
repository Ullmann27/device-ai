#!/usr/bin/env bash
set -Eeuo pipefail

# Run frozen M6 EWM2 source on untouched 240-pair corpus.
# Does not mutate owner facts, training weights, Gold labels or production main.
ROOT="$(cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT"

CANDIDATE_SHA=3ba1ba0bba28e441d2b85ae098371901d3db7407
RECEIPTS="$ROOT/app/build/reports/ewm-m6/ewm-v2-foreign-240-receipts.jsonl"

if ! command -v node >/dev/null 2>&1; then
  echo 'M6_BLOCKED: node unavailable' >&2
  exit 3
fi
if ! command -v git >/dev/null 2>&1; then
  echo 'M6_BLOCKED: git unavailable' >&2
  exit 3
fi
if ! git merge-base --is-ancestor "$CANDIDATE_SHA" HEAD; then
  echo 'M6_BLOCKED: frozen candidate commit is not an ancestor of checkout' >&2
  exit 3
fi
if [[ -x "$ROOT/gradlew" ]]; then
  GRADLE="$ROOT/gradlew"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE="$(command -v gradle)"
else
  echo 'M6_NOT_RUN: Android Gradle execution unavailable; no model result' >&2
  exit 3
fi

node "$ROOT/scripts/continuum-ewm-v2-holdout-integrity.mjs"
rm -f -- "$RECEIPTS"
"$GRADLE" -p "$ROOT" :app:testDebugUnitTest \
  --tests "at.heinz.deviceai.intelligence.ewm.Ewm2Foreign240ReceiptTest" \
  --console=plain --no-daemon
if [[ ! -s "$RECEIPTS" ]]; then
  echo 'M6_NOT_RUN: no Kotlin EWM2 receipts were exported' >&2
  exit 3
fi

node "$ROOT/scripts/continuum-ewm-v2-holdout-score.mjs" \
  --receipts "$RECEIPTS" --candidate-sha "$CANDIDATE_SHA"
