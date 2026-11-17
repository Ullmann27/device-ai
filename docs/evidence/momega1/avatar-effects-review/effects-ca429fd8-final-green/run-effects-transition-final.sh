#!/usr/bin/env bash
set -uo pipefail
source /workspace/momega1-build-env/env.sh
cd /workspace/momega1-build-env/guide-green-source-a39b614b || exit 69
qa_sha=ca429fd81007473f1b0c0e66be6112f78f652613
qa_out=/workspace/momega1-build-env/effects-final-ca429fd8
[[ $(git rev-parse HEAD) == "$qa_sha" ]] || exit 69
git diff --quiet HEAD -- app scripts .github tools backend || exit 69
mkdir "$qa_out" || exit 69
{ git rev-parse HEAD; git status --porcelain; git ls-files -s app scripts .github tools backend; } > "$qa_out/source-before.txt"
sha256sum /workspace/momega1-build-env/effects-independent/* /workspace/momega1-build-env/effects-combined.init.gradle /workspace/momega1-build-env/run-effects-transition-final.sh > "$qa_out/harness.sha256"
qa_cpus=$(python3 -c 'import os; print(",".join(map(str, sorted(os.sched_getaffinity(0))[:2])))')
printf '%s\n' "$qa_cpus" > "$qa_out/cpu-affinity.txt"
taskset -c "$qa_cpus" gradle --no-daemon --max-workers=2 --console=plain -I /workspace/momega1-build-env/effects-combined.init.gradle -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m -XX:ActiveProcessorCount=2' -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=40 -PcontinuumUiWidgetTests=true -PcontinuumUiEvidenceDir="$qa_out/ui" -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest > "$qa_out/gradle.txt" 2>&1
qa_result=$?
printf '%s\n' "$qa_result" > "$qa_out/gradle.exit"
mkdir "$qa_out/xml"
cp app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.ui.IndependentTaskEffectsTest.xml app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.IndependentTaskEffectsBoundaryUiTest.xml app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.IndependentUiFeedbackUiTest.xml app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.ui.ContinuumTaskEffectsTest.xml app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.ContinuumTaskEffectsUiTest.xml app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.ContinuumUiFeedbackTest.xml app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.ContinuumHologramMotionUiTest.xml app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.HologramAudioTest.xml app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.TtsHologramSignalTest.xml app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.ui.CognitiveWorkPresentationTest.xml app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.ContinuumAvatarNativeUiTest.xml app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.ContinuumAvatar3dLifecycleUiTest.xml "$qa_out/xml/"
{ git rev-parse HEAD; git status --porcelain; git ls-files -s app scripts .github tools backend; } > "$qa_out/source-after.txt"
cmp "$qa_out/source-before.txt" "$qa_out/source-after.txt" > "$qa_out/source-cmp.txt" 2>&1
printf '%s\n' "$?" > "$qa_out/source-cmp.exit"
exit "$qa_result"
