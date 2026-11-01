#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
checkpoint=${1:?separately retained frozen c28 model checkpoint}
out=${2:?fresh scratch output directory}
: "${CONTINUUM_KOTLIN_COMPILER_CP:?Kotlin compiler dependency classpath}"
: "${CONTINUUM_KOTLIN_STDLIB:?Kotlin stdlib jar}"
[[ ! -e "$out" ]] || { echo 'Refuse to overwrite first-run evidence' >&2; exit 1; }
mkdir -p "$out"
printf '%s\n' 'CONSUMED_VALIDATION_REPLAY_NOT_FRESH_BLIND' > "$out/STATUS.txt"
for variant in baseline candidate; do
 mkdir -p "$out/$variant-classes"
 java -cp "$CONTINUUM_KOTLIN_COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath "$CONTINUUM_KOTLIN_STDLIB" -d "$out/$variant-classes" "$root/sources/$variant/EvidenceBoundNaturalLocationProbe.kt" "$root/tools/BlindDataset.kt" "$root/tools/BlindProbeHarness.kt"
 java -Xmx512m -cp "$out/$variant-classes:$CONTINUUM_KOTLIN_STDLIB" at.heinz.deviceai.learning.span.BlindProbeHarnessKt > "$out/$variant-replay.tsv"
done
mkdir -p "$out/learned-classes"
java -cp "$CONTINUUM_KOTLIN_COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath "$CONTINUUM_KOTLIN_STDLIB" -d "$out/learned-classes" "$root/sources/learned/ContinuumResidualSpanCore.kt" "$root/sources/learned/EvidenceBoundNaturalLocationProbe.kt" "$root/sources/learned/VerifiedLocationSpanResearch.kt" "$root/tools/BlindDataset.kt" "$root/tools/LearnedBlindHarness.kt"
java -Xmx512m -cp "$out/learned-classes:$CONTINUUM_KOTLIN_STDLIB" at.heinz.deviceai.learning.span.LearnedBlindHarnessKt "$checkpoint" > "$out/learned-replay.tsv" 2> "$out/learned-read-only-check.txt"
