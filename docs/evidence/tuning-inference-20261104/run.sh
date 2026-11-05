#!/usr/bin/env bash
set -euo pipefail

# Local installed toolchain only: no Gradle daemon, downloads, Android or paid runner.
if [[ $# != 3 ]]; then
  printf '%s\n' 'Usage: bash run.sh GRADLE_LIB_DIR OUTPUT_DIR before|after' >&2
  exit 2
fi
lm_toolchain_lib=$(realpath "$1")
lm_output_dir=$(realpath -m "$2")
lm_phase=$3
if [[ $lm_phase != before && $lm_phase != after ]]; then exit 2; fi
lm_repo_root=$(git rev-parse --show-toplevel)
lm_baseline=e101e393dd6c80803ac9d9ac91fccd9812c4a18d
lm_source_dir="$lm_output_dir/$lm_phase-sources"
mkdir -p "$lm_source_dir"
for lm_source in "$lm_repo_root"/app/src/main/java/at/heinz/deviceai/learning/lm/*.kt; do
  lm_relative=${lm_source#"$lm_repo_root/"}
  if [[ $lm_phase == before ]]; then
    git -C "$lm_repo_root" show "$lm_baseline:$lm_relative" > "$lm_source_dir/${lm_source##*/}"
  else
    cp "$lm_source" "$lm_source_dir/${lm_source##*/}"
  fi
done
python3 - "$lm_repo_root" "$lm_phase" "$lm_source_dir" "$lm_output_dir/source-$lm_phase.json" <<'PY'
from pathlib import Path
import hashlib, json, subprocess, sys
root, phase, sources, receipt = Path(sys.argv[1]), sys.argv[2], Path(sys.argv[3]), Path(sys.argv[4])
base = 'e101e393dd6c80803ac9d9ac91fccd9812c4a18d'
model_path = 'app/src/main/java/at/heinz/deviceai/learning/lm/LmModel.kt'
baseline = subprocess.check_output(['git', '-C', str(root), 'show', base + ':' + model_path])
reference = root / 'app/src/test/java/at/heinz/deviceai/learning/lm/LmSamplingTest.kt'
def method(text):
    start = text.index('    fun sample(')
    return text[start:text.index('\n    }', start) + 6]
assert method(baseline.decode()) == method(reference.read_text()), 'Frozen reference differs from verified baseline'
sha = lambda data: hashlib.sha256(data).hexdigest()
if phase == 'before':
    assert (sources / 'LmModel.kt').read_bytes() == baseline, 'Invalid BEFORE source'
receipt.write_text(json.dumps({
    'phase': phase, 'baselineCommit': base,
    'checkoutHead': subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip(),
    'baselineLmModelSha256': sha(baseline), 'referenceMethodMatchesBaseline': True,
    'sourceSha256': {p.name: sha(p.read_bytes()) for p in sorted(sources.glob('*.kt'))},
    'testSha256': sha(reference.read_bytes()),
    'benchmarkSha256': sha((root / 'docs/evidence/tuning-inference-20261104/LmSamplingBenchmark.kt').read_bytes())
}, indent=2) + '\n')
PY
lm_classpath="$lm_toolchain_lib/kotlin-stdlib-2.0.20.jar:$lm_toolchain_lib/junit-4.13.2.jar:$lm_toolchain_lib/hamcrest-core-1.3.jar"
java -cp "$lm_toolchain_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 17 -classpath "$lm_classpath" \
  "$lm_source_dir"/*.kt \
  "$lm_repo_root/app/src/test/java/at/heinz/deviceai/learning/lm/LmSamplingTest.kt" \
  "$lm_repo_root/docs/evidence/tuning-inference-20261104/LmSamplingBenchmark.kt" \
  -d "$lm_output_dir/$lm_phase.jar"
java -cp "$lm_output_dir/$lm_phase.jar:$lm_classpath" org.junit.runner.JUnitCore \
  at.heinz.deviceai.learning.lm.LmSamplingTest | tee "$lm_output_dir/tests-$lm_phase.txt"
if [[ ${LM_SAMPLING_RUN_BENCHMARK:-0} == 1 ]]; then
  java -Xms256m -Xmx256m -cp "$lm_output_dir/$lm_phase.jar:$lm_classpath" \
    at.heinz.deviceai.learning.lm.LmSamplingBenchmark "$lm_phase" | tee "$lm_output_dir/$lm_phase.jsonl"
fi
