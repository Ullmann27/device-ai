#!/usr/bin/env bash
# Native Kotlin/JUnit for the actual disposable Neuron Demo source graph.
# Never loads owner data or signs an Android APK.
set -Eeuo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
deps=${CONTINUUM_FABRIC_JVM_DEPS:?set existing Kotlin/JUnit jar directory}
out=${1:?external output directory}
mkdir -p "$out"
out=$(cd "$out" && pwd -P)
[[ "$out" != "$root" && "$out" != "$root/"* ]] || exit 2
cp=$(find "$deps" -maxdepth 1 -name '*.jar' -type f -printf '%p:')
[[ -n "$cp" ]] || { echo "NO_KOTLIN_JUNIT_DEPS" >&2; exit 69; }
sources=(
 "$root/app/src/main/java/at/heinz/deviceai/learning/LearningSnapshot.kt"
 "$root/app/src/main/java/at/heinz/deviceai/learning/TinyNeuralNetwork.kt"
 "$root/app/src/main/java/at/heinz/deviceai/learning/NeuralDialogue.kt"
 "$root/app/src/main/java/at/heinz/deviceai/learning/NeuralDemoSandbox.kt"
 "$root/app/src/test/java/at/heinz/deviceai/learning/NeuralDemoSandboxTest.kt"
)
sha256sum "${sources[@]}" > "$out/source-blobs.sha256"
java -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect \
  -classpath "$cp" -jvm-target 17 -d "$out/classes" "${sources[@]}" \
  > "$out/compile.txt" 2>&1
java -cp "$out/classes:$cp" org.junit.runner.JUnitCore \
  at.heinz.deviceai.learning.NeuralDemoSandboxTest | tee "$out/junit.txt"
