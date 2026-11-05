#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source_file="${1:-$root/app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt}"
expected_blob="${2:-12c1c716c57bc9656897a6428ed0ee1c4f443142}"
check_blob() {
  actual="$(git hash-object "$1")"
  echo "SOURCE_ID $1 $actual"
  if [[ "$actual" != "$2" ]]; then echo "SOURCE_BLOB_MISMATCH $1" >&2; exit 76; fi
}
check_blob "$source_file" "$expected_blob"
tests="$root/app/src/test/java/at/heinz/deviceai/learning"
check_blob "$tests/MemoryRetrieverIndexSnapshotR6Test.kt" "0240f1273745acd80f0be1851b07ce86655e682f"
check_blob "$tests/MemoryRetrieverAtomicIndexR7Test.kt" "76c793321a6476dbaa9e28eaa10248f65d526fdb"
check_blob "$tests/MemoryRetrieverPolarityAndLimitR8Test.kt" "a2c62141b9b1c2d9a4557e983c1433a1ac28438c"
check_blob "$tests/MemoryNegationExactnessR9Test.kt" "70b70b0ab8ca316c3c835cbc0857562f91a30047"
check_blob "$tests/MemoryNegationNearNeighborR9Test.kt" "bf2fa57a1976dde44e20b1aada5f2e5d2e5e8a71"
kotlinc -version
java -version
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
kotlinc "$source_file" \
    "$tests/MemoryRetrieverIndexSnapshotR6Test.kt" \
    "$tests/MemoryRetrieverAtomicIndexR7Test.kt" \
    "$tests/MemoryRetrieverPolarityAndLimitR8Test.kt" \
    "$tests/MemoryNegationExactnessR9Test.kt" \
    "$tests/MemoryNegationNearNeighborR9Test.kt" \
    "$root/tools/memory-index-atomic-r7/EventEmittingCompileStub.kt" \
    "$root/tools/memory-r9-exact-negation/JUnitHostR9.kt" \
    "$root/tools/memory-r9-exact-negation/RunR9Host.kt" \
    -include-runtime -d "$tmp/r9-host.jar"
java -Xmx512m -jar "$tmp/r9-host.jar"
