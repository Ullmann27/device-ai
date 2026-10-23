#!/usr/bin/env bash
# EXP-02/05/12: opt-in Linux-only sandbox for ONE staged, standalone JS candidate.
# No model strings are evaluated as commands. Never substitute for Android proof.
set -Eeuo pipefail
umask 077
fail() { printf 'CONTINUUM_ISOLATION_UNAVAILABLE: %s\n' "$1" >&2; exit 2; }
[[ "$(uname -s)" == Linux ]] || fail 'Linux required'
[[ $# == 1 && -n "${CONTINUUM_ISOLATION_STAGE:-}" ]] || fail 'trusted stage and one candidate required'
for binary in node unshare mount chroot setpriv timeout prlimit install ldd awk stat realpath sha256sum; do
  command -v "$binary" >/dev/null 2>&1 || fail "missing tool: $binary"
done
stage="$CONTINUUM_ISOLATION_STAGE"
[[ -d "$stage" && ! -L "$stage" ]] || fail 'unsafe staging directory'
stage="$(realpath -e -- "$stage")"
[[ "$(stat -c %u -- "$stage")" == "$(id -u)" ]] || fail 'stage not owned by caller'
[[ "$(stat -c %a -- "$stage")" =~ ^(700|500)$ ]] || fail 'stage must be mode 700 or 500'
candidate="$1"
[[ -f "$candidate" && ! -L "$candidate" ]] || fail 'candidate must be a regular file'
candidate="$(realpath -e -- "$candidate")"
[[ "$candidate" == "$stage"/* && "${candidate#"$stage"/}" != */* ]] || fail 'candidate outside root or nested'
[[ "$candidate" == *.mjs ]] || fail 'standalone .mjs only'
[[ "$(stat -c %u -- "$candidate")" == "$(id -u)" ]] || fail 'candidate not owned by caller'
size="$(stat -c %s -- "$candidate")"
[[ "$size" -gt 0 && "$size" -le 16384 ]] || fail 'candidate size outside 1..16384 bytes'
root="$(mktemp -d "${TMPDIR:-/tmp}/continuum-isolation.XXXXXXXX")"
cleanup() { chmod -R u+w -- "$root" 2>/dev/null || true; rm -rf -- "$root"; }
trap cleanup EXIT
mkdir -p -- "$root/bin" "$root/work" "$root/lib64"
node_binary="$(command -v node)"
install -m 0555 -- "$node_binary" "$root/bin/node"
setpriv_binary="$(command -v setpriv)"
install -m 0555 -- "$setpriv_binary" "$root/bin/setpriv"
for runtime_binary in "$node_binary" "$setpriv_binary"; do
  while IFS= read -r dependency; do
    [[ "$dependency" == /* && -f "$dependency" ]] || fail 'unexpected dynamic dependency'
    install -D -m 0444 -- "$dependency" "$root$dependency"
  done < <(ldd "$runtime_binary" | awk '/=> \// {print $3} /^[[:space:]]*\// {print $1}')
done
for loader in /lib64/ld-linux-x86-64.so.2 /lib/ld-linux-aarch64.so.1; do
  if [[ -f "$loader" ]]; then install -D -m 0555 -- "$loader" "$root$loader"; fi
done
install -m 0444 -- "$candidate" "$root/work/candidate.mjs"
source_hash="$(sha256sum "$candidate" | awk '{print $1}')"
staged_hash="$(sha256sum "$root/work/candidate.mjs" | awk '{print $1}')"
[[ "$source_hash" == "$staged_hash" ]] || fail 'source changed during staging'
chmod -R a-w -- "$root"
# Root directory is mounted read-only INSIDE a new mount namespace, and the
# untrusted Node process receives no host filesystem, credentials, or network.
# Drop capabilities after chroot; do not run external shell strings from code.
prlimit --cpu=8 --as=3221225472 --nofile=64 -- \
 timeout --signal=TERM --kill-after=1s 12s \
 env -i PATH=/usr/sbin:/usr/bin:/bin HOME=/work LC_ALL=C ROOT="$root" \
 unshare --user --map-root-user --mount --net --ipc --pid --fork --kill-child \
 --propagation private /bin/sh -ec '
   mount --bind "$ROOT" "$ROOT"
   mount -o remount,bind,ro "$ROOT"
   # Apply the process budget after entering the new user namespace: before
   # unshare it counts unrelated host processes for the caller and can prevent
   # timeout/unshare from forking at all. CPU/address-space limits still cover setup.
   exec prlimit --nproc=32 -- chroot "$ROOT" /bin/setpriv --no-new-privs --bounding-set=-all \
     --inh-caps=-all --ambient-caps=-all /bin/node /work/candidate.mjs
 '
