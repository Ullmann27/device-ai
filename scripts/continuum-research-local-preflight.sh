#!/usr/bin/env bash
# Local-only Continuum research integration preflight. Never signs, builds or installs an APK.
set -Eeuo pipefail
umask 077

root=$(git rev-parse --show-toplevel)
cd "$root"
branch=$(git symbolic-ref --quiet --short HEAD || true)
if [[ "$branch" == main || "$branch" == production || "$branch" == release/* ]]; then
  echo "RESEARCH_PREFLIGHT_REFUSED: productive or release branch" >&2
  exit 69
fi
git -c core.fileMode=false diff --quiet HEAD -- || {
  echo "RESEARCH_PREFLIGHT_REFUSED: tracked checkout is dirty" >&2
  exit 69
}
head_sha=$(git rev-parse --verify HEAD)
[[ "$head_sha" =~ ^[a-f0-9]{40}$ ]] || exit 69
out=${1:-$(mktemp -d "${TMPDIR:-/tmp}/continuum-research-gate-XXXXXXXX")}
mkdir -p "$out"
out=$(cd "$out" && pwd -P)
[[ "$out" != "$root" && "$out" != "$root/"* ]] || {
  echo "RESEARCH_PREFLIGHT_REFUSED: output inside repository" >&2
  exit 69
}
node --version > "$out/node-version.txt"
paths=(
  scripts/continuum-research-tap-summary.mjs
  scripts/continuum-research-tap-summary.test.mjs
  .github/continuum/research-evidence-gate.mjs
  .github/continuum/research-evidence-gate.test.mjs
  .github/continuum/research-repair-bridge.mjs
  .github/continuum/research-repair-bridge.test.mjs
  .github/continuum/ccc1-independent-verifier.mjs
  .github/continuum/repair-research-verifier.mjs
  app/src/main/java/at/heinz/deviceai/learning/interactive/ResearchFeedbackFileLock.kt
  app/src/main/java/at/heinz/deviceai/learning/interactive/ResearchFeedback.kt
  app/src/test/java/at/heinz/deviceai/learning/interactive/ResearchFeedbackMultiprocessTest.kt
)
sha256sum "${paths[@]}" > "$out/checked-source-sha256.txt"
tests=(
  scripts/continuum-research-tap-summary.test.mjs
  .github/continuum/research-evidence-gate.test.mjs
  .github/continuum/research-repair-bridge.test.mjs
  .github/continuum/repair-research-verifier.test.mjs
  .github/continuum/ccc1-independent-verifier.test.mjs
  .github/continuum/scoped-repair-pipeline.test.mjs
)
if env -u NODE_TEST_CONTEXT node --test --test-concurrency=2 "${tests[@]}" > "$out/node-tests.txt" 2>&1; then
  node scripts/continuum-research-tap-summary.mjs "$out/node-tests.txt" > "$out/node-summary.txt" || {
    echo "NODE_TAP_EVIDENCE_INVALID; output=$out/node-tests.txt" >&2
    exit 1
  }
  node_status=$(sed -n 's/^status=//p' "$out/node-summary.txt")
  [[ "$node_status" == ALL_EXECUTED_PASS || "$node_status" == PASS_WITH_UNEXECUTED_CASES ]] || {
    echo "NODE_TAP_EVIDENCE_INVALID: missing bounded status" >&2
    exit 1
  }
else
  echo "NODE_TESTS_FAILED; output=$out/node-tests.txt" >&2
  tail -n 70 "$out/node-tests.txt" >&2
  exit 1
fi
kotlin_status=NOT_RUN_MISSING_JVM_DEPS
if [[ -n "${CONTINUUM_FABRIC_JVM_DEPS:-}" ]]; then
  if bash scripts/continuum-feedback-review-jvm.sh "$out/kotlin-feedback" > "$out/kotlin-runner.log" 2>&1; then
    kotlin_status=PASS
  else
    echo "KOTLIN_JVM_TESTS_FAILED; output=$out/kotlin-runner.log" >&2
    tail -n 70 "$out/kotlin-runner.log" >&2
    exit 1
  fi
fi
printf 'schema=continuum-research-preflight-v1\nsource_sha=%s\nbranch=%s\nnode_tests=%s\nkotlin_jvm_tests=%s\nandroid_sdk=NOT_RUN\nowner_signing=NOT_RUN\nrelease_eligible=FALSE\n' \
  "$head_sha" "$branch" "$node_status" "$kotlin_status" > "$out/STATUS.txt"
cat "$out/STATUS.txt"
printf 'logs=%s\n' "$out"
