#!/usr/bin/env bash
# Optional Aider experiment in a detached, disposable Git worktree.
set -euo pipefail
umask 077
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
VENV="$HOME/.local/share/continuum-aider-nvidia/venv"
MODE="${1:-}"
if [[ "$MODE" == '--check' ]]; then
  command -v python3 >/dev/null || { echo PYTHON_REQUIRED;exit 2; }
  echo "Aider installed: $([[ -x "$VENV/bin/aider" ]] && echo yes || echo no)"
  echo "NVIDIA_API_KEY available: $([[ -n "${NVIDIA_API_KEY:-}" ]] && echo yes || echo no)"
  echo 'No network request made.'
  exit 0
elif [[ "$MODE" == '--install' ]]; then
  python3 -m venv "$VENV"
  "$VENV/bin/python" -m pip install --upgrade pip aider-chat
  "$VENV/bin/aider" --version
  exit 0
elif [[ "$MODE" != '--run' || $# -ne 1 ]]; then
  echo 'Usage: bash scripts/continuum-aider-nvidia-lab.sh --check|--install|--run' >&2
  exit 2
fi
[[ -x "$VENV/bin/aider" ]] || { echo AIDER_NOT_INSTALLED;exit 2; }
[[ "${NVIDIA_API_KEY:-}" =~ ^nvapi-[A-Za-z0-9_-]{12,256}$ ]] || { echo ROTATED_NVIDIA_KEY_REQUIRED;exit 2; }
[[ "$(git -C "$ROOT" symbolic-ref --quiet --short HEAD)" == 'research/continuum-cloud-gpu-admission-20261030' ]] || { echo RESEARCH_BRANCH_REQUIRED;exit 2; }
[[ -z "$(git -C "$ROOT" status --porcelain)" ]] || { echo CLEAN_SOURCE_TREE_REQUIRED;exit 2; }
SANDBOX="$(mktemp -d "${TMPDIR:-/tmp}/continuum-aider-XXXXXX")"
cleanup(){ git -C "$ROOT" worktree remove --force "$SANDBOX" 2>/dev/null || true; rm -rf -- "$SANDBOX"; }
trap cleanup EXIT
# Aider only sees a disposable copy; no push, no production commits.
git -C "$ROOT" worktree add --detach "$SANDBOX" HEAD >/dev/null
export OPENAI_API_BASE='https://integrate.api.nvidia.com/v1'
export OPENAI_API_KEY="$NVIDIA_API_KEY"
unset NVIDIA_API_KEY
cd "$SANDBOX"
"$VENV/bin/aider" \
  --model openai/nvidia/nemotron-3-ultra-550b-a55b \
  --model-settings-file docs/research/neuro-agent/aider-nvidia-ultra-model-settings.yml \
  --no-auto-commits --no-auto-test --no-auto-lint \
  --no-suggest-shell-commands --no-watch-files \
  app/src/main/java/at/heinz/deviceai/research/neuro/TemporalNeuralShadow.kt
