#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
checkpoint=${1:?separately retained frozen natural-model.bin}
out=${2:?fresh scratch output directory}
: "${CONTINUUM_KOTLIN_COMPILER_CP:?Kotlin compiler dependency classpath}"
: "${CONTINUUM_KOTLIN_STDLIB:?Kotlin stdlib jar}"
[[ ! -e "$out" ]] || { echo 'Refuse overwrite of prior result' >&2; exit 1; }
mkdir -p "$out/classes"
printf '%s\n' 'REPLAY_CONSUMED_VALIDATION_NOT_FRESH_TEST' > "$out/STATUS.txt"
java -cp "$CONTINUUM_KOTLIN_COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath "$CONTINUUM_KOTLIN_STDLIB" -d "$out/classes" "$root/sources/learned/ContinuumResidualSpanCore.kt" "$root/sources/learned/EvidenceBoundNaturalLocationProbe.kt" "$root/sources/learned/VerifiedLocationSpanResearch.kt" "$root/tools/BlindDataset.kt" "$root/tools/LearnedBlindHarness.kt"
java -Xmx512m -cp "$out/classes:$CONTINUUM_KOTLIN_STDLIB" at.heinz.deviceai.learning.span.LearnedBlindHarnessKt "$checkpoint" > "$out/learned-replay.tsv" 2> "$out/read-only-integrity.txt"
