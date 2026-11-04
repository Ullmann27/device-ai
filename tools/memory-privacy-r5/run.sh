#!/usr/bin/env bash
# Method-scoped Kotlin regression: compiles the exact method extracted from the repository source.
# No Gradle, model, SDK, signing, network or installed Android state is touched.
set -euo pipefail
repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source="${1:-$repo/app/src/main/java/at/heinz/deviceai/cognitive/AiContextCompiler.kt}"
probe="$repo/tools/memory-privacy-r5/RedactorProbe.kt"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
python3 - "$source" "$tmp/RedactorSource.kt" <<'PY'
import hashlib
import pathlib
import sys
source = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
start = source.index("    private fun redactSecrets(text: String): String {")
end = source.find("\n    private fun utf8Size", start)
if end < 0:
    # Standalone exact-method mirrors used only to replay the frozen RED/GREEN.
    end = source.index("\n}", start)
method = source[start:end].rstrip() + "\n"
out = ("class RedactorSource {\n"
       "    fun inspect(text: String): String = redactSecrets(text)\n"
       + method + "}\n")
pathlib.Path(sys.argv[2]).write_text(out, encoding="utf-8")
print("SOURCE_SHA256=" + hashlib.sha256(source.encode("utf-8")).hexdigest())
print("METHOD_SHA256=" + hashlib.sha256(method.encode("utf-8")).hexdigest())
print("EXTRACTED_KOTLIN_CODE_UNCHANGED=true")
PY
"${KOTLINC:-kotlinc}" "$tmp/RedactorSource.kt" "$probe" -include-runtime -d "$tmp/r5.jar"
"${JAVA:-java}" -jar "$tmp/r5.jar"
