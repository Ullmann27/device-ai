#!/usr/bin/env bash
set -euo pipefail
source /workspace/continuum-build-env/env.sh
review_root=$(cd "$(dirname "$0")/../.." && pwd)
compiled_run=${1:?run.sh output with freshly compiled pool classes}
out=${2:?new diagnostic output directory}
mkdir -p "$out/classes"
test ! -e "$out/source.txt"
cp "$compiled_run/source.txt" "$out/source.txt"
cp "$compiled_run/inputs.sha256" "$out/pool-inputs.sha256"
sha256sum "$review_root/tools/synapse-lifecycle-review/QueueRetentionProbe.kt" > "$out/probe-input.sha256"
graph=${CONTINUUM_REVIEW_GRAPH:-/workspace/continuum-breakthrough-quality/app/build/tmp/kotlin-classes/debug}
kotlinc "$review_root/tools/synapse-lifecycle-review/QueueRetentionProbe.kt" -jvm-target 17 -cp "$compiled_run/classes" -d "$out/classes" > "$out/compile.txt" 2>&1
set +e
java -cp "$out/classes:$compiled_run/classes:$graph:/workspace/continuum-build-env/kotlin-official/kotlinc/lib/kotlin-stdlib.jar" at.heinz.deviceai.chief.QueueRetentionProbe > "$out/observation.txt" 2>&1
result=$?
set -e
printf '%s\n' "$result" > "$out/observation.exit"
cat "$out/observation.txt"
exit "$result"
