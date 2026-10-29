#!/usr/bin/env bash
# Read-only commit/working-tree check shared by QA and the local signer.
# Prints only a fixed error code, never diffs, paths or credentials.
continuum_require_source_state() {
  local root expected actual state
  if [[ $# -ne 2 ]]; then
    printf '%s\n' CONTINUUM_SOURCE_STATE_REFUSED >&2
    return 69
  fi
  root="$1"; expected="$2"
  if [[ ! "$expected" =~ ^[a-f0-9]{40}$ ]] ||
     ! actual="$(git -C "$root" rev-parse --verify HEAD 2>/dev/null)" ||
     ! state="$(git -C "$root" status --porcelain --untracked-files=normal 2>/dev/null)" ||
     [[ "$actual" != "$expected" || -n "$state" ]]; then
    printf '%s\n' CONTINUUM_SOURCE_STATE_REFUSED >&2
    return 69
  fi
}
