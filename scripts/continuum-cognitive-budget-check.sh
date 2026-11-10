#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
out=${1:?pass external output directory}
kotlinc=${KOTLINC:-kotlinc}
java=${JAVA:-java}
junit=${CONTINUUM_JUNIT_JARS:?set JUnit4 and Hamcrest jar paths}
mkdir -p "$out"
out=$(cd "$out" && pwd -P)
[[ "$out" != "$root" && "$out" != "$root/"* ]] || { echo 'Output must be outside source' >&2; exit 2; }
# Exact source contracts, no replacements.
python3 - "$root" "$out" <<'PY'
import pathlib, sys
root, out = map(pathlib.Path, sys.argv[1:])
src = (root / 'app/src/main/java/at/heinz/deviceai/cognitive/ContinuumIntelligence.kt').read_text()
start = src.index('enum class ReasoningPassRole ')
end = src.index('data class ContinuumIntelligenceRequest(', start)
(out / 'ReasoningContracts.kt').write_text('package at.heinz.deviceai.cognitive\n\n' + src[start:end])
PY
sources=(
    "$root/app/src/main/java/at/heinz/deviceai/cognitive/Deliberation.kt"
    "$root/app/src/main/java/at/heinz/deviceai/localmodel/LocalModelComputeBudget.kt"
    "$root/app/src/main/java/at/heinz/deviceai/cognitive/CognitiveBudgetController.kt"
    "$root/app/src/main/java/at/heinz/deviceai/cognitive/CognitiveBudgetResearchRunner.kt"
    "$out/ReasoningContracts.kt"
)
tests=(
    "$root/app/src/test/java/at/heinz/deviceai/cognitive/CognitiveBudgetControllerTest.kt"
    "$root/app/src/test/java/at/heinz/deviceai/cognitive/CognitiveBudgetResearchRunnerTest.kt"
    "$root/app/src/test/java/at/heinz/deviceai/cognitive/DeliberationTest.kt"
    "$root/app/src/test/java/at/heinz/deviceai/localmodel/LocalModelComputeBudgetTest.kt"
)
experiment="$root/tools/continuum-cognitive-budget/CognitiveBudgetExperiment.kt"
revision=$(git -C "$root" rev-parse HEAD)
{
    printf 'source_revision=%s\n' "$revision"
    git -C "$root" status --porcelain
    sha256sum "${sources[@]}" "${tests[@]}" "$experiment" "$root/scripts/continuum-cognitive-budget-check.sh"
    "$kotlinc" -version
    "$java" -version
} > "$out/provenance.txt" 2>&1
"$kotlinc" "${sources[@]}" "${tests[@]}" "$experiment" -jvm-target 17 -cp "$junit" -include-runtime \
    -d "$out/cognitive-budget.jar" > "$out/compile.txt" 2>&1
"$java" -Xmx256m -cp "$out/cognitive-budget.jar:$junit" org.junit.runner.JUnitCore \
    at.heinz.deviceai.cognitive.CognitiveBudgetControllerTest \
    at.heinz.deviceai.cognitive.CognitiveBudgetResearchRunnerTest \
    at.heinz.deviceai.cognitive.DeliberationTest \
    at.heinz.deviceai.localmodel.LocalModelComputeBudgetTest | tee "$out/junit.txt"
"$java" -Xmx256m -cp "$out/cognitive-budget.jar" at.heinz.deviceai.cognitive.CognitiveBudgetExperiment "$revision" \
    > "$out/controlled-ab.tsv" 2> "$out/controlled-summary.txt"
cat "$out/controlled-summary.txt"
"$java" -Xmx256m -cp "$out/cognitive-budget.jar" at.heinz.deviceai.cognitive.CognitiveBudgetExperiment "$revision" modes \
    > "$out/mode-receipts.tsv"
