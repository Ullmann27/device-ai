#!/usr/bin/env bash
# Build an unsigned local artifact only, never an owner update.
set -uo pipefail
qa_repo=${1:?frozen checkout}; qa_sha=${2:?full source SHA}; qa_out=${3:?external evidence output}
source /workspace/momega1-build-env/env.sh
cd "$qa_repo" || exit 69
[[ $(git rev-parse HEAD) == "$qa_sha" ]] || exit 69
git diff --quiet HEAD -- app scripts .github tools backend || exit 69
mkdir -p "$qa_out"
qa_out=$(realpath "$qa_out")
[[ "$qa_out" != "$PWD" && "$qa_out" != "$PWD/"* ]] || exit 69
{ git rev-parse HEAD; git status --porcelain; } > "$qa_out/apk-source-before.txt"
qa_cpus=$(python3 -c 'import os; print(",".join(map(str, sorted(os.sched_getaffinity(0))[:2])))')
printf '%s\n' "$qa_cpus" > "$qa_out/apk-cpu-affinity.txt"
taskset -c "$qa_cpus" gradle --no-daemon --max-workers=2 --console=plain -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m -XX:ActiveProcessorCount=2' -PdeviceAiMentorPreview=true -PcontinuumLocalRenderUnsigned=true -PcontinuumStandaloneVersionCode=40 -Pkotlin.compiler.execution.strategy=in-process :app:assembleRelease > "$qa_out/gradle-unsigned-apk.txt" 2>&1
qa_build=$?
printf '%s\n' "$qa_build" > "$qa_out/gradle-unsigned-apk.exit"
[[ "$qa_build" == 0 ]] || exit "$qa_build"
qa_apk="$PWD/app/build/outputs/apk/release/app-release-unsigned.apk"
sha256sum "$qa_apk" > "$qa_out/APK-SHA256.txt"
stat -c 'apk_bytes=%s' "$qa_apk" > "$qa_out/APK-SIZE.txt"
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$qa_apk" > "$qa_out/APK-BADGING.txt" 2>&1
qa_badging=$?
printf '%s\n' "$qa_badging" > "$qa_out/APK-BADGING.exit"
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs "$qa_apk" > "$qa_out/APK-UNSIGNED-VERIFY.txt" 2>&1
qa_signer=$?
printf '%s\n' "$qa_signer" > "$qa_out/APK-UNSIGNED-VERIFY.exit"
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -c -p 4 "$qa_apk" > "$qa_out/APK-ZIPALIGN.txt" 2>&1
qa_align=$?
printf '%s\n' "$qa_align" > "$qa_out/APK-ZIPALIGN.exit"
{ git rev-parse HEAD; git status --porcelain; } > "$qa_out/apk-source-after.txt"
[[ $(git rev-parse HEAD) == "$qa_sha" ]] || exit 69
git diff --quiet HEAD -- app scripts .github tools backend || exit 69
[[ "$qa_badging" == 0 && "$qa_align" == 0 && "$qa_signer" != 0 ]] || exit 1
