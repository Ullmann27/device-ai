#!/usr/bin/env bash
set -euo pipefail
source_root=${1:?source checkout}
output=${2:?external evidence directory}
test_root=$(cd "$(dirname "$0")" && pwd)
: "${RECALL_DEPENDENCY_CLASSES:?frozen app classes}"
: "${RECALL_JSON_JAR:?JSON jar}"
: "${RECALL_JUNIT_JAR:?JUnit jar}"
: "${RECALL_HAMCREST_JAR:?Hamcrest jar}"
: "${RECALL_KOTLINC:?Kotlin compiler}"
: "${RECALL_JAVA:?Java binary}"
: "${RECALL_STDLIB:?Kotlin standard library}"
mkdir -p "$output"
sources=(
 "$source_root/app/src/main/java/at/heinz/deviceai/cognitive/MemoryEcology.kt"
 "$source_root/app/src/main/java/at/heinz/deviceai/cognitive/AgentExperienceRecall.kt"
 "$source_root/app/src/main/java/at/heinz/deviceai/cognitive/BoundedAgent.kt"
 "$test_root/IndependentRecallOrderingTest.kt"
)
classpath="$RECALL_DEPENDENCY_CLASSES:$RECALL_JSON_JAR:$RECALL_JUNIT_JAR:$RECALL_HAMCREST_JAR"
sha256sum "${sources[@]}" > "$output/source-sha256.txt"
"$RECALL_JAVA" -version > "$output/toolchain.txt" 2>&1
"$RECALL_KOTLINC" -version >> "$output/toolchain.txt" 2>&1
"$RECALL_KOTLINC" "${sources[@]}" -jvm-target 17 \
  -Xfriend-paths="$RECALL_DEPENDENCY_CLASSES" -cp "$classpath" \
  -d "$output/tests.jar" > "$output/compile.txt" 2>&1
set +e
"$RECALL_JAVA" -cp "$output/tests.jar:$classpath:$RECALL_STDLIB" \
  org.junit.runner.JUnitCore at.heinz.deviceai.cognitive.IndependentRecallOrderingTest \
  > "$output/junit.txt" 2>&1
status=$?
set -e
cat "$output/junit.txt"
exit "$status"
