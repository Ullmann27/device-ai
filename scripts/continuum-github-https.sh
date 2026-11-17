#!/usr/bin/env bash
# Existing authorized checkout only. Uses gh's credential helper; never copies
# tokens into remotes, shell arguments, source files or build logs.
set -Eeuo pipefail
umask 077
die(){ printf 'CONTINUUM_HTTPS_BLOCKED: %s\n' "$*" >&2; exit 69; }
[[ $# == 1 && ( "$1" == doctor || "$1" == setup ) ]] || { echo 'Usage: bash scripts/continuum-github-https.sh doctor|setup' >&2; exit 64; }
mode=$1
for tool in git gh timeout; do command -v "$tool" >/dev/null 2>&1 || die "Missing executable: $tool"; done
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
origin="$(git -C "$root" remote get-url origin 2>/dev/null)" || die 'Existing official repository checkout required'
case "$origin" in
 https://github.com/Ullmann27/device-ai|https://github.com/Ullmann27/device-ai.git|git@github.com:Ullmann27/device-ai.git|ssh://git@github.com/Ullmann27/device-ai.git) ;;
 *) die 'Origin is not the official Ullmann27/device-ai repository; unchanged' ;;
esac
if ! timeout --foreground 15 gh auth status --hostname github.com >/dev/null 2>&1; then
 die 'GitHub authentication unavailable. In your PRIVATE Termux terminal run: GH_PROMPT_DISABLED=1 GH_BROWSER=termux-open-url timeout --foreground 300 gh auth login --hostname github.com --git-protocol https --web ; complete device authorization privately, then rerun setup. Do not paste credentials into chat or Git URLs.'
fi
url='https://github.com/Ullmann27/device-ai.git'
# Bypass stale SSH/credential-helper configuration for this read-only check.
# No token is expanded: Git asks the installed gh helper through stdin/stdout.
if ! GIT_TERMINAL_PROMPT=0 timeout --foreground 30 git -c "url.$url.insteadOf=$url" -c credential.helper= -c 'credential.helper=!gh auth git-credential' ls-remote --exit-code "$url" HEAD >/dev/null 2>&1; then
 die 'Authenticated HTTPS read failed (network, account access or credential helper). Origin unchanged.'
fi
if [[ "$mode" == setup ]]; then
 timeout --foreground 15 gh auth setup-git --hostname github.com >/dev/null 2>&1 || die 'Cannot configure gh credential helper'
 # An exact repository-local identity mapping wins over a broader historical
 # HTTPS -> SSH insteadOf rule without deleting the owner's global Git setup.
 git -C "$root" config --local "url.$url.insteadOf" "$url"
 git -C "$root" remote set-url origin "$url"
fi
printf 'CONTINUUM_HTTPS=READ_VERIFIED\nrepository=Ullmann27/device-ai\nmode=%s\nremote_transport=%s\n' "$mode" "$( [[ "$mode" == setup || "$origin" == https:* ]] && echo HTTPS || echo SSH_UNCHANGED_RUN_SETUP )"
