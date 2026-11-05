#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source_file="${1:-$root/app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt}"
expected_blob="${2:-a0849ac148164fb94e7f0725182ce831546519d4}"
actual_blob="$(git hash-object "$source_file")"
echo "Source blob: $actual_blob (expected $expected_blob)"
if [[ "$actual_blob" != "$expected_blob" ]]; then echo "SOURCE_BLOB_MISMATCH" >&2; exit 76; fi
kotlinc -version
java -version
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
kotlinc "$source_file" \
  "$root/app/src/test/java/at/heinz/deviceai/learning/MemoryRetrieverAtomicIndexR7Test.kt" \
  "$root/app/src/test/java/at/heinz/deviceai/learning/MemoryRetrieverIndexSnapshotR6Test.kt" \
  "$root/tools/memory-index-atomic-r7/EventEmittingCompileStub.kt" \
  "$root/tools/memory-index-atomic-r7/JUnitHostStub.kt" \
  "$root/tools/memory-index-atomic-r7/HostRegressionRunner.kt" \
  -include-runtime -d "$tmp/host.jar"
java -jar "$tmp/host.jar"
