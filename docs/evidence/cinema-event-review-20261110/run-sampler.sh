#!/usr/bin/env bash
set -euo pipefail
root=/workspace/scratch/5900afa6af29/cinema-event-review
out=/tmp/continuum-toolchain/cinema-event-review
base=/workspace/scratch/5900afa6af29/cognitive-memory-qa/app/build/tmp/kotlin-classes/debug
tools=/tmp/continuum-toolchain
export JAVA_HOME="$tools/jdk"
export JAVA_OPTS='-Xmx256m -XX:MaxMetaspaceSize=192m'
cp="$base:$tools/junit.jar:$tools/hamcrest.jar"
sampler=app/src/main/java/at/heinz/deviceai/ui/WindowProjectedMotionSampler.kt
gaze=app/src/main/java/at/heinz/deviceai/ui/JarvisWindowGaze.kt
test=app/src/test/java/at/heinz/deviceai/ui/WindowProjectedMotionSamplerTest.kt
cd "$root"
git show a7b4d1a090ab7117f29f6fcc3440b60b863395a5:"$sampler" > "$out/BaselineSampler.kt"
{
    git rev-parse HEAD HEAD^{tree}
    git status --porcelain
    sha256sum "$sampler" "$gaze" "$test" "$out/BaselineSampler.kt"
    git -C /workspace/scratch/5900afa6af29/cognitive-memory-qa rev-parse HEAD HEAD:app/src/main
    sha256sum "$base/at/heinz/deviceai/ui/JarvisCinemaEntrance.class" "$base/at/heinz/deviceai/ui/JarvisShellLayout.class" "$base/at/heinz/deviceai/cockpit/WindowProjectedMotion.class" "$base/at/heinz/deviceai/avatar/GazeTarget.class"
} > "$out/source.txt"
for arm in baseline candidate; do
    selected="$root/$sampler"
    if [[ "$arm" == baseline ]]; then selected="$out/BaselineSampler.kt"; fi
    "$tools/kotlinc/bin/kotlinc" "$selected" "$root/$gaze" "$root/$test" -jvm-target 17 -cp "$cp" -include-runtime -d "$out/$arm.jar" > "$out/$arm-compile.txt" 2>&1
    set +e
    "$tools/jdk/bin/java" -Xmx128m -cp "$out/$arm.jar:$cp" org.junit.runner.JUnitCore at.heinz.deviceai.ui.WindowProjectedMotionSamplerTest > "$out/$arm-junit.txt" 2>&1
    result=$?
    set -e
    printf '%s=%s\n' "$arm" "$result"
    if [[ "$arm" == candidate && "$result" != 0 ]]; then cat "$out/$arm-junit.txt"; exit "$result"; fi
 done
cat "$out/baseline-junit.txt"
cat "$out/candidate-junit.txt"
