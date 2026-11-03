#!/usr/bin/env bash
set -euo pipefail
review_root=$(cd "$(dirname "$0")/../.." && pwd)
candidate=${1:?candidate source checkout}
out=${2:?fresh output directory}
base_classes=${GENESIS_BASE_CLASSES:-/workspace/continuum-breakthrough-quality/app/build/tmp/kotlin-classes/debug}
legacy_classes=${GENESIS_V1_CLASSES:-/workspace/synapse-review-build/candidate-2c5cd84a/classes}
deps=${CONTINUUM_FABRIC_JVM_DEPS:-/workspace/continuum-build-env/native-graph-deps}
[[ ! -e "$out" ]] || { echo 'Output must be fresh' >&2; exit 2; }
mkdir -p "$out/classes" "$out/legacy-classes"
cp=$(find "$deps" -maxdepth 1 -name '*.jar' -type f -printf '%p:')
cp="$base_classes:$cp"
legacy_cp="$legacy_classes:$cp"
git -C "$candidate" rev-parse HEAD > "$out/candidate-sha.txt"
git -C "$candidate" status --short > "$out/candidate-status.txt"
java -version > "$out/java-version.txt" 2>&1
sha256sum "$legacy_classes/at/heinz/deviceai/chief/ChiefBotProfileStore.class" > "$out/legacy-store-class.sha256"
java -Xmx512m -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$legacy_cp" -jvm-target 17 -d "$out/legacy-classes" "$review_root/tools/genesis-brain-review/LegacyProfileFixture.kt" > "$out/legacy-compile.log" 2>&1
sources=(
 "$candidate/app/src/main/java/at/heinz/deviceai/chief/ChiefBotProfileStore.kt"
 "$candidate/app/src/main/java/at/heinz/deviceai/chief/ChiefBotConversation.kt"
 "$candidate/app/src/main/java/at/heinz/deviceai/cognitive/AiContextCompiler.kt"
 "$candidate/app/src/test/java/at/heinz/deviceai/cognitive/AiContextCompilerTest.kt"
 "$review_root/tools/genesis-brain-review/ConversationIndependentChecks.kt"
 "$review_root/tools/genesis-brain-review/ConversationStorageChecks.kt"
)
sha256sum "${sources[@]}" > "$out/source-hashes.txt"
for package in cognitive chief localmodel; do find "$base_classes/at/heinz/deviceai/$package" -type f -name '*.class' -exec sha256sum {} +; done > "$out/base-dependency-class-hashes.txt"
java -Xmx768m -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -Xfriend-paths="$base_classes" -classpath "$out/legacy-classes:$cp" -jvm-target 17 -d "$out/classes" "${sources[@]}" > "$out/compile.log" 2>&1
set +e
timeout 60s java -Xmx512m -cp "$out/classes:$cp" at.heinz.deviceai.chief.ConversationIndependentChecks > "$out/conversation.log" 2>&1
conversation_status=$?
timeout 60s java -Xmx512m -Dgenesis.review.v1fixtureClasspath="$out/legacy-classes:$legacy_cp" -cp "$out/classes:$out/legacy-classes:$cp" at.heinz.deviceai.chief.ConversationStorageChecks > "$out/storage.log" 2>&1
storage_status=$?
timeout 30s java -Xmx512m -cp "$out/classes:$cp" org.junit.runner.JUnitCore at.heinz.deviceai.cognitive.AiContextCompilerTest > "$out/compiler-regressions.log" 2>&1
compiler_status=$?
set -e
printf '%s\n' "$conversation_status" > "$out/conversation.exit"
printf '%s\n' "$storage_status" > "$out/storage.exit"
printf '%s\n' "$compiler_status" > "$out/compiler-regressions.exit"
cat "$out/conversation.log" "$out/storage.log" "$out/compiler-regressions.log"
[[ "$conversation_status" == 0 && "$storage_status" == 0 && "$compiler_status" == 0 ]]
