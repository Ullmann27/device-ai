#!/usr/bin/env bash
# Offline self-test of the CRE1 host-runner's fail-closed boundaries. Never invokes the real model.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
RUNNER="$ROOT/scripts/exp20-m5-cre1-real-gate.sh"
[[ -f "$RUNNER" ]] || { echo "M5 runner missing: $RUNNER" >&2; exit 2; }
TMP="$(mktemp -d "${TMPDIR:-/tmp}/m5-runner-selftest.XXXXXXXX")"
trap 'rm -rf -- "$TMP"' EXIT
mkdir -p "$TMP/fake-repo/scripts" "$TMP/mock-bin" "$TMP/output"
cp -- "$RUNNER" "$TMP/fake-repo/scripts/exp20-m5-cre1-real-gate.sh"
touch "$TMP/junit.jar" "$TMP/hamcrest.jar"
cat > "$TMP/mock-bin/node" <<'NODE'
#!/usr/bin/env bash
set -eu
[[ "$#" -eq 3 && "$2" == prepare ]]
mkdir -p "$3"
printf 'invalid frozen checkpoint for negative test\n' > "$3/node-cre1.cre1"
NODE
chmod +x "$TMP/mock-bin/node"
run() {
  KOTLINC=/bin/true JUNIT_JAR="$TMP/junit.jar" HAMCREST_JAR="$TMP/hamcrest.jar" \
    PATH="$TMP/mock-bin:$PATH" M5_OUT="$1" bash "$TMP/fake-repo/scripts/exp20-m5-cre1-real-gate.sh" \
    >"$2" 2>&1
}

# An invalid checkpoint must stop before any Kotlin build or audit report.
set +e
run "$TMP/output" "$TMP/hash-mismatch.log"
code=$?
set -e
[[ "$code" -ne 0 && ! -e "$TMP/output/results/cre1-blind-gate.txt" ]]
grep -q 'SHA_MISMATCH' "$TMP/hash-mismatch.log"
echo 'PASS 1/3 mismatched checkpoint is rejected before audit'

# A caller must not place scratch files in the repository itself.
set +e
run "$TMP/fake-repo/inside" "$TMP/inside-repo.log"
code=$?
set -e
[[ "$code" -ne 0 ]]
grep -q 'M5_OUT must be outside the source repository' "$TMP/inside-repo.log"
echo 'PASS 2/3 scratch directory inside source tree is rejected'

# Missing required local compiler path must fail before Node/checkpoint generation.
set +e
KOTLINC=/this/path/does/not/exist JUNIT_JAR="$TMP/junit.jar" HAMCREST_JAR="$TMP/hamcrest.jar" \
  PATH="$TMP/mock-bin:$PATH" M5_OUT="$TMP/missing-compiler" \
  bash "$TMP/fake-repo/scripts/exp20-m5-cre1-real-gate.sh" >"$TMP/missing-compiler.log" 2>&1
code=$?
set -e
[[ "$code" -ne 0 && ! -e "$TMP/missing-compiler/cre1/node-cre1.cre1" ]]
grep -q 'Invalid local compiler/JUnit paths' "$TMP/missing-compiler.log"
echo 'PASS 3/3 nonexistent compiler path fails before creating a checkpoint'
echo 'M5_RUNNER_FAIL_CLOSED_TESTS=3/3 REAL_CRE1_NOT_RUN SEALED_UNOPENED'
