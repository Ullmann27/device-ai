#!/bin/bash
set -uo pipefail
source /workspace/continuum-build-env/env.sh
qa_out=/workspace/continuum-quality-runs/combined
[[ "$(git rev-parse HEAD)" == 64ec06a73f50cae9ee49035c840710d87777d50f ]] || exit 69
git diff --quiet HEAD -- app scripts .github tools backend || exit 69
gradle --no-daemon --max-workers=2 --console=plain -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' -PdeviceAiMentorPreview=true -PcontinuumLocalRenderUnsigned=true -PcontinuumStandaloneVersionCode=40 -Pkotlin.compiler.execution.strategy=in-process :app:assembleRelease > "$qa_out/gradle-unsigned-apk.log" 2>&1
qa_build=$?
printf 'exit_code=%s\n' "$qa_build" > "$qa_out/gradle-unsigned-apk.exit"
[[ "$qa_build" == 0 ]] || exit "$qa_build"
qa_apk="$PWD/app/build/outputs/apk/release/app-release-unsigned.apk"
sha256sum "$qa_apk" > "$qa_out/APK-SHA256.txt"
stat -c 'apk_bytes=%s' "$qa_apk" > "$qa_out/APK-SIZE.txt"
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$qa_apk" > "$qa_out/APK-BADGING.txt" 2>&1
printf 'exit_code=%s\n' "$?" > "$qa_out/APK-BADGING.exit"
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs "$qa_apk" > "$qa_out/APK-UNSIGNED-VERIFY.txt" 2>&1
printf 'exit_code=%s\n' "$?" > "$qa_out/APK-UNSIGNED-VERIFY.exit"
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -c -p 4 "$qa_apk" > "$qa_out/APK-ZIPALIGN.txt" 2>&1
printf 'exit_code=%s\n' "$?" > "$qa_out/APK-ZIPALIGN.exit"
