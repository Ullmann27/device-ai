#!/usr/bin/env bash
# Exact invocation used for RESULT.json (same pattern as .github/workflows/continuum-code53-frozen-qa.yml, lines 165-176):
# the validator file stays unchanged (its SHA-256 is recorded as validatorSha256) and the expected source/tree are
# injected at runtime from the frozen checkout, so EXPECTED_SOURCE/EXPECTED_TREE in the file are only defaults.
# Usage (repo root, clean checkout of the APK source): run-validator54.sh <validator.py> <apk> <out-dir> <build-tools> <java-home>
set -euo pipefail
# Same guard as the Code53 workflow: refuse a validator whose bytes differ from the recorded one.
printf "%s  %s\n" e70a2470f95e0cca4fe25751cc67907fb4e08e4dbfd1bc1fc060b99606252be7 "$1" | sha256sum -c -
SRC=$(git rev-parse HEAD); TREE=$(git rev-parse 'HEAD^{tree}')
python3 -I - "$1" "$SRC" "$TREE" "$2" "$3" --repo "$PWD" --build-tools "$4" --java-home "$5" <<'PY'
import importlib.util, sys
path, source_sha, source_tree, *arguments = sys.argv[1:]
spec = importlib.util.spec_from_file_location('code54_validator', path)
v = importlib.util.module_from_spec(spec); spec.loader.exec_module(v)
v.EXPECTED_SOURCE = source_sha; v.EXPECTED_TREE = source_tree
sys.argv = [path, *arguments]
raise SystemExit(v.main())
PY
