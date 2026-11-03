#!/usr/bin/env bash
set -euo pipefail
candidate=${1:?source checkout}
out=${2:?fresh output directory}
deps=${CONTINUUM_FABRIC_JVM_DEPS:-/workspace/continuum-build-env/jvm-deps}
mkdir -p "$out/classes"
cp=$(find "$deps" -maxdepth 1 -name '*.jar' -type f -printf '%p:')
sources=(
 "$candidate/app/src/main/java/at/heinz/deviceai/localmodel/LocalConversationBoundary.kt"
 "$candidate/app/src/main/java/at/heinz/deviceai/localmodel/LocalInferenceDelivery.kt"
 "$candidate/app/src/test/java/at/heinz/deviceai/localmodel/LocalConversationBoundaryTest.kt"
 "$candidate/app/src/test/java/at/heinz/deviceai/localmodel/LocalInferenceDeliveryTest.kt"
)
git -C "$candidate" rev-parse HEAD > "$out/source-sha.txt"
sha256sum "${sources[@]}" > "$out/source-hashes.txt"
java -version > "$out/java-version.txt" 2>&1
java -Xmx512m -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$cp" -jvm-target 17 -d "$out/classes" "${sources[@]}" > "$out/compile.log" 2>&1
set +e
timeout 20s java -Xmx256m -cp "$out/classes:$cp" org.junit.runner.JUnitCore at.heinz.deviceai.localmodel.LocalConversationBoundaryTest at.heinz.deviceai.localmodel.LocalInferenceDeliveryTest > "$out/checks.log" 2>&1
status=$?
set -e
printf '%s\n' "$status" > "$out/checks.exit"
cat "$out/checks.log"
exit "$status"
