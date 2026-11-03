#!/usr/bin/env bash
set -uo pipefail
source /workspace/momega1-build-env/env.sh
cd /workspace/momega1-build-env/guide-green-source-a39b614b || exit 69
qa_sha=72a646188afd7872423a135944f5f7b16a45d6d9
qa_out=/workspace/momega1-build-env/full-layout-72a64618
[[ $(git rev-parse HEAD) == "$qa_sha" ]] || exit 69
git diff --quiet HEAD -- app scripts .github tools backend || exit 69
mkdir "$qa_out" || exit 69
{ git rev-parse HEAD; git status --porcelain; git ls-files -s app scripts .github tools backend; } > "$qa_out/source-before.txt"
sha256sum /workspace/momega1-build-env/full-layout-independent/* /workspace/momega1-build-env/full-layout.init.gradle /workspace/momega1-build-env/run-full-layout.sh > "$qa_out/harness.sha256"
qa_cpus=$(python3 -c 'import os; print(",".join(map(str, sorted(os.sched_getaffinity(0))[:2])))')
printf '%s\n' "$qa_cpus" > "$qa_out/cpu-affinity.txt"
taskset -c "$qa_cpus" gradle --no-daemon --max-workers=2 --console=plain -I /workspace/momega1-build-env/full-layout.init.gradle -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m -XX:ActiveProcessorCount=2' -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=40 -PcontinuumUiWidgetTests=true -PcontinuumUiEvidenceDir="$qa_out/ui" -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest > "$qa_out/gradle.txt" 2>&1
qa_result=$?
printf '%s\n' "$qa_result" > "$qa_out/gradle.exit"
mkdir "$qa_out/xml"
cp app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.IndependentFullLayoutUiTest.xml "$qa_out/xml/"
{ git rev-parse HEAD; git status --porcelain; git ls-files -s app scripts .github tools backend; } > "$qa_out/source-after.txt"
cmp "$qa_out/source-before.txt" "$qa_out/source-after.txt" > "$qa_out/source-cmp.txt" 2>&1
printf '%s\n' "$?" > "$qa_out/source-cmp.exit"
exit "$qa_result"
