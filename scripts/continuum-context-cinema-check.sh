#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
out=${1:?external output directory}
junit=${CONTINUUM_JUNIT_JARS:?JUnit4 and Hamcrest classpath}
compiler=${KOTLINC:-kotlinc}
mkdir -p "$out"
out=$(cd "$out" && pwd -P)
[[ "$out" != "$root" && "$out" != "$root/"* ]] || exit 2
sources=("$root/app/src/main/java/at/heinz/deviceai/learning/context/Ctx3Context.kt")
for name in LocalConversationFollowUpPolicy ConversationSituationModel LocalConversationSituationTracker \
    InternetConsentPolicy LocalSpeechInputPolicy ContinuousConversationMode SpeechRecoveryPolicy SpeechTurnRecovery \
    ApprovedStreamingSpeechAssembler JarvisVoiceProfile VoiceTurnLatencyTrace; do
    sources+=("$root/app/src/main/java/at/heinz/deviceai/cognitive/$name.kt")
done
classes=(at.heinz.deviceai.Ctx3ContextTest at.heinz.deviceai.Ctx3OrderedCorrectionTest)
for name in LocalConversationFollowUpPolicy ContextDiscoursePrefix ConversationSituationModel \
    LocalConversationSituationTracker SituationReturnMorphology ContinuousConversationMode SpeechRecoveryPolicy \
    SpeechTurnRecovery ApprovedStreamingSpeechAssembler JarvisVoiceProfile VoiceTurnLatencyTrace; do
    classes+=("at.heinz.deviceai.cognitive.${name}Test")
done
for name in "${classes[@]}"; do
    sources+=("$root/app/src/test/java/${name//./\/}.kt")
done
git -C "$root" rev-parse HEAD > "$out/head.txt"
git -C "$root" status --short > "$out/worktree.txt"
sha256sum "${sources[@]}" > "$out/source-sha256.txt"
"$compiler" -version > "$out/toolchain.txt" 2>&1
java -version >> "$out/toolchain.txt" 2>&1
"$compiler" "${sources[@]}" -jvm-target 17 -cp "$junit" -include-runtime \
    -d "$out/context.jar" > "$out/compile.txt" 2>&1
java -cp "$out/context.jar:$junit" org.junit.runner.JUnitCore "${classes[@]}" | tee "$out/junit.txt"
