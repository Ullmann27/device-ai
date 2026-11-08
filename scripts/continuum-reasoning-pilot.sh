#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
out=${1:?pass an external output directory}
arm=${2:?sequential or batch}
ref=${3:-WORKTREE}
junit=${CONTINUUM_JUNIT_JARS:?set to JUnit4 and Hamcrest jar paths separated by colon}
[[ "$arm" == sequential || "$arm" == batch ]] || { echo 'Invalid arm' >&2; exit 2; }
command -v kotlinc >/dev/null || { echo 'UNAVAILABLE: kotlinc' >&2; exit 2; }
mkdir -p "$out"
out=$(cd "$out" && pwd -P)
[[ "$out" != "$root" && "$out" != "$root/"* ]] || { echo 'Output must be outside source' >&2; exit 2; }
sources=()
tests=()
classes=()
for name in HypothesisWorkspace LightweightJtmsEngine CounterfactualWorldOverlay; do
    path="app/src/main/java/at/heinz/deviceai/cognitive/$name.kt"
    if [[ "$ref" == WORKTREE ]]; then
        cp "$root/$path" "$out/$name.kt"
    else
        git -C "$root" show "$ref:$path" > "$out/$name.kt"
    fi
    sources+=("$out/$name.kt")
    tests+=("$root/app/src/test/java/at/heinz/deviceai/cognitive/${name}Test.kt")
    classes+=("at.heinz.deviceai.cognitive.${name}Test")
done
if [[ "$arm" == batch ]]; then
    tests+=("$root/app/src/test/java/at/heinz/deviceai/cognitive/HypothesisPriorBatchTest.kt")
    classes+=("at.heinz.deviceai.cognitive.HypothesisPriorBatchTest")
fi
{
    git -C "$root" rev-parse HEAD
    printf 'source_ref=%s admission=%s\n' "$ref" "$arm"
    git -C "$root" status --porcelain
    sha256sum "${sources[@]}" "$root/tools/continuum-cognitive-pilots/ReasoningPilot.kt"
    kotlinc -version
    java -version
} > "$out/provenance.txt" 2>&1
kotlinc "${sources[@]}" "${tests[@]}" \
    "$root/tools/continuum-cognitive-pilots/ReasoningPilot.kt" \
    "$root/tools/continuum-cognitive-pilots/admission-$arm.kt" \
    -jvm-target 17 -cp "$junit" -include-runtime -d "$out/reasoning.jar" > "$out/compile.txt" 2>&1
java -cp "$out/reasoning.jar:$junit" org.junit.runner.JUnitCore "${classes[@]}" | tee "$out/junit.txt"
java -cp "$out/reasoning.jar" at.heinz.deviceai.cognitive.ReasoningPilot --assert | tee "$out/reasoning.tsv"
