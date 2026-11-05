#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
source_rel=app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt
src="$root/$source_rel"
tests="$root/app/src/test/java/at/heinz/deviceai/learning"
tool="$root/tools/memory-r11"
check_blob() {
    local hash
    hash="$(git -C "$root" hash-object "$1")"
    printf 'SOURCE_ID %s %s\n' "$1" "$hash"
    [[ "$hash" == "$2" ]] || { echo SOURCE_BLOB_MISMATCH >&2; exit 76; }
}
check_blob "$src" b8c4930ce54b7dd6528beb9bee2a1fc84c122301
check_blob "$tests/MemoryOneDigitEvidenceR11Test.kt" efeee016465f7eb4f1fca00801ba121b9cada180
check_blob "$tests/MemoryRepeatedNegationR11Test.kt" 6764f1af7a6c744bda7a009784f33174f88408c0
check_blob "$tool/RunR11.kt" a241937ca011348aa070eb78461db2b42ff12786
check_blob "$root/tools/memory-r10/JUnitHostR10.kt" b4bc09e171362638e65874ddffd5a00d6d17d97a
check_blob "$root/tools/memory-r10/EventEmittingStub.kt" b4f5990e37bb4826babe9e9234712f12189cbbe4
check_blob "$root/tools/memory-r10/ParityR10.kt" 982dfc1597a9530e2a3a0323b084b69e09f11a6c

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
if (( $# > 0 )); then cp "$1" "$tmp/baseline.kt"
else git -C "$root" show 3e1e465bf425d3eafd7155da51aa7cb2fc59b808:"$source_rel" > "$tmp/baseline.kt"
fi
check_blob "$tmp/baseline.kt" c7c4d8d701e8fd1d2b886aa0a6b627c1b6a1bd99
kotlinc -version
java -version
for kind in baseline patched; do
    if [[ "$kind" == baseline ]]; then source="$tmp/baseline.kt"; else source="$src"; fi
    kotlinc "$source" "$tests/MemoryOneDigitEvidenceR11Test.kt" "$tests/MemoryRepeatedNegationR11Test.kt" \
        "$root/tools/memory-r10/EventEmittingStub.kt" "$root/tools/memory-r10/JUnitHostR10.kt" \
        "$tool/RunR11.kt" -include-runtime -d "$tmp/${kind}.jar"
    set +e
    java -Xmx512m -jar "$tmp/${kind}.jar" > "$tmp/${kind}.txt" 2>&1
    test_exit=$?
    set -e
    cat "$tmp/${kind}.txt"
    if [[ "$kind" == baseline ]]; then
        [[ $test_exit -eq 1 ]] && grep -qx 'TOTAL=9 PASS=2 FAIL=7' "$tmp/baseline.txt" || { echo RED_UNEXPECTED >&2; exit 1; }
    else
        [[ $test_exit -eq 0 ]] && grep -qx 'TOTAL=9 PASS=9 FAIL=0' "$tmp/patched.txt" || { echo GREEN_UNEXPECTED >&2; exit 1; }
    fi
done
echo R11_SOURCE_BOUND_RED_2_7_GREEN_9_0
