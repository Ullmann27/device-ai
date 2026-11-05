#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source_file="${1:-$root/app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt}"
expected_blob="${2:-b0200e5246130d056ce02eb570f69dbcfa641ae5}"
actual_blob="$(git hash-object "$source_file")"
printf 'SOURCE_BLOB=%s EXPECTED=%s\n' "$actual_blob" "$expected_blob"
if [[ "$actual_blob" != "$expected_blob" ]]; then
  echo SOURCE_BLOB_MISMATCH >&2
  exit 76
fi
kotlinc -version
java -version
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
kotlinc "$source_file" \
  "$root/app/src/test/java/at/heinz/deviceai/learning/MemoryRetrieverPolarityAndLimitR8Test.kt" \
  "$root/app/src/test/java/at/heinz/deviceai/learning/MemoryRetrieverAtomicIndexR7Test.kt" \
  "$root/app/src/test/java/at/heinz/deviceai/learning/MemoryRetrieverIndexSnapshotR6Test.kt" \
  "$root/tools/memory-index-atomic-r7/EventEmittingCompileStub.kt" \
  "$root/tools/memory-index-atomic-r7/JUnitHostStub.kt" \
  "$root/tools/memory-r8-negation/HostR8RegressionRunner.kt" \
  -include-runtime -d "$tmp/memory-r8-host.jar"
java -jar "$tmp/memory-r8-host.jar"
