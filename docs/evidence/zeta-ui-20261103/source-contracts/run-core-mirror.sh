#!/usr/bin/env bash
set -euo pipefail
source /workspace/continuum-build-env/env.sh
out=${1:?}
testfile=${2:?}
mkdir -p "$out/classes"
cp_path=$(cat /workspace/continuum-build-env/baseline64-ui-classpath.txt)
kotlinc app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeLayout.kt \
 app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeScreen.kt \
 app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeElements.kt \
 app/src/main/java/at/heinz/deviceai/ui/ContinuumCockpitElements.kt "$testfile" \
 -jvm-target 17 -cp "$cp_path" -d "$out/classes" > "$out/compile.txt" 2>&1
/usr/bin/java -cp "$out/classes:$cp_path" org.junit.runner.JUnitCore at.heinz.deviceai.ui.ContinuumCoreMirrorTest > "$out/junit.txt" 2>&1
