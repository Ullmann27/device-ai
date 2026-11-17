#!/usr/bin/env bash
set -euo pipefail
out=${1:?external evidence directory}
deps=${CONTINUUM_FABRIC_JVM_DEPS:?official Kotlin/JUnit dependencies}
base_classes=${CONTINUUM_BASELINE_CLASSES:?real compiled baseline classes}
mkdir -p "$out"
cp=$(find "$deps" -maxdepth 1 -type f -name '*.jar' -printf '%p:')"$base_classes"
sources=(
 app/src/main/java/at/heinz/deviceai/chief/ChiefBotConversation.kt
 app/src/main/java/at/heinz/deviceai/chief/ChiefBotProfileStore.kt
 app/src/main/java/at/heinz/deviceai/chief/ChiefWorkerPool.kt
 app/src/main/java/at/heinz/deviceai/chief/ChiefDelegationCoordinator.kt
 app/src/main/java/at/heinz/deviceai/cognitive/AiContextCompiler.kt
 app/src/test/java/at/heinz/deviceai/chief/ChiefBotConversationTest.kt
 app/src/test/java/at/heinz/deviceai/chief/ChiefBotProfileStoreTest.kt
 app/src/test/java/at/heinz/deviceai/chief/ChiefDelegationCoordinatorTest.kt
 app/src/test/java/at/heinz/deviceai/cognitive/AiContextCompilerTest.kt
)
git rev-parse HEAD > "$out/source-sha.txt"
sha256sum "${sources[@]}" > "$out/source-hashes.txt"
find "$deps" -maxdepth 1 -type f -name '*.jar' -print0 | sort -z | xargs -0 sha256sum > "$out/dependency-hashes.txt"
find "$base_classes" -type f -name '*.class' -print0 | sort -z | xargs -0 sha256sum > "$out/baseline-class-hashes.txt"
java -version > "$out/java.txt" 2>&1
set +e
java -Xmx768m -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect \
 -Xfriend-paths="$base_classes" -classpath "$cp" -jvm-target 17 -d "$out/classes" "${sources[@]}" > "$out/compile.log" 2>&1
result=$?
printf '%s\n' "$result" > "$out/compile.exit"
if [[ "$result" -ne 0 ]]; then cat "$out/compile.log"; exit "$result"; fi
java -Xmx512m -cp "$out/classes:$cp" org.junit.runner.JUnitCore \
 at.heinz.deviceai.chief.ChiefBotConversationTest \
 at.heinz.deviceai.chief.ChiefBotProfileStoreTest \
 at.heinz.deviceai.chief.ChiefDelegationCoordinatorTest \
 at.heinz.deviceai.cognitive.AiContextCompilerTest > "$out/junit.log" 2>&1
result=$?
printf '%s\n' "$result" > "$out/junit.exit"
cat "$out/junit.log"
exit "$result"
