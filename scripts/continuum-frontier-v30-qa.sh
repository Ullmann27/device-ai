#!/usr/bin/env bash
# Research Android QA from the REAL current project; never sign or publish an APK.
set -Eeuo pipefail
umask 077
cd "$(git rev-parse --show-toplevel)"
[[ -z "${CONTINUUM_LOCAL_SIGNING_FILE:-}" && -z "${CONTINUUM_LOCAL_SIGNING_PASSWORD:-}" &&
   -z "${CONTINUUM_MENTOR_SIGNING_FILE:-}" && -z "${CONTINUUM_MENTOR_SIGNING_PASSWORD:-}" ]] || {
  echo 'QA MUST NOT RECEIVE OWNER SIGNING SECRETS' >&2
  exit 71
}
command -v node >/dev/null || { echo "Node is required" >&2; exit 71; }
[[ -f scripts/render-continuum-m8-qa.sh ]] || { echo "M8 baseline QA runner missing" >&2; exit 71; }
[[ -f tools/continuum-virtual-fabric/reuse-broker.test.mjs ]] || {
  echo 'Frontier research tests missing' >&2
  exit 71
}
[[ -f app/src/main/java/at/heinz/deviceai/cognitive/ContinuumComputeReuseResearch.kt ]] || {
  echo 'Kotlin research brain adapter missing' >&2
  exit 71
}
if ! grep -Fq 'continuumStandaloneVersionCode=29' scripts/render-continuum-m8-qa.sh; then
  echo 'Unexpected M8 QA version structure; manual review required' >&2
  exit 71
fi
if ! grep -Fq 'research/continuum-m8-operational-model-20261031' scripts/render-continuum-m8-qa.sh; then
  echo 'Unexpected M8 QA branch contract; manual review required' >&2
  exit 71
fi
mkdir -p continuum-frontier-qa
printf 'status=NODE_TESTS_PENDING\nsource_sha=%s\nsigned=false\n' "$(git rev-parse HEAD)" > continuum-frontier-qa/STATUS.txt
node --test tools/continuum-virtual-fabric/*.test.mjs > continuum-frontier-qa/NODE-FRONTIER.txt 2>&1 || {
  cat continuum-frontier-qa/NODE-FRONTIER.txt >&2
  echo 'Frontier Node tests failed' >&2
  exit 72
}
node tools/continuum-virtual-fabric/reuse-experiment.mjs > continuum-frontier-qa/REUSE-METRICS.txt
node tools/continuum-virtual-fabric/shared-stage-experiment.mjs > continuum-frontier-qa/SHARED-STAGES.txt
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
sed -e 's#research/continuum-m8-operational-model-20261031#research/continuum-nemo-virtual-fabric-20261031#g' \
    -e 's/continuumStandaloneVersionCode=29/continuumStandaloneVersionCode=30/g' \
    -e 's/version_code=29/version_code=30/g' \
    scripts/render-continuum-m8-qa.sh > "$tmp"
bash -n "$tmp"
# The canonical M8 QA executes full Android JVM tests, lint, and unsigned release assembly.
# This additional research driver cannot bypass any of those checks.
bash "$tmp"
test -s app/build/outputs/apk/release/app-release-unsigned.apk || {
  echo 'No unsigned APK produced by Android QA' >&2; exit 73;
}
printf 'status=SOURCE_QA_PASS_UNSIGNED_NOT_INSTALLABLE\nsource_sha=%s\nversion_code=30\nsigned=false\n' "$(git rev-parse HEAD)" > continuum-frontier-qa/STATUS.txt
printf '<!doctype html><html lang="de"><meta charset="utf-8"><title>Continuum Research v30 QA</title><body><h1>QA — unsigned only</h1><p>No APK approved for device installation; original certificate absent.</p></body></html>' > continuum-frontier-qa/index.html
echo 'CONTINUUM_FRONTIER_V30_QA=PASS UNSIGNED_NOT_INSTALLABLE'
