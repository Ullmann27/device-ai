#!/usr/bin/env bash
set -euo pipefail
review_root=$(cd "$(dirname "$0")/../.." && pwd)
build_env=${1:?path containing jdk17, kotlinc, android-sdk and jvm-deps}
out=${2:?fresh absolute evidence directory}
test ! -e "$out"
mkdir -p "$out/classes"
cd "$review_root"
git rev-parse HEAD > "$out/revision.txt"
sources=(
  app/src/main/java/at/heinz/deviceai/cognitive/LocalSpeechInputPolicy.kt
  app/src/main/java/at/heinz/deviceai/cognitive/LocalSpeechSession.kt
  app/src/main/java/at/heinz/deviceai/cognitive/OwnerCapabilityReply.kt
  app/src/main/java/at/heinz/deviceai/cognitive/OwnerChatIntentRouter.kt
  app/src/main/java/at/heinz/deviceai/cognitive/LocalClockIntent.kt
  app/src/main/java/at/heinz/deviceai/cognitive/InternetConsentPolicy.kt
  app/src/main/java/at/heinz/deviceai/ui/ContinuumDashboardState.kt
  app/src/main/java/at/heinz/deviceai/AndroidOnDeviceSpeechInput.kt
  app/src/test/java/at/heinz/deviceai/cognitive/LocalSpeechSessionTest.kt
  app/src/test/java/at/heinz/deviceai/cognitive/OwnerCapabilityReplyTest.kt
  app/src/test/java/at/heinz/deviceai/cognitive/LocalSpeechInputPolicyTest.kt
  app/src/test/java/at/heinz/deviceai/cognitive/SpeechDictationDraftTest.kt
  app/src/test/java/at/heinz/deviceai/AndroidSpeechFailureTest.kt
)
sha256sum "${sources[@]}" > "$out/source-hashes.txt"
classpath="$build_env/jvm-deps/junit-4.13.2.jar:$build_env/jvm-deps/hamcrest-core-1.3.jar:$build_env/android-sdk/platforms/android-36/android.jar"
"$build_env/jdk17/bin/java" -version > "$out/java.txt" 2>&1
set +e
JAVA_HOME="$build_env/jdk17" "$build_env/kotlinc/bin/kotlinc" -jvm-target 17 -cp "$classpath" -d "$out/classes" "${sources[@]}" > "$out/compile.log" 2>&1
compiled=$?
printf '%s\n' "$compiled" > "$out/compile.exit"
if [[ "$compiled" != 0 ]]; then cat "$out/compile.log"; exit "$compiled"; fi
"$build_env/jdk17/bin/java" -cp "$out/classes:$classpath:$build_env/kotlinc/lib/kotlin-stdlib.jar" org.junit.runner.JUnitCore \
  at.heinz.deviceai.cognitive.LocalSpeechSessionTest \
  at.heinz.deviceai.cognitive.OwnerCapabilityReplyTest \
  at.heinz.deviceai.cognitive.LocalSpeechInputPolicyTest \
  at.heinz.deviceai.cognitive.SpeechDictationDraftTest \
  at.heinz.deviceai.AndroidSpeechFailureTest > "$out/junit.log" 2>&1
result=$?
printf '%s\n' "$result" > "$out/junit.exit"
cat "$out/junit.log"
exit "$result"
