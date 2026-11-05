#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
base="6b79a9c001f7607db1511d2398b62b744e362e50"
path="app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
if [[ $# -gt 0 ]]; then cp "$1" "$tmp/r8.kt"; else git -C "$root" show "$base:$path" > "$tmp/r8.kt"; fi
cp "$root/$path" "$tmp/r9.kt"
[[ "$(git hash-object "$tmp/r8.kt")" == "b0200e5246130d056ce02eb570f69dbcfa641ae5" ]] || { echo BASE_SOURCE_MISMATCH >&2; exit 76; }
[[ "$(git hash-object "$tmp/r9.kt")" == "12c1c716c57bc9656897a6428ed0ee1c4f443142" ]] || { echo NEW_SOURCE_MISMATCH >&2; exit 76; }
stub="$root/tools/memory-index-atomic-r7/EventEmittingCompileStub.kt"
parity="$root/tools/memory-r9-exact-negation/ParityR9.kt"
kotlinc "$tmp/r8.kt" "$stub" "$parity" -include-runtime -d "$tmp/r8.jar"
java -jar "$tmp/r8.jar" > "$tmp/r8.out"
kotlinc "$tmp/r9.kt" "$stub" "$parity" -include-runtime -d "$tmp/r9.jar"
java -jar "$tmp/r9.jar" > "$tmp/r9.out"
[[ "$(wc -l < "$tmp/r8.out")" -eq 98 ]] || { echo INCOMPLETE_PARITY >&2; exit 1; }
diff -u "$tmp/r8.out" "$tmp/r9.out"
sha256sum "$tmp/r8.out" "$tmp/r9.out"
echo "96_OF_96_NONBARE_NEGATION_RETRIEVAL_COMPARISONS_EQUAL"
