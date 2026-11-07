#!/bin/bash
set -euo pipefail
qa_sha=${1:?};qa_out=${2:?}
[[ "$(git rev-parse HEAD)" == "$qa_sha" && ! -e "$qa_out" ]] || exit 69
git diff --quiet HEAD -- app scripts .github tools backend || exit 69
source /workspace/continuum-build-env/env.sh
export CONTINUUM_FABRIC_JVM_DEPS=/workspace/continuum-build-env/native-graph-deps
mkdir -p "$qa_out"
{ git rev-parse HEAD; git rev-parse HEAD^{tree}; node --version; printf 'CONTINUUM_FABRIC_JVM_DEPS=%s\n' "$CONTINUUM_FABRIC_JVM_DEPS"; date -u +%FT%TZ; } > "$qa_out/source-environment.txt"
set +e
node --test --test-reporter=tap --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs tools/continuum-virtual-fabric/*.test.mjs > "$qa_out/node-full.tap" 2>&1
qa_node=$?
printf 'exit_code=%s\n' "$qa_node" > "$qa_out/node-full.exit"
node --test --test-reporter=tap backend/test/*.test.js > "$qa_out/backend.tap" 2>&1
qa_backend=$?
printf 'exit_code=%s\n' "$qa_backend" > "$qa_out/backend.exit"
[[ "$qa_node" == 0 && "$qa_backend" == 0 ]]
