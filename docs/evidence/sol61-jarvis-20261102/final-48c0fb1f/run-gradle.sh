#!/usr/bin/env bash
set -Eeuo pipefail
cd /workspace/continuum-jarvis
export JAVA_HOME=/workspace/continuum-android-sdk-host/jdk17
export ANDROID_HOME=/workspace/continuum-android-sdk-host/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME=/workspace/continuum-android-sdk-host/gradle-cache
export JAVA_TOOL_OPTIONS="$(cat /workspace/continuum-android-sdk-host/java-network-options)"
export PATH="/workspace/continuum-android-sdk-host/node22/node-v22.16.0-linux-x64/bin:$JAVA_HOME/bin:$ANDROID_HOME/build-tools/35.0.0:$PATH"
git rev-parse HEAD > /workspace/jarvis-qa/48c0fb1f/source.sha
node -p 'Date.now()' > /workspace/jarvis-qa/48c0fb1f/gradle-start.txt
code=0
./gradlew --offline --no-daemon --continue --max-workers=2 --console=plain \
  -I /workspace/jarvis-qa/48c0fb1f/gradle-evidence.init.gradle \
  -Dorg.gradle.jvmargs='-Xmx1600m -XX:MaxMetaspaceSize=512m' \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true \
  -PcontinuumStandaloneVersionCode=38 \
  :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug \
  > /workspace/jarvis-qa/48c0fb1f/android-gradle.log 2>&1 || code=$?
printf '%s\n' "$code" > /workspace/jarvis-qa/48c0fb1f/android-gradle.exit
node scripts/continuum-qa-evidence.mjs android "$PWD" "$(cat /workspace/jarvis-qa/48c0fb1f/gradle-start.txt)" > /workspace/jarvis-qa/48c0fb1f/android-junit-proof.json
exit "$code"
