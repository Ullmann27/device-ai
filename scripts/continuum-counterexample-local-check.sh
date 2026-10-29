#!/usr/bin/env bash
# Opt-in local research validation: never edits the app, ledger, or protected branch.
set -Eeuo pipefail
umask 077
fail() { printf 'COUNTEREXAMPLE_CHECK_BLOCKED: %s\n' "$*" >&2; exit 69; }
[[ $# -eq 2 && "$1" =~ ^[a-f0-9]{40}$ && "$2" == /* ]] || {
 echo 'Usage: bash scripts/continuum-counterexample-local-check.sh <exact-HEAD-SHA> <new-absolute-output-dir>' >&2
 exit 64
}
expected="$1"
out="$2"
root="$(cd -- "$(dirname -- "$0")/.." && pwd -P)"
[[ -f "$root/experimental/ccc1-counterexample-lab/run.mjs" ]] || fail 'Complete checkout required'
[[ "$(git -C "$root" rev-parse --verify HEAD)" == "$expected" ]] || fail 'SHA mismatch'
[[ -z "$(git -C "$root" status --porcelain)" ]] || fail 'Dirty source tree'
[[ "$out" != "$root" && "$out" != "$root"/* ]] || fail 'Output inside source tree'
[[ ! -e "$out" ]] || fail 'Use a new output directory'
command -v node >/dev/null 2>&1 || fail 'Node required'
node_major="$(node -p 'Number(process.versions.node.split(".")[0])')"
[[ "$node_major" == 20 || "$node_major" == 22 || "$node_major" == 24 ]] || fail 'Node20/22/24 required'
mkdir -m 700 -p -- "$out"
cd "$root"
node --test experimental/ccc1-counterexample-lab/policy.test.mjs \
 scripts/continuum-host-evidence-gate.test.mjs \
 scripts/continuum-evidence-integrity.test.mjs \
 scripts/continuum-fold7-acceptance.test.mjs > "$out/unit-tests.log" 2>&1 || fail 'Research unit tests failed'
node experimental/ccc1-counterexample-lab/run.mjs "$out/trial" > "$out/trial-output.log" 2>&1 || fail 'Research trial failed'
[[ "$(git -C "$root" rev-parse HEAD)" == "$expected" && -z "$(git -C "$root" status --porcelain)" ]] || fail 'Source changed during run'
node - "$out/trial/report.json" "$out/summary.json" "$expected" <<'NODE'
const fs = require('node:fs');
const [reportFile,outputFile,expected] = process.argv.slice(2);
const report = JSON.parse(fs.readFileSync(reportFile,'utf8'));
if(report.sourceSha!==expected || report.productionActivationAllowed!==false ||
 report.mode!=='RESEARCH_SHADOW_ONLY' || report.sourceUnchanged!==true)
 throw Error('RESEARCH_SOURCE_OR_AUTHORITY_MISMATCH');
fs.writeFileSync(outputFile,JSON.stringify({schema:1,source:expected,
 result:'RESEARCH_ONLY_NOT_RELEASE_QA',tasks:report.tasks,arms:report.arms,
 device:'NOT_RUN',apk:'NOT_BUILT',productionMergeAuthorized:false},null,2)+'\n');
NODE
printf 'COUNTEREXAMPLE_RESEARCH_COMPLETE: %s\n' "$out/summary.json"
