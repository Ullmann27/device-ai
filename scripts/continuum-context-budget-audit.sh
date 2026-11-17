#!/usr/bin/env bash
# RESEARCH_SHADOW. Two precompiled exact-source host trees; no app weights, training or device access.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
base=${1:?baseline host main classes}
candidate=${2:?candidate host main classes}
out=${3:?fresh output directory}
: "${CONTINUUM_KOTLIN_COMPILER_CP:?compiler jars}"
: "${CONTINUUM_KOTLIN_STDLIB:?stdlib jar}"
: "${CONTINUUM_JUNIT_CP:?JUnit/Hamcrest/json jars}"
[[ ! -e "$out" ]]
mkdir -p "$out/harness"
(cd "$root/docs/evidence/sovereign-cognition-20261102" && sha256sum -c frozen-context-holdout.sha256)
java -cp "$CONTINUUM_KOTLIN_COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 17 -module-name app -Xfriend-paths="$base" \
  -cp "$base:$CONTINUUM_KOTLIN_STDLIB:$CONTINUUM_JUNIT_CP" \
  "$root/app/src/test/java/at/heinz/deviceai/cognitive/ContextIntegrityFixture.kt" \
  "$root/tools/continuum-span-research/ContextBudgetExperiment.kt" \
  "$root/tools/continuum-span-research/CdxConditioningAudit.kt" -d "$out/harness"
corpus="$root/docs/evidence/sovereign-cognition-20261102/frozen-context-holdout.json"
java -cp "$out/harness:$base:$CONTINUUM_KOTLIN_STDLIB:$CONTINUUM_JUNIT_CP" \
  at.heinz.deviceai.cognitive.ContextBudgetExperiment "$corpus" "$out/parent.json" parent
java -cp "$out/harness:$candidate:$CONTINUUM_KOTLIN_STDLIB:$CONTINUUM_JUNIT_CP" \
  at.heinz.deviceai.cognitive.ContextBudgetExperiment "$corpus" "$out/candidate.json" candidate
java -cp "$out/harness:$base:$CONTINUUM_KOTLIN_STDLIB:$CONTINUUM_JUNIT_CP" \
  at.heinz.deviceai.learning.CdxConditioningAudit "$corpus" "$out/conditioning.json"
sha256sum "$base/at/heinz/deviceai/cognitive/AiContextCompiler.class" \
  "$candidate/at/heinz/deviceai/cognitive/AiContextCompiler.class" "$corpus" > "$out/inputs.sha256"
