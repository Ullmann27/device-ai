#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
src="$root/app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt"
tests="$root/app/src/test/java/at/heinz/deviceai/learning"
check_blob() {
  local observed
  observed="$(git hash-object "$1")"
  echo "SOURCE_ID $1 $observed"
  [[ "$observed" == "$2" ]] || { echo SOURCE_BLOB_MISMATCH >&2; exit 76; }
}
check_blob "$src" c7c4d8d701e8fd1d2b886aa0a6b627c1b6a1bd99
check_blob "$tests/MemoryRetrieverFuzzyAmbiguityR10Test.kt" afa93dd899a04ac57e735510be66655fbba8e181
check_blob "$tests/MemoryNegationIndexR10Test.kt" 69b13ff6a3098cca4b98180fed91d366b77acac2
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
if (( $# > 0 )); then cp "$1" "$tmp/baseline.kt"
else git -C "$root" show 386389e9aaaa44dadd3bbb919f2658dbaf43fa86:app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt > "$tmp/baseline.kt"
fi
check_blob "$tmp/baseline.kt" 12c1c716c57bc9656897a6428ed0ee1c4f443142
kotlinc -version
java -version
for kind in baseline patched; do
  if [[ "$kind" == baseline ]]; then file="$tmp/baseline.kt"; else file="$src"; fi
  kotlinc "$file" "$tests/MemoryRetrieverFuzzyAmbiguityR10Test.kt" "$tests/MemoryNegationIndexR10Test.kt" \
    "$root/tools/memory-r10/EventEmittingStub.kt" "$root/tools/memory-r10/JUnitHostR10.kt" \
    "$root/tools/memory-r10/RunR10.kt" -include-runtime -d "$tmp/$kind.jar"
  set +e
  java -Xmx512m -jar "$tmp/$kind.jar" > "$tmp/$kind.txt" 2>&1
  status=$?
  set -e
  cat "$tmp/$kind.txt"
  if [[ "$kind" == baseline ]]; then
    [[ $status -eq 1 ]] && grep -qx 'TOTAL=7 PASS=6 FAIL=1' "$tmp/baseline.txt" || { echo BASELINE_UNEXPECTED >&2; exit 1; }
  else
    [[ $status -eq 0 ]] && grep -qx 'TOTAL=7 PASS=7 FAIL=0' "$tmp/patched.txt" || { echo PATCH_NOT_GREEN >&2; exit 1; }
  fi
done
echo 'R10_REPRODUCED_RED_TO_GREEN'
