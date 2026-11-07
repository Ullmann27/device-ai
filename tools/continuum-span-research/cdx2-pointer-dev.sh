#!/usr/bin/env bash
# Standalone JVM research only. Fresh destination required; never reads Blind-C or app weights.
set -euo pipefail
repo=$(cd "$(dirname "$0")/../.." && pwd)
out=${1:?usage: cdx2-pointer-dev.sh FRESH_OUTPUT_DIRECTORY}
test ! -e "$out"
mkdir -p "$out/classes"
cached=${CDX2_CACHED_MAIN:-/workspace/continuum-checks/post-fable-final-harness/main}
deps=${CDX2_JARS:-/workspace/continuum-toolchain}
compiler=${CDX2_KOTLINC:-/workspace/post-fable-toolchain/bin/kotlinc}
cp="$out/classes:$cached:$deps/json.jar:$deps/stdlib.jar"
"$compiler" -module-name app -Xfriend-paths="$cached" -cp "$cached:$deps/json.jar:$deps/stdlib.jar" \
  "$repo/tools/continuum-span-research/Cdx2PointerCandidate.kt" \
  "$repo/tools/continuum-span-research/Cdx2PointerSynthetic.kt" \
  "$repo/tools/continuum-span-research/Cdx2PointerDevHarness.kt" -d "$out/classes"
if java -cp "$cp" at.heinz.deviceai.learning.span.Cdx2PointerDevHarness red > "$out/red-copy.log" 2>&1; then
  echo "ERROR: untrained selector unexpectedly passed copy control" >&2; exit 1
fi
java -cp "$cp" at.heinz.deviceai.learning.span.Cdx2PointerSynthetic "$out"
java -cp "$cp" at.heinz.deviceai.learning.span.Cdx2PointerCandidate train \
  "$out/TRAIN.json" "$out/DEV.json" "$out/checkpoint.json" "$out/train-report.json"
java -cp "$cp" at.heinz.deviceai.learning.span.Cdx2PointerDevHarness "$out/checkpoint.json" | tee "$out/controls.log"
java -cp "$cp" at.heinz.deviceai.learning.span.Cdx2PointerDevHarness evaluate \
  "$out/checkpoint.json" "$out/DEV.json" "$out/dev-report.json" | tee "$out/dev-summary.log"
sha256sum "$repo/tools/continuum-span-research/Cdx2PointerCandidate.kt" \
  "$repo/tools/continuum-span-research/Cdx2PointerSynthetic.kt" \
  "$repo/tools/continuum-span-research/Cdx2PointerDevHarness.kt" \
  "$repo/tools/continuum-span-research/cdx2-pointer-dev.sh" \
  "$out/TRAIN.json" "$out/DEV.json" "$out/checkpoint.json" "$out/dev-report.json" "$out/train-report.json" > "$out/FREEZE.sha256"
cat "$out/train-report.json"
