#!/usr/bin/env bash
set -uo pipefail
qa_repo=${1:?checkout}; qa_sha=${2:?full source SHA}; qa_out=${3:?evidence directory}; qa_class=${4:?JUnit class}
[[ "$qa_class" =~ ^[A-Za-z0-9_.]+$ ]] || exit 69
source /workspace/momega1-build-env/env.sh
cd "$qa_repo" || exit 69
[[ $(git rev-parse HEAD) == "$qa_sha" ]] || exit 69
git diff --quiet HEAD -- app scripts .github tools backend || exit 69
mkdir -p "$qa_out"
qa_out=$(realpath "$qa_out")
cat > "$qa_out/test-filter.init.gradle" <<FILTER
allprojects {
 tasks.withType(Test).configureEach {
  executable = '/usr/bin/java'
  filter { includeTestsMatching '$qa_class' }
 }
}
FILTER
{ git rev-parse HEAD; git status --porcelain; git ls-files -s app scripts .github tools backend; } > "$qa_out/source-before.txt"
gradle --no-daemon --max-workers=2 --console=plain -I "$qa_out/test-filter.init.gradle" -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=40 -PcontinuumUiWidgetTests=true -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest > "$qa_out/gradle.txt" 2>&1
qa_exit=$?
printf '%s\n' "$qa_exit" > "$qa_out/gradle.exit"
[[ -f "app/build/test-results/testDebugUnitTest/TEST-$qa_class.xml" ]] && cp "app/build/test-results/testDebugUnitTest/TEST-$qa_class.xml" "$qa_out/junit.xml"
{ git rev-parse HEAD; git status --porcelain; git ls-files -s app scripts .github tools backend; } > "$qa_out/source-after.txt"
[[ $(git rev-parse HEAD) == "$qa_sha" ]] || exit 69
git diff --quiet HEAD -- app scripts .github tools backend || exit 69
exit "$qa_exit"
