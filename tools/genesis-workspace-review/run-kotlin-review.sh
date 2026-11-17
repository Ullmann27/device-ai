#!/usr/bin/env bash
set -euo pipefail
review_root=$(cd "$(dirname "$0")/../.." && pwd)
revision=${1:?exact candidate revision}
out=${2:?fresh evidence directory}
test ! -e "$out"
mkdir -p "$out/source" "$out/classes"
base=${CONTINUUM_REVIEW_GRAPH:-/workspace/continuum-breakthrough-quality/app/build/tmp/kotlin-classes/debug}
deps=${CONTINUUM_FABRIC_JVM_DEPS:-/workspace/continuum-build-env/native-graph-deps}
classpath="$base"
while IFS= read -r jar; do classpath="$classpath:$jar"; done < <(rg --files "$deps" -g '*.jar' | sort)
sources=(app/src/main/java/at/heinz/deviceai/chief/ChiefBotProfileStore.kt app/src/main/java/at/heinz/deviceai/chief/ChiefBotHandoff.kt app/src/main/java/at/heinz/deviceai/chief/ChiefWorkspaceWiring.kt app/src/main/java/at/heinz/deviceai/chief/ChiefWorkerPool.kt app/src/main/java/at/heinz/deviceai/routines/ContinuumRoutines.kt app/src/main/java/at/heinz/deviceai/cognitive/ContinuumState.kt app/src/main/java/at/heinz/deviceai/cognitive/ContinuumSeed.kt)
git -C "$review_root" rev-parse "$revision" > "$out/candidate-sha.txt"
git -C "$review_root" archive "$revision" "${sources[@]}" | tar -x -C "$out/source"
for i in "${!sources[@]}"; do sources[$i]="$out/source/${sources[$i]}"; done
sources+=("$review_root/tools/genesis-workspace-review/IndependentBotHandoffTest.kt")
sha256sum "${sources[@]}" > "$out/source-hashes.txt"
java -version > "$out/java.txt" 2>&1
set +e
java -Xmx512m -cp "$classpath" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$classpath" -jvm-target 17 -d "$out/classes" "${sources[@]}" > "$out/compile.log" 2>&1
compiled=$?
printf '%s\n' "$compiled" > "$out/compile.exit"
if [[ "$compiled" != 0 ]]; then cat "$out/compile.log"; exit "$compiled"; fi
java -Xmx384m -cp "$out/classes:$classpath" org.junit.runner.JUnitCore at.heinz.deviceai.chief.IndependentBotHandoffTest > "$out/review.log" 2>&1
result=$?
printf '%s\n' "$result" > "$out/review.exit"
cat "$out/review.log"
exit "$result"
