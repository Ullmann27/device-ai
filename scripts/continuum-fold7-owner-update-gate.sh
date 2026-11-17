#!/usr/bin/env bash
# Git-SHA-pinned preflight for the EXISTING signed Continuum Lokal main app.
# No app installation, model deletion, Git pull or signature regeneration.
set -Eeuo pipefail
umask 077
blocked() { printf 'CONTINUUM_FOLD7_BLOCKED: %s\n' "$*" >&2; exit 69; }
usage() {
  printf 'Usage: bash scripts/continuum-fold7-owner-update-gate.sh doctor|qa|owner <approved-full-git-SHA>\n' >&2
  exit 64
}
[[ $# -eq 2 ]] || usage
mode="$1"
approved_sha="$2"
case "$mode" in doctor|qa|owner) ;; *) usage ;; esac
[[ "$approved_sha" =~ ^[0-9a-f]{40}$ ]] || usage

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
[[ -f "$root/app/build.gradle.kts" &&
   -f "$root/scripts/continuum-fold7-standalone-apk.sh" &&
   -f "$root/scripts/continuum-fold7-standalone-apk.test.mjs" &&
   -f "$root/scripts/continuum-fusion-full-regression.sh" ]] ||
  blocked 'Full Continuum Lokal source checkout required'
command -v git >/dev/null 2>&1 || blocked 'Git is unavailable'
actual_sha="$(git -C "$root" rev-parse --verify HEAD 2>/dev/null)" || blocked 'Git checkout missing'
[[ "$actual_sha" == "$approved_sha" ]] ||
  blocked "Wrong source SHA: expected $approved_sha; found $actual_sha"
[[ -z "$(git -C "$root" status --porcelain --untracked-files=normal)" ]] ||
  blocked 'Git working tree is not clean'
branch="$(git -C "$root" symbolic-ref --quiet --short HEAD 2>/dev/null)" ||
  blocked 'Detached HEAD is not an approved development branch'
