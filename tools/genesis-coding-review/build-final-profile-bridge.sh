#!/usr/bin/env bash
set -euo pipefail
source_root=/workspace/genesis-g04-source
qa_root=/workspace/synapse-ui-candidate
expected=1595389274acb840c4ab544fa699c9eb2a04bd53
out=${1:?fresh output directory}
[[ ! -e "$out" ]] || { echo 'fresh output directory required' >&2; exit 2; }
[[ $(git -C "$source_root" rev-parse HEAD) == "$expected" ]]
[[ $(git -C "$qa_root" rev-parse HEAD) == "$expected" ]]
[[ -z $(git -C "$source_root" status --porcelain) ]]
[[ -z $(git -C "$qa_root" status --porcelain) ]]
base="$qa_root/app/build/tmp/kotlin-classes/debug"
deps=/workspace/continuum-build-env/native-graph-deps
[[ -f "$base/at/heinz/deviceai/chief/ChiefBotConversation.class" ]]
mkdir -p "$out/classes"
cp=$(find "$deps" -maxdepth 1 -type f -name '*.jar' -printf '%p:')
cp="$base:$cp"
sources=(
 "$source_root/app/src/main/java/at/heinz/deviceai/chief/ChiefBotHandoff.kt"
 "$source_root/app/src/main/java/at/heinz/deviceai/chief/ChiefBotProfileStore.kt"
 "$source_root/app/src/main/java/at/heinz/deviceai/chief/ChiefWorkspaceWiring.kt"
 "$source_root/app/src/main/java/at/heinz/deviceai/chief/ChiefWorkerPool.kt"
 "$source_root/app/src/main/java/at/heinz/deviceai/routines/ContinuumRoutines.kt"
 "$source_root/app/src/main/java/at/heinz/deviceai/cognitive/ContinuumState.kt"
 "$source_root/app/src/main/java/at/heinz/deviceai/cognitive/ContinuumSeed.kt"
 "$source_root/tools/genesis-workspaces/HandoffHostChecks.kt"
 "$source_root/app/src/main/java/at/heinz/deviceai/chief/ChiefBotConversation.kt"
 "$source_root/app/src/main/java/at/heinz/deviceai/cognitive/AiContextCompiler.kt"
)
printf '%s\n' "$expected" > "$out/source-sha.txt"
git -C "$source_root" status --short > "$out/source-status.txt"
sha256sum "${sources[@]}" > "$out/inputs.sha256"
find "$base" -type f -name '*.class' -print0 | sort -z | xargs -0 sha256sum > "$out/qa-class-dependencies.sha256"
java -version > "$out/java-version.txt" 2>&1
# These are internal members of the SAME actual Android module. The focused
# host compilation preserves that module friendship, as the existing brain
# review does; no production declaration or visibility is weakened.
set +e
java -Xmx768m -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect \
 -Xfriend-paths="$base" -classpath "$cp" -jvm-target 17 -d "$out/classes" "${sources[@]}" \
 > "$out/compile.log" 2>&1
compiled=$?
printf '%s\n' "$compiled" > "$out/compile.exit"
[[ "$compiled" == 0 ]] || exit "$compiled"
printf '%s' "$out/classes:$cp" > "$out/classpath.txt"
java -Xmx256m -cp "$out/classes:$cp" at.heinz.deviceai.chief.HandoffHostChecks > "$out/checks.log" 2>&1
checked=$?
printf '%s\n' "$checked" > "$out/checks.exit"
cat "$out/checks.log"
exit "$checked"
