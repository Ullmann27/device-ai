#!/usr/bin/env bash
set -uo pipefail
qa_repo=${1:?frozen checkout}; qa_sha=${2:?full source SHA}; qa_out=${3:?new evidence directory}
source /workspace/momega1-build-env/env.sh
cd "$qa_repo" || exit 69
[[ $(git rev-parse HEAD) == "$qa_sha" ]] || exit 69
git diff --quiet HEAD -- app scripts .github tools backend || exit 69
mkdir "$qa_out" || exit 69
qa_out=$(realpath "$qa_out")
qa_cpus=$(python3 -c 'import os; a=sorted(os.sched_getaffinity(0)); print(",".join(map(str,a[-2:])))')
printf '%s\n' "$qa_cpus" > "$qa_out/cpu-affinity.txt"
{ git rev-parse HEAD; git status --porcelain; git ls-files -s app scripts .github tools backend; } > "$qa_out/source-before.txt"
sha256sum "${BASH_SOURCE[0]}" /workspace/momega1-build-env/env.sh /workspace/momega1-build-env/isolation.json backend/package-lock.json > "$qa_out/harness.sha256"
export CONTINUUM_FABRIC_JVM_DEPS=/workspace/momega1-build-env/jvm-deps
export CONTINUUM_TEST_ISOLATION_CONFIG=/workspace/momega1-build-env/isolation.json
export CONTINUUM_WORKSPACE_HOST_PROOF=1
export CONTINUUM_WORKSPACE_EVIDENCE_DIR="$qa_out/workspace-host"
node --version > "$qa_out/node-version.txt"
taskset -c "$qa_cpus" node --test --test-reporter=tap --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs tools/continuum-virtual-fabric/*.test.mjs > "$qa_out/node-full.tap" 2>&1
qa_node=$?; printf '%s\n' "$qa_node" > "$qa_out/node-full.exit"
cd backend || exit 69
taskset -c "$qa_cpus" npm ci --ignore-scripts --no-audit --no-fund --registry=https://registry.npmjs.org > "$qa_out/backend-install.txt" 2>&1
qa_install=$?; printf '%s\n' "$qa_install" > "$qa_out/backend-install.exit"
qa_check=69; qa_backend=69
if [[ "$qa_install" == 0 ]]; then
 taskset -c "$qa_cpus" npm run check > "$qa_out/backend-check.txt" 2>&1
 qa_check=$?; printf '%s\n' "$qa_check" > "$qa_out/backend-check.exit"
 taskset -c "$qa_cpus" npm test > "$qa_out/backend-test.tap" 2>&1
 qa_backend=$?; printf '%s\n' "$qa_backend" > "$qa_out/backend-test.exit"
else
 printf 'NOT_RUN: dependency installation failed\n' > "$qa_out/backend-test.exit"
fi
cd .. || exit 69
{ git rev-parse HEAD; git status --porcelain; git ls-files -s app scripts .github tools backend; } > "$qa_out/source-after.txt"
cmp "$qa_out/source-before.txt" "$qa_out/source-after.txt" > "$qa_out/source-cmp.txt" 2>&1
qa_cmp=$?; printf '%s\n' "$qa_cmp" > "$qa_out/source-cmp.exit"
[[ "$qa_node" == 0 && "$qa_install" == 0 && "$qa_check" == 0 && "$qa_backend" == 0 && "$qa_cmp" == 0 ]] || exit 1
