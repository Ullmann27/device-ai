#!/usr/bin/env bash
# EXP-20 M5: offline/host-only replication of the registered CRE1 query-blind gate.
# Does not generate or open a SEALED holdout. Does not modify production files.
set -euo pipefail
umask 077

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
: "${KOTLINC:?Set KOTLINC to a verified kotlinc executable}"
: "${JUNIT_JAR:?Set JUNIT_JAR to a local JUnit 4 jar}"
: "${HAMCREST_JAR:?Set HAMCREST_JAR to a local Hamcrest jar}"
for cmd in java node sha256sum; do command -v "$cmd" >/dev/null || { echo "Missing host prerequisite: $cmd" >&2; exit 2; }; done
[[ -x "$KOTLINC" && -f "$JUNIT_JAR" && -f "$HAMCREST_JAR" ]] || { echo 'Invalid local compiler/JUnit paths' >&2; exit 2; }

if [[ -z "${M5_OUT:-}" ]]; then
  M5_OUT="$(mktemp -d "${TMPDIR:-/tmp}/continuum-m5.XXXXXXXX")"
fi
mkdir -p "$M5_OUT"
M5_OUT="$(cd "$M5_OUT" && pwd -P)"
case "$M5_OUT/" in "$ROOT/"*) echo 'M5_OUT must be outside the source repository' >&2; exit 2;; esac
export OUT="$M5_OUT"
mkdir -p "$OUT/cre1" "$OUT/results"

EXPECTED_DATA_SHA=7d70d20f79f03e10ffb4cef76292cd52201e66299aca42c8a1287ebf2b327dd2
EXPECTED_CRE1_SHA=2290194d25cc98cf6cc42e6b57f5ddcb2105ba14115e2e5295ad50c906f10db6
check_sha() {
  local actual
  actual="$(sha256sum "$1")"
  actual="${actual%% *}"
  if [[ "$actual" != "$2" ]]; then
    printf 'SHA_MISMATCH file=%s expected=%s actual=%s\n' "$1" "$2" "$actual" >&2
    return 1
  fi
  printf 'SHA_OK %s %s\n' "$actual" "$1"
}

cd "$ROOT"
echo 'STEP_1 generate frozen historical P3 CRE1 parity checkpoint locally (never committed)'
node .github/continuum/cre-parity.mjs prepare "$OUT/cre1"
check_sha "$OUT/cre1/node-cre1.cre1" "$EXPECTED_CRE1_SHA"

echo 'STEP_2 compile REAL Kotlin research + CRE1 sources from repository, no model stub'
WITH_ARGON_TEST=1 scripts/exp20-p2-host-run.sh build

echo 'STEP_3 run original host regression and new M5 negative/structure checks'
scripts/exp20-p2-host-run.sh junit \
  at.heinz.deviceai.research.exp20.BoundedCtrnnAliasingTest \
  at.heinz.deviceai.research.exp20.BoundedCtrnnTest \
  at.heinz.deviceai.research.argon4.Argon4F1PrototypeTest \
  at.heinz.deviceai.research.exp20.p2.P2InfraTest \
  at.heinz.deviceai.research.exp20.p2.P2SafetyAuditTest \
  at.heinz.deviceai.research.exp20.p3.P4MathTest
scripts/exp20-p2-host-run.sh main at.heinz.deviceai.research.exp20.m5.M5DataSelfTest
scripts/exp20-p2-host-run.sh main at.heinz.deviceai.research.exp20.m5.M5BlindGateSelfTest

echo 'STEP_4 generate DEV/VAL only and verify dataset SHA'
scripts/exp20-p2-host-run.sh main at.heinz.deviceai.research.exp20.m5.M5Data generate "$OUT/dev-val.tsv"
check_sha "$OUT/dev-val.tsv" "$EXPECTED_DATA_SHA"
scripts/exp20-p2-host-run.sh main at.heinz.deviceai.research.exp20.m5.M5LexicalAudit "$OUT/dev-val.tsv"

echo 'STEP_5 execute actual CRE1 query-blind VAL gate with two independent immutable hashes'
scripts/exp20-p2-host-run.sh main at.heinz.deviceai.research.exp20.m5.M5Cre1BlindGate \
  "$OUT/dev-val.tsv" "$OUT/cre1/node-cre1.cre1" "$EXPECTED_DATA_SHA" "$EXPECTED_CRE1_SHA" \
  "$OUT/results/cre1-blind-gate.txt"
printf 'COMPLETE host_only=true sealed_opened=false production_modified=false results=%s\n' "$OUT/results/cre1-blind-gate.txt"
