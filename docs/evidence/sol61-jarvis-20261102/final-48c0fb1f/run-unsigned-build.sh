#!/usr/bin/env bash
set -Eeuo pipefail
cd /workspace/continuum-jarvis
export JAVA_HOME=/workspace/continuum-android-sdk-host/jdk17
export ANDROID_HOME=/workspace/continuum-android-sdk-host/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME=/workspace/continuum-android-sdk-host/gradle-cache
export JAVA_TOOL_OPTIONS="$(cat /workspace/continuum-android-sdk-host/java-network-options)"
export PATH="/workspace/continuum-android-sdk-host/node22/node-v22.16.0-linux-x64/bin:$JAVA_HOME/bin:$ANDROID_HOME/build-tools/35.0.0:$PATH"
./gradlew --offline --no-daemon --max-workers=2 --console=plain \
  -Dorg.gradle.jvmargs='-Xmx1600m -XX:MaxMetaspaceSize=512m' \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PdeviceAiMentorPreview=true -PcontinuumLocalRenderUnsigned=true \
  -PcontinuumStandaloneVersionCode=38 :app:assembleRelease :app:bundleRelease \
  > /workspace/jarvis-qa/48c0fb1f/original-package-assembly.log 2>&1
apk=app/build/outputs/apk/release/app-release-unsigned.apk
aapt2 dump badging "$apk" > /workspace/jarvis-qa/48c0fb1f/apk-badging.txt
grep -Fq "package: name='at.heinz.deviceai.continuumlocal' versionCode='38' versionName='0.3.37'" /workspace/jarvis-qa/48c0fb1f/apk-badging.txt
if apksigner verify "$apk" > /workspace/jarvis-qa/48c0fb1f/apk-unsigned-proof.txt 2>&1; then
 echo 'Unexpected signer' >&2;exit 1
fi
sha256sum "$apk" > /workspace/jarvis-qa/48c0fb1f/apk.sha256
stat -c '%s' "$apk" > /workspace/jarvis-qa/48c0fb1f/apk.bytes
git rev-parse HEAD > /workspace/jarvis-qa/48c0fb1f/apk-source.sha
git diff --exit-code HEAD -- > /workspace/jarvis-qa/48c0fb1f/build-source-diff.txt
printf 'UNSIGNED_ORIGINAL_APK_BUILT\n' > /workspace/jarvis-qa/48c0fb1f/assembly-status.txt
