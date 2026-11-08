#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd -P)
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
# Record the exact source SHA before the tests so RED runs stay tied to a commit.
git -C "$root" rev-parse HEAD > "$out/source-sha.txt"
git -C "$root" status --porcelain >> "$out/source-sha.txt"
# A RED run is a real nonzero exit; it is recorded and propagated, never hidden.
set +e
java -Xmx256m -cp "$out/h4.jar:$junit" org.junit.runner.JUnitCore \
    at.heinz.deviceai.cognitive.HypothesisWorkspaceTest \
    at.heinz.deviceai.cognitive.HypothesisPriorBatchTest \
    at.heinz.deviceai.cognitive.HypothesisRobustProbeTest | tee "$out/junit.txt"
status=${PIPESTATUS[0]}
set -e
echo "junit_exit=$status" >> "$out/source-sha.txt"
exit "$status"
