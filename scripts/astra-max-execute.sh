#!/usr/bin/env bash
# Offline device-ai acceptance and worker launcher. Never changes tracked sources.
set -Eeuo pipefail

fail() { printf '[CONTINUUM BLOCKED] %s\n' "$*" >&2; exit 69; }
usage() {
  printf 'Usage: %s {validate|worker-once|worker-loop|android-release}\n' "$0" >&2
  exit 64
}
(( $# == 1 )) || usage
mode="$1"
case "$mode" in
  validate|worker-once|worker-loop|android-release) ;;
  *) usage ;;
esac
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
root="$(cd -- "$script_dir/.." && pwd -P)"
cd "$root"
command -v git >/dev/null 2>&1 || fail 'Git fehlt.'
command -v node >/dev/null 2>&1 || fail 'Node.js fehlt.'
[[ "$(git rev-parse --show-toplevel 2>/dev/null)" == "$root" ]] ||
  fail 'Kein vollständiger Git-Checkout.'
branch="$(git symbolic-ref --quiet --short HEAD)" ||
  fail 'Detached HEAD: bitte den isolierten Arbeitsbranch auschecken.'
[[ "$branch" == "continuum/argon-safety-review-20261017" ]] ||
  fail "Falscher Branch: $branch. Kein automatischer Lauf auf main oder Fremdbranches."
head="$(git rev-parse --verify HEAD)" || fail 'Commit nicht bestimmbar.'
[[ "$head" =~ ^[0-9a-f]{40}$ ]] || fail 'Ungültiger Commit-Hash.'
[[ -z "$(git status --porcelain)" ]] ||
  fail 'Arbeitsverzeichnis verändert. Vor dem Testen Änderungen sichern oder isolieren.'
node_major="$(node -p 'Number(process.versions.node.split(".")[0])')" ||
  fail 'Node-Version unbekannt.'
[[ "$node_major" == 20 || "$node_major" == 22 ]] ||
  fail "Node 20 oder 22 erforderlich, gefunden $node_major."

state_home="${XDG_STATE_HOME:-$HOME/.local/state}/device-ai-continuum"
realpath_cmd="$(command -v realpath)" || fail 'realpath fehlt.'
state_home="$("$realpath_cmd" -m -- "$state_home")"
[[ "$state_home" != "$root" && "$state_home" != "$root"/* ]] ||
  fail 'Worker-Zustand darf nicht im Git-Checkout liegen.'
mkdir -p -- "$state_home"
chmod 700 -- "$state_home"
state_home="$(cd -- "$state_home" && pwd -P)"
[[ "$state_home" != "$root" && "$state_home" != "$root"/* ]] ||
  fail 'Worker-Zustand verweist durch einen Symlink in den Git-Checkout.'

printf 'Device AI Continuum | %s | Branch %s | Commit %s\n' "$mode" "$branch" "$head"

case "$mode" in
  validate)
    node --test .github/continuum/autonomous-teamleader.test.mjs \
      .github/continuum/teamleader-end-to-end.test.mjs \
      .github/continuum/development-planner.test.mjs \
      .github/continuum/m4-local-gate.test.mjs \
      .github/continuum/repair-cycle-safety.integration.test.mjs
    node .github/continuum/m4-local-gate.mjs \
      --root "$root" --out "$state_home/proofs" --budget-minutes 30
    printf 'Node/Backend/Repair-E2E-Pfad geprüft; keine Android-Freigabe behauptet.\n'
    ;;
  worker-once)
    node .github/continuum/teamleader-cli.mjs --once \
      --root "$root" --state "$state_home/worker" --budget-minutes 20
    ;;
  worker-loop)
    node .github/continuum/teamleader-cli.mjs --loop \
      --root "$root" --state "$state_home/worker" \
      --interval-minutes 60 --budget-minutes 20
    ;;
  android-release)
    bash scripts/local-m4-android.sh "$head"
    gradle :app:lintDebug --console=plain -Pkotlin.incremental=false
    gradle :app:assembleDebug -PdeviceAiPreview=true --console=plain -Pkotlin.incremental=false
    artifact='app/build/outputs/apk/debug/app-debug.apk'
    [[ -f "$artifact" ]] || fail "APK nicht erzeugt: $artifact"
    sha256sum "$artifact"
    ;;
esac
