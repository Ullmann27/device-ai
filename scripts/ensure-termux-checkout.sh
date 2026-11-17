#!/usr/bin/env bash
# Continuum owner checkout guard. Purely local and fail-closed.
# Usage: ensure-termux-checkout.sh <checkout-directory> <expected-40-char-SHA> [--repair-missing]
# Never fetches, resets branches, disables sparse checkout, or overwrites files.
set -Eeuo pipefail
umask 077

fatal() { printf 'CONTINUUM_CHECKOUT_FAILED: %s\n' "$*" >&2; exit 20; }
[[ $# -ge 2 && $# -le 3 ]] || fatal 'Usage: <checkout> <expected-SHA> [--repair-missing]'
checkout="$1"
expected="$2"
mode="${3:---check}"
[[ "$mode" == '--check' || "$mode" == '--repair-missing' ]] || fatal 'Invalid mode'
[[ "$expected" =~ ^[0-9a-f]{40}$ ]] || fatal 'Invalid source commit fingerprint'
[[ -d "$checkout" && ! -L "$checkout" ]] || fatal 'Checkout directory absent or a symlink'
command -v git >/dev/null 2>&1 || fatal 'Git unavailable'
root="$(git -C "$checkout" rev-parse --show-toplevel 2>/dev/null)" || fatal 'Not a Git checkout'
[[ "$(cd "$checkout" && pwd -P)" == "$(cd "$root" && pwd -P)" ]] || fatal 'Point to the checkout root, not a nested directory'
[[ ! -L "$root" ]] || fatal 'Symlinked root disallowed'
head="$(git -C "$root" rev-parse --verify HEAD 2>/dev/null)" || fatal 'HEAD unavailable'
[[ "$head" == "$expected" ]] || fatal 'Source commit mismatch; never restore across versions'

required=(
  'scripts/continuum-owned-app.mjs'
  'scripts/continuum-typed-coding-bridge.mjs'
  '.github/continuum/coding-task.mjs'
  '.github/continuum/ccc1-cli.mjs'
)
for file in "${required[@]}"; do
  blob="$(git -C "$root" rev-parse --verify "$head:$file" 2>/dev/null)" || fatal "Missing tracked blob: $file"
  [[ "$(git -C "$root" cat-file -t "$blob" 2>/dev/null)" == 'blob' ]] || fatal "Not a Git file blob: $file"
  modebits="$(git -C "$root" ls-tree "$head" -- "$file" | awk '{print $1}')"
  [[ "$modebits" == '100644' || "$modebits" == '100755' ]] || fatal "Not a regular tracked file: $file"
  target="$root/$file"
  for parent in "$root/scripts" "$root/.github" "$root/.github/continuum"; do
    [[ ! -L "$parent" ]] || fatal 'Symlinked checkout subdirectory disallowed'
  done
  [[ ! -L "$target" ]] || fatal "Symlinked script disallowed: $file"
  if [[ ! -e "$target" ]]; then
    if [[ "$mode" != '--repair-missing' ]]; then
      fatal "Missing local file: $file; use --repair-missing only after owner-reviewed preflight"
    fi
    directory="$(dirname "$target")"
    [[ ! -e "$directory" ]] && mkdir -p -- "$directory"
    [[ -d "$directory" && ! -L "$directory" ]] || fatal "Untrusted target directory: $file"
    temp="$(mktemp "$directory/.continuum-restore-XXXXXXXX")" || fatal 'Temporary file unavailable'
    if ! git -C "$root" cat-file blob "$blob" > "$temp"; then
      rm -f -- "$temp"
      fatal "Missing local Git object content: $file"
    fi
    if [[ "$modebits" == '100755' ]]; then chmod 700 -- "$temp"; else chmod 600 -- "$temp"; fi
    # Hard link is atomic and refuses replacement of a concurrent existing file.
    if ! ln -- "$temp" "$target" 2>/dev/null; then
      rm -f -- "$temp"
      fatal "Concurrent change or unsafe restore target: $file"
    fi
    rm -f -- "$temp"
    printf 'RESTORED_FROM_PINNED_HEAD: %s\n' "$file"
  fi
  [[ -f "$target" && ! -L "$target" ]] || fatal "Unverified target: $file"
  actual="$(git hash-object -- "$target" 2>/dev/null)" || fatal "Cannot hash file: $file"
  [[ "$actual" == "$blob" ]] || fatal "Modified script, refused to overwrite: $file"
  printf 'VERIFIED: %s\n' "$file"
done
printf 'CONTINUUM_CHECKOUT_OK SHA=%s\n' "$head"
