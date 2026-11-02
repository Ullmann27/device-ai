#!/usr/bin/env bash
# Selected actual Kotlin source graph only; no Android SDK/APK/owner activation.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
deps=${CONTINUUM_FABRIC_JVM_DEPS:?set existing Kotlin/JUnit jar directory}
out=${1:?external output directory}; mkdir -p "$out"; out=$(cd "$out" && pwd -P)
[[ "$out" != "$root" && "$out" != "$root/"* ]] || exit 2
cp=$(find "$deps" -maxdepth 1 -name '*.jar' -type f -printf '%p:')
sources=("$root/app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningExperience.kt"
 "$root/app/src/main/java/at/heinz/deviceai/learning/interactive/ResearchFeedbackFileLock.kt"
 "$root/app/src/main/java/at/heinz/deviceai/learning/interactive/ResearchFeedback.kt"
 "$root/app/src/test/java/at/heinz/deviceai/learning/interactive/ResearchFeedbackTest.kt"
 "$root/app/src/test/java/at/heinz/deviceai/learning/interactive/ResearchFeedbackMultiprocessTest.kt")
sha256sum "${sources[@]}" > "$out/source-blobs.sha256"
java -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$cp" -jvm-target 17 -d "$out/classes" "${sources[@]}" > "$out/compile.txt" 2>&1
java -cp "$out/classes:$cp" org.junit.runner.JUnitCore at.heinz.deviceai.learning.interactive.ResearchFeedbackTest at.heinz.deviceai.learning.interactive.ResearchFeedbackMultiprocessTest | tee "$out/junit.txt"
