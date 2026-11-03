#!/usr/bin/env bash
set -euo pipefail
candidate=${1:?exact candidate SHA required}
output=${2:?new evidence directory required}
repo=$(git rev-parse --show-toplevel)
mkdir "$output"
output=$(cd "$output" && pwd)
snapshot=$(mktemp -d)
trap 'rm -rf "$snapshot"' EXIT
readiness_cp=$(cat /workspace/momega1-readiness/docs/evidence/momega1/readiness/d32fe38a/test-classpath.txt)
printf 'candidate=%s\n' "$(git rev-parse "$candidate^{commit}")" > "$output/source.txt"
for path in app/src/main/java/at/heinz/deviceai/AndroidAvatarCameraInput.kt app/src/main/java/at/heinz/deviceai/cognitive/AvatarCameraPolicy.kt app/src/main/java/at/heinz/deviceai/cognitive/ContinuumReadinessGuide.kt; do
 git show "$candidate:$path" > "$snapshot/$(basename "$path")"
 sha256sum "$snapshot/$(basename "$path")" >> "$output/source.txt"
done
cp tools/momega1-readiness-review/*.kt tools/momega1-readiness-review/ReviewJUnit.java "$snapshot/"
sha256sum tools/momega1-readiness-review/* > "$output/harness.sha256"
printf '%s\n' "$readiness_cp" > "$output/test-classpath.txt"
set +e
/workspace/momega1-build-env/jdk17/bin/java -XX:ActiveProcessorCount=1 -Xmx512m -cp '/workspace/momega1-build-env/jvm-deps/*' org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath "$readiness_cp" -d "$snapshot/tests.jar" "$snapshot"/*.kt > "$output/compile.txt" 2>&1
compile=$?;printf '%s\n' "$compile" > "$output/compile.exit"
if [ "$compile" -ne 0 ]; then cat "$output/compile.txt";exit "$compile";fi
sha256sum "$snapshot/tests.jar" > "$output/compiled.sha256"
/workspace/momega1-build-env/jdk17/bin/java -XX:ActiveProcessorCount=1 -Xmx512m -Drobolectric.offline=true -Drobolectric.dependency.dir=/workspace/momega1-android/app/build/ui-test-sdk -cp "$snapshot/tests.jar:$readiness_cp" "$snapshot/ReviewJUnit.java" at.heinz.deviceai.cognitive.IndependentCameraPolicyTest at.heinz.deviceai.cognitive.IndependentReadinessGuideTest at.heinz.deviceai.IndependentCameraCallbacksUiTest > "$output/tests.txt" 2>&1
result=$?;printf '%s\n' "$result" > "$output/tests.exit"
cat "$output/tests.txt"
exit "$result"
