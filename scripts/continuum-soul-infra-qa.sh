#!/usr/bin/env bash
# Independent read-only QA for Continuum native corpus and DeepSeek metadata/storage preflight.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$ROOT"
: "${CONTINUUM_SOUL_QA_SHA:?Exact Git SHA is required for pinned QA}"
ACTUAL="$(git rev-parse HEAD)"
if [[ "$ACTUAL" != "$CONTINUUM_SOUL_QA_SHA" ]]; then
  echo "BLOCKED_WRONG_SOURCE: expected $CONTINUUM_SOUL_QA_SHA, got $ACTUAL" >&2
  exit 2
fi
command -v node >/dev/null || { echo "BLOCKED_NO_NODE" >&2; exit 2; }
node -e 'if (Number(process.versions.node.split(".")[0]) < 20) process.exit(1)'
OUT="$ROOT/soul-infrastructure-qa"
mkdir -p "$OUT"
set +e
node --test scripts/continuum-native-corpus.test.mjs scripts/continuum-deepseek-tier-plan.test.mjs scripts/continuum-deepseek-hub-preflight.test.mjs >"$OUT/TESTS.txt" 2>&1
STATUS=$?
set -e
if [[ "$STATUS" -ne 0 ]]; then
  printf 'SOURCE=%s\nSTATUS=FAILED\n' "$ACTUAL" >"$OUT/STATUS.txt"
  cat "$OUT/TESTS.txt"
  exit "$STATUS"
fi
# Read only the official Hub metadata; network errors never become pass.
set +e
node scripts/continuum-deepseek-hub-preflight.mjs >"$OUT/HUB.txt" 2>"$OUT/HUB-ERROR.txt"
HUB_EXIT=$?
set -e
if [[ "$HUB_EXIT" -eq 0 ]]; then
  HUB_STATUS="METADATA_READ_ONLY_PINNED"
else
  HUB_STATUS="BLOCKED_HUB_METADATA"
fi
printf 'SOURCE=%s\nSTATUS=PASSED_NODE_TESTS_ONLY\nHUB_STATUS=%s\nANDROID_APK=NOT_BUILT\nMODEL_WEIGHTS=NOT_DOWNLOADED\nREAL_DEEPSEEK_INFERENCE=NOT_RUN\n' "$ACTUAL" "$HUB_STATUS" >"$OUT/STATUS.txt"
cat "$OUT/TESTS.txt"
echo "CONTINUUM_SOUL_INFRA_QA_PASSED=$ACTUAL"
