#!/usr/bin/env bash
set -euo pipefail
repo=$(cd "$(dirname "$0")/../.." && pwd)
out=${1:?new absolute output directory required}
case "$out" in /*) ;; *) exit 2;; esac
mkdir "$out"
mkdir "$out/source"
source_sha=d8a927dd6ced4dea489e2042afb4b5624157fa3b
git -C "$repo" archive "$source_sha" .github/continuum scripts tools/momega1-memory-review | tar -x -C "$out/source"
printf '%s\n' "$source_sha" > "$out/source.txt"
set +e
(
 cd "$out/source" || exit 1
 MOMEGA1_CANDIDATE_ROOT="$out/source" node --import ./tools/momega1-memory-review/tests/caller-brand-loader.mjs --test --test-reporter=tap tools/momega1-memory-review/tests/caller-accounting.test.mjs
) > "$out/caller.tap" 2>&1
caller_exit=$?
(
 cd "$out/source" || exit 1
 node --test --test-reporter=tap .github/continuum/coding-orchestrator.test.mjs .github/continuum/coding-provider.test.mjs .github/continuum/coding-rejection-feedback.test.mjs .github/continuum/coding-time-budget.test.mjs .github/continuum/momega1-loop.test.mjs .github/continuum/ccc1-technical-recall.test.mjs .github/continuum/ccc1-experience-durability.test.mjs scripts/continuum-local-model-native.test.mjs
) > "$out/uninstrumented.tap" 2>&1
canonical_exit=$?
set -e
printf '%s\n' "$caller_exit" > "$out/caller.exit"
printf '%s\n' "$canonical_exit" > "$out/uninstrumented.exit"
test "$caller_exit" -eq 0 && test "$canonical_exit" -eq 0
