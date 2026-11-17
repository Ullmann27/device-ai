#!/usr/bin/env bash
# Native existing memory/CRE1/CTX3 graph, no APK, private chat or weight promotion.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
deps=${CONTINUUM_FABRIC_JVM_DEPS:?official Kotlin/JUnit/android-all jars}
out=${1:?external output directory}
mkdir -p "$out"
out=$(cd "$out" && pwd -P)
[[ "$out" != "$root" && "$out" != "$root/"* ]] || exit 2
cp=$(find "$deps" -maxdepth 1 -name '*.jar' -type f -printf '%p:')
sources=()
for name in ComputeFabric ContinuumState ContinuumSeed ContinuumSeedStore ProofCarryingMemory MemoryEcology SelfModel \
    CognitiveGenome CognitiveCore IdentityState BehaviorProfile BehaviorUpdate IdentityUpdateValidator IdentityDrift \
    CognitivePersistence ContinuumRuntimeStore Deliberation BoundedAgent AgentCheckpointStore HybridMemoryRetriever ExperienceCompiler \
    ReadOnlyToolPlan CognitiveTurnTrace ContinuumIntelligence CognitiveDecisionBridge LocalConversationContext; do
    sources+=("$root/app/src/main/java/at/heinz/deviceai/cognitive/$name.kt")
done
for name in AgentToolLayer ToolRegistry AgentProtocol ToolExecutionReceiptStore CommandInterpreter BootstrapBrain BootstrapTraining; do
    sources+=("$root/app/src/main/java/at/heinz/deviceai/$name.kt")
done
for file in "$root"/app/src/main/java/at/heinz/deviceai/learning/*.kt; do
    case "$(basename "$file")" in
        MentorSource.kt) ;;
        ContinuumAutonomousScheduler.kt|ContinuumDeviceTeamleaderScheduler.kt|ContinuumPublicStudyScheduler.kt|Mentor*.kt|NeuralPracticeScheduler.kt|ContinuumTrainingMonitor.kt|ContinuumPublicStudyCycleResult.kt) continue ;;
    esac
    sources+=("$file")
done
for file in "$root"/app/src/main/java/at/heinz/deviceai/learning/context/*.kt \
    "$root"/app/src/main/java/at/heinz/deviceai/learning/span/*.kt; do sources+=("$file"); done
classes=(at.heinz.deviceai.learning.ContinuumCognitiveRuntimeTest at.heinz.deviceai.learning.ContinuumDialogueConversationBindingTest
    at.heinz.deviceai.learning.ContinuumMemoryDialogueTest at.heinz.deviceai.learning.Cre1PromotionGateTest
    at.heinz.deviceai.Ctx3ContextTest at.heinz.deviceai.Ctx3SessionTest at.heinz.deviceai.Ctx3ShadowTest)
for name in "${classes[@]}"; do
    relative=${name//./\/};sources+=("$root/app/src/test/java/$relative.kt")
done
sha256sum "${sources[@]}" > "$out/source-blobs.sha256"
java -Xmx1024m -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect \
    -classpath "$cp" -jvm-target 17 -d "$out/classes" "${sources[@]}" > "$out/compile.txt" 2>&1
java -Xmx768m -cp "$out/classes:$root/app/src/test/resources:$cp" org.junit.runner.JUnitCore "${classes[@]}" | tee "$out/junit.txt"
