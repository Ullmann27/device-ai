#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
out=${1:?fresh evidence directory}
[[ ! -e "$out" ]] || { echo 'fresh evidence directory required' >&2; exit 2; }
mkdir -p "$out/classes"
deps=${CONTINUUM_FABRIC_JVM_DEPS:-/workspace/continuum-build-env/native-graph-deps}
base=${SYNAPSE_BASE_CLASSES:-$root/app/build/tmp/kotlin-classes/debug}
[[ -d "$base" ]] || { echo 'Build this source first or set SYNAPSE_BASE_CLASSES to its verified Gradle classes.' >&2; exit 2; }
base_root=$(git -C "$base" rev-parse --show-toplevel)
[[ $(git -C "$root" rev-parse HEAD:app/src/main) == $(git -C "$base_root" rev-parse HEAD:app/src/main) ]] || {
 echo 'Gradle dependency source differs from the tested production source.' >&2; exit 2;
}
[[ -z $(git -C "$root" status --porcelain -- app/src/main) &&
   -z $(git -C "$base_root" status --porcelain -- app/src/main) ]] || {
 echo 'Production source must be clean for the focused host check.' >&2; exit 2;
}
cp=$(find "$deps" -maxdepth 1 -type f -name '*.jar' -printf '%p:')
cp="$base:${cp%:}"
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
# The integrated V2 registry references its real conversation codec. Compile the
# candidate's sources when present, never substitute old V1 classes for a final run.
if [[ -f "$root/app/src/main/java/at/heinz/deviceai/chief/ChiefBotConversation.kt" ]]; then
 sources+=("$root/app/src/main/java/at/heinz/deviceai/chief/ChiefBotConversation.kt"
  "$root/app/src/main/java/at/heinz/deviceai/cognitive/AiContextCompiler.kt")
fi
git -C "$root" rev-parse HEAD > "$out/source-sha.txt"
git -C "$root" status --short > "$out/source-status.txt"
git -C "$base_root" rev-parse HEAD > "$out/gradle-source-sha.txt"
sha256sum "${sources[@]}" > "$out/inputs.sha256"
find "$base" -type f -name '*.class' -print0 | sort -z | xargs -0 sha256sum > "$out/gradle-class-dependencies.sha256"
java -version > "$out/java-version.txt" 2>&1
# Focused compilation is part of the same Android module, whose internal helpers
# remain internal. Dependency bytes and their source checkout are recorded above.
set +e
java -Xmx768m -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$cp" \
 -Xfriend-paths="$base" -jvm-target 17 -d "$out/classes" "${sources[@]}" > "$out/compile.log" 2>&1
compiled=$?
printf '%s\n' "$compiled" > "$out/compile.exit"
[[ "$compiled" == 0 ]] || exit "$compiled"
printf '%s' "$out/classes:$cp" > "$out/classpath.txt"
# The owner service does not need the compiler, JUnit or Android emulator JAR.
# Emit and test its actual smaller runtime dependency closure, with no empty
# classpath entry that could accidentally make the working directory executable.
runtime_cp="$out/classes:$base"
for pattern in 'json-*.jar' 'kotlin-stdlib-*.jar' 'kotlinx-coroutines-core-jvm-*.jar'; do
 jars=("$deps"/$pattern)
 [[ ${#jars[@]} == 1 && -f "${jars[0]}" ]] || { echo "Expected one runtime dependency: $pattern" >&2; exit 2; }
 runtime_cp="$runtime_cp:${jars[0]}"
done
printf '%s' "$runtime_cp" > "$out/runtime-classpath.txt"
java -Xmx256m -cp "$runtime_cp" at.heinz.deviceai.chief.HandoffHostChecks > "$out/checks.log" 2>&1
status=$?
set -e
printf '%s\n' "$status" > "$out/checks.exit"
cat "$out/checks.log"
exit "$status"
