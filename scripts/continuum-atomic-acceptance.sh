#!/usr/bin/env bash
# EXP-01/02/12. One-shot offline acceptance using the existing QA systems.
# Does NOT assemble, sign, install, update weights, merge or write to main.
set -Eeuo pipefail
umask 077

if [[ $# -ne 4 || "$1" != "--expected-head" || "$3" != "--out" ]]; then
  echo 'Usage: bash scripts/continuum-atomic-acceptance.sh --expected-head <40-hex SHA> --out <new absolute directory>' >&2
  exit 2
fi
expected="$2"
destination="$4"
[[ "$expected" =~ ^[a-f0-9]{40}$ && "$destination" == /* ]] || exit 2
for executable in git node java timeout readlink; do
  command -v "$executable" >/dev/null || { echo "UNAVAILABLE $executable" >&2; exit 3; }
done
root="$(cd -- "$(dirname -- "$0")/.." && pwd -P)"
cd "$root"
actual="$(git rev-parse --verify HEAD)"
[[ "$actual" == "$expected" ]] || { echo 'HEAD_MISMATCH' >&2; exit 4; }
branch="$(git symbolic-ref --quiet --short HEAD)" || { echo 'DETACHED_HEAD_REFUSED' >&2; exit 4; }
case "$branch" in main|master|production|release/*) echo 'PROTECTED_BRANCH_REFUSED' >&2; exit 4 ;; esac
[[ -z "$(git status --porcelain --untracked-files=all)" ]] || { echo 'DIRTY_SOURCE_REFUSED' >&2; exit 4; }
[[ -f "$root/gradlew" ]] || { echo 'GRADLE_WRAPPER_MISSING' >&2; exit 3; }

# readlink -m canonicalizes parents even when the new destination does not exist.
out="$(readlink -m -- "$destination")"
case "$out/" in "$root/"*) echo 'SOURCE_OUTPUT_REFUSED' >&2; exit 4 ;; esac
[[ ! -e "$out" && ! -L "$out" ]] || { echo 'OUTPUT_MUST_BE_NEW' >&2; exit 4; }
mkdir -- "$out"
printf '%s\n' "$expected" > "$out/source-head.sha"
printf '%s\n' "$branch" > "$out/source-branch.txt"
: > "$out/checks.tsv"
grade=FAIL

finish() {
  local code="$?"
  trap - EXIT INT TERM
  RECEIPT_OUTPUT="$out" RECEIPT_SHA="$expected" RECEIPT_BRANCH="$branch" \
    RECEIPT_GRADE="$grade" RECEIPT_EXIT="$code" \
    node --input-type=commonjs - <<'NODE' || true
const fs=require('node:fs'), path=require('node:path'), e=process.env;
const names=['android-gradle','node-critical','node-full'];
const completed=fs.readFileSync(path.join(e.RECEIPT_OUTPUT,'checks.tsv'),'utf8')
  .split('\n').filter(Boolean).map(x=>x.split('\t'));
const checks=names.map(name=>{
  const item=completed.find(x=>x[0]===name);
  return {name,status:item?.[1]||'NOT_RUN',exitCode:item?Number(item[2]):null,
    rawLog:name+'.log'};
});
const passed=e.RECEIPT_GRADE==='PASS' && e.RECEIPT_EXIT==='0' &&
  checks.every(x=>x.status==='PASS' && x.exitCode===0);
const receipt={schemaVersion:1,sourceHead:e.RECEIPT_SHA,sourceBranch:e.RECEIPT_BRANCH,
  status:passed?'PASS':'FAIL',checks,deviceVerified:false,apkProduced:false,
  modelInferenceVerified:false,signingPerformed:false,
  generatedBy:'scripts/continuum-atomic-acceptance.sh',
  recordedAt:new Date().toISOString()};
fs.writeFileSync(path.join(e.RECEIPT_OUTPUT,'acceptance.json'),
  JSON.stringify(receipt,null,2)+'\n',{flag:'wx',mode:0o600});
NODE
  exit "$code"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# Never allow a QA command to see release-signing credentials.
unset CONTINUUM_LOCAL_SIGNING_FILE CONTINUUM_LOCAL_SIGNING_PASSWORD
unset CONTINUUM_MENTOR_SIGNING_FILE CONTINUUM_MENTOR_SIGNING_PASSWORD

run() {
  local name="$1"
  shift
  local code=0
  timeout --signal=TERM --kill-after=20s 45m "$@" > "$out/$name.log" 2>&1 || code="$?"
  if [[ "$code" -eq 0 ]]; then
    printf '%s\tPASS\t0\n' "$name" >> "$out/checks.tsv"
  else
    printf '%s\tFAIL\t%s\n' "$name" "$code" >> "$out/checks.tsv"
    echo "QA_STEP_FAILED $name code=$code" >&2
    exit 1
  fi
}

# Native Kotlin/Android toolgate tests + all app JUnit + lint. No APK task.
run android-gradle bash "$root/gradlew" --offline --no-daemon \
  :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug

# Existing canonical Node QA enforces exit codes, confirmed process termination,
# complete JUnit evidence, source identity and no skipped-only success.
run node-critical node "$root/scripts/continuum-reliability-qa.mjs" critical --out "$out/critical"
node --input-type=commonjs - "$out/critical/report.json" <<'NODE'
const r=require(process.argv[2]);
if(r.status!=='PASS'||r.sourceUnchanged!==true)process.exit(1);
NODE
run node-full node "$root/scripts/continuum-reliability-qa.mjs" full-node --out "$out/full-node"
node --input-type=commonjs - "$out/full-node/report.json" <<'NODE'
const r=require(process.argv[2]);
if(r.status!=='PASS'||r.sourceUnchanged!==true)process.exit(1);
NODE
[[ "$(git rev-parse --verify HEAD)" == "$expected" ]] || { echo 'HEAD_CHANGED' >&2; exit 4; }
[[ -z "$(git status --porcelain --untracked-files=all)" ]] || { echo 'SOURCE_CHANGED' >&2; exit 4; }
grade=PASS
echo "CONTINUUM_ACCEPTANCE_PASS $expected"
