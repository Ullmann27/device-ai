#!/usr/bin/env bash
# Native selected Kotlin source graph + shared Kotlin→Node contract.
# Requires official Kotlin compiler-embeddable and dependencies, JUnit4 and real
# android-all framework jar in CONTINUUM_FABRIC_JVM_DEPS. No source/test doubles.
# Not Gradle/AGP, LiteRT inference, an APK, or an Android device test.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
deps=${CONTINUUM_FABRIC_JVM_DEPS:?set CONTINUUM_FABRIC_JVM_DEPS to the compiler/dependency jar directory}
out=${1:?pass an external output directory}
mkdir -p "$out"
out=$(cd "$out" && pwd -P)
[[ "$out" != "$root" && "$out" != "$root/"* ]] || { echo 'Use output outside source' >&2; exit 2; }
cp=$(find "$deps" -maxdepth 1 -name '*.jar' -type f -printf '%p:' | sort)
sources=()
for name in ComputeFabric ContinuumState ContinuumSeed ProofCarryingMemory MemoryEcology SelfModel \
    CognitiveGenome CognitiveCore IdentityState BehaviorProfile BehaviorUpdate IdentityUpdateValidator IdentityDrift VirtualModelFabric \
    ContinuumIntelligence Deliberation DevelopmentTypes DevelopmentProtocol DevelopmentUiController DevelopmentCoordinator; do
    sources+=("$root/app/src/main/java/at/heinz/deviceai/cognitive/$name.kt")
done
for name in AgentToolLayer ToolRegistry AgentProtocol ToolExecutionReceiptStore CommandInterpreter; do
    sources+=("$root/app/src/main/java/at/heinz/deviceai/$name.kt")
done
for name in ComputeFabricTest ComputeResultVerifierTest VirtualModelFabricTest DevelopmentClaimGateTest; do
    sources+=("$root/app/src/test/java/at/heinz/deviceai/cognitive/$name.kt")
done
java -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -version > "$out/compiler.txt" 2>&1
java -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$cp" \
    -jvm-target 17 -d "$out/classes" "${sources[@]}" > "$out/compile.txt" 2>&1
cd "$root"
runtimeCp="$out/classes:$root/app/src/test/resources:$cp"
java -cp "$runtimeCp" org.junit.runner.JUnitCore \
    at.heinz.deviceai.cognitive.ComputeFabricTest \
    at.heinz.deviceai.cognitive.ComputeResultVerifierTest \
    at.heinz.deviceai.cognitive.VirtualModelFabricTest \
    at.heinz.deviceai.cognitive.DevelopmentClaimGateTest | tee "$out/junit.txt"
java -cp "$runtimeCp" at.heinz.deviceai.cognitive.VirtualModelFabricFixtureMain > "$out/registry-snapshot.json"
CONTINUUM_FABRIC_SNAPSHOT_FILE="$out/registry-snapshot.json" node --test \
    .github/continuum/compute-fabric-model-adapter.test.mjs | tee "$out/node-contract.txt"