case "$branch" in main|master|production|prod|release|main/*|master/*|production/*|prod/*|release/*)
  blocked 'Production branch cannot be used as candidate' ;;
esac
origin="$(git -C "$root" remote get-url origin 2>/dev/null)" || blocked 'Git origin missing'
case "$origin" in
  https://github.com/Ullmann27/device-ai|https://github.com/Ullmann27/device-ai.git|git@github.com:Ullmann27/device-ai.git|ssh://git@github.com/Ullmann27/device-ai.git) ;;
  *) blocked 'Git origin is not the official private Continuum repository' ;;
esac
# Prevent a misleading pass for the legacy mentorpaired APK.
grep -Fq '"at.heinz.deviceai.continuumlocal"' "$root/app/build.gradle.kts" ||
  blocked 'This source cannot produce the Continuum Lokal package'
grep -Fq -- '-PcontinuumLocalStandalone=true' "$root/scripts/continuum-fold7-standalone-apk.sh" ||
  blocked 'The local standalone signing lane is unavailable'

# Every QA run receives a fresh external evidence directory; previous valid
# runs remain immutable and cannot cause a false failure on a later check.
new_evidence_directory() {
  local evidence_root="$HOME/.local/state/device-ai-continuum/fusion-regression"
  mkdir -p -- "$evidence_root" || blocked 'Cannot create private evidence root'
  [[ ! -L "$evidence_root" ]] || blocked 'Evidence root is a symlink'
  mktemp -d "$evidence_root/${mode}-${actual_sha}-XXXXXXXX" ||
    blocked 'Cannot reserve fresh regression evidence directory'
}
case "$mode" in
  doctor)
    bash -n "$root/scripts/continuum-fold7-standalone-apk.sh" ||
      blocked 'Local release script has invalid shell syntax'
    printf 'CONTINUUM_FOLD7_DOCTOR_SOURCE=%s\n' "$actual_sha"
    printf 'CONTINUUM_FOLD7_TARGET_PACKAGE=at.heinz.deviceai.continuumlocal\n'
    printf 'CONTINUUM_FOLD7_DOCTOR_INSTALLABLE=FALSE\n'
    ;;
  qa)
    command -v node >/dev/null 2>&1 || blocked 'Node.js unavailable for QA contracts'
    (
      unset CONTINUUM_MENTOR_SIGNING_FILE CONTINUUM_MENTOR_SIGNING_PASSWORD
      unset CONTINUUM_LOCAL_SIGNING_FILE CONTINUUM_LOCAL_SIGNING_PASSWORD
      unset CONTINUUM_OWNER_LOCAL_APPROVED_SHA
      export CONTINUUM_FUSION_REGRESSION_OUTPUT="$(new_evidence_directory)"
      bash "$root/scripts/continuum-fusion-full-regression.sh" "$approved_sha" || exit 69
      node --test "$root/scripts/continuum-fold7-standalone-apk.test.mjs" || exit 69
    ) || blocked 'Continuum Lokal QA contracts failed'
    printf 'CONTINUUM_FOLD7_QA_PASS=%s\n' "$actual_sha"
    printf 'CONTINUUM_FOLD7_QA_INSTALLABLE=FALSE\n'
    ;;
  owner)
    [[ "${CONTINUUM_OWNER_LOCAL_APPROVED_SHA:-}" == "$approved_sha" ]] ||
      blocked 'Exact-source owner approval missing'
    [[ -z "${CONTINUUM_MENTOR_SIGNING_FILE:-}" &&
       -z "${CONTINUUM_MENTOR_SIGNING_PASSWORD:-}" ]] ||
      blocked 'Historic Mentor Pair signer must never be used for Continuum Lokal'
    version="${CONTINUUM_OWNER_NEXT_VERSION_CODE:-}"
    [[ "$version" =~ ^(2[7-9]|[3-9][0-9]|[1-9][0-9]{2,3})$ ]] &&
      (( version <= 9999 )) || blocked 'New Continuum Lokal versionCode must be 27..9999'
    keydir="$HOME/.local/share/device-ai-continuum/signing"
    keyfile="$keydir/continuum-local.p12"
    fingerprint="$keydir/continuum-local-cert-sha256.txt"
    [[ -d "$keydir" && ! -L "$keydir" &&
       -s "$keyfile" && ! -L "$keyfile" &&
       -s "$fingerprint" && ! -L "$fingerprint" ]] ||
      blocked 'Original Continuum Lokal P12 and pinned certificate must exist; no new signer'
    expected_fp='6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23'
    [[ "$(cat "$fingerprint")" == "$expected_fp" ]] ||
      blocked 'Continuum Lokal certificate pin differs from installed version'
    # Re-run full regressions on the exact source without passing signer material.
    (
      unset CONTINUUM_LOCAL_SIGNING_FILE CONTINUUM_LOCAL_SIGNING_PASSWORD
      export CONTINUUM_FUSION_REGRESSION_OUTPUT="$(new_evidence_directory)"
      bash "$root/scripts/continuum-fusion-full-regression.sh" "$approved_sha"
    ) || blocked 'Full local regression failed; signed release refused'
    [[ "$approved_sha" == "$(git -C "$root" rev-parse --verify HEAD)" &&
       -z "$(git -C "$root" status --porcelain --untracked-files=normal)" ]] ||
      blocked 'Source changed during QA; owner release refused'
    unset CONTINUUM_LOCAL_SIGNING_FILE CONTINUUM_LOCAL_SIGNING_PASSWORD
    bash "$root/scripts/continuum-fold7-standalone-apk.sh" build "$version" ||
      blocked 'Continuum Lokal release build failed'
    printf 'CONTINUUM_FOLD7_OWNER_ARTIFACT_NOT_INSTALLED=TRUE\n'
    ;;
esac
