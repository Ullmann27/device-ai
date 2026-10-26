#!/usr/bin/env bash
# Native selected source graph; no fake Kotlin types, no APK/device claim.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
deps=${CONTINUUM_FABRIC_JVM_DEPS:?set the existing Kotlin/JUnit dependency directory}
out=${1:?external output directory}; mode=${2:-baseline}; split=${3:-dev}
mkdir -p "$out"; out=$(cd "$out" && pwd -P)
[[ "$out" != "$root" && "$out" != "$root/"* ]] || exit 2
[[ "$split" =~ ^(train|dev|holdout)$ ]] || exit 2
cp=$(find "$deps" -maxdepth 1 -name '*.jar' -type f -printf '%p:')
sources=()
for name in ComputeFabric ContinuumState ContinuumSeed ProofCarryingMemory MemoryEcology SelfModel CognitiveGenome CognitiveCore IdentityState BehaviorProfile BehaviorUpdate IdentityUpdateValidator IdentityDrift VirtualModelFabric ContinuumIntelligence Deliberation DevelopmentTypes DevelopmentProtocol DevelopmentUiController DevelopmentCoordinator LocalConversationContext LocalDialogueQualityGate; do
 sources+=("$root/app/src/main/java/at/heinz/deviceai/cognitive/$name.kt")
done
for name in AgentToolLayer ToolRegistry AgentProtocol ToolExecutionReceiptStore CommandInterpreter; do sources+=("$root/app/src/main/java/at/heinz/deviceai/$name.kt"); done
for name in ContinuumKnowledgeArchive ContinuumArchiveAnswerPolicy ContinuumLearningExperience LearningSnapshot TrainableSubwordEncoder Swb2ExperimentalEncoder Cre1InferenceEncoder TinyContextDecoder; do sources+=("$root/app/src/main/java/at/heinz/deviceai/learning/$name.kt"); done
sources+=("$root/app/src/main/java/at/heinz/deviceai/learning/context/Ctx3Context.kt")
while IFS= read -r f; do sources+=("$f"); done < <(find "$root/experimental/interactive-intelligence/src" "$root/experimental/interactive-intelligence/test" -name '*.kt' -type f | sort)
java -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -version > "$out/compiler.txt" 2>&1
java -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$cp" -jvm-target 17 -d "$out/classes" "${sources[@]}" > "$out/compile.txt" 2>&1
cd "$root"
if [[ "$mode" == baseline ]]; then
 java -cp "$out/classes:$cp" at.heinz.deviceai.research.interactive.LabBaseline "$root/experimental/interactive-intelligence/fixtures/$split.tsv" > "$out/baseline-$split.tsv"
elif [[ "$mode" == evaluate ]]; then
 java -cp "$out/classes:$cp" org.junit.runner.JUnitCore at.heinz.deviceai.research.interactive.InteractiveLabTest | tee "$out/junit.txt"
 java -cp "$out/classes:$cp" at.heinz.deviceai.research.interactive.LabEvaluation "$root/experimental/interactive-intelligence/fixtures/$split.tsv" "$out" > "$out/results-$split.tsv"
else echo 'unknown mode' >&2; exit 2; fi
git rev-parse HEAD > "$out/source-sha.txt"
