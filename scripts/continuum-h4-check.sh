#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
out=${1:?external output directory}
junit=${CONTINUUM_JUNIT_JARS:?JUnit4 and Hamcrest classpath}
command -v kotlinc >/dev/null || { echo 'UNAVAILABLE: kotlinc' >&2; exit 2; }
mkdir -p "$out"
out=$(cd "$out" && pwd -P)
[[ "$out" != "$root" && "$out" != "$root/"* ]] || { echo 'Output must be outside source' >&2; exit 2; }
sources=("$root/app/src/main/java/at/heinz/deviceai/cognitive/HypothesisWorkspace.kt")
tests=("$root/app/src/test/java/at/heinz/deviceai/cognitive/HypothesisWorkspaceTest.kt"
       "$root/app/src/test/java/at/heinz/deviceai/cognitive/HypothesisPriorBatchTest.kt"
       "$root/app/src/test/java/at/heinz/deviceai/cognitive/HypothesisRobustProbeTest.kt")
kotlinc "${sources[@]}" "${tests[@]}" "$root/tools/continuum-cognitive-pilots/H4DiagnosisPilot.kt" \
    -jvm-target 17 -cp "$junit" -include-runtime -d "$out/h4.jar" > "$out/compile.txt" 2>&1
java -Xmx256m -cp "$out/h4.jar:$junit" org.junit.runner.JUnitCore \
    at.heinz.deviceai.cognitive.HypothesisWorkspaceTest \
    at.heinz.deviceai.cognitive.HypothesisPriorBatchTest \
    at.heinz.deviceai.cognitive.HypothesisRobustProbeTest | tee "$out/junit.txt"
git -C "$root" rev-parse HEAD > "$out/source-sha.txt"
