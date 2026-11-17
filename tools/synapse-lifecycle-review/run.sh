#!/usr/bin/env bash
set -euo pipefail
source /workspace/continuum-build-env/env.sh
review_root=$(cd "$(dirname "$0")/../.." && pwd)
candidate_root=${1:?candidate worktree required}
out=${2:?empty evidence directory required}
mkdir -p "$out/classes"
test ! -e "$out/source.txt"
graph=${CONTINUUM_REVIEW_GRAPH:-/workspace/continuum-breakthrough-quality/app/build/tmp/kotlin-classes/debug}
test -f "$graph/at/heinz/deviceai/chief/WorkerBlocked.class"
classpath="$graph"
while IFS= read -r jar; do classpath="$classpath:$jar"; done < <(rg --files /workspace/continuum-build-env/native-graph-deps -g '*.jar' | sort)
git -C "$candidate_root" rev-parse HEAD > "$out/source.txt"
git -C "$candidate_root" status --short >> "$out/source.txt"
sha256sum "$candidate_root/app/src/main/java/at/heinz/deviceai/chief/ChiefWorkerPool.kt" "$review_root/tools/synapse-lifecycle-review/IndependentPoolLifecycleTest.kt" > "$out/inputs.sha256"
kotlinc "$candidate_root/app/src/main/java/at/heinz/deviceai/chief/ChiefWorkerPool.kt" \
  "$candidate_root/app/src/test/java/at/heinz/deviceai/chief/ChiefWorkerPoolTest.kt" \
  "$review_root/tools/synapse-lifecycle-review/IndependentPoolLifecycleTest.kt" \
  -module-name app -jvm-target 17 -classpath "$classpath" -d "$out/classes" > "$out/compile.log" 2>&1
set +e
java -cp "$out/classes:$classpath" org.junit.runner.JUnitCore at.heinz.deviceai.chief.IndependentPoolLifecycleTest > "$out/independent.log" 2>&1
independent_exit=$?
java -cp "$out/classes:$classpath" org.junit.runner.JUnitCore at.heinz.deviceai.chief.ChiefWorkerPoolTest > "$out/existing.log" 2>&1
existing_exit=$?
set -e
printf '%s\n' "$independent_exit" > "$out/independent.exit"
printf '%s\n' "$existing_exit" > "$out/existing.exit"
cat "$out/independent.log" "$out/existing.log"
test "$independent_exit" -eq 0 && test "$existing_exit" -eq 0
