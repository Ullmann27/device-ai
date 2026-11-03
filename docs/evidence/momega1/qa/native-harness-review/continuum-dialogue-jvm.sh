#!/usr/bin/env bash
# Native existing memory/CRE1/CTX3 graph, no APK, private chat or weight promotion.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
deps=${CONTINUUM_FABRIC_JVM_DEPS:?official Kotlin/JUnit/JSON/android-all/OkHttp/Okio jars}
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
# OpenWebStudyFetch belongs only to the excluded Android public-study job service,
# not to the memory/CRE1/CTX3 graph exercised below.
for file in "$root"/app/src/main/java/at/heinz/deviceai/learning/*.kt; do
    case "$(basename "$file")" in
        MentorSource.kt) ;;
        ContinuumAutonomousScheduler.kt|ContinuumDeviceTeamleaderScheduler.kt|ContinuumPublicStudyScheduler.kt|ContinuumOpenWebStudyFetch.kt|Mentor*.kt|NeuralPracticeScheduler.kt|ContinuumTrainingMonitor.kt|ContinuumPublicStudyCycleResult.kt) continue ;;
    esac
    sources+=("$file")
done
for file in "$root"/app/src/main/java/at/heinz/deviceai/learning/context/*.kt \
    "$root"/app/src/main/java/at/heinz/deviceai/learning/span/*.kt; do sources+=("$file"); done
# Actual transitive source dependencies of the selected graph; no substitute API classes.
for file in \
    app/src/main/java/at/heinz/deviceai/ToolExecutionStartLatch.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/AdaptiveIdentity.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/AgentExperienceRecall.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/AgentTaskEvidence.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/AuthorizedConversationMemoryShadow.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/BoundedResearchTextSanitizer.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/CertifiedLocationReflex.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/ContinuumBenchmark.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/DevelopmentBacklog.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/DevelopmentBacklogStore.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/DevelopmentProtocol.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/DevelopmentTypes.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/DeviceDevelopmentBacklog.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/ExternalContentActionGuard.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/FreshKnowledgeNeed.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/GeneralWebDiscoveryProvider.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/InternetConsentPolicy.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/JarvisIntentIntake.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/JarvisUnifiedEvidenceContract.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/LocalClockIntent.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/NetworkTurnConstraint.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/OwnerChatIntentRouter.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/PinnedResearchHttpsConnection.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/PublicKnowledgeTopicRouter.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/Research.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/ResearchDialogueHandoff.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/ResearchEngine.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/ResearchNegationSignals.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/ResearchQuestionFocus.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/TemporalRoleEvidenceLedger.kt \
    app/src/main/java/at/heinz/deviceai/cognitive/VerifiedSkillReview.kt \
    app/src/main/java/at/heinz/deviceai/intelligence/ewm/Ewm1Brain.kt \
    app/src/main/java/at/heinz/deviceai/intelligence/ewm/Ewm1EvidenceWorkspace.kt \
    app/src/main/java/at/heinz/deviceai/intelligence/ewm/Ewm1Lexicon.kt \
    app/src/main/java/at/heinz/deviceai/intelligence/ewm/Ewm1SourceIndex.kt \
    app/src/main/java/at/heinz/deviceai/intelligence/ewm/Ewm1Workspace.kt; do
    sources+=("$root/$file")
done
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
