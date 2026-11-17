#!/usr/bin/env bash
set -euo pipefail
source /workspace/continuum-build-env/env.sh
repo=$(cd "$(dirname "$0")/../../.." && pwd)
out=${1:?fresh output directory required}
mkdir -p "$out/classes"
cp_path=$(cat /workspace/continuum-build-env/baseline64-ui-classpath.txt)
git -C "$repo" rev-parse HEAD > "$out/source.txt"
kotlinc "$repo/app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeLayout.kt" \
  "$repo/app/src/test/java/at/heinz/deviceai/ui/ContinuumHomeLayoutTest.kt" \
  "$repo/app/src/test/java/at/heinz/deviceai/ui/ContinuumAdaptiveCockpitLayoutTest.kt" \
  -jvm-target 17 -cp "$cp_path" -d "$out/classes" > "$out/compile.txt" 2>&1
/usr/bin/java -cp "$out/classes:$cp_path" org.junit.runner.JUnitCore \
  at.heinz.deviceai.ui.ContinuumHomeLayoutTest at.heinz.deviceai.ui.ContinuumAdaptiveCockpitLayoutTest > "$out/junit.txt" 2>&1
