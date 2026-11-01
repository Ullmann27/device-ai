#!/usr/bin/env bash
set -euo pipefail
tools_dir=$(cd "$(dirname "$0")" && pwd)
corpus=${1:?frozen corpus JSON}
expected_hash=${2:?frozen SHA256}
baseline=${3:?baseline class directory}
candidate=${4:?candidate class directory}
out=${5:?fresh output directory}
: "${CONTINUUM_KOTLIN_COMPILER_CP:?Kotlin compiler/dependency classpath}"
: "${CONTINUUM_KOTLIN_STDLIB:?Kotlin stdlib jar}"
[[ ! -e "$out" ]] || { echo 'Refuse to overwrite retained review results' >&2; exit 1; }
mkdir -p "$out/harness"
python3 "$tools_dir/generate_dataset.py" "$corpus" "$out" "$expected_hash"
java -cp "$CONTINUUM_KOTLIN_COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
 -no-stdlib -no-reflect -jvm-target 17 -Xfriend-paths="$candidate" \
 -classpath "$candidate:$CONTINUUM_KOTLIN_STDLIB" -d "$out/harness" \
 "$out/FrozenDataset.kt" "$tools_dir/IndependentMemoryReview.kt"
printf '%s\n' 'REPLAY_CONSUMED_VALIDATION_NOT_FRESH_TEST' > "$out/STATUS.txt"
java -Xmx1g -cp "$out/harness:$baseline:$CONTINUUM_KOTLIN_STDLIB" at.heinz.deviceai.learning.IndependentMemoryReviewKt > "$out/baseline.tsv" 2> "$out/baseline.summary.txt"
java -Xmx1g -cp "$out/harness:$candidate:$CONTINUUM_KOTLIN_STDLIB" at.heinz.deviceai.learning.IndependentMemoryReviewKt > "$out/candidate.tsv" 2> "$out/candidate.summary.txt"
cat "$out/baseline.summary.txt" "$out/candidate.summary.txt"
