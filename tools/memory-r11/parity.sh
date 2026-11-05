#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
source_rel=app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt
current="$root/$source_rel"
[[ "$(git -C "$root" hash-object "$current")" == b8c4930ce54b7dd6528beb9bee2a1fc84c122301 ]] || exit 76
[[ "$(git -C "$root" hash-object "$root/tools/memory-r10/ParityR10.kt")" == 982dfc1597a9530e2a3a0323b084b69e09f11a6c ]] || exit 76
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
if (( $# > 0 )); then cp "$1" "$tmp/r10.kt"
else git -C "$root" show 3e1e465bf425d3eafd7155da51aa7cb2fc59b808:"$source_rel" > "$tmp/r10.kt"
fi
[[ "$(git -C "$root" hash-object "$tmp/r10.kt")" == c7c4d8d701e8fd1d2b886aa0a6b627c1b6a1bd99 ]] || exit 76
for tag in r10 r11; do
    if [[ "$tag" == r10 ]]; then src="$tmp/r10.kt"; else src="$current"; fi
    kotlinc "$src" "$root/tools/memory-r10/EventEmittingStub.kt" "$root/tools/memory-r10/ParityR10.kt" -include-runtime -d "$tmp/$tag.jar"
    java -Xmx512m -jar "$tmp/$tag.jar" > "$tmp/$tag.txt"
done
[[ "$(wc -l < "$tmp/r10.txt")" -eq 112 ]] || exit 1
diff -u "$tmp/r10.txt" "$tmp/r11.txt"
[[ "$(sha256sum "$tmp/r11.txt" | cut -d' ' -f1)" == 643a9ac0a14626ce10874947591354e1a702f167c9dca5008664a5514e9f70ff ]] || exit 1
echo R11_PARITY_112_OF_112_IDENTICAL
