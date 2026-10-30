#!/usr/bin/env bash
# EXP-20 P2 host laboratory build/run (NOT an Android/Gradle build, NOT a Fold7 measurement).
#
#   KOTLINC=/path/kotlinc/bin/kotlinc JUNIT_JAR=... HAMCREST_JAR=... OUT=/tmp/exp20 \
#     scripts/exp20-p2-host-run.sh build
#   scripts/exp20-p2-host-run.sh junit <fqcn>...        # JUnitCore on the built jar
#   scripts/exp20-p2-host-run.sh main <fqcn> [args]...   # run a main class
#
# The real decision path (ComputeAllocationPolicy, ContinuumTaskClassifier, DialogStateKernel) is compiled from
# the unmodified app/src/main files listed below. CommandInterpreter.kt imports android.content.Context, so its
# first 25 lines (enum Risk, CommandStep, CommandPlan) are copied verbatim without that import into a host-only
# shim under $OUT; the shim never enters app/src, so the Android build cannot see a duplicate class.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
: "${KOTLINC:?set KOTLINC}" "${JUNIT_JAR:?set JUNIT_JAR}" "${HAMCREST_JAR:?set HAMCREST_JAR}" "${OUT:?set OUT}"
MAIN="$ROOT/app/src/main/java"
TEST="$ROOT/app/src/test/java"
CLOSURE=(
  at/heinz/deviceai/AgentProtocol.kt
  at/heinz/deviceai/AgentToolLayer.kt
  at/heinz/deviceai/ToolExecutionReceiptStore.kt
  at/heinz/deviceai/ToolRegistry.kt
  at/heinz/deviceai/cognitive/BehaviorProfile.kt
  at/heinz/deviceai/cognitive/BehaviorUpdate.kt
  at/heinz/deviceai/cognitive/CognitiveCore.kt
  at/heinz/deviceai/cognitive/CognitiveGenome.kt
  at/heinz/deviceai/cognitive/ContinuumIntelligence.kt
  at/heinz/deviceai/cognitive/Deliberation.kt
  at/heinz/deviceai/cognitive/HybridMemoryRetriever.kt
  at/heinz/deviceai/cognitive/IdentityDrift.kt
  at/heinz/deviceai/cognitive/IdentityState.kt
  at/heinz/deviceai/cognitive/IdentityUpdateValidator.kt
  at/heinz/deviceai/cognitive/MemoryEcology.kt
  at/heinz/deviceai/cognitive/SelfModel.kt
  at/heinz/deviceai/intelligence/ewm/Ewm1Lexicon.kt
  at/heinz/deviceai/intelligence/ewm/Ewm1EvidenceWorkspace.kt
  at/heinz/deviceai/learning/ContinuumLearningExperience.kt
  at/heinz/deviceai/learning/interactive/ResearchFeedback.kt
  at/heinz/deviceai/learning/interactive/ResearchFeedbackFileLock.kt
  at/heinz/deviceai/learning/span/ContinuumResidualSpanCore.kt
  at/heinz/deviceai/learning/Cre1InferenceEncoder.kt
  at/heinz/deviceai/learning/LearningSnapshot.kt
  at/heinz/deviceai/learning/TrainableSubwordEncoder.kt
  at/heinz/deviceai/research/argon4/ComputeAllocationPolicy.kt
  at/heinz/deviceai/research/argon4/DialogStateKernel.kt
  at/heinz/deviceai/research/argon4/JustifiedRelationStore.kt
  at/heinz/deviceai/ui/ContinuumDashboardState.kt
)
CP="$OUT/exp20.jar:$JUNIT_JAR:$HAMCREST_JAR"

case "${1:-}" in
  build)
    mkdir -p "$OUT/shim/at/heinz/deviceai"
    sed -n '1,25p' "$MAIN/at/heinz/deviceai/CommandInterpreter.kt" | grep -v '^import android\.' \
      > "$OUT/shim/at/heinz/deviceai/HostShimRisk.kt"
    srcs=("$OUT/shim/at/heinz/deviceai/HostShimRisk.kt")
    for f in "${CLOSURE[@]}"; do srcs+=("$MAIN/$f"); done
    while IFS= read -r f; do srcs+=("$f"); done < <(find "$TEST/at/heinz/deviceai/research/exp20" -name '*.kt' | sort)
    if [[ "${WITH_ARGON_TEST:-0}" == 1 ]]; then srcs+=("$TEST/at/heinz/deviceai/research/argon4/Argon4F1PrototypeTest.kt"); fi
    for f in "${srcs[@]}"; do sha256sum "$f"; done | sed "s|$ROOT/||; s|$OUT/|\$OUT/|" > "$OUT/build-inputs.sha256"
    "$KOTLINC" "${srcs[@]}" -cp "$JUNIT_JAR:$HAMCREST_JAR" -jvm-target 17 -include-runtime -d "$OUT/exp20.jar"
    echo "BUILD_OK $(sha256sum "$OUT/exp20.jar" | cut -c1-64) sources=${#srcs[@]}"
    ;;
  junit) shift; java -cp "$CP" org.junit.runner.JUnitCore "$@" ;;
  main) shift; cls="$1"; shift; java ${JAVA_OPTS:-} -cp "$CP" "$cls" "$@" ;;
  *) echo "usage: $0 build|junit <classes>|main <class> [args]" >&2; exit 2 ;;
esac
