#!/usr/bin/env bash
set -u
root=${1:-$(git rev-parse --show-toplevel)}
out=${2:-/tmp/deviceai-grounding-kotlin}
jdk=/workspace/continuum-toolchain/jdk17
compiler=/workspace/continuum-toolchain/kotlin/kotlinc/bin/kotlinc
base=${3:-/workspace/device-ai/app/build/tmp/kotlin-classes/debug}
stdlib=/workspace/continuum-toolchain/kotlin/kotlinc/lib/kotlin-stdlib.jar
junit=/workspace/continuum-toolchain/gradle-8.11.1/lib/junit-4.13.2.jar
hamcrest=/workspace/continuum-toolchain/gradle-8.11.1/lib/hamcrest-core-1.3.jar
mkdir -p "$out/main" "$out/test"
JAVA_HOME="$jdk" "$compiler" "$root/app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryDialogue.kt" -module-name DeviceAI_app_debug -jvm-target 17 -Xfriend-paths="$base" -cp "$base" -d "$out/main" > "$out/compile-main.txt" 2>&1 || { cat "$out/compile-main.txt"; exit 2; }
JAVA_HOME="$jdk" "$compiler" "$root/app/src/test/java/at/heinz/deviceai/learning/ContinuumLocationGroundingTest.kt" "$root/app/src/test/java/at/heinz/deviceai/learning/ContinuumIdentifierGroundingTest.kt" "$root/app/src/test/java/at/heinz/deviceai/learning/ContinuumMemoryDialogueTest.kt" "$root/app/src/test/java/at/heinz/deviceai/learning/ContinuumDialogueConversationBindingTest.kt" "$root/app/src/test/java/at/heinz/deviceai/learning/ContinuumCognitiveRuntimeTest.kt" -module-name DeviceAI_app_debug -jvm-target 17 -Xfriend-paths="$base,$out/main" -cp "$out/main:$base:$junit:$hamcrest" -d "$out/test" > "$out/compile-test.txt" 2>&1 || { cat "$out/compile-test.txt"; exit 2; }
"$jdk/bin/java" -Xmx1g -cp "$out/test:$out/main:$base:$root/app/src/test/resources:$stdlib:$junit:$hamcrest" org.junit.runner.JUnitCore at.heinz.deviceai.learning.ContinuumLocationGroundingTest at.heinz.deviceai.learning.ContinuumIdentifierGroundingTest at.heinz.deviceai.learning.ContinuumMemoryDialogueTest at.heinz.deviceai.learning.ContinuumDialogueConversationBindingTest at.heinz.deviceai.learning.ContinuumCognitiveRuntimeTest
