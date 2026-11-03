#!/usr/bin/env bash
set -uo pipefail
source /workspace/momega1-build-env/env.sh
cd /workspace/momega1-android || exit 69
qa_sha=4b98da9362f91ab9930a7f4b279737a9e80f94bd
qa_out=/workspace/momega1-build-env/planning-final-4b98da93
[[ $(git rev-parse HEAD) == "$qa_sha" ]] || exit 69
git diff --quiet HEAD -- app scripts .github tools backend || exit 69
mkdir "$qa_out" || exit 69
{ git rev-parse HEAD; git status --porcelain; git ls-files -s app scripts .github tools backend; } > "$qa_out/source-before.txt"
sha256sum /workspace/momega1-build-env/planning-independent-final/* /workspace/momega1-build-env/android-independent-final/* /workspace/momega1-build-env/planning-final.init.gradle /workspace/momega1-build-env/run-planning-final.sh > "$qa_out/harness.sha256"
gradle --no-daemon --max-workers=2 --console=plain -I /workspace/momega1-build-env/planning-final.init.gradle -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=40 -PcontinuumUiWidgetTests=true -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest > "$qa_out/gradle.txt" 2>&1
qa_result=$?
printf '%s\n' "$qa_result" > "$qa_out/gradle.exit"
mkdir "$qa_out/xml"
cp app/build/test-results/testDebugUnitTest/TEST-*.xml "$qa_out/xml/"
{ git rev-parse HEAD; git status --porcelain; git ls-files -s app scripts .github tools backend; } > "$qa_out/source-after.txt"
cmp "$qa_out/source-before.txt" "$qa_out/source-after.txt" > "$qa_out/source-cmp.txt" 2>&1
printf '%s\n' "$?" > "$qa_out/source-cmp.exit"
exit "$qa_result"
