#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
out=${1:?pass an absolute external output directory}
[[ "$out" == /* ]] || { echo 'Absolute output required' >&2; exit 2; }
mkdir -p "$out"
out=$(cd "$out" && pwd -P)
[[ "$out" != "$root" && "$out" != "$root/"* ]] || { echo 'External output required' >&2; exit 2; }
python3 - "$out" <<'PY_CHECK'
import pathlib, sys
assert not any(pathlib.Path(sys.argv[1]).iterdir()), 'Empty output directory required'
PY_CHECK
cd "$root"
node --test experimental/brain-lab-20261110/lab.test.mjs \
  tools/continuum-virtual-fabric/*.test.mjs \
  tools/continuum-cognitive-pilots/verified-reuse-pilot.test.mjs > "$out/node-tests.txt"
node experimental/brain-lab-20261110/run.mjs "$out/comparison.json" > "$out/summary.json"
if [[ -n "${CONTINUUM_JUNIT_JARS:-}" ]] && command -v kotlinc >/dev/null; then
  bash scripts/continuum-reasoning-pilot.sh "$out/reasoning-batch" batch
  set +e
  bash scripts/continuum-reasoning-pilot.sh "$out/reasoning-sequential" sequential
  baseline_status=$?
  set -e
  [[ "$baseline_status" == 1 ]] || { echo 'Unexpected baseline status' >&2; exit 1; }
  python3 - "$out/reasoning-sequential/reasoning.tsv" <<'PY'
import pathlib, sys
text = pathlib.Path(sys.argv[1]).read_text()
assert '768 prior invariance failures; 2 planning scenario failures' in text
assert 'DEV-equal4\t24\t0\t' in text
assert 'HOLDOUT-unequal4\t24\t0\t' in text
assert 'HOLDOUT-equal6\t720\t0\t' in text
print('EXPECTED_BASELINE_RED verified')
PY
else
  echo 'Kotlin counterfactual replication NOT_RUN: kotlinc/JUnit unavailable'
fi
