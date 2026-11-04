#!/usr/bin/env bash
set -euo pipefail
if [ "$#" -ne 3 ]; then
  echo 'Usage: bash replay_gradient_audit.sh REPOSITORY KOTLIN_2_0_20_LIB NEW_OUTPUT_DIRECTORY' >&2
  exit 2
fi
audit_repo=$(realpath "$1")
audit_tools=$(realpath "$2")
audit_output=$(realpath -m "$3")
audit_sources="$audit_repo/app/src/main/java/at/heinz/deviceai/learning/lm"
audit_harness="$audit_repo/experimental/weight-space-20261104/support-round2/LmGradientAudit.kt"
mkdir "$audit_output"
git -C "$audit_repo" diff --exit-code 30cd3a89810ed14fa84eca95236d3177e016118d -- \
  app/src/main/java/at/heinz/deviceai/learning/lm/LmModel.kt \
  app/src/main/java/at/heinz/deviceai/learning/lm/LmTensor.kt \
  app/src/main/java/at/heinz/deviceai/learning/lm/LmBpeTokenizer.kt > "$audit_output/production-basis-diff.log"
java -version > "$audit_output/java-version.log" 2>&1
sha256sum "$audit_sources/LmModel.kt" "$audit_sources/LmTensor.kt" "$audit_sources/LmBpeTokenizer.kt" \
  "$audit_harness" "$audit_tools/kotlin-compiler-embeddable-2.0.20.jar" \
  "$audit_tools/kotlin-stdlib-2.0.20.jar" "$audit_tools/annotations-24.0.1.jar" > "$audit_output/input-sha256.txt"
java -cp "$audit_tools/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 17 \
  -classpath "$audit_tools/kotlin-stdlib-2.0.20.jar:$audit_tools/annotations-24.0.1.jar" \
  -d "$audit_output/audit.jar" "$audit_sources/LmTensor.kt" "$audit_sources/LmModel.kt" \
  "$audit_sources/LmBpeTokenizer.kt" "$audit_harness" > "$audit_output/compile.log" 2>&1
timeout --signal=KILL 60s java -Xmx512m -XX:ActiveProcessorCount=1 \
  -cp "$audit_output/audit.jar:$audit_tools/kotlin-stdlib-2.0.20.jar" \
  at.heinz.deviceai.learning.lm.LmGradientAudit "$audit_output" > "$audit_output/run.log" 2>&1
sha256sum "$audit_output/audit.jar" "$audit_output/run.log" "$audit_output"/*.tsv > "$audit_output/artifact-sha256.txt"
cat "$audit_output/run.log"
