#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
out=${1:?pass an external output directory}
toolchain=${CONTINUUM_TOOLCHAIN:-/tmp/continuum-toolchain}
kotlinc=${KOTLINC:-$toolchain/kotlinc/bin/kotlinc}
java=${CONTINUUM_JAVA:-$toolchain/jdk/bin/java}
android=${CONTINUUM_ANDROID_JAR:-$toolchain/android-sdk/platforms/android-36/android.jar}
cp="$toolchain/junit.jar:$toolchain/hamcrest.jar:$android"
mkdir -p "$out"
out=$(cd "$out" && pwd -P)
[[ "$out" != "$root" && "$out" != "$root/"* ]] || exit 2
cd "$root"
sources=()
for name in CognitiveCore MemoryEcology HybridMemoryRetriever ExperienceCompiler VerifiedSkillReview IdentityState BehaviorProfile \
    SelfModel BehaviorUpdate CognitiveGenome IdentityUpdateValidator IdentityDrift ContinuumSeed ContinuumState ComputeFabric; do
    sources+=("app/src/main/java/at/heinz/deviceai/cognitive/$name.kt")
done
for name in AgentToolLayer ToolRegistry AgentProtocol CommandInterpreter ToolExecutionReceiptStore ToolExecutionStartLatch; do
    sources+=("app/src/main/java/at/heinz/deviceai/$name.kt")
done
sources+=(app/src/main/java/at/heinz/deviceai/cognitive/research/ContinuumMemorySkillResearch.kt)
tests=(app/src/test/java/at/heinz/deviceai/cognitive/research/ContinuumMemorySkillResearchTest.kt
    app/src/test/java/at/heinz/deviceai/cognitive/VerifiedSkillReviewTest.kt
    app/src/test/java/at/heinz/deviceai/cognitive/MemoryEcologyTest.kt
    app/src/test/java/at/heinz/deviceai/cognitive/MemoryStoreSnapshotTest.kt)
if [[ -f app/src/test/java/at/heinz/deviceai/cognitive/research/MemorySkillFourArmExperiment.kt ]]; then
    tests+=(app/src/test/java/at/heinz/deviceai/cognitive/research/MemorySkillFourArmExperiment.kt)
fi
git rev-parse HEAD > "$out/commit.txt"
git diff --stat > "$out/uncommitted.txt"
sha256sum "${sources[@]}" "${tests[@]}" scripts/research/run-continuum-memory-skills.sh > "$out/source-blobs.sha256"
"$kotlinc" -version > "$out/compiler.txt" 2>&1
"$kotlinc" "${sources[@]}" "${tests[@]}" -jvm-target 17 -cp "$cp" -include-runtime \
    -d "$out/tests.jar" > "$out/compile.txt" 2>&1
"$java" -cp "$out/tests.jar:$cp" org.junit.runner.JUnitCore \
    at.heinz.deviceai.cognitive.research.ContinuumMemorySkillResearchTest \
    at.heinz.deviceai.cognitive.VerifiedSkillReviewTest \
    at.heinz.deviceai.cognitive.MemoryEcologyTest \
    at.heinz.deviceai.cognitive.MemoryStoreSnapshotTest | tee "$out/junit.txt"
if [[ -f app/src/test/java/at/heinz/deviceai/cognitive/research/MemorySkillFourArmExperiment.kt ]]; then
    "$java" -cp "$out/tests.jar:$cp" at.heinz.deviceai.cognitive.research.MemorySkillFourArmExperiment "$out"
fi
