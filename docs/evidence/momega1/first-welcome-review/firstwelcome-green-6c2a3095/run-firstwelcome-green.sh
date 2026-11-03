#!/usr/bin/env bash
set -uo pipefail
source /workspace/momega1-build-env/env.sh
cd /workspace/momega1-build-env/guide-green-source-a39b614b || exit 69
qa_sha=6c2a30955fee800d6ddeffde9206158ea43537e9
qa_out=/workspace/momega1-build-env/firstwelcome-green-6c2a3095
[[ $(git rev-parse HEAD) == "$qa_sha" ]] || exit 69
git diff --quiet HEAD -- app scripts .github tools backend || exit 69
mkdir "$qa_out" || exit 69
{ git rev-parse HEAD; git status --porcelain; git ls-files -s app scripts .github tools backend; } > "$qa_out/source-before.txt"
sha256sum /workspace/momega1-build-env/firstwelcome-independent-v2/* /workspace/momega1-build-env/guide-independent-caller-final/* /workspace/momega1-build-env/firstwelcome-green.init.gradle /workspace/momega1-build-env/run-firstwelcome-green.sh > "$qa_out/harness.sha256"
gradle --no-daemon --max-workers=2 --console=plain -I /workspace/momega1-build-env/firstwelcome-green.init.gradle -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=40 -PcontinuumUiWidgetTests=true -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest > "$qa_out/gradle.txt" 2>&1
qa_result=$?
printf '%s\n' "$qa_result" > "$qa_out/gradle.exit"
mkdir "$qa_out/xml"
cp app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.ContinuumFirstVoiceUiTest.xml app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.IndependentFirstWelcomeUiTest.xml app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.IndependentReadinessVoiceCompletionUiTest.xml app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.ContinuumReadinessGuideUiTest.xml app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.IndependentReadinessCallerUiTest.xml "$qa_out/xml/"
{ git rev-parse HEAD; git status --porcelain; git ls-files -s app scripts .github tools backend; } > "$qa_out/source-after.txt"
cmp "$qa_out/source-before.txt" "$qa_out/source-after.txt" > "$qa_out/source-cmp.txt" 2>&1
printf '%s\n' "$?" > "$qa_out/source-cmp.exit"
exit "$qa_result"
