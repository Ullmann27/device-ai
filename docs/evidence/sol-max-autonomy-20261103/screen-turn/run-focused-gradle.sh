#!/usr/bin/env bash
set -Eeuo pipefail
cd /workspace/continuum-jarvis
export JAVA_HOME=/workspace/continuum-android-sdk-host/jdk17
export ANDROID_HOME=/workspace/continuum-android-sdk-host/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME=/workspace/continuum-android-sdk-host/gradle-cache
export JAVA_TOOL_OPTIONS="$(cat /workspace/continuum-android-sdk-host/java-network-options)"
export PATH="/workspace/continuum-android-sdk-host/node22/node-v22.16.0-linux-x64/bin:$JAVA_HOME/bin:$ANDROID_HOME/build-tools/35.0.0:$PATH"
exec ./gradlew --offline --no-daemon --max-workers=2 --console=plain \
  -Dorg.gradle.jvmargs='-Xmx1600m -XX:MaxMetaspaceSize=512m' \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true \
  -PcontinuumStandaloneVersionCode=38 :app:testDebugUnitTest \
  -I /workspace/autonomy-20261103/screen-focused.init.gradle
