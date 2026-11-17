#!/usr/bin/env bash
set -euo pipefail
source /workspace/continuum-build-env/env.sh
repo=$(cd "$(dirname "$0")/../../.." && pwd)
out=${1:?fresh output directory required}
mkdir -p "$out/classes"
cp_path=$(cat /workspace/continuum-build-env/baseline64-ui-classpath.txt)
git -C "$repo" rev-parse HEAD > "$out/source.txt"
kotlinc "$repo/app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeLayout.kt" \
  "$repo/app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeScreen.kt" \
  "$repo/app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeElements.kt" \
  "$repo/app/src/main/java/at/heinz/deviceai/ui/ContinuumCockpitElements.kt" \
  "$repo/tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/Java2DContinuumPainter.kt" \
  "$repo/tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/ReferencePreviewData.kt" \
  "$repo/tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/ContinuumHomeScreenTest.kt" \
  -jvm-target 17 -cp "$cp_path" -d "$out/classes" > "$out/compile.txt" 2>&1
cd "$repo"
/usr/bin/java -Djava.awt.headless=true -cp "$out/classes:$cp_path" org.junit.runner.JUnitCore \
  at.heinz.deviceai.ui.ContinuumHomeScreenTest > "$out/junit.txt" 2>&1
