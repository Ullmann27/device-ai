#!/usr/bin/env bash
# Authored synthetic HOST research only. Requires an installed Kotlin compiler
# classpath and stdlib; all variant source/classes live in an isolated temp dir.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
: "${CONTINUUM_KOTLIN_COMPILER_CP:?Kotlin compiler and its dependencies}"
: "${CONTINUUM_KOTLIN_STDLIB:?Kotlin stdlib jar}"
out=${1:?output JSONL path}
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT
test ! -e "$out" || { echo 'Refusing to overwrite an experiment report' >&2; exit 2; }
for context in 8 32 64; do
  mkdir -p "$scratch/$context"
  sed "s/const val CONTEXT=8/const val CONTEXT=$context/" \
    "$root/app/src/main/java/at/heinz/deviceai/learning/TinyContextDecoder.kt" > "$scratch/$context/TinyContextDecoder.kt"
  # Larger research variants exceed CDX1's production 64-KiB snapshot contract.
  # Lift only the TEMPORARY experiment serializer's bound; never migrate a store.
  sed -e 's/65504/524256/g' -e 's/65536/524288/g' \
    "$root/app/src/main/java/at/heinz/deviceai/learning/LearningSnapshot.kt" > "$scratch/$context/LearningSnapshot.kt"
  sources=(TrainableSubwordEncoder ContinuumCdxLargeCurriculum ContinuumIntelligenceAudit DecoderGenerativeGate)
  files=()
  for name in "${sources[@]}"; do files+=("$root/app/src/main/java/at/heinz/deviceai/learning/$name.kt"); done
  java -cp "$CONTINUUM_KOTLIN_COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib -no-reflect -jvm-target 17 -classpath "$CONTINUUM_KOTLIN_STDLIB" \
    -d "$scratch/$context/classes" "${files[@]}" "$scratch/$context/TinyContextDecoder.kt" "$scratch/$context/LearningSnapshot.kt" \
    "$root/app/src/test/java/at/heinz/deviceai/learning/Cdx1CausalAblation.kt"
  for seed in 91 17 203; do
    for mode in raw unit zero; do
      java -Xmx256m -cp "$scratch/$context/classes:$CONTINUUM_KOTLIN_STDLIB" \
        at.heinz.deviceai.learning.Cdx1CausalAblation "$seed" "$mode" >> "$out"
    done
  done
done
echo "HOST_REGRESSION_ABLATION_WRITTEN=$out"
