#!/usr/bin/env bash
set -euo pipefail

if [[ $# != 3 ]]; then
  printf '%s\n' 'Usage: bash run.sh GRADLE_LIB_DIR OUTPUT_DIR before|after' >&2
  exit 2
fi
lm_toolchain_lib=$(realpath "$1")
lm_output_dir=$(realpath -m "$2")
lm_phase=$3
if [[ $lm_phase != before && $lm_phase != after ]]; then exit 2; fi
lm_repo_root=$(git rev-parse --show-toplevel)
lm_evidence=docs/evidence/three-tuning-bots-r2-20261104/inference
lm_source_dir="$lm_output_dir/$lm_phase-sources"
mkdir -p "$lm_source_dir"
python3 "$lm_repo_root/$lm_evidence/resolve-baseline.py" "$lm_repo_root" > "$lm_output_dir/baseline-$lm_phase.json"
lm_baseline=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["baselineCommit"])' "$lm_output_dir/baseline-$lm_phase.json")
while IFS= read -r lm_relative; do
  [[ $lm_relative == *.kt ]] || continue
  git -C "$lm_repo_root" show "$lm_baseline:$lm_relative" > "$lm_source_dir/${lm_relative##*/}"
done < <(git -C "$lm_repo_root" ls-tree -r --name-only "$lm_baseline" -- app/src/main/java/at/heinz/deviceai/learning/lm)
if [[ $lm_phase == after ]]; then
  cp "$lm_repo_root/$lm_evidence/candidate/LmModel.kt" "$lm_source_dir/LmModel.kt"
fi
python3 - "$lm_repo_root" "$lm_phase" "$lm_source_dir" "$lm_output_dir/source-$lm_phase.json" "$lm_output_dir/baseline-$lm_phase.json" <<'PY'
from pathlib import Path
import hashlib, json, subprocess, sys
root, phase, sources, receipt = Path(sys.argv[1]), sys.argv[2], Path(sys.argv[3]), Path(sys.argv[4])
resolution = json.loads(Path(sys.argv[5]).read_text())
base = resolution['baselineCommit']
model_path = 'app/src/main/java/at/heinz/deviceai/learning/lm/LmModel.kt'
baseline = subprocess.check_output(['git', '-C', str(root), 'show', base + ':' + model_path])
tests = root / 'app/src/test/java/at/heinz/deviceai/learning/lm'
reference = tests / 'LmSamplingWeightsTest.kt'
def method(text):
    start = text.index('    fun sample(')
    return text[start:text.index('\n    }', start) + 6]
assert method(baseline.decode()) == method(reference.read_text()), 'R2 reference differs from round-one baseline'
sha = lambda data: hashlib.sha256(data).hexdigest()
candidate = root / 'docs/evidence/three-tuning-bots-r2-20261104/inference/candidate/LmModel.kt'
candidate_hash = 'c329c9d78d28cbcccbd94671eed4f009a6c8c95a5ba69251c07c05bd845d4448'
assert sha(candidate.read_bytes()) == candidate_hash, 'Archived candidate differs from the measured source'
paths = subprocess.check_output(['git', '-C', str(root), 'ls-tree', '-r', '--name-only', base,
    '--', 'app/src/main/java/at/heinz/deviceai/learning/lm'], text=True).splitlines()
paths = [path for path in paths if path.endswith('.kt')]
assert {p.name for p in sources.glob('*.kt')} == {Path(path).name for path in paths}, 'Unexpected source snapshot files'
for path in paths:
    expected = candidate.read_bytes() if phase == 'after' and path == model_path else subprocess.check_output(
        ['git', '-C', str(root), 'show', base + ':' + path])
    assert (sources / Path(path).name).read_bytes() == expected, 'Snapshot differs from the resolved baseline/candidate'
receipt.write_text(json.dumps({
    **resolution, 'phase': phase,
    'candidateStatus': 'CANDIDATE_NOT_ADOPTED',
    'sourceOrigin': 'round_one_snapshot' if phase == 'before' else 'round_one_snapshot_with_archived_candidate',
    'archivedCandidateSha256': candidate_hash,
    'checkoutHead': subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip(),
    'baselineLmModelSha256': sha(baseline), 'referenceMethodMatchesRoundOne': True,
    'sourceSha256': {p.name: sha(p.read_bytes()) for p in sorted(sources.glob('*.kt'))},
    'testSha256': {name: sha((tests / name).read_bytes()) for name in ['LmSamplingTest.kt', 'LmSamplingWeightsTest.kt']},
    'benchmarkSha256': sha((root / 'docs/evidence/three-tuning-bots-r2-20261104/inference/LmSamplingRound2Benchmark.kt').read_bytes())
}, indent=2) + '\n')
PY
lm_classpath="$lm_toolchain_lib/kotlin-stdlib-2.0.20.jar:$lm_toolchain_lib/junit-4.13.2.jar:$lm_toolchain_lib/hamcrest-core-1.3.jar"
java -cp "$lm_toolchain_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 17 -classpath "$lm_classpath" \
  "$lm_source_dir"/*.kt \
  "$lm_repo_root/app/src/test/java/at/heinz/deviceai/learning/lm/LmSamplingTest.kt" \
  "$lm_repo_root/app/src/test/java/at/heinz/deviceai/learning/lm/LmSamplingWeightsTest.kt" \
  "$lm_repo_root/$lm_evidence/LmSamplingRound2Benchmark.kt" \
  -d "$lm_output_dir/$lm_phase.jar"
java -cp "$lm_output_dir/$lm_phase.jar:$lm_classpath" org.junit.runner.JUnitCore \
  at.heinz.deviceai.learning.lm.LmSamplingTest at.heinz.deviceai.learning.lm.LmSamplingWeightsTest \
  | tee "$lm_output_dir/tests-$lm_phase.txt"
if [[ ${LM_SAMPLING_RUN_BENCHMARK:-0} == 1 ]]; then
  java -Xms256m -Xmx256m -cp "$lm_output_dir/$lm_phase.jar:$lm_classpath" \
    at.heinz.deviceai.learning.lm.LmSamplingRound2Benchmark "$lm_phase" | tee "$lm_output_dir/$lm_phase.jsonl"
fi
