#!/usr/bin/env bash
# Actual Kotlin/JUnit checks of pure production classes. Not Gradle, lint,
# Android framework/UI, an APK, device behavior or a replacement for Render QA.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
: "${CONTINUUM_KOTLIN_COMPILER_CP:?Kotlin compiler/dependency classpath}"
: "${CONTINUUM_KOTLIN_STDLIB:?Kotlin stdlib jar}"
: "${CONTINUUM_JUNIT_CP:?JUnit4, Hamcrest and org.json jars}"
out=${1:?isolated output directory}
mkdir -p "$out"
main=(LearningSnapshot TinyContextDecoder TrainableSubwordEncoder ContinuumPairedEncoderBinding
  ContinuumAtomicPairedBundleStore ContinuumDecoderBridge DecoderGenerativeGate
  ContinuumLearningExperience ContinuumOrthographyProbe ContinuumMemoryDialogue OwnerAttributeEvidenceScope ContinuumEvidenceWindow OwnerMemoryPerspective)
tests=(DecoderGenerativePromotionTest DecoderGenerativeGateTest ContinuumDecoderBridgeTest
  TinyContextDecoderTest ContinuumOwnerRoleDialogueTest TrainableSubwordEncoderTest ContinuumEvidenceWindowTest OwnerMemoryPerspectiveTest)
files=()
classes=()
for name in "${main[@]}"; do files+=("$root/app/src/main/java/at/heinz/deviceai/learning/$name.kt"); done
for name in Ewm1Lexicon Ewm1EvidenceWorkspace Ewm1Workspace; do
  files+=("$root/app/src/main/java/at/heinz/deviceai/intelligence/ewm/$name.kt")
done
for name in "${tests[@]}"; do
  files+=("$root/app/src/test/java/at/heinz/deviceai/learning/$name.kt")
  classes+=("at.heinz.deviceai.learning.$name")
done
java -cp "$CONTINUUM_KOTLIN_COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 17 -classpath "$CONTINUUM_KOTLIN_STDLIB:$CONTINUUM_JUNIT_CP" \
  -d "$out" "${files[@]}"
java -Xmx512m -cp "$out:$CONTINUUM_KOTLIN_STDLIB:$CONTINUUM_JUNIT_CP:$root/app/src/test/resources" \
  org.junit.runner.JUnitCore "${classes[@]}"
echo 'SOL314_HOST_KOTLIN_JUNIT_CHECKED_NOT_ANDROID_OR_DEVICE'
