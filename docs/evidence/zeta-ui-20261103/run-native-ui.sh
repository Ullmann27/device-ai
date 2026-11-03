#!/usr/bin/env bash
set -euo pipefail
source /workspace/continuum-build-env/env.sh
repo=$(cd "$(dirname "$0")/../../.." && pwd)
out=${1:?fresh output directory required}
mkdir -p "$out/classes"
cp_path=$(cat /workspace/continuum-build-env/baseline64-ui-classpath.txt)
sources=("$repo/app/src/uiTest/java/at/heinz/deviceai/ContinuumAdaptiveCockpitUiTest.kt")
if [[ ${2:-baseline} == candidate ]]; then
  for name in HomeLayout HomeScreen HomeElements HomeView CockpitElements; do
    source_file="$repo/app/src/main/java/at/heinz/deviceai/ui/Continuum${name}.kt"
    [[ ! -f "$source_file" ]] || sources+=("$source_file")
  done
fi
git -C "$repo" rev-parse HEAD > "$out/source.txt"
kotlinc "${sources[@]}" -jvm-target 17 -cp "$cp_path" \
  -Xfriend-paths=/workspace/continuum-quality/app/build/tmp/kotlin-classes/debug -d "$out/classes" > "$out/compile.txt" 2>&1
cd /workspace/continuum-quality/app
/usr/bin/java -Xmx512m -Drobolectric.offline=true \
  -Drobolectric.dependency.dir=/workspace/continuum-quality/app/build/ui-test-sdk \
  -Dcontinuum.ui.evidenceDir="$out/images" -cp "$out/classes:$cp_path" \
  org.junit.runner.JUnitCore at.heinz.deviceai.ContinuumAdaptiveCockpitUiTest > "$out/junit.txt" 2>&1
