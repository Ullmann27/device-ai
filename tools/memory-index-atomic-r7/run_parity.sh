#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
source_path="app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt"
expected="a0849ac148164fb94e7f0725182ce831546519d4"
[[ "$(git hash-object "$source_path")" == "$expected" ]] || { echo SOURCE_BLOB_MISMATCH >&2; exit 76; }
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
git show 5073cdc0ee96687d3b17cfefa8710174c8514b96:"$source_path" > "$tmp/baseline.kt"
[[ "$(git hash-object "$tmp/baseline.kt")" == "b2d6ac9b078557f8c74fe0be9dc0032a15e52323" ]] || { echo BASELINE_BLOB_MISMATCH >&2;exit 76; }
python3 - "$tmp/baseline.kt" "$tmp/BaselineRetriever.kt" <<'PY'
from pathlib import Path
import sys
s=Path(sys.argv[1]).read_text()
classes=s[s.index("/** Variant A:"):].replace("class Bm25GermanRetriever(","class BaselineBm25GermanRetriever(").replace("class CharNgramHashRetriever(","class BaselineCharNgramHashRetriever(")
Path(sys.argv[2]).write_text("package at.heinz.deviceai.learning\nimport kotlin.math.ln\nimport kotlin.math.sqrt\n"+classes)
PY
kotlinc "$source_path" "$tmp/BaselineRetriever.kt" \
  tools/memory-index-atomic-r7/EventEmittingCompileStub.kt \
  tools/memory-index-atomic-r7/ParityProbe.kt -include-runtime -d "$tmp/parity.jar"
java -jar "$tmp/parity.jar"
