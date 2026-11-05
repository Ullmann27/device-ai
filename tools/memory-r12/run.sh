#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
source_rel=app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt
src="$root/$source_rel"
test_dir="$root/app/src/test/java/at/heinz/deviceai/learning"
r10="$root/tools/memory-r10"
runner="$root/tools/memory-r12/RunR12.kt"
test_r12="$test_dir/MemoryNumericEvidenceR12Test.kt"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

check_blob() {
  local actual
  actual="$(git hash-object "$1")"
  printf 'SOURCE_ID %s %s\n' "$1" "$actual"
  [[ "$actual" == "$2" ]] || { echo SOURCE_BLOB_MISMATCH >&2; exit 76; }
}
check_blob "$src" dcaeed0b13eb3f4f88128e45c250d6d84a319c72
check_blob "$test_r12" c6fc1b7ed6a8cbbbaf87c46a2e3e685a5878ed8e
check_blob "$test_dir/MemoryOneDigitEvidenceR11Test.kt" efeee016465f7eb4f1fca00801ba121b9cada180
check_blob "$test_dir/MemoryRepeatedNegationR11Test.kt" 6764f1af7a6c744bda7a009784f33174f88408c0
check_blob "$r10/JUnitHostR10.kt" b4bc09e171362638e65874ddffd5a00d6d17d97a
check_blob "$r10/EventEmittingStub.kt" b4f5990e37bb4826babe9e9234712f12189cbbe4
check_blob "$r10/ParityR10.kt" 982dfc1597a9530e2a3a0323b084b69e09f11a6c

if (( $# > 0 )); then
  cp "$1" "$tmp/original.kt"
else
  git -C "$root" show 915dba534eb7c79b6343636cc6646226a044998f:"$source_rel" > "$tmp/original.kt"
fi
check_blob "$tmp/original.kt" b8c4930ce54b7dd6528beb9bee2a1fc84c122301

# Baseline has no numericEvidenceTerms API: omit only the new helper-only test method.
python3 - "$test_r12" "$tmp/baseline-tests.kt" <<'PY'
from pathlib import Path
import sys
source=Path(sys.argv[1]).read_text()
start=source.index("    @Test fun temporalAndFactualNumbersRemainSeparate()")
stop=source.index("\n    }",start)+len("\n    }")
Path(sys.argv[2]).write_text(source[:start]+source[stop:])
PY

common=("$test_dir/MemoryOneDigitEvidenceR11Test.kt" "$test_dir/MemoryRepeatedNegationR11Test.kt"
        "$r10/JUnitHostR10.kt" "$r10/EventEmittingStub.kt" "$runner")
kotlinc -version
java -version

kotlinc "$tmp/original.kt" "$tmp/baseline-tests.kt" "${common[@]}" -include-runtime -d "$tmp/baseline.jar"
set +e
java -Xmx512m -jar "$tmp/baseline.jar" > "$tmp/baseline.txt" 2>&1
base_exit=$?
set -e
cat "$tmp/baseline.txt"
[[ $base_exit -eq 1 ]] && grep -qx 'TOTAL=17 PASS=11 FAIL=6' "$tmp/baseline.txt" || { echo R12_RED_UNEXPECTED >&2; exit 1; }

kotlinc "$src" "$test_r12" "${common[@]}" -include-runtime -d "$tmp/patched.jar"
java -Xmx512m -jar "$tmp/patched.jar" > "$tmp/patched.txt"
cat "$tmp/patched.txt"
grep -qx 'TOTAL=18 PASS=18 FAIL=0' "$tmp/patched.txt" || { echo R12_GREEN_UNEXPECTED >&2; exit 1; }

for kind in original patched; do
  if [[ "$kind" == original ]]; then model="$tmp/original.kt"; else model="$src"; fi
  kotlinc "$model" "$r10/ParityR10.kt" "$r10/EventEmittingStub.kt" -include-runtime -d "$tmp/parity-$kind.jar"
  java -Xmx512m -jar "$tmp/parity-$kind.jar" > "$tmp/parity-$kind.txt"
done
cmp "$tmp/parity-original.txt" "$tmp/parity-patched.txt"
[[ "$(wc -l < "$tmp/parity-patched.txt")" == 112 ]] || exit 1
[[ "$(sha256sum "$tmp/parity-patched.txt" | cut -d ' ' -f1)" == 643a9ac0a14626ce10874947591354e1a702f167c9dca5008664a5514e9f70ff ]] || exit 1
echo "R12_SOURCE_BOUND_RED_11_6_GREEN_18_0_PARITY_112_IDENTICAL"
