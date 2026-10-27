#!/usr/bin/env bash
# Real selected Kotlin/JVM source graph. No Android UI, SDK or model-inference claim.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
deps=${CONTINUUM_FABRIC_JVM_DEPS:?set path to official Kotlin/JUnit/android-all dependency jars}
out=${1:?pass an external output directory}
mkdir -p "$out"
out=$(cd "$out" && pwd -P)
[[ "$out" != "$root" && "$out" != "$root/"* ]] || exit 2
cp=$(find "$deps" -maxdepth 1 -name '*.jar' -type f -printf '%p:')
[[ -n "$cp" ]] || exit 69
sources=()
for name in ComputeFabric ContinuumState ContinuumSeed ContinuumSeedStore ProofCarryingMemory MemoryEcology SelfModel \
    CognitiveGenome CognitiveCore IdentityState BehaviorProfile BehaviorUpdate IdentityUpdateValidator IdentityDrift \
    CognitivePersistence ContinuumRuntimeStore Deliberation BoundedAgent AgentCheckpointStore HybridMemoryRetriever ExperienceCompiler ReadOnlyToolPlan; do
    sources+=("$root/app/src/main/java/at/heinz/deviceai/cognitive/$name.kt")
done
for name in AgentToolLayer ToolRegistry AgentProtocol ToolExecutionReceiptStore CommandInterpreter BootstrapBrain BootstrapTraining DeviceStorageProbe; do
    sources+=("$root/app/src/main/java/at/heinz/deviceai/$name.kt")
done
for name in LearningSnapshot TinyNeuralNetwork NeuralDialogue StructuredKnowledge OnlineNaiveBayes; do
    sources+=("$root/app/src/main/java/at/heinz/deviceai/learning/$name.kt")
done
tests=(BoundedAgentTest BoundedAgentOutcomeEvidenceTest ExperienceEvidenceRegressionTest ReadOnlyToolPlanTest)
for name in "${tests[@]}"; do
    sources+=("$root/app/src/test/java/at/heinz/deviceai/cognitive/$name.kt")
done
sha256sum "${sources[@]}" > "$out/source-blobs.sha256"
java -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -version > "$out/compiler.txt" 2>&1
java -Xmx768m -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect \
    -classpath "$cp" -jvm-target 17 -d "$out/classes" "${sources[@]}" > "$out/compile.txt" 2>&1
classes=()
for name in "${tests[@]}"; do classes+=("at.heinz.deviceai.cognitive.$name"); done
java -Xmx512m -Dcontinuum.action.evidenceDir="$out" -cp "$out/classes:$cp" org.junit.runner.JUnitCore "${classes[@]}" | tee "$out/junit.txt"
