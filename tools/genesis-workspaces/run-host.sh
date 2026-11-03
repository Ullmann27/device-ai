#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
out=${1:?fresh evidence directory}
[[ ! -e "$out" ]] || { echo 'fresh evidence directory required' >&2; exit 2; }
mkdir -p "$out/classes"
deps=${CONTINUUM_FABRIC_JVM_DEPS:-/workspace/continuum-build-env/native-graph-deps}
base=${SYNAPSE_BASE_CLASSES:-/workspace/continuum-breakthrough-quality/app/build/tmp/kotlin-classes/debug}
cp=$(find "$deps" -maxdepth 1 -type f -name '*.jar' -printf '%p:')
cp="$base:$cp"
sources=(
 "$root/app/src/main/java/at/heinz/deviceai/chief/ChiefBotHandoff.kt"
 "$root/app/src/main/java/at/heinz/deviceai/chief/ChiefBotProfileStore.kt"
 "$root/app/src/main/java/at/heinz/deviceai/chief/ChiefWorkspaceWiring.kt"
 "$root/app/src/main/java/at/heinz/deviceai/chief/ChiefWorkerPool.kt"
 "$root/app/src/main/java/at/heinz/deviceai/routines/ContinuumRoutines.kt"
 "$root/app/src/main/java/at/heinz/deviceai/cognitive/ContinuumState.kt"
 "$root/app/src/main/java/at/heinz/deviceai/cognitive/ContinuumSeed.kt"
 "$root/tools/genesis-workspaces/HandoffHostChecks.kt"
)
git -C "$root" rev-parse HEAD > "$out/source-sha.txt"
git -C "$root" status --short > "$out/source-status.txt"
sha256sum "${sources[@]}" > "$out/inputs.sha256"
java -version > "$out/java-version.txt" 2>&1
java -Xmx768m -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$cp" \
 -jvm-target 17 -d "$out/classes" "${sources[@]}" > "$out/compile.log" 2>&1
printf '%s' "$out/classes:$cp" > "$out/classpath.txt"
set +e
java -Xmx256m -cp "$out/classes:$cp" at.heinz.deviceai.chief.HandoffHostChecks > "$out/checks.log" 2>&1
status=$?
set -e
printf '%s\n' "$status" > "$out/checks.exit"
cat "$out/checks.log"
exit "$status"
