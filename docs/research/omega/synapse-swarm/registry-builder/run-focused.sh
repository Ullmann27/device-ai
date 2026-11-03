#!/usr/bin/env bash
set -euo pipefail
root=$(git rev-parse --show-toplevel)
out=${1:?external evidence directory}
deps=${CONTINUUM_FABRIC_JVM_DEPS:?official Kotlin/JUnit dependencies}
base_classes=${CONTINUUM_BASELINE_CLASSES:?real baseline Android Kotlin classes, no stubs}
mkdir -p "$out"
cp=$(find "$deps" -maxdepth 1 -type f -name '*.jar' -printf '%p:')"$base_classes"
source_paths=(
 app/src/main/java/at/heinz/deviceai/cognitive/ContinuumState.kt
 app/src/main/java/at/heinz/deviceai/cognitive/ContinuumSeed.kt
 app/src/main/java/at/heinz/deviceai/chief/ChiefWorkerPool.kt
 app/src/main/java/at/heinz/deviceai/chief/ChiefDelegationCoordinator.kt
 app/src/main/java/at/heinz/deviceai/chief/ChiefBotProfileStore.kt
 app/src/test/java/at/heinz/deviceai/chief/ChiefBotProfileStoreTest.kt
 app/src/test/java/at/heinz/deviceai/chief/ChiefDelegationCoordinatorTest.kt
 app/src/test/java/at/heinz/deviceai/chief/ChiefWorkerPoolTest.kt
 app/src/test/java/at/heinz/deviceai/cognitive/ContinuumStateTest.kt
)
git rev-parse HEAD > "$out/source-sha.txt"
sha256sum "${source_paths[@]}" > "$out/source-hashes.txt"
find "$base_classes" -type f -name '*.class' -print0 | sort -z | xargs -0 sha256sum > "$out/baseline-class-hashes.txt"
find "$deps" -maxdepth 1 -type f -name '*.jar' -print0 | sort -z | xargs -0 sha256sum > "$out/dependency-hashes.txt"
java -version > "$out/java.txt" 2>&1
set +e
java -Xmx768m -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$cp" -jvm-target 17 -d "$out/classes" "${source_paths[@]}" > "$out/compile.log" 2>&1
status=$?
printf '%s\n' "$status" > "$out/compile.exit"
if [[ "$status" -ne 0 ]]; then cat "$out/compile.log"; exit "$status"; fi
java -Xmx512m -cp "$out/classes:$cp" org.junit.runner.JUnitCore \
 at.heinz.deviceai.chief.ChiefBotProfileStoreTest \
 at.heinz.deviceai.chief.ChiefDelegationCoordinatorTest \
 at.heinz.deviceai.chief.ChiefWorkerPoolTest \
 at.heinz.deviceai.cognitive.ContinuumStateTest > "$out/junit.log" 2>&1
status=$?
printf '%s\n' "$status" > "$out/junit.exit"
cat "$out/junit.log"
exit "$status"
