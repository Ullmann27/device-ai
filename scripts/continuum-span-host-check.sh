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
for name in ContinuumResidualSpanCore ContinuumCounterfactualCurriculum EvidenceBoundNaturalLocationProbe VerifiedLocationSpanResearch NaturalLocationLabComparison EntityRelationLocationShadowAdapter DualEvidenceReadContract; do
  files+=("$root/app/src/main/java/at/heinz/deviceai/learning/span/$name.kt")
done
tests=(ContinuumResidualSpanCoreTest ContinuumResidualSpanCoreVariantsTest EvidenceBoundNaturalLocationProbeTest NaturalLocationVerificationTest NaturalEntitySpanCoreTest LocationEvidenceSnapshotTest NaturalLocationLabComparisonTest RoleAwareShadowTest DualEvidenceReadContractTest)
files+=("$root/app/src/test/java/at/heinz/deviceai/learning/span/ContinuumResidualSpanCoreChecks.kt")
files+=("$root/app/src/main/java/at/heinz/deviceai/cognitive/NetworkTurnConstraint.kt")
files+=("$root/app/src/main/java/at/heinz/deviceai/cognitive/OwnerChatIntentRouter.kt")
classes=(at.heinz.deviceai.learning.span.ContinuumResidualSpanCoreSpanTest)
for name in "${tests[@]}"; do
  files+=("$root/app/src/test/java/at/heinz/deviceai/learning/span/$name.kt")
  classes+=("at.heinz.deviceai.learning.span.$name")
done
files+=("$root/tools/continuum-span-research/NaturalSpanExperiment.kt" "$root/tools/continuum-span-research/ConditioningCounterfactualExperiment.kt" "$root/tools/continuum-span-research/SpanResourceExperiment.kt" "$root/tools/continuum-span-research/RoleBindingRedControl.kt")
java -cp "$CONTINUUM_KOTLIN_COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 17 -classpath "$CONTINUUM_KOTLIN_STDLIB:$CONTINUUM_JUNIT_CP" \
  -d "$out/classes" "${files[@]}"
cp="$out/classes:$CONTINUUM_KOTLIN_STDLIB:$CONTINUUM_JUNIT_CP:$root/app/src/test/resources:$root/app/src/main/assets"
java -Xmx512m -cp "$cp" org.junit.runner.JUnitCore "${classes[@]}"
if java -Xmx128m -cp "$cp" at.heinz.deviceai.learning.RoleBindingRedControl legacy > "$out/legacy-role-red.txt" 2>&1; then
  echo 'Legacy negative control unexpectedly passed' >&2; exit 1
else
  result=$?
  [[ "$result" == 1 && "$(<"$out/legacy-role-red.txt")" == *EXPECTED_LEGACY_COLLISION_RED* ]] || exit 1
fi
java -Xmx128m -cp "$cp" at.heinz.deviceai.learning.RoleBindingRedControl shadow
if [[ "${CONTINUUM_SPAN_RUN_EXPERIMENTS:-0}" == 1 ]]; then
  java -Xmx512m -cp "$cp" at.heinz.deviceai.learning.span.NaturalSpanExperiment "$out/authored-ranker"
  java -Xmx512m -cp "$cp" at.heinz.deviceai.learning.ConditioningCounterfactualExperiment "$out/counterfactual.json" "$root/app/src/main/assets/continuum-research/natural-location-v3.csx"
fi

if [[ "${CONTINUUM_SPAN_RUN_RESOURCES:-0}" == 1 ]]; then
  java -Xmx128m -cp "$cp" at.heinz.deviceai.learning.span.SpanResourceExperiment \
    "$root/app/src/main/assets/continuum-research/natural-location-v3.csx" "$out/host-resources.json"
fi
