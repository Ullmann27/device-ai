#!/usr/bin/env bash
set -u
review_root=$(cd "$(dirname "$0")/../.." && pwd)
review_target=${WORKSPACE_REVIEW_TARGET:?required}
review_out=${WORKSPACE_REVIEW_EVIDENCE:?required}
mkdir -p "$review_out"
review_classes=$(mktemp -d /tmp/momega1-independent-kotlin.XXXXXX)
review_compile_cp=$(find /workspace/momega1-build-env/jvm-deps -name '*.jar' -printf '%p:' | sed 's/:$//')
review_runtime_cp="$review_compile_cp:/workspace/momega1-android/app/build/tmp/kotlin-classes/debug:/workspace/momega1-android/app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes:/workspace/momega1-build-env/android-sdk/platforms/android-36/android.jar"
review_java=/workspace/momega1-build-env/jdk17/bin/java
for relative in app/src/main/java/at/heinz/deviceai/chief/ChiefWorkerPool.kt app/src/main/java/at/heinz/deviceai/routines/ContinuumRoutines.kt; do
 cmp "$review_target/$relative" "/workspace/momega1-android/$relative" || exit 2
 sha256sum "$review_target/$relative"
done > "$review_out/source-equivalence.txt"
"$review_java" -Xmx512m -cp "$review_compile_cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$review_runtime_cp" -jvm-target 17 -d "$review_classes" \
 "$review_target/app/src/main/java/at/heinz/deviceai/chief/ChiefWorkspaceWiring.kt" \
 "$review_target/app/src/test/java/at/heinz/deviceai/chief/ChiefWorkspaceWiringTest.kt" \
 "$review_target/app/src/test/java/at/heinz/deviceai/chief/ChiefWorkspaceWiringHostTest.kt" \
 "$review_target/app/src/test/java/at/heinz/deviceai/chief/ChiefWorkerPoolTest.kt" \
 "$review_target/app/src/test/java/at/heinz/deviceai/routines/ContinuumRoutinesTest.kt" \
 "$review_root/tools/momega1-workspace-review/WorkspaceIndependentReviewTest.kt" > "$review_out/compile.log" 2>&1
review_code=$?; printf '%s\n' "$review_code" > "$review_out/compile.exit"
if [ "$review_code" -ne 0 ]; then cat "$review_out/compile.log"; exit "$review_code"; fi
"$review_java" -cp "$review_classes:$review_runtime_cp" org.junit.runner.JUnitCore at.heinz.deviceai.chief.ChiefWorkerPoolTest at.heinz.deviceai.routines.ContinuumRoutinesTest at.heinz.deviceai.chief.ChiefWorkspaceWiringTest > "$review_out/existing.log" 2>&1
printf '%s\n' "$?" > "$review_out/existing.exit"
"$review_java" -cp "$review_classes:$review_runtime_cp" org.junit.runner.JUnitCore at.heinz.deviceai.chief.WorkspaceIndependentReviewTest > "$review_out/adversarial.log" 2>&1
printf '%s\n' "$?" > "$review_out/adversarial.exit"
CONTINUUM_WORKSPACE_CHIEF_HOST_PROOF=1 CONTINUUM_WORKSPACE_CHIEF_EVIDENCE_DIR="$review_out/host" "$review_java" -Dcontinuum.workspace.repo="$review_target" -cp "$review_classes:$review_runtime_cp" org.junit.runner.JUnitCore at.heinz.deviceai.chief.ChiefWorkspaceWiringHostTest > "$review_out/host.log" 2>&1
printf '%s\n' "$?" > "$review_out/host.exit"
find "$review_classes" -name '*.class' -exec sha256sum '{}' \; > "$review_out/class-hashes.txt"
sha256sum /workspace/momega1-build-env/jvm-deps/*.jar > "$review_out/dependency-hashes.txt"
cat "$review_out/existing.log" "$review_out/adversarial.log" "$review_out/host.log"
rm -rf "$review_classes"

# Discard ephemeral synthetic authority/repositories; retain observations.
for review_case in pool-mailbox cancel; do
 rm -rf "$review_out/host/$review_case/worker-alpha" "$review_out/host/$review_case/worker-beta" "$review_out/host/$review_case/worker-alpha-state" "$review_out/host/$review_case/worker-beta-state"
 rm -f "$review_out/host/$review_case/synthetic-fixture.json"
done
