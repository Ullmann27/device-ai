#!/usr/bin/env bash
set -euo pipefail
source /workspace/continuum-build-env/env.sh
repo=$(git rev-parse --show-toplevel)
evidence="$repo/docs/evidence/zeta-challenger-20261103"
cache=/workspace/zeta-challenger-run
basejar=/workspace/continuum-quality/app/build/intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar
cp_path=$(cat /workspace/continuum-build-env/baseline64-ui-classpath.txt)
cp_path=${cp_path//$basejar/$cache/prepared-classes.jar}
for name in baseline candidate; do
 out="$evidence/$name/native"; mkdir -p "$cache/native-$name" "$out/images"
 kotlinc "$evidence/$name/source/ContinuumHomeView.kt" "$evidence/ZetaNativeHoldouts.kt" -jvm-target 17 -cp "$cache/$name:$cp_path" -Xfriend-paths="$cache/$name,$cache/prepared-classes.jar" -d "$cache/native-$name" > "$out/compile.txt" 2>&1
 ( cd /workspace/continuum-quality/app
 java -Xmx512m -Drobolectric.offline=true -Drobolectric.dependency.dir=/workspace/continuum-quality/app/build/ui-test-sdk -Dzeta.output="$out/images" -cp "$cache/native-$name:$cache/$name:$cp_path" org.junit.runner.JUnitCore at.heinz.deviceai.ui.ZetaNativeHoldouts > "$out/junit.txt" 2>&1 )
done
