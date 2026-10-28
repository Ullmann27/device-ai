#!/usr/bin/env bash
# One owner-controlled Fold7 update path. No CI, root, install, key upload or main write.
set -Eeuo pipefail
umask 077
die() { printf 'CONTINUUM_FOLD7_UPDATE_BLOCKED=%s\n' "$*" >&2; exit 69; }
usage() {
  printf 'Usage: bash scripts/continuum-fold7-update.sh <next-versionCode> [--offline]\n' >&2
  printf 'Example: bash scripts/continuum-fold7-update.sh 27\n' >&2
  exit 64
}
[[ $# -ge 1 && $# -le 2 ]] || usage
version="$1"
[[ "$version" =~ ^(2[7-9]|[3-9][0-9]|[1-9][0-9]{2,3})$ ]] && (( version <= 9999 )) || usage
[[ $# -eq 1 || "$2" == "--offline" ]] || usage
offline=false
if [[ $# -eq 2 ]]; then offline=true; fi

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$root"
[[ "${PREFIX:-}" == /data/data/com.termux/files/usr* ]] || die 'This update wrapper runs only in Fold7 Termux'
[[ -f scripts/continuum-fold7-standalone-apk.sh &&
   -f scripts/continuum-fusion-full-regression.sh &&
   -f .github/continuum/cre-parity.mjs &&
   -f .github/continuum/contrastive-parity.mjs ]] ||
  die 'Required existing Continuum release and parity scripts are missing'
[[ "$(git rev-parse --show-toplevel 2>/dev/null)" == "$root" ]] || die 'Use the original source checkout'
[[ -z "$(git status --porcelain --untracked-files=normal)" ]] || die 'Uncommitted changes; stop and review first'
branch="$(git symbolic-ref --quiet --short HEAD)" || die 'Detached Git HEAD is not a release candidate'
case "$branch" in main|master|production|release|prod) die 'Refusing to release directly from the production branch';; esac
origin="$(git remote get-url origin 2>/dev/null)" || die 'GitHub origin missing'
case "$origin" in
  https://github.com/Ullmann27/device-ai|https://github.com/Ullmann27/device-ai.git|git@github.com:Ullmann27/device-ai.git|ssh://git@github.com/Ullmann27/device-ai.git) ;;
  *) die 'Unexpected Git origin; do not fetch from an untrusted project';;
esac
signing="$HOME/.local/share/device-ai-continuum/signing"
[[ -d "$signing" && ! -L "$signing" &&
   -s "$signing/continuum-local.p12" && ! -L "$signing/continuum-local.p12" &&
   -s "$signing/continuum-local-cert-sha256.txt" &&
   ! -L "$signing/continuum-local-cert-sha256.txt" ]] ||
  die 'Existing original Continuum Lokal P12 and certificate fingerprint required; NEVER regenerate'

if [[ "$offline" == false ]]; then
  upstream="$(git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}' 2>/dev/null)" ||
    die 'Branch has no configured upstream'
  [[ "$upstream" == origin/* ]] || die 'Tracked upstream must be origin'
  printf 'CONTINUUM_UPDATE_SYNC_BRANCH=%s\n' "$branch"
  GIT_TERMINAL_PROMPT=0 git pull --ff-only ||
    die 'Git fast-forward failed. For password-free updates, configure GitHub SSH on Fold7 once'
else
  printf 'CONTINUUM_UPDATE_OFFLINE_SOURCE=TRUE\n'
fi
[[ -z "$(git status --porcelain --untracked-files=normal)" ]] ||
  die 'Unexpected changes after sync'
head="$(git rev-parse --verify HEAD)"
[[ "$head" =~ ^[0-9a-f]{40}$ ]] || die 'Untrusted Git source hash'
printf 'CONTINUUM_UPDATE_APPROVED_SOURCE=%s\n' "$head"

state="$HOME/.local/state/device-ai-continuum"
mkdir -p "$state/parity" "$state/fusion-regression"
work="$(mktemp -d "$state/parity/run-XXXXXXXX")"
export CONTINUUM_CRE1_PARITY_DIR="$work/cre1"
export CONTINUUM_CONTRASTIVE_PARITY_DIR="$work/contrastive"
export CONTINUUM_FUSION_REGRESSION_OUTPUT
CONTINUUM_FUSION_REGRESSION_OUTPUT="$(mktemp -d "$state/fusion-regression/$head-XXXXXXXX")"
export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export CONTINUUM_GRADLE_PATH="$HOME/continuum-toolchains/gradle-8.11.1/bin/gradle"
unset CONTINUUM_MENTOR_SIGNING_PASSWORD CONTINUUM_MENTOR_SIGNING_FILE
node .github/continuum/cre-parity.mjs prepare "$CONTINUUM_CRE1_PARITY_DIR" ||
  die 'Could not prepare CRE1 Kotlin/Node comparison'
node .github/continuum/contrastive-parity.mjs prepare "$CONTINUUM_CONTRASTIVE_PARITY_DIR" ||
  die 'Could not prepare SWB2 Kotlin/Node comparison'
bash scripts/continuum-fusion-full-regression.sh "$head" ||
  die 'Full Android JUnit/lint regression did not pass; no release APK was built'
node .github/continuum/cre-parity.mjs verify "$CONTINUUM_CRE1_PARITY_DIR" ||
  die 'CRE1 Kotlin/Node result diverged'
node .github/continuum/contrastive-parity.mjs verify "$CONTINUUM_CONTRASTIVE_PARITY_DIR" ||
  die 'SWB2 Kotlin/Node result diverged'
printf 'CONTINUUM_UPDATE_FULL_QA_PASS=%s\n' "$head"
bash scripts/continuum-fold7-standalone-apk.sh build "$version" ||
  die 'Signed local release build failed'
printf 'CONTINUUM_UPDATE_DONE=APK_PREPARED_NOT_INSTALLED\n'
printf 'CONTINUUM_UPDATE_NOTE=Open verified APK in Downloads/Continuum_back_up manually; keep all old app data.\n'
