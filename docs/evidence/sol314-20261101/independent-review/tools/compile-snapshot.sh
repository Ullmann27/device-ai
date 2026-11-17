#!/usr/bin/env bash
set -euo pipefail
repo=${1:?existing repository path}
sha=${2:?exact source commit}
out=${3:?fresh scratch output directory}
: "${CONTINUUM_KOTLIN_COMPILER_CP:?Kotlin compiler/dependency classpath}"
: "${CONTINUUM_KOTLIN_STDLIB:?Kotlin stdlib jar}"
: "${CONTINUUM_JSON_JAR:?org.json jar}"
[[ ! -e "$out" ]] || { echo 'Refuse to overwrite a source/class snapshot' >&2; exit 1; }
mkdir -p "$out/source" "$out/classes"
files=()
for name in LearningSnapshot TrainableSubwordEncoder ContinuumLearningExperience ContinuumOrthographyProbe ContinuumMemoryDialogue OwnerAttributeEvidenceScope; do
 path="app/src/main/java/at/heinz/deviceai/learning/$name.kt"
 if git -C "$repo" cat-file -e "$sha:$path" 2>/dev/null; then
  git -C "$repo" show "$sha:$path" > "$out/source/$name.kt"
  files+=("$out/source/$name.kt")
 fi
done
for name in Ewm1Lexicon Ewm1EvidenceWorkspace Ewm1Workspace; do
 path="app/src/main/java/at/heinz/deviceai/intelligence/ewm/$name.kt"
 git -C "$repo" show "$sha:$path" > "$out/source/$name.kt"
 files+=("$out/source/$name.kt")
done
java -cp "$CONTINUUM_KOTLIN_COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
 -no-stdlib -no-reflect -jvm-target 17 -classpath "$CONTINUUM_KOTLIN_STDLIB:$CONTINUUM_JSON_JAR" -d "$out/classes" "${files[@]}"
git -C "$repo" rev-parse "$sha" > "$out/SOURCE_COMMIT.txt"
