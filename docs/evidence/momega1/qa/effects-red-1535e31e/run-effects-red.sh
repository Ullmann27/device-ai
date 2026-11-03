#!/usr/bin/env bash
set -uo pipefail
source /workspace/momega1-build-env/env.sh
cd /workspace/momega1-build-env/guide-green-source-a39b614b || exit 69
qa_sha=1535e31e3659c7486d4a0a3bd93a766b1f0e61ac
qa_out=/workspace/momega1-build-env/effects-red-1535e31e
[[ $(git rev-parse HEAD) == "$qa_sha" ]] || exit 69
git diff --quiet HEAD -- app scripts .github tools backend || exit 69
mkdir "$qa_out" || exit 69
{ git rev-parse HEAD; git status --porcelain; git ls-files -s app scripts .github tools backend; } > "$qa_out/source-before.txt"
sha256sum /workspace/momega1-build-env/effects-red.init.gradle /workspace/momega1-build-env/run-effects-red.sh > "$qa_out/harness.sha256"
gradle --no-daemon --max-workers=2 --console=plain -I /workspace/momega1-build-env/effects-red.init.gradle -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=40 -PcontinuumUiWidgetTests=true -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest > "$qa_out/gradle.txt" 2>&1
qa_result=$?
printf '%s\n' "$qa_result" > "$qa_out/gradle.exit"
mkdir "$qa_out/xml"
cp app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.ui.ContinuumTaskEffectsTest.xml app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.ContinuumTaskEffectsUiTest.xml app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.ContinuumUiFeedbackTest.xml "$qa_out/xml/"
{ git rev-parse HEAD; git status --porcelain; git ls-files -s app scripts .github tools backend; } > "$qa_out/source-after.txt"
cmp "$qa_out/source-before.txt" "$qa_out/source-after.txt" > "$qa_out/source-cmp.txt" 2>&1
printf '%s\n' "$?" > "$qa_out/source-cmp.exit"
exit "$qa_result"
