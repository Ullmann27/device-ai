#!/usr/bin/env bash
set -euo pipefail
source /workspace/continuum-build-env/env.sh
repo=$(git rev-parse --show-toplevel)
evidence="$repo/docs/evidence/zeta-challenger-20261103"
cache=/workspace/zeta-challenger-run
mkdir -p "$cache"
basejar=/workspace/continuum-quality/app/build/intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar
if [[ ! -f "$cache/prepared-classes.jar" ]]; then cp "$basejar" "$cache/prepared-classes.jar"; fi
cp_path=$(cat /workspace/continuum-build-env/baseline64-ui-classpath.txt)
cp_path=${cp_path//$basejar/$cache/prepared-classes.jar}
sha256sum "$cache/prepared-classes.jar" > "$evidence/prepared-classes.sha256"
for spec in ${ZETA_SPECS:-baseline:04b4292d796bc151c40ab18405226cd9faef01cf candidate:d8340ff10c9efec5cfdfe8baa63bcf58bdfbe3d4}; do
  name=${spec%%:*}; rev=${spec#*:}; out="$evidence/$name"; src="$out/source"; build="$cache/$name"
  mkdir -p "$src" "$build" "$out/images"
  sources=()
  for file in ContinuumHomeLayout ContinuumHomeElements ContinuumHomeScreen ContinuumCockpitElements; do
    path="app/src/main/java/at/heinz/deviceai/ui/$file.kt"
    if git cat-file -e "$rev:$path" 2>/dev/null; then git show "$rev:$path" > "$src/$file.kt"; sources+=("$src/$file.kt"); fi
  done
  git show "$rev:app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeView.kt" > "$src/ContinuumHomeView.kt"
  printf '%s\n' "$rev" > "$out/source-commit.txt"
  sha256sum "$src"/*.kt > "$out/source.sha256"
  kotlinc "${sources[@]}" tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/Java2DContinuumPainter.kt "$evidence/ZetaHoldouts.kt" -jvm-target 17 -cp "$cp_path" -Xfriend-paths="$cache/prepared-classes.jar" -d "$build" > "$out/compile.txt" 2>&1
  java -Xmx512m -Djava.awt.headless=true -cp "$build:$cp_path" at.heinz.deviceai.ui.ZetaHoldouts "$out/images" > "$out/results.tsv" 2> "$out/stderr.txt"
done
