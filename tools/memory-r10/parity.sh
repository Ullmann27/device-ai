#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
current="$root/app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
if (( $# > 0 )); then cp "$1" "$tmp/r9.kt"
else git -C "$root" show 386389e9aaaa44dadd3bbb919f2658dbaf43fa86:app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt > "$tmp/r9.kt"
fi
[[ "$(git hash-object "$tmp/r9.kt")" == 12c1c716c57bc9656897a6428ed0ee1c4f443142 ]] || exit 76
[[ "$(git hash-object "$current")" == c7c4d8d701e8fd1d2b886aa0a6b627c1b6a1bd99 ]] || exit 76
for tag in r9 r10; do
  if [[ "$tag" == r9 ]]; then src="$tmp/r9.kt"; else src="$current"; fi
  kotlinc "$src" "$root/tools/memory-r10/EventEmittingStub.kt" "$root/tools/memory-r10/ParityR10.kt" -include-runtime -d "$tmp/$tag.jar"
  java -Xmx512m -jar "$tmp/$tag.jar" > "$tmp/$tag.txt"
done
[[ "$(wc -l < "$tmp/r9.txt")" -eq 112 ]] || { echo INCOMPLETE_CASE_COUNT >&2; exit 1; }
diff -u "$tmp/r9.txt" "$tmp/r10.txt"
[[ "$(sha256sum "$tmp/r10.txt" | cut -d' ' -f1)" == 643a9ac0a14626ce10874947591354e1a702f167c9dca5008664a5514e9f70ff ]] || { echo PARITY_HASH_MISMATCH >&2; exit 1; }
echo 'R10_PARITY_112_OF_112_IDENTICAL'
