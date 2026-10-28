#!/usr/bin/env bash
# Single entry point for the real Continuum owner app on a verified checkout.
# QA never installs. Owner signing is an explicit, separate invocation.
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
   -f "$root/scripts/continuum-local-owner-apk.sh" &&
   -f "$root/scripts/continuum-local-mainapp-qa.sh" &&
   -f "$root/scripts/continuum-fusion-full-regression.sh" ]] ||
  blocked 'Full existing main-app source checkout required'
command -v git >/dev/null 2>&1 || blocked 'Git is not available'
actual_sha="$(git -C "$root" rev-parse --verify HEAD 2>/dev/null)" ||
  blocked 'No Git repository. Clone the private device-ai project into Termux first.'
[[ "$actual_sha" == "$approved_sha" ]] ||
  blocked "Wrong source SHA: expected $approved_sha; found $actual_sha"
[[ -z "$(git -C "$root" status --porcelain --untracked-files=normal)" ]] ||
  blocked 'Git working tree is not clean'
origin="$(git -C "$root" remote get-url origin 2>/dev/null)" ||
  blocked 'Official GitHub origin is missing'
case "$origin" in
  https://github.com/Ullmann27/device-ai|https://github.com/Ullmann27/device-ai.git|git@github.com:Ullmann27/device-ai.git|ssh://git@github.com/Ullmann27/device-ai.git) ;;
  *) blocked 'Git origin is not the official private Continuum repository' ;;
esac
case "$mode" in
  doctor)
    bash "$root/scripts/continuum-local-owner-apk.sh" doctor
    printf 'CONTINUUM_FOLD7_DOCTOR_SOURCE=%s\n' "$actual_sha"
    ;;
  qa)
    # Never expose the original P12 or password to the disposable QA toolchain.
    (
      unset CONTINUUM_MENTOR_SIGNING_FILE CONTINUUM_MENTOR_SIGNING_PASSWORD
      unset CONTINUUM_OWNER_LOCAL_APPROVED_SHA
      export CONTINUUM_LOCAL_QA_APPROVED_SHA="$approved_sha"
      bash "$root/scripts/continuum-fusion-full-regression.sh" "$approved_sha"
      bash "$root/scripts/continuum-local-mainapp-qa.sh" --offline
    )
    printf 'CONTINUUM_FOLD7_QA_PASS=%s\n' "$actual_sha"
    printf 'CONTINUUM_FOLD7_QA_INSTALLABLE=FALSE\n'
    ;;
  owner)
    [[ "${CONTINUUM_OWNER_LOCAL_APPROVED_SHA:-}" == "$approved_sha" ]] ||
      blocked 'Owner has not explicitly approved this exact Git SHA'
    [[ -n "${CONTINUUM_OWNER_NEXT_VERSION_CODE:-}" ]] ||
      blocked 'Set a new versionCode strictly greater than the currently installed owner app'
    [[ -n "${CONTINUUM_MENTOR_SIGNING_FILE:-}" &&
       -f "$CONTINUUM_MENTOR_SIGNING_FILE" &&
       ! -L "$CONTINUUM_MENTOR_SIGNING_FILE" ]] ||
      blocked 'Original private owner P12 is unavailable'
    [[ -n "${CONTINUUM_MENTOR_SIGNING_PASSWORD:-}" ]] ||
      blocked 'Original owner P12 password is unavailable'
    bash "$root/scripts/continuum-local-owner-apk.sh" build --offline
    printf 'CONTINUUM_FOLD7_OWNER_ARTIFACT_NOT_INSTALLED=TRUE\n'
    ;;
esac
