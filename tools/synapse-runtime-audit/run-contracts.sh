#!/usr/bin/env bash
set -euo pipefail
# Review only. Archives explicit source objects; never reads PR413 task material.
repo=$(cd "$(dirname "$0")/../.." && pwd)
out=${1:?new evidence/work directory required}
mkdir "$out"
mkdir "$out/source" "$out/reviewer"
chief=5684efb1a2804fe28bbb0b52dc3904e1745afef1
runtime=c78cf8a551a4d12f5dbb27ec3641fd4ae9e6f76d
review=88a6e2fb1ce52ab090a8c6b13de7cd9bad4a7aaf
tree=$(git -C "$repo" merge-tree --write-tree "$chief" "$runtime")
test "$tree" = 5d7c6bb534ea01b0f9ab87ee3afea4c5204ec315
git -C "$repo" archive "$tree" .github/continuum scripts tools/momega1-memory-review | tar -x -C "$out/source"
git -C "$repo" archive "$review" tools/momega1-challenger/provider-contract.test.mjs tools/momega1-challenger/runtime-review.test.mjs tools/momega1-challenger/reviewed-pair-contract.test.mjs tools/momega1-challenger/genuine-brand-forgery.test.mjs tools/momega1-challenger/synthetic-runtime.c | tar -x -C "$out/reviewer"
printf '%s\n' "$chief" "$runtime" "$review" "$tree" > "$out/sources.txt"
node --version > "$out/node-version.txt"
set +e
MOMEGA1_TARGET="$out/source" MOMEGA1_REVIEWED_RUNTIME_SHA256=c2c13a2a27c5a85137b6d083074fa83e0d990db129e715f8452352e3730cbf5a MOMEGA1_EXPECT_REVIEWED=1 node --test --test-reporter=tap "$out/reviewer/tools/momega1-challenger/provider-contract.test.mjs" "$out/reviewer/tools/momega1-challenger/runtime-review.test.mjs" "$out/reviewer/tools/momega1-challenger/reviewed-pair-contract.test.mjs" "$out/reviewer/tools/momega1-challenger/genuine-brand-forgery.test.mjs" > "$out/runtime.tap" 2>&1
runtime_exit=$?
(
 cd "$out/source" || exit 1
 MOMEGA1_CANDIDATE_ROOT="$out/source" node --import ./tools/momega1-memory-review/tests/fixture-tier-compat.mjs --test --test-reporter=tap tools/momega1-memory-review/tests/git-lineage.test.mjs tools/momega1-memory-review/tests/recall-ablation.test.mjs
) > "$out/lineage.tap" 2>&1
lineage_exit=$?
(
 cd "$out/source" || exit 1
 node --test --test-reporter=tap .github/continuum/coding-provider.test.mjs .github/continuum/coding-rejection-feedback.test.mjs .github/continuum/coding-time-budget.test.mjs .github/continuum/momega1-loop.test.mjs .github/continuum/ccc1-technical-recall.test.mjs .github/continuum/ccc1-experience-durability.test.mjs scripts/continuum-local-model-native.test.mjs
) > "$out/composed.tap" 2>&1
composed_exit=$?
set -e
printf '%s\n' "$runtime_exit" > "$out/runtime.exit"
printf '%s\n' "$lineage_exit" > "$out/lineage.exit"
printf '%s\n' "$composed_exit" > "$out/composed.exit"
test "$runtime_exit" -eq 0 && test "$lineage_exit" -eq 0 && test "$composed_exit" -eq 0
