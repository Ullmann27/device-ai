#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
out=${1:?fresh scratch output directory}
: "${CONTINUUM_KOTLIN_COMPILER_CP:?Kotlin compiler dependency classpath}"
: "${CONTINUUM_KOTLIN_STDLIB:?Kotlin stdlib jar}"
[[ ! -e "$out" ]] || { echo 'Refuse overwrite of prior result' >&2; exit 1; }
mkdir -p "$out"
printf '%s\n' 'REPLAY_CONSUMED_VALIDATION_NOT_FRESH_TEST' > "$out/STATUS.txt"
for variant in baseline candidate; do
 mkdir -p "$out/$variant-classes"
 java -cp "$CONTINUUM_KOTLIN_COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath "$CONTINUUM_KOTLIN_STDLIB" -d "$out/$variant-classes" "$root/sources/$variant/EvidenceBoundNaturalLocationProbe.kt" "$root/tools/BlindDataset.kt" "$root/tools/BlindProbeHarness.kt" "$root/tools/ForgedVerifierHarness.kt"
 java -Xmx512m -cp "$out/$variant-classes:$CONTINUUM_KOTLIN_STDLIB" at.heinz.deviceai.learning.span.BlindProbeHarnessKt > "$out/$variant-blind-replay.tsv"
 java -Xmx512m -cp "$out/$variant-classes:$CONTINUUM_KOTLIN_STDLIB" at.heinz.deviceai.learning.span.ForgedVerifierHarnessKt > "$out/$variant-forged-replay.tsv"
done
