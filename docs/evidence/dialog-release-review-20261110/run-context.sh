#!/usr/bin/env bash
set -euo pipefail
root=/workspace/scratch/5900afa6af29/dialog-release-review
out=/tmp/continuum-toolchain/dialog-release-review
tools=/tmp/continuum-toolchain
deps=/workspace/scratch/5900afa6af29/cognitive-memory-qa/app/build/tmp/kotlin-classes/debug
export JAVA_HOME="$tools/jdk"
export JAVA_OPTS='-Xmx256m -XX:MaxMetaspaceSize=192m'
cp="$deps:$tools/junit.jar:$tools/hamcrest.jar"
cd "$root"
source=app/src/main/java/at/heinz/deviceai/cognitive/LocalConversationContext.kt
test=app/src/test/java/at/heinz/deviceai/cognitive/ConversationControlTest.kt
git show a07b183b67b9c00192bb96b61a0002f74e23372b:"$source" > "$out/LocalConversationContext.kt"
sha256sum "$source" "$test" "$out/LocalConversationContext.kt" > "$out/source-sha256.txt"
for arm in baseline candidate; do
 selected="$root/$source"
 if [[ "$arm" == baseline ]]; then selected="$out/LocalConversationContext.kt"; fi
 "$tools/kotlinc/bin/kotlinc" "$selected" "$root/$test" -jvm-target 17 -Xfriend-paths="$deps" -cp "$cp" -include-runtime -d "$out/$arm.jar" > "$out/$arm-compile.txt" 2>&1
 set +e
 "$tools/jdk/bin/java" -Xmx128m -cp "$out/$arm.jar:$cp" org.junit.runner.JUnitCore at.heinz.deviceai.cognitive.ConversationControlTest > "$out/$arm-junit.txt" 2>&1
 result=$?
 set -e
 printf '%s=%s\n' "$arm" "$result"
 if [[ "$arm" == candidate && "$result" != 0 ]]; then cat "$out/$arm-junit.txt"; exit "$result"; fi
 done
cat "$out/baseline-junit.txt"
cat "$out/candidate-junit.txt"
