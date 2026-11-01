#!/usr/bin/env bash
# Real pure Kotlin/JVM tests. Android/lint/APK/device acceptance remains Render/Fold7.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
: "${CONTINUUM_KOTLIN_COMPILER_CP:?Kotlin compiler/dependencies}"
: "${CONTINUUM_KOTLIN_STDLIB:?Kotlin stdlib jar}"
: "${CONTINUUM_JUNIT_CP:?JUnit4, Hamcrest, org.json jars}"
out=${1:?isolated output directory}
mkdir -p "$out/classes"
files=()
for name in LearningSnapshot TinyContextDecoder TrainableSubwordEncoder; do
  files+=("$root/app/src/main/java/at/heinz/deviceai/learning/$name.kt")
done
for name in ContinuumResidualSpanCore ContinuumCounterfactualCurriculum EvidenceBoundNaturalLocationProbe VerifiedLocationSpanResearch NaturalLocationLabComparison; do
  files+=("$root/app/src/main/java/at/heinz/deviceai/learning/span/$name.kt")
done
tests=(ContinuumResidualSpanCoreTest ContinuumResidualSpanCoreVariantsTest EvidenceBoundNaturalLocationProbeTest NaturalLocationVerificationTest NaturalEntitySpanCoreTest LocationEvidenceSnapshotTest NaturalLocationLabComparisonTest)
files+=("$root/app/src/test/java/at/heinz/deviceai/learning/span/ContinuumResidualSpanCoreChecks.kt")
classes=(at.heinz.deviceai.learning.span.ContinuumResidualSpanCoreSpanTest)
for name in "${tests[@]}"; do
  files+=("$root/app/src/test/java/at/heinz/deviceai/learning/span/$name.kt")
  classes+=("at.heinz.deviceai.learning.span.$name")
done
files+=("$root/tools/continuum-span-research/NaturalSpanExperiment.kt" "$root/tools/continuum-span-research/ConditioningCounterfactualExperiment.kt")
java -cp "$CONTINUUM_KOTLIN_COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 17 -classpath "$CONTINUUM_KOTLIN_STDLIB:$CONTINUUM_JUNIT_CP" \
  -d "$out/classes" "${files[@]}"
cp="$out/classes:$CONTINUUM_KOTLIN_STDLIB:$CONTINUUM_JUNIT_CP:$root/app/src/test/resources:$root/app/src/main/assets"
java -Xmx512m -cp "$cp" org.junit.runner.JUnitCore "${classes[@]}"
if [[ "${CONTINUUM_SPAN_RUN_EXPERIMENTS:-0}" == 1 ]]; then
  java -Xmx512m -cp "$cp" at.heinz.deviceai.learning.span.NaturalSpanExperiment "$out/authored-ranker"
  java -Xmx512m -cp "$cp" at.heinz.deviceai.learning.ConditioningCounterfactualExperiment "$out/counterfactual.json"
fi
